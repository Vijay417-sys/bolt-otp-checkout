import { useEffect, useRef, useState } from 'react';

/**
 * Debounces a rapidly changing value.
 *
 * Used by the checkout form so that the recognition API is called once the user
 * pauses typing, rather than on every keystroke.
 *
 * @param {*} value value to debounce
 * @param {number} delay milliseconds to wait before the value is published
 * @param {(value: *) => void} [onSettle] called every time the value settles,
 *   even when it settles on the value it already held. Needed because returning
 *   the same value twice is indistinguishable from not having changed at all:
 *   without this, clearing the field and retyping the same address would never
 *   re-trigger recognition, leaving the form permanently inactive.
 * @returns {*} the debounced value
 */
export function useDebounce(value, delay = 500, onSettle) {
  const [debouncedValue, setDebouncedValue] = useState(value);

  // Held in a ref so that passing a fresh callback on every render does not
  // restart the timer and defeat the debounce.
  const onSettleRef = useRef(onSettle);
  onSettleRef.current = onSettle;

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedValue(value);
      onSettleRef.current?.(value);
    }, delay);
    // Clearing on every change is what makes this a debounce: the pending
    // timer is discarded whenever the value changes again.
    return () => clearTimeout(timer);
  }, [value, delay]);

  return debouncedValue;
}

export default useDebounce;
