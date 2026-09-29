import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import RegistrationForm from './RegistrationForm';
import { ApiError } from '../services/api';

vi.mock('../services/api', () => ({
  ApiError: class ApiError extends Error {},
  registerUser: vi.fn(),
  recognizeUser: vi.fn(),
  verifyOtp: vi.fn(),
  submitCheckout: vi.fn(),
  getHealth: vi.fn(),
}));

import { registerUser } from '../services/api';

const fillForm = async (user, { firstName, lastName, email }) => {
  if (firstName) await user.type(screen.getByLabelText(/first name/i), firstName);
  if (lastName) await user.type(screen.getByLabelText(/last name/i), lastName);
  if (email) await user.type(screen.getByLabelText(/^email/i), email);
};

describe('RegistrationForm', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders the registration form', () => {
    render(<RegistrationForm />);
    expect(screen.getByRole('heading', { name: /create your account/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/first name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/last name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^email/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /create account/i })).toBeInTheDocument();
  });

  it('shows validation errors and does not call the API when the form is empty', async () => {
    const user = userEvent.setup();
    render(<RegistrationForm />);

    await user.click(screen.getByRole('button', { name: /create account/i }));

    expect(await screen.findByText('First name is required')).toBeInTheDocument();
    expect(screen.getByText('Last name is required')).toBeInTheDocument();
    expect(screen.getByText('Email is required')).toBeInTheDocument();
    expect(registerUser).not.toHaveBeenCalled();
  });

  it('rejects a malformed email before calling the API', async () => {
    const user = userEvent.setup();
    render(<RegistrationForm />);

    await fillForm(user, { firstName: 'Vijay', lastName: 'Hosapeti', email: 'not-an-email' });
    await user.click(screen.getByRole('button', { name: /create account/i }));

    expect(await screen.findByText('Please enter a valid email address')).toBeInTheDocument();
    expect(registerUser).not.toHaveBeenCalled();
  });

  it('calls the API and displays the generated code on success', async () => {
    const user = userEvent.setup();
    registerUser.mockResolvedValue({ message: 'Registration successful', code: '482193' });
    const onRegistered = vi.fn();

    render(<RegistrationForm onRegistered={onRegistered} />);
    await fillForm(user, { firstName: 'Vijay', lastName: 'Hosapeti', email: 'vijay@example.com' });
    await user.click(screen.getByRole('button', { name: /create account/i }));

    expect(await screen.findByText('482193')).toBeInTheDocument();
    expect(screen.getByText(/save this code for checkout login/i)).toBeInTheDocument();
    // The email is normalised before it is sent.
    expect(registerUser).toHaveBeenCalledWith({
      email: 'vijay@example.com',
      firstName: 'Vijay',
      lastName: 'Hosapeti',
    });
  });

  it('hands the registered email to the parent when continuing to checkout', async () => {
    const user = userEvent.setup();
    registerUser.mockResolvedValue({ code: '482193' });
    const onRegistered = vi.fn();

    render(<RegistrationForm onRegistered={onRegistered} />);
    await fillForm(user, { firstName: 'Vijay', lastName: 'Hosapeti', email: '  Vijay@Example.COM ' });
    await user.click(screen.getByRole('button', { name: /create account/i }));

    await user.click(await screen.findByRole('button', { name: /go to checkout/i }));
    // The email is passed so checkout can pre-fill it - never the login code.
    expect(onRegistered).toHaveBeenCalledWith('vijay@example.com');
  });

  it('surfaces a duplicate email error from the server', async () => {
    const user = userEvent.setup();
    registerUser.mockRejectedValue(new ApiError('Email is already registered', 409));

    render(<RegistrationForm />);
    await fillForm(user, { firstName: 'Vijay', lastName: 'Hosapeti', email: 'vijay@example.com' });
    await user.click(screen.getByRole('button', { name: /create account/i }));

    expect(await screen.findByText('Email is already registered')).toBeInTheDocument();
  });

  it('disables the submit button while the request is in flight', async () => {
    const user = userEvent.setup();
    let resolveRequest;
    registerUser.mockReturnValue(
      new Promise((resolve) => {
        resolveRequest = resolve;
      }),
    );

    render(<RegistrationForm />);
    await fillForm(user, { firstName: 'Vijay', lastName: 'Hosapeti', email: 'vijay@example.com' });
    await user.click(screen.getByRole('button', { name: /create account/i }));

    const button = await screen.findByRole('button', { name: /creating account/i });
    expect(button).toBeDisabled();

    resolveRequest({ code: '482193' });
    await waitFor(() => expect(screen.getByText('482193')).toBeInTheDocument());
  });
});
