import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { CustomerDetail } from "@/components/customers/CustomerDetail";
import { getCurrentAccount, getCustomer } from "@/lib/api/server";
import { canArchiveCustomers } from "@/lib/roles";

export const metadata: Metadata = {
  title: "Cliente | Orça Aí",
};

type Props = {
  params: Promise<{ id: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
};

// A customer of another organization is "not found", exactly like a nonexistent one.
export default async function CustomerPage({ params, searchParams }: Props) {
  const { id } = await params;
  const [loaded, account, search] = await Promise.all([getCustomer(id), getCurrentAccount(), searchParams]);
  if (!loaded || !account) {
    notFound();
  }
  const { customer } = loaded;

  return (
    <>
      <p className="breadcrumb">
        <Link href="/app/customers">Clientes</Link>
      </p>
      <h1>{customer.name}</h1>
      <div className="panel">
        <CustomerDetail
          customer={customer}
          etag={loaded.etag}
          canArchive={canArchiveCustomers(account.role)}
          justCreated={search.created === "1"}
        />
      </div>
    </>
  );
}
