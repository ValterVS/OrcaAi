import type { CurrentAccount } from "@/lib/api/auth";
import { roleLabel } from "@/lib/roles";
import { LogoutButton } from "./LogoutButton";

export function AppHeader({ account }: { account: CurrentAccount }) {
  return (
    <header className="app-header">
      <div className="app-header-brand">
        <span className="brand">Orça Aí</span>
        <span className="app-header-organization">{account.organizationName}</span>
      </div>
      <div className="app-header-user">
        <div className="app-header-identity">
          <span className="app-header-name">{account.userName}</span>
          <span className="app-header-role">{roleLabel(account.role)}</span>
        </div>
        <LogoutButton />
      </div>
    </header>
  );
}
