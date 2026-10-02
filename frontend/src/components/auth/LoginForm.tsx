"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { ApiError } from "@/lib/api/client";
import { login } from "@/lib/api/auth";
import { genericErrorMessage } from "@/lib/messages";
import { hasErrors, validateLogin, type FieldErrors } from "@/lib/validation";
import { TextField } from "@/components/form/TextField";

const INVALID_CREDENTIALS = "E-mail ou senha inválidos.";

export function LoginForm() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);
    const errors = validateLogin({ email, password });
    setFieldErrors(errors);
    if (hasErrors(errors)) {
      return;
    }

    setSubmitting(true);
    try {
      await login(email.trim(), password);
      router.replace("/app");
      router.refresh();
    } catch (error) {
      setPassword("");
      setFormError(error instanceof ApiError && error.status === 401 ? INVALID_CREDENTIALS : genericErrorMessage(error));
      setSubmitting(false);
    }
  }

  return (
    <form className="form" onSubmit={handleSubmit} noValidate>
      <TextField
        name="email"
        label="E-mail"
        type="email"
        autoComplete="email"
        value={email}
        onChange={setEmail}
        error={fieldErrors.email}
      />
      <TextField
        name="password"
        label="Senha"
        type="password"
        autoComplete="current-password"
        value={password}
        onChange={setPassword}
        error={fieldErrors.password}
      />
      {formError && (
        <p className="form-error" role="alert">
          {formError}
        </p>
      )}
      <button type="submit" className="button-primary" disabled={submitting}>
        {submitting ? "Entrando..." : "Entrar"}
      </button>
    </form>
  );
}
