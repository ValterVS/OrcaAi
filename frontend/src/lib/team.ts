import type { Role } from "./api/auth";
import type { AssignableRole } from "./api/team";

// UX only: these mirror the backend rules to hide actions that would be refused anyway.

export function invitableRoles(actor: Role): AssignableRole[] {
  if (actor === "OWNER") {
    return ["MEMBER", "ADMIN"];
  }
  return actor === "ADMIN" ? ["MEMBER"] : [];
}

export function canManageTeam(actor: Role): boolean {
  return actor === "OWNER" || actor === "ADMIN";
}

/** Deactivate, reactivate, resend or revoke for a user or invitation with {@code target} role. */
export function canManage(actor: Role, target: Role): boolean {
  if (actor === "OWNER") {
    return target !== "OWNER";
  }
  return actor === "ADMIN" && target === "MEMBER";
}

export function canChangeRole(actor: Role, target: Role): boolean {
  return actor === "OWNER" && target !== "OWNER";
}
