import { expect, test } from "@playwright/test"
import { API_URL, apiCall, createDomain, signIn, PROVIDER, type TestDomain } from "./support"

test.skip(PROVIDER === "keycloak", "signs in with a password")

/** A visitor follows a short link */
async function visit(domain: TestDomain, slug: string, times = 1) {
  for (let i = 0; i < times; i++) {
    const response = await fetch(`${API_URL}/${slug}`, { headers: { "X-Forwarded-Host": domain.host }, redirect: "manual" })
    expect(response.status).toBe(302)
  }
}

test("visits show per domain, then per link", async ({ page }) => {
  const domain = await createDomain()
  const quiet = await createDomain()
  const create = (slug: string) => apiCall<{ id: string }>("/api/links", { method: "POST", body: { targetUrl: `https://example.com/${slug}`, domainId: domain.id, slug } })
  await create("launch")
  await create("pricing")
  await create("unseen")
  await visit(domain, "launch", 3)
  await visit(domain, "pricing")

  await signIn(page)
  await page.getByRole("link", { name: "Engagement" }).click()
  await expect(page).toHaveURL(/\/engagement/)
  await page.getByRole("combobox", { name: "Domain" }).click()
  await page.getByRole("option", { name: domain.host }).click()

  // Live: the last 24 hours, per hour
  await page.getByRole("tab", { name: "24 hours" }).click()
  await expect(page.getByRole("heading", { name: "Engagement" })).toBeVisible()
  await expect(page.getByText("Visits per hour")).toBeVisible()
  await expect(page.getByRole("img", { name: /^4 visits over 24 hours/ })).toBeVisible()
  const top = page.getByRole("list", { name: "Most visited links" }).getByRole("listitem")
  await expect(top).toHaveCount(2)
  await expect(top.first()).toContainText("/launch")
  await expect(top.first()).toContainText("3")
  await expect(page.getByRole("list", { name: "Visits per domain" }).getByRole("listitem")).toHaveText([new RegExp(`${domain.host}\\s*4`)])

  // Same values without the chart
  await page.getByRole("tab", { name: "Table" }).click()
  await expect(page.getByRole("table", { name: "Visits per hour" }).locator("tbody tr")).toHaveCount(24)
  await expect(page.getByRole("table", { name: "Visits per hour" }).locator("tbody tr").first()).toContainText("4")

  // A domain without visits
  await page.getByRole("combobox", { name: "Domain" }).click()
  await page.getByRole("option", { name: quiet.host }).click()
  await expect(page.getByText("No visit yet", { exact: true })).toBeVisible()
  await page.getByRole("combobox", { name: "Domain" }).click()
  await page.getByRole("option", { name: domain.host }).click()

  // One link, over 30 days
  await top.first().getByRole("button").click()
  await expect(page).toHaveURL(/link=/)
  await expect(page.getByText(`Visits of https://${domain.host}/launch`)).toBeVisible()
  await page.getByRole("tab", { name: "30 days" }).click()
  await expect(page.getByRole("table", { name: "Visits per day" }).locator("tbody tr")).toHaveCount(30)
  await expect(page.getByRole("table", { name: "Visits per day" }).locator("tbody tr").first()).toContainText("3")
  await page.getByRole("button", { name: "All links" }).click()
  await expect(top).toHaveCount(2)
})

test("a link opens its engagement from its details", async ({ page }) => {
  const domain = await createDomain()
  await apiCall("/api/links", { method: "POST", body: { targetUrl: "https://example.com/a", domainId: domain.id, slug: "campaign" } })
  await visit(domain, "campaign", 2)
  await signIn(page)
  await page.goto(`/links?domain=${domain.id}`)
  await page.getByRole("row").filter({ hasText: "/campaign" }).click()
  await page.getByRole("dialog").getByRole("link", { name: "Engagement" }).click()
  await expect(page.getByText(`Visits of https://${domain.host}/campaign`)).toBeVisible()
  await expect(page.getByRole("img", { name: /^2 visits/ })).toBeVisible()
})
