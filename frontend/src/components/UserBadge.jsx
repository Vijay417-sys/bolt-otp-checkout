/**
 * Shows the authenticated user's name at the top of the checkout form.
 * Renders nothing when the user is checking out as a guest.
 */
export default function UserBadge({ user }) {
  if (!user) return null;

  const initials = `${user.firstName?.[0] || ''}${user.lastName?.[0] || ''}`.toUpperCase();

  return (
    <div className="flex items-center gap-3 rounded-xl border border-green-200 bg-green-50 px-4 py-3">
      <span
        aria-hidden="true"
        className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-green-600 text-sm font-semibold text-white"
      >
        {initials}
      </span>
      <div className="min-w-0">
        <p className="text-sm font-semibold text-green-900">
          Welcome, {user.firstName} {user.lastName}
        </p>
        <p className="truncate text-xs text-green-700">Signed in with your login code</p>
      </div>
    </div>
  );
}
