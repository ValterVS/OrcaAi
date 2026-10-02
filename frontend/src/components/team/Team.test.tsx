import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import {
  changeMemberRole,
  deactivateMember,
  inviteMember,
  reactivateMember,
  resendInvitation,
  revokeInvitation,
  type Invitation,
  type Member,
} from "@/lib/api/team";
import { apiError, router } from "@/test/mocks";
import { InviteForm } from "./InviteForm";
import { PendingInvitations } from "./PendingInvitations";
import { TeamMembers } from "./TeamMembers";

vi.mock("next/navigation", () => ({ useRouter: () => router }));
vi.mock("@/lib/api/team", () => ({
  changeMemberRole: vi.fn(),
  deactivateMember: vi.fn(),
  reactivateMember: vi.fn(),
  inviteMember: vi.fn(),
  resendInvitation: vi.fn(),
  revokeInvitation: vi.fn(),
}));

const owner: Member = { id: "u-owner", name: "Dona", email: "dona@example.com", role: "OWNER", active: true, version: 0 };
const admin: Member = { id: "u-admin", name: "Paula", email: "paula@example.com", role: "ADMIN", active: true, version: 2 };
const member: Member = { id: "u-member", name: "Beto", email: "beto@example.com", role: "MEMBER", active: true, version: 1 };
const inactive: Member = { ...member, id: "u-off", name: "Caio", active: false, version: 4 };
const team = [owner, admin, member, inactive];

function row(name: string) {
  return within(screen.getByRole("cell", { name: new RegExp(`^${name}`) }).closest("tr") as HTMLElement);
}

describe("TeamMembers", () => {
  it("shows name, email, role in Portuguese and status, marking the current user", () => {
    render(<TeamMembers members={team} actorId={owner.id} actorRole="OWNER" />);

    expect(row("Dona").getByText("Proprietário")).toBeTruthy();
    expect(row("Dona").getByText("Você")).toBeTruthy();
    expect(row("Paula").getByText("Administrador")).toBeTruthy();
    expect(row("Beto").getByText("Membro")).toBeTruthy();
    expect(row("Caio").getByText("Inativo")).toBeTruthy();
    expect(row("Beto").getByText("beto@example.com")).toBeTruthy();
  });

  it("gives the owner role and status actions on everyone but the owner", () => {
    render(<TeamMembers members={team} actorId={owner.id} actorRole="OWNER" />);

    expect(row("Dona").queryAllByRole("button")).toHaveLength(0);
    expect(row("Paula").getByRole("button", { name: "Tornar membro" })).toBeTruthy();
    expect(row("Paula").getByRole("button", { name: "Desativar" })).toBeTruthy();
    expect(row("Beto").getByRole("button", { name: "Tornar administrador" })).toBeTruthy();
    expect(row("Caio").getByRole("button", { name: "Reativar" })).toBeTruthy();
  });

  it("lets an admin manage members only, without role changes", () => {
    render(<TeamMembers members={team} actorId={admin.id} actorRole="ADMIN" />);

    expect(row("Dona").queryAllByRole("button")).toHaveLength(0);
    expect(row("Paula").queryAllByRole("button")).toHaveLength(0);
    expect(row("Beto").getByRole("button", { name: "Desativar" })).toBeTruthy();
    expect(row("Beto").queryByRole("button", { name: /Tornar/ })).toBeNull();
  });

  it("shows no actions to a member", () => {
    render(<TeamMembers members={team} actorId={member.id} actorRole="MEMBER" />);

    expect(screen.queryAllByRole("button")).toHaveLength(0);
  });

  it("promotes, deactivates and reactivates with the row's version, then refreshes", async () => {
    vi.mocked(changeMemberRole).mockResolvedValue({ ...member, role: "ADMIN" });
    vi.mocked(deactivateMember).mockResolvedValue({ ...admin, active: false });
    vi.mocked(reactivateMember).mockResolvedValue({ ...inactive, active: true });
    render(<TeamMembers members={team} actorId={owner.id} actorRole="OWNER" />);
    const user = userEvent.setup();

    await user.click(row("Beto").getByRole("button", { name: "Tornar administrador" }));
    expect((await screen.findByRole("status")).textContent).toBe("Beto agora é Administrador.");
    expect(changeMemberRole).toHaveBeenCalledWith(member, "ADMIN");

    await user.click(row("Paula").getByRole("button", { name: "Desativar" }));
    await waitFor(() => expect(deactivateMember).toHaveBeenCalledWith(admin));
    await user.click(row("Caio").getByRole("button", { name: "Reativar" }));
    await waitFor(() => expect(reactivateMember).toHaveBeenCalledWith(inactive));
    expect(router.refresh).toHaveBeenCalledTimes(3);
  });

  it("explains a stale row and a refused action", async () => {
    const detail = "Este registro foi alterado por outra pessoa. Recarregue a página para ver a versão mais recente.";
    vi.mocked(deactivateMember).mockRejectedValueOnce(apiError({ status: 412, detail }));
    vi.mocked(deactivateMember).mockRejectedValueOnce(apiError({ status: 403 }));
    render(<TeamMembers members={team} actorId={owner.id} actorRole="OWNER" />);
    const user = userEvent.setup();

    await user.click(row("Beto").getByRole("button", { name: "Desativar" }));
    expect((await screen.findByRole("alert")).textContent).toBe(detail);
    await user.click(row("Beto").getByRole("button", { name: "Desativar" }));
    await waitFor(() =>
      expect(screen.getByRole("alert").textContent).toBe("Você não tem permissão para esta ação."),
    );
  });
});

