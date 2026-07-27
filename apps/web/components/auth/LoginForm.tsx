"use client";

import { useState } from "react";
import { login } from "@/lib/api/auth";

export function LoginForm({ onSuccess }: { onSuccess: () => void }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await login(email, password);
      onSuccess();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Login failed");
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4 max-w-sm">
      <label className="flex flex-col gap-1">
        <span>Email</span>
        <input
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          className="border rounded-md px-3 py-2"
          required
        />
      </label>
      <label className="flex flex-col gap-1">
        <span>Password</span>
        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          className="border rounded-md px-3 py-2"
          required
        />
      </label>
      {error && <p className="text-sm text-red-600">{error}</p>}
      <button type="submit" className="rounded-md bg-primary px-4 py-2 text-primary-foreground">
        Log in
      </button>
    </form>
  );
}
