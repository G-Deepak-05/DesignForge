import type { AuthResponse, RegisterFields, UserResponse } from "@designforge/shared-types";
import { apiFetch } from "./client";

export function login(email: string, password: string): Promise<AuthResponse> {
  return apiFetch<AuthResponse>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify({ email, password }),
  });
}

export function register(fields: RegisterFields): Promise<UserResponse> {
  return apiFetch<UserResponse>("/api/auth/register", {
    method: "POST",
    body: JSON.stringify(fields),
  });
}
