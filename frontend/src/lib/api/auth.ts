import { apiFetch } from "./client";

export type Role = "OWNER" | "ADMIN" | "MEMBER";

export type CurrentUser = {
  id: string;
  organizationId: string;
  email: string;
  role: Role;
};

export async function login(email: string, password: string): Promise<void> {
  await apiFetch<void>("/auth/csrf");
  await apiFetch<void>("/auth/login", {
    method: "POST",
    body: new URLSearchParams({ email, password }),
  });
}

export function logout(): Promise<void> {
  return apiFetch<void>("/auth/logout", { method: "POST" });
}

export function getCurrentUser(): Promise<CurrentUser> {
  return apiFetch<CurrentUser>("/auth/me");
}
