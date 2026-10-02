import { describe, expect, it } from "vitest";
import { listHref, parseListQuery, validateCustomer } from "./customers";

describe("parseListQuery", () => {
  it("defaults to active customers on the first page", () => {
    expect(parseListQuery({})).toEqual({ status: "ACTIVE", q: "", page: 0 });
  });

  it("ignores values the backend would reject", () => {
    expect(parseListQuery({ status: "DELETED", page: "-3", q: "  João  " })).toEqual({
      status: "ACTIVE",
      q: "João",
      page: 0,
    });
    expect(parseListQuery({ status: "archived", page: "2" })).toEqual({ status: "ARCHIVED", q: "", page: 2 });
    expect(parseListQuery({ q: "x".repeat(300) }).q).toHaveLength(100);
  });
});

describe("listHref", () => {
  it("keeps the URL short for the default view and encodes the search", () => {
    expect(listHref({ status: "ACTIVE" })).toBe("/app/customers");
    expect(listHref({ status: "ARCHIVED", q: "joão & cia", page: 1 })).toBe(
      "/app/customers?status=ARCHIVED&q=jo%C3%A3o+%26+cia&page=1",
    );
  });
});

describe("validateCustomer", () => {
  const valid = { name: "João", phone: "", email: "", notes: "" };

  it("requires only the name", () => {
    expect(validateCustomer(valid)).toEqual({});
    expect(validateCustomer({ ...valid, name: "   " }).name).toBe("Informe o nome do cliente.");
  });

  it("applies the same limits as the backend", () => {
    const errors = validateCustomer({
      name: "a".repeat(151),
      phone: "9".repeat(41),
      email: "nao-e-email",
      notes: "x".repeat(4001),
    });

    expect(Object.keys(errors).sort()).toEqual(["email", "name", "notes", "phone"]);
    expect(validateCustomer({ ...valid, name: "a".repeat(150), notes: "x".repeat(4000) })).toEqual({});
  });
});
