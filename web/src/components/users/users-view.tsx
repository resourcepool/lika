"use client"

import { useMemo, useState } from "react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { formatDistanceToNow } from "date-fns"
import { Ban, Bot, CheckCircle2, KeyRound, Loader2, LogOut, Mail, MoreHorizontal, Pencil, Shield, Trash2, UserPlus } from "lucide-react"
import { toast } from "sonner"
import { api } from "@/lib/client"
import { ROLES } from "@/lib/access"
import type { Domain, DomainRole, User, UserLink, UserStatus } from "@/lib/types"
import { useDomains, useUsers } from "@/hooks/use-lika"
import { useSession } from "@/components/app/session-context"
import { AccessBadges, OneTimeDialog, RolesEditor } from "@/components/access/access"
import { PageHeader } from "@/components/app/page-header"
import { ConfirmDialog } from "@/components/app/confirm-dialog"
import { EmptyState } from "@/components/app/empty-state"
import { FormField } from "@/components/app/form-field"
import { ALL_DOMAINS, DomainSwitcher } from "@/components/links/domain-switcher"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from "@/components/ui/dropdown-menu"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { Switch } from "@/components/ui/switch"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"

const ALL_STATUSES = "all"

type Confirmation = { user: User; action: "disable" | "enable" | "delete" | "revoke" | "revokeKey" }

/** Domains the signed-in user may grant roles on */
function useGrantableDomains(): Domain[] {
  const domains = useDomains()
  const session = useSession()
  return useMemo(
    () => (domains.data ?? []).filter((d) => session.admin || session.managedDomainIds.includes(d.id)),
    [domains.data, session.admin, session.managedDomainIds],
  )
}

