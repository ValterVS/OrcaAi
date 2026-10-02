import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { archiveCustomer, restoreCustomer, updateCustomer, type Customer } from "@/lib/api/customers";
import { apiError, router } from "@/test/mocks";
import { CustomerDetail } from "./CustomerDetail";

vi.mock("next/navigation", () => ({ useRouter: () => router }));
vi.mock("@/lib/api/customers", () => ({
  updateCustomer: vi.fn(),
  archiveCustomer: vi.fn(),
  restoreCustomer: vi.fn(),
}));

const customer: Customer = {
  id: "c-1",
  name: "João da Silva",
  phone: "11 98888-7777",
  email: null,
  notes: "Linha 1\nLinha 2",
  archived: false,
  createdAt: "2026-10-02T15:00:00Z",
  updatedAt: "2026-10-02T15:30:00Z",
  version: 3,
};

describe("CustomerDetail", () => {
  it("shows the data and a success message right after creation", () => {
    render(<CustomerDetail customer={customer} canArchive justCreated />);

    expect(screen.getByRole("status").textContent).toBe("Cliente cadastrado com sucesso.");
    expect(screen.getByText("11 98888-7777")).toBeTruthy();
    expect(screen.getByText("Não informado")).toBeTruthy();
    expect(screen.getByText(/Linha 1/).textContent).toBe("Linha 1\nLinha 2");
  });

  it("renders hostile notes as plain text", () => {
    const { container } = render(
      <CustomerDetail
        customer={{ ...customer, notes: '<img src=x onerror="alert(1)">', name: "<b>negrito</b>" }}
        canArchive={false}
        justCreated={false}
      />,
    );

    expect(container.querySelector("img")).toBeNull();
    expect(container.querySelector("b")).toBeNull();
    expect(screen.getByText('<img src=x onerror="alert(1)">')).toBeTruthy();
  });

  it("edits with the version it was loaded with and refreshes", async () => {
    vi.mocked(updateCustomer).mockResolvedValue({ ...customer, name: "João Silva", version: 4 });
    render(<CustomerDetail customer={customer} canArchive={false} justCreated={false} />);
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: "Editar" }));
    const name = screen.getByLabelText("Nome *");
    await user.clear(name);
    await user.type(name, "João Silva");
    await user.click(screen.getByRole("button", { name: "Salvar alterações" }));

    expect((await screen.findByRole("status")).textContent).toBe("Alterações salvas.");
    expect(updateCustomer).toHaveBeenCalledWith("c-1", 3, {
      name: "João Silva",
      phone: "11 98888-7777",
      email: "",
      notes: "Linha 1\nLinha 2",
    });
    expect(router.refresh).toHaveBeenCalled();
  });

  it("explains a concurrent change instead of overwriting it", async () => {
    const detail = "Este registro foi alterado por outra pessoa. Recarregue a página para ver a versão mais recente.";
    vi.mocked(updateCustomer).mockRejectedValue(apiError({ status: 409, detail }));
    render(<CustomerDetail customer={customer} canArchive={false} justCreated={false} />);
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: "Editar" }));
    await user.click(screen.getByRole("button", { name: "Salvar alterações" }));

    expect((await screen.findByRole("alert")).textContent).toBe(detail);
  });

  it("lets owners and admins archive and restore", async () => {
    vi.mocked(archiveCustomer).mockResolvedValue({ ...customer, archived: true });
    vi.mocked(restoreCustomer).mockResolvedValue(customer);
    const { rerender } = render(<CustomerDetail customer={customer} canArchive justCreated={false} />);
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: "Arquivar cliente" }));
    expect((await screen.findByRole("status")).textContent).toBe("Cliente arquivado.");
    expect(archiveCustomer).toHaveBeenCalledWith("c-1");

    rerender(<CustomerDetail customer={{ ...customer, archived: true }} canArchive justCreated={false} />);
    expect(screen.getByText("Este cliente está arquivado.")).toBeTruthy();
    await user.click(screen.getByRole("button", { name: "Restaurar cliente" }));
    await waitFor(() => expect(restoreCustomer).toHaveBeenCalledWith("c-1"));
    expect((await screen.findByRole("status")).textContent).toBe("Cliente restaurado.");
  });

  it("hides archive actions from members and reports failures", async () => {
    const { rerender } = render(<CustomerDetail customer={customer} canArchive={false} justCreated={false} />);
    expect(screen.queryByRole("button", { name: "Arquivar cliente" })).toBeNull();

    vi.mocked(archiveCustomer).mockRejectedValue(apiError({ status: 403 }));
    rerender(<CustomerDetail customer={customer} canArchive justCreated={false} />);
    await userEvent.setup().click(screen.getByRole("button", { name: "Arquivar cliente" }));

    expect((await screen.findByRole("alert")).textContent).toBe(
      "Não foi possível concluir agora. Tente novamente em instantes.",
    );
  });
});
