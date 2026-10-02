import { redirect } from "next/navigation";

// /app sends visitors without a session to /login.
export default function HomePage() {
  redirect("/app");
}
