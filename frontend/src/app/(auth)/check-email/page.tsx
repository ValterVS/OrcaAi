import type { Metadata } from "next";
import Link from "next/link";
import { ResendVerificationForm } from "@/components/auth/ResendVerificationForm";

export const metadata: Metadata = {
  title: "Verifique seu e-mail | Orça Aí",
};

export default function CheckEmailPage() {
  return (
    <>
      <h1>Verifique seu e-mail</h1>
      <p className="auth-lead">Enviamos as instruções para confirmar sua conta.</p>
      <h2>Não recebeu?</h2>
      <p className="auth-hint">Confira a caixa de spam ou informe seu e-mail para reenviar a mensagem.</p>
      <ResendVerificationForm />
      <p className="auth-switch">
        Já confirmou? <Link href="/login">Entrar</Link>
      </p>
    </>
  );
}
