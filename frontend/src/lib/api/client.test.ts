import { afterEach, describe, expect, it, vi } from "vitest";
import { apiFetch, ApiError } from "./client";

function mockFetch(response: Response) {
  const fetchMock = vi.fn().mockResolvedValue(response);
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function sentHeaders(fetchMock: ReturnType<typeof vi.fn>): Headers {
  return fetchMock.mock.calls[0][1].headers as Headers;
}

describe("apiFetch", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("sends the CSRF token on state-changing requests", async () => {
    vi.stubGlobal("document", { cookie: "other=1; XSRF-TOKEN=abc%3D123" });
    const fetchMock = mockFetch(new Response(null, { status: 204 }));

    await apiFetch("/auth/logout", { method: "POST" });

    expect(fetchMock.mock.calls[0][0]).toBe("/api/auth/logout");
    expect(sentHeaders(fetchMock).get("X-XSRF-TOKEN")).toBe("abc=123");
  });

  it("does not send the CSRF token on safe requests", async () => {
    vi.stubGlobal("document", { cookie: "XSRF-TOKEN=abc" });
    const fetchMock = mockFetch(Response.json({ ok: true }));

    await expect(apiFetch("/auth/me")).resolves.toEqual({ ok: true });
    expect(sentHeaders(fetchMock).has("X-XSRF-TOKEN")).toBe(false);
  });

  it("accepts successful answers without a body", async () => {
    vi.stubGlobal("document", { cookie: "XSRF-TOKEN=abc" });
    mockFetch(new Response(null, { status: 201 }));

    await expect(apiFetch("/invitations/accept", { method: "POST" })).resolves.toBeUndefined();
  });

  it("raises ApiError with the problem details", async () => {
    mockFetch(Response.json({ title: "Bad Request", detail: "Dados inválidos." }, { status: 400 }));

    const error = await apiFetch("/anything").catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(400);
    expect((error as ApiError).message).toBe("Dados inválidos.");
  });
});
