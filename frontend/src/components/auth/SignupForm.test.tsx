import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { signup } from "@/lib/api/auth";
import { apiError, deferred, router } from "@/test/mocks";
import { SignupForm } from "./SignupForm";

vi.mock("next/navigation", () => ({ useRouter: () => router }));
vi.mock("@/lib/api/auth", () => ({ signup: vi.fn() }));

const PASSWORD = "uma senha bem longa";

async function fillForm(overrides: Partial<Record<string, string>> = {}) {
  const user = userEvent.setup();
  const values = {
    "Nome da empresa": " Reformas Silva ",
    "Seu nome": "Maria Silva",
    "E-mail": " maria@example.com ",
    Senha: PASSWORD,
    ...overrides,
  };
  for (const [label, value] of Object.entries(values)) {
    if (value) {
      await user.type(screen.getByLabelText(label), value);
    }
  }
  return user;
}

describe("SignupForm", () => {
  beforeEach(() => {
    vi.mocked(signup).mockResolvedValue();
  });

  it("validates fields before calling the API", async () => {
    render(<SignupForm />);
    const user = await fillForm({ "Nome da empresa": "", "E-mail": "not-an-email", Senha: "curta" });

    await user.click(screen.getByRole("button", { name: "Criar conta" }));

    expect(screen.getByText("Informe o nome da empresa.")).toBeTruthy();
    expect(screen.getByText("Informe um e-mail válido.")).toBeTruthy();
    expect(screen.getByText("A senha deve ter entre 12 e 64 caracteres.")).toBeTruthy();
    expect(signup).not.toHaveBeenCalled();
  });

  it("sends the sign-up and goes to the check-email page instead of the app", async () => {
    render(<SignupForm />);
    const user = await fillForm();

    await user.click(screen.getByRole("button", { name: "Criar conta" }));

    await waitFor(() => expect(router.replace).toHaveBeenCalledWith("/check-email"));
    expect(signup).toHaveBeenCalledWith({
      companyName: "Reformas Silva",
      ownerName: "Maria Silva",
      email: "maria@example.com",
      password: PASSWORD,
    });
    expect(router.replace).not.toHaveBeenCalledWith("/app");
  });

  it("shows a loading state while submitting", async () => {
    const pending = deferred();
    vi.mocked(signup).mockReturnValue(pending.promise);
    render(<SignupForm />);
    const user = await fillForm();

    await user.click(screen.getByRole("button", { name: "Criar conta" }));

    expect((screen.getByRole("button", { name: "Criando conta..." }) as HTMLButtonElement).disabled).toBe(true);
    pending.resolve();
    await waitFor(() => expect(router.replace).toHaveBeenCalled());
  });

  it("shows field errors returned by the server", async () => {
    vi.mocked(signup).mockRejectedValue(
      apiError({ status: 400, errors: [{ field: "email", message: "Informe um e-mail válido." }] }),
    );
    render(<SignupForm />);
    const user = await fillForm();

    await user.click(screen.getByRole("button", { name: "Criar conta" }));

    expect(await screen.findByText("Informe um e-mail válido.")).toBeTruthy();
    expect(router.replace).not.toHaveBeenCalled();
  });

  it("explains rate limiting without revealing anything about the account", async () => {
    vi.mocked(signup).mockRejectedValue(apiError({ status: 429 }));
    render(<SignupForm />);
    const user = await fillForm();

    await user.click(screen.getByRole("button", { name: "Criar conta" }));

    expect((await screen.findByRole("alert")).textContent).toBe(
      "Muitas tentativas. Aguarde alguns minutos e tente novamente.",
    );
  });

  it("hides technical failures behind a neutral message", async () => {
    vi.mocked(signup).mockRejectedValue(apiError({ status: 500, detail: "Erro interno." }));
    render(<SignupForm />);
    const user = await fillForm();

    await user.click(screen.getByRole("button", { name: "Criar conta" }));

    expect((await screen.findByRole("alert")).textContent).toBe(
      "Não foi possível concluir agora. Tente novamente em instantes.",
    );
  });
});
