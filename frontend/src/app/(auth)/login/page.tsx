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
      <p className="auth-switch">
        Ainda não tem conta? <Link href="/signup">Cadastre sua empresa</Link>
      </p>
    </>
  );
}
