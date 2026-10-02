import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import OverviewPage from "./page";

describe("OverviewPage", () => {
  it("shows a real empty state instead of invented numbers", () => {
    const { container } = render(<OverviewPage />);

    expect(screen.getByRole("heading", { name: "Visão geral" })).toBeTruthy();
    expect(screen.getByText("Seu espaço está pronto.")).toBeTruthy();
    expect(screen.getByText("O próximo passo será cadastrar seu primeiro cliente.")).toBeTruthy();
    expect(container.textContent).not.toMatch(/\d/);
  });
});
