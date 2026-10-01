import type { Link } from "@/lib/types"

export type LinkStatus = "active" | "scheduled" | "expired"

/**
 * Mirrors how the API resolves a link: expired after its expiry date,
 * scheduled before its activation instant.
 */
export function linkStatus(link: Pick<Link, "options">, now: Date = new Date()): LinkStatus {
  const expiryDate = link.options.expiry?.expiryDate
  if (expiryDate && localDate(now) > expiryDate) {
    return "expired"
  }
  const activation = link.options.activation?.activationInstant
  if (activation && now < new Date(activation)) {
    return "scheduled"
  }
  return "active"
}

/** yyyy-MM-dd in local time, comparable as a string */
export function localDate(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0")
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/** Value for a datetime-local input, from an ISO instant */
export function toDateTimeLocal(iso: string | null | undefined): string {
  if (!iso) {
    return ""
  }
  const date = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, "0")
  return `${localDate(date)}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** ISO instant with offset, from a datetime-local input value */
export function fromDateTimeLocal(value: string): string {
  return new Date(value).toISOString()
}
