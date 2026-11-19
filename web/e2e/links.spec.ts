import { expect, test } from "@playwright/test"
import { API_URL, apiCall, createDomain, signIn, unique, PROVIDER } from "./support"

test.skip(PROVIDER === "keycloak", "signs in with a password")

test("create, inspect, edit and delete a link", async ({ page }) => {
  const domain = await createDomain()
  const slug = unique("promo")
  await signIn(page)
  await page.goto(`/links?domain=${domain.id}`)

  await page.getByRole("button", { name: "New link" }).click()
  const dialog = page.getByRole("dialog")
  await dialog.getByLabel("Destination").fill("https://example.com/spring")
  await dialog.getByLabel("Slug").fill(slug)
  await expect(dialog.getByLabel("Available")).toBeVisible()
  await dialog.getByPlaceholder("No password").fill("s3cret")
  await dialog.getByRole("button", { name: "Add metadata" }).click()
  await dialog.getByLabel("Key").fill("campaign")
  await dialog.getByLabel("Value").fill("spring")
  await dialog.getByRole("button", { name: "Create link" }).click()
  await expect(dialog).toBeHidden()

  const row = page.getByRole("row").filter({ hasText: `/${slug}` })
  await expect(row).toContainText("protected")
  await row.click()
  const sheet = page.getByRole("dialog")
  await expect(sheet).toContainText(`https://${domain.host}/${slug}`)
  await expect(sheet).toContainText("campaign")
  await expect(sheet).toContainText("You")

  await sheet.getByRole("button", { name: "Edit" }).click()
  const edit = page.getByRole("dialog")
  await expect(edit.getByLabel("Slug")).toBeDisabled()
  await expect(edit.getByText("Password set")).toBeVisible()
  await edit.getByLabel("Destination").fill("https://example.com/summer")
  await edit.getByRole("button", { name: "Save" }).click()
  await expect(edit).toBeHidden()
  await expect(row).toContainText("https://example.com/summer")
  // Omitting the password on edit kept it
  await expect(row).toContainText("protected")

  await row.getByRole("button", { name: `Actions for /${slug}` }).click()
  await page.getByRole("menuitem", { name: "Delete" }).click()
  await page.getByRole("alertdialog").getByRole("button", { name: "Delete" }).click()
  await expect(row).toHaveCount(0)
})

test("a taken slug is flagged with suggestions", async ({ page }) => {
  const domain = await createDomain()
  const slug = unique("taken")
  await apiCall("/api/links", { method: "POST", body: { targetUrl: "https://example.com", domainId: domain.id, slug } })
  await signIn(page)
  await page.goto(`/links?domain=${domain.id}`)
  await page.getByRole("button", { name: "New link" }).click()
  const dialog = page.getByRole("dialog")
  await dialog.getByLabel("Slug").fill(slug)
  await expect(dialog.getByLabel("Taken")).toBeVisible()
  await expect(dialog.getByText("Taken. Try")).toBeVisible()
})

test("search, sorting and scheduled status", async ({ page }) => {
  const domain = await createDomain()
  const tomorrow = new Date(Date.now() + 24 * 3600 * 1000).toISOString()
  await apiCall("/api/links", { method: "POST", body: { targetUrl: "https://example.com/a", domainId: domain.id, slug: "alpha" } })
  await apiCall("/api/links", {
    method: "POST",
    body: { targetUrl: "https://example.com/b", domainId: domain.id, slug: "bravo", options: { activation: { activationInstant: tomorrow } } },
  })
  await signIn(page)
  await page.goto(`/links?domain=${domain.id}`)
  await expect(page.getByRole("row").filter({ hasText: "/bravo" })).toContainText("scheduled")

  await page.getByRole("button", { name: "Short link" }).click()
  const rows = page.getByRole("row").filter({ hasText: domain.host })
  await expect(rows.first()).toContainText("/bravo")
  await page.getByRole("button", { name: "Short link" }).click()
  await expect(rows.first()).toContainText("/alpha")

  await page.getByLabel("Search links").fill("bravo")
  await expect(rows).toHaveCount(1)
})

test("filter on metadata, then change and delete the selected links at once", async ({ page }) => {
  const domain = await createDomain()
  const create = (slug: string, metadata: Record<string, string>) =>
    apiCall("/api/links", { method: "POST", body: { targetUrl: `https://example.com/${slug}`, domainId: domain.id, slug, metadata } })
  await create("fr-summer", { campaign: "summer", lang: "fr" })
  await create("en-summer", { campaign: "summer", lang: "en" })
  await create("winter", { campaign: "winter" })
  await signIn(page)
  await page.goto(`/links?domain=${domain.id}`)
  const rows = page.getByRole("row").filter({ hasText: domain.host })
  await expect(rows).toHaveCount(3)

  await page.getByRole("button", { name: "Filter by metadata" }).click()
  await page.getByLabel("Metadata key").fill("campaign")
  await page.getByLabel("Metadata value").fill("Summer")
  await page.getByRole("button", { name: "Add", exact: true }).click()
  await expect(rows).toHaveCount(2)
  await expect(page.getByRole("button", { name: "Remove filter campaign" })).toBeVisible()

  await page.getByLabel("Select the links of this page").click()
  const toolbar = page.getByRole("toolbar", { name: "Selected links" })
  await expect(toolbar).toContainText("2 links selected")

  await toolbar.getByRole("button", { name: "Set metadata" }).click()
  const dialog = page.getByRole("dialog")
  await dialog.getByLabel("Key").fill("season")
  await dialog.getByLabel("Value").fill("2026")
  await dialog.getByRole("button", { name: "Apply to 2 links" }).click()
  await expect(dialog).toBeHidden()

  // Both carry the new entry, and kept theirs
  await page.getByRole("button", { name: "Filter", exact: true }).click()
  await page.getByLabel("Metadata key").fill("season")
  await page.getByRole("button", { name: "Add", exact: true }).click()
  await expect(rows).toHaveCount(2)

  await page.getByLabel("Select /fr-summer").click()
  await expect(toolbar).toContainText("1 link selected")
  await toolbar.getByRole("button", { name: "Delete" }).click()
  await page.getByRole("alertdialog").getByRole("button", { name: "Delete" }).click()
  await expect(rows).toHaveCount(1)
  await expect(rows.first()).toContainText("/en-summer")
  await expect(toolbar).toBeHidden()

  await page.getByRole("button", { name: "Clear filters" }).click()
  await expect(rows).toHaveCount(2)
})

