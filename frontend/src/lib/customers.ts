import type { CustomerInput, CustomerStatus } from "./api/customers";
import type { FieldErrors } from "./validation";

const STATUSES: CustomerStatus[] = ["ACTIVE", "ARCHIVED", "ALL"];
const MAX_SEARCH_LENGTH = 100;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+$/;

export const STATUS_LABELS: Record<CustomerStatus, string> = {
  ACTIVE: "Ativos",
  ARCHIVED: "Arquivados",
  ALL: "Todos",
};

export type CustomerListQuery = { status: CustomerStatus; q: string; page: number };

type RawSearchParams = Record<string, string | string[] | undefined>;

function first(value: string | string[] | undefined): string {
  return (Array.isArray(value) ? value[0] : value) ?? "";
}

/** Turns the URL into a query the backend accepts; anything unexpected falls back to the defaults. */
export function parseListQuery(params: RawSearchParams): CustomerListQuery {
  const status = first(params.status).toUpperCase() as CustomerStatus;
  const page = Number.parseInt(first(params.page), 10);
  return {
    status: STATUSES.includes(status) ? status : "ACTIVE",
    q: first(params.q).trim().slice(0, MAX_SEARCH_LENGTH),
    page: Number.isInteger(page) && page > 0 ? page : 0,
  };
}

export function listHref(query: Partial<CustomerListQuery>): string {
  const params = new URLSearchParams();
  if (query.status && query.status !== "ACTIVE") {
    params.set("status", query.status);
  }
  if (query.q) {
    params.set("q", query.q);
  }
  if (query.page) {
    params.set("page", String(query.page));
  }
  const search = params.toString();
  return search ? `/app/customers?${search}` : "/app/customers";
}

export function validateCustomer(input: CustomerInput): FieldErrors {
  const errors: FieldErrors = {};
  const name = input.name.trim();
  if (!name) {
    errors.name = "Informe o nome do cliente.";
  } else if (name.length > 150) {
    errors.name = "O nome deve ter no máximo 150 caracteres.";
  }
  if (input.phone.trim().length > 40) {
    errors.phone = "O telefone deve ter no máximo 40 caracteres.";
  }
  const email = input.email.trim();
  if (email.length > 254) {
    errors.email = "O e-mail deve ter no máximo 254 caracteres.";
  } else if (email && !EMAIL_PATTERN.test(email)) {
    errors.email = "Informe um e-mail válido.";
  }
  if (input.notes.trim().length > 4000) {
    errors.notes = "As observações devem ter no máximo 4000 caracteres.";
  }
  return errors;
}

const dateTime = new Intl.DateTimeFormat("pt-BR", {
  dateStyle: "short",
  timeStyle: "short",
  timeZone: "America/Sao_Paulo",
});

export function formatDateTime(iso: string): string {
  return dateTime.format(new Date(iso));
}
