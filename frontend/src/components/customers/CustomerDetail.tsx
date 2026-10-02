"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { archiveCustomer, restoreCustomer, updateCustomer, type Customer } from "@/lib/api/customers";
import { formatDateTime } from "@/lib/customers";
import { genericErrorMessage } from "@/lib/messages";
import { CustomerForm } from "./CustomerForm";

type CustomerDetailProps = {
  customer: Customer;
  canArchive: boolean;
  justCreated: boolean;
};

function Field({ label, value }: { label: string; value: string | null }) {
  return (
    <div className="detail-field">
      <dt>{label}</dt>
      <dd>{value ?? "Não informado"}</dd>
    </div>
  );
}

/** All values are rendered as text; nothing the user typed is ever interpreted as HTML. */
export function CustomerDetail({ customer, canArchive, justCreated }: CustomerDetailProps) {
  const router = useRouter();
  const [editing, setEditing] = useState(false);
  const [notice, setNotice] = useState<string | null>(justCreated ? "Cliente cadastrado com sucesso." : null);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function changeArchive(action: typeof archiveCustomer, done: string) {
    setPending(true);
    setError(null);
    setNotice(null);
    try {
      await action(customer.id);
      setNotice(done);
      router.refresh();
    } catch (failure) {
      setError(genericErrorMessage(failure));
    } finally {
      setPending(false);
    }
  }

  if (editing) {
    return (
      <CustomerForm
        initial={{
          name: customer.name,
          phone: customer.phone ?? "",
          email: customer.email ?? "",
          notes: customer.notes ?? "",
        }}
        submitLabel="Salvar alterações"
        pendingLabel="Salvando..."
        onSubmit={(input) => updateCustomer(customer.id, customer.version, input)}
        onSaved={() => {
          setEditing(false);
          setNotice("Alterações salvas.");
          router.refresh();
        }}
        onCancel={() => setEditing(false)}
      />
    );
  }

  return (
    <div className="customer-detail">
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
      {customer.archived && <p className="notice-archived">Este cliente está arquivado.</p>}

      <dl className="detail-list">
        <Field label="Nome" value={customer.name} />
        <Field label="Telefone" value={customer.phone} />
        <Field label="E-mail" value={customer.email} />
        <div className="detail-field">
          <dt>Observações</dt>
          <dd className="notes">{customer.notes ?? "Não informado"}</dd>
        </div>
        <Field label="Atualizado em" value={formatDateTime(customer.updatedAt)} />
      </dl>

      <div className="form-actions">
        <button
          type="button"
          className="button-primary"
          onClick={() => {
            setNotice(null);
            setEditing(true);
          }}
          disabled={pending}
        >
          Editar
        </button>
        {canArchive &&
          (customer.archived ? (
            <button
              type="button"
              className="button-secondary"
              onClick={() => changeArchive(restoreCustomer, "Cliente restaurado.")}
              disabled={pending}
            >
              Restaurar cliente
            </button>
          ) : (
            <button
              type="button"
              className="button-secondary"
              onClick={() => changeArchive(archiveCustomer, "Cliente arquivado.")}
              disabled={pending}
            >
              Arquivar cliente
            </button>
          ))}
      </div>
    </div>
  );
}
