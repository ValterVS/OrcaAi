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

/**
 * Changes send back the ETag the customer was read with (If-Match); if someone changed it since,
 * the backend answers 412 and nothing is overwritten.
 */
export function updateCustomer(id: string, etag: string, input: CustomerInput): Promise<Customer> {
  return sendJson<Customer>("PUT", customerPath(id), input, { "If-Match": etag });
}

export function archiveCustomer(id: string, etag: string): Promise<Customer> {
  return sendJson<Customer>("POST", customerPath(id, "/archive"), undefined, { "If-Match": etag });
}

export function restoreCustomer(id: string, etag: string): Promise<Customer> {
  return sendJson<Customer>("POST", customerPath(id, "/restore"), undefined, { "If-Match": etag });
}
