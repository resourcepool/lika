"use client"

import { useEffect, useState } from "react"
import { useForm, useWatch } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Loader2, MoreHorizontal, Pencil, Plus, Trash2 } from "lucide-react"
import { toast } from "sonner"
import { api } from "@/lib/client"
import type { Domain, Page, Link } from "@/lib/types"
import { useAccounts, useDomains } from "@/hooks/use-lika"
import { useSession } from "@/components/app/session-context"
import { PageHeader } from "@/components/app/page-header"
import { ConfirmDialog } from "@/components/app/confirm-dialog"
import { EmptyState } from "@/components/app/empty-state"
import { FormField } from "@/components/app/form-field"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { Switch } from "@/components/ui/switch"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"

const SELF = "self"

export function DomainsView() {
  const domains = useDomains()
  const accounts = useAccounts()
  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<Domain>()
  const [deleting, setDeleting] = useState<Domain>()
  const ownerName = (id: string | null) => (id ? (accounts.data?.find((a) => a.id === id)?.name ?? id.slice(0, 8)) : "system")

  return (
    <>
      <PageHeader title="Domains" description="Hosts serving short links. Keys manage the domains they own or were granted.">
        <Button onClick={() => setCreating(true)}>
          <Plus />
          New domain
        </Button>
      </PageHeader>
      <div className="p-4 md:p-6">
        <div className="overflow-hidden rounded-lg border">
          <Table>
            <TableHeader className="bg-muted/40">
              <TableRow className="hover:bg-transparent">
                {["Host", "Type", "Owner", "Short URL base", "Landing", ""].map((h, i) => (
                  <TableHead key={i} className="font-mono text-[11px] tracking-wider uppercase">
                    {h}
                  </TableHead>
                ))}
              </TableRow>
            </TableHeader>
            <TableBody>
              {domains.isPending && (
                <TableRow>
                  <TableCell colSpan={6}>
                    <Skeleton className="h-8 w-full" />
                  </TableCell>
                </TableRow>
              )}
              {domains.data?.map((d) => (
                <TableRow key={d.id}>
                  <TableCell className="font-mono text-sm font-medium">{d.host}</TableCell>
                  <TableCell>
                    <Badge variant="outline" className="font-mono text-[11px]">
                      {d.standard ? "standard" : "custom"}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-sm">{ownerName(d.ownerId)}</TableCell>
                  <TableCell className="font-mono text-xs text-muted-foreground">{d.apiBaseUrl}</TableCell>
                  <TableCell className="font-mono text-xs text-muted-foreground">{d.landingBaseUrl ?? "—"}</TableCell>
                  <TableCell className="text-right">
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="ghost" size="icon" className="size-8" aria-label={`Actions for ${d.host}`}>
                          <MoreHorizontal />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem onSelect={() => setEditing(d)}>
                          <Pencil /> Edit URLs
                        </DropdownMenuItem>
                        <DropdownMenuItem variant="destructive" onSelect={() => setDeleting(d)}>
                          <Trash2 /> Delete
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </TableCell>
                </TableRow>
              ))}
              {domains.data?.length === 0 && (
                <TableRow className="hover:bg-transparent">
                  <TableCell colSpan={6}>
                    <EmptyState title="No domain" description="Add the host your short links are served from." />
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </div>
      </div>
      <CreateDomainDialog open={creating} onOpenChange={setCreating} />
      <EditDomainDialog domain={editing} onOpenChange={(open) => !open && setEditing(undefined)} />
      <DeleteDomainDialog key={`delete-${deleting?.id}`} domain={deleting} onOpenChange={(open) => !open && setDeleting(undefined)} />
    </>
  )
}

const urlField = z.url({ message: "Enter a full URL, e.g. https://go.example.com" })

const createSchema = z.object({
  host: z
    .string()
    .trim()
    .toLowerCase()
    .regex(/^[a-z0-9.-]+(:\d+)?$/, "A host name, optionally with a port, e.g. go.example.com"),
  apiBaseUrl: urlField,
  landingBaseUrl: z.union([z.literal(""), urlField]),
  ownerId: z.string(),
  standard: z.boolean(),
})

function CreateDomainDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const queryClient = useQueryClient()
  const session = useSession()
  const accounts = useAccounts(open)
  const form = useForm<z.infer<typeof createSchema>>({
    resolver: zodResolver(createSchema),
    defaultValues: { host: "", apiBaseUrl: "", landingBaseUrl: "", ownerId: SELF, standard: false },
  })
  useEffect(() => {
    if (open) form.reset()
  }, [open, form])

  const create = useMutation({
    mutationFn: (v: z.infer<typeof createSchema>) =>
      api<Domain>("/api/admin/domains", {
        method: "POST",
        body: { host: v.host, apiBaseUrl: v.apiBaseUrl.replace(/\/+$/, ""), landingBaseUrl: v.landingBaseUrl },
        query: { ownerId: v.ownerId === SELF ? undefined : v.ownerId, global: v.standard || undefined },
      }),
    onSuccess: (d) => {
      queryClient.invalidateQueries({ queryKey: ["domains"] })
      toast.success(`${d.host} added`)
      onOpenChange(false)
    },
    onError: (e) => toast.error(e.message),
  })

  const { errors } = form.formState
  const [host, apiBaseUrl, ownerId, standard] = useWatch({ control: form.control, name: ["host", "apiBaseUrl", "ownerId", "standard"] })
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>New domain</DialogTitle>
          <DialogDescription>Point the host to the Lika API, then register it here. The host cannot change later.</DialogDescription>
        </DialogHeader>
        <form id="domain-form" className="space-y-4" onSubmit={form.handleSubmit((v) => create.mutate(v))}>
          <FormField label="Host" htmlFor="host" error={errors.host?.message}>
            <Input
              id="host"
              placeholder="go.example.com"
              className="font-mono text-sm"
              {...form.register("host", {
                onBlur: (e) => !form.getValues("apiBaseUrl") && e.target.value && form.setValue("apiBaseUrl", `https://${e.target.value.trim().toLowerCase()}`),
              })}
            />
          </FormField>
          <FormField label="Short URL base" htmlFor="apiBaseUrl" error={errors.apiBaseUrl?.message} hint={`Short links read ${apiBaseUrl || "https://" + (host || "host")}/slug`}>
            <Input id="apiBaseUrl" placeholder="https://go.example.com" className="font-mono text-sm" {...form.register("apiBaseUrl")} />
          </FormField>
          <FormField label="Landing base URL" htmlFor="landingBaseUrl" error={errors.landingBaseUrl?.message} hint="Optional. Home page of the domain, and target of landing links.">
            <Input id="landingBaseUrl" placeholder="https://example.com" className="font-mono text-sm" {...form.register("landingBaseUrl")} />
          </FormField>
          <FormField label="Owner">
            <Select value={ownerId} onValueChange={(v) => form.setValue("ownerId", v)}>
              <SelectTrigger className="w-full" aria-label="Owner">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={SELF}>Me{session.accountName ? ` (${session.accountName})` : ""}</SelectItem>
                {accounts.data
                  ?.filter((a) => a.id !== session.accountId)
                  .map((a) => (
                    <SelectItem key={a.id} value={a.id}>
                      {a.name}
                    </SelectItem>
                  ))}
              </SelectContent>
            </Select>
          </FormField>
          <label className="flex items-center justify-between gap-4 rounded-md border p-3">
            <span>
              <span className="block text-sm font-medium">Standard domain</span>
              <span className="block text-xs text-muted-foreground">Shown as system-wide. Other keys still need a grant.</span>
            </span>
            <Switch checked={standard} onCheckedChange={(v) => form.setValue("standard", v)} />
          </label>
        </form>
        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button type="submit" form="domain-form" disabled={create.isPending}>
            {create.isPending && <Loader2 className="animate-spin" />}
            Add domain
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

const editSchema = z.object({ apiBaseUrl: urlField, landingBaseUrl: z.union([z.literal(""), urlField]) })

function EditDomainDialog({ domain, onOpenChange }: { domain?: Domain; onOpenChange: (open: boolean) => void }) {
  const queryClient = useQueryClient()
  const form = useForm<z.infer<typeof editSchema>>({ resolver: zodResolver(editSchema) })
  useEffect(() => {
    if (domain) form.reset({ apiBaseUrl: domain.apiBaseUrl, landingBaseUrl: domain.landingBaseUrl ?? "" })
  }, [domain, form])

  const update = useMutation({
    mutationFn: (v: z.infer<typeof editSchema>) =>
      api<Domain>(`/api/admin/domains/${domain!.id}`, { method: "PATCH", body: { apiBaseUrl: v.apiBaseUrl.replace(/\/+$/, ""), landingBaseUrl: v.landingBaseUrl } }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["domains"] })
      queryClient.invalidateQueries({ queryKey: ["links"] })
      toast.success("Domain updated")
      onOpenChange(false)
    },
    onError: (e) => toast.error(e.message),
  })
  const { errors } = form.formState
  return (
    <Dialog open={!!domain} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle className="font-mono">{domain?.host}</DialogTitle>
          <DialogDescription>Changing the short URL base rewrites the short URL of every link of the domain.</DialogDescription>
        </DialogHeader>
        <form id="domain-edit-form" className="space-y-4" onSubmit={form.handleSubmit((v) => update.mutate(v))}>
          <FormField label="Short URL base" htmlFor="edit-apiBaseUrl" error={errors.apiBaseUrl?.message}>
            <Input id="edit-apiBaseUrl" className="font-mono text-sm" {...form.register("apiBaseUrl")} />
          </FormField>
          <FormField label="Landing base URL" htmlFor="edit-landingBaseUrl" error={errors.landingBaseUrl?.message}>
            <Input id="edit-landingBaseUrl" placeholder="optional" className="font-mono text-sm" {...form.register("landingBaseUrl")} />
          </FormField>
        </form>
        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button type="submit" form="domain-edit-form" disabled={update.isPending}>
            {update.isPending && <Loader2 className="animate-spin" />}
            Save
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/**
 * Deleting a domain with links requires typing its host: the API then deletes them too.
 */
function DeleteDomainDialog({ domain, onOpenChange }: { domain?: Domain; onOpenChange: (open: boolean) => void }) {
  const queryClient = useQueryClient()
  const [typed, setTyped] = useState("")
  const count = useQuery({
    queryKey: ["links", "count", domain?.id],
    queryFn: () => api<Page<Link>>("/api/links", { query: { domainId: domain!.id, limit: 1 } }).then((p) => p.totalItemCount),
    enabled: !!domain,
  })
  const linkCount = count.data ?? 0
  const remove = useMutation({
    mutationFn: () => api(`/api/admin/domains/${domain!.id}`, { method: "DELETE", query: { force: linkCount > 0 || undefined } }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["domains"] })
      queryClient.invalidateQueries({ queryKey: ["links"] })
      queryClient.invalidateQueries({ queryKey: ["accounts"] })
      toast.success(`${domain?.host} deleted`)
      onOpenChange(false)
    },
    onError: (e) => toast.error(e.message),
  })
  return (
    <ConfirmDialog
      open={!!domain}
      onOpenChange={onOpenChange}
      title={`Delete ${domain?.host}?`}
      description={
        count.isPending ? (
          "Counting links…"
        ) : linkCount > 0 ? (
          <>
            Its <strong className="text-foreground">{linkCount} link{linkCount > 1 ? "s" : ""}</strong> will be deleted too, and stop working immediately. Keys
            granted this domain lose access. This cannot be undone.
          </>
        ) : (
          "Keys granted this domain lose access. This cannot be undone."
        )
      }
      confirmLabel={linkCount > 0 ? `Delete domain and ${linkCount} link${linkCount > 1 ? "s" : ""}` : "Delete domain"}
      pending={remove.isPending}
      disabled={count.isPending || (linkCount > 0 && typed !== domain?.host)}
      onConfirm={() => remove.mutate()}
    >
      {linkCount > 0 && (
        <div className="space-y-1.5">
          <Label htmlFor="confirm-host" className="text-xs text-muted-foreground">
            Type <span className="font-mono text-foreground">{domain?.host}</span> to confirm
          </Label>
          <Input id="confirm-host" autoComplete="off" className="font-mono text-sm" value={typed} onChange={(e) => setTyped(e.target.value)} />
        </div>
      )}
    </ConfirmDialog>
  )
}
