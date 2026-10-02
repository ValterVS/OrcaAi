import type { Metadata } from "next";
import Link from "next/link";
import { CustomerList, StatusFilter } from "@/components/customers/CustomerList";
import { CustomerSearchForm } from "@/components/customers/CustomerSearchForm";
import { getCustomers } from "@/lib/api/server";
import { parseListQuery } from "@/lib/customers";

export const metadata: Metadata = {
  title: "Clientes | Orça Aí",
};

type Props = { searchParams: Promise<Record<string, string | string[] | undefined>> };

export default async function CustomersPage({ searchParams }: Props) {
  const query = parseListQuery(await searchParams);
  const page = await getCustomers(query);

  return (
    <>
      <div className="page-header">
        <h1>Clientes</h1>
        <Link href="/app/customers/new" className="button-primary button-link">
          Novo cliente
        </Link>
      </div>
      <div className="list-toolbar">
        <CustomerSearchForm key={query.q} status={query.status} q={query.q} />
        <StatusFilter query={query} />
      </div>
      {query.status === "ARCHIVED" && <p className="notice-archived">Exibindo clientes arquivados.</p>}
      <CustomerList page={page} query={query} />
    </>
  );
}
