"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import type { Role } from "@/lib/api/auth";
import { resendInvitation, revokeInvitation, type Invitation } from "@/lib/api/team";
import { formatDateTime } from "@/lib/customers";
import { actionErrorMessage } from "@/lib/messages";
import { roleLabel } from "@/lib/roles";
import { canManage } from "@/lib/team";

export function PendingInvitations({ invitations, actorRole }: { invitations: Invitation[]; actorRole: Role }) {
  const router = useRouter();
  const [pendingId, setPendingId] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function run(invitation: Invitation, action: () => Promise<unknown>, done: string) {
    setPendingId(invitation.id);
    setNotice(null);
    setError(null);
    try {
      await action();
      setNotice(done);
      router.refresh();
    } catch (failure) {
      setError(actionErrorMessage(failure));
    } finally {
      setPendingId(null);
    }
  }

  if (invitations.length === 0) {
    return <p className="muted">Nenhum convite pendente.</p>;
  }

  return (
    <>
      {notice && (
        <p className="form-success" role="status">
          {notice}
        </p>
      )}
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      <div className="table-scroll">
        <table className="data-table">
          <thead>
            <tr>
              <th scope="col">E-mail</th>
              <th scope="col">Papel</th>
              <th scope="col">Validade</th>
              <th scope="col">
                <span className="visually-hidden">Ações</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {invitations.map((invitation) => (
              <tr key={invitation.id}>
                <td>{invitation.email}</td>
                <td>{roleLabel(invitation.role)}</td>
                <td>{invitation.expired ? "Expirado" : `Até ${formatDateTime(invitation.expiresAt)}`}</td>
                <td className="row-actions">
                  {canManage(actorRole, invitation.role) && (
                    <>
                      <button
                        type="button"
                        className="button-secondary"
                        disabled={pendingId === invitation.id}
                        onClick={() => run(invitation, () => resendInvitation(invitation.id), `Convite reenviado para ${invitation.email}.`)}
                      >
                        Reenviar
                      </button>
                      <button
                        type="button"
                        className="button-secondary"
                        disabled={pendingId === invitation.id}
                        onClick={() => run(invitation, () => revokeInvitation(invitation.id), `Convite para ${invitation.email} revogado.`)}
                      >
                        Revogar
                      </button>
                    </>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
