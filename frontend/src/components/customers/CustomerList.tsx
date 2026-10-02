import Link from "next/link";
import type { CustomerPage } from "@/lib/api/customers";
import { formatDateTime, listHref, STATUS_LABELS, type CustomerListQuery } from "@/lib/customers";

export function StatusFilter({ query }: { query: CustomerListQuery }) {
  return (
    <nav className="status-filter" aria-label="Situação">
      {(["ACTIVE", "ARCHIVED", "ALL"] as const).map((status) => (
        <Link
          key={status}
          href={listHref({ status, q: query.q })}
          aria-current={query.status === status ? "page" : undefined}
        >
          {STATUS_LABELS[status]}
        </Link>
      ))}
    </nav>
  );
}

function EmptyState({ query }: { query: CustomerListQuery }) {
  if (query.q) {
    return <p className="empty-state-title">Nenhum cliente encontrado para esta busca.</p>;
  }
  if (query.status === "ARCHIVED") {
    return <p className="empty-state-title">Nenhum cliente arquivado.</p>;
  }
  return (
    <>
      <p className="empty-state-title">Nenhum cliente cadastrado ainda.</p>
      <p>Cadastre o primeiro cliente para começar a organizar seus orçamentos.</p>
      <Link href="/app/customers/new" className="button-primary button-link">
        Novo cliente
      </Link>
    </>
  );
}

export function CustomerList({ page, query }: { page: CustomerPage; query: CustomerListQuery }) {
  if (page.items.length === 0) {
    return (
      <section className="empty-state">
        <EmptyState query={query} />
      </section>
    );
  }

  return (
    <>
      <div className="table-scroll">
        <table className="data-table">
          <thead>
            <tr>
              <th scope="col">Nome</th>
              <th scope="col" className="hide-small">Telefone</th>
              <th scope="col" className="hide-small">E-mail</th>
              <th scope="col">Atualizado em</th>
            </tr>
          </thead>
          <tbody>
            {page.items.map((customer) => (
              <tr key={customer.id}>
                <td>
                  <Link href={`/app/customers/${customer.id}`}>{customer.name}</Link>
                  {customer.archived && <span className="badge">Arquivado</span>}
                </td>
                <td className="hide-small">{customer.phone ?? "—"}</td>
                <td className="hide-small">{customer.email ?? "—"}</td>
                <td>{formatDateTime(customer.updatedAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pagination page={page} query={query} />
    </>
  );
}

function Pagination({ page, query }: { page: CustomerPage; query: CustomerListQuery }) {
  if (page.totalPages <= 1) {
    return null;
  }
  const hasPrevious = page.page > 0;
  const hasNext = page.page + 1 < page.totalPages;

  return (
    <nav className="pagination" aria-label="Paginação">
      {hasPrevious ? <Link href={listHref({ ...query, page: page.page - 1 })}>Anterior</Link> : <span />}
      <span>
        Página {page.page + 1} de {page.totalPages} ({page.totalItems} clientes)
      </span>
      {hasNext ? <Link href={listHref({ ...query, page: page.page + 1 })}>Próxima</Link> : <span />}
    </nav>
  );
}
