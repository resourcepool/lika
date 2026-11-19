import { readFile } from "node:fs/promises"
import { expect, test } from "@playwright/test"
import { API_URL, createDomain, createUser, signIn, PROVIDER } from "./support"

test.skip(PROVIDER === "keycloak", "signs in with a password")

async function mcp(key: string, method: string, params: object = {}) {
  const response = await fetch(`${API_URL}/mcp`, {
    method: "POST",
    headers: { Authorization: `Bearer ${key}`, "Content-Type": "application/json", Accept: "application/json, text/event-stream" },
    body: JSON.stringify({ jsonrpc: "2.0", id: 1, method, params }),
  })
  return { status: response.status, body: response.status === 200 ? await response.json() : undefined }
}

test("a user creates a user API key per application, connects an assistant, and revokes one", async ({ page }) => {
  const domain = await createDomain()
  const user = await createUser({ [domain.id]: "editor" })
  await signIn(page, user)
  await page.goto("/profile")
  await expect(page.getByLabel("MCP server URL")).toHaveText(`${API_URL}/mcp`)
  await expect(page.getByLabel("MCP configuration")).toContainText("claude mcp add --transport http lika")
  await expect(page.getByText("You have no user API key.")).toBeVisible()

  async function createKey(application: string) {
    await page.getByRole("button", { name: "New key" }).click()
    const naming = page.getByRole("dialog", { name: "New user API key" })
    await naming.getByLabel("Application").fill(application)
    await naming.getByRole("button", { name: "Create key" }).click()
    const dialog = page.getByRole("dialog", { name: `Your user API key for ${application}` })
    const key = (await dialog.getByLabel("User API key").textContent())!
    expect(key).toMatch(/^lika_u_/)
    return { key, dialog }
  }

  const cursor = await createKey("Cursor")
  await cursor.dialog.getByRole("tab", { name: "Cursor" }).click()
  await expect(cursor.dialog.getByLabel("MCP configuration")).toContainText(`"Authorization": "Bearer ${cursor.key}"`)
  await cursor.dialog.getByRole("button", { name: "Done" }).click()
  const desktop = await createKey("Claude Desktop")
  await desktop.dialog.getByRole("button", { name: "Done" }).click()

  // One name per application
  await page.getByRole("button", { name: "New key" }).click()
  await page.getByLabel("Application").fill("cursor")
  await expect(page.getByText("You already have a key for this application")).toBeVisible()
  await expect(page.getByRole("button", { name: "Create key" })).toBeDisabled()
  await page.getByRole("button", { name: "Cancel" }).click()

  const tools = await mcp(cursor.key, "tools/list")
  const names = tools.body.result.tools.map((t: { name: string }) => t.name)
  expect(names).toContain("create_link")
  expect(names).not.toContain("list_users")
  const created = await mcp(cursor.key, "tools/call", { name: "create_link", arguments: { domainId: domain.id, targetUrl: "https://example.com/assistant", slug: "assistant" } })
  expect(created.body.result.structuredContent.shortenedUrl).toBe(`https://${domain.host}/assistant`)
  // MCP only
  expect((await fetch(`${API_URL}/api/domains`, { headers: { Authorization: `Bearer ${cursor.key}` } })).status).toBe(403)

  // Each key tells when its application last used it
  await page.reload()
  const keys = page.getByRole("list", { name: "User API keys" }).getByRole("listitem")
  await expect(keys).toHaveCount(2)
  await expect(keys.filter({ hasText: "Cursor" })).toContainText("last used")
  await expect(keys.filter({ hasText: "Claude Desktop" })).toContainText("never used")

  // Revoking one leaves the other connected
  await page.getByRole("button", { name: "Revoke the key of Cursor" }).click()
  await page.getByRole("alertdialog").getByRole("button", { name: "Revoke" }).click()
  await expect(keys).toHaveCount(1)
  expect((await mcp(cursor.key, "tools/list")).status).toBe(401)
  expect((await mcp(desktop.key, "tools/list")).status).toBe(200)
})

test("a user downloads the Claude plugin with the skills of their rights", async ({ page }) => {
  const domain = await createDomain()
  const user = await createUser({ [domain.id]: "editor" })
  await signIn(page, user)
  await page.goto("/profile")
  const downloading = page.waitForEvent("download")
  await page.getByRole("button", { name: "Download plugin" }).click()
  const download = await downloading
  expect(download.suggestedFilename()).toBe("lika-claude-plugin.zip")
  const zip = await readFile((await download.path())!)
  // A zip, holding the manifest and the skills
  expect(zip.subarray(0, 2).toString()).toBe("PK")
  expect(zip.includes("lika/.claude-plugin/plugin.json")).toBe(true)
  expect(zip.includes("lika/skills/lika-links/SKILL.md")).toBe(true)
})
