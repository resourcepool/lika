import { describe, expect, it } from "vitest"
import { linkStatus } from "@/lib/link-status"

const now = new Date("2026-06-15T12:00:00")
const options = (o: object) => ({ options: { activation: null, landing: null, expiry: null, password: null, ...o } })

describe("linkStatus", () => {
  it("is active without activation nor expiry", () => {
    expect(linkStatus(options({}), now)).toBe("active")
  })
  it("stays active on its last day, like the API", () => {
    expect(linkStatus(options({ expiry: { expiryDate: "2026-06-15" } }), now)).toBe("active")
    expect(linkStatus(options({ expiry: { expiryDate: "2026-06-14" } }), now)).toBe("expired")
  })
  it("is scheduled before its activation instant", () => {
    expect(linkStatus(options({ activation: { activationInstant: "2026-06-16T00:00:00Z", targetUrlBeforeActivation: null } }), now)).toBe("scheduled")
    expect(linkStatus(options({ activation: { activationInstant: "2026-06-01T00:00:00Z", targetUrlBeforeActivation: null } }), now)).toBe("active")
  })
  it("reports expiry before activation", () => {
    expect(
      linkStatus(options({ expiry: { expiryDate: "2026-06-01" }, activation: { activationInstant: "2026-07-01T00:00:00Z", targetUrlBeforeActivation: null } }), now),
    ).toBe("expired")
  })
})
