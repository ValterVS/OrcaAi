import { afterEach, describe, expect, it, vi } from "vitest";
import { archiveCustomer, updateCustomer } from "./customers";

function mockFetch(...responses: Response[]) {
  const fetchMock = vi.fn();
  for (const response of responses) {
    fetchMock.mockResolvedValueOnce(response);
  }
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

describe("customer API", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("sends the version read in If-Match together with the CSRF token", async () => {
    vi.stubGlobal("document", { cookie: "XSRF-TOKEN=abc" });
    const fetchMock = mockFetch(Response.json({ id: "c 1" }));

    await updateCustomer("c 1", 7, { name: "João", phone: "", email: "", notes: "" });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/customers/c%201");
    expect(init.method).toBe("PUT");
    expect(init.headers.get("If-Match")).toBe('"7"');
    expect(init.headers.get("X-XSRF-TOKEN")).toBe("abc");
  });

  it("obtains a CSRF token first when none is present", async () => {
    let cookie = "";
    vi.stubGlobal("document", {
      get cookie() {
        return cookie;
      },
    });
    const fetchMock = vi.fn().mockImplementation(async (url: string) => {
      if (url === "/api/auth/csrf") {
        cookie = "XSRF-TOKEN=novo";
        return new Response(null, { status: 204 });
      }
      return Response.json({ id: "c1" });
    });
    vi.stubGlobal("fetch", fetchMock);

    await archiveCustomer("c1");

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual(["/api/auth/csrf", "/api/customers/c1/archive"]);
    expect(fetchMock.mock.calls[1][1].headers.get("X-XSRF-TOKEN")).toBe("novo");
  });
});
