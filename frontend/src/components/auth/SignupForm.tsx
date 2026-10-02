"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { ApiError } from "@/lib/api/client";
import { login, signup, type SignupData } from "@/lib/api/auth";
import { genericErrorMessage } from "@/lib/messages";
import { hasErrors, PASSWORD_RULE, validateSignup, type FieldErrors } from "@/lib/validation";
import { TextField } from "./TextField";

type Status = "editing" | "submitting" | "created" | "createdNeedsLogin";

const EMPTY: SignupData = { companyName: "", ownerName: "", email: "", password: "" };

function errorsFromServer(error: ApiError): FieldErrors {
  const errors: FieldErrors = {};
  for (const violation of error.problem.errors ?? []) {
    errors[violation.field] ??= violation.message;
  }
  return errors;
}

function messageFor(error: unknown): string {
  if (error instanceof ApiError && error.status === 422 && error.problem.detail) {
    return error.problem.detail;
  }
  return genericErrorMessage(error);
}

export function SignupForm() {
  const router = useRouter();
  const [data, setData] = useState<SignupData>(EMPTY);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [status, setStatus] = useState<Status>("editing");

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

    setStatus("submitting");
    const request: SignupData = {
      companyName: data.companyName.trim(),
      ownerName: data.ownerName.trim(),
      email: data.email.trim(),
      password: data.password,
    };
    try {
      await signup(request);
    } catch (error) {
      setStatus("editing");
      if (error instanceof ApiError && error.status === 400 && error.problem.errors?.length) {
        setFieldErrors(errorsFromServer(error));
      } else {
        setFormError(messageFor(error));
      }
      return;
    }

    // The password is only kept in memory long enough to open the first session.
    setData(EMPTY);
    setStatus("created");
    try {
      await login(request.email, request.password);
      router.replace("/app");
      router.refresh();
    } catch {
      setStatus("createdNeedsLogin");
    }
  }

  if (status === "created" || status === "createdNeedsLogin") {
    return (
      <div className="form-success" role="status">
        <p>Conta criada com sucesso.</p>
        {status === "created" ? (
          <p>Entrando no seu espaço...</p>
        ) : (
          <p>
            <Link href="/login">Entre com seu e-mail e senha</Link> para continuar.
          </p>
        )}
      </div>
    );
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
      <button type="submit" className="button-primary" disabled={status === "submitting"}>
        {status === "submitting" ? "Criando conta..." : "Criar conta"}
      </button>
    </form>
  );
}
