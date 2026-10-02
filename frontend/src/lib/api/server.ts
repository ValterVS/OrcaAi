import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { cache } from "react";
import type { CurrentAccount } from "./auth";
import type { Customer, CustomerPage, CustomerStatus } from "./customers";

// Server-side only. BACKEND_URL is not exposed to the browser (no NEXT_PUBLIC_ prefix).
const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";
const SESSION_COOKIE = "SESSION";

/** GET on the backend forwarding only the session cookie; null when there is no session cookie. */
async function backendGet(path: string): Promise<Response | null> {
  const session = (await cookies()).get(SESSION_COOKIE);
  if (!session) {
    return null;
  }
  return fetch(`${backendUrl}/api${path}`, {
    headers: { Cookie: `${SESSION_COOKIE}=${session.value}`, Accept: "application/json" },
    cache: "no-store",
  });
}

async function readOrFail<T>(response: Response | null, what: string): Promise<T> {
  if (!response || response.status === 401) {
    redirect("/login");
  }
  if (!response.ok) {
    throw new Error(`Could not load ${what} (status ${response.status})`);
  }
  return (await response.json()) as T;
}

/**
 * Who the session belongs to, or null without a valid session. Cached per request, so layouts and
 * pages can both ask without a second backend call.
 */
export const getCurrentAccount = cache(async (): Promise<CurrentAccount | null> => {
  const response = await backendGet("/auth/me");
  if (!response || response.status === 401) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`Could not load the current account (status ${response.status})`);
  }
  return (await response.json()) as CurrentAccount;
});

export async function getCustomers(query: { status: CustomerStatus; q: string; page: number }): Promise<CustomerPage> {
  const params = new URLSearchParams({ status: query.status, page: String(query.page) });
  if (query.q) {
    params.set("q", query.q);
  }
  return readOrFail<CustomerPage>(await backendGet(`/customers?${params}`), "customers");
}

/** Null when the customer does not exist for this organization (or the id is malformed). */
export async function getCustomer(id: string): Promise<Customer | null> {
  const response = await backendGet(`/customers/${encodeURIComponent(id)}`);
  if (response && (response.status === 404 || response.status === 400)) {
    return null;
  }
  return readOrFail<Customer>(response, "customer");
}
