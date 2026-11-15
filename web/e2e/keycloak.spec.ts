import { expect, test, type Page } from "@playwright/test"
import { API_URL, PROVIDER, apiCall, createDomain, unique } from "./support"

/**
 * Single sign-on with Keycloak: API with LIKA_AUTH_PROVIDER=keycloak and the realm of dev/keycloak.
 * E2E_PROVIDER=keycloak E2E_KEYCLOAK_URL=http://localhost:8180
 */
test.skip(PROVIDER !== "keycloak", "needs an API signing in with Keycloak")

const KEYCLOAK = process.env.E2E_KEYCLOAK_URL ?? "http://localhost:8180"
const ADMIN = { email: "kc-admin@e2e.test", password: "e2e password 123" }

async function keycloakSignIn(page: Page, email: string, password: string) {
  await page.getByRole("link", { name: "Sign in with Keycloak" }).click()
  await expect(page).toHaveURL(new RegExp(`^${KEYCLOAK}/realms/lika/`))
  await page.locator("#username").fill(email)
  await page.locator("#password").fill(password)
  await page.locator("#kc-login").click()
}

async function keycloakAdminToken(): Promise<string> {
  const response = await fetch(`${KEYCLOAK}/realms/master/protocol/openid-connect/token`, {
    method: "POST",
    body: new URLSearchParams({ grant_type: "password", client_id: "admin-cli", username: "admin", password: "admin" }),
  })
  return ((await response.json()) as { access_token: string }).access_token
}

test.beforeAll(async () => {
  const config = await apiCall<{ setupRequired: boolean }>("/api/auth/config", { token: null })
  if (config.setupRequired) {
    // No session from the API: the admin then signs in with Keycloak
    const response = await fetch(`${API_URL}/api/auth/setup`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ setupToken: process.env.E2E_SETUP_TOKEN ?? "e2e-setup-token", name: "KC Admin", ...ADMIN }),
    })
    expect(response.status).toBe(204)
  }
})

test("the admin signs in with Keycloak, then out", async ({ page }) => {
  await page.goto("/links")
  await expect(page).toHaveURL(/\/signin/)
  await expect(page.getByLabel("Password")).toHaveCount(0)
  await keycloakSignIn(page, ADMIN.email, ADMIN.password)
  await expect(page).toHaveURL(/\/links/)
  await expect(page.getByRole("link", { name: "Domains" })).toBeVisible()

  await page.goto("/profile")
  await expect(page.getByText("Your password is managed by Keycloak")).toBeVisible()

  await page.getByRole("button", { name: "Sign out", exact: true }).click()
  await expect(page).toHaveURL(/\/signin/)
})

test("an existing Keycloak user joins through their invitation", async ({ page }) => {
  const domain = await createDomain()
  let invitation: { link: string }
  try {
    invitation = await apiCall("/api/users/invitations", { method: "POST", body: { email: "ada@example.com", name: "Ada", domains: { [domain.id]: "editor" } } })
  } catch {
    test.skip(true, "Ada joined in a previous run")
    return
  }
  await page.goto(new URL(invitation.link).pathname)
  await expect(page.getByText("You already have an account")).toBeVisible()
  await page.getByRole("button", { name: "Accept the invitation" }).click()
  // Straight to Keycloak: there is no other way to sign in
  await page.locator("#username").fill("ada@example.com")
  await page.locator("#password").fill("ada password 123")
  await page.locator("#kc-login").click()
  await expect(page).toHaveURL(/\/links/)
  await page.getByRole("combobox", { name: "Domain" }).click()
  await expect(page.getByRole("option", { name: domain.host })).toBeVisible()
})

test("a Keycloak user without invitation is turned away", async ({ page }) => {
  const email = `${unique("stranger")}@e2e.test`
  const response = await fetch(`${KEYCLOAK}/admin/realms/lika/users`, {
    method: "POST",
    headers: { Authorization: `Bearer ${await keycloakAdminToken()}`, "Content-Type": "application/json" },
    body: JSON.stringify({ username: email, email, emailVerified: true, enabled: true, firstName: "S", lastName: "T", credentials: [{ type: "password", value: "stranger password 1", temporary: false }] }),
  })
  expect(response.status).toBe(201)
  await page.goto("/signin")
  await keycloakSignIn(page, email, "stranger password 1")
  await expect(page).toHaveURL(/\/signin\?error=not-invited/)
  await expect(page.getByText("This account has no access to Lika")).toBeVisible()
})
