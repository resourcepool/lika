"use client"

import { useState } from "react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { CalendarClock, Loader2, Tag, Trash2, X, XCircle } from "lucide-react"
import { toast } from "sonner"
import { api } from "@/lib/client"
import { DEFAULT_EXPIRY_PRESET, expiryDateOf, type ExpiryChoice } from "@/lib/expiry"
import { MAX_BATCH, metadataKeysOf, rowsToMetadata } from "@/lib/link-batch"
import type { Link, LinkBatchRequest, LinkBatchResult } from "@/lib/types"
import { ConfirmDialog } from "@/components/app/confirm-dialog"
import { ExpiryPicker } from "@/components/links/expiry-picker"
import { MetadataEditor, type MetadataRow } from "@/components/links/metadata-editor"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Label } from "@/components/ui/label"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"

type Props = {
  selected: Link[]
  onClear: () => void
  /** Links changed by an operation, to refresh the selection */
  onUpdated: (links: Link[]) => void
}

type Open = "metadata" | "remove" | "expiry" | "delete" | null

const plural = (n: number) => `${n} link${n > 1 ? "s" : ""}`

/**
 * Operations on the selected links: one request, all links or none.
 */
export function LinkBatchBar({ selected, onClear, onUpdated }: Props) {
  const queryClient = useQueryClient()
  const [open, setOpen] = useState<Open>(null)
  const [rows, setRows] = useState<MetadataRow[]>([{ key: "", value: "" }])
  const [removedKeys, setRemovedKeys] = useState<string[]>([])
  const [expiry, setExpiry] = useState<ExpiryChoice>({ preset: DEFAULT_EXPIRY_PRESET, date: "" })
  const expiryDate = expiryDateOf(expiry)
  const tooMany = selected.length > MAX_BATCH
  const keys = metadataKeysOf(selected)

  const run = useMutation({
    mutationFn: (req: Omit<LinkBatchRequest, "ids">) =>
      api<LinkBatchResult>("/api/links/batch", { method: "POST", body: { ...req, ids: selected.map((l) => l.id) } }),
    onSuccess: (result, req) => {
      queryClient.invalidateQueries({ queryKey: ["links"] })
      queryClient.invalidateQueries({ queryKey: ["link-metadata"] })
      setOpen(null)
      if (req.action === "delete") {
        toast.success(`${plural(result.count)} deleted`)
        onClear()
      } else {
        toast.success(`${plural(result.count)} updated`)
        onUpdated(result.links)
      }
    },
    onError: (error) => toast.error(error.message),
  })

  function show(which: Open) {
    setRows([{ key: "", value: "" }])
    setRemovedKeys([])
    setExpiry({ preset: DEFAULT_EXPIRY_PRESET, date: "" })
    setOpen(which)
  }

  const metadata = rowsToMetadata(rows)

  return (
    <>
      <div className="flex flex-wrap items-center gap-2 rounded-lg border bg-muted/40 px-3 py-2 text-sm" role="toolbar" aria-label="Selected links">
        <span className="font-medium">{plural(selected.length)} selected</span>
        {tooMany && <span className="text-destructive">At most {MAX_BATCH} at once</span>}
        <div className="ml-auto flex flex-wrap items-center gap-1">
          <Button variant="ghost" size="sm" disabled={tooMany} onClick={() => show("metadata")}>
            <Tag /> Set metadata
          </Button>
          <Button variant="ghost" size="sm" disabled={tooMany || keys.length === 0} onClick={() => show("remove")}>
            <XCircle /> Remove metadata
          </Button>
          <Button variant="ghost" size="sm" disabled={tooMany} onClick={() => show("expiry")}>
            <CalendarClock /> Expiry
          </Button>
          <Button variant="ghost" size="sm" className="text-destructive hover:text-destructive" disabled={tooMany} onClick={() => show("delete")}>
            <Trash2 /> Delete
          </Button>
          <Button variant="ghost" size="icon" className="size-8" aria-label="Clear selection" onClick={onClear}>
            <X />
          </Button>
        </div>
      </div>

      <Dialog open={open === "metadata"} onOpenChange={(o) => !o && setOpen(null)}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>Set metadata</DialogTitle>
            <DialogDescription>Added to {plural(selected.length)}, replacing the values of these keys. Other entries are kept.</DialogDescription>
          </DialogHeader>
          <MetadataEditor rows={rows} onChange={setRows} />
          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(null)}>
              Cancel
            </Button>
            <Button disabled={run.isPending || Object.keys(metadata).length === 0} onClick={() => run.mutate({ action: "set_metadata", metadata })}>
              {run.isPending && <Loader2 className="animate-spin" />}
              Apply to {plural(selected.length)}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={open === "remove"} onOpenChange={(o) => !o && setOpen(null)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Remove metadata</DialogTitle>
            <DialogDescription>Keys to remove from the {plural(selected.length)} selected.</DialogDescription>
          </DialogHeader>
          <div className="max-h-64 space-y-2 overflow-y-auto">
            {keys.map((key) => (
              <Label key={key} className="flex items-center gap-2 font-mono text-xs font-normal">
                <Checkbox
                  checked={removedKeys.includes(key)}
                  onCheckedChange={(checked) => setRemovedKeys(checked ? [...removedKeys, key] : removedKeys.filter((k) => k !== key))}
                />
                {key}
                <span className="text-muted-foreground">({selected.filter((l) => key in (l.metadata ?? {})).length})</span>
              </Label>
            ))}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(null)}>
              Cancel
            </Button>
            <Button disabled={run.isPending || removedKeys.length === 0} onClick={() => run.mutate({ action: "remove_metadata", keys: removedKeys })}>
              {run.isPending && <Loader2 className="animate-spin" />}
              Remove
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={open === "expiry"} onOpenChange={(o) => !o && setOpen(null)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Expiry</DialogTitle>
            <DialogDescription>The {plural(selected.length)} stop working after their last day, and are deleted the next night.</DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="batch-expiry">Expires</Label>
            <ExpiryPicker id="batch-expiry" value={expiry} onChange={setExpiry} />
          </div>
          <DialogFooter className="sm:justify-between">
            <Button variant="ghost" disabled={run.isPending} onClick={() => run.mutate({ action: "set_expiry", expiryDate: null })}>
              Remove expiry
            </Button>
            <div className="flex gap-2">
              <Button variant="outline" onClick={() => setOpen(null)}>
                Cancel
              </Button>
              <Button disabled={run.isPending || !expiryDate} onClick={() => run.mutate({ action: "set_expiry", expiryDate })}>
                {run.isPending && <Loader2 className="animate-spin" />}
                Set expiry
              </Button>
            </div>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={open === "delete"}
        onOpenChange={(o) => !o && setOpen(null)}
        title={`Delete ${plural(selected.length)}?`}
        description="Their short URLs stop working immediately. This cannot be undone."
        confirmLabel="Delete"
        pending={run.isPending}
        onConfirm={() => run.mutate({ action: "delete" })}
      />
    </>
  )
}
