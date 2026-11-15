import { expect, test } from "@playwright/test"
import { ADMIN, PASSWORD, apiCall, createDomain, createUser, ensureAdmin, signIn, PROVIDER } from "./support"

test.skip(PROVIDER === "keycloak", "signs in with a password")

test("visitors without a session are sent to sign in, then back", async ({ page }) => {
  await ensureAdmin()
  await page.goto("/admin/domains")
  await expect(page).toHaveURL(/\/signin\?next=%2Fadmin%2Fdomains/)
  await page.getByLabel("Email").fill(ADMIN.email)
  await page.getByLabel("Password").fill(ADMIN.password)
  await page.getByRole("button", { name: "Sign in" }).click()
  await expect(page).toHaveURL(/\/admin\/domains/)
})

test("a wrong password is rejected", async ({ page }) => {
  await ensureAdmin()
  await page.goto("/signin")
  await page.getByLabel("Email").fill(ADMIN.email)
  await page.getByLabel("Password").fill("not the password")
  await page.getByRole("button", { name: "Sign in" }).click()
  await expect(page.getByText("Wrong email or password")).toBeVisible()
})

test("the setup is closed once an admin exists", async ({ page }) => {
  await ensureAdmin()
  await page.goto("/setup")
  await expect(page).toHaveURL(/\/signin/)
})

test("the session cookie is httpOnly, long-lived, and holds no readable token", async ({ page, context }) => {
  await signIn(page)
  const cookie = (await context.cookies()).find((c) => c.name === "lika_session")!
  expect(cookie.httpOnly).toBe(true)
  expect(cookie.expires * 1000 - Date.now()).toBeGreaterThan(390 * 24 * 3600 * 1000)
  // Encrypted (JWE), so the access token inside cannot be read: no JWT payload such as {"iss":"lika"
  expect(cookie.value.split(".")).toHaveLength(5)
  expect(cookie.value).not.toContain("eyJpc3Mi")
  expect(await page.evaluate(() => document.cookie)).not.toContain("lika_session")
})

test("an editor only sees their domains, and no manage or admin section", async ({ page }) => {
  const domain = await createDomain()
  const user = await createUser({ [domain.id]: "editor" })
  await signIn(page, user)
  await expect(page.getByRole("link", { name: "Users" })).toHaveCount(0)
  await expect(page.getByRole("link", { name: "Domains" })).toHaveCount(0)
  await page.getByRole("combobox", { name: "Domain" }).click()
  await expect(page.getByRole("option")).toHaveText(["All domains", domain.host])
  await page.keyboard.press("Escape")
  await page.goto("/users")
  await expect(page).toHaveURL(/\/links/)
  await page.goto("/admin/domains")
  await expect(page).toHaveURL(/\/links/)
})

test("new rights show without signing in again", async ({ page }) => {
  const domain = await createDomain()
  const user = await createUser({ [domain.id]: "viewer" })
  await signIn(page, user)
  await expect(page.getByRole("link", { name: "Users" })).toHaveCount(0)
  await apiCall(`/api/users/${user.id}/access`, { method: "PUT", body: { domains: { [domain.id]: "manager" } } })
  await page.reload()
  await expect(page.getByRole("link", { name: "Users" })).toBeVisible()
})

test("disabling a user signs their browser out", async ({ page }) => {
  const user = await createUser({ [(await createDomain()).id]: "editor" })
  await signIn(page, user)
  await apiCall(`/api/users/${user.id}/disable`, { method: "POST" })
  await page.reload()
  await expect(page).toHaveURL(/\/signin\?expired=1/)
  await expect(page.getByText(/session has ended/)).toBeVisible()
})

test("an invited user joins through the link", async ({ page, browser }) => {
  const domain = await createDomain()
  await signIn(page)
  await page.goto("/users")
  await page.getByRole("button", { name: "Invite user" }).click()
  const dialog = page.getByRole("dialog")
  const email = `joiner-${Date.now()}@e2e.test`
  await dialog.getByLabel("Email").fill(email)
  await dialog.getByLabel("Name").fill("Joiner")
  // The filter only shows with many domains
  const filter = dialog.getByLabel("Filter domains")
  if (await filter.isVisible()) await filter.fill(domain.host)
  await dialog.getByRole("combobox", { name: `Role on ${domain.host}` }).click()
  await page.getByRole("option", { name: "Editor" }).click()
  await dialog.getByRole("button", { name: "Send invitation" }).click()

  const linkDialog = page.getByRole("dialog", { name: "Invitation link" })
  await expect(linkDialog).toContainText("No email was sent")
  const link = (await linkDialog.getByLabel("Invitation link").textContent())!
  await linkDialog.getByRole("button", { name: "Done" }).click()
  await expect(page.getByRole("row").filter({ hasText: email })).toContainText("invited")

  const invitee = await (await browser.newContext()).newPage()
  await invitee.goto(new URL(link).pathname)
  await expect(invitee.getByLabel("Email")).toHaveValue(email)
  await invitee.getByLabel("Password", { exact: true }).fill(PASSWORD)
  await invitee.getByLabel("Confirm password").fill(PASSWORD)
  await invitee.getByRole("button", { name: "Accept and sign in" }).click()
  await expect(invitee).toHaveURL(/\/links/)
  await invitee.getByRole("combobox", { name: "Domain" }).click()
  await expect(invitee.getByRole("option")).toHaveText(["All domains", domain.host])

  await invitee.goto(new URL(link).pathname)
  await expect(invitee.getByText("Invitation expired")).toBeVisible()
})

test("without emails, a forgotten password goes through an admin", async ({ page }) => {
  test.skip(PROVIDER === "firebase", "Firebase emails reset links itself")
  await page.goto("/forgot-password")
  await expect(page.getByText("Ask an admin for a password reset link")).toBeVisible()
})

test("signing out ends the session", async ({ page }) => {
  await signIn(page)
  await page.getByRole("button", { name: "Sign out" }).click()
  await expect(page).toHaveURL(/\/signin/)
  await page.goto("/links")
  await expect(page).toHaveURL(/\/signin/)
})
