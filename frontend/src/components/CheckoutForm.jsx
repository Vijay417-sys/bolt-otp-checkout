import { useCallback, useEffect, useRef, useState } from 'react';
import { recognizeUser, submitCheckout } from '../services/api';
import useDebounce from '../hooks/useDebounce';
import { normalizeEmail, validateCheckoutForm, validateEmail } from '../utils/validation';
import OtpModal from './OtpModal';
import UserBadge from './UserBadge';
import { Spinner } from './LoadingSpinner';
import Toast from './Toast';

const RECOGNITION_DELAY = 500;

/**
 * Checkout form.
 *
 * Email recognition runs in the background: the debounced email triggers a
 * lookup while the user is still free to type their phone and address. The
 * phone and address fields are never disabled while recognition is in flight.
 */
export default function CheckoutForm({ prefillEmail = '', onCheckoutSuccess }) {
  const [form, setForm] = useState({ email: prefillEmail, phone: '', shippingAddress: '' });
  const [errors, setErrors] = useState({});

  const [recognition, setRecognition] = useState('idle'); // idle | checking | registered | guest | error
  const [user, setUser] = useState(null);
  const sessionTokenRef = useRef(null);
  const [otpModalOpen, setOtpModalOpen] = useState(false);

  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState('');
  const [submitSuccess, setSubmitSuccess] = useState('');

  // Emails whose modal was already dismissed, so it is not reopened on every keystroke.
  const dismissedForEmailRef = useRef(new Set());
  // Identifies the newest recognition request so stale responses are ignored.
  const requestIdRef = useRef(0);

  // Counts how many times the debounce has settled. Recognition keys off this
  // counter rather than the debounced string, because clearing the field and
  // retyping the same address settles on an identical string - React would see
  // no change, and recognition would never run again for that email.
  const [recognitionRun, setRecognitionRun] = useState(0);
  const debouncedEmail = useDebounce(form.email, RECOGNITION_DELAY, () =>
    setRecognitionRun((run) => run + 1)
  );

  // Reset any completed login when the email changes to a different address.
  const previousEmailRef = useRef(normalizeEmail(form.email));
  useEffect(() => {
    const current = normalizeEmail(form.email);
    if (current !== previousEmailRef.current) {
      previousEmailRef.current = current;
      setUser(null);
      sessionTokenRef.current = null;
      setOtpModalOpen(false);
      setRecognition('idle');
      setSubmitSuccess('');
    }
  }, [form.email]);

  useEffect(() => {
    const email = normalizeEmail(debouncedEmail);

    if (!email) {
      setRecognition('idle');
      return undefined;
    }
    // Never call the API for an incomplete or malformed address.
    if (validateEmail(email)) {
      setRecognition('idle');
      return undefined;
    }

    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;

    setRecognition('checking');

    let cancelled = false;

    (async () => {
      try {
        const result = await recognizeUser(email);
        // Discard the response if a newer request has already been issued.
        if (cancelled || requestIdRef.current !== requestId) return;

        if (result.registered) {
          setRecognition('registered');
          if (!dismissedForEmailRef.current.has(email)) {
            setOtpModalOpen(true);
          }
        } else {
          setRecognition('guest');
        }
      } catch {
        if (cancelled || requestIdRef.current !== requestId) return;
        setRecognition('error');
      }
    })();

    return () => {
      cancelled = true;
    };
    // recognitionRun is the trigger; debouncedEmail carries the value to send.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [recognitionRun]);

  const updateField = (field) => (event) => {
    const { value } = event.target;
    setForm((previous) => ({ ...previous, [field]: value }));
    if (errors[field]) {
      setErrors((previous) => ({ ...previous, [field]: undefined }));
    }
    if (field !== 'email') {
      setSubmitSuccess('');
    }
  };

  // Live email feedback (§23). Only once something has actually been typed, so
  // the field is not scolding an empty box while the user is still on the first
  // character, and only for a genuinely malformed address.
  const trimmedEmail = form.email.trim();
  const liveEmailError =
    trimmedEmail.length > 0 && validateEmail(form.email)
      ? validateEmail(form.email)
      : '';
  const showLiveEmailError = Boolean(liveEmailError) && !errors.email;

  const handleOtpSuccess = useCallback(
    (verifiedUser) => {
      setUser(verifiedUser);
      setOtpModalOpen(false);
      // The signed session token lets the backend link the checkout to this user
      // without trusting anything the client sends in the request body.
      sessionTokenRef.current = verifiedUser?.sessionToken || null;
      // Do not reopen the modal for this email after a successful login.
      dismissedForEmailRef.current.add(normalizeEmail(form.email));
    },
    [form.email],
  );

  const handleSkipLogin = useCallback(() => {
    // Remember the choice so the modal does not reappear for this same email.
    dismissedForEmailRef.current.add(normalizeEmail(form.email));
    setOtpModalOpen(false);
    setUser(null);
    sessionTokenRef.current = null;
  }, [form.email]);

  const handleRetryLogin = useCallback(() => {
    dismissedForEmailRef.current.delete(normalizeEmail(form.email));
    setOtpModalOpen(true);
  }, [form.email]);

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (submitting) return;

    const validationErrors = validateCheckoutForm(form);
    setErrors(validationErrors);
    setSubmitError('');
    if (Object.keys(validationErrors).length > 0) return;

    setSubmitting(true);
    try {
      await submitCheckout(
        {
          email: normalizeEmail(form.email),
          phone: form.phone.trim(),
          shippingAddress: form.shippingAddress.trim(),
        },
        sessionTokenRef.current,
      );
      setSubmitSuccess('Checkout submitted successfully!');
      onCheckoutSuccess?.();
    } catch (err) {
      setSubmitError(err.message || 'Unable to submit checkout. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <section aria-labelledby="checkout-title">
      {user ? (
        <div className="mb-5">
          <UserBadge user={user} />
        </div>
      ) : (
        <div className="mb-5 flex items-center gap-2 text-sm text-slate-600">
          <span
            aria-hidden="true"
            className="flex h-6 w-6 items-center justify-center rounded-full bg-slate-200 text-slate-500"
          >
            <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.5 20.25a7.5 7.5 0 0115 0v.75h-15v-.75z" />
            </svg>
          </span>
          Guest checkout
        </div>
      )}

      <h2 id="checkout-title" className="text-xl font-semibold text-slate-900">
        Checkout
      </h2>
      <p className="mt-1.5 text-sm text-slate-600">
        Enter your details below. We will check whether you already have an account.
      </p>

      <form onSubmit={handleSubmit} className="mt-6 space-y-4" noValidate>
        <div>
          <label htmlFor="checkout-email" className="field-label">
            Email
          </label>
          <input
            id="checkout-email"
            name="email"
            type="email"
            autoComplete="email"
            value={form.email}
            onChange={updateField('email')}
            aria-invalid={Boolean(errors.email || liveEmailError)}
            aria-describedby="checkout-email-status"
            className={`field-input ${errors.email || liveEmailError ? 'field-input-error' : ''}`}
            placeholder="you@example.com"
          />
          {errors.email || showLiveEmailError ? (
            <p role="alert" className="mt-1.5 text-xs font-medium text-red-700">
              {errors.email || liveEmailError}
            </p>
          ) : (
            <div id="checkout-email-status" aria-live="polite">
              <RecognitionStatus
                state={recognition}
                onRetryLogin={handleRetryLogin}
                canRetry={recognition === 'registered' && !user}
              />
            </div>
          )}
        </div>

        <div>
          <label htmlFor="checkout-phone" className="field-label">
            Phone
          </label>
          <input
            id="checkout-phone"
            name="phone"
            type="tel"
            autoComplete="tel"
            value={form.phone}
            onChange={updateField('phone')}
            aria-invalid={Boolean(errors.phone)}
            aria-describedby={errors.phone ? 'checkout-phone-error' : undefined}
            className={`field-input ${errors.phone ? 'field-input-error' : ''}`}
            placeholder="+91 98765 43210"
          />
          {errors.phone && (
            <p id="checkout-phone-error" role="alert" className="mt-1.5 text-xs font-medium text-red-700">
              {errors.phone}
            </p>
          )}
        </div>

        <div>
          <label htmlFor="checkout-address" className="field-label">
            Shipping address
          </label>
          <textarea
            id="checkout-address"
            name="shippingAddress"
            rows={3}
            value={form.shippingAddress}
            onChange={updateField('shippingAddress')}
            aria-invalid={Boolean(errors.shippingAddress)}
            aria-describedby={errors.shippingAddress ? 'checkout-address-error' : undefined}
            className={`field-input resize-y ${errors.shippingAddress ? 'field-input-error' : ''}`}
            placeholder="Flat 4, MG Road, Bengaluru, Karnataka 560001, India"
          />
          {errors.shippingAddress && (
            <p id="checkout-address-error" role="alert" className="mt-1.5 text-xs font-medium text-red-700">
              {errors.shippingAddress}
            </p>
          )}
        </div>

        {submitError && <Toast message={submitError} type="error" onDismiss={() => setSubmitError('')} />}
        {submitSuccess && <Toast message={submitSuccess} type="success" />}

        <button type="submit" disabled={submitting} className="btn-primary w-full">
          {submitting && <Spinner label="" className="text-white" />}
          {submitting ? 'Submitting...' : 'Submit checkout'}
        </button>
      </form>

      {otpModalOpen && (
        <OtpModal
          email={normalizeEmail(form.email)}
          onSuccess={handleOtpSuccess}
          onSkip={handleSkipLogin}
        />
      )}
    </section>
  );
}

/** Inline status line under the email field. Text always accompanies the icon. */
function RecognitionStatus({ state, onRetryLogin, canRetry }) {
  switch (state) {
    case 'checking':
      return (
        <p className="field-hint text-slate-500">
          <Spinner label="" />
          <span>Checking account...</span>
        </p>
      );
    case 'registered':
      return (
        <p className="field-hint font-medium text-green-700">
          <CheckIcon />
          <span>Account recognized</span>
          {canRetry && (
            <button
              type="button"
              onClick={onRetryLogin}
              className="ml-1 font-semibold text-blue-700 underline underline-offset-2 hover:text-blue-800"
            >
              Enter login code
            </button>
          )}
        </p>
      );
    case 'guest':
      return (
        <p className="field-hint text-slate-600">
          <InfoIcon />
          <span>No account found. Continue as guest.</span>
        </p>
      );
    case 'error':
      return (
        <p className="field-hint font-medium text-amber-700">
          <InfoIcon />
          <span>Unable to check email. You can still continue as guest.</span>
        </p>
      );
    default:
      return null;
  }
}

function CheckIcon() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" strokeWidth={2.5} stroke="currentColor" aria-hidden="true">
      <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 12.75l6 6 9-13.5" />
    </svg>
  );
}

function InfoIcon() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor" aria-hidden="true">
      <path strokeLinecap="round" strokeLinejoin="round" d="M11.25 11.25l.041-.02a.75.75 0 011.063.852l-.708 2.836a.75.75 0 001.063.853l.041-.021M21 12a9 9 0 11-18 0 9 9 0 0118 0zm-9-3.75h.008v.008H12V8.25z" />
    </svg>
  );
}
