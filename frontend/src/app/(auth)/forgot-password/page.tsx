import type { Metadata } from "next";
import Link from "next/link";
import { ForgotPasswordForm } from "@/components/auth/ForgotPasswordForm";

export const metadata: Metadata = {
  title: "Esqueci minha senha | Orça Aí",
};

export default function ForgotPasswordPage() {
  return (
    <>
      <h1>Esqueci minha senha</h1>
      <p className="auth-lead">Informe o e-mail da sua conta para receber as instruções de redefinição.</p>
      <ForgotPasswordForm />
      <p className="auth-switch">
        <Link href="/login">Voltar para o login</Link>
      </p>
    </>
  );
}
