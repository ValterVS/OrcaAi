import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { Customer, CustomerPage } from "@/lib/api/customers";
import type { CustomerListQuery } from "@/lib/customers";
import { CustomerList, StatusFilter } from "./CustomerList";

const ACTIVE: CustomerListQuery = { status: "ACTIVE", q: "", page: 0 };

function customer(overrides: Partial<Customer> = {}): Customer {
  return {
    id: "01a0fe04-a7ff-7d6a-9225-eb2940169d74",
    name: "João da Silva",
    phone: "(11) 98888-7777",
    email: "joao@example.com",
    notes: null,
    archived: false,
    createdAt: "2026-10-02T15:00:00Z",
    updatedAt: "2026-10-02T15:30:00Z",
    version: 0,
    ...overrides,
  };
}

function page(items: Customer[], overrides: Partial<CustomerPage> = {}): CustomerPage {
  return { items, page: 0, size: 20, totalItems: items.length, totalPages: items.length ? 1 : 0, ...overrides };
}

describe("CustomerList", () => {
  it("invites to create the first customer when there are none", () => {
    render(<CustomerList page={page([])} query={ACTIVE} />);

    expect(screen.getByText("Nenhum cliente cadastrado ainda.")).toBeTruthy();
    expect(screen.getByRole("link", { name: "Novo cliente" }).getAttribute("href")).toBe("/app/customers/new");
    expect(screen.queryByRole("table")).toBeNull();
  });

  it("distinguishes an empty search and an empty archive from an empty account", () => {
    const { rerender } = render(<CustomerList page={page([])} query={{ ...ACTIVE, q: "inexistente" }} />);
    expect(screen.getByText("Nenhum cliente encontrado para esta busca.")).toBeTruthy();

    rerender(<CustomerList page={page([])} query={{ ...ACTIVE, status: "ARCHIVED" }} />);
    expect(screen.getByText("Nenhum cliente arquivado.")).toBeTruthy();
    expect(screen.queryByRole("link", { name: "Novo cliente" })).toBeNull();
  });

  it("lists customers with link, contact data, update time and archived badge", () => {
    render(
      <CustomerList
        page={page([customer(), customer({ id: "2", name: "Ana", phone: null, email: null, archived: true })])}
        query={{ ...ACTIVE, status: "ALL" }}
      />,
    );

    const rows = within(screen.getByRole("table")).getAllByRole("row");
    expect(rows).toHaveLength(3);
    expect(within(rows[1]).getByRole("link", { name: "João da Silva" }).getAttribute("href")).toBe(
      "/app/customers/01a0fe04-a7ff-7d6a-9225-eb2940169d74",
    );
    expect(rows[1].textContent).toContain("(11) 98888-7777");
    expect(rows[1].textContent).toContain("02/10/2026, 12:30");
    expect(rows[2].textContent).toContain("Arquivado");
    expect(rows[2].textContent).toContain("—");
  });

  it("pages through results keeping the search and status", () => {
    render(
      <CustomerList
        page={page([customer()], { page: 1, totalItems: 45, totalPages: 3 })}
        query={{ status: "ARCHIVED", q: "silva", page: 1 }}
      />,
    );

    expect(screen.getByText("Página 2 de 3 (45 clientes)")).toBeTruthy();
    expect(screen.getByRole("link", { name: "Anterior" }).getAttribute("href")).toBe(
      "/app/customers?status=ARCHIVED&q=silva",
    );
    expect(screen.getByRole("link", { name: "Próxima" }).getAttribute("href")).toBe(
      "/app/customers?status=ARCHIVED&q=silva&page=2",
    );
  });

  it("renders user-provided text as text, never as HTML", () => {
    const hostile = '<img src=x onerror="alert(1)"><script>alert(2)</script>';
    const { container } = render(
      <CustomerList page={page([customer({ name: hostile, email: hostile, phone: hostile })])} query={ACTIVE} />,
    );

    expect(container.querySelector("img")).toBeNull();
    expect(container.querySelector("script")).toBeNull();
    expect(screen.getByRole("link", { name: hostile })).toBeTruthy();
  });
});

describe("StatusFilter", () => {
  it("offers active, archived and all, keeping the search and marking the current one", () => {
    render(<StatusFilter query={{ status: "ARCHIVED", q: "silva", page: 2 }} />);

    expect(screen.getByRole("link", { name: "Ativos" }).getAttribute("href")).toBe("/app/customers?q=silva");
    const archived = screen.getByRole("link", { name: "Arquivados" });
    expect(archived.getAttribute("aria-current")).toBe("page");
    expect(archived.getAttribute("href")).toBe("/app/customers?status=ARCHIVED&q=silva");
    expect(screen.getByRole("link", { name: "Todos" }).getAttribute("href")).toBe("/app/customers?status=ALL&q=silva");
  });
});
