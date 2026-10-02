"use client";

import { useEffect, useState } from "react";

/**
 * Reads the token from the URL fragment (#token=...). Fragments are never sent to a server, so the
 * token stays out of access logs and Referer headers. It is removed from the address bar at once.
 *
 * Returns undefined until the browser has been read, then the token or null.
 */
export function useFragmentToken(): string | null | undefined {
  const [token, setToken] = useState<string | null | undefined>(undefined);

  useEffect(() => {
    const value = new URLSearchParams(window.location.hash.slice(1)).get("token");
    if (window.location.hash) {
      window.history.replaceState(null, "", window.location.pathname + window.location.search);
    }
    // Reading a browser-only value after hydration is the intended use of this effect.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setToken(value);
  }, []);

  return token;
}
