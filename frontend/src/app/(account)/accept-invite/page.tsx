import type { Metadata } from "next";
import { AcceptInviteForm } from "@/components/auth/AcceptInviteForm";

export const metadata: Metadata = {
  title: "Aceitar convite | Orça Aí",
  referrer: "no-referrer",
};

export default function AcceptInvitePage() {
  return (
    <>
      <h1>Aceitar convite</h1>
      <p className="auth-lead">Escolha seu nome e uma senha para acessar a empresa que convidou você.</p>
      <AcceptInviteForm />
    </>
  );
}
