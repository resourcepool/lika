import { expect, type Page } from "@playwright/test"

/** Root admin key of the API's default configuration */
export const ADMIN_TOKEN = process.env.E2E_ADMIN_TOKEN ?? "lika-client-secret"
export const API_URL = process.env.E2E_API_URL ?? "http://localhost:8080"

export const unique = (prefix: string) => `${prefix}-${Math.random().toString(36).slice(2, 8)}`

export async function signIn(page: Page, token: string = ADMIN_TOKEN) {
  await page.goto("/signin")
  await page.getByLabel("API key").fill(token)
  await page.getByRole("button", { name: "Sign in" }).click()
  await expect(page).toHaveURL(/\/links/)
}

/** Direct API calls, to set up data without going through the UI */
export async function apiCall<T>(path: string, init: { method?: string; body?: unknown; token?: string } = {}): Promise<T> {
  const response = await fetch(`${API_URL}${path}`, {
    method: init.method ?? "GET",
    headers: { Accept: "application/json", "Content-Type": "application/json", Authorization: `Bearer ${init.token ?? ADMIN_TOKEN}` },
    body: init.body === undefined ? undefined : JSON.stringify(init.body),
  })
  if (!response.ok) {
    throw new Error(`${init.method ?? "GET"} ${path}: ${response.status} ${await response.text()}`)
  }
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export type TestDomain = { id: string; host: string }
export type TestAccount = { id: string; name: string; defaultAccessToken: string; defaultPublicReadAccessToken: string }

export async function createDomain(): Promise<TestDomain> {
  const host = `${unique("e2e")}.test`
  return apiCall<TestDomain>("/api/admin/domains", { method: "POST", body: { host, apiBaseUrl: `https://${host}`, landingBaseUrl: "" } })
}

export async function createAccount(domainIds: string[] = []): Promise<TestAccount> {
  return apiCall<TestAccount>("/api/admin/accounts", { method: "POST", body: { name: unique("key"), global: false, domainIds } })
}
