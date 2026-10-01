import { describe, expect, it } from "vitest"
import { isSecureRequest } from "@/lib/session-cookie"

describe("isSecureRequest", () => {
  it("trusts the reverse proxy first", () => {
    expect(isSecureRequest(new Headers({ "x-forwarded-proto": "https" }), "http:")).toBe(true)
    expect(isSecureRequest(new Headers({ "x-forwarded-proto": "http" }), "https:")).toBe(false)
  })
  it("falls back to the origin, then to the request protocol", () => {
    expect(isSecureRequest(new Headers({ origin: "https://lika.test" }))).toBe(true)
    expect(isSecureRequest(new Headers(), "http:")).toBe(false)
  })
})
