import { expect, type Page } from "@playwright/test"

/** Admin API key of the API's default configuration, for setting up data */
const ADMIN_TOKEN = process.env.E2E_ADMIN_TOKEN ?? "lika-client-secret"
export const API_URL = process.env.E2E_API_URL ?? "http://localhost:8080"
/** Fixed setup token of the API under test (LIKA_SETUP_TOKEN) */
const SETUP_TOKEN = process.env.E2E_SETUP_TOKEN ?? "e2e-setup-token"
export const PASSWORD = "e2e password 123"
/** Identity provider of the API under test: local and firebase sign in with a password, keycloak with single sign-on */
export const PROVIDER = process.env.E2E_PROVIDER ?? "local"

export type Credentials = { email: string; password: string }

export const ADMIN: Credentials = { email: process.env.E2E_ADMIN_EMAIL ?? "admin@e2e.test", password: process.env.E2E_ADMIN_PASSWORD ?? PASSWORD }

export const unique = (prefix: string) => `${prefix}-${Math.random().toString(36).slice(2, 8)}`

/** Direct API calls, to set up data without going through the UI */
export async function apiCall<T>(path: string, init: { method?: string; body?: unknown; token?: string | null } = {}): Promise<T> {
  const token = init.token === undefined ? ADMIN_TOKEN : init.token
  const response = await fetch(`${API_URL}${path}`, {
    method: init.method ?? "GET",
    headers: { Accept: "application/json", "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: init.body === undefined ? undefined : JSON.stringify(init.body),
  })
  if (!response.ok) {
    throw new Error(`${init.method ?? "GET"} ${path}: ${response.status} ${await response.text()}`)
  }
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

/** Creates the first admin through the setup, unless done by a previous run */
export async function ensureAdmin() {
  const config = await apiCall<{ setupRequired: boolean }>("/api/auth/config", { token: null })
  if (config.setupRequired) {
    await apiCall("/api/auth/setup", { method: "POST", token: null, body: { setupToken: SETUP_TOKEN, name: "E2E Admin", ...ADMIN } })
  }
}

export async function signIn(page: Page, user: Credentials = ADMIN) {
  if (user === ADMIN) await ensureAdmin()
  await page.goto("/signin")
  await page.getByLabel("Email").fill(user.email)
  await page.getByLabel("Password").fill(user.password)
  await page.getByRole("button", { name: "Sign in" }).click()
  await expect(page).toHaveURL(/\/links/)
}

export type TestDomain = { id: string; host: string }
type TestUser = Credentials & { id: string; name: string }

export async function createDomain(): Promise<TestDomain> {
  const host = `${unique("e2e")}.test`
  return apiCall<TestDomain>("/api/admin/domains", { method: "POST", body: { host, apiBaseUrl: `https://${host}`, landingBaseUrl: "" } })
}

/** Invites a user with the admin key and accepts the invitation */
export async function createUser(domains: Record<string, "viewer" | "editor" | "manager"> = {}, admin = false): Promise<TestUser> {
  const email = `${unique("user")}@e2e.test`
  const invitation = await apiCall<{ link: string; user: { id: string; name: string } }>("/api/users/invitations", {
    method: "POST",
    body: { email, name: `User ${email.split("@")[0]}`, admin, domains },
  })
  const token = invitation.link.split("/").pop()!
  await apiCall(`/api/auth/invitations/${token}/accept`, { method: "POST", token: null, body: { password: PASSWORD } })
  return { id: invitation.user.id, name: invitation.user.name, email, password: PASSWORD }
}

export async function createKey(domains: Record<string, "viewer" | "editor">): Promise<{ secret: string; key: { id: string; name: string } }> {
  return apiCall("/api/keys", { method: "POST", body: { name: unique("app"), domains } })
}