describe("InviteForm", () => {
  it("offers admin and member to the owner but only member to an admin", () => {
    const { rerender } = render(<InviteForm actorRole="OWNER" />);
    const options = () => within(screen.getByLabelText("Papel")).getAllByRole("option").map((o) => o.textContent);
    expect(options()).toEqual(["Membro", "Administrador"]);

    rerender(<InviteForm actorRole="ADMIN" />);
    expect(options()).toEqual(["Membro"]);
  });

  it("validates the email and sends the invitation with the chosen role", async () => {
    vi.mocked(inviteMember).mockResolvedValue({
      id: "i1", email: "novo@example.com", role: "ADMIN", expiresAt: "2026-10-05T12:00:00Z", expired: false,
      lastSentAt: "2026-10-02T12:00:00Z",
    });
    render(<InviteForm actorRole="OWNER" />);
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: "Convidar membro" }));
    expect(screen.getByText("Informe o e-mail.")).toBeTruthy();

    await user.type(screen.getByLabelText("E-mail"), " novo@example.com ");
    await user.selectOptions(screen.getByLabelText("Papel"), "ADMIN");
    await user.click(screen.getByRole("button", { name: "Convidar membro" }));

    expect((await screen.findByRole("status")).textContent).toBe("Convite enviado para novo@example.com.");
    expect(inviteMember).toHaveBeenCalledWith("novo@example.com", "ADMIN");
    expect(router.refresh).toHaveBeenCalled();
  });

  it("shows the generic refusal without details about the address", async () => {
    vi.mocked(inviteMember).mockRejectedValue(
      apiError({ status: 422, detail: "Não foi possível enviar o convite para este endereço." }),
    );
    render(<InviteForm actorRole="ADMIN" />);
    const user = userEvent.setup();

    await user.type(screen.getByLabelText("E-mail"), "existente@example.com");
    await user.click(screen.getByRole("button", { name: "Convidar membro" }));

    expect((await screen.findByRole("alert")).textContent).toBe("Não foi possível enviar o convite para este endereço.");
  });
});

describe("PendingInvitations", () => {
  const invitations: Invitation[] = [
    { id: "i-admin", email: "a@example.com", role: "ADMIN", expiresAt: "2026-10-05T15:00:00Z", expired: false, lastSentAt: "2026-10-02T15:00:00Z" },
    { id: "i-member", email: "m@example.com", role: "MEMBER", expiresAt: "2026-09-01T15:00:00Z", expired: true, lastSentAt: "2026-08-29T15:00:00Z" },
  ];

  it("lists email, role and validity, and is empty-friendly", () => {
    const { rerender } = render(<PendingInvitations invitations={invitations} actorRole="OWNER" />);

    expect(screen.getByText("a@example.com")).toBeTruthy();
    expect(screen.getByText("Até 05/10/2026, 12:00")).toBeTruthy();
    expect(screen.getByText("Expirado")).toBeTruthy();

    rerender(<PendingInvitations invitations={[]} actorRole="OWNER" />);
    expect(screen.getByText("Nenhum convite pendente.")).toBeTruthy();
  });

  it("lets an admin act only on member invitations", () => {
    render(<PendingInvitations invitations={invitations} actorRole="ADMIN" />);

    expect(row("a@example.com").queryAllByRole("button")).toHaveLength(0);
    expect(row("m@example.com").getByRole("button", { name: "Reenviar" })).toBeTruthy();
  });

  it("resends and revokes, reporting cooldown messages", async () => {
    vi.mocked(resendInvitation).mockRejectedValueOnce(
      apiError({ status: 422, detail: "Um convite foi enviado há pouco para este endereço. Aguarde alguns minutos para reenviar." }),
    );
    vi.mocked(revokeInvitation).mockResolvedValue();
    render(<PendingInvitations invitations={invitations} actorRole="OWNER" />);
    const user = userEvent.setup();

    await user.click(row("a@example.com").getByRole("button", { name: "Reenviar" }));
    expect((await screen.findByRole("alert")).textContent).toContain("Aguarde alguns minutos");

    await user.click(row("m@example.com").getByRole("button", { name: "Revogar" }));
    expect((await screen.findByRole("status")).textContent).toBe("Convite para m@example.com revogado.");
    expect(revokeInvitation).toHaveBeenCalledWith("i-member");
  });
});
