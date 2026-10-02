"use client";

import { requestPasswordReset } from "@/lib/api/auth";
import { EmailRequestForm } from "./EmailRequestForm";

export function ForgotPasswordForm() {
  return (
    <EmailRequestForm
      submitLabel="Enviar instruções"
      pendingLabel="Enviando..."
      doneMessage="Se existir uma conta para este e-mail, você receberá as instruções."
      onSubmit={requestPasswordReset}
    />
  );
}
