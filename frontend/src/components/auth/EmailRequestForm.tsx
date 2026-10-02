"use client";

import { useState, type FormEvent } from "react";
import { genericErrorMessage } from "@/lib/messages";
import { hasErrors, validateEmailRequest, type FieldErrors } from "@/lib/validation";
import { TextField } from "@/components/form/TextField";

type EmailRequestFormProps = {
  submitLabel: string;
  pendingLabel: string;
  /** Shown after any accepted request; must not depend on whether the account exists. */
  doneMessage: string;
  onSubmit: (email: string) => Promise<void>;
};

/** One email field whose outcome is the same message for every address (resend, forgot password). */
export function EmailRequestForm({ submitLabel, pendingLabel, doneMessage, onSubmit }: EmailRequestFormProps) {
  const [email, setEmail] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);
    const errors = validateEmailRequest({ email });
    setFieldErrors(errors);
    if (hasErrors(errors)) {
      return;
    }

    setSubmitting(true);
    try {
      await onSubmit(email.trim());
      setDone(true);
    } catch (error) {
      setFormError(genericErrorMessage(error));
    } finally {
      setSubmitting(false);
    }
  }

  if (done) {
    return (
      <p className="form-success" role="status">
        {doneMessage}
      </p>
    );
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
      {formError && (
        <p className="form-error" role="alert">
          {formError}
        </p>
      )}
      <button type="submit" className="button-primary" disabled={submitting}>
        {submitting ? pendingLabel : submitLabel}
      </button>
    </form>
  );
}
