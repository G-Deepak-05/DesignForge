// apps/web/e2e/auth-flow.spec.ts
import { test, expect } from "@playwright/test";

test("a new user can register, log in, and see the app shell", async ({ page }) => {
  const uniqueEmail = `e2e-${Date.now()}@example.com`;

  await page.goto("/register");
  await page.getByLabel(/display name/i).fill("E2E Tester");
  await page.getByLabel(/email/i).fill(uniqueEmail);
  await page.getByLabel(/password/i).fill("password123");
  await page.getByRole("button", { name: /create account/i }).click();

  await expect(page).toHaveURL(/\/login/);

  await page.getByLabel(/email/i).fill(uniqueEmail);
  await page.getByLabel(/password/i).fill("password123");
  await page.getByRole("button", { name: /log in/i }).click();

  await expect(page).toHaveURL("http://localhost:3000/");
  await expect(page.getByRole("link", { name: /learning hub/i })).toBeVisible();
  await expect(page.getByRole("link", { name: /pattern explorer/i })).toBeVisible();
  await expect(page.getByRole("link", { name: /interviews/i })).toBeVisible();
});
