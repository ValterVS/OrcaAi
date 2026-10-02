import type { Role } from "./auth";
import { sendJson } from "./client";

export type Member = {
  id: string;
  name: string;
  email: string;
  role: Role;
  active: boolean;
  /** Sent back as If-Match when changing this member. */
  version: number;
};

export type Invitation = {
  id: string;
  email: string;
  role: Role;
  expiresAt: string;
  expired: boolean;
  lastSentAt: string;
};

export type AssignableRole = "ADMIN" | "MEMBER";

function ifMatch(member: Member): Record<string, string> {
  return { "If-Match": `"${member.version}"` };
}

function memberPath(member: Member, action: string): string {
  return `/team/members/${encodeURIComponent(member.id)}/${action}`;
}

export function inviteMember(email: string, role: AssignableRole): Promise<Invitation> {
  return sendJson<Invitation>("POST", "/team/invitations", { email, role });
}

export function resendInvitation(id: string): Promise<Invitation> {
  return sendJson<Invitation>("POST", `/team/invitations/${encodeURIComponent(id)}/resend`);
}

export function revokeInvitation(id: string): Promise<void> {
  return sendJson<void>("POST", `/team/invitations/${encodeURIComponent(id)}/revoke`);
}

export function changeMemberRole(member: Member, role: AssignableRole): Promise<Member> {
  return sendJson<Member>("PUT", memberPath(member, "role"), { role }, ifMatch(member));
}

export function deactivateMember(member: Member): Promise<Member> {
  return sendJson<Member>("POST", memberPath(member, "deactivate"), undefined, ifMatch(member));
}

export function reactivateMember(member: Member): Promise<Member> {
  return sendJson<Member>("POST", memberPath(member, "reactivate"), undefined, ifMatch(member));
}

/** Public: email, organization and role come from the invitation on the server. */
export function acceptInvitation(token: string, name: string, password: string): Promise<void> {
  return sendJson<void>("POST", "/invitations/accept", { token, name, password });
}
