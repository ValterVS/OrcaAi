import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { InviteForm } from "@/components/team/InviteForm";
import { PendingInvitations } from "@/components/team/PendingInvitations";
import { TeamMembers } from "@/components/team/TeamMembers";
import { getCurrentAccount, getPendingInvitations, getTeamMembers } from "@/lib/api/server";
import { canManageTeam } from "@/lib/team";

export const metadata: Metadata = {
  title: "Equipe | Orça Aí",
};

export default async function TeamPage() {
  const account = await getCurrentAccount();
  if (!account) {
    redirect("/login");
  }
  const manager = canManageTeam(account.role);
  const [members, invitations] = await Promise.all([
    getTeamMembers(),
    manager ? getPendingInvitations() : Promise.resolve(null),
  ]);

  return (
    <>
      <h1>Equipe</h1>
      <section className="section">
        <TeamMembers members={members} actorId={account.userId} actorRole={account.role} />
      </section>
      {manager && (
        <>
          <section className="section panel">
            <h2>Convidar membro</h2>
            <InviteForm actorRole={account.role} />
          </section>
          <section className="section">
            <h2>Convites pendentes</h2>
            <PendingInvitations invitations={invitations ?? []} actorRole={account.role} />
          </section>
        </>
      )}
    </>
  );
}
