import "./globals.css";
import type { ReactNode } from "react";
import { ThemeProvider } from "@/components/theme/ThemeProvider";
import { AppShell } from "@/components/nav/AppShell";

export const metadata = {
  title: "DesignForge",
  description: "Master Low-Level Design (LLD) & High-Level Design (HLD) Interviews.",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body>
        <ThemeProvider>
          <AppShell>{children}</AppShell>
        </ThemeProvider>
      </body>
    </html>
  );
}
