import { vi } from "vitest";
import { ApiError, type Problem } from "@/lib/api/client";

export const router = {
  replace: vi.fn(),
  push: vi.fn(),
  refresh: vi.fn(),
};

export function apiError(problem: Problem): ApiError {
  return new ApiError(problem);
}

/** A promise the test resolves explicitly, to observe loading states. */
export function deferred() {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
