import { addDays, addMonths } from "date-fns"
import { localDate } from "@/lib/link-status"

/** When a link expires: a delay from today, or a date chosen by hand */
export type ExpiryPreset = "7d" | "30d" | "3m" | "custom"

export const EXPIRY_PRESETS: { id: ExpiryPreset; label: string }[] = [
  { id: "7d", label: "In 7 days" },
  { id: "30d", label: "In 30 days" },
  { id: "3m", label: "In 3 months" },
  { id: "custom", label: "Custom date" },
]

export const DEFAULT_EXPIRY_PRESET: ExpiryPreset = "7d"

export type ExpiryChoice = { preset: ExpiryPreset; date: string }

/**
 * Last day of the link (yyyy-MM-dd, local time) for a choice: counted from today for a delay,
 * the chosen date for "custom". Empty while no custom date is chosen.
 */
export function expiryDateOf(choice: ExpiryChoice, today: Date = new Date()): string {
  switch (choice.preset) {
    case "7d":
      return localDate(addDays(today, 7))
    case "30d":
      return localDate(addDays(today, 30))
    case "3m":
      return localDate(addMonths(today, 3))
    case "custom":
      return choice.date
  }
}
