import { describe, expect, it } from "vitest";
import { buildContentSecurityPolicy, generateNonce } from "./csp";

describe("buildContentSecurityPolicy", () => {
  it("is strict in production", () => {
    const csp = buildContentSecurityPolicy({ nonce: "abc", isDevelopment: false, httpsOnly: true });

    expect(csp).toContain("script-src 'self' 'nonce-abc' 'strict-dynamic'");
    expect(csp).toContain("frame-ancestors 'none'");
    expect(csp).toContain("object-src 'none'");
    expect(csp).toContain("upgrade-insecure-requests");
    expect(csp).not.toContain("unsafe-eval");
    expect(csp).not.toContain("unsafe-inline");
  });

  it("relaxes only development tooling needs and does not force HTTPS locally", () => {
    const csp = buildContentSecurityPolicy({ nonce: "abc", isDevelopment: true, httpsOnly: false });

    expect(csp).toContain("'unsafe-eval'");
    expect(csp).toContain("style-src 'self' 'unsafe-inline'");
    expect(csp).toContain("script-src 'self' 'nonce-abc' 'strict-dynamic'");
    expect(csp).not.toContain("upgrade-insecure-requests");
  });
});

describe("generateNonce", () => {
  it("returns a fresh 128-bit value each time", () => {
    const first = generateNonce();

    expect(atob(first)).toHaveLength(16);
    expect(generateNonce()).not.toBe(first);
  });
});
