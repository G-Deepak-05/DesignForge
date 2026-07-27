"use client";

import { useRouter } from "next/navigation";
import { RegisterForm } from "@/components/auth/RegisterForm";

export default function RegisterPage() {
  const router = useRouter();
  return (
    <div>
      <h1 className="text-xl font-semibold mb-4">Create your account</h1>
      <RegisterForm onSuccess={() => router.push("/login")} />
    </div>
  );
}
