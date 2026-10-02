import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { resetPassword } from "@/lib/api/auth";
import { apiError, deferred } from "@/test/mocks";
import { ResetPasswordForm } from "./ResetPasswordForm";

vi.mock("@/lib/api/auth", () => ({ resetPassword: vi.fn() }));

const TOKEN = "UmVkZWZpbmljYW8gZGUgc2VuaGEgcGFyYSB0ZXN0ZXMgYXFp";
const NEW_PASSWORD = "minha nova senha longa";

async function fill(password: string, confirmation: string) {
  const user = userEvent.setup();
  await user.type(await screen.findByLabelText("Nova senha"), password);
  await user.type(screen.getByLabelText("Confirme a nova senha"), confirmation);
  await user.click(screen.getByRole("button", { name: "Redefinir senha" }));
}

function openLink(hash: string) {
  window.history.replaceState(null, "", `/reset-password${hash}`);
}

describe("ResetPasswordForm", () => {
  it("resets the password, clears the link and offers to sign in without logging in", async () => {
    vi.mocked(resetPassword).mockResolvedValue();
    openLink(`#token=${TOKEN}`);
    render(<ResetPasswordForm />);

    await fill(NEW_PASSWORD, NEW_PASSWORD);

    expect((await screen.findByRole("status")).textContent).toContain("Sua senha foi redefinida.");
    expect(screen.getByRole("link", { name: "Entrar" }).getAttribute("href")).toBe("/login");
    expect(resetPassword).toHaveBeenCalledWith(TOKEN, NEW_PASSWORD);
    expect(window.location.hash).toBe("");
  });

  it("requires matching passwords that follow the policy", async () => {
    openLink(`#token=${TOKEN}`);
    render(<ResetPasswordForm />);

    await fill("curta", "curta");
    expect(screen.getByText("A senha deve ter entre 12 e 64 caracteres.")).toBeTruthy();

    await fill(NEW_PASSWORD, "outra senha bem longa");
    expect(screen.getByText("As senhas não conferem.")).toBeTruthy();
    expect(resetPassword).not.toHaveBeenCalled();
  });

  it("shows a loading state while saving", async () => {
    const pending = deferred();
    vi.mocked(resetPassword).mockReturnValue(pending.promise);
    openLink(`#token=${TOKEN}`);
    render(<ResetPasswordForm />);

    await fill(NEW_PASSWORD, NEW_PASSWORD);

    expect((screen.getByRole("button", { name: "Salvando..." }) as HTMLButtonElement).disabled).toBe(true);
    pending.resolve();
    await waitFor(() => expect(screen.getByRole("status")).toBeTruthy());
  });

  it("explains an expired or used link and offers a new request", async () => {
    const detail = "Este link de redefinição expirou. Solicite uma nova redefinição.";
    vi.mocked(resetPassword).mockRejectedValue(apiError({ status: 422, detail }));
    openLink(`#token=${TOKEN}`);
    render(<ResetPasswordForm />);

    await fill(NEW_PASSWORD, NEW_PASSWORD);

    const alert = await screen.findByRole("alert");
    expect(alert.textContent).toContain(detail);
    expect(screen.getByRole("link", { name: "Solicitar uma nova redefinição" })).toBeTruthy();
    expect((screen.getByLabelText("Nova senha") as HTMLInputElement).value).toBe("");
  });

  it("hides technical failures", async () => {
    vi.mocked(resetPassword).mockRejectedValue(apiError({ status: 500, detail: "Erro interno." }));
    openLink(`#token=${TOKEN}`);
    render(<ResetPasswordForm />);

    await fill(NEW_PASSWORD, NEW_PASSWORD);

    expect((await screen.findByRole("alert")).textContent).toContain(
      "Não foi possível concluir agora. Tente novamente em instantes.",
    );
  });

  it("treats a link without token as invalid", async () => {
    openLink("");
    render(<ResetPasswordForm />);

    expect((await screen.findByRole("alert")).textContent).toBe("Este link de redefinição é inválido.");
    expect(screen.queryByLabelText("Nova senha")).toBeNull();
  });
});
