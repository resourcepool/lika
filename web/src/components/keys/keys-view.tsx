"use client"

import { useMemo, useState } from "react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { format, formatDistanceToNow, isPast } from "date-fns"
import { Loader2, MoreHorizontal, Pencil, Plus, RefreshCw, Trash2 } from "lucide-react"
import { toast } from "sonner"
import { api } from "@/lib/client"
import type { ApiKey, ApiKeySecret, Domain, DomainRole } from "@/lib/types"
import { useApiKeys, useDomains, useUsers } from "@/hooks/use-lika"
import { useSession } from "@/components/app/session-context"
import { AccessBadges, OneTimeDialog, RolesEditor } from "@/components/access/access"
import { PageHeader } from "@/components/app/page-header"
import { ConfirmDialog } from "@/components/app/confirm-dialog"
import { EmptyState } from "@/components/app/empty-state"
import { FormField } from "@/components/app/form-field"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from "@/components/ui/dropdown-menu"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { Switch } from "@/components/ui/switch"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"

/** Applications read or edit links: managing a domain is for people */
const KEY_ROLES: DomainRole[] = ["viewer", "editor"]

function useGrantableDomains(): Domain[] {
  const domains = useDomains()
  const session = useSession()
  return useMemo(
    () => (domains.data ?? []).filter((d) => session.admin || session.managedDomainIds.includes(d.id)),
    [domains.data, session.admin, session.managedDomainIds],
  )
}

/** End of the chosen day, local time */
function endOfDay(date: string): string | null {
  return date ? new Date(`${date}T23:59:59`).toISOString() : null
}

