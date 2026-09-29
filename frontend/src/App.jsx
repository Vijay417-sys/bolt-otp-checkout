import { useState } from 'react';
import RegistrationForm from './components/RegistrationForm';
import CheckoutForm from './components/CheckoutForm';

const TABS = [
  { id: 'register', label: 'Register' },
  { id: 'checkout', label: 'Checkout' },
];

/**
 * Application shell. Registration and checkout are two views of the same flow:
 * registering hands the email over to checkout so recognition can pick it up
 * immediately.
 */
export default function App() {
  const [view, setView] = useState('register');
  const [checkoutEmail, setCheckoutEmail] = useState('');

  const goToCheckout = (email = '') => {
    setCheckoutEmail(email);
    setView('checkout');
  };

  return (
    <div className="flex min-h-screen flex-col">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-3xl flex-wrap items-center justify-between gap-3 px-4 py-4 sm:px-6">
          <div className="flex items-center gap-2.5">
            <span
              aria-hidden="true"
              className="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-600 text-sm font-bold text-white"
            >
              B
            </span>
            <span className="text-base font-semibold tracking-tight text-slate-900">Bolt Checkout</span>
          </div>

          <nav aria-label="Main">
            <div className="flex gap-1 rounded-lg bg-slate-100 p-1">
              {TABS.map((tab) => (
                <button
                  key={tab.id}
                  type="button"
                  onClick={() => (tab.id === 'checkout' ? goToCheckout() : setView('register'))}
                  aria-current={view === tab.id ? 'page' : undefined}
                  className={`rounded-md px-3.5 py-1.5 text-sm font-medium transition ${
                    view === tab.id
                      ? 'bg-white text-slate-900 shadow-sm'
                      : 'text-slate-600 hover:text-slate-900'
                  }`}
                >
                  {tab.label}
                </button>
              ))}
            </div>
          </nav>
        </div>
      </header>

      <main className="flex-1 px-4 py-8 sm:px-6 sm:py-12">
        <div className="mx-auto max-w-lg rounded-2xl border border-slate-200 bg-white p-6 shadow-sm sm:p-8">
          {view === 'register' ? (
            <RegistrationForm onRegistered={goToCheckout} />
          ) : (
            <CheckoutForm
              key={checkoutEmail || 'blank'}
              prefillEmail={checkoutEmail}
              onCheckoutSuccess={() => {}}
            />
          )}
        </div>
      </main>

      <footer className="border-t border-slate-200 bg-white px-4 py-4 text-center text-xs text-slate-500">
        Bolt OTP Checkout &middot; React + Spring Boot + MySQL
      </footer>
    </div>
  );
}
