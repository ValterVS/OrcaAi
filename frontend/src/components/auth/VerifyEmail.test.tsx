import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { verifyEmail } from "@/lib/api/auth";
import { apiError, deferred } from "@/test/mocks";
import { VerifyEmail } from "./VerifyEmail";

vi.mock("@/lib/api/auth", () => ({ verifyEmail: vi.fn() }));

const TOKEN = "Qm9hIHRlbnRhdGl2YSwgbWFzIGlzc28gZSBzbyB1bSB0ZXN0ZQ";

function openLink(hash: string) {
  window.history.replaceState(null, "", `/verify-email${hash}`);
}

describe("VerifyEmail", () => {
  it("reads the token from the fragment and removes it from the address bar", async () => {
    openLink(`#token=${TOKEN}`);
    render(<VerifyEmail />);

    expect(await screen.findByRole("button", { name: "Confirmar e-mail" })).toBeTruthy();
    expect(window.location.hash).toBe("");
    expect(verifyEmail).not.toHaveBeenCalled();
  });

  it("confirms only after an explicit click and offers to sign in", async () => {
    vi.mocked(verifyEmail).mockResolvedValue();
    openLink(`#token=${TOKEN}`);
    render(<VerifyEmail />);

    await userEvent.setup().click(await screen.findByRole("button", { name: "Confirmar e-mail" }));

    expect((await screen.findByRole("status")).textContent).toContain("E-mail confirmado com sucesso.");
    expect(screen.getByRole("link", { name: "Entrar" }).getAttribute("href")).toBe("/login");
    expect(verifyEmail).toHaveBeenCalledWith(TOKEN);
  });

  it("shows a loading state while confirming", async () => {
    const pending = deferred();
    vi.mocked(verifyEmail).mockReturnValue(pending.promise);
    openLink(`#token=${TOKEN}`);
    render(<VerifyEmail />);

    await userEvent.setup().click(await screen.findByRole("button", { name: "Confirmar e-mail" }));

    expect((screen.getByRole("button", { name: "Confirmando..." }) as HTMLButtonElement).disabled).toBe(true);
    pending.resolve();
    await waitFor(() => expect(screen.getByRole("status")).toBeTruthy());
  });

  it.each([
    "Este link de confirmação expirou. Solicite um novo envio.",
    "Este link de confirmação já foi utilizado.",
  ])("explains a rejected link: %s", async (detail) => {
    vi.mocked(verifyEmail).mockRejectedValue(apiError({ status: 422, detail }));
    openLink(`#token=${TOKEN}`);
    render(<VerifyEmail />);

    await userEvent.setup().click(await screen.findByRole("button", { name: "Confirmar e-mail" }));

    expect((await screen.findByRole("alert")).textContent).toBe(detail);
    expect(screen.getByRole("link", { name: "Solicitar um novo e-mail de confirmação" })).toBeTruthy();
  });

  it("treats a link without token as invalid", async () => {
    openLink("");
    render(<VerifyEmail />);

    expect((await screen.findByRole("alert")).textContent).toBe("Este link de confirmação é inválido.");
    expect(screen.queryByRole("button")).toBeNull();
  });
});
