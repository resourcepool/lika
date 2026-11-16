import { describe, expect, it } from "vitest"
import { addFilter, metadataKeysOf, metadataParams, rowsToMetadata } from "@/lib/link-batch"
import { withQuery } from "@/lib/client"
import type { Link } from "@/lib/types"

describe("metadata filters", () => {
  it("become repeated meta parameters, a key alone matching any value", () => {
    const filters = addFilter(addFilter([], "campaign", " summer "), "lang", "")
    expect(metadataParams(filters)).toEqual(["campaign=summer", "lang"])
    expect(withQuery("/api/links", { meta: metadataParams(filters), filter: "" })).toBe("/api/links?meta=campaign%3Dsummer&meta=lang")
  })

  it("are not added twice, nor empty", () => {
    const filters = addFilter([], "campaign", "summer")
    expect(addFilter(filters, "Campaign", "SUMMER")).toBe(filters)
    expect(addFilter(filters, "  ", "x")).toBe(filters)
    expect(addFilter(filters, "campaign", "")).toHaveLength(2)
  })
})

describe("batch helpers", () => {
  it("list the metadata keys of the selected links", () => {
    const links = [{ metadata: { b: "1", a: "2" } }, { metadata: { a: "3", c: "4" } }, { metadata: {} }] as unknown as Link[]
    expect(metadataKeysOf(links)).toEqual(["a", "b", "c"])
  })

  it("turn editor rows into metadata, without blank keys", () => {
    expect(rowsToMetadata([{ key: " team ", value: "growth" }, { key: "", value: "x" }, { key: "q", value: "" }])).toEqual({ team: "growth", q: "" })
  })
})
