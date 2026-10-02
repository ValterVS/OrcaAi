import type { Metadata } from "next";
import { ResetPasswordForm } from "@/components/auth/ResetPasswordForm";

export const metadata: Metadata = {
  title: "Redefinir senha | Orça Aí",
  referrer: "no-referrer",
};

export default function ResetPasswordPage() {
  return (
    <>
      <h1>Redefinir senha</h1>
      <ResetPasswordForm />
    </>
  );
}
