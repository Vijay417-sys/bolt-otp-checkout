import { describe, expect, it } from 'vitest';
import {
  normalizeEmail,
  validateCheckoutForm,
  validateEmail,
  validateOtp,
  validatePhone,
  validateRegistrationForm,
  validateShippingAddress,
} from '../utils/validation';

describe('validateEmail', () => {
  it('rejects an empty value', () => {
    expect(validateEmail('')).toBe('Email is required');
  });

  it('rejects a malformed address', () => {
    expect(validateEmail('not-an-email')).toBe('Please enter a valid email address');
    expect(validateEmail('missing@domain')).toBe('Please enter a valid email address');
    expect(validateEmail('@example.com')).toBe('Please enter a valid email address');
  });

  it('rejects an address containing spaces', () => {
    expect(validateEmail('vijay @example.com')).toBe('Email cannot contain spaces');
  });

  it('accepts ordinary addresses, including subdomains and plus-addressing', () => {
    expect(validateEmail('vijay@example.com')).toBeNull();
    expect(validateEmail('first.last@sub.example.co.in')).toBeNull();
    expect(validateEmail('vijay+bolt@example.com')).toBeNull();
  });
});

describe('normalizeEmail', () => {
  it('trims and lowercases', () => {
    expect(normalizeEmail('  Vijay@Example.COM  ')).toBe('vijay@example.com');
  });
});

describe('validatePhone', () => {
  it('requires a value', () => {
    expect(validatePhone('')).toBe('Phone number is required');
  });

  it('rejects letters and overly short numbers', () => {
    expect(validatePhone('abcdefg')).toBe('Please enter a valid phone number');
    expect(validatePhone('12345')).toBe('Phone number is too short');
  });

  it('accepts international formats', () => {
    expect(validatePhone('+91 98765 43210')).toBeNull();
    expect(validatePhone('+1 (415) 555-0123')).toBeNull();
  });
});

describe('validateShippingAddress', () => {
  it('requires a complete address', () => {
    expect(validateShippingAddress('')).toBe('Shipping address is required');
    expect(validateShippingAddress('Bengaluru')).toBe('Please enter a complete shipping address');
  });

  it('accepts a full address', () => {
    expect(validateShippingAddress('Flat 4, MG Road, Bengaluru, Karnataka 560001, India')).toBeNull();
  });
});

describe('validateOtp', () => {
  it('requires exactly six digits', () => {
    expect(validateOtp('')).toBe('Please enter the 6-digit login code');
    expect(validateOtp('12345')).toBe('Please enter the 6-digit login code');
    expect(validateOtp('1234567')).toBe('Please enter the 6-digit login code');
  });

  it('rejects non-numeric characters', () => {
    expect(validateOtp('12345a')).toBe('The login code must contain digits only');
  });

  it('accepts a valid code', () => {
    expect(validateOtp('482193')).toBeNull();
  });
});

describe('form validators', () => {
  it('collects every registration problem at once', () => {
    const errors = validateRegistrationForm({ firstName: '', lastName: '', email: 'bad' });
    expect(Object.keys(errors).sort()).toEqual(['email', 'firstName', 'lastName']);
  });

  it('returns no errors for a valid registration', () => {
    expect(
      validateRegistrationForm({ firstName: 'Vijay', lastName: 'Hosapeti', email: 'vijay@example.com' }),
    ).toEqual({});
  });

  it('collects every checkout problem at once', () => {
    const errors = validateCheckoutForm({ email: 'bad', phone: '', shippingAddress: '' });
    expect(Object.keys(errors).sort()).toEqual(['email', 'phone', 'shippingAddress']);
  });

  it('returns no errors for a valid checkout', () => {
    expect(
      validateCheckoutForm({
        email: 'vijay@example.com',
        phone: '+91 98765 43210',
        shippingAddress: 'Flat 4, MG Road, Bengaluru, Karnataka 560001, India',
      }),
    ).toEqual({});
  });
});
