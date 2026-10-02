"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { logout } from "@/lib/api/auth";

export function LogoutButton() {
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [failed, setFailed] = useState(false);

  async function handleLogout() {
    setPending(true);
    setFailed(false);
    try {
      await logout();
      router.replace("/login");
      router.refresh();
    } catch {
      setFailed(true);
      setPending(false);
    }
  }

  return (
    <div className="logout">
      <button type="button" className="button-secondary" onClick={handleLogout} disabled={pending}>
        {pending ? "Saindo..." : "Sair"}
      </button>
      {failed && (
        <p className="form-error" role="alert">
          Não foi possível sair. Tente novamente.
        </p>
      )}
    </div>
  );
}