test("a link expires after a delay chosen in a list, or on a custom date", async ({ page }) => {
  const domain = await createDomain()
  await signIn(page)
  await page.goto(`/links?domain=${domain.id}`)
  const day = (offset: number) => {
    const date = new Date()
    date.setDate(date.getDate() + offset)
    const pad = (n: number) => String(n).padStart(2, "0")
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
  }
  const expiryOf = async (slug: string) =>
    (await apiCall<{ options: { expiry: { expiryDate: string } | null } }>(`/api/search/slug/${slug}?domainId=${domain.id}`)).options.expiry?.expiryDate

  await page.getByRole("button", { name: "New link" }).click()
  const dialog = page.getByRole("dialog")
  await dialog.getByLabel("Destination").fill("https://example.com/soon")
  await dialog.getByLabel("Slug").fill("soon")
  await dialog.getByRole("switch", { name: "Expiry" }).click()
  // Delays first, the date only for "Custom date"
  await expect(dialog.getByLabel("Last day")).toHaveCount(0)
  await dialog.getByRole("combobox", { name: "Expires" }).click()
  await expect(page.getByRole("option")).toHaveText(["In 7 days", "In 30 days", "In 3 months", "Custom date"])
  await page.getByRole("option", { name: "In 30 days" }).click()
  await dialog.getByRole("button", { name: "Create link" }).click()
  await expect(dialog).toBeHidden()
  expect(await expiryOf("soon")).toBe(day(30))

  // An existing expiry is edited as its date
  await page.getByRole("row").filter({ hasText: "/soon" }).getByRole("button", { name: "Actions for /soon" }).click()
  await page.getByRole("menuitem", { name: "Edit" }).click()
  await expect(dialog.getByRole("combobox", { name: "Expires" })).toHaveText("Custom date")
  await expect(dialog.getByLabel("Last day")).toHaveValue(day(30))
  await dialog.getByLabel("Last day").fill(day(45))
  await dialog.getByRole("button", { name: "Save" }).click()
  await expect(dialog).toBeHidden()
  expect(await expiryOf("soon")).toBe(day(45))

  // Same choices for several links at once
  await page.getByLabel("Select /soon").click()
  await page.getByRole("toolbar", { name: "Selected links" }).getByRole("button", { name: "Expiry" }).click()
  await page.getByRole("button", { name: "Set expiry" }).click()
  await expect(page.getByRole("dialog")).toBeHidden()
  expect(await expiryOf("soon")).toBe(day(7))
})

test("links unused for a year are found and deleted from the cleanup", async ({ page }) => {
  const domain = await createDomain()
  // Old links come from an archive: the API dates the links it creates
  const old = (slug: string) => ({ targetUrl: `https://example.com/${slug}`, slug, createdDateTime: "2024-01-15T10:00:00Z", lastUpdateDateTime: "2024-01-15T10:00:00Z" })
  await apiCall("/api/admin/archive/import", {
    method: "POST",
    body: { version: 3, domains: [{ host: domain.host, apiBaseUrl: `https://${domain.host}`, links: [old("forgotten"), old("abandoned"), old("popular")] }] },
  })
  await apiCall("/api/links", { method: "POST", body: { targetUrl: "https://example.com/new", domainId: domain.id, slug: "recent" } })
  // Someone still follows /popular
  await fetch(`${API_URL}/popular`, { headers: { "X-Forwarded-Host": domain.host }, redirect: "manual" })

  await signIn(page)
  await page.goto(`/links?domain=${domain.id}`)
  await page.getByRole("button", { name: "Clean up" }).click()
  const dialog = page.getByRole("dialog", { name: "Clean up unused links" })
  const rows = dialog.getByRole("table", { name: "Unused links" }).locator("tbody tr")
  await expect(rows).toHaveCount(2)
  await expect(dialog).toContainText("/forgotten")
  await expect(dialog).toContainText("/abandoned")
  await expect(dialog).toContainText("Lika counts visits since")

  await dialog.getByLabel("Select /forgotten").click()
  await dialog.getByRole("button", { name: "Delete 1 link" }).click()
  await page.getByRole("alertdialog").getByRole("button", { name: "Delete" }).click()
  await expect(rows).toHaveCount(1)
  await dialog.getByRole("button", { name: "Done" }).click()

  const links = page.getByRole("row").filter({ hasText: domain.host })
  await expect(links).toHaveCount(3)
  await expect(links.filter({ hasText: "/forgotten" })).toHaveCount(0)
})
