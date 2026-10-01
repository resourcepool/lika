import { expect, test } from "@playwright/test"
import { readFile } from "node:fs/promises"
import { apiCall, createAccount, createDomain, signIn, unique } from "./support"

test.beforeEach(async ({ page }) => {
  await signIn(page)
})

test("create a domain, edit it, and delete it with its links", async ({ page }) => {
  const host = `${unique("ui")}.test`
  await page.goto("/admin/domains")
  await page.getByRole("button", { name: "New domain" }).click()
  const dialog = page.getByRole("dialog")
  await dialog.getByLabel("Host").fill(host)
  await dialog.getByLabel("Short URL base").fill(`https://${host}`)
  await dialog.getByRole("button", { name: "Add domain" }).click()
  const row = page.getByRole("row").filter({ hasText: host })
  await expect(row).toBeVisible()

  await row.getByRole("button", { name: `Actions for ${host}` }).click()
  await page.getByRole("menuitem", { name: "Edit URLs" }).click()
  await page.getByLabel("Short URL base").fill("https://s.test")
  await page.getByRole("button", { name: "Save" }).click()
  await expect(row).toContainText("https://s.test")

  const domain = (await apiCall<{ id: string; host: string }[]>("/api/admin/domains")).find((d) => d.host === host)!
  await apiCall("/api/links", { method: "POST", body: { targetUrl: "https://example.com", domainId: domain.id } })
  await row.getByRole("button", { name: `Actions for ${host}` }).click()
  await page.getByRole("menuitem", { name: "Delete" }).click()
  const confirm = page.getByRole("alertdialog")
  await expect(confirm).toContainText("1 link")
  const button = confirm.getByRole("button", { name: "Delete domain and 1 link" })
  await expect(button).toBeDisabled()
  await confirm.getByLabel(/to confirm/).fill(host)
  await button.click()
  await expect(row).toHaveCount(0)
})

test("create a scoped key, rename it, change its domains, regenerate and delete it", async ({ page }) => {
  const d1 = await createDomain()
  const d2 = await createDomain()
  const name = unique("team")
  await page.goto("/admin/keys")
  await page.getByRole("button", { name: "New account" }).click()
  const dialog = page.getByRole("dialog")
  await dialog.getByLabel("Name").fill(name)
  await dialog.getByLabel("Filter domains").fill(d1.host)
  await dialog.getByRole("checkbox").first().check()
  await dialog.getByRole("button", { name: "Create" }).click()
  const row = page.getByRole("row").filter({ hasText: name })
  await expect(row).toContainText(d1.host)

  await row.getByRole("button", { name: `Actions for ${name}` }).click()
  await page.getByRole("menuitem", { name: "Edit domains" }).click()
  const grants = page.getByRole("dialog")
  await grants.getByLabel("Filter domains").fill(".test")
  await grants.locator("label", { hasText: d1.host }).getByRole("checkbox").uncheck()
  await grants.locator("label", { hasText: d2.host }).getByRole("checkbox").check()
  await grants.getByRole("button", { name: "Save" }).click()
  await expect(row).toContainText(d2.host)
  await expect(row).not.toContainText(d1.host)

  const renamed = unique("renamed")
  await row.getByRole("button", { name: `Actions for ${name}` }).click()
  await page.getByRole("menuitem", { name: "Rename" }).click()
  await page.getByRole("dialog").getByLabel("Name").fill(renamed)
  await page.getByRole("dialog").getByRole("button", { name: "Save" }).click()
  const renamedRow = page.getByRole("row").filter({ hasText: renamed })
  await expect(renamedRow).toBeVisible()

  await renamedRow.getByRole("button", { name: "Show key" }).first().click()
  const before = await renamedRow.locator("code").first().textContent()
  await renamedRow.getByRole("button", { name: `Actions for ${renamed}` }).click()
  await page.getByRole("menuitem", { name: "Regenerate read-write key" }).click()
  await page.getByRole("alertdialog").getByRole("button", { name: "Regenerate" }).click()
  await expect(renamedRow.locator("code").first()).not.toHaveText(before!)

  await renamedRow.getByRole("button", { name: `Actions for ${renamed}` }).click()
  await page.getByRole("menuitem", { name: "Delete" }).click()
  await page.getByRole("alertdialog").getByRole("button", { name: "Delete account" }).click()
  await expect(renamedRow).toHaveCount(0)
})

test("export, then preview and run an import", async ({ page }) => {
  const domain = await createDomain()
  await createAccount([domain.id])
  await page.goto("/admin/archive")

  await page.getByRole("button", { name: "Export archive" }).click()
  const download = page.waitForEvent("download")
  await page.getByRole("alertdialog").getByRole("button", { name: "Download" }).click()
  const file = await (await download).path()
  const archive = JSON.parse(await readFile(file, "utf8"))
  expect(archive.version).toBe(2)
  expect(archive.domains.some((d: { host: string }) => d.host === domain.host)).toBe(true)

  await page.getByLabel("Archive file").setInputFiles(file)
  await page.getByRole("button", { name: "Preview import" }).click()
  await expect(page.getByText("Preview — nothing written yet")).toBeVisible()
  await page.getByRole("button", { name: "Import", exact: true }).click()
  await expect(page.getByText("Imported", { exact: true })).toBeVisible()
})
