"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { TextField } from "@/components/form/TextField";
import { ApiError } from "@/lib/api/client";
import { acceptInvitation } from "@/lib/api/team";
import { actionErrorMessage } from "@/lib/messages";
import { useFragmentToken } from "@/lib/useFragmentToken";
import { hasErrors, PASSWORD_RULE, validateNewPassword, type FieldErrors } from "@/lib/validation";

const INVALID_LINK = "Este convite é inválido.";

/** Email, company and role come from the invitation on the server; only name and password are asked. */
export function AcceptInviteForm() {
  const token = useFragmentToken();
  const [name, setName] = useState("");
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
    const errors: FieldErrors = { ...validateNewPassword({ password, confirmation }) };
    if (!name.trim()) {
      errors.name = "Informe seu nome.";
    }
    setFieldErrors(errors);
    if (hasErrors(errors)) {
      return;
    }

    setSubmitting(true);
    try {
      await acceptInvitation(token, name.trim(), password);
      setDone(true);
    } catch (error) {
      if (error instanceof ApiError && error.status === 400 && error.problem.errors?.length) {
        const serverErrors: FieldErrors = {};
        for (const violation of error.problem.errors) {
          serverErrors[violation.field] ??= violation.message;
        }
        setFieldErrors(serverErrors);
      } else {
        setFormError(actionErrorMessage(error));
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
        <p>Conta criada com sucesso.</p>
        <Link href="/login" className="button-primary button-link">
          Entrar
        </Link>
      </div>
    );
  }

  if (!token) {
    return (
      <p className="form-error" role="alert">
        {INVALID_LINK}
      </p>
    );
  }

  return (
    <form className="form" onSubmit={handleSubmit} noValidate>
      <TextField name="name" label="Seu nome" autoComplete="name" value={name} onChange={setName} error={fieldErrors.name} />
      <TextField
        name="password"
        label="Senha"
        type="password"
        autoComplete="new-password"
        value={password}
        onChange={setPassword}
        error={fieldErrors.password}
        hint={PASSWORD_RULE}
      />
      <TextField
        name="confirmation"
        label="Confirme a senha"
        type="password"
        autoComplete="new-password"
        value={confirmation}
        onChange={setConfirmation}
        error={fieldErrors.confirmation}
      />
      {formError && (
        <p className="form-error" role="alert">
          {formError}
        </p>
      )}
      <button type="submit" className="button-primary" disabled={submitting}>
        {submitting ? "Criando conta..." : "Criar conta"}
      </button>
    </form>
  );
}
