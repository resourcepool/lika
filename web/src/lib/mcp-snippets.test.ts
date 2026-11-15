import { describe, expect, it } from "vitest"
import { KEY_PLACEHOLDER, MCP_CLIENTS, mcpSnippet } from "@/lib/mcp-snippets"

const url = "https://go.example.com/mcp"

describe("MCP client configurations", () => {
  it("authenticate every client with the user API key", () => {
    for (const { id } of MCP_CLIENTS) {
      const snippet = mcpSnippet(id, url, "lika_u_secret")
      expect(snippet).toContain(url)
      expect(snippet).toContain("Bearer lika_u_secret")
    }
  })

  it("are valid JSON for the clients configured with a file", () => {
    expect(JSON.parse(mcpSnippet("cursor", url)).mcpServers.lika).toEqual({ url, headers: { Authorization: `Bearer ${KEY_PLACEHOLDER}` } })
    expect(JSON.parse(mcpSnippet("vscode", url)).servers.lika.type).toBe("http")
    const desktop = JSON.parse(mcpSnippet("claude-desktop", url, "k")).mcpServers.lika
    // mcp-remote splits arguments on spaces: the header value goes through an environment variable
    expect(desktop.args).toContain("Authorization:${LIKA_AUTH}")
    expect(desktop.env.LIKA_AUTH).toBe("Bearer k")
  })
})
