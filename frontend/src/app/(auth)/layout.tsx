import { redirect } from "next/navigation";
import type { ReactNode } from "react";
import { AuthCard } from "@/components/auth/AuthCard";
import type { CurrentAccount } from "@/lib/api/auth";
import { getCurrentAccount } from "@/lib/api/server";

export default async function AuthLayout({ children }: { children: ReactNode }) {
  let account: CurrentAccount | null = null;
  try {
    account = await getCurrentAccount();
  } catch {
    // Backend unavailable: still show the form instead of bouncing between /login and /app.
  }
  if (account) {
    redirect("/app");
  }

  return <AuthCard>{children}</AuthCard>;
}
