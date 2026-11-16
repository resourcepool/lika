"use client"

import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { format, formatDistanceToNow } from "date-fns"
import { Bot, Download, Loader2, RefreshCw, Trash2 } from "lucide-react"
import { toast } from "sonner"
import { ApiError, api, withQuery } from "@/lib/client"
import { MCP_CLIENTS, mcpSnippet, type McpClient } from "@/lib/mcp-snippets"
import type { ApiKeySecret, Me } from "@/lib/types"
import { ConfirmDialog } from "@/components/app/confirm-dialog"
import { CopyButton } from "@/components/app/copy-button"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"

export type McpEndpoint = { url: string; configured: boolean }

/**
 * Connects an AI assistant to the MCP server of Lika with the user API key of the signed-in user.
 */
export function McpCard({ mcp }: { mcp: McpEndpoint }) {
  const queryClient = useQueryClient()
  const me = useQuery({ queryKey: ["me"], queryFn: () => api<Me>("/api/me") })
  const key = me.data?.apiKey
  const [secret, setSecret] = useState<string>()
  const [confirming, setConfirming] = useState<"regenerate" | "revoke">()

  const create = useMutation({
    mutationFn: () => api<ApiKeySecret>("/api/me/api-key", { method: "POST" }),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ["me"] })
      setConfirming(undefined)
      setSecret(result.secret)
    },
    onError: (e) => toast.error(e.message),
  })
  const revoke = useMutation({
    mutationFn: () => api("/api/me/api-key", { method: "DELETE" }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["me"] })
      setConfirming(undefined)
      toast.success("User API key revoked", { description: "Assistants using it are disconnected." })
    },
    onError: (e) => toast.error(e.message),
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Bot className="size-4 text-brand" /> AI assistants (MCP)
        </CardTitle>
        <CardDescription>
          Let Claude, Cursor or any MCP client manage links for you. Your user API key acts with your current rights, and only works with the MCP server.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="space-y-1.5">
          <p className="text-sm font-medium">MCP server URL</p>
          <div className="flex items-center gap-1 rounded-md border bg-muted/40 px-2 py-1">
            <code className="min-w-0 flex-1 truncate font-mono text-xs" aria-label="MCP server URL">
              {mcp.url}
            </code>
            <CopyButton value={mcp.url} label="Copy MCP URL" />
          </div>
          {!mcp.configured && <p className="text-xs text-muted-foreground">This is the address the web interface uses. If assistants cannot reach it, ask an admin to set LIKA_PUBLIC_API_URL on the API.</p>}
        </div>

        <div className="flex flex-wrap items-center justify-between gap-3 rounded-md border p-3">
          {key ? (
            <div className="min-w-0 text-sm">
              <p>
                <code className="font-mono text-xs">
                  {key.prefix}
                  {"•".repeat(8)}
                </code>
              </p>
              <p className="text-xs text-muted-foreground">
                Created {format(new Date(key.createdAt), "PP")} · {key.lastUsedAt ? `last used ${formatDistanceToNow(new Date(key.lastUsedAt), { addSuffix: true })}` : "never used"}
              </p>
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">{me.isPending ? "Loading…" : "You have no user API key."}</p>
          )}
          <div className="flex gap-2">
            {key ? (
              <>
                <Button variant="secondary" size="sm" onClick={() => setConfirming("regenerate")}>
                  <RefreshCw /> Regenerate
                </Button>
                <Button variant="ghost" size="sm" onClick={() => setConfirming("revoke")}>
                  <Trash2 /> Revoke
                </Button>
              </>
            ) : (
              <Button size="sm" disabled={create.isPending || me.isPending} onClick={() => create.mutate()}>
                {create.isPending && <Loader2 className="animate-spin" />}
                Create user API key
              </Button>
            )}
          </div>
        </div>

        <Snippets url={mcp.url} />

        <ClaudePlugin mcp={mcp} />
      </CardContent>

      <ConfirmDialog
        open={!!confirming}
        onOpenChange={(open) => !open && setConfirming(undefined)}
        title={confirming === "revoke" ? "Revoke your user API key?" : "Regenerate your user API key?"}
        description={
          confirming === "revoke"
            ? "Assistants using it are disconnected immediately."
            : "The current key stops working immediately: update your assistants with the new one."
        }
        confirmLabel={confirming === "revoke" ? "Revoke" : "Regenerate"}
        pending={create.isPending || revoke.isPending}
        onConfirm={() => (confirming === "revoke" ? revoke.mutate() : create.mutate())}
      />
      <Dialog open={!!secret} onOpenChange={(open) => !open && setSecret(undefined)}>
        <DialogContent className="sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>Your user API key</DialogTitle>
            <DialogDescription>Copy it now: it is not shown again. Anyone holding it acts as you on Lika.</DialogDescription>
          </DialogHeader>
          <div className="flex items-center gap-1 rounded-md border bg-muted/40 p-2">
            <code className="min-w-0 flex-1 font-mono text-xs break-all" aria-label="User API key">
              {secret}
            </code>
            {secret && <CopyButton value={secret} label="Copy key" />}
          </div>
          <Snippets url={mcp.url} secret={secret} />
          <DialogFooter>
            <Button onClick={() => setSecret(undefined)}>Done</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  )
}

