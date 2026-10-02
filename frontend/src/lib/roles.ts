import type { Role } from "./api/auth";

const ROLE_LABELS: Record<Role, string> = {
  OWNER: "Proprietário",
  ADMIN: "Administrador",
  MEMBER: "Membro",
};

export function roleLabel(role: Role): string {
  return ROLE_LABELS[role];
}
