import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import * as authApi from "@/lib/api/auth";
import { LoginForm } from "./LoginForm";

describe("LoginForm", () => {
  it("submits email and password and calls onSuccess", async () => {
    const loginSpy = vi.spyOn(authApi, "login").mockResolvedValue({
      accessToken: "token",
      refreshToken: "refresh",
      user: { id: "1", email: "jane@example.com", displayName: "Jane", locale: "en" },
    });
    const onSuccess = vi.fn();

    render(<LoginForm onSuccess={onSuccess} />);
    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: "jane@example.com" } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: "password123" } });
    fireEvent.click(screen.getByRole("button", { name: /log in/i }));

    await waitFor(() => expect(loginSpy).toHaveBeenCalledWith("jane@example.com", "password123"));
    await waitFor(() => expect(onSuccess).toHaveBeenCalled());
  });

  it("shows an error message when login fails", async () => {
    vi.spyOn(authApi, "login").mockRejectedValue(new Error("Invalid email or password"));

    render(<LoginForm onSuccess={vi.fn()} />);
    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: "jane@example.com" } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: "wrong" } });
    fireEvent.click(screen.getByRole("button", { name: /log in/i }));

    expect(await screen.findByText(/invalid email or password/i)).toBeInTheDocument();
  });
});
