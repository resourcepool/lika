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

test("a user creates their user API key, connects an assistant, and revokes it", async ({ page }) => {
  const domain = await createDomain()
  const user = await createUser({ [domain.id]: "editor" })
  await signIn(page, user)
  await page.goto("/profile")
  await expect(page.getByLabel("MCP server URL")).toHaveText(`${API_URL}/mcp`)
  await expect(page.getByLabel("MCP configuration")).toContainText("claude mcp add --transport http lika")

  await page.getByRole("button", { name: "Create user API key" }).click()
  const dialog = page.getByRole("dialog", { name: "Your user API key" })
  const key = (await dialog.getByLabel("User API key").textContent())!
  expect(key).toMatch(/^lika_u_/)
  await dialog.getByRole("tab", { name: "Cursor" }).click()
  await expect(dialog.getByLabel("MCP configuration")).toContainText(`"Authorization": "Bearer ${key}"`)
  await dialog.getByRole("button", { name: "Done" }).click()
  await expect(page.getByText(key.slice(0, 11))).toBeVisible()

  const tools = await mcp(key, "tools/list")
  const names = tools.body.result.tools.map((t: { name: string }) => t.name)
  expect(names).toContain("create_link")
  expect(names).not.toContain("list_users")
  const created = await mcp(key, "tools/call", { name: "create_link", arguments: { domainId: domain.id, targetUrl: "https://example.com/assistant", slug: "assistant" } })
  expect(created.body.result.structuredContent.shortenedUrl).toBe(`https://${domain.host}/assistant`)
  // MCP only
  expect((await fetch(`${API_URL}/api/domains`, { headers: { Authorization: `Bearer ${key}` } })).status).toBe(403)

  await page.getByRole("button", { name: "Revoke" }).click()
  await page.getByRole("alertdialog").getByRole("button", { name: "Revoke" }).click()
  await expect(page.getByRole("button", { name: "Create user API key" })).toBeVisible()
  expect((await mcp(key, "tools/list")).status).toBe(401)
})
