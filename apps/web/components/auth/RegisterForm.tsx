"use client";

import { useState } from "react";
import { register } from "@/lib/api/auth";

export function RegisterForm({ onSuccess }: { onSuccess: () => void }) {
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await register({ displayName, email, password, locale: "en" });
      onSuccess();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Registration failed");
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4 max-w-sm">
      <label className="flex flex-col gap-1">
        <span>Display name</span>
        <input
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          className="border rounded-md px-3 py-2"
          required
        />
      </label>
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
        Create account
      </button>
    </form>
  );
}
