"use client";

import { resendVerification } from "@/lib/api/auth";
import { EmailRequestForm } from "./EmailRequestForm";

export function ResendVerificationForm() {
  return (
    <EmailRequestForm
      submitLabel="Reenviar e-mail"
      pendingLabel="Enviando..."
      doneMessage="Se houver uma confirmação pendente para este e-mail, enviaremos uma nova mensagem."
      onSubmit={resendVerification}
    />
  );
}
