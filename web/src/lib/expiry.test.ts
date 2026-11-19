import { describe, expect, it } from "vitest"
import { expiryDateOf } from "@/lib/expiry"

describe("expiryDateOf", () => {
  const today = new Date(2026, 10, 30, 15, 0)
  it("counts delays from today", () => {
    expect(expiryDateOf({ preset: "7d", date: "" }, today)).toBe("2026-12-07")
    expect(expiryDateOf({ preset: "30d", date: "" }, today)).toBe("2026-12-30")
  })
  it("keeps months whole, down to the last day of a shorter month", () => {
    expect(expiryDateOf({ preset: "3m", date: "" }, today)).toBe("2027-02-28")
  })
  it("takes the custom date as chosen, ignoring it for delays", () => {
    expect(expiryDateOf({ preset: "custom", date: "2027-06-01" }, today)).toBe("2027-06-01")
    expect(expiryDateOf({ preset: "custom", date: "" }, today)).toBe("")
    expect(expiryDateOf({ preset: "7d", date: "2027-06-01" }, today)).toBe("2026-12-07")
  })
})
