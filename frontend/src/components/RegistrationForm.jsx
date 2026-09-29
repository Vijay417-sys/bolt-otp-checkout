import { useState } from 'react';
import { registerUser } from '../services/api';
import { normalizeEmail, validateRegistrationForm } from '../utils/validation';
import { Spinner } from './LoadingSpinner';
import Toast from './Toast';

/**
 * Registration form. On success the backend returns the generated 6-digit
 * login code, which is displayed on screen exactly as the assignment requires -
 * no email or SMS delivery is involved.
 *
 * @param onRegistered called with the registered email when the user continues
 *   to checkout, so the email field can be pre-filled.
 */
export default function RegistrationForm({ onRegistered }) {
  const [form, setForm] = useState({ firstName: '', lastName: '', email: '' });
  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const [serverError, setServerError] = useState('');
  const [registeredCode, setRegisteredCode] = useState('');

  const isSuccess = Boolean(registeredCode);

  const updateField = (field) => (event) => {
    const { value } = event.target;
    setForm((previous) => ({ ...previous, [field]: value }));
    // Clear the field error as soon as the user edits that field.
    if (errors[field]) {
      setErrors((previous) => ({ ...previous, [field]: undefined }));
    }
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (submitting) return;

    const validationErrors = validateRegistrationForm(form);
    setErrors(validationErrors);
    setServerError('');
    if (Object.keys(validationErrors).length > 0) return;

    setSubmitting(true);
    try {
      const response = await registerUser({
        email: normalizeEmail(form.email),
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
      });
      setRegisteredCode(response.code);
    } catch (err) {
      setServerError(err.message || 'Registration failed. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  if (isSuccess) {
    return (
      <section className="animate-fade-in text-center" aria-labelledby="registration-success-title">
        <div className="mx-auto mb-5 flex h-12 w-12 items-center justify-center rounded-full bg-green-100 text-green-600">
          <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 12.75l6 6 9-13.5" />
          </svg>
        </div>

        <h2 id="registration-success-title" className="text-xl font-semibold text-slate-900">
          Registration successful
        </h2>
        <p className="mt-1.5 text-sm text-slate-600">Your login code is</p>

        <p
          className="my-5 rounded-xl border border-blue-200 bg-blue-50 py-5 font-mono text-4xl font-bold tracking-[0.35em] text-blue-700"
          aria-label={`Your login code is ${registeredCode.split('').join(' ')}`}
        >
          {registeredCode}
        </p>

        <p className="text-sm text-slate-600">Save this code for checkout login.</p>

        <button
          type="button"
          onClick={() => onRegistered?.(form.email.trim().toLowerCase())}
          className="btn-primary mt-6 w-full"
        >
          Go to checkout
        </button>
      </section>
    );
  }

  return (
    <section aria-labelledby="registration-title">
      <h2 id="registration-title" className="text-xl font-semibold text-slate-900">
        Create your account
      </h2>
      <p className="mt-1.5 text-sm text-slate-600">
        We will generate a 6-digit code you can use to log in at checkout.
      </p>

      <form onSubmit={handleSubmit} className="mt-6 space-y-4" noValidate>
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label htmlFor="firstName" className="field-label">
              First name
            </label>
            <input
              id="firstName"
              name="firstName"
              type="text"
              autoComplete="given-name"
              value={form.firstName}
              onChange={updateField('firstName')}
              aria-invalid={Boolean(errors.firstName)}
              aria-describedby={errors.firstName ? 'firstName-error' : undefined}
              className={`field-input ${errors.firstName ? 'field-input-error' : ''}`}
              placeholder="Vijay"
            />
            {errors.firstName && (
              <p id="firstName-error" role="alert" className="mt-1.5 text-xs font-medium text-red-700">
                {errors.firstName}
              </p>
            )}
          </div>

          <div>
            <label htmlFor="lastName" className="field-label">
              Last name
            </label>
            <input
              id="lastName"
              name="lastName"
              type="text"
              autoComplete="family-name"
              value={form.lastName}
              onChange={updateField('lastName')}
              aria-invalid={Boolean(errors.lastName)}
              aria-describedby={errors.lastName ? 'lastName-error' : undefined}
              className={`field-input ${errors.lastName ? 'field-input-error' : ''}`}
              placeholder="Hosapeti"
            />
            {errors.lastName && (
              <p id="lastName-error" role="alert" className="mt-1.5 text-xs font-medium text-red-700">
                {errors.lastName}
              </p>
            )}
          </div>
        </div>

        <div>
          <label htmlFor="email" className="field-label">
            Email
          </label>
          <input
            id="email"
            name="email"
            type="email"
            autoComplete="email"
            value={form.email}
            onChange={updateField('email')}
            aria-invalid={Boolean(errors.email)}
            aria-describedby={errors.email ? 'email-error' : undefined}
            className={`field-input ${errors.email ? 'field-input-error' : ''}`}
            placeholder="you@example.com"
          />
          {errors.email && (
            <p id="email-error" role="alert" className="mt-1.5 text-xs font-medium text-red-700">
              {errors.email}
            </p>
          )}
        </div>

        {serverError && <Toast message={serverError} type="error" />}

        <button type="submit" disabled={submitting} className="btn-primary w-full">
          {submitting && <Spinner label="" className="text-white" />}
          {submitting ? 'Creating account...' : 'Create account'}
        </button>
      </form>
    </section>
  );
}
