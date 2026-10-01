"use client"

import { useMemo, useState } from "react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { Eye, EyeOff, Globe, Loader2, MoreHorizontal, Pencil, Plus, RefreshCw, Search, ShieldCheck, Trash2 } from "lucide-react"
import { toast } from "sonner"
import { api } from "@/lib/client"
import type { Account, Domain } from "@/lib/types"
import { useAccounts, useDomains } from "@/hooks/use-lika"
import { useSession } from "@/components/app/session-context"
import { PageHeader } from "@/components/app/page-header"
import { ConfirmDialog } from "@/components/app/confirm-dialog"
import { CopyButton } from "@/components/app/copy-button"
import { FormField } from "@/components/app/form-field"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from "@/components/ui/dropdown-menu"
import { Input } from "@/components/ui/input"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Skeleton } from "@/components/ui/skeleton"
import { Switch } from "@/components/ui/switch"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"

type TokenKind = "read_write" | "read"

export function KeysView() {
  const accounts = useAccounts()
  const domains = useDomains()
  const session = useSession()
  const queryClient = useQueryClient()
  const [creating, setCreating] = useState(false)
  const [renaming, setRenaming] = useState<Account>()
  const [granting, setGranting] = useState<Account>()
  const [regenerating, setRegenerating] = useState<{ account: Account; token: TokenKind }>()
  const [deleting, setDeleting] = useState<Account>()

  const refresh = () => queryClient.invalidateQueries({ queryKey: ["accounts"] })

  const regenerate = useMutation({
    mutationFn: ({ account, token }: { account: Account; token: TokenKind }) =>
      api<Account>(`/api/admin/accounts/${account.id}/regenerate`, { method: "POST", body: { token } }),
    onSuccess: (_, { account, token }) => {
      refresh()
      setRegenerating(undefined)
      toast.success(`${token === "read" ? "Read" : "Read-write"} key of ${account.name} regenerated`, { description: "The previous key no longer works." })
      if (account.id === session.accountId && token === "read_write") {
        // This browser was signed in with the revoked key: reload everything from the sign-in page
        // eslint-disable-next-line @next/next/no-location-assign-relative-destination
        window.location.assign("/signin?expired=1")
      }
    },
    onError: (e) => toast.error(e.message),
  })

  const remove = useMutation({
    mutationFn: (account: Account) => api(`/api/admin/accounts/${account.id}`, { method: "DELETE" }),
    onSuccess: (_, account) => {
      refresh()
      setDeleting(undefined)
      toast.success(`${account.name} deleted`)
    },
    onError: (e) => toast.error(e.message),
  })

  const hostOf = (id: string) => domains.data?.find((d) => d.id === id)?.host
  const owned = (account: Account) => domains.data?.filter((d) => d.ownerId === account.id) ?? []

  return (
    <>
      <PageHeader title="API Keys" description="Each account has a read-write key and a read-only key, scoped to its domains.">
        <Button onClick={() => setCreating(true)}>
          <Plus />
          New account
        </Button>
      </PageHeader>
      <div className="p-4 md:p-6">
        <div className="overflow-hidden rounded-lg border">
          <Table>
            <TableHeader className="bg-muted/40">
              <TableRow className="hover:bg-transparent">
                {["Account", "Domains", "Read-write key", "Read-only key", ""].map((h, i) => (
                  <TableHead key={i} className="font-mono text-[11px] tracking-wider uppercase">
                    {h}
                  </TableHead>
                ))}
              </TableRow>
            </TableHeader>
            <TableBody>
              {accounts.isPending && (
                <TableRow>
                  <TableCell colSpan={5}>
                    <Skeleton className="h-8 w-full" />
                  </TableCell>
                </TableRow>
              )}
              {accounts.data?.map((account) => (
                <TableRow key={account.id}>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <span className="font-medium">{account.name}</span>
                      {account.id === session.accountId && <span className="font-mono text-[11px] text-muted-foreground">(you)</span>}
                    </div>
                  </TableCell>
                  <TableCell className="max-w-xs">
                    {account.global ? (
                      <Badge className="gap-1 font-mono text-[11px]">
                        <ShieldCheck className="size-3" /> admin · all domains
                      </Badge>
                    ) : (
                      <div className="flex flex-wrap gap-1">
                        {owned(account).map((d) => (
                          <Badge key={d.id} variant="secondary" className="font-mono text-[11px]" title="Owned">
                            {d.host}
                          </Badge>
                        ))}
                        {account.domainIds.map((id) => (
                          <Badge key={id} variant="outline" className="font-mono text-[11px]">
                            {hostOf(id) ?? id.slice(0, 8)}
                          </Badge>
                        ))}
                        {account.domainIds.length + owned(account).length === 0 && <span className="text-xs text-muted-foreground">No domain</span>}
                      </div>
                    )}
                  </TableCell>
                  <TableCell>
                    <SecretToken value={account.defaultAccessToken} />
                  </TableCell>
                  <TableCell>
                    <SecretToken value={account.defaultPublicReadAccessToken} />
                  </TableCell>
                  <TableCell className="text-right">
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="ghost" size="icon" className="size-8" aria-label={`Actions for ${account.name}`}>
                          <MoreHorizontal />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem onSelect={() => setRenaming(account)}>
                          <Pencil /> Rename
                        </DropdownMenuItem>
                        {!account.global && (
                          <DropdownMenuItem onSelect={() => setGranting(account)}>
                            <Globe /> Edit domains
                          </DropdownMenuItem>
                        )}
                        <DropdownMenuSeparator />
                        <DropdownMenuItem onSelect={() => setRegenerating({ account, token: "read_write" })}>
                          <RefreshCw /> Regenerate read-write key
                        </DropdownMenuItem>
                        <DropdownMenuItem onSelect={() => setRegenerating({ account, token: "read" })}>
                          <RefreshCw /> Regenerate read-only key
                        </DropdownMenuItem>
                        <DropdownMenuSeparator />
                        <DropdownMenuItem variant="destructive" onSelect={() => setDeleting(account)}>
                          <Trash2 /> Delete
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      </div>

      {/* Keyed so each opening starts from a fresh form */}
      {creating && <CreateAccountDialog open onOpenChange={setCreating} domains={domains.data ?? []} />}
      <RenameDialog key={`rename-${renaming?.id}`} account={renaming} onOpenChange={(open) => !open && setRenaming(undefined)} />
      <GrantsDialog key={`grants-${granting?.id}`} account={granting} domains={domains.data ?? []} onOpenChange={(open) => !open && setGranting(undefined)} />
      <ConfirmDialog
        open={!!regenerating}
        onOpenChange={(open) => !open && setRegenerating(undefined)}
        title={`Regenerate the ${regenerating?.token === "read" ? "read-only" : "read-write"} key?`}
        description={
          <>
            The current key of <strong className="text-foreground">{regenerating?.account.name}</strong> stops working immediately. Clients using it must be
            updated with the new one.
            {regenerating?.account.id === session.accountId && regenerating?.token === "read_write" && " You are signed in with this key: you will be signed out."}
          </>
        }
        confirmLabel="Regenerate"
        pending={regenerate.isPending}
        onConfirm={() => regenerating && regenerate.mutate(regenerating)}
      />
      <ConfirmDialog
        open={!!deleting}
        onOpenChange={(open) => !open && setDeleting(undefined)}
        title={`Delete ${deleting?.name}?`}
        description="Both keys stop working immediately. Its links and owned domains are kept."
        confirmLabel="Delete account"
        pending={remove.isPending}
        onConfirm={() => deleting && remove.mutate(deleting)}
      />
    </>
  )
}

function SecretToken({ value }: { value: string }) {
  const [shown, setShown] = useState(false)
  return (
    <div className="flex items-center gap-0.5">
      <code className="w-32 truncate font-mono text-xs text-muted-foreground">{shown ? value : `${value.slice(0, 4)}${"•".repeat(12)}`}</code>
      <Button variant="ghost" size="icon" className="size-7 text-muted-foreground" onClick={() => setShown(!shown)} aria-label={shown ? "Hide key" : "Show key"}>
        {shown ? <EyeOff /> : <Eye />}
      </Button>
      <CopyButton value={value} label="Copy key" />
    </div>
  )
}

function DomainPicker({ domains, value, onChange }: { domains: Domain[]; value: string[]; onChange: (ids: string[]) => void }) {
  const [filter, setFilter] = useState("")
  const visible = useMemo(() => domains.filter((d) => d.host.includes(filter.trim().toLowerCase())), [domains, filter])
  const toggle = (id: string, checked: boolean) => onChange(checked ? [...value, id] : value.filter((v) => v !== id))
  return (
    <div className="rounded-md border">
      <div className="relative border-b">
        <Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input value={filter} onChange={(e) => setFilter(e.target.value)} placeholder="Filter domains" className="border-0 pl-9 shadow-none focus-visible:ring-0" aria-label="Filter domains" />
      </div>
      <ScrollArea className="h-52">
        <div className="p-1">
          {visible.map((d) => (
            <label key={d.id} className="flex cursor-pointer items-center gap-3 rounded px-2 py-1.5 hover:bg-accent">
              <Checkbox checked={value.includes(d.id)} onCheckedChange={(c) => toggle(d.id, c === true)} />
              <span className="font-mono text-sm">{d.host}</span>
              {d.standard && <span className="ml-auto font-mono text-[11px] text-muted-foreground">standard</span>}
            </label>
          ))}
          {visible.length === 0 && <p className="p-3 text-center text-sm text-muted-foreground">No domain</p>}
        </div>
      </ScrollArea>
      <p className="border-t px-3 py-1.5 font-mono text-[11px] text-muted-foreground">{value.length} selected</p>
    </div>
  )
}

function CreateAccountDialog({ open, onOpenChange, domains }: { open: boolean; onOpenChange: (open: boolean) => void; domains: Domain[] }) {
  const queryClient = useQueryClient()
  const [name, setName] = useState("")
  const [global, setGlobal] = useState(false)
  const [domainIds, setDomainIds] = useState<string[]>([])
  const create = useMutation({
    mutationFn: () => api<Account>("/api/admin/accounts", { method: "POST", body: { name: name.trim(), global, domainIds: global ? [] : domainIds } }),
    onSuccess: (account) => {
      queryClient.invalidateQueries({ queryKey: ["accounts"] })
      toast.success(`${account.name} created`, { description: "Copy its keys from the table." })
      onOpenChange(false)
    },
    onError: (e) => toast.error(e.message),
  })
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>New account</DialogTitle>
          <DialogDescription>Creates a read-write key and a read-only key.</DialogDescription>
        </DialogHeader>
        <form
          id="account-form"
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault()
            create.mutate()
          }}
        >
          <FormField label="Name" htmlFor="account-name">
            <Input id="account-name" required value={name} onChange={(e) => setName(e.target.value)} placeholder="marketing-site" />
          </FormField>
          <label className="flex items-center justify-between gap-4 rounded-md border p-3">
            <span>
              <span className="block text-sm font-medium">Admin</span>
              <span className="block text-xs text-muted-foreground">Manages every domain, key and archive.</span>
            </span>
            <Switch checked={global} onCheckedChange={setGlobal} aria-label="Admin" />
          </label>
          {!global && (
            <FormField label="Domains">
              <DomainPicker domains={domains} value={domainIds} onChange={setDomainIds} />
            </FormField>
          )}
        </form>
        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button type="submit" form="account-form" disabled={create.isPending || !name.trim()}>
            {create.isPending && <Loader2 className="animate-spin" />}
            Create
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function RenameDialog({ account, onOpenChange }: { account?: Account; onOpenChange: (open: boolean) => void }) {
  const queryClient = useQueryClient()
  const [name, setName] = useState(account?.name ?? "")
  const rename = useMutation({
    mutationFn: () => api<Account>(`/api/admin/accounts/${account!.id}`, { method: "PATCH", body: { name: name.trim() } }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["accounts"] })
      toast.success("Account renamed")
      onOpenChange(false)
    },
    onError: (e) => toast.error(e.message),
  })
  return (
    <Dialog open={!!account} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Rename account</DialogTitle>
          <DialogDescription>Its keys do not change.</DialogDescription>
        </DialogHeader>
        <form
          id="rename-form"
          onSubmit={(e) => {
            e.preventDefault()
            rename.mutate()
          }}
        >
          <FormField label="Name" htmlFor="rename">
            <Input id="rename" required value={name} onChange={(e) => setName(e.target.value)} />
          </FormField>
        </form>
        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button type="submit" form="rename-form" disabled={rename.isPending || !name.trim()}>
            {rename.isPending && <Loader2 className="animate-spin" />}
            Save
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function GrantsDialog({ account, domains, onOpenChange }: { account?: Account; domains: Domain[]; onOpenChange: (open: boolean) => void }) {
  const queryClient = useQueryClient()
  const [domainIds, setDomainIds] = useState<string[]>(account?.domainIds ?? [])
  const ownedHosts = domains.filter((d) => d.ownerId === account?.id)
  const save = useMutation({
    mutationFn: () => api<Account>(`/api/admin/accounts/${account!.id}/domains`, { method: "PUT", body: { domainIds } }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["accounts"] })
      toast.success("Domains updated")
      onOpenChange(false)
    },
    onError: (e) => toast.error(e.message),
  })
  return (
    <Dialog open={!!account} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Domains of {account?.name}</DialogTitle>
          <DialogDescription>
            Its read-write key manages every link of these domains, whoever created them.
            {ownedHosts.length > 0 && ` It also owns ${ownedHosts.map((d) => d.host).join(", ")}.`}
          </DialogDescription>
        </DialogHeader>
        <DomainPicker domains={domains.filter((d) => d.ownerId !== account?.id)} value={domainIds} onChange={setDomainIds} />
        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={() => save.mutate()} disabled={save.isPending}>
            {save.isPending && <Loader2 className="animate-spin" />}
            Save
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
