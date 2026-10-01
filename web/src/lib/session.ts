import "server-only"
import { EncryptJWT, jwtDecrypt } from "jose"
import { cookies } from "next/headers"
import { sessionSecret } from "@/lib/env"
import { SESSION_COOKIE, SESSION_MAX_AGE } from "@/lib/session-cookie"

export type Session = {
  /** Read-write API key of the account */
  token: string
  admin: boolean
  accountId?: string
  accountName?: string
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

export async function encryptSession(session: Session): Promise<string> {
  return new EncryptJWT({ ...session })
    .setProtectedHeader({ alg: "dir", enc: "A256GCM" })
    .setIssuedAt()
    .encrypt(await key())
}

export async function decryptSession(value: string | undefined): Promise<Session | null> {
  if (!value) {
    return null
  }
  try {
    const { payload } = await jwtDecrypt(value, await key())
    if (typeof payload.token !== "string") {
      return null
    }
    return {
      token: payload.token,
      admin: payload.admin === true,
      accountId: typeof payload.accountId === "string" ? payload.accountId : undefined,
      accountName: typeof payload.accountName === "string" ? payload.accountName : undefined,
    }
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
