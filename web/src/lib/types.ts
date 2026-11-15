/** Shapes of the Lika API (see /v3/api-docs). */

export type Domain = {
  id: string
  standard: boolean
  host: string
  apiBaseUrl: string
  landingBaseUrl: string | null
}

/** Each role includes the previous ones. Keys are viewers or editors only. */
export type DomainRole = "viewer" | "editor" | "manager"

/** The `lika` claim of a user token */
export type LikaClaims = {
  v: number
  admin: boolean
  domains: Record<string, DomainRole>
}

export type UserStatus = "invited" | "active" | "disabled"

export type User = {
  id: string
  email: string
  name: string
  status: UserStatus
  admin: boolean
  domains: Record<string, DomainRole>
  version: number
  hasPassword: boolean
  externalId: string | null
  invitedBy: string | null
  createdAt: string
  lastLoginAt: string | null
}

export type AuthProvider = "local" | "firebase" | "keycloak"

/** Single sign-on with the identity provider (authorization code with PKCE) */
export type SsoConfig = {
  label: string
  authorizationEndpoint: string
  clientId: string
  scope: string
  params: Record<string, string>
}

export type AuthConfig = {
  provider: AuthProvider
  setupRequired: boolean
  mailEnabled: boolean
  /** Public URL of the MCP server, when the API knows its public URL */
  mcpUrl: string | null
  /** Email and password typed in the sign-in form of Lika */
  passwordSignIn: boolean
  sso: SsoConfig | null
  /** Where users manage their password, when Lika cannot */
  accountUrl: string | null
  /** How users who forgot their password get a new one */
  passwordReset: "email" | "admin" | "provider"
}

export type InvitationInfo = { email: string; name: string; accountExists: boolean }

export type AuthTokens = { accessToken: string; expiresIn: number; refreshToken: string; user: User }

/** @param apiKey the user API key of the user, for the MCP server */
export type Me = { user: User; claims: LikaClaims; provider: AuthProvider; apiKey: ApiKey | null }

/** Invitation or password reset link, to pass on by hand when no email was sent */
export type UserLink = { user: User; link: string; emailSent: boolean; expiresAt: string }

export type ApiKey = {
  id: string
  name: string
  admin: boolean
  domains: Record<string, DomainRole>
  prefix: string
  createdBy: string | null
  createdAt: string
  expiresAt: string | null
  lastUsedAt: string | null
  /** Set on user API keys only */
  userId: string | null
}

/** The secret is only returned on creation and regeneration */
export type ApiKeySecret = { key: ApiKey; secret: string }

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
  users: ImportCounts
  apiKeys: ImportCounts
  domains: ImportCounts
  links: ImportCounts
}
