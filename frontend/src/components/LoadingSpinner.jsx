/**
 * Accessible loading indicator.
 * Announced to screen readers via role="status" and labelled for assistive tech.
 */
export default function LoadingSpinner({ label = 'Loading', className = '' }) {
  return (
    <span
      role="status"
      aria-label={label}
      className={`inline-flex items-center gap-2 text-sm text-slate-500 ${className}`}
    >
      <SpinnerIcon />
      <span>{label}</span>
    </span>
  );
}

/** Icon-only variant for use inside buttons. */
export function Spinner({ label = 'Loading', className = '' }) {
  return (
    <span role="status" aria-label={label} className={`inline-flex ${className}`}>
      <SpinnerIcon />
    </span>
  );
}

function SpinnerIcon() {
  return (
    <svg
      className="h-4 w-4 animate-spin text-current"
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
    >
      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
      <path className="opacity-90" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" />
    </svg>
  );
}
