import type { Role } from "./api/auth";

const ROLE_LABELS: Record<Role, string> = {
  OWNER: "Proprietário",
  ADMIN: "Administrador",
  MEMBER: "Membro",
};

export function roleLabel(role: Role): string {
  return ROLE_LABELS[role];
}

/** UX only: the backend refuses archive and restore for other roles. */
export function canArchiveCustomers(role: Role): boolean {
  return role === "OWNER" || role === "ADMIN";
}
