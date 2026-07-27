import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import * as authApi from "@/lib/api/auth";
import { RegisterForm } from "./RegisterForm";

describe("RegisterForm", () => {
  it("submits registration fields and calls onSuccess", async () => {
    const registerSpy = vi.spyOn(authApi, "register").mockResolvedValue({
      id: "1",
      email: "jane@example.com",
      displayName: "Jane",
      locale: "en",
    });
    const onSuccess = vi.fn();

    render(<RegisterForm onSuccess={onSuccess} />);
    fireEvent.change(screen.getByLabelText(/display name/i), { target: { value: "Jane" } });
    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: "jane@example.com" } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: "password123" } });
    fireEvent.click(screen.getByRole("button", { name: /create account/i }));

    await waitFor(() =>
      expect(registerSpy).toHaveBeenCalledWith({
        email: "jane@example.com",
        password: "password123",
        displayName: "Jane",
        locale: "en",
      })
    );
    await waitFor(() => expect(onSuccess).toHaveBeenCalled());
  });
});
