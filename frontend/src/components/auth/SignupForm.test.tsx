import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { login, signup } from "@/lib/api/auth";
import { apiError, deferred, router } from "@/test/mocks";
import { SignupForm } from "./SignupForm";

vi.mock("next/navigation", () => ({ useRouter: () => router }));
vi.mock("@/lib/api/auth", () => ({ signup: vi.fn(), login: vi.fn() }));

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
    vi.mocked(login).mockResolvedValue();
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

  it("creates the account, signs in and opens the app", async () => {
    render(<SignupForm />);
    const user = await fillForm();

    await user.click(screen.getByRole("button", { name: "Criar conta" }));

    await waitFor(() => expect(router.replace).toHaveBeenCalledWith("/app"));
    expect(signup).toHaveBeenCalledWith({
      companyName: "Reformas Silva",
      ownerName: "Maria Silva",
      email: "maria@example.com",
      password: PASSWORD,
    });
    expect(login).toHaveBeenCalledWith("maria@example.com", PASSWORD);
    expect(screen.getByRole("status").textContent).toContain("Conta criada com sucesso.");
  });

  it("shows a loading state while submitting", async () => {
    const pending = deferred();
    vi.mocked(signup).mockReturnValue(pending.promise);
    render(<SignupForm />);
    const user = await fillForm();

    await user.click(screen.getByRole("button", { name: "Criar conta" }));

    const button = screen.getByRole("button", { name: "Criando conta..." }) as HTMLButtonElement;
    expect(button.disabled).toBe(true);
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
    expect(login).not.toHaveBeenCalled();
  });

  it("shows the generic rejection without technical details", async () => {
    vi.mocked(signup).mockRejectedValue(
      apiError({ status: 422, detail: "Não foi possível criar a conta com os dados informados." }),
    );
    render(<SignupForm />);
    const user = await fillForm();

    await user.click(screen.getByRole("button", { name: "Criar conta" }));

    expect((await screen.findByRole("alert")).textContent).toBe(
      "Não foi possível criar a conta com os dados informados.",
    );
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

  it("asks to sign in manually when the automatic login fails", async () => {
    vi.mocked(login).mockRejectedValue(apiError({ status: 500 }));
    render(<SignupForm />);
    const user = await fillForm();

    await user.click(screen.getByRole("button", { name: "Criar conta" }));

    expect(await screen.findByRole("link", { name: "Entre com seu e-mail e senha" })).toBeTruthy();
    expect(router.replace).not.toHaveBeenCalled();
  });
});
