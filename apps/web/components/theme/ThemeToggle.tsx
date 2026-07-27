"use client";

import { Moon, Sun } from "lucide-react";
import { useTheme } from "next-themes";

export function ThemeToggle() {
  const { resolvedTheme, setTheme } = useTheme();

  return (
    <button
      aria-label="Toggle theme"
      onClick={() => setTheme(resolvedTheme === "dark" ? "light" : "dark")}
      className="rounded-md p-2 hover:bg-muted"
    >
      <Sun className="hidden dark:block h-5 w-5" />
      <Moon className="block dark:hidden h-5 w-5" />
    </button>
  );
}
