import type { Metadata } from "next";
import Link from "next/link";
import { NewCustomerForm } from "@/components/customers/NewCustomerForm";

export const metadata: Metadata = {
  title: "Novo cliente | Orça Aí",
};

export default function NewCustomerPage() {
  return (
    <>
      <p className="breadcrumb">
        <Link href="/app/customers">Clientes</Link>
      </p>
      <h1>Novo cliente</h1>
      <div className="panel">
        <NewCustomerForm />
      </div>
    </>
  );
}
