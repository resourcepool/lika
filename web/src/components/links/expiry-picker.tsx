"use client"

import { format } from "date-fns"
import { EXPIRY_PRESETS, expiryDateOf, type ExpiryChoice, type ExpiryPreset } from "@/lib/expiry"
import { localDate } from "@/lib/link-status"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"

/**
 * When a link expires: usual delays, or "Custom date" for a date picked by hand.
 */
export function ExpiryPicker({ value, onChange, id }: { value: ExpiryChoice; onChange: (value: ExpiryChoice) => void; id: string }) {
  const lastDay = expiryDateOf(value)
  return (
    <div className="flex flex-wrap items-center gap-2">
      <Select value={value.preset} onValueChange={(preset) => onChange({ ...value, preset: preset as ExpiryPreset })}>
        <SelectTrigger id={id} className="w-44" aria-label="Expires">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {EXPIRY_PRESETS.map((preset) => (
            <SelectItem key={preset.id} value={preset.id}>
              {preset.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      {value.preset === "custom" ? (
        <Input
          type="date"
          className="w-44 font-mono text-sm"
          aria-label="Last day"
          min={localDate(new Date())}
          value={value.date}
          onChange={(e) => onChange({ ...value, date: e.target.value })}
        />
      ) : (
        <p className="font-mono text-xs text-muted-foreground">last day {format(new Date(`${lastDay}T00:00`), "PP")}</p>
      )}
    </div>
  )
}
