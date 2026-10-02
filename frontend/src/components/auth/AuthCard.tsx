import type { ReactNode } from "react";

export function AuthCard({ children }: { children: ReactNode }) {
  return (
    <main className="auth-page">
      <div className="auth-card">
        <p className="brand">Orça Aí</p>
        {children}
      </div>
    </main>
  );
}
