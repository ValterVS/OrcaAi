import { sendJson } from "./client";

export type CustomerStatus = "ACTIVE" | "ARCHIVED" | "ALL";

export type Customer = {
  id: string;
  name: string;
  phone: string | null;
  email: string | null;
  notes: string | null;
  archived: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type CustomerPage = {
  items: Customer[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
};

export type CustomerInput = {
  name: string;
  phone: string;
  email: string;
  notes: string;
};

function customerPath(id: string, action = ""): string {
  return `/customers/${encodeURIComponent(id)}${action}`;
}

export function createCustomer(input: CustomerInput): Promise<Customer> {
  return sendJson<Customer>("POST", "/customers", input);
}

/** Saves only if nobody changed the customer since {@code version} was read (409 otherwise). */
export function updateCustomer(id: string, version: number, input: CustomerInput): Promise<Customer> {
  return sendJson<Customer>("PUT", customerPath(id), input, { "If-Match": `"${version}"` });
}

export function archiveCustomer(id: string): Promise<Customer> {
  return sendJson<Customer>("POST", customerPath(id, "/archive"));
}

export function restoreCustomer(id: string): Promise<Customer> {
  return sendJson<Customer>("POST", customerPath(id, "/restore"));
}
