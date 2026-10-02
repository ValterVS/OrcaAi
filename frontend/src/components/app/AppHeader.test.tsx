import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { logout, type CurrentAccount } from "@/lib/api/auth";
import { apiError, router } from "@/test/mocks";
import { AppHeader } from "./AppHeader";

vi.mock("next/navigation", () => ({ useRouter: () => router }));
vi.mock("@/lib/api/auth", () => ({ logout: vi.fn() }));

const account: CurrentAccount = {
  userId: "01a0fd57-5034-7379-8bc3-fa395e4e8104",
  userName: "Maria Silva",
  role: "OWNER",
  organizationId: "01a0fd57-4fe9-701f-893e-cacad98bc4a3",
  organizationName: "Reformas Silva",
};

describe("AppHeader", () => {
  it("shows the organization, the user and the role", () => {
    render(<AppHeader account={account} />);

    expect(screen.getByText("Orça Aí")).toBeTruthy();
    expect(screen.getByText("Reformas Silva")).toBeTruthy();
    expect(screen.getByText("Maria Silva")).toBeTruthy();
    expect(screen.getByText("Proprietário")).toBeTruthy();
  });

  it("logs out and goes to the login page", async () => {
    vi.mocked(logout).mockResolvedValue();
    render(<AppHeader account={account} />);

    await userEvent.setup().click(screen.getByRole("button", { name: "Sair" }));

    await waitFor(() => expect(router.replace).toHaveBeenCalledWith("/login"));
    expect(logout).toHaveBeenCalledOnce();
  });

  it("stays on the page and says so when logout fails", async () => {
    vi.mocked(logout).mockRejectedValue(apiError({ status: 500 }));
    render(<AppHeader account={account} />);

    await userEvent.setup().click(screen.getByRole("button", { name: "Sair" }));

    expect((await screen.findByRole("alert")).textContent).toBe("Não foi possível sair. Tente novamente.");
    expect(router.replace).not.toHaveBeenCalled();
  });
});
