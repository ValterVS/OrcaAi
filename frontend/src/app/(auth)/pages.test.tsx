import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import CheckEmailPage from "./check-email/page";
import LoginPage from "./login/page";

vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: vi.fn(), refresh: vi.fn() }) }));
vi.mock("@/lib/api/auth", () => ({ login: vi.fn(), resendVerification: vi.fn() }));

describe("LoginPage", () => {
  it("links to password recovery and to resending the confirmation", () => {
    render(<LoginPage />);

    expect(screen.getByRole("link", { name: "Esqueci minha senha" }).getAttribute("href")).toBe("/forgot-password");
    expect(screen.getByRole("link", { name: "Não recebeu o e-mail de confirmação?" }).getAttribute("href")).toBe(
      "/check-email",
    );
  });
});

describe("CheckEmailPage", () => {
  it("asks to check the inbox and offers to resend, without account details", () => {
    render(<CheckEmailPage />);

    expect(screen.getByRole("heading", { name: "Verifique seu e-mail" })).toBeTruthy();
    expect(screen.getByText("Enviamos as instruções para confirmar sua conta.")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Reenviar e-mail" })).toBeTruthy();
  });
});
