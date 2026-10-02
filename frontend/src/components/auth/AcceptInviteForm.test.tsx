import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { acceptInvitation } from "@/lib/api/team";
import { apiError } from "@/test/mocks";
import { AcceptInviteForm } from "./AcceptInviteForm";

vi.mock("@/lib/api/team", () => ({ acceptInvitation: vi.fn() }));

const TOKEN = "Q29udml0ZSBkZSB0ZXN0ZSBwYXJhIGEgZXF1aXBlIGRhIGVtcHJlc2E";
const PASSWORD = "senha do convidado longa";

function openLink(hash: string) {
  window.history.replaceState(null, "", `/accept-invite${hash}`);
}

async function fill(name: string, password: string, confirmation: string) {
  const user = userEvent.setup();
  if (name) {
    await user.type(await screen.findByLabelText("Seu nome"), name);
  }
  await user.type(screen.getByLabelText("Senha"), password);
  await user.type(screen.getByLabelText("Confirme a senha"), confirmation);
  await user.click(screen.getByRole("button", { name: "Criar conta" }));
}

describe("AcceptInviteForm", () => {
  it("reads the token from the fragment, removes it and creates the account", async () => {
    vi.mocked(acceptInvitation).mockResolvedValue();
    openLink(`#token=${TOKEN}`);
    render(<AcceptInviteForm />);

    await fill(" Ana ", PASSWORD, PASSWORD);

    expect((await screen.findByRole("status")).textContent).toContain("Conta criada com sucesso.");
    expect(screen.getByRole("link", { name: "Entrar" }).getAttribute("href")).toBe("/login");
    expect(acceptInvitation).toHaveBeenCalledWith(TOKEN, "Ana", PASSWORD);
    expect(window.location.hash).toBe("");
  });

  it("asks for a name and a valid, confirmed password before sending", async () => {
    openLink(`#token=${TOKEN}`);
    render(<AcceptInviteForm />);

    await fill("", "curta", "curta");
    expect(screen.getByText("Informe seu nome.")).toBeTruthy();
    expect(screen.getByText("A senha deve ter entre 12 e 64 caracteres.")).toBeTruthy();
    expect(acceptInvitation).not.toHaveBeenCalled();
  });

  it("explains a used or expired invitation", async () => {
    vi.mocked(acceptInvitation).mockRejectedValue(apiError({ status: 422, detail: "Este convite já foi utilizado." }));
    openLink(`#token=${TOKEN}`);
    render(<AcceptInviteForm />);

    await fill("Ana", PASSWORD, PASSWORD);

    expect((await screen.findByRole("alert")).textContent).toBe("Este convite já foi utilizado.");
  });

  it("treats a link without token as invalid", async () => {
    openLink("");
    render(<AcceptInviteForm />);

    expect((await screen.findByRole("alert")).textContent).toBe("Este convite é inválido.");
    expect(screen.queryByLabelText("Seu nome")).toBeNull();
  });
});
