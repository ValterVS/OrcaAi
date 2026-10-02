"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import type { CustomerStatus } from "@/lib/api/customers";
import { listHref } from "@/lib/customers";

/** Submitting starts again from the first page and keeps the selected status. */
export function CustomerSearchForm({ status, q }: { status: CustomerStatus; q: string }) {
  const router = useRouter();
  const [term, setTerm] = useState(q);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    router.push(listHref({ status, q: term.trim() }));
  }

  return (
    <form className="search-form" role="search" onSubmit={handleSubmit}>
      <label htmlFor="customer-search" className="visually-hidden">
        Buscar clientes
      </label>
      <input
        id="customer-search"
        type="search"
        placeholder="Buscar clientes"
        maxLength={100}
        value={term}
        onChange={(event) => setTerm(event.target.value)}
      />
      <button type="submit" className="button-secondary">
        Buscar
      </button>
    </form>
  );
}
