import { cookies } from "next/headers";
import type { CurrentAccount } from "./auth";

// Server-side only. BACKEND_URL is not exposed to the browser (no NEXT_PUBLIC_ prefix).
const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";
const SESSION_COOKIE = "SESSION";

/**
 * Asks the backend who the session belongs to, forwarding only the session cookie.
 * Returns null when there is no valid session; other failures throw.
 */
export async function getCurrentAccount(): Promise<CurrentAccount | null> {
  const session = (await cookies()).get(SESSION_COOKIE);
  if (!session) {
    return null;
  }

  const response = await fetch(`${backendUrl}/api/auth/me`, {
    headers: { Cookie: `${SESSION_COOKIE}=${session.value}`, Accept: "application/json" },
    cache: "no-store",
  });
  if (response.status === 401) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`Could not load the current account (status ${response.status})`);
  }
  return (await response.json()) as CurrentAccount;
}
