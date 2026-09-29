const TONE_CLASSES = {
  success: 'border-green-200 bg-green-50 text-green-900',
  error: 'border-red-200 bg-red-50 text-red-900',
  info: 'border-blue-200 bg-blue-50 text-blue-900',
};

const ICONS = {
  success: (
    <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 12.75l6 6 9-13.5" />
  ),
  error: (
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      d="M12 9v3.75m0 3.75h.008v.008H12v-.008zM10.29 3.86l-8.02 13.89A1.5 1.5 0 003.68 20.25h16.64a1.5 1.5 0 001.41-2.5L13.71 3.86a1.5 1.5 0 00-2.42 0z"
    />
  ),
  info: (
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      d="M11.25 11.25l.041-.02a.75.75 0 011.063.852l-.708 2.836a.75.75 0 001.063.853l.041-.021M21 12a9 9 0 11-18 0 9 9 0 0118 0zm-9-3.75h.008v.008H12V8.25z"
    />
  ),
};

/**
 * Transient status message. Errors use role="alert" (assertive) while other
 * tones use role="status" (polite) so screen readers announce them promptly.
 */
export default function Toast({ message, type = 'info', onDismiss }) {
  if (!message) return null;

  return (
    <div
      role={type === 'error' ? 'alert' : 'status'}
      aria-live={type === 'error' ? 'assertive' : 'polite'}
      className={`flex items-start gap-2.5 rounded-lg border px-3.5 py-3 text-sm animate-fade-in ${
        TONE_CLASSES[type] || TONE_CLASSES.info
      }`}
    >
      <svg
        className="mt-0.5 h-4 w-4 shrink-0"
        fill="none"
        viewBox="0 0 24 24"
        strokeWidth={1.8}
        stroke="currentColor"
        aria-hidden="true"
      >
        {ICONS[type] || ICONS.info}
      </svg>
      <p className="flex-1">{message}</p>
      {onDismiss && (
        <button
          type="button"
          onClick={onDismiss}
          className="-mr-1 rounded p-1 text-current/70 transition hover:bg-black/5 hover:text-current"
          aria-label="Dismiss message"
        >
          <svg className="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2.5} aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      )}
    </div>
  );
}
