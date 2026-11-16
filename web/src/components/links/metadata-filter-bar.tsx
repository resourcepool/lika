"use client"

import { useId, useState } from "react"
import { Plus, Tags, X } from "lucide-react"
import { addFilter, type MetadataFilter } from "@/lib/link-batch"
import type { MetadataKeyValues } from "@/lib/types"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"

type Props = {
  filters: MetadataFilter[]
  onChange: (filters: MetadataFilter[]) => void
  /** Keys and values in use, suggested while typing */
  known: MetadataKeyValues[]
}

/**
 * Metadata filters of the link list, as removable chips. Links must match all of them.
 */
export function MetadataFilterBar({ filters, onChange, known }: Props) {
  const [adding, setAdding] = useState(false)
  const [key, setKey] = useState("")
  const [value, setValue] = useState("")
  const keysId = useId()
  const valuesId = useId()
  const values = known.find((k) => k.key.toLowerCase() === key.trim().toLowerCase())?.values ?? []

  function submit(event: React.FormEvent) {
    event.preventDefault()
    if (!key.trim()) return
    onChange(addFilter(filters, key, value))
    setKey("")
    setValue("")
    setAdding(false)
  }

  return (
    <div className="flex flex-wrap items-center gap-2">
      {filters.map((filter, index) => (
        <span key={`${filter.key}=${filter.value}`} className="inline-flex h-8 items-center gap-1 rounded-md border bg-muted/40 pr-1 pl-2.5 font-mono text-xs">
          <span className="text-muted-foreground">{filter.key}</span>
          <span className="text-muted-foreground">=</span>
          <span>{filter.value ?? <em className="text-muted-foreground not-italic">any</em>}</span>
          <Button
            variant="ghost"
            size="icon"
            className="size-6"
            aria-label={`Remove filter ${filter.key}`}
            onClick={() => onChange(filters.filter((_, i) => i !== index))}
          >
            <X className="size-3" />
          </Button>
        </span>
      ))}

      {adding ? (
        <form onSubmit={submit} className="flex flex-wrap items-center gap-2">
          <Input
            autoFocus
            list={keysId}
            value={key}
            onChange={(e) => setKey(e.target.value)}
            onKeyDown={(e) => e.key === "Escape" && setAdding(false)}
            placeholder="key"
            aria-label="Metadata key"
            className="h-8 w-40 font-mono text-xs"
          />
          <datalist id={keysId}>
            {known.map((k) => (
              <option key={k.key} value={k.key}>{`${k.linkCount} link${k.linkCount > 1 ? "s" : ""}`}</option>
            ))}
          </datalist>
          <span className="font-mono text-xs text-muted-foreground">=</span>
          <Input
            list={valuesId}
            value={value}
            onChange={(e) => setValue(e.target.value)}
            onKeyDown={(e) => e.key === "Escape" && setAdding(false)}
            placeholder="any value"
            aria-label="Metadata value"
            className="h-8 w-40 font-mono text-xs"
          />
          <datalist id={valuesId}>
            {values.map((v) => (
              <option key={v} value={v} />
            ))}
          </datalist>
          <Button type="submit" size="sm" disabled={!key.trim()}>
            Add
          </Button>
          <Button type="button" variant="ghost" size="sm" onClick={() => setAdding(false)}>
            Cancel
          </Button>
        </form>
      ) : (
        <Button variant="outline" size="sm" onClick={() => setAdding(true)}>
          {filters.length ? <Plus /> : <Tags />}
          {filters.length ? "Filter" : "Filter by metadata"}
        </Button>
      )}

      {filters.length > 1 && !adding && (
        <Button variant="ghost" size="sm" className="text-muted-foreground" onClick={() => onChange([])}>
          Clear filters
        </Button>
      )}
    </div>
  )
}
