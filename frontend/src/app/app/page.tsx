import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Visão geral | Orça Aí",
};

export default function OverviewPage() {
  return (
    <>
      <h1>Visão geral</h1>
      <section className="empty-state">
        <p className="empty-state-title">Seu espaço está pronto.</p>
        <p>O próximo passo será cadastrar seu primeiro cliente.</p>
      </section>
    </>
  );
}
