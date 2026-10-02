import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { requestPasswordReset, resendVerification } from "@/lib/api/auth";
import { apiError, deferred } from "@/test/mocks";
import { ForgotPasswordForm } from "./ForgotPasswordForm";
import { ResendVerificationForm } from "./ResendVerificationForm";

vi.mock("@/lib/api/auth", () => ({ requestPasswordReset: vi.fn(), resendVerification: vi.fn() }));

async function submit(email: string, button: string) {
  const user = userEvent.setup();
  if (email) {
    await user.type(screen.getByLabelText("E-mail"), email);
  }
  await user.click(screen.getByRole("button", { name: button }));
}

describe("ForgotPasswordForm", () => {
  it("always shows the same message, whatever the address", async () => {
    vi.mocked(requestPasswordReset).mockResolvedValue();
    render(<ForgotPasswordForm />);

    await submit(" maria@example.com ", "Enviar instruções");

    expect((await screen.findByRole("status")).textContent).toBe(
      "Se existir uma conta para este e-mail, você receberá as instruções.",
    );
    expect(requestPasswordReset).toHaveBeenCalledWith("maria@example.com");
  });

  it("validates the email before calling the API", async () => {
    render(<ForgotPasswordForm />);

    await submit("maria@", "Enviar instruções");

    expect(screen.getByText("Informe um e-mail válido.")).toBeTruthy();
    expect(requestPasswordReset).not.toHaveBeenCalled();
  });

  it("disables the button while sending", async () => {
    const pending = deferred();
    vi.mocked(requestPasswordReset).mockReturnValue(pending.promise);
    render(<ForgotPasswordForm />);

    await submit("maria@example.com", "Enviar instruções");

    expect((screen.getByRole("button", { name: "Enviando..." }) as HTMLButtonElement).disabled).toBe(true);
    pending.resolve();
    await waitFor(() => expect(screen.getByRole("status")).toBeTruthy());
  });

  it("shows rate limiting and failures without account details", async () => {
    vi.mocked(requestPasswordReset).mockRejectedValue(apiError({ status: 429 }));
    render(<ForgotPasswordForm />);

    await submit("maria@example.com", "Enviar instruções");

    expect((await screen.findByRole("alert")).textContent).toBe(
      "Muitas tentativas. Aguarde alguns minutos e tente novamente.",
    );
  });
});

describe("ResendVerificationForm", () => {
  it("resends and shows a neutral confirmation", async () => {
    vi.mocked(resendVerification).mockResolvedValue();
    render(<ResendVerificationForm />);

    await submit("maria@example.com", "Reenviar e-mail");

    expect((await screen.findByRole("status")).textContent).toBe(
      "Se houver uma confirmação pendente para este e-mail, enviaremos uma nova mensagem.",
    );
    expect(resendVerification).toHaveBeenCalledWith("maria@example.com");
  });

  it("shows a generic error when the request fails", async () => {
    vi.mocked(resendVerification).mockRejectedValue(new TypeError("Failed to fetch"));
    render(<ResendVerificationForm />);

    await submit("maria@example.com", "Reenviar e-mail");

    expect((await screen.findByRole("alert")).textContent).toBe(
      "Não foi possível concluir agora. Tente novamente em instantes.",
    );
  });
});
