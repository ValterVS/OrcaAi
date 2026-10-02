import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { createCustomer, type Customer } from "@/lib/api/customers";
import { apiError, router } from "@/test/mocks";
import { CustomerSearchForm } from "./CustomerSearchForm";
import { NewCustomerForm } from "./NewCustomerForm";

vi.mock("next/navigation", () => ({ useRouter: () => router }));
vi.mock("@/lib/api/customers", () => ({ createCustomer: vi.fn() }));

const created: Customer = {
  id: "01a0fe04-a7ff-7d6a-9225-eb2940169d74",
  name: "João da Silva",
  phone: null,
  email: null,
  notes: null,
  archived: false,
  createdAt: "2026-10-02T15:00:00Z",
  updatedAt: "2026-10-02T15:00:00Z",
};

describe("NewCustomerForm", () => {
  beforeEach(() => {
    vi.mocked(createCustomer).mockResolvedValue(created);
  });

  it("validates before sending", async () => {
    render(<NewCustomerForm />);
    const user = userEvent.setup();
    await user.type(screen.getByLabelText("E-mail"), "nao-e-email");

    await user.click(screen.getByRole("button", { name: "Salvar cliente" }));

    expect(screen.getByText("Informe o nome do cliente.")).toBeTruthy();
    expect(screen.getByText("Informe um e-mail válido.")).toBeTruthy();
    expect(createCustomer).not.toHaveBeenCalled();
  });

  it("creates the customer and opens it with a success message", async () => {
    render(<NewCustomerForm />);
    const user = userEvent.setup();
    await user.type(screen.getByLabelText("Nome *"), "João da Silva");
    await user.type(screen.getByLabelText("Telefone"), "11 98888-7777");
    await user.type(screen.getByLabelText("Observações"), "Prefere à tarde");

    await user.click(screen.getByRole("button", { name: "Salvar cliente" }));

    await waitFor(() =>
      expect(router.push).toHaveBeenCalledWith("/app/customers/01a0fe04-a7ff-7d6a-9225-eb2940169d74?created=1"),
    );
    expect(createCustomer).toHaveBeenCalledWith({
      name: "João da Silva",
      phone: "11 98888-7777",
      email: "",
      notes: "Prefere à tarde",
    });
  });

  it("shows server validation next to the fields and generic failures as a message", async () => {
    vi.mocked(createCustomer).mockRejectedValueOnce(
      apiError({ status: 400, errors: [{ field: "phone", message: "O telefone deve ter no máximo 40 caracteres." }] }),
    );
    render(<NewCustomerForm />);
    const user = userEvent.setup();
    await user.type(screen.getByLabelText("Nome *"), "João");

    await user.click(screen.getByRole("button", { name: "Salvar cliente" }));
    expect(await screen.findByText("O telefone deve ter no máximo 40 caracteres.")).toBeTruthy();

    vi.mocked(createCustomer).mockRejectedValueOnce(apiError({ status: 500, detail: "Erro interno." }));
    await user.click(screen.getByRole("button", { name: "Salvar cliente" }));
    expect((await screen.findByRole("alert")).textContent).toBe(
      "Não foi possível concluir agora. Tente novamente em instantes.",
    );
    expect(router.push).not.toHaveBeenCalled();
  });

  it("disables saving while the request is in flight", async () => {
    let finish!: (customer: Customer) => void;
    vi.mocked(createCustomer).mockReturnValue(new Promise((resolve) => (finish = resolve)));
    render(<NewCustomerForm />);
    const user = userEvent.setup();
    await user.type(screen.getByLabelText("Nome *"), "João");

    await user.click(screen.getByRole("button", { name: "Salvar cliente" }));

    expect((screen.getByRole("button", { name: "Salvando..." }) as HTMLButtonElement).disabled).toBe(true);
    finish(created);
    await waitFor(() => expect(router.push).toHaveBeenCalled());
  });
});

describe("CustomerSearchForm", () => {
  it("searches from the first page keeping the selected status", async () => {
    render(<CustomerSearchForm status="ARCHIVED" q="" />);
    const user = userEvent.setup();

    await user.type(screen.getByRole("searchbox", { name: "Buscar clientes" }), "  silva  ");
    await user.click(screen.getByRole("button", { name: "Buscar" }));

    expect(router.push).toHaveBeenCalledWith("/app/customers?status=ARCHIVED&q=silva");
  });

  it("clearing the search shows the full list again", async () => {
    render(<CustomerSearchForm status="ACTIVE" q="silva" />);
    const user = userEvent.setup();

    await user.clear(screen.getByRole("searchbox", { name: "Buscar clientes" }));
    await user.click(screen.getByRole("button", { name: "Buscar" }));

    expect(router.push).toHaveBeenCalledWith("/app/customers");
  });
});