export function UsersView() {
  const session = useSession()
  const users = useUsers()
  const domains = useDomains()
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<string>(ALL_STATUSES)
  const [domainId, setDomainId] = useState(ALL_DOMAINS)
  const [inviting, setInviting] = useState(false)
  const [editingAccess, setEditingAccess] = useState<User>()
  const [editingDetails, setEditingDetails] = useState<User>()
  const [confirming, setConfirming] = useState<Confirmation>()
  const [link, setLink] = useState<{ kind: "invitation" | "reset"; result: UserLink }>()

  const hostOf = (id: string) => domains.data?.find((d) => d.id === id)?.host
  const visible = (users.data ?? []).filter(
    (u) => (status === ALL_STATUSES || u.status === status) && (domainId === ALL_DOMAINS || u.admin || u.domains[domainId] !== undefined),
  )
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ["users"] })
    queryClient.invalidateQueries({ queryKey: ["me"] })
  }

  const sendLink = useMutation({
    mutationFn: ({ user, kind }: { user: User; kind: "invitation" | "reset" }) =>
      api<UserLink>(`/api/users/${user.id}/${kind === "invitation" ? "invitation" : "password-reset"}`, { method: "POST" }),
    onSuccess: (result, { kind }) => {
      refresh()
      setLink({ kind, result })
    },
    onError: (e) => toast.error(e.message),
  })

  const confirm = useMutation({
    mutationFn: ({ user, action }: Confirmation) =>
      action === "delete"
        ? api(`/api/users/${user.id}`, { method: "DELETE" })
        : action === "revokeKey"
          ? api(`/api/users/${user.id}/api-key`, { method: "DELETE" })
          : api(`/api/users/${user.id}/${action === "revoke" ? "sessions/revoke" : action}`, { method: "POST" }),
    onSuccess: (_, { user, action }) => {
      refresh()
      setConfirming(undefined)
      const done = {
        disable: "disabled",
        enable: "enabled",
        delete: user.status === "invited" ? "invitation withdrawn" : "deleted",
        revoke: "signed out everywhere",
        revokeKey: "user API key revoked",
      }
      toast.success(`${user.name}: ${done[action]}`)
    },
    onError: (e) => toast.error(e.message),
  })

  return (
    <>
      <PageHeader title="Users" description={session.admin ? "Everyone who signs in to Lika, and what they may do." : "Users of the domains you manage."}>
        <Button onClick={() => setInviting(true)}>
          <UserPlus />
          Invite user
        </Button>
      </PageHeader>
      <div className="space-y-4 p-4 md:p-6">
        <div className="flex flex-wrap items-center gap-2">
          <Select value={status} onValueChange={setStatus}>
            <SelectTrigger className="w-40 text-xs" aria-label="Status">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL_STATUSES}>All statuses</SelectItem>
              <SelectItem value="active">Active</SelectItem>
              <SelectItem value="invited">Invited</SelectItem>
              <SelectItem value="disabled">Disabled</SelectItem>
            </SelectContent>
          </Select>
          <DomainSwitcher domains={domains.data ?? []} value={domainId} onChange={setDomainId} />
        </div>
        <div className="overflow-hidden rounded-lg border">
          <Table>
            <TableHeader className="bg-muted/40">
              <TableRow className="hover:bg-transparent">
                {["User", "Access", "Status", "Last sign-in", ""].map((h, i) => (
                  <TableHead key={i} className="font-mono text-[11px] tracking-wider uppercase">
                    {h}
                  </TableHead>
                ))}
              </TableRow>
            </TableHeader>
            <TableBody>
              {users.isPending && (
                <TableRow>
                  <TableCell colSpan={5}>
                    <Skeleton className="h-8 w-full" />
                  </TableCell>
                </TableRow>
              )}
              {visible.map((user) => {
                const self = user.id === session.user.id
                const editable = session.admin || (!user.admin && !self)
                return (
                  <TableRow key={user.id}>
                    <TableCell>
                      <div className="flex min-w-0 flex-col">
                        <span className="flex items-center gap-2 font-medium">
                          {user.name}
                          {self && <span className="font-mono text-[11px] text-muted-foreground">(you)</span>}
                        </span>
                        <span className="truncate font-mono text-xs text-muted-foreground">{user.email}</span>
                      </div>
                    </TableCell>
                    <TableCell className="max-w-sm">
                      <AccessBadges admin={user.admin} domains={user.domains} hostOf={hostOf} />
                    </TableCell>
                    <TableCell>
                      <StatusBadge status={user.status} />
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {user.lastLoginAt ? formatDistanceToNow(new Date(user.lastLoginAt), { addSuffix: true }) : "Never"}
                    </TableCell>
                    <TableCell className="text-right">
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon" className="size-8" aria-label={`Actions for ${user.name}`}>
                            <MoreHorizontal />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          {editable && (
                            <DropdownMenuItem onSelect={() => setEditingAccess(user)}>
                              <Shield /> Edit access
                            </DropdownMenuItem>
                          )}
                          {user.status === "invited" && (session.admin || !user.admin) && (
                            <DropdownMenuItem onSelect={() => sendLink.mutate({ user, kind: "invitation" })}>
                              <Mail /> Resend invitation
                            </DropdownMenuItem>
                          )}
                          {session.admin && (
                            <>
                              <DropdownMenuItem onSelect={() => setEditingDetails(user)}>
                                <Pencil /> Edit name and email
                              </DropdownMenuItem>
                              {user.status === "active" && (
                                <DropdownMenuItem onSelect={() => sendLink.mutate({ user, kind: "reset" })}>
                                  <KeyRound /> Send password reset link
                                </DropdownMenuItem>
                              )}
                              {user.status === "active" && (
                                <DropdownMenuItem onSelect={() => setConfirming({ user, action: "revoke" })}>
                                  <LogOut /> Sign out everywhere
                                </DropdownMenuItem>
                              )}
                              <DropdownMenuItem onSelect={() => setConfirming({ user, action: "revokeKey" })}>
                                <Bot /> Revoke user API key
                              </DropdownMenuItem>
                              <DropdownMenuSeparator />
                              {user.status === "disabled" ? (
                                <DropdownMenuItem onSelect={() => setConfirming({ user, action: "enable" })}>
                                  <CheckCircle2 /> Enable
                                </DropdownMenuItem>
                              ) : (
                                <DropdownMenuItem onSelect={() => setConfirming({ user, action: "disable" })}>
                                  <Ban /> Disable
                                </DropdownMenuItem>
                              )}
                            </>
                          )}
                          {(session.admin || (user.status === "invited" && !user.admin)) && (
                            <DropdownMenuItem variant="destructive" onSelect={() => setConfirming({ user, action: "delete" })}>
                              <Trash2 /> {user.status === "invited" ? "Withdraw invitation" : "Delete"}
                            </DropdownMenuItem>
                          )}
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
          {users.isSuccess && visible.length === 0 && <EmptyState title="No user" description="Invite someone to manage short links with you." />}
        </div>
      </div>

      {inviting && (
        <InviteDialog
          onOpenChange={setInviting}
          onInvited={(result) => {
            refresh()
            setInviting(false)
            setLink({ kind: "invitation", result })
          }}
        />
      )}
      {editingAccess && <AccessDialog user={editingAccess} onOpenChange={(open) => !open && setEditingAccess(undefined)} onSaved={refresh} />}
      {editingDetails && <DetailsDialog user={editingDetails} onOpenChange={(open) => !open && setEditingDetails(undefined)} onSaved={refresh} />}
      <ConfirmDialog
        open={!!confirming}
        onOpenChange={(open) => !open && setConfirming(undefined)}
        title={confirmTitle(confirming)}
        description={confirmDescription(confirming)}
        confirmLabel={
          confirming
            ? { disable: "Disable", enable: "Enable", delete: confirming.user.status === "invited" ? "Withdraw" : "Delete", revoke: "Sign out", revokeKey: "Revoke" }[
                confirming.action
              ]
            : ""
        }
        destructive={confirming?.action !== "enable"}
        pending={confirm.isPending}
        onConfirm={() => confirming && confirm.mutate(confirming)}
      />
      <OneTimeDialog
        open={!!link}
        onOpenChange={(open) => !open && setLink(undefined)}
        title={link?.kind === "reset" ? "Password reset link" : "Invitation link"}
        label={link?.kind === "reset" ? "Reset link" : "Invitation link"}
        value={link?.result.link}
        description={
          link && (
            <>
              {link.result.emailSent ? (
                <>
                  Emailed to <strong className="text-foreground">{link.result.user.email}</strong>. You can also pass it on yourself.
                </>
              ) : (
                <>
                  No email was sent: pass this link on to <strong className="text-foreground">{link.result.user.email}</strong>.
                </>
              )}{" "}
              It works once and expires {formatDistanceToNow(new Date(link.result.expiresAt), { addSuffix: true })}.
            </>
          )
        }
        warning="Anyone with this link can sign in as this user. Previous links no longer work."
      />
    </>
  )
}

function StatusBadge({ status }: { status: UserStatus }) {
  if (status === "active") return <Badge variant="outline" className="font-mono text-[11px] text-brand">active</Badge>
  if (status === "invited") return <Badge variant="secondary" className="font-mono text-[11px]">invited</Badge>
  return <Badge variant="destructive" className="font-mono text-[11px]">disabled</Badge>
}

function confirmTitle(c?: Confirmation): string {
  if (!c) return ""
  const name = c.user.name
  return {
    disable: `Disable ${name}?`,
    enable: `Enable ${name}?`,
    delete: c.user.status === "invited" ? `Withdraw the invitation of ${name}?` : `Delete ${name}?`,
    revoke: `Sign ${name} out everywhere?`,
    revokeKey: `Revoke the user API key of ${name}?`,
  }[c.action]
}

function confirmDescription(c?: Confirmation): string {
  if (!c) return ""
  return {
    disable: "They are signed out immediately and cannot sign in until enabled again. Their links are kept.",
    enable: "They can sign in again with their password, with the access they had.",
    delete: c.user.status === "invited" ? "The invitation link stops working." : "They are signed out immediately and cannot sign in anymore. Their links are kept.",
    revoke: "Every browser they are signed in on is signed out. They can sign in again.",
    revokeKey: "AI assistants using it are disconnected immediately. They can create a new one in their profile.",
  }[c.action]
}

function InviteDialog({ onOpenChange, onInvited }: { onOpenChange: (open: boolean) => void; onInvited: (result: UserLink) => void }) {
  const session = useSession()
  const domains = useGrantableDomains()
  const [email, setEmail] = useState("")
  const [name, setName] = useState("")
  const [admin, setAdmin] = useState(false)
  const [roles, setRoles] = useState<Record<string, DomainRole>>({})
  const invite = useMutation({
    mutationFn: () => api<UserLink>("/api/users/invitations", { method: "POST", body: { email: email.trim(), name: name.trim(), admin, domains: admin ? {} : roles } }),
    onSuccess: onInvited,
    onError: (e) => toast.error(e.message),
  })
  const valid = email.includes("@") && (admin || Object.keys(roles).length > 0)
  return (
    <Dialog open onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Invite a user</DialogTitle>
          <DialogDescription>They receive a link to choose their password. Their access applies as soon as they sign in.</DialogDescription>
        </DialogHeader>
        <form
          id="invite-form"
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault()
            invite.mutate()
          }}
        >
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField label="Email" htmlFor="invite-email">
              <Input id="invite-email" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} placeholder="ada@example.com" />
            </FormField>
            <FormField label="Name" htmlFor="invite-name">
              <Input id="invite-name" value={name} onChange={(e) => setName(e.target.value)} placeholder="Ada Lovelace" />
            </FormField>
          </div>
          {session.admin && (
            <label className="flex items-center justify-between gap-4 rounded-md border p-3">
              <span>
                <span className="block text-sm font-medium">Admin</span>
                <span className="block text-xs text-muted-foreground">Every right: domains, users, keys and archives.</span>
              </span>
              <Switch checked={admin} onCheckedChange={setAdmin} aria-label="Admin" />
            </label>
          )}
          {!admin && (
            <FormField label="Access">
              <RolesEditor domains={domains} value={roles} onChange={setRoles} roles={ROLES} />
            </FormField>
          )}
        </form>
        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button type="submit" form="invite-form" disabled={invite.isPending || !valid}>
            {invite.isPending && <Loader2 className="animate-spin" />}
            Send invitation
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function AccessDialog({ user, onOpenChange, onSaved }: { user: User; onOpenChange: (open: boolean) => void; onSaved: () => void }) {
  const session = useSession()
  const domains = useGrantableDomains()
  const [admin, setAdmin] = useState(user.admin)
  const [roles, setRoles] = useState<Record<string, DomainRole>>(user.domains)
  const save = useMutation({
    mutationFn: () => {
      // Only the domains shown are sent: roles elsewhere stay as they are
      const changes: Record<string, DomainRole | null> = {}
      for (const d of domains) {
        if (roles[d.id] !== user.domains[d.id]) changes[d.id] = roles[d.id] ?? null
      }
      return api<User>(`/api/users/${user.id}/access`, { method: "PUT", body: { admin: session.admin ? admin : undefined, domains: changes } })
    },
    onSuccess: () => {
      onSaved()
      toast.success(`Access of ${user.name} updated`, { description: "It applies immediately." })
      onOpenChange(false)
    },
    onError: (e) => toast.error(e.message),
  })
  return (
    <Dialog open onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Access of {user.name}</DialogTitle>
          <DialogDescription>{session.admin ? "Applies immediately, even to their open sessions." : "You only change the domains you manage."}</DialogDescription>
        </DialogHeader>
        {session.admin && (
          <label className="flex items-center justify-between gap-4 rounded-md border p-3">
            <span>
              <span className="block text-sm font-medium">Admin</span>
              <span className="block text-xs text-muted-foreground">Every right: domains, users, keys and archives.</span>
            </span>
            <Switch checked={admin} onCheckedChange={setAdmin} aria-label="Admin" />
          </label>
        )}
        {!admin && <RolesEditor domains={domains} value={roles} onChange={setRoles} roles={ROLES} />}
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

function DetailsDialog({ user, onOpenChange, onSaved }: { user: User; onOpenChange: (open: boolean) => void; onSaved: () => void }) {
  const [name, setName] = useState(user.name)
  const [email, setEmail] = useState(user.email)
  const save = useMutation({
    mutationFn: () => api<User>(`/api/users/${user.id}`, { method: "PATCH", body: { name: name.trim(), email: email.trim() } }),
    onSuccess: () => {
      onSaved()
      toast.success("User updated")
      onOpenChange(false)
    },
    onError: (e) => toast.error(e.message),
  })
  return (
    <Dialog open onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Edit {user.name}</DialogTitle>
          <DialogDescription>They sign in with the new email from now on.</DialogDescription>
        </DialogHeader>
        <form
          id="details-form"
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault()
            save.mutate()
          }}
        >
          <FormField label="Name" htmlFor="details-name">
            <Input id="details-name" required value={name} onChange={(e) => setName(e.target.value)} />
          </FormField>
          <FormField label="Email" htmlFor="details-email">
            <Input id="details-email" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
          </FormField>
        </form>
        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button type="submit" form="details-form" disabled={save.isPending || !name.trim() || !email.includes("@")}>
            {save.isPending && <Loader2 className="animate-spin" />}
            Save
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
