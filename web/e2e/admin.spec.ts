import { expect, test, type Page } from "@playwright/test"
import { readFile } from "node:fs/promises"
import { apiCall, createDomain, createKey, createUser, signIn, unique, PROVIDER } from "./support"

test.skip(PROVIDER === "keycloak", "signs in with a password")

test.beforeEach(async ({ page }) => {
  await signIn(page)
})

/** Sets the role on a domain in the open roles editor */
async function pickRole(page: Page, host: string, role: string) {
  const dialog = page.getByRole("dialog")
  const filter = dialog.getByLabel("Filter domains")
  if (await filter.isVisible()) await filter.fill(host)
  await dialog.getByRole("combobox", { name: `Role on ${host}` }).click()
  await page.getByRole("option", { name: role, exact: true }).click()
}

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

test("create a key, see its secret once, edit, regenerate and delete it", async ({ page }) => {
  const d1 = await createDomain()
  const d2 = await createDomain()
  const name = unique("app")
  await page.goto("/keys")
  await page.getByRole("button", { name: "New key" }).click()
  const dialog = page.getByRole("dialog")
  await dialog.getByLabel("Application").fill(name)
  await pickRole(page, d1.host, "Editor")
  await dialog.getByRole("button", { name: "Create key" }).click()

  const secretDialog = page.getByRole("dialog", { name: `Secret of ${name}` })
  const secret = (await secretDialog.getByLabel("API key").textContent())!
  expect(secret).toMatch(/^lika_/)
  await secretDialog.getByRole("button", { name: "Done" }).click()
  const row = page.getByRole("row").filter({ hasText: name })
  await expect(row).toContainText(d1.host)
  await expect(row).not.toContainText(secret)
  await apiCall("/api/domains", { token: secret })

  await row.getByRole("button", { name: `Actions for ${name}` }).click()
  await page.getByRole("menuitem", { name: "Edit" }).click()
  await pickRole(page, d1.host, "No access")
  await pickRole(page, d2.host, "Viewer")
  await page.getByRole("dialog").getByRole("button", { name: "Save" }).click()
  await expect(row).toContainText(d2.host)
  await expect(row).not.toContainText(d1.host)

  await row.getByRole("button", { name: `Actions for ${name}` }).click()
  await page.getByRole("menuitem", { name: "Regenerate secret" }).click()
  await page.getByRole("alertdialog").getByRole("button", { name: "Regenerate" }).click()
  const regenerated = (await page.getByRole("dialog", { name: `Secret of ${name}` }).getByLabel("API key").textContent())!
  expect(regenerated).not.toBe(secret)
  await page.getByRole("button", { name: "Done" }).click()
  await expect(apiCall("/api/domains", { token: secret })).rejects.toThrow(/401/)

  await row.getByRole("button", { name: `Actions for ${name}` }).click()
  await page.getByRole("menuitem", { name: "Delete" }).click()
  await page.getByRole("alertdialog").getByRole("button", { name: "Delete key" }).click()
  await expect(row).toHaveCount(0)
})

test("change the access of a user, disable, enable and delete them", async ({ page }) => {
  const domain = await createDomain()
  const user = await createUser({ [domain.id]: "viewer" })
  await page.goto("/users")
  const row = page.getByRole("row").filter({ hasText: user.email })
  await expect(row).toContainText(`${domain.host}· viewer`)

  await row.getByRole("button", { name: `Actions for ${user.name}` }).click()
  await page.getByRole("menuitem", { name: "Edit access" }).click()
  await pickRole(page, domain.host, "Manager")
  await page.getByRole("dialog").getByRole("button", { name: "Save" }).click()
  await expect(row).toContainText(`${domain.host}· manager`)

  await row.getByRole("button", { name: `Actions for ${user.name}` }).click()
  await page.getByRole("menuitem", { name: "Send password reset link" }).click()
  await expect(page.getByRole("dialog", { name: "Password reset link" }).getByLabel("Reset link")).toContainText("/reset-password/")
  await page.getByRole("button", { name: "Done" }).click()

  await row.getByRole("button", { name: `Actions for ${user.name}` }).click()
  await page.getByRole("menuitem", { name: "Disable" }).click()
  await page.getByRole("alertdialog").getByRole("button", { name: "Disable" }).click()
  await expect(row).toContainText("disabled")
  await row.getByRole("button", { name: `Actions for ${user.name}` }).click()
  await page.getByRole("menuitem", { name: "Enable" }).click()
  await page.getByRole("alertdialog").getByRole("button", { name: "Enable" }).click()
  await expect(row).toContainText("active")

  await row.getByRole("button", { name: `Actions for ${user.name}` }).click()
  await page.getByRole("menuitem", { name: "Delete" }).click()
  await page.getByRole("alertdialog").getByRole("button", { name: "Delete" }).click()
  await expect(row).toHaveCount(0)
})

test("export, then preview and run an import", async ({ page }) => {
  const domain = await createDomain()
  await createKey({ [domain.id]: "editor" })
  await page.goto("/admin/archive")

  await page.getByRole("button", { name: "Export archive" }).click()
  const download = page.waitForEvent("download")
  await page.getByRole("alertdialog").getByRole("button", { name: "Download" }).click()
  const file = await (await download).path()
  const archive = JSON.parse(await readFile(file, "utf8"))
  expect(archive.version).toBe(3)
  expect(archive.apiKeys.length).toBeGreaterThan(0)
  expect(archive.domains.some((d: { host: string }) => d.host === domain.host)).toBe(true)

  await page.getByLabel("Archive file").setInputFiles(file)
  await page.getByRole("button", { name: "Preview import" }).click()
  await expect(page.getByText("Preview — nothing written yet")).toBeVisible()
  await page.getByRole("button", { name: "Import", exact: true }).click()
  await expect(page.getByText("Imported", { exact: true })).toBeVisible()
})
