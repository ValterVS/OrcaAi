import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { login } from "@/lib/api/auth";
import { apiError, deferred, router } from "@/test/mocks";
import { LoginForm } from "./LoginForm";

vi.mock("next/navigation", () => ({ useRouter: () => router }));
vi.mock("@/lib/api/auth", () => ({ login: vi.fn() }));

async function submit(email: string, password: string) {
  const user = userEvent.setup();
  if (email) {
    await user.type(screen.getByLabelText("E-mail"), email);
  }
  if (password) {
    await user.type(screen.getByLabelText("Senha"), password);
  }
  await user.click(screen.getByRole("button", { name: "Entrar" }));
}

describe("LoginForm", () => {
  beforeEach(() => {
    vi.mocked(login).mockResolvedValue();
  });

  it("requires email and password", async () => {
    render(<LoginForm />);

    await submit("", "");

    expect(screen.getByText("Informe o e-mail.")).toBeTruthy();
    expect(screen.getByText("Informe a senha.")).toBeTruthy();
    expect(login).not.toHaveBeenCalled();
  });

  it("signs in and opens the app", async () => {
    render(<LoginForm />);

    await submit(" maria@example.com ", "uma senha bem longa");

    await waitFor(() => expect(router.replace).toHaveBeenCalledWith("/app"));
    expect(login).toHaveBeenCalledWith("maria@example.com", "uma senha bem longa");
  });

  it("disables the button while signing in", async () => {
    const pending = deferred();
    vi.mocked(login).mockReturnValue(pending.promise);
    render(<LoginForm />);

    await submit("maria@example.com", "uma senha bem longa");

    expect((screen.getByRole("button", { name: "Entrando..." }) as HTMLButtonElement).disabled).toBe(true);
    pending.resolve();
    await waitFor(() => expect(router.replace).toHaveBeenCalled());
  });

  it("shows one generic message for invalid credentials and clears the password", async () => {
    vi.mocked(login).mockRejectedValue(apiError({ status: 401, detail: "Não autenticado." }));
    render(<LoginForm />);

    await submit("maria@example.com", "senha errada qualquer");

    expect((await screen.findByRole("alert")).textContent).toBe("E-mail ou senha inválidos.");
    expect((screen.getByLabelText("Senha") as HTMLInputElement).value).toBe("");
    expect(router.replace).not.toHaveBeenCalled();
  });

  it("explains rate limiting", async () => {
    vi.mocked(login).mockRejectedValue(apiError({ status: 429 }));
    render(<LoginForm />);

    await submit("maria@example.com", "senha errada qualquer");

    expect((await screen.findByRole("alert")).textContent).toBe(
      "Muitas tentativas. Aguarde alguns minutos e tente novamente.",
    );
  });

  it("hides server failures behind a neutral message", async () => {
    vi.mocked(login).mockRejectedValue(new TypeError("Failed to fetch"));
    render(<LoginForm />);

    await submit("maria@example.com", "uma senha bem longa");

    expect((await screen.findByRole("alert")).textContent).toBe(
      "Não foi possível concluir agora. Tente novamente em instantes.",
    );
  });
});
