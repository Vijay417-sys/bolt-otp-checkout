/**
 * Client-side validation helpers.
 *
 * Frontend validation is for fast feedback only - the backend independently
 * validates every request, so these rules are never the security boundary.
 */

/**
 * Practical, not overly strict, email check.
 * Rejects spaces, missing @, and missing dotted domain while still allowing
 * plus-addressing, subdomains and hyphens.
 */
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

/**
 * Allows an optional leading +, digits, spaces and common separators.
 * Deliberately permissive so it does not reject legitimate international
 * formats; the digit-count rule below is what enforces a real length.
 */
const PHONE_PATTERN = /^[+]?[\d\s()-]{3,20}$/;

export function normalizeEmail(email) {
  return (email || '').trim().toLowerCase();
}

/** @returns {string|null} error message, or null when the email looks valid. */
export function validateEmail(email) {
  const value = (email || '').trim();
  if (!value) return 'Email is required';
  if (/\s/.test(value)) return 'Email cannot contain spaces';
  if (!EMAIL_PATTERN.test(value)) return 'Please enter a valid email address';
  return null;
}

export function validateRequired(value, fieldLabel) {
  if (!(value || '').trim()) return `${fieldLabel} is required`;
  return null;
}

export function validateName(value, fieldLabel) {
  const error = validateRequired(value, fieldLabel);
  if (error) return error;
  if (value.trim().length > 100) return `${fieldLabel} must be 100 characters or fewer`;
  return null;
}

export function validatePhone(phone) {
  const value = (phone || '').trim();
  if (!value) return 'Phone number is required';
  if (!PHONE_PATTERN.test(value)) return 'Please enter a valid phone number';
  const digitCount = (value.match(/\d/g) || []).length;
  if (digitCount < 7) return 'Phone number is too short';
  if (digitCount > 15) return 'Phone number is too long';
  return null;
}

export function validateShippingAddress(address) {
  const error = validateRequired(address, 'Shipping address');
  if (error) return error;
  if (address.trim().length < 10) return 'Please enter a complete shipping address';
  return null;
}

/** @returns {string|null} error message, or null when the code is valid. */
export function validateOtp(code) {
  const value = (code || '').trim();
  if (!value) return 'Please enter the 6-digit login code';
  if (/\D/.test(value)) return 'The login code must contain digits only';
  if (value.length !== 6) return 'Please enter the 6-digit login code';
  return null;
}

/** Validates the whole registration form, returning a map of field -> message. */
export function validateRegistrationForm({ firstName, lastName, email }) {
  const errors = {};
  const nameError = validateName(firstName, 'First name');
  if (nameError) errors.firstName = nameError;
  const lastNameError = validateName(lastName, 'Last name');
  if (lastNameError) errors.lastName = lastNameError;
  const emailError = validateEmail(email);
  if (emailError) errors.email = emailError;
  return errors;
}

/** Validates the whole checkout form, returning a map of field -> message. */
export function validateCheckoutForm({ email, phone, shippingAddress }) {
  const errors = {};
  const emailError = validateEmail(email);
  if (emailError) errors.email = emailError;
  const phoneError = validatePhone(phone);
  if (phoneError) errors.phone = phoneError;
  const addressError = validateShippingAddress(shippingAddress);
  if (addressError) errors.shippingAddress = addressError;
  return errors;
}
