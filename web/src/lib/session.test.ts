import { beforeEach, describe, expect, it, vi } from "vitest"

vi.mock("next/headers", () => ({ cookies: vi.fn() }))

describe("session encryption", () => {
  beforeEach(() => {
    vi.resetModules()
    vi.stubEnv("LIKA_SESSION_SECRET", "a".repeat(40))
  })

  it("round-trips, and the key is not readable from the cookie", async () => {
    const { encryptSession, decryptSession } = await import("@/lib/session")
    const value = await encryptSession({ token: "secret-key", admin: true, accountName: "root" })
    expect(value).not.toContain("secret-key")
    expect(atob(value.split(".")[0])).not.toContain("secret-key")
    expect(await decryptSession(value)).toEqual({ token: "secret-key", admin: true, accountId: undefined, accountName: "root" })
  })

  it("rejects cookies encrypted with another secret, or tampered with", async () => {
    const { encryptSession, decryptSession } = await import("@/lib/session")
    const value = await encryptSession({ token: "secret-key", admin: false })
    expect(await decryptSession(value.slice(0, -2) + "xx")).toBeNull()
    vi.stubEnv("LIKA_SESSION_SECRET", "b".repeat(40))
    expect(await decryptSession(value)).toBeNull()
  })

  it("refuses a missing or short secret", async () => {
    vi.stubEnv("LIKA_SESSION_SECRET", "short")
    const { encryptSession } = await import("@/lib/session")
    await expect(encryptSession({ token: "t", admin: false })).rejects.toThrow(/LIKA_SESSION_SECRET/)
  })
})
