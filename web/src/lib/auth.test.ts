import { afterEach, describe, expect, it, vi } from "vitest"
import type { Session } from "@/lib/session"

vi.mock("next/headers", () => ({ cookies: vi.fn() }))

const session: Session = {
  accessToken: "a",
  refreshToken: "r1",
  expiresAt: 100_000,
  user: { id: "u1", email: "ada@example.com", name: "Ada" },
  claims: { v: 1, admin: false, domains: {} },
}

describe("session renewal", () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.resetModules()
  })

  it("renews shortly before the access token expires", async () => {
    const { needsRenewal } = await import("@/lib/auth")
    expect(needsRenewal(session, 50_000)).toBe(false)
    expect(needsRenewal(session, 80_000)).toBe(true)
  })

  it("shares one renewal between concurrent requests, refresh tokens being single-use", async () => {
    vi.stubEnv("LIKA_API_URL", "http://api.test")
    const fetch = vi.fn(async () =>
      Response.json({
        accessToken: "a2",
        refreshToken: "r2",
        expiresIn: 900,
        user: { id: "u1", email: "ada@example.com", name: "Ada", version: 2, admin: true, domains: {} },
      }),
    )
    vi.stubGlobal("fetch", fetch)
    const { renewSession } = await import("@/lib/auth")
    const [first, second] = await Promise.all([renewSession(session), renewSession(session)])
    expect(fetch).toHaveBeenCalledTimes(1)
    expect(first).toEqual(second)
    expect(first?.refreshToken).toBe("r2")
    expect(first?.claims.admin).toBe(true)
  })

  it("ends the session when the API refuses the refresh token", async () => {
    vi.stubEnv("LIKA_API_URL", "http://api.test")
    vi.stubGlobal("fetch", vi.fn(async () => Response.json({ errorCode: "invalid-credentials" }, { status: 401 })))
    const { renewSession } = await import("@/lib/auth")
    expect(await renewSession({ ...session, refreshToken: "dead" })).toBeNull()
  })
})
