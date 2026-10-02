import { describe, expect, it } from "vitest";
import { canChangeRole, canManage, canManageTeam, invitableRoles } from "./team";

describe("team rules (UX mirror of the backend)", () => {
  it("lets the owner invite admins and members, admins only members, members nobody", () => {
    expect(invitableRoles("OWNER")).toEqual(["MEMBER", "ADMIN"]);
    expect(invitableRoles("ADMIN")).toEqual(["MEMBER"]);
    expect(invitableRoles("MEMBER")).toEqual([]);
    expect(canManageTeam("MEMBER")).toBe(false);
  });

  it("never offers actions on the owner", () => {
    expect(canManage("OWNER", "OWNER")).toBe(false);
    expect(canManage("ADMIN", "OWNER")).toBe(false);
    expect(canChangeRole("OWNER", "OWNER")).toBe(false);
  });

  it("limits admins to members and role changes to the owner", () => {
    expect(canManage("ADMIN", "MEMBER")).toBe(true);
    expect(canManage("ADMIN", "ADMIN")).toBe(false);
    expect(canChangeRole("ADMIN", "MEMBER")).toBe(false);
    expect(canChangeRole("OWNER", "MEMBER")).toBe(true);
  });
});
