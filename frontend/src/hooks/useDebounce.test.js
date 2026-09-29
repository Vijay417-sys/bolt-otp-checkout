import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import useDebounce from './useDebounce';

describe('useDebounce', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('publishes the initial value immediately', () => {
    const { result } = renderHook(() => useDebounce('first', 500));
    expect(result.current).toBe('first');
  });

  it('does not publish a new value before the delay elapses', () => {
    const { result, rerender } = renderHook(({ value }) => useDebounce(value, 500), {
      initialProps: { value: 'a' },
    });

    rerender({ value: 'ab' });
    act(() => {
      vi.advanceTimersByTime(499);
    });
    expect(result.current).toBe('a');
  });

  it('publishes the value once the delay elapses', () => {
    const { result, rerender } = renderHook(({ value }) => useDebounce(value, 500), {
      initialProps: { value: 'a' },
    });

    rerender({ value: 'ab' });
    act(() => {
      vi.advanceTimersByTime(500);
    });
    expect(result.current).toBe('ab');
  });

  it('settles on the same value again when it is cleared and retyped', () => {
    // Regression test: React cannot distinguish "set to the value it already
    // holds" from "never changed", so a settle callback is what lets a consumer
    // re-run work when the user clears the field and types the same value.
    const onSettle = vi.fn();
    const { rerender } = renderHook(({ value }) => useDebounce(value, 500, onSettle), {
      initialProps: { value: 'vijay@example.com' },
    });

    onSettle.mockClear();

    for (const value of ['', 'v', 'vi', 'vijay@example.com']) {
      rerender({ value });
      act(() => {
        vi.advanceTimersByTime(100);
      });
    }
    act(() => {
      vi.advanceTimersByTime(500);
    });

    expect(onSettle).toHaveBeenCalledWith('vijay@example.com');
  });

  it('does not call the settle callback before the delay elapses', () => {
    const onSettle = vi.fn();
    const { rerender } = renderHook(({ value }) => useDebounce(value, 500, onSettle), {
      initialProps: { value: 'a' },
    });
    onSettle.mockClear();

    rerender({ value: 'ab' });
    act(() => {
      vi.advanceTimersByTime(499);
    });
    expect(onSettle).not.toHaveBeenCalled();
  });

  it('does not restart the timer when only the callback identity changes', () => {
    const onSettle = vi.fn();
    // A fresh callback on every render is the normal case; it must not reset the
    // pending timer, or the debounce would never fire in a real component.
    const { result, rerender } = renderHook(
      ({ value }) => useDebounce(value, 500, vi.fn()),
      { initialProps: { value: 'a' } }
    );

    rerender({ value: 'ab' });
    rerender({ value: 'ab' });
    act(() => {
      vi.advanceTimersByTime(300);
    });
    rerender({ value: 'ab' });
    act(() => {
      vi.advanceTimersByTime(200);
    });

    expect(result.current).toBe('ab');
  });

  it('only publishes the final value across rapid changes', () => {
    const { result, rerender } = renderHook(({ value }) => useDebounce(value, 500), {
      initialProps: { value: '' },
    });

    // Simulates fast typing: the intermediate values must never be published.
    for (const value of ['v', 'vi', 'vij', 'vija', 'vijay@', 'vijay@example.com']) {
      rerender({ value });
      act(() => {
        vi.advanceTimersByTime(100);
      });
    }

    act(() => {
      vi.advanceTimersByTime(500);
    });
    expect(result.current).toBe('vijay@example.com');
  });
});
