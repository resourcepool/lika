import "server-only"
import { EncryptJWT, jwtDecrypt } from "jose"
import { cookies } from "next/headers"
import { sessionSecret } from "@/lib/env"
import { SESSION_COOKIE, SESSION_MAX_AGE } from "@/lib/session-cookie"
import type { AuthTokens, LikaClaims } from "@/lib/types"

export type SessionUser = { id: string; email: string; name: string }

export type Session = {
  accessToken: string
  refreshToken: string
  /** Epoch milliseconds */
  expiresAt: number
  user: SessionUser
  /** Rights when the access token was issued. The API enforces the current ones. */
  claims: LikaClaims
}

let cachedKey: { secret: string; key: Uint8Array } | undefined

async function key(): Promise<Uint8Array> {
  const secret = sessionSecret()
  if (cachedKey?.secret !== secret) {
    const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(secret))
    cachedKey = { secret, key: new Uint8Array(digest) }
  }
  return cachedKey.key
}

export function sessionFromTokens(tokens: AuthTokens, now = Date.now()): Session {
  const { user } = tokens
  return {
    accessToken: tokens.accessToken,
    refreshToken: tokens.refreshToken,
    expiresAt: now + tokens.expiresIn * 1000,
    user: { id: user.id, email: user.email, name: user.name },
    claims: { v: user.version, admin: user.admin, domains: user.domains },
  }
}

export async function encryptSession(session: Session): Promise<string> {
  return encryptPayload(session)
}

/** Encrypted with the session secret: only this server reads it */
export async function encryptPayload(payload: object): Promise<string> {
  return new EncryptJWT({ ...payload })
    .setProtectedHeader({ alg: "dir", enc: "A256GCM" })
    .setIssuedAt()
    .encrypt(await key())
}

export async function decryptPayload(value: string | undefined): Promise<Record<string, unknown> | null> {
  if (!value) return null
  try {
    return (await jwtDecrypt(value, await key())).payload
  } catch {
    return null
  }
}

export async function decryptSession(value: string | undefined): Promise<Session | null> {
  if (!value) {
    return null
  }
  try {
    const { payload } = await jwtDecrypt(value, await key())
    const { accessToken, refreshToken, expiresAt, user, claims } = payload as Partial<Session>
    if (typeof accessToken !== "string" || typeof refreshToken !== "string" || typeof expiresAt !== "number" || !user?.id || !claims) {
      // Includes the API-key sessions of Lika 0.6
      return null
    }
    return { accessToken, refreshToken, expiresAt, user, claims }
  } catch {
    // Tampered cookie, or encrypted with another secret
    return null
  }
}

export async function getSession(): Promise<Session | null> {
  const store = await cookies()
  return decryptSession(store.get(SESSION_COOKIE)?.value)
}

export async function saveSession(session: Session, secure: boolean) {
  const store = await cookies()
  store.set(SESSION_COOKIE, await encryptSession(session), {
    httpOnly: true,
    sameSite: "lax",
    secure,
    path: "/",
    maxAge: SESSION_MAX_AGE,
  })
}

export async function clearSession() {
  const store = await cookies()
  store.delete(SESSION_COOKIE)
}
