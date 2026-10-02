import { apiFetch } from "./client";

export type Role = "OWNER" | "ADMIN" | "MEMBER";

export type CurrentAccount = {
  userId: string;
  userName: string;
  role: Role;
  organizationId: string;
  organizationName: string;
};

export type SignupData = {
  companyName: string;
  ownerName: string;
  email: string;
  password: string;
};

// Issues the XSRF-TOKEN cookie that every state-changing request must echo back.
function ensureCsrfToken(): Promise<void> {
  return apiFetch<void>("/auth/csrf");
}

export async function signup(data: SignupData): Promise<void> {
  await ensureCsrfToken();
  await apiFetch<void>("/auth/signup", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
}

export async function login(email: string, password: string): Promise<void> {
  await ensureCsrfToken();
  await apiFetch<void>("/auth/login", {
    method: "POST",
    body: new URLSearchParams({ email, password }),
  });
}

export async function logout(): Promise<void> {
  await ensureCsrfToken();
  await apiFetch<void>("/auth/logout", { method: "POST" });
}
