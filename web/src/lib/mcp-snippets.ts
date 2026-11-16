export type McpClient = "claude-code" | "cursor" | "vscode" | "claude-desktop" | "curl"

export const MCP_CLIENTS: { id: McpClient; label: string; hint: string }[] = [
  { id: "claude-code", label: "Claude Code", hint: "Run in a terminal." },
  { id: "cursor", label: "Cursor", hint: "In ~/.cursor/mcp.json, or .cursor/mcp.json in a project." },
  { id: "vscode", label: "VS Code", hint: "In .vscode/mcp.json, or with MCP: Add Server." },
  { id: "claude-desktop", label: "Claude Desktop", hint: "In claude_desktop_config.json (Settings › Developer). Needs Node.js." },
  { id: "curl", label: "curl", hint: "Check the connection: lists your tools." },
]

/** Placeholder shown until the user creates a key */
export const KEY_PLACEHOLDER = "<your user API key>"

/**
 * Configuration of an MCP client for the Lika MCP server, authenticated with a user API key.
 */
export function mcpSnippet(client: McpClient, url: string, key: string = KEY_PLACEHOLDER): string {
  const auth = `Bearer ${key}`
  switch (client) {
    case "claude-code":
      return `claude mcp add --transport http lika ${url} \\\n  --header "Authorization: ${auth}"`
    case "cursor":
      return JSON.stringify({ mcpServers: { lika: { url, headers: { Authorization: auth } } } }, null, 2)
    case "vscode":
      return JSON.stringify({ servers: { lika: { type: "http", url, headers: { Authorization: auth } } } }, null, 2)
    case "claude-desktop":
      return JSON.stringify(
        // X-Api-Key has no space in its value, which mcp-remote would split on
        { mcpServers: { lika: { command: "npx", args: ["-y", "mcp-remote", url, "--header", `X-Api-Key:${key}`] } } },
        null,
        2,
      )
    case "curl":
      return `curl -s ${url} \\\n  -H "Authorization: ${auth}" \\\n  -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" \\\n  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'`
  }
}
