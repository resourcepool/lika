"use client"

import { Plus, X } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"

export type MetadataRow = { key: string; value: string }

export function MetadataEditor({ rows, onChange }: { rows: MetadataRow[]; onChange: (rows: MetadataRow[]) => void }) {
  const update = (index: number, row: Partial<MetadataRow>) => onChange(rows.map((r, i) => (i === index ? { ...r, ...row } : r)))
  return (
    <div className="space-y-2">
      {rows.map((row, index) => (
        <div key={index} className="flex gap-2">
          <Input aria-label="Key" placeholder="key" className="font-mono text-xs" value={row.key} onChange={(e) => update(index, { key: e.target.value })} />
          <Input aria-label="Value" placeholder="value" className="font-mono text-xs" value={row.value} onChange={(e) => update(index, { value: e.target.value })} />
          <Button type="button" variant="ghost" size="icon" aria-label="Remove entry" onClick={() => onChange(rows.filter((_, i) => i !== index))}>
            <X />
          </Button>
        </div>
      ))}
      <Button type="button" variant="outline" size="sm" onClick={() => onChange([...rows, { key: "", value: "" }])}>
        <Plus />
        Add metadata
      </Button>
    </div>
  )
}

export function toMetadata(rows: MetadataRow[]): Record<string, string> {
  return Object.fromEntries(rows.filter((r) => r.key.trim()).map((r) => [r.key.trim(), r.value]))
}

export function toRows(metadata: Record<string, string> | undefined): MetadataRow[] {
  return Object.entries(metadata ?? {}).map(([key, value]) => ({ key, value }))
}
