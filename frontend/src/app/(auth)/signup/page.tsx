import type { Metadata } from "next";
import Link from "next/link";
import { SignupForm } from "@/components/auth/SignupForm";

export const metadata: Metadata = {
  title: "Cadastrar empresa | Orça Aí",
};

export default function SignupPage() {
  return (
    <>
      <h1>Cadastre sua empresa</h1>
      <p className="auth-lead">Crie o espaço da sua empresa para organizar orçamentos e propostas.</p>
      <SignupForm />
      <p className="auth-switch">
        Já tem conta? <Link href="/login">Entrar</Link>
      </p>
    </>
  );
}