export function KeysView() {
  const session = useSession()
  const keys = useApiKeys()
  const domains = useDomains()
  const users = useUsers()
  const queryClient = useQueryClient()
  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<ApiKey>()
  const [regenerating, setRegenerating] = useState<ApiKey>()
  const [deleting, setDeleting] = useState<ApiKey>()
  const [secret, setSecret] = useState<ApiKeySecret>()

  const hostOf = (id: string) => domains.data?.find((d) => d.id === id)?.host
  const creatorOf = (id: string | null) => {
    if (!id) return "migrated"
    if (id === session.user.id) return "you"
    return users.data?.find((u) => u.id === id)?.name ?? keys.data?.find((k) => k.id === id)?.name ?? "—"
  }
  const refresh = () => queryClient.invalidateQueries({ queryKey: ["keys"] })

  const regenerate = useMutation({
    mutationFn: (key: ApiKey) => api<ApiKeySecret>(`/api/keys/${key.id}/regenerate`, { method: "POST" }),
    onSuccess: (result) => {
      refresh()
      setRegenerating(undefined)
      setSecret(result)
    },
    onError: (e) => toast.error(e.message),
  })

  const remove = useMutation({
    mutationFn: (key: ApiKey) => api(`/api/keys/${key.id}`, { method: "DELETE" }),
    onSuccess: (_, key) => {
      refresh()
      setDeleting(undefined)
      toast.success(`${key.name} deleted`)
    },
    onError: (e) => toast.error(e.message),
  })

  return (
    <>
      <PageHeader title="API Keys" description="Credentials of the applications calling the Lika API. A key outlives the person who created it.">
        <Button onClick={() => setCreating(true)}>
          <Plus />
          New key
        </Button>
      </PageHeader>
      <div className="p-4 md:p-6">
        <div className="overflow-hidden rounded-lg border">
          <Table>
            <TableHeader className="bg-muted/40">
              <TableRow className="hover:bg-transparent">
                {["Application", "Access", "Key", "Created", "Last used", "Expires", ""].map((h, i) => (
                  <TableHead key={i} className="font-mono text-[11px] tracking-wider uppercase">
                    {h}
                  </TableHead>
                ))}
              </TableRow>
            </TableHeader>
            <TableBody>
              {keys.isPending && (
                <TableRow>
                  <TableCell colSpan={7}>
                    <Skeleton className="h-8 w-full" />
                  </TableCell>
                </TableRow>
              )}
              {keys.data?.map((key) => (
                <TableRow key={key.id}>
                  <TableCell className="font-medium">{key.name}</TableCell>
                  <TableCell className="max-w-xs">
                    <AccessBadges admin={key.admin} domains={key.domains} hostOf={hostOf} />
                  </TableCell>
                  <TableCell>
                    <code className="font-mono text-xs text-muted-foreground">
                      {key.prefix}
                      {"•".repeat(8)}
                    </code>
                  </TableCell>
                  <TableCell className="text-sm text-muted-foreground">
                    <span title={format(new Date(key.createdAt), "PPpp")}>{format(new Date(key.createdAt), "PP")}</span>
                    <span className="block font-mono text-[11px]">by {creatorOf(key.createdBy)}</span>
                  </TableCell>
                  <TableCell className="text-sm text-muted-foreground">
                    {key.lastUsedAt ? formatDistanceToNow(new Date(key.lastUsedAt), { addSuffix: true }) : "Never"}
                  </TableCell>
                  <TableCell className="text-sm">
                    {!key.expiresAt ? (
                      <span className="text-muted-foreground">Never</span>
                    ) : isPast(new Date(key.expiresAt)) ? (
                      <Badge variant="destructive" className="font-mono text-[11px]">
                        expired
                      </Badge>
                    ) : (
                      <span className="text-muted-foreground">{format(new Date(key.expiresAt), "PP")}</span>
                    )}
                  </TableCell>
                  <TableCell className="text-right">
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="ghost" size="icon" className="size-8" aria-label={`Actions for ${key.name}`}>
                          <MoreHorizontal />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem onSelect={() => setEditing(key)}>
                          <Pencil /> Edit
                        </DropdownMenuItem>
                        <DropdownMenuItem onSelect={() => setRegenerating(key)}>
                          <RefreshCw /> Regenerate secret
                        </DropdownMenuItem>
                        <DropdownMenuSeparator />
                        <DropdownMenuItem variant="destructive" onSelect={() => setDeleting(key)}>
                          <Trash2 /> Delete
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          {keys.isSuccess && keys.data.length === 0 && <EmptyState title="No API key" description="Create one for each application calling the Lika API." />}
        </div>
      </div>

      {creating && (
        <KeyDialog
          onOpenChange={setCreating}
          onSaved={(result) => {
            refresh()
            setCreating(false)
            if ("secret" in result) setSecret(result)
          }}
        />
      )}
      {editing && (
        <KeyDialog
          apiKey={editing}
          onOpenChange={(open) => !open && setEditing(undefined)}
          onSaved={() => {
            refresh()
            setEditing(undefined)
            toast.success("Key updated")
          }}
        />
      )}
      <ConfirmDialog
        open={!!regenerating}
        onOpenChange={(open) => !open && setRegenerating(undefined)}
        title={`Regenerate the secret of ${regenerating?.name}?`}
        description="The current secret stops working immediately. The application must be updated with the new one."
        confirmLabel="Regenerate"
        pending={regenerate.isPending}
        onConfirm={() => regenerating && regenerate.mutate(regenerating)}
      />
      <ConfirmDialog
        open={!!deleting}
        onOpenChange={(open) => !open && setDeleting(undefined)}
        title={`Delete ${deleting?.name}?`}
        description="The key stops working immediately. The links it created are kept."
        confirmLabel="Delete key"
        pending={remove.isPending}
        onConfirm={() => deleting && remove.mutate(deleting)}
      />
      <OneTimeDialog
        open={!!secret}
        onOpenChange={(open) => !open && setSecret(undefined)}
        title={`Secret of ${secret?.key.name}`}
        label="API key"
        value={secret?.secret}
        description={
          <>
            Send it as <code className="font-mono text-xs text-foreground">Authorization: Bearer &lt;key&gt;</code> or{" "}
            <code className="font-mono text-xs text-foreground">X-Api-Key: &lt;key&gt;</code>.
          </>
        }
        warning="Copy it now: it is not shown again. If it is lost, regenerate it."
      />
    </>
  )
}

function KeyDialog({ apiKey, onOpenChange, onSaved }: { apiKey?: ApiKey; onOpenChange: (open: boolean) => void; onSaved: (result: ApiKey | ApiKeySecret) => void }) {
  const session = useSession()
  const domains = useGrantableDomains()
  const [name, setName] = useState(apiKey?.name ?? "")
  const [admin, setAdmin] = useState(apiKey?.admin ?? false)
  const [roles, setRoles] = useState<Record<string, DomainRole>>(apiKey?.domains ?? {})
  const [expires, setExpires] = useState(!!apiKey?.expiresAt)
  const [expiryDate, setExpiryDate] = useState(apiKey?.expiresAt ? format(new Date(apiKey.expiresAt), "yyyy-MM-dd") : "")
  const save = useMutation({
    mutationFn: () => {
      const expiresAt = expires ? endOfDay(expiryDate) : null
      if (apiKey) {
        return api<ApiKey>(`/api/keys/${apiKey.id}`, {
          method: "PATCH",
          body: { name: name.trim(), domains: apiKey.admin ? undefined : roles, expiresAt: expiresAt ?? undefined, removeExpiry: !expires },
        })
      }
      return api<ApiKeySecret>("/api/keys", { method: "POST", body: { name: name.trim(), admin, domains: admin ? {} : roles, expiresAt } })
    },
    onSuccess: onSaved,
    onError: (e) => toast.error(e.message),
  })
  const valid = name.trim() && (admin || Object.keys(roles).length > 0) && (!expires || expiryDate)
  return (
    <Dialog open onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{apiKey ? `Edit ${apiKey.name}` : "New API key"}</DialogTitle>
          <DialogDescription>{apiKey ? "Changes apply to the next request of the application." : "The secret is shown once, right after creation."}</DialogDescription>
        </DialogHeader>
        <form
          id="key-form"
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault()
            save.mutate()
          }}
        >
          <FormField label="Application" htmlFor="key-name">
            <Input id="key-name" required value={name} onChange={(e) => setName(e.target.value)} placeholder="marketing-site" />
          </FormField>
          {session.admin && !apiKey && (
            <label className="flex items-center justify-between gap-4 rounded-md border p-3">
              <span>
                <span className="block text-sm font-medium">Admin key</span>
                <span className="block text-xs text-muted-foreground">Every right, e.g. for backups. Keep it safe.</span>
              </span>
              <Switch checked={admin} onCheckedChange={setAdmin} aria-label="Admin key" />
            </label>
          )}
          {!admin && (
            <FormField label="Access">
              <RolesEditor domains={domains} value={roles} onChange={setRoles} roles={KEY_ROLES} />
            </FormField>
          )}
          <div className="flex flex-wrap items-center gap-3 rounded-md border p-3">
            <label className="flex items-center gap-2 text-sm">
              <Checkbox checked={expires} onCheckedChange={(c) => setExpires(c === true)} aria-label="Expires" />
              Expires on
            </label>
            <Input type="date" className="w-44" disabled={!expires} value={expiryDate} min={format(new Date(), "yyyy-MM-dd")} onChange={(e) => setExpiryDate(e.target.value)} aria-label="Expiry date" />
          </div>
        </form>
        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button type="submit" form="key-form" disabled={save.isPending || !valid}>
            {save.isPending && <Loader2 className="animate-spin" />}
            {apiKey ? "Save" : "Create key"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
