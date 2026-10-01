import { describe, expect, it } from "vitest"
import { withQuery } from "@/lib/client"

describe("withQuery", () => {
  it("skips empty values", () => {
    expect(withQuery("/api/links", { domainId: undefined, filter: "", limit: 20, force: false })).toBe("/api/links?limit=20&force=false")
    expect(withQuery("/api/links", {})).toBe("/api/links")
  })
})
