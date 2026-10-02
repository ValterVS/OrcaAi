import type { Metadata } from "next";
import { VerifyEmail } from "@/components/auth/VerifyEmail";

export const metadata: Metadata = {
  title: "Confirmar e-mail | Orça Aí",
  referrer: "no-referrer",
};

export default function VerifyEmailPage() {
  return (
    <>
      <h1>Confirmar e-mail</h1>
      <VerifyEmail />
    </>
  );
}
