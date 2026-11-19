"use client"

import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { format } from "date-fns"
import { Info, Trash2 } from "lucide-react"
import { toast } from "sonner"
import { api } from "@/lib/client"
import type { Domain, Link, LinkBatchResult, UnusedLinks } from "@/lib/types"
import { useSession } from "@/components/app/session-context"
import { ConfirmDialog } from "@/components/app/confirm-dialog"
import { EmptyState } from "@/components/app/empty-state"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Skeleton } from "@/components/ui/skeleton"

type Props = {
  onOpenChange: (open: boolean) => void
  domains: Domain[]
  /** Only the links of this domain, or of every domain when undefined */
  domainId?: string
}

const plural = (n: number) => `${n} link${n > 1 ? "s" : ""}`
const YEAR = 365 * 24 * 3600 * 1000

/**
 * Finds the links nobody visited for a year, to delete those no longer needed.
 */
export function LinkCleanupDialog({ onOpenChange, domains, domainId }: Props) {
  const session = useSession()
  const queryClient = useQueryClient()
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [confirming, setConfirming] = useState(false)
  const unused = useQuery({
    queryKey: ["links", "unused", domainId ?? "all"],
    queryFn: () => api<UnusedLinks>("/api/links/unused", { query: { domainId } }),
  })
  const links = unused.data?.links ?? []
  const editable = (link: Link) => {
    const domain = domains.find((d) => d.host === link.host)
    return !!domain && session.can(domain.id, "editor")
  }
  const deletable = links.filter(editable)
  const chosen = deletable.filter((l) => selected.has(l.id))
  const scope = domainId ? (domains.find((d) => d.id === domainId)?.host ?? "this domain") : "your domains"
  // Visits are counted since Lika 0.17: until a year has passed, "unused" only covers that time
  const partial = unused.data && unused.data.since === unused.data.trackedSince

  const remove = useMutation({
    mutationFn: () => api<LinkBatchResult>("/api/links/batch", { method: "POST", body: { action: "delete", ids: chosen.map((l) => l.id) } }),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ["links"] })
      queryClient.invalidateQueries({ queryKey: ["link-metadata"] })
      setConfirming(false)
      setSelected(new Set())
      toast.success(`${plural(result.count)} deleted`)
    },
    onError: (error) => toast.error(error.message),
  })

  function toggle(ids: string[], on: boolean) {
    setSelected((current) => {
      const next = new Set(current)
      ids.forEach((id) => (on ? next.add(id) : next.delete(id)))
      return next
    })
  }

  return (
    <>
      <Dialog open onOpenChange={onOpenChange}>
        <DialogContent className="flex max-h-[90vh] flex-col sm:max-w-3xl">
          <DialogHeader>
            <DialogTitle>Clean up unused links</DialogTitle>
            <DialogDescription>Links of {scope} created over a year ago which nobody visited for a year. Select the ones to delete.</DialogDescription>
          </DialogHeader>

          {partial && unused.data && (
            <Alert>
              <Info />
              <AlertDescription className="text-xs">
                Lika counts visits since {format(new Date(unused.data.trackedSince), "PP")} only: these links had no visit since then. A full year of history will be
                available on {format(new Date(new Date(unused.data.trackedSince).getTime() + YEAR), "PP")}.
              </AlertDescription>
            </Alert>
          )}

          <div className="min-h-0 flex-1 overflow-y-auto rounded-md border">
            {unused.isPending && (
              <div className="space-y-2 p-3">
                {Array.from({ length: 4 }).map((_, i) => (
                  <Skeleton key={i} className="h-8 w-full" />
                ))}
              </div>
            )}
            {unused.isError && <EmptyState title="Could not look for unused links" description={unused.error.message} />}
            {unused.isSuccess && links.length === 0 && <EmptyState title="Nothing to clean up" description="Every link over a year old was visited during the last year." />}
            {links.length > 0 && (
              <table className="w-full text-sm" aria-label="Unused links">
                <thead className="sticky top-0 bg-muted text-left font-mono text-[11px] tracking-wider text-muted-foreground uppercase">
                  <tr>
                    <th className="w-10 p-2 pl-3">
                      <Checkbox
                        aria-label="Select every unused link"
                        disabled={deletable.length === 0}
                        checked={chosen.length > 0 && chosen.length === deletable.length ? true : chosen.length > 0 ? "indeterminate" : false}
                        onCheckedChange={() => toggle(deletable.map((l) => l.id), chosen.length < deletable.length)}
                      />
                    </th>
                    <th className="p-2 font-normal">Short link</th>
                    <th className="p-2 font-normal">Destination</th>
                    <th className="p-2 pr-3 font-normal whitespace-nowrap">Created</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {links.map((link) => (
                    <tr key={link.id} data-state={selected.has(link.id) ? "selected" : undefined} className="data-[state=selected]:bg-muted/50">
                      <td className="p-2 pl-3">
                        <Checkbox
                          aria-label={`Select /${link.slug}`}
                          disabled={!editable(link)}
                          title={editable(link) ? undefined : "You cannot edit the links of this domain"}
                          checked={selected.has(link.id)}
                          onCheckedChange={(on) => toggle([link.id], on === true)}
                        />
                      </td>
                      <td className="max-w-48 p-2">
                        <div className="truncate font-mono text-sm font-medium">/{link.slug}</div>
                        <div className="truncate font-mono text-xs text-muted-foreground">{link.host}</div>
                      </td>
                      <td className="max-w-64 p-2">
                        <a href={link.targetUrl} target="_blank" rel="noreferrer" className="block truncate font-mono text-xs text-muted-foreground hover:text-foreground">
                          {link.targetUrl}
                        </a>
                      </td>
                      <td className="p-2 pr-3 font-mono text-xs whitespace-nowrap text-muted-foreground">{format(new Date(link.createdDateTime), "PP")}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>

          <DialogFooter className="items-center sm:justify-between">
            <p className="text-xs text-muted-foreground">
              {unused.data && unused.data.total > links.length
                ? `The ${links.length} oldest of ${unused.data.total} unused links: delete some to see the next ones.`
                : unused.data && links.length > 0
                  ? `${plural(unused.data.total)} unused`
                  : ""}
            </p>
            <div className="flex gap-2">
              <Button variant="ghost" onClick={() => onOpenChange(false)}>
                Done
              </Button>
              <Button variant="destructive" disabled={chosen.length === 0} onClick={() => setConfirming(true)}>
                <Trash2 />
                {chosen.length ? `Delete ${plural(chosen.length)}` : "Delete"}
              </Button>
            </div>
          </DialogFooter>
        </DialogContent>
      </Dialog>
      <ConfirmDialog
        open={confirming}
        onOpenChange={setConfirming}
        title={`Delete ${plural(chosen.length)}?`}
        description="Their short URLs stop working immediately. This cannot be undone."
        confirmLabel="Delete"
        pending={remove.isPending}
        onConfirm={() => remove.mutate()}
      />
    </>
  )
}
