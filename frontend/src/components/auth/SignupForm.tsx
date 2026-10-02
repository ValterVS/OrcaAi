"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { ApiError } from "@/lib/api/client";
import { signup, type SignupData } from "@/lib/api/auth";
import { genericErrorMessage } from "@/lib/messages";
import { hasErrors, PASSWORD_RULE, validateSignup, type FieldErrors } from "@/lib/validation";
import { TextField } from "./TextField";

const EMPTY: SignupData = { companyName: "", ownerName: "", email: "", password: "" };

function errorsFromServer(error: ApiError): FieldErrors {
  const errors: FieldErrors = {};
  for (const violation of error.problem.errors ?? []) {
    errors[violation.field] ??= violation.message;
  }
  return errors;
}

/**
 * The backend answers the same way whether or not the address already has an account, so the
 * next step is always the "check your email" page.
 */
export function SignupForm() {
  const router = useRouter();
  const [data, setData] = useState<SignupData>(EMPTY);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function update(field: keyof SignupData) {
    return (value: string) => setData((current) => ({ ...current, [field]: value }));
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);
    const errors = validateSignup(data);
    setFieldErrors(errors);
    if (hasErrors(errors)) {
      return;
    }

    setSubmitting(true);
    try {
      await signup({
        companyName: data.companyName.trim(),
        ownerName: data.ownerName.trim(),
        email: data.email.trim(),
        password: data.password,
      });
      setData(EMPTY);
      router.replace("/check-email");
    } catch (error) {
      setSubmitting(false);
      if (error instanceof ApiError && error.status === 400 && error.problem.errors?.length) {
        setFieldErrors(errorsFromServer(error));
      } else {
        setFormError(genericErrorMessage(error));
      }
    }
  }

  return (
    <form className="form" onSubmit={handleSubmit} noValidate>
      <TextField
        name="companyName"
        label="Nome da empresa"
        autoComplete="organization"
        value={data.companyName}
        onChange={update("companyName")}
        error={fieldErrors.companyName}
      />
      <TextField
        name="ownerName"
        label="Seu nome"
        autoComplete="name"
        value={data.ownerName}
        onChange={update("ownerName")}
        error={fieldErrors.ownerName}
      />
      <TextField
        name="email"
        label="E-mail"
        type="email"
        autoComplete="email"
        value={data.email}
        onChange={update("email")}
        error={fieldErrors.email}
      />
      <TextField
        name="password"
        label="Senha"
        type="password"
        autoComplete="new-password"
        value={data.password}
        onChange={update("password")}
        error={fieldErrors.password}
        hint={PASSWORD_RULE}
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
