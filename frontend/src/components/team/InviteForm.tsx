"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { TextField } from "@/components/form/TextField";
import type { Role } from "@/lib/api/auth";
import { ApiError } from "@/lib/api/client";
import { inviteMember, type AssignableRole } from "@/lib/api/team";
import { actionErrorMessage } from "@/lib/messages";
import { roleLabel } from "@/lib/roles";
import { invitableRoles } from "@/lib/team";
import { hasErrors, validateEmailRequest, type FieldErrors } from "@/lib/validation";

export function InviteForm({ actorRole }: { actorRole: Role }) {
  const router = useRouter();
  const roles = invitableRoles(actorRole);
  const [email, setEmail] = useState("");
  const [role, setRole] = useState<AssignableRole>("MEMBER");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);
    setNotice(null);
    const errors = validateEmailRequest({ email });
    setFieldErrors(errors);
    if (hasErrors(errors)) {
      return;
    }

    setSubmitting(true);
    try {
      const invitation = await inviteMember(email.trim(), role);
      setNotice(`Convite enviado para ${invitation.email}.`);
      setEmail("");
      router.refresh();
    } catch (error) {
      if (error instanceof ApiError && error.status === 400 && error.problem.errors?.length) {
        setFieldErrors({ email: error.problem.errors[0].message });
      } else {
        setFormError(actionErrorMessage(error));
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="form invite-form" onSubmit={handleSubmit} noValidate>
      <TextField name="invite-email" label="E-mail" type="email" autoComplete="off" value={email} onChange={setEmail} error={fieldErrors.email} />
      <div className="field">
        <label htmlFor="invite-role">Papel</label>
        <select id="invite-role" value={role} onChange={(event) => setRole(event.target.value as AssignableRole)}>
          {roles.map((option) => (
            <option key={option} value={option}>
              {roleLabel(option)}
            </option>
          ))}
        </select>
      </div>
      {formError && (
        <p className="form-error" role="alert">
          {formError}
        </p>
      )}
      {notice && (
        <p className="form-success" role="status">
          {notice}
        </p>
      )}
      <div className="form-actions">
        <button type="submit" className="button-primary" disabled={submitting}>
          {submitting ? "Enviando..." : "Convidar membro"}
        </button>
      </div>
    </form>
  );
}
