"use client";

import { useState, type FormEvent } from "react";
import { TextAreaField } from "@/components/form/TextAreaField";
import { TextField } from "@/components/form/TextField";
import { ApiError } from "@/lib/api/client";
import type { Customer, CustomerInput } from "@/lib/api/customers";
import { validateCustomer } from "@/lib/customers";
import { actionErrorMessage } from "@/lib/messages";
import { hasErrors, type FieldErrors } from "@/lib/validation";

type CustomerFormProps = {
  initial?: CustomerInput;
  submitLabel: string;
  pendingLabel: string;
  onSubmit: (input: CustomerInput) => Promise<Customer>;
  onSaved: (customer: Customer) => void;
  onCancel?: () => void;
};

const EMPTY: CustomerInput = { name: "", phone: "", email: "", notes: "" };

export function CustomerForm({ initial = EMPTY, submitLabel, pendingLabel, onSubmit, onSaved, onCancel }: CustomerFormProps) {
  const [values, setValues] = useState<CustomerInput>(initial);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function update(field: keyof CustomerInput) {
    return (value: string) => setValues((current) => ({ ...current, [field]: value }));
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);
    const errors = validateCustomer(values);
    setFieldErrors(errors);
    if (hasErrors(errors)) {
      return;
    }

    setSubmitting(true);
    try {
      onSaved(await onSubmit(values));
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
      setSubmitting(false);
    }
  }

  return (
    <form className="form" onSubmit={handleSubmit} noValidate>
      <TextField name="name" label="Nome *" value={values.name} onChange={update("name")} error={fieldErrors.name} />
      <TextField
        name="phone"
        label="Telefone"
        type="tel"
        autoComplete="off"
        value={values.phone}
        onChange={update("phone")}
        error={fieldErrors.phone}
      />
      <TextField
        name="email"
        label="E-mail"
        type="email"
        autoComplete="off"
        value={values.email}
        onChange={update("email")}
        error={fieldErrors.email}
      />
      <TextAreaField name="notes" label="Observações" value={values.notes} onChange={update("notes")} error={fieldErrors.notes} />
      {formError && (
        <p className="form-error" role="alert">
          {formError}
        </p>
      )}
      <div className="form-actions">
        <button type="submit" className="button-primary" disabled={submitting}>
          {submitting ? pendingLabel : submitLabel}
        </button>
        {onCancel && (
          <button type="button" className="button-secondary" onClick={onCancel} disabled={submitting}>
            Cancelar
          </button>
        )}
      </div>
    </form>
  );
}
