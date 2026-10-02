"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { ApiError } from "@/lib/api/client";
import { resetPassword } from "@/lib/api/auth";
import { genericErrorMessage } from "@/lib/messages";
import { useFragmentToken } from "@/lib/useFragmentToken";
import { hasErrors, PASSWORD_RULE, validateNewPassword, type FieldErrors } from "@/lib/validation";
import { TextField } from "@/components/form/TextField";

const INVALID_LINK = "Este link de redefinição é inválido.";

function messageFor(error: unknown): string {
  if (error instanceof ApiError && error.status === 422 && error.problem.detail) {
    return error.problem.detail;
  }
  return genericErrorMessage(error);
}

export function ResetPasswordForm() {
  const token = useFragmentToken();
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!token) {
      return;
    }
    setFormError(null);
    const errors = validateNewPassword({ password, confirmation });
    setFieldErrors(errors);
    if (hasErrors(errors)) {
      return;
    }

    setSubmitting(true);
    try {
      await resetPassword(token, password);
      setDone(true);
    } catch (error) {
      if (error instanceof ApiError && error.status === 400 && error.problem.errors?.length) {
        setFieldErrors({ password: PASSWORD_RULE });
      } else {
        setFormError(messageFor(error));
      }
    } finally {
      setPassword("");
      setConfirmation("");
      setSubmitting(false);
    }
  }

  if (token === undefined) {
    return null;
  }

  if (done) {
    return (
      <div className="form-success" role="status">
        <p>Sua senha foi redefinida.</p>
        <Link href="/login" className="button-primary button-link">
          Entrar
        </Link>
      </div>
    );
  }

  if (!token) {
    return (
      <div className="form">
        <p className="form-error" role="alert">
          {INVALID_LINK}
        </p>
        <p>
          <Link href="/forgot-password">Solicitar uma nova redefinição</Link>
        </p>
      </div>
    );
  }

  return (
    <form className="form" onSubmit={handleSubmit} noValidate>
      <TextField
        name="password"
        label="Nova senha"
        type="password"
        autoComplete="new-password"
        value={password}
        onChange={setPassword}
        error={fieldErrors.password}
        hint={PASSWORD_RULE}
      />
      <TextField
        name="confirmation"
        label="Confirme a nova senha"
        type="password"
        autoComplete="new-password"
        value={confirmation}
        onChange={setConfirmation}
        error={fieldErrors.confirmation}
      />
      {formError && (
        <div className="form-error" role="alert">
          <p>{formError}</p>
          <p>
            <Link href="/forgot-password">Solicitar uma nova redefinição</Link>
          </p>
        </div>
      )}
      <button type="submit" className="button-primary" disabled={submitting}>
        {submitting ? "Salvando..." : "Redefinir senha"}
      </button>
    </form>
  );
}
