import Link from "next/link";
import type { ReactNode } from "react";
import { ThemeToggle } from "@/components/theme/ThemeToggle";

const NAV_ITEMS = [
  { href: "/learning", label: "Learning Hub" },
  { href: "/patterns", label: "Pattern Explorer" },
  { href: "/interviews", label: "Interviews" },
];

export function AppShell({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen bg-background text-foreground">
      <header className="flex items-center justify-between border-b px-6 py-4">
        <span className="font-semibold">DesignForge</span>
        <nav className="flex items-center gap-4">
          {NAV_ITEMS.map((item) => (
            <Link key={item.href} href={item.href} className="text-sm hover:underline">
              {item.label}
            </Link>
          ))}
          <ThemeToggle />
        </nav>
      </header>
      <main className="px-6 py-8">{children}</main>
    </div>
  );
}
