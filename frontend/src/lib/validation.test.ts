import { describe, expect, it } from "vitest";
import { validateSignup } from "./validation";

const valid = {
  companyName: "Reformas Silva",
  ownerName: "Maria",
  email: "maria@example.com",
  password: "uma senha bem longa",
};

describe("validateSignup", () => {
  it("accepts a valid sign-up", () => {
    expect(validateSignup(valid)).toEqual({});
  });

  it("applies the same password policy as the backend", () => {
    expect(validateSignup({ ...valid, password: "x".repeat(11) }).password).toBeDefined();
    expect(validateSignup({ ...valid, password: "x".repeat(12) }).password).toBeUndefined();
    expect(validateSignup({ ...valid, password: "x".repeat(64) }).password).toBeUndefined();
    expect(validateSignup({ ...valid, password: "x".repeat(65) }).password).toBeDefined();
    // 40 characters but 80 bytes: beyond what BCrypt can use.
    expect(validateSignup({ ...valid, password: "ç".repeat(40) }).password).toBeDefined();
  });

  it("does not demand composition rules", () => {
    expect(validateSignup({ ...valid, password: "tudo minusculo sem simbolos" }).password).toBeUndefined();
  });

  it("rejects blank names and malformed emails", () => {
    const errors = validateSignup({ ...valid, companyName: "  ", ownerName: "", email: "maria@" });

    expect(Object.keys(errors).sort()).toEqual(["companyName", "email", "ownerName"]);
  });
});
