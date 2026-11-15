"use client"

import { useRef, useState } from "react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { AlertTriangle, Download, FileJson, Loader2, Upload } from "lucide-react"
import { toast } from "sonner"
import { api } from "@/lib/client"
import { localDate } from "@/lib/link-status"
import type { ImportReport } from "@/lib/types"
import { PageHeader } from "@/components/app/page-header"
import { ConfirmDialog } from "@/components/app/confirm-dialog"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import { Label } from "@/components/ui/label"
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"

type Conflict = "skip" | "overwrite"
type Archive = { accounts?: unknown[]; users?: unknown[]; apiKeys?: unknown[]; domains?: unknown[]; version?: number }

export function ArchiveView() {
  return (
    <>
      <PageHeader title="Import / Export" description="Move users, API keys, domains and links between Lika instances, or back them up." />
      <div className="grid gap-4 p-4 md:p-6 xl:grid-cols-2">
        <ExportCard />
        <ImportCard />
      </div>
    </>
  )
}

function ExportCard() {
  const [confirming, setConfirming] = useState(false)
  const exportArchive = useMutation({
    mutationFn: () => api<unknown>("/api/admin/archive/export", { method: "POST" }),
    onSuccess: (archive) => {
      const url = URL.createObjectURL(new Blob([JSON.stringify(archive, null, 2)], { type: "application/json" }))
      const a = document.createElement("a")
      a.href = url
      a.download = `lika-archive-${localDate(new Date())}.json`
      a.click()
      URL.revokeObjectURL(url)
      setConfirming(false)
      toast.success("Archive downloaded")
    },
    onError: (e) => toast.error(e.message),
  })
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Download className="size-4 text-brand" /> Export
        </CardTitle>
        <CardDescription>Every account with its keys and domain grants, every domain, every link with its metadata and password hash.</CardDescription>
      </CardHeader>
      <CardFooter>
        <Button onClick={() => setConfirming(true)}>
          <Download />
          Export archive
        </Button>
      </CardFooter>
      <ConfirmDialog
        open={confirming}
        onOpenChange={setConfirming}
        title="Export contains API keys"
        description="The file holds every API key in clear text, except the root key from the configuration. Anyone with the file can use them: store it like a password."
        confirmLabel="Download"
        destructive={false}
        pending={exportArchive.isPending}
        onConfirm={() => exportArchive.mutate()}
      />
    </Card>
  )
}

