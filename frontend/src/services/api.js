/**
 * Centralised API access for the whole application.
 *
 * Every component talks to the backend through this module so that the base URL,
 * headers and error handling live in exactly one place.
 */

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

/** Error thrown for any non-2xx response, carrying the HTTP status for callers. */
export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

async function request(path, { method = 'GET', body, headers = {} } = {}) {
  let response;

  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers: {
        'Content-Type': 'application/json',
        ...headers,
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    // Network failure, DNS error, CORS rejection, offline browser.
    throw new ApiError('Unable to reach the server. Please try again.', 0);
  }

  let payload = null;
  const text = await response.text();
  if (text) {
    try {
      payload = JSON.parse(text);
    } catch {
      payload = null;
    }
  }

  if (!response.ok) {
    const message = payload?.message || 'Something went wrong. Please try again.';
    throw new ApiError(message, response.status);
  }

  return payload;
}

/** Liveness probe for the backend. */
export function getHealth() {
  return request('/api/health');
}

/** Registers a user and returns the generated 6-digit login code. */
export function registerUser({ email, firstName, lastName }) {
  return request('/api/auth/register', {
    method: 'POST',
    body: { email, firstName, lastName },
  });
}

/**
 * Background recognition check.
 * Resolves to `{ registered: boolean }` - it never exposes personal data.
 */
export function recognizeUser(email) {
  return request(`/api/auth/recognize?email=${encodeURIComponent(email)}`);
}

/** Verifies the login code. Resolves with the user profile on success. */
export function verifyOtp({ email, code }) {
  return request('/api/auth/verify', { method: 'POST', body: { email, code } });
}

/**
 * Submits a checkout.
 * @param sessionToken optional token from a successful OTP verification; when
 *   present the backend links the record to the authenticated user, otherwise
 *   the checkout is stored as a guest with a null user id.
 */
export function submitCheckout({ email, phone, shippingAddress }, sessionToken) {
  return request('/api/checkout', {
    method: 'POST',
    body: { email, phone, shippingAddress },
    headers: sessionToken ? { 'X-Session-Token': sessionToken } : {},
  });
}

export const api = {
  getHealth,
  registerUser,
  recognizeUser,
  verifyOtp,
  submitCheckout,
};
