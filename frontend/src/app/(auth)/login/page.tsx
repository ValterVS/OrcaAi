import type { Metadata } from "next";
import Link from "next/link";
import { LoginForm } from "@/components/auth/LoginForm";

export const metadata: Metadata = {
  title: "Entrar | Orça Aí",
};

export default function LoginPage() {
  return (
    <>
      <h1>Entrar</h1>
      <LoginForm />
      <ul className="auth-links">
        <li>
          <Link href="/forgot-password">Esqueci minha senha</Link>
        </li>
        <li>
          <Link href="/check-email">Não recebeu o e-mail de confirmação?</Link>
        </li>
      </ul>
      <p className="auth-switch">
        Ainda não tem conta? <Link href="/signup">Cadastre sua empresa</Link>
      </p>
    </>
  );
}
