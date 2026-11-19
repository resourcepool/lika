"use client"

import { Globe } from "lucide-react"
import { Select, SelectContent, SelectItem, SelectSeparator, SelectTrigger, SelectValue } from "@/components/ui/select"
import { ALL_DOMAINS } from "@/hooks/use-domain-selection"
import type { Domain } from "@/lib/types"

export { ALL_DOMAINS }

export function DomainSwitcher({ domains, value, onChange }: { domains: Domain[]; value: string; onChange: (value: string) => void }) {
  return (
    <Select value={value} onValueChange={onChange}>
      <SelectTrigger className="w-56 font-mono text-xs" aria-label="Domain">
        <Globe className="text-muted-foreground" />
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value={ALL_DOMAINS}>All domains</SelectItem>
        {domains.length > 0 && <SelectSeparator />}
        {domains.map((d) => (
          <SelectItem key={d.id} value={d.id} className="font-mono text-xs">
            {d.host}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}
