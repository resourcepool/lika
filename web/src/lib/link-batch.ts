import type { Link } from "@/lib/types"

/** A metadata entry links must have: `value` null matches any value. Matched whole, ignoring case. */
export type MetadataFilter = { key: string; value: string | null }

/** At most this many links per batch operation (the API's limit) */
export const MAX_BATCH = 500

/** Values of the repeated `meta` parameter of GET /api/links */
export function metadataParams(filters: MetadataFilter[]): string[] {
  return filters.map((f) => (f.value === null ? f.key : `${f.key}=${f.value}`))
}

export function sameFilter(a: MetadataFilter, b: MetadataFilter): boolean {
  return a.key.toLowerCase() === b.key.toLowerCase() && (a.value ?? "").toLowerCase() === (b.value ?? "").toLowerCase() && (a.value === null) === (b.value === null)
}

/** Adds a filter typed by the user, unless empty or already there. An empty value means any value. */
export function addFilter(filters: MetadataFilter[], key: string, value: string): MetadataFilter[] {
  const filter = { key: key.trim(), value: value.trim() === "" ? null : value.trim() }
  if (!filter.key || filters.some((f) => sameFilter(f, filter))) {
    return filters
  }
  return [...filters, filter]
}

/** Metadata keys of some links, without duplicates, sorted */
export function metadataKeysOf(links: Link[]): string[] {
  return [...new Set(links.flatMap((link) => Object.keys(link.metadata ?? {})))].sort((a, b) => a.localeCompare(b))
}

/** Rows of the metadata editor as a map: blank keys dropped, later rows win */
export function rowsToMetadata(rows: { key: string; value: string }[]): Record<string, string> {
  return Object.fromEntries(rows.filter((row) => row.key.trim() !== "").map((row) => [row.key.trim(), row.value]))
}
