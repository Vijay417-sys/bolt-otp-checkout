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
