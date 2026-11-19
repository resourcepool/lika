import { format } from "date-fns"
import type { EngagementPoint, EngagementRange, EngagementResolution } from "@/lib/types"

export const ENGAGEMENT_RANGES: { id: EngagementRange; label: string; title: string }[] = [
  { id: "24h", label: "24 hours", title: "Last 24 hours, per hour" },
  { id: "7d", label: "7 days", title: "Last 7 days, per day" },
  { id: "30d", label: "30 days", title: "Last 30 days, per day" },
  { id: "90d", label: "13 weeks", title: "Last 13 weeks, per week" },
  { id: "12m", label: "12 months", title: "Last 52 weeks, per week" },
]

export const DEFAULT_RANGE: EngagementRange = "30d"

export function parseRange(value: string | null): EngagementRange {
  return ENGAGEMENT_RANGES.some((r) => r.id === value) ? (value as EngagementRange) : DEFAULT_RANGE
}

export const PERIOD_NAMES: Record<EngagementResolution, string> = { hour: "hour", day: "day", week: "week" }

/**
 * Hours are shown in local time. Days and weeks are those of the API, in UTC: their date is shown as is,
 * not shifted to the day before or after by the time zone of the browser.
 */
function periodDate(point: EngagementPoint, resolution: EngagementResolution): Date {
  return resolution === "hour" ? new Date(point.start) : new Date(`${point.start.slice(0, 10)}T00:00`)
}

/** Short label of a period, for the axis */
export function axisLabel(point: EngagementPoint, resolution: EngagementResolution): string {
  return format(periodDate(point, resolution), resolution === "hour" ? "HH:mm" : "MMM d")
}

/** Full label of a period, for tooltips and the table */
export function periodLabel(point: EngagementPoint, resolution: EngagementResolution): string {
  const date = periodDate(point, resolution)
  switch (resolution) {
    case "hour":
      return `${format(date, "EEE d MMM, HH:mm")} – ${format(new Date(date.getTime() + 3600_000), "HH:mm")}`
    case "day":
      return format(date, "EEEE, PP")
    case "week":
      return `Week of ${format(date, "PP")}`
  }
}

/** Indexes of the periods labelled on the axis: about `max` of them, evenly spread, ending on the last one */
export function labelledIndexes(count: number, max: number): Set<number> {
  const step = Math.max(1, Math.ceil(count / max))
  const indexes = new Set<number>()
  for (let i = count - 1; i >= 0; i -= step) {
    indexes.add(i)
  }
  return indexes
}

/**
 * Top of the value axis: a round number at or above `max`, with the ticks to draw (0 included).
 * Visits are whole: no tick falls between two integers.
 */
export function valueAxis(max: number): { top: number; ticks: number[] } {
  if (max <= 4) {
    const top = Math.max(max, 1)
    return { top, ticks: Array.from({ length: top + 1 }, (_, i) => i) }
  }
  const rough = max / 4
  const magnitude = 10 ** Math.floor(Math.log10(rough))
  const step = [1, 2, 2.5, 5, 10].map((m) => m * magnitude).find((s) => s >= rough && Number.isInteger(s)) ?? 10 * magnitude
  const top = Math.ceil(max / step) * step
  return { top, ticks: Array.from({ length: Math.round(top / step) + 1 }, (_, i) => i * step) }
}

const compact = new Intl.NumberFormat("en", { notation: "compact", maximumFractionDigits: 1 })
const full = new Intl.NumberFormat("en")

/** 1,284 in full, 12.9K beyond ten thousand */
export function formatCount(value: number): string {
  return value >= 10_000 ? compact.format(value) : full.format(value)
}

export function busiest(series: EngagementPoint[]): EngagementPoint | undefined {
  return series.reduce<EngagementPoint | undefined>((best, point) => (point.count > (best?.count ?? 0) ? point : best), undefined)
}
