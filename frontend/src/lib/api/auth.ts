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

async function postJson(path: string, body: unknown): Promise<void> {
  await ensureCsrfToken();
  await apiFetch<unknown>(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export async function signup(data: SignupData): Promise<void> {
  await postJson("/auth/signup", data);
}

export async function resendVerification(email: string): Promise<void> {
  await postJson("/auth/resend-verification", { email });
}

export async function verifyEmail(token: string): Promise<void> {
  await postJson("/auth/verify-email", { token });
}

export async function requestPasswordReset(email: string): Promise<void> {
  await postJson("/auth/forgot-password", { email });
}

export async function resetPassword(token: string, password: string): Promise<void> {
  await postJson("/auth/reset-password", { token, password });
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
