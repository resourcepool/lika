"use client"

import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { format, formatDistanceToNow } from "date-fns"
import { Bot, Download, Loader2, Plus, Trash2 } from "lucide-react"
import { toast } from "sonner"
import { ApiError, api, withQuery } from "@/lib/client"
import { MCP_CLIENTS, mcpSnippet, type McpClient } from "@/lib/mcp-snippets"
import type { ApiKey, ApiKeySecret, Me } from "@/lib/types"
import { ConfirmDialog } from "@/components/app/confirm-dialog"
import { CopyButton } from "@/components/app/copy-button"
import { FormField } from "@/components/app/form-field"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"

export type McpEndpoint = { url: string; configured: boolean }

/** Suggested names for a key: the application it is for */
const APPLICATIONS = ["Claude Desktop", "Claude Code", "Cursor", "VS Code"]

/**
 * Connects AI assistants to the MCP server of Lika with user API keys of the signed-in user, one per application.
 */
export function McpCard({ mcp }: { mcp: McpEndpoint }) {
  const queryClient = useQueryClient()
  const me = useQuery({ queryKey: ["me"], queryFn: () => api<Me>("/api/me") })
  const keys = me.data?.apiKeys ?? []
  const [creating, setCreating] = useState(false)
  const [name, setName] = useState("")
  const [created, setCreated] = useState<ApiKeySecret>()
  const [revoking, setRevoking] = useState<ApiKey>()
  const taken = keys.some((k) => k.name.toLowerCase() === name.trim().toLowerCase())

  const create = useMutation({
    mutationFn: () => api<ApiKeySecret>("/api/me/api-keys", { method: "POST", body: { name: name.trim() } }),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ["me"] })
      setCreating(false)
      setCreated(result)
    },
    onError: (e) => toast.error(e.message),
  })
  const revoke = useMutation({
    mutationFn: (key: ApiKey) => api(`/api/me/api-keys/${key.id}`, { method: "DELETE" }),
    onSuccess: (_, key) => {
      queryClient.invalidateQueries({ queryKey: ["me"] })
      setRevoking(undefined)
      toast.success(`Key of ${key.name} revoked`, { description: "It is disconnected. Your other applications are not." })
    },
    onError: (e) => toast.error(e.message),
  })

  function openCreate() {
    setName("")
    setCreating(true)
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Bot className="size-4 text-brand" /> AI assistants (MCP)
        </CardTitle>
        <CardDescription>
          Let Claude, Cursor or any MCP client manage links for you. Create one user API key per application: each acts with your current rights, only works with
          the MCP server, and can be revoked without disconnecting the others.
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

        <div className="space-y-1.5">
          <div className="flex items-center justify-between gap-3">
            <p className="text-sm font-medium">User API keys</p>
            <Button size="sm" variant={keys.length ? "secondary" : "default"} disabled={me.isPending} onClick={openCreate}>
              <Plus /> New key
            </Button>
          </div>
          {keys.length === 0 ? (
            <p className="rounded-md border p-3 text-sm text-muted-foreground">{me.isPending ? "Loading…" : "You have no user API key."}</p>
          ) : (
            <ul className="divide-y rounded-md border" aria-label="User API keys">
              {keys.map((key) => (
                <li key={key.id} className="flex flex-wrap items-center justify-between gap-3 p-3">
                  <div className="min-w-0 text-sm">
                    <p className="flex flex-wrap items-baseline gap-x-2">
                      <span className="font-medium">{key.name}</span>
                      <code className="font-mono text-xs text-muted-foreground">
                        {key.prefix}
                        {"•".repeat(8)}
                      </code>
                    </p>
                    <p className="text-xs text-muted-foreground">
                      Created {format(new Date(key.createdAt), "PP")} · {key.lastUsedAt ? `last used ${formatDistanceToNow(new Date(key.lastUsedAt), { addSuffix: true })}` : "never used"}
                    </p>
                  </div>
                  <Button variant="ghost" size="sm" aria-label={`Revoke the key of ${key.name}`} onClick={() => setRevoking(key)}>
                    <Trash2 /> Revoke
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </div>

        <Snippets url={mcp.url} />

        <ClaudePlugin mcp={mcp} />
      </CardContent>

      <Dialog open={creating} onOpenChange={setCreating}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>New user API key</DialogTitle>
            <DialogDescription>Which application will use it? Its name tells your keys apart, with when each was last used.</DialogDescription>
          </DialogHeader>
          <form
            id="user-key-form"
            className="space-y-3"
            onSubmit={(e) => {
              e.preventDefault()
              create.mutate()
            }}
          >
            <FormField label="Application" htmlFor="user-key-name" error={taken ? "You already have a key for this application" : undefined}>
              <Input id="user-key-name" required autoFocus maxLength={60} placeholder="Claude Desktop" value={name} onChange={(e) => setName(e.target.value)} />
            </FormField>
            <div className="flex flex-wrap gap-1.5">
              {APPLICATIONS.filter((a) => !keys.some((k) => k.name.toLowerCase() === a.toLowerCase())).map((application) => (
                <button key={application} type="button" className="rounded border px-1.5 py-0.5 text-xs hover:border-brand" onClick={() => setName(application)}>
                  {application}
                </button>
              ))}
            </div>
          </form>
          <DialogFooter>
            <Button variant="ghost" onClick={() => setCreating(false)}>
              Cancel
            </Button>
            <Button type="submit" form="user-key-form" disabled={create.isPending || !name.trim() || taken}>
              {create.isPending && <Loader2 className="animate-spin" />}
              Create key
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
      <ConfirmDialog
        open={!!revoking}
        onOpenChange={(open) => !open && setRevoking(undefined)}
        title={`Revoke the key of ${revoking?.name ?? ""}?`}
        description="This application is disconnected immediately. Your other keys keep working."
        confirmLabel="Revoke"
        pending={revoke.isPending}
        onConfirm={() => revoking && revoke.mutate(revoking)}
      />
      <Dialog open={!!created} onOpenChange={(open) => !open && setCreated(undefined)}>
        <DialogContent className="sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>Your user API key for {created?.key.name}</DialogTitle>
            <DialogDescription>Copy it now: it is not shown again. Anyone holding it acts as you on Lika.</DialogDescription>
          </DialogHeader>
          <div className="flex items-center gap-1 rounded-md border bg-muted/40 p-2">
            <code className="min-w-0 flex-1 font-mono text-xs break-all" aria-label="User API key">
              {created?.secret}
            </code>
            {created && <CopyButton value={created.secret} label="Copy key" />}
          </div>
          <Snippets url={mcp.url} secret={created?.secret} />
          <DialogFooter>
            <Button onClick={() => setCreated(undefined)}>Done</Button>
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
          <AlertDescription className="text-xs">{hint} Replace the placeholder with a user API key, shown once when you create it.</AlertDescription>
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
          Add › Upload plugin. For Claude Code, set LIKA_API_KEY to a user API key. Download it again when your rights change.
        </p>
      </div>
      <Button variant="secondary" size="sm" disabled={download.isPending} onClick={() => download.mutate()}>
        {download.isPending ? <Loader2 className="animate-spin" /> : <Download />}
        Download plugin
      </Button>
    </div>
  )
}
