import { useCallback, useEffect, useRef, useState } from 'react';
import { verifyOtp } from '../services/api';
import { validateOtp } from '../utils/validation';
import { Spinner } from './LoadingSpinner';

const DIGITS = 6;

/**
 * Modal asking a recognised user for their 6-digit login code.
 *
 * Accessibility: focus moves into the dialog on open and is trapped inside it,
 * Escape skips login, and the dialog is labelled for screen readers.
 */
export default function OtpModal({ email, onSuccess, onSkip }) {
  const [code, setCode] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const dialogRef = useRef(null);
  const firstInputRef = useRef(null);
  // Set when a retry should grab focus once the inputs are enabled again.
  const pendingFocusRef = useRef(false);

  useEffect(() => {
    firstInputRef.current?.focus();
  }, []);

  /** Keeps Tab cycling within the dialog while it is open. */
  const handleKeyDown = useCallback(
    (event) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        onSkip?.();
        return;
      }
      if (event.key !== 'Tab' || !dialogRef.current) return;

      const focusable = dialogRef.current.querySelectorAll(
        'button:not([disabled]), input:not([disabled])',
      );
      if (focusable.length === 0) return;

      const first = focusable[0];
      const last = focusable[focusable.length - 1];

      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    },
    [onSkip],
  );

  const focusBox = (index) => {
    const inputs = dialogRef.current?.querySelectorAll('input');
    const target = inputs?.[Math.min(Math.max(index, 0), DIGITS - 1)];
    target?.focus();
    target?.setSelectionRange?.(1, 1);
  };

  /**
   * Updates a single digit box.
   * Writing by index (rather than replacing the whole code) is what lets the
   * user type across boxes, paste a full code, or correct one digit in place.
   */
  const setDigitAt = (index, digit) => {
    setCode((previous) => {
      const next = previous.padEnd(DIGITS, ' ').split('');
      next[index] = digit;
      // Re-joining keeps unfilled positions as spaces so each box stays blank.
      return next.join('').replace(/ +$/, '');
    });
  };

  /** Handles typing into one box. Each box holds at most a single digit. */
  const handleChange = (index, rawValue) => {
    const digit = rawValue.replace(/\D/g, '').slice(-1);
    if (digit) {
      setDigitAt(index, digit);
    }
    // Clear a previous error as soon as the user starts correcting it.
    if (error) setError('');
  };

  /** Spreads a pasted or autofilled code across the boxes. */
  const handlePaste = (index, event) => {
    const pasted = event.clipboardData.getData('text').replace(/\D/g, '');
    if (!pasted) return;
    event.preventDefault();
    setCode(pasted.slice(0, DIGITS));
    setError('');
    focusBox(index + pasted.length);
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (submitting) return; // guards against duplicate submissions

    const validationError = validateOtp(code);
    if (validationError) {
      setError(validationError);
      return;
    }

    setSubmitting(true);
    setError('');
    try {
      const user = await verifyOtp({ email, code });
      onSuccess?.(user);
    } catch (err) {
      // The modal stays open and the checkout form behind it is untouched,
      // so the user can simply try another code.
      setError(err.message || 'Invalid login code. Please try again.');
      setCode('');
      // Focus is restored only after the inputs are re-enabled, otherwise the
      // browser refuses to focus a disabled element and the user is stranded.
      pendingFocusRef.current = true;
    } finally {
      setSubmitting(false);
    }
  };

  // Re-focus the first box after a failed attempt so the user can type again
  // straight away without reaching for the mouse.
  useEffect(() => {
    if (submitting || !pendingFocusRef.current) return;
    pendingFocusRef.current = false;
    firstInputRef.current?.focus();
  }, [submitting, error]);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 px-4 py-8 backdrop-blur-sm"
      onKeyDown={handleKeyDown}
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="otp-modal-title"
        aria-describedby="otp-modal-description"
        className="w-full max-w-sm animate-scale-in rounded-2xl bg-white p-6 shadow-2xl"
      >
        <div className="mb-5 text-center">
          <span
            aria-hidden="true"
            className="mx-auto mb-3 flex h-11 w-11 items-center justify-center rounded-full bg-blue-50 text-blue-600"
          >
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" strokeWidth={1.8} stroke="currentColor">
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M12 6v6l4 2m6-2a10 10 0 11-20 0 10 10 0 0120 0z"
              />
            </svg>
          </span>
          <h2 id="otp-modal-title" className="text-lg font-semibold text-slate-900">
            Welcome back
          </h2>
          <p id="otp-modal-description" className="mt-1.5 text-sm text-slate-600">
            We found an account for <span className="font-medium text-slate-800">{email}</span>.
          </p>
        </div>

        <form onSubmit={handleSubmit} noValidate>
          <label htmlFor="otp-code" className="field-label text-center">
            Enter your 6-digit login code
          </label>

          <div className="mb-1 flex justify-center gap-2">
            {Array.from({ length: DIGITS }).map((_, index) => (
              <input
                // Only the first box is tabbable; the rest are filled by typing.
                key={index}
                ref={index === 0 ? firstInputRef : null}
                type="text"
                inputMode="numeric"
                autoComplete={index === 0 ? 'one-time-code' : 'off'}
                maxLength={1}
                tabIndex={index === 0 ? 0 : -1}
                aria-label={`Login code digit ${index + 1} of ${DIGITS}`}
                aria-invalid={Boolean(error)}
                value={code[index] || ''}
                disabled={submitting}
                onChange={(event) => {
                  handleChange(index, event.target.value);
                  // Advance to the next box after a digit is entered.
                  if (/\d/.test(event.target.value)) {
                    focusBox(index + 1);
                  }
                }}
                onPaste={(event) => handlePaste(index, event)}
                onKeyDown={(event) => {
                  if (event.key === 'Backspace' && !code[index] && index > 0) {
                    event.preventDefault();
                    setDigitAt(index - 1, '');
                    focusBox(index - 1);
                  } else if (event.key === 'ArrowLeft' && index > 0) {
                    event.preventDefault();
                    focusBox(index - 1);
                  } else if (event.key === 'ArrowRight' && index < DIGITS - 1) {
                    event.preventDefault();
                    focusBox(index + 1);
                  }
                }}
                className="otp-digit"
              />
            ))}
          </div>

          {error && (
            <p role="alert" className="mt-3 text-center text-sm font-medium text-red-700">
              {error}
            </p>
          )}

          {submitting && (
            <p className="mt-2 flex justify-center">
              <Spinner label="Verifying..." />
            </p>
          )}

          <div className="mt-5 flex flex-col gap-2 sm:flex-row">
            <button
              type="submit"
              disabled={submitting || code.length !== DIGITS}
              className="btn-primary flex-1"
            >
              {submitting ? 'Verifying...' : 'Continue'}
            </button>
            <button
              type="button"
              onClick={() => onSkip?.()}
              disabled={submitting}
              className="btn-secondary flex-1"
            >
              Skip login
            </button>
          </div>

          <p className="mt-3 text-center text-xs text-slate-500">
            Your checkout details are kept if you skip login.
          </p>
        </form>
      </div>
    </div>
  );
}
