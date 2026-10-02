"use client";

import { useRouter } from "next/navigation";
import { createCustomer } from "@/lib/api/customers";
import { CustomerForm } from "./CustomerForm";

export function NewCustomerForm() {
  const router = useRouter();

  return (
    <CustomerForm
      submitLabel="Salvar cliente"
      pendingLabel="Salvando..."
      onSubmit={createCustomer}
      onSaved={(customer) => router.push(`/app/customers/${customer.id}?created=1`)}
      onCancel={() => router.push("/app/customers")}
    />
  );
}
