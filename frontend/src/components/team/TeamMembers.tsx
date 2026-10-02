"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import type { Role } from "@/lib/api/auth";
import { changeMemberRole, deactivateMember, reactivateMember, type Member } from "@/lib/api/team";
import { actionErrorMessage } from "@/lib/messages";
import { roleLabel } from "@/lib/roles";
import { canChangeRole, canManage } from "@/lib/team";

type TeamMembersProps = {
  members: Member[];
  actorId: string;
  actorRole: Role;
};

export function TeamMembers({ members, actorId, actorRole }: TeamMembersProps) {
  const router = useRouter();
  const [pendingId, setPendingId] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function run(member: Member, action: () => Promise<unknown>, done: string) {
    setPendingId(member.id);
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
              <th scope="col">Nome</th>
              <th scope="col" className="hide-small">E-mail</th>
              <th scope="col">Papel</th>
              <th scope="col">Status</th>
              <th scope="col">
                <span className="visually-hidden">Ações</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {members.map((member) => {
              const self = member.id === actorId;
              const busy = pendingId === member.id;
              return (
                <tr key={member.id}>
                  <td>
                    {member.name}
                    {self && <span className="badge">Você</span>}
                  </td>
                  <td className="hide-small">{member.email}</td>
                  <td>{roleLabel(member.role)}</td>
                  <td>{member.active ? "Ativo" : "Inativo"}</td>
                  <td className="row-actions">
                    {!self && canChangeRole(actorRole, member.role) && (
                      <button
                        type="button"
                        className="button-secondary"
                        disabled={busy}
                        onClick={() =>
                          member.role === "ADMIN"
                            ? run(member, () => changeMemberRole(member, "MEMBER"), `${member.name} agora é Membro.`)
                            : run(member, () => changeMemberRole(member, "ADMIN"), `${member.name} agora é Administrador.`)
                        }
                      >
                        {member.role === "ADMIN" ? "Tornar membro" : "Tornar administrador"}
                      </button>
                    )}
                    {!self && canManage(actorRole, member.role) && (
                      <button
                        type="button"
                        className="button-secondary"
                        disabled={busy}
                        onClick={() =>
                          member.active
                            ? run(member, () => deactivateMember(member), `${member.name} foi desativado.`)
                            : run(member, () => reactivateMember(member), `${member.name} foi reativado.`)
                        }
                      >
                        {member.active ? "Desativar" : "Reativar"}
                      </button>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </>
  );
}