function ImportCard() {
  const queryClient = useQueryClient()
  const input = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<{ name: string; archive: Archive }>()
  const [conflict, setConflict] = useState<Conflict>("skip")
  const [preview, setPreview] = useState<ImportReport>()
  const [result, setResult] = useState<ImportReport>()

  const run = useMutation({
    mutationFn: (dryRun: boolean) => api<ImportReport>("/api/admin/archive/import", { method: "POST", body: file!.archive, query: { dryRun, conflict } }),
    onSuccess: (report) => {
      if (report.dryRun) {
        setPreview(report)
      } else {
        setResult(report)
        setPreview(undefined)
        queryClient.invalidateQueries()
        toast.success("Archive imported")
      }
    },
    onError: (e) => toast.error(e.message),
  })

  async function pick(f: File | undefined) {
    setPreview(undefined)
    setResult(undefined)
    if (!f) return
    try {
      const archive = JSON.parse(await f.text()) as Archive
      if (!archive || (!Array.isArray(archive.accounts) && !Array.isArray(archive.domains))) {
        throw new Error("not a Lika archive")
      }
      setFile({ name: f.name, archive })
    } catch (e) {
      setFile(undefined)
      toast.error(`Cannot read ${f.name}`, { description: e instanceof Error ? e.message : undefined })
    }
  }

  function changeConflict(value: Conflict) {
    setConflict(value)
    setPreview(undefined)
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Upload className="size-4 text-brand" /> Import
        </CardTitle>
        <CardDescription>
          Users are matched by email, API keys by name, domains by host, links by host and slug. Accounts of archives before 0.7 become API keys. Reads archives from Lika 0.4 and later. A preview runs first: nothing is written until you
          confirm.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-5">
        <input ref={input} type="file" accept="application/json,.json" className="hidden" onChange={(e) => pick(e.target.files?.[0])} aria-label="Archive file" />
        <button
          type="button"
          onClick={() => input.current?.click()}
          className="flex w-full items-center gap-3 rounded-lg border border-dashed p-4 text-left transition-colors hover:border-brand"
        >
          <FileJson className="size-8 shrink-0 text-muted-foreground" />
          <span className="min-w-0">
            <span className="block truncate text-sm font-medium">{file?.name ?? "Choose an archive"}</span>
            <span className="block font-mono text-xs text-muted-foreground">
              {file ? archiveSummary(file.archive) : ".json exported from Lika"}
            </span>
          </span>
        </button>

        <div className="space-y-2">
          <Label>When an item already exists</Label>
          <RadioGroup value={conflict} onValueChange={(v) => changeConflict(v as Conflict)} className="grid gap-2 sm:grid-cols-2">
            <ConflictOption value="skip" title="Skip it" description="Keep what is here." />
            <ConflictOption value="overwrite" title="Overwrite it" description="Replace it with the archive." />
          </RadioGroup>
        </div>

        {(preview ?? result) && <ReportTable report={(preview ?? result)!} />}
        {conflict === "overwrite" && preview && (
          <Alert variant="destructive">
            <AlertTriangle />
            <AlertTitle>Overwriting replaces existing data</AlertTitle>
            <AlertDescription>Existing links, domains and keys listed as updated will match the archive.</AlertDescription>
          </Alert>
        )}
      </CardContent>
      <CardFooter className="gap-2">
        {!preview ? (
          <Button onClick={() => run.mutate(true)} disabled={!file || run.isPending} variant={result ? "outline" : "default"}>
            {run.isPending && <Loader2 className="animate-spin" />}
            Preview import
          </Button>
        ) : (
          <>
            <Button onClick={() => run.mutate(false)} disabled={run.isPending} variant={conflict === "overwrite" ? "destructive" : "default"}>
              {run.isPending && <Loader2 className="animate-spin" />}
              Import
            </Button>
            <Button variant="ghost" onClick={() => setPreview(undefined)}>
              Cancel
            </Button>
          </>
        )}
      </CardFooter>
    </Card>
  )
}

function ConflictOption({ value, title, description }: { value: Conflict; title: string; description: string }) {
  return (
    <Label className="flex cursor-pointer items-start gap-3 rounded-md border p-3 font-normal has-[[data-state=checked]]:border-brand">
      <RadioGroupItem value={value} className="mt-0.5" />
      <span>
        <span className="block text-sm font-medium">{title}</span>
        <span className="block text-xs text-muted-foreground">{description}</span>
      </span>
    </Label>
  )
}

function ReportTable({ report }: { report: ImportReport }) {
  const rows = [
    ["Users", report.users],
    ["API keys", report.apiKeys],
    ["Domains", report.domains],
    ["Links", report.links],
  ] as const
  return (
    <div className="space-y-2">
      <p className="font-mono text-xs text-muted-foreground">{report.dryRun ? "Preview — nothing written yet" : "Imported"}</p>
      <div className="overflow-hidden rounded-md border">
        <Table>
          <TableHeader className="bg-muted/40">
            <TableRow className="hover:bg-transparent">
              {["", "Created", "Updated", "Skipped"].map((h) => (
                <TableHead key={h} className="font-mono text-[11px] tracking-wider uppercase">
                  {h}
                </TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map(([label, counts]) => (
              <TableRow key={label}>
                <TableCell className="font-medium">{label}</TableCell>
                <TableCell className="font-mono text-brand">{counts.created}</TableCell>
                <TableCell className="font-mono">{counts.updated}</TableCell>
                <TableCell className="font-mono text-muted-foreground">{counts.skipped}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </div>
  )
}

function archiveSummary(archive: Archive): string {
  const version = archive.version ?? 1
  const domains = `${archive.domains?.length ?? 0} domains`
  if (version < 3) return `v${version} · ${archive.accounts?.length ?? 0} accounts · ${domains}`
  return `v${version} · ${archive.users?.length ?? 0} users · ${archive.apiKeys?.length ?? 0} keys · ${domains}`
}
