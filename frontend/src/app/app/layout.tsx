import { redirect } from "next/navigation";
import type { ReactNode } from "react";
import { AppHeader } from "@/components/app/AppHeader";
import { getCurrentAccount } from "@/lib/api/server";

/**
 * Redirects visitors without a session. This is navigation, not authorization: layouts are not
 * re-rendered on every client navigation, and the backend checks the session on every API call.
 */
export default async function AppLayout({ children }: { children: ReactNode }) {
  const account = await getCurrentAccount();
  if (!account) {
    redirect("/login");
  }

  return (
    <div className="app-shell">
      <AppHeader account={account} />
      <main className="app-main">{children}</main>
    </div>
  );
}
