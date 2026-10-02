import type { ReactNode } from "react";
import { AuthCard } from "@/components/auth/AuthCard";

// Pages opened from email links: unlike /login and /signup, they work with or without a session.
export default function AccountLayout({ children }: { children: ReactNode }) {
  return <AuthCard>{children}</AuthCard>;
}
