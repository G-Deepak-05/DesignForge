"use client";

import { useRouter } from "next/navigation";
import { LoginForm } from "@/components/auth/LoginForm";

export default function LoginPage() {
  const router = useRouter();
  return (
    <div>
      <h1 className="text-xl font-semibold mb-4">Log in</h1>
      <LoginForm onSuccess={() => router.push("/")} />
    </div>
  );
}
