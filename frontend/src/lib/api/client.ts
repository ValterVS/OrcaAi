const CSRF_COOKIE = "XSRF-TOKEN";
const CSRF_HEADER = "X-XSRF-TOKEN";
const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);

export type Problem = {
  status: number;
  title?: string;
  detail?: string;
  errors?: { field: string; message: string }[];
};

export class ApiError extends Error {
  constructor(readonly problem: Problem) {
    super(problem.detail ?? `Request failed with status ${problem.status}`);
    this.name = "ApiError";
  }

  get status() {
    return this.problem.status;
  }
}

function readCookie(name: string): string | undefined {
  if (typeof document === "undefined") {
    return undefined;
  }
  const prefix = `${name}=`;
  const match = document.cookie.split("; ").find((cookie) => cookie.startsWith(prefix));
  return match ? decodeURIComponent(match.slice(prefix.length)) : undefined;
}

async function toProblem(response: Response): Promise<Problem> {
  try {
    return { ...(await response.json()), status: response.status };
  } catch {
    return { status: response.status };
  }
}

// Browser-only client: authentication relies on the HttpOnly session cookie sent by the browser.
export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");

  if (!SAFE_METHODS.has(method)) {
    const token = readCookie(CSRF_COOKIE);
    if (token) {
      headers.set(CSRF_HEADER, token);
    }
  }

  const response = await fetch(`/api${path}`, {
    ...init,
    method,
    headers,
    credentials: "same-origin",
  });

  if (!response.ok) {
    throw new ApiError(await toProblem(response));
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

/** For state-changing calls outside the auth flow: makes sure a CSRF token exists, then sends JSON. */
export async function sendJson<T>(
  method: "POST" | "PUT",
  path: string,
  body?: unknown,
  headers?: Record<string, string>,
): Promise<T> {
  if (!readCookie(CSRF_COOKIE)) {
    await apiFetch<void>("/auth/csrf");
  }
  return apiFetch<T>(path, {
    method,
    headers: { ...(body === undefined ? {} : { "Content-Type": "application/json" }), ...headers },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}
