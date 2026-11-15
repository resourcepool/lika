import "server-only"
import { connection } from "next/server"
import { likaJson } from "@/lib/lika-api"
import { sessionFromTokens, type Session } from "@/lib/session"
import type { AuthConfig, AuthTokens } from "@/lib/types"

/** Renew the access token this long before it expires */
const EXPIRY_MARGIN_MS = 30_000

/**
 * Refresh tokens are single-use. Requests sent together by the browser carry the same one:
 * they share a single renewal, remembered for a while for late comers.
 */
const renewals = new Map<string, { at: number; result: Promise<Session | null> }>()
const RENEWAL_MEMORY_MS = 60_000

export function needsRenewal(session: Session, now = Date.now()): boolean {
  return session.expiresAt - EXPIRY_MARGIN_MS <= now
}

/** @return the renewed session, or null when the session is over (revoked, expired, user disabled) */
export function renewSession(session: Session): Promise<Session | null> {
  const now = Date.now()
  for (const [token, renewal] of renewals) {
    if (now - renewal.at > RENEWAL_MEMORY_MS) renewals.delete(token)
  }
  const pending = renewals.get(session.refreshToken)
  if (pending) {
    return pending.result
  }
  const result = likaJson<AuthTokens>("/api/auth/refresh", { body: { refreshToken: session.refreshToken } })
    .then((r) => (r.ok ? sessionFromTokens(r.data) : null))
    .catch((e) => {
      renewals.delete(session.refreshToken)
      throw e
    })
  renewals.set(session.refreshToken, { at: now, result })
  return result
}

/** Read at request time: whether setup is required changes while the server runs */
export async function authConfig(): Promise<AuthConfig | null> {
  await connection()
  try {
    const r = await likaJson<AuthConfig>("/api/auth/config")
    return r.ok ? r.data : null
  } catch {
    return null
  }
}

/** Same rule as the API */
export const PASSWORD_MIN_LENGTH = 10