function Snippets({ url, secret }: { url: string; secret?: string }) {
  const [client, setClient] = useState<McpClient>("claude-code")
  const snippet = mcpSnippet(client, url, secret)
  const hint = MCP_CLIENTS.find((c) => c.id === client)?.hint
  return (
    <div className="space-y-2">
      <div className="flex flex-wrap gap-1" role="tablist" aria-label="MCP client">
        {MCP_CLIENTS.map((c) => (
          <Button key={c.id} role="tab" aria-selected={client === c.id} variant={client === c.id ? "secondary" : "ghost"} size="sm" onClick={() => setClient(c.id)}>
            {c.label}
          </Button>
        ))}
      </div>
      <div className="relative rounded-md border bg-muted/40">
        <pre className="overflow-x-auto p-3 pr-10 font-mono text-xs leading-relaxed" aria-label="MCP configuration">
          {snippet}
        </pre>
        <div className="absolute top-1.5 right-1.5">
          <CopyButton value={snippet} label="Copy configuration" />
        </div>
      </div>
      {!secret && (
        <Alert>
          <AlertDescription className="text-xs">{hint} Replace the placeholder with your user API key, shown once when you create it.</AlertDescription>
        </Alert>
      )}
      {secret && <p className="text-xs text-muted-foreground">{hint}</p>}
    </div>
  )
}

/**
 * Claude reads skills from plugins only, not from the MCP server: the plugin brings them, for the user's rights now.
 */
function ClaudePlugin({ mcp }: { mcp: McpEndpoint }) {
  const download = useMutation({
    mutationFn: async () => {
      // The API knows the public URL when LIKA_PUBLIC_API_URL is set; otherwise, the one shown here
      const response = await fetch(withQuery("/bff/api/me/claude-plugin", { mcpUrl: mcp.configured ? undefined : mcp.url }))
      if (!response.ok) {
        const error = (await response.json().catch(() => undefined)) as { message?: string } | undefined
        throw new ApiError(response.status, error?.message ?? `Download failed (${response.status})`)
      }
      const url = URL.createObjectURL(await response.blob())
      const a = document.createElement("a")
      a.href = url
      a.download = "lika-claude-plugin.zip"
      a.click()
      URL.revokeObjectURL(url)
    },
    onError: (e) => toast.error(e.message),
  })
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 rounded-md border p-3">
      <div className="min-w-0 flex-1 space-y-1 text-sm">
        <p className="font-medium">Claude plugin</p>
        <p className="text-xs text-muted-foreground">
          The skills that teach Claude how to use Lika, for your current rights, with this MCP server as connector. Upload it in Claude, under Customize › Plugins ›
          Add › Upload plugin. For Claude Code, set LIKA_API_KEY to your user API key. Download it again when your rights change.
        </p>
      </div>
      <Button variant="secondary" size="sm" disabled={download.isPending} onClick={() => download.mutate()}>
        {download.isPending ? <Loader2 className="animate-spin" /> : <Download />}
        Download plugin
      </Button>
    </div>
  )
}
