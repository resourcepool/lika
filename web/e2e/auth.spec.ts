import { expect, test } from "@playwright/test"
import { apiCall, createAccount, createDomain, signIn } from "./support"

test("visitors without a session are sent to sign in", async ({ page }) => {
  await page.goto("/links")
  await expect(page).toHaveURL(/\/signin/)
})

test("an invalid key is rejected", async ({ page }) => {
  await page.goto("/signin")
  await page.getByLabel("API key").fill("not-a-key")
  await page.getByRole("button", { name: "Sign in" }).click()
  await expect(page.getByText("This key is not valid.")).toBeVisible()
})

test("a read-only key is rejected with an explanation", async ({ page }) => {
  const account = await createAccount()
  await page.goto("/signin")
  await page.getByLabel("API key").fill(account.defaultPublicReadAccessToken)
  await page.getByRole("button", { name: "Sign in" }).click()
  await expect(page.getByText(/read-only key/)).toBeVisible()
})

test("the session cookie is httpOnly, long-lived, and holds no readable key", async ({ page, context }) => {
  await signIn(page)
  const cookie = (await context.cookies()).find((c) => c.name === "lika_session")!
  expect(cookie.httpOnly).toBe(true)
  expect(cookie.expires * 1000 - Date.now()).toBeGreaterThan(390 * 24 * 3600 * 1000)
  expect(cookie.value).not.toContain("lika-client-secret")
  expect(await page.evaluate(() => document.cookie)).not.toContain("lika_session")
})

test("a scoped key only sees its domains and no admin section", async ({ page }) => {
  const domain = await createDomain()
  const account = await createAccount([domain.id])
  await signIn(page, account.defaultAccessToken)
  await expect(page.getByRole("link", { name: "API Keys" })).toHaveCount(0)
  await page.getByRole("combobox", { name: "Domain" }).click()
  await expect(page.getByRole("option")).toHaveText(["All domains", domain.host])
  await page.goto("/admin/keys")
  await expect(page).toHaveURL(/\/links/)
})

test("regenerating the key in use signs the browser out", async ({ page }) => {
  const account = await createAccount()
  await signIn(page, account.defaultAccessToken)
  await apiCall(`/api/admin/accounts/${account.id}/regenerate`, { method: "POST", body: { token: "read_write" } })
  await page.reload()
  await expect(page).toHaveURL(/\/signin\?expired=1/)
  await expect(page.getByText(/revoked or regenerated/)).toBeVisible()
})

test("signing out ends the session", async ({ page }) => {
  await signIn(page)
  await page.getByRole("button", { name: "Sign out" }).click()
  await expect(page).toHaveURL(/\/signin/)
  await page.goto("/links")
  await expect(page).toHaveURL(/\/signin/)
})
