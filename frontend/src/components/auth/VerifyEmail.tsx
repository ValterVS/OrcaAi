"use client";

import Link from "next/link";
import { useState } from "react";
import { ApiError } from "@/lib/api/client";
import { verifyEmail } from "@/lib/api/auth";
import { genericErrorMessage } from "@/lib/messages";
import { useFragmentToken } from "@/lib/useFragmentToken";

const INVALID_LINK = "Este link de confirmação é inválido.";

type Status = "ready" | "submitting" | "verified" | "failed";

/**
 * Confirmation needs an explicit click: email scanners that open links must not verify accounts.
 */
export function VerifyEmail() {
  const token = useFragmentToken();
  const [status, setStatus] = useState<Status>("ready");
  const [error, setError] = useState<string | null>(null);

  async function confirm() {
    if (!token) {
      return;
    }
    setStatus("submitting");
    try {
      await verifyEmail(token);
      setStatus("verified");
    } catch (failure) {
      setError(failure instanceof ApiError && failure.status === 422 && failure.problem.detail
        ? failure.problem.detail
        : genericErrorMessage(failure));
      setStatus("failed");
    }
  }

  if (token === undefined) {
    return null;
  }

  if (status === "verified") {
    return (
      <div className="form-success" role="status">
        <p>E-mail confirmado com sucesso.</p>
        <Link href="/login" className="button-primary button-link">
          Entrar
        </Link>
      </div>
    );
  }

  if (!token || status === "failed") {
    return (
      <div className="form">
        <p className="form-error" role="alert">
          {token ? error : INVALID_LINK}
        </p>
        <p>
          <Link href="/check-email">Solicitar um novo e-mail de confirmação</Link>
        </p>
      </div>
    );
  }

  return (
    <div className="form">
      <p>Clique no botão abaixo para confirmar seu endereço de e-mail.</p>
      <button type="button" className="button-primary" onClick={confirm} disabled={status === "submitting"}>
        {status === "submitting" ? "Confirmando..." : "Confirmar e-mail"}
      </button>
    </div>
  );
}
