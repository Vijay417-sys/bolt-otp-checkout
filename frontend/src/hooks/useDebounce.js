import { useEffect, useState } from 'react';

/**
 * Debounces a rapidly changing value.
 *
 * Used by the checkout form so that the recognition API is called once the user
 * pauses typing, rather than on every keystroke.
 *
 * @param {*} value value to debounce
 * @param {number} delay milliseconds to wait before the value is published
 * @returns {*} the debounced value
 */
export function useDebounce(value, delay = 500) {
  const [debouncedValue, setDebouncedValue] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedValue(value), delay);
    // Clearing on every change is what makes this a debounce: the pending
    // timer is discarded whenever the value changes again.
    return () => clearTimeout(timer);
  }, [value, delay]);

  return debouncedValue;
}

export default useDebounce;
