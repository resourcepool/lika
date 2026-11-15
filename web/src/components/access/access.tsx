"use client"

import { useMemo, useState } from "react"
import { AlertTriangle, Search, ShieldCheck } from "lucide-react"
import { ROLE_DESCRIPTIONS, ROLE_LABELS } from "@/lib/access"
import type { Domain, DomainRole } from "@/lib/types"
import { CopyButton } from "@/components/app/copy-button"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"

const NONE = "none"

/**
 * Role per domain, for the domains the signed-in user may grant.
 */
export function RolesEditor({
  domains,
  value,
  onChange,
  roles,
}: {
  domains: Domain[]
  value: Record<string, DomainRole>
  onChange: (value: Record<string, DomainRole>) => void
  roles: DomainRole[]
}) {
  const [filter, setFilter] = useState("")
  const visible = useMemo(() => domains.filter((d) => d.host.includes(filter.trim().toLowerCase())), [domains, filter])
  const set = (id: string, role: string) => {
    const next = { ...value }
    if (role === NONE) delete next[id]
    else next[id] = role as DomainRole
    onChange(next)
  }
  const count = Object.keys(value).filter((id) => domains.some((d) => d.id === id)).length
  return (
    <div className="rounded-md border">
      {domains.length > 6 && (
        <div className="relative border-b">
          <Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input value={filter} onChange={(e) => setFilter(e.target.value)} placeholder="Filter domains" className="border-0 pl-9 shadow-none focus-visible:ring-0" aria-label="Filter domains" />
        </div>
      )}
      <div className="max-h-60 overflow-y-auto p-1">
        {visible.map((d) => (
          <div key={d.id} className="flex items-center gap-3 rounded px-2 py-1 hover:bg-accent/50">
            <span className="min-w-0 flex-1 truncate font-mono text-sm">{d.host}</span>
            <Select value={value[d.id] ?? NONE} onValueChange={(v) => set(d.id, v)}>
              <SelectTrigger size="sm" className="w-32 text-xs" aria-label={`Role on ${d.host}`}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent align="end">
                <SelectItem value={NONE} className="text-muted-foreground">
                  No access
                </SelectItem>
                {roles.map((r) => (
                  <SelectItem key={r} value={r} title={ROLE_DESCRIPTIONS[r]}>
                    {ROLE_LABELS[r]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        ))}
        {visible.length === 0 && <p className="p-3 text-center text-sm text-muted-foreground">No domain</p>}
      </div>
      <p className="border-t px-3 py-1.5 font-mono text-[11px] text-muted-foreground">
        {count} domain{count === 1 ? "" : "s"} with access
      </p>
      <p className="border-t px-3 py-1.5 text-xs text-muted-foreground">
        {roles.map((r) => (
          <span key={r} className="mr-3 inline-block">
            <span className="text-foreground">{ROLE_LABELS[r]}</span> {ROLE_DESCRIPTIONS[r].toLowerCase()}
          </span>
        ))}
      </p>
    </div>
  )
}

/** Admin badge, or one badge per domain role */
export function AccessBadges({ admin, domains, hostOf }: { admin: boolean; domains: Record<string, DomainRole>; hostOf: (id: string) => string | undefined }) {
  if (admin) {
    return (
      <Badge className="gap-1 font-mono text-[11px]">
        <ShieldCheck className="size-3" /> admin · all domains
      </Badge>
    )
  }
  const entries = Object.entries(domains)
  if (entries.length === 0) {
    return <span className="text-xs text-muted-foreground">No access</span>
  }
  return (
    <div className="flex flex-wrap gap-1">
      {entries.map(([id, role]) => (
        <Badge key={id} variant={role === "manager" ? "secondary" : "outline"} className="font-mono text-[11px]">
          {hostOf(id) ?? id.slice(0, 8)}
          <span className="text-muted-foreground">· {role}</span>
        </Badge>
      ))}
    </div>
  )
}

/**
 * Shows a secret or a link that cannot be retrieved later.
 */
export function OneTimeDialog({
  open,
  onOpenChange,
  title,
  description,
  value,
  label,
  warning,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description: React.ReactNode
  value?: string
  label: string
  warning?: string
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription asChild>
            <div>{description}</div>
          </DialogDescription>
        </DialogHeader>
        <div className="flex items-center gap-1 rounded-md border bg-muted/40 p-2">
          <code className="min-w-0 flex-1 font-mono text-xs break-all" aria-label={label}>
            {value}
          </code>
          {value && <CopyButton value={value} label={`Copy ${label.toLowerCase()}`} />}
        </div>
        {warning && (
          <Alert>
            <AlertTriangle />
            <AlertDescription>{warning}</AlertDescription>
          </Alert>
        )}
        <DialogFooter>
          <Button onClick={() => onOpenChange(false)}>Done</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
