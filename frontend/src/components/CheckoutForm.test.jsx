import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import CheckoutForm from './CheckoutForm';
import { ApiError } from '../services/api';

vi.mock('../services/api', () => ({
  ApiError: class ApiError extends Error {},
  registerUser: vi.fn(),
  recognizeUser: vi.fn(),
  verifyOtp: vi.fn(),
  submitCheckout: vi.fn(),
  getHealth: vi.fn(),
}));

import { recognizeUser, verifyOtp, submitCheckout } from '../services/api';

const ADDRESS = 'Flat 4, MG Road, Bengaluru, Karnataka 560001, India';

const typeEmail = async (user, value) => {
  const field = screen.getByLabelText(/^email/i);
  await user.clear(field);
  await user.type(field, value);
};

const fillRest = async (user) => {
  await user.type(screen.getByLabelText(/^phone/i), '+91 98765 43210');
  await user.type(screen.getByLabelText(/shipping address/i), ADDRESS);
};

describe('CheckoutForm', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders the checkout form as a guest', () => {
    render(<CheckoutForm />);
    expect(screen.getByRole('heading', { name: /checkout/i })).toBeInTheDocument();
    expect(screen.getByText(/guest checkout/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^phone/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/shipping address/i)).toBeInTheDocument();
  });

  it('does not call the recognition API for an invalid email', async () => {
    const user = userEvent.setup();
    render(<CheckoutForm />);

    await typeEmail(user, 'not-an-email');
    await new Promise((resolve) => setTimeout(resolve, 800));

    expect(recognizeUser).not.toHaveBeenCalled();
  });

  it('calls the recognition API once after the debounce delay', async () => {
    const user = userEvent.setup();
    recognizeUser.mockResolvedValue({ registered: false });
    render(<CheckoutForm />);

    await typeEmail(user, 'guest@example.com');
    await new Promise((resolve) => setTimeout(resolve, 900));

    // Typing character by character must not produce one request per keystroke.
    expect(recognizeUser).toHaveBeenCalledTimes(1);
    expect(recognizeUser).toHaveBeenCalledWith('guest@example.com');
  });

  it('opens the OTP modal for a registered email', async () => {
    const user = userEvent.setup();
    recognizeUser.mockResolvedValue({ registered: true });
    render(<CheckoutForm />);

    await typeEmail(user, 'vijay@example.com');

    const dialog = await screen.findByRole('dialog', {}, { timeout: 3000 });
    expect(dialog).toBeInTheDocument();
    expect(screen.getByText(/welcome back/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /skip login/i })).toBeInTheDocument();
  });

  it('shows the continue button as disabled until six digits are entered', async () => {
    const user = userEvent.setup();
    recognizeUser.mockResolvedValue({ registered: true });
    render(<CheckoutForm />);

    await typeEmail(user, 'vijay@example.com');
    const dialog = await screen.findByRole('dialog', {}, { timeout: 3000 });
    expect(dialog).toBeInTheDocument();

    await user.type(screen.getByLabelText(/login code digit 1 of 6/i), '482');
    expect(screen.getByRole('button', { name: /continue/i })).toBeDisabled();

    await user.type(screen.getByLabelText(/login code digit 4 of 6/i), '193');
    await waitFor(() => expect(screen.getByRole('button', { name: /continue/i })).toBeEnabled());
  });

  it('ignores non-numeric characters in the OTP field', async () => {
    const user = userEvent.setup();
    recognizeUser.mockResolvedValue({ registered: true });
    render(<CheckoutForm />);

    await typeEmail(user, 'vijay@example.com');
    await screen.findByRole('dialog', {}, { timeout: 3000 });

    await user.type(screen.getByLabelText(/login code digit 1 of 6/i), 'abc');
    expect(screen.getByLabelText(/login code digit 1 of 6/i)).toHaveValue('');
    expect(screen.getByRole('button', { name: /continue/i })).toBeDisabled();
  });

  it('keeps the modal open and shows an error for an incorrect code', async () => {
    const user = userEvent.setup();
    recognizeUser.mockResolvedValue({ registered: true });
    verifyOtp.mockRejectedValue(new ApiError('Invalid login code', 401));
    render(<CheckoutForm />);

    await typeEmail(user, 'vijay@example.com');
    await screen.findByRole('dialog', {}, { timeout: 3000 });

    await user.type(screen.getByLabelText(/login code digit 1 of 6/i), '111111');
    await user.click(screen.getByRole('button', { name: /continue/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid login code');
    // Modal must remain open and the checkout data must be intact.
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByLabelText(/^email/i)).toHaveValue('vijay@example.com');
  });

  it('logs the user in with a correct code and shows their name', async () => {
    const user = userEvent.setup();
    recognizeUser.mockResolvedValue({ registered: true });
    verifyOtp.mockResolvedValue({
      success: true,
      userId: 1,
      firstName: 'Vijay',
      lastName: 'Hosapeti',
      sessionToken: 'signed.token',
    });
    render(<CheckoutForm prefillEmail="vijay@example.com" />);

    await screen.findByRole('dialog', {}, { timeout: 3000 });
    await user.type(screen.getByLabelText(/login code digit 1 of 6/i), '482193');
    await user.click(screen.getByRole('button', { name: /continue/i }));

    expect(await screen.findByText(/welcome, vijay hosapeti/i)).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    // The email field is preserved after login.
    expect(screen.getByLabelText(/^email/i)).toHaveValue('vijay@example.com');
  });

  it('closes the modal and keeps checkout data when the user skips login', async () => {
    const user = userEvent.setup();
    recognizeUser.mockResolvedValue({ registered: true });
    render(<CheckoutForm />);

    await typeEmail(user, 'vijay@example.com');
    await screen.findByRole('dialog', {}, { timeout: 3000 });

    // Type some data before skipping, to prove it survives.
    await fillRest(user);
    await user.click(screen.getByRole('button', { name: /skip login/i }));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.getByText(/guest checkout/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^phone/i)).toHaveValue('+91 98765 43210');
    expect(screen.getByLabelText(/shipping address/i)).toHaveValue(ADDRESS);
  });

  it('does not reopen the modal for the same email after skipping', async () => {
    const user = userEvent.setup();
    recognizeUser.mockResolvedValue({ registered: true });
    render(<CheckoutForm />);

    await typeEmail(user, 'vijay@example.com');
    await screen.findByRole('dialog', {}, { timeout: 3000 });
    await user.click(screen.getByRole('button', { name: /skip login/i }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    // Continue editing the same email: the modal must stay closed.
    const field = screen.getByLabelText(/^email/i);
    await user.type(field, 'x');
    await user.type(field, '{backspace}');
    await new Promise((resolve) => setTimeout(resolve, 900));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('validates the checkout form before calling the API', async () => {
    const user = userEvent.setup();
    render(<CheckoutForm />);

    await user.click(screen.getByRole('button', { name: /submit checkout/i }));

    expect(await screen.findByText('Email is required')).toBeInTheDocument();
    expect(screen.getByText('Phone number is required')).toBeInTheDocument();
    expect(submitCheckout).not.toHaveBeenCalled();
  });

  it('submits a guest checkout without a session token', async () => {
    const user = userEvent.setup();
    submitCheckout.mockResolvedValue({ success: true, message: 'Checkout submitted successfully' });
    render(<CheckoutForm />);

    await typeEmail(user, 'guest@example.com');
    await fillRest(user);
    await user.click(screen.getByRole('button', { name: /submit checkout/i }));

    expect(await screen.findByText('Checkout submitted successfully!')).toBeInTheDocument();
    expect(submitCheckout).toHaveBeenCalledWith(
      { email: 'guest@example.com', phone: '+91 98765 43210', shippingAddress: ADDRESS },
      null,
    );
  });

  it('sends the session token for an authenticated checkout', async () => {
    const user = userEvent.setup();
    recognizeUser.mockResolvedValue({ registered: true });
    verifyOtp.mockResolvedValue({
      success: true,
      userId: 1,
      firstName: 'Vijay',
      lastName: 'Hosapeti',
      sessionToken: 'signed.token',
    });
    submitCheckout.mockResolvedValue({ success: true, message: 'Checkout submitted successfully' });

    render(<CheckoutForm prefillEmail="vijay@example.com" />);
    await screen.findByRole('dialog', {}, { timeout: 3000 });
    await user.type(screen.getByLabelText(/login code digit 1 of 6/i), '482193');
    await user.click(screen.getByRole('button', { name: /continue/i }));
    await screen.findByText(/welcome, vijay hosapeti/i);

    await fillRest(user);
    await user.click(screen.getByRole('button', { name: /submit checkout/i }));

    await waitFor(() => expect(submitCheckout).toHaveBeenCalled());
    expect(submitCheckout).toHaveBeenCalledWith(
      { email: 'vijay@example.com', phone: '+91 98765 43210', shippingAddress: ADDRESS },
      'signed.token',
    );
  });

  it('displays a server error when checkout fails', async () => {
    const user = userEvent.setup();
    submitCheckout.mockRejectedValue(new ApiError('Unable to submit checkout', 500));

    render(<CheckoutForm prefillEmail="guest@example.com" />);
    await fillRest(user);
    await user.click(screen.getByRole('button', { name: /submit checkout/i }));

    expect(await screen.findByText('Unable to submit checkout')).toBeInTheDocument();
  });

  it('shows a "Checking account..." status while recognition is in flight', async () => {
    const user = userEvent.setup();
    let resolveRecognition;
    recognizeUser.mockReturnValue(
      new Promise((resolve) => {
        resolveRecognition = resolve;
      }),
    );

    render(<CheckoutForm />);
    await typeEmail(user, 'vijay@example.com');

    expect(await screen.findByText(/checking account/i)).toBeInTheDocument();

    resolveRecognition({ registered: false });
    await waitFor(() => expect(screen.queryByText(/checking account/i)).not.toBeInTheDocument());
  });

  it('keeps phone and address usable while recognition is running', async () => {
    const user = userEvent.setup();
    let resolveRecognition;
    recognizeUser.mockReturnValue(
      new Promise((resolve) => {
        resolveRecognition = resolve;
      }),
    );

    render(<CheckoutForm />);
    await typeEmail(user, 'vijay@example.com');

    // Recognition is deliberately left pending while the user keeps typing.
    await fillRest(user);

    const phoneField = screen.getByLabelText(/^phone/i);
    const addressField = screen.getByLabelText(/shipping address/i);
    expect(phoneField).toBeEnabled();
    expect(addressField).toBeEnabled();
    expect(phoneField).toHaveValue('+91 98765 43210');
    expect(addressField).toHaveValue(ADDRESS);

    resolveRecognition({ registered: false });
    await screen.findByText(/no account found/i);
  });

  it('reports a recognition failure without blocking checkout', async () => {
    const user = userEvent.setup();
    recognizeUser.mockRejectedValue(new ApiError('Unable to check email', 0));
    submitCheckout.mockResolvedValue({ success: true, message: 'Checkout submitted successfully' });

    render(<CheckoutForm />);
    await typeEmail(user, 'vijay@example.com');

    expect(
      await screen.findByText(/unable to check email.*continue as guest/i, {}, { timeout: 3000 }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    await fillRest(user);
    await user.click(screen.getByRole('button', { name: /submit checkout/i }));
    expect(await screen.findByText('Checkout submitted successfully!')).toBeInTheDocument();
  });
});
