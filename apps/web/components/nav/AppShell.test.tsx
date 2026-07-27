import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AppShell } from "./AppShell";

describe("AppShell", () => {
  it("renders the primary navigation links", () => {
    render(<AppShell><div>content</div></AppShell>);

    expect(screen.getByRole("link", { name: /learning hub/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /pattern explorer/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /interviews/i })).toBeInTheDocument();
  });

  it("renders the theme toggle button", () => {
    render(<AppShell><div>content</div></AppShell>);

    expect(screen.getByRole("button", { name: /toggle theme/i })).toBeInTheDocument();
  });

  it("renders the page content passed as children", () => {
    render(<AppShell><div>unique-content-marker</div></AppShell>);

    expect(screen.getByText("unique-content-marker")).toBeInTheDocument();
  });
});
