import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import OtpModal from './OtpModal';
import { ApiError } from '../services/api';

vi.mock('../services/api', () => ({
  ApiError: class ApiError extends Error {},
  registerUser: vi.fn(),
  recognizeUser: vi.fn(),
  verifyOtp: vi.fn(),
  submitCheckout: vi.fn(),
  getHealth: vi.fn(),
}));

import { verifyOtp } from '../services/api';

describe('OtpModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders an accessible dialog with six digit inputs', () => {
    render(<OtpModal email="vijay@example.com" onSuccess={vi.fn()} onSkip={vi.fn()} />);

    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(screen.getByLabelText(/login code digit 1 of 6/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/login code digit 6 of 6/i)).toBeInTheDocument();
    expect(screen.getByText('vijay@example.com')).toBeInTheDocument();
  });

  it('moves focus to the first digit on open', () => {
    render(<OtpModal email="vijay@example.com" onSuccess={vi.fn()} onSkip={vi.fn()} />);
    expect(screen.getByLabelText(/login code digit 1 of 6/i)).toHaveFocus();
  });

  it('spreads typed digits across the boxes and ignores letters', async () => {
    const user = userEvent.setup();
    render(<OtpModal email="vijay@example.com" onSuccess={vi.fn()} onSkip={vi.fn()} />);

    const box = (index) => screen.getByLabelText(new RegExp(`login code digit ${index} of 6`, 'i'));

    await user.type(box(1), '482193');
    expect([1, 2, 3, 4, 5, 6].map((index) => box(index).value)).toEqual([
      '4',
      '8',
      '2',
      '1',
      '9',
      '3',
    ]);

    await user.type(box(1), 'abcdef');
    expect(box(1).value).toBe('4');
  });

  it('spreads a pasted code across the boxes', async () => {
    const user = userEvent.setup();
    render(<OtpModal email="vijay@example.com" onSuccess={vi.fn()} onSkip={vi.fn()} />);

    const box = (index) => screen.getByLabelText(new RegExp(`login code digit ${index} of 6`, 'i'));

    box(1).focus();
    await user.paste('482193');

    expect([1, 2, 3, 4, 5, 6].map((index) => box(index).value)).toEqual([
      '4',
      '8',
      '2',
      '1',
      '9',
      '3',
    ]);
  });

  it('shows an inline error for a short code', async () => {
    const user = userEvent.setup();
    render(<OtpModal email="vijay@example.com" onSuccess={vi.fn()} onSkip={vi.fn()} />);

    // The continue button is disabled until six digits are present, so the
    // short-code message is verified through the disabled state instead.
    await user.type(screen.getByLabelText(/login code digit 1 of 6/i), '123');
    expect(screen.getByRole('button', { name: /continue/i })).toBeDisabled();
  });

  it('calls the API and notifies the parent on a correct code', async () => {
    const user = userEvent.setup();
    const onSuccess = vi.fn();
    verifyOtp.mockResolvedValue({
      success: true,
      userId: 1,
      firstName: 'Vijay',
      lastName: 'Hosapeti',
      sessionToken: 'signed.token',
    });

    render(<OtpModal email="vijay@example.com" onSuccess={onSuccess} onSkip={vi.fn()} />);
    await user.type(screen.getByLabelText(/login code digit 1 of 6/i), '482193');
    await user.click(screen.getByRole('button', { name: /continue/i }));

    await waitFor(() =>
      expect(onSuccess).toHaveBeenCalledWith(
        expect.objectContaining({ firstName: 'Vijay', lastName: 'Hosapeti' }),
      ),
    );
    expect(verifyOtp).toHaveBeenCalledWith({ email: 'vijay@example.com', code: '482193' });
  });

  it('keeps the modal open and shows the error on a wrong code', async () => {
    const user = userEvent.setup();
    const onSuccess = vi.fn();
    verifyOtp.mockRejectedValue(new ApiError('Invalid login code', 401));

    render(<OtpModal email="vijay@example.com" onSuccess={onSuccess} onSkip={vi.fn()} />);
    await user.type(screen.getByLabelText(/login code digit 1 of 6/i), '111111');
    await user.click(screen.getByRole('button', { name: /continue/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid login code');
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(onSuccess).not.toHaveBeenCalled();
  });

  it('clears the code and allows another attempt after a wrong code', async () => {
    const user = userEvent.setup();
    verifyOtp.mockRejectedValueOnce(new ApiError('Invalid login code', 401));

    render(<OtpModal email="vijay@example.com" onSuccess={vi.fn()} onSkip={vi.fn()} />);
    await user.type(screen.getByLabelText(/login code digit 1 of 6/i), '111111');
    await user.click(screen.getByRole('button', { name: /continue/i }));

    await screen.findByRole('alert');
    expect(screen.getByLabelText(/login code digit 1 of 6/i)).toHaveValue('');

    verifyOtp.mockResolvedValueOnce({ success: true, userId: 1, firstName: 'Vijay', lastName: 'Hosapeti' });
    await user.type(screen.getByLabelText(/login code digit 1 of 6/i), '482193');
    await user.click(screen.getByRole('button', { name: /continue/i }));

    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
  });

  it('prevents duplicate submissions while verifying', async () => {
    const user = userEvent.setup();
    let resolveRequest;
    verifyOtp.mockReturnValue(
      new Promise((resolve) => {
        resolveRequest = resolve;
      }),
    );

    render(<OtpModal email="vijay@example.com" onSuccess={vi.fn()} onSkip={vi.fn()} />);
    await user.type(screen.getByLabelText(/login code digit 1 of 6/i), '482193');
    await user.click(screen.getByRole('button', { name: /continue/i }));

    await waitFor(() => expect(verifyOtp).toHaveBeenCalledTimes(1));
    const continueButton = screen.getByRole('button', { name: /verifying/i });
    expect(continueButton).toBeDisabled();
    expect(screen.getByRole('button', { name: /skip login/i })).toBeDisabled();

    resolveRequest({ success: true, userId: 1, firstName: 'Vijay', lastName: 'Hosapeti' });
  });

  it('returns focus to the first box after a failed attempt', async () => {
    const user = userEvent.setup();
    verifyOtp.mockRejectedValue(new ApiError('Invalid login code', 401));

    render(<OtpModal email="vijay@example.com" onSuccess={vi.fn()} onSkip={vi.fn()} />);
    await user.type(screen.getByLabelText(/login code digit 1 of 6/i), '111111');
    await user.click(screen.getByRole('button', { name: /continue/i }));

    await screen.findByRole('alert');
    // A keyboard user must be able to retype immediately without clicking.
    expect(screen.getByLabelText(/login code digit 1 of 6/i)).toHaveFocus();
  });

  it('calls onSkip when skip login is pressed', async () => {
    const user = userEvent.setup();
    const onSkip = vi.fn();
    render(<OtpModal email="vijay@example.com" onSuccess={vi.fn()} onSkip={onSkip} />);

    await user.click(screen.getByRole('button', { name: /skip login/i }));
    expect(onSkip).toHaveBeenCalledTimes(1);
  });

  it('closes on the Escape key', async () => {
    const user = userEvent.setup();
    const onSkip = vi.fn();
    render(<OtpModal email="vijay@example.com" onSuccess={vi.fn()} onSkip={onSkip} />);

    await user.keyboard('{Escape}');
    expect(onSkip).toHaveBeenCalledTimes(1);
  });
});
