import { beforeEach, describe, expect, it, vi } from "vitest"
import type { AuthTokens } from "@/lib/types"

vi.mock("next/headers", () => ({ cookies: vi.fn() }))

const tokens: AuthTokens = {
  accessToken: "header.payload.signature",
  refreshToken: "refresh-secret",
  expiresIn: 900,
  user: {
    id: "u1",
    email: "ada@example.com",
    name: "Ada",
    status: "active",
    admin: false,
    domains: { d1: "editor" },
    version: 3,
    hasPassword: true,
    externalId: null,
    invitedBy: null,
    createdAt: "2026-01-01T00:00:00Z",
    lastLoginAt: null,
  },
}

describe("session", () => {
  beforeEach(() => {
    vi.resetModules()
    vi.stubEnv("LIKA_SESSION_SECRET", "a".repeat(40))
  })

  it("is built from the tokens of the API, with the claims of the user", async () => {
    const { sessionFromTokens } = await import("@/lib/session")
    const session = sessionFromTokens(tokens, 1_000)
    expect(session.expiresAt).toBe(901_000)
    expect(session.claims).toEqual({ v: 3, admin: false, domains: { d1: "editor" } })
    expect(session.user).toEqual({ id: "u1", email: "ada@example.com", name: "Ada" })
  })

  it("round-trips encrypted, the tokens not being readable from the cookie", async () => {
    const { encryptSession, decryptSession, sessionFromTokens } = await import("@/lib/session")
    const session = sessionFromTokens(tokens)
    const value = await encryptSession(session)
    expect(value).not.toContain("refresh-secret")
    expect(atob(value.split(".")[0])).not.toContain("refresh-secret")
    expect(await decryptSession(value)).toEqual(session)
  })

  it("rejects cookies encrypted with another secret, tampered with, or from Lika 0.6", async () => {
    const { encryptSession, decryptSession, sessionFromTokens } = await import("@/lib/session")
    const value = await encryptSession(sessionFromTokens(tokens))
    expect(await decryptSession(value.slice(0, -2) + "xx")).toBeNull()
    // API-key session of 0.6
    expect(await decryptSession(await encryptSession({ token: "key", admin: true } as never))).toBeNull()
    vi.stubEnv("LIKA_SESSION_SECRET", "b".repeat(40))
    expect(await decryptSession(value)).toBeNull()
  })

  it("refuses a missing or short secret", async () => {
    vi.stubEnv("LIKA_SESSION_SECRET", "short")
    const { encryptSession, sessionFromTokens } = await import("@/lib/session")
    await expect(encryptSession(sessionFromTokens(tokens))).rejects.toThrow(/LIKA_SESSION_SECRET/)
  })
})
