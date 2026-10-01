/** Shapes of the Lika API (see /v3/api-docs). */

export type Domain = {
  id: string
  standard: boolean
  ownerId: string | null
  host: string
  apiBaseUrl: string
  landingBaseUrl: string | null
}

export type Account = {
  id: string
  name: string
  global: boolean
  defaultAccessToken: string
  defaultPublicReadAccessToken: string
  domainIds: string[]
}

export type LinkType = "SIMPLE" | "WITH_LANDING"

export type Link = {
  id: string
  host: string
  createdBy: string
  slug: string
  shortenedUrl: string
  targetUrl: string
  type: LinkType
  createdDateTime: string
  lastUpdateDateTime: string
  options: {
    activation: { activationInstant: string | null; targetUrlBeforeActivation: string | null } | null
    landing: { templateId: string | null } | null
    expiry: { expiryDate: string | null } | null
    password: { enabled: boolean } | null
  }
  metadata: Record<string, string>
}

export type LinkInput = {
  id?: string
  targetUrl: string
  domainId: string
  slug?: string
  type: LinkType
  options: {
    activation?: { activationInstant: string; targetUrlBeforeActivation?: string }
    expiry?: { expiryDate: string }
    landing?: { templateId: string }
    /** Omitted on edit: keeps the current password */
    password?: { password?: string; remove?: boolean }
  }
  metadata: Record<string, string>
}

export type Page<T> = {
  totalItemCount: number
  items: T[]
}

export type SortProperty = "creation_date" | "last_activity" | "slug"
export type SortDirection = "asc" | "desc"

export type ImportCounts = { created: number; updated: number; skipped: number }

export type ImportReport = {
  dryRun: boolean
  conflictMode: "skip" | "overwrite"
  accounts: ImportCounts
  domains: ImportCounts
  links: ImportCounts
}
