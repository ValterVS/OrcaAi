import type { Metadata } from "next";
import { connection } from "next/server";
import type { ReactNode } from "react";
import "./globals.css";

export const metadata: Metadata = {
  title: "Orça Aí",
  description: "Orçamentos e propostas para obras e reformas",
};

export default async function RootLayout({ children }: { children: ReactNode }) {
  // The CSP nonce is per request, so pages cannot be prerendered.
  await connection();

  return (
    <html lang="pt-BR">
      <body>{children}</body>
    </html>
  );
}
