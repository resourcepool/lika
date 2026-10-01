import { describe, expect, it } from "vitest"
import { emptyLinkForm, formToLinkInput, linkFormSchema, linkToForm } from "@/lib/link-form"
import type { Link } from "@/lib/types"

const link: Link = {
  id: "l1",
  host: "go.test",
  createdBy: "a1",
  slug: "promo",
  shortenedUrl: "https://go.test/promo",
  targetUrl: "https://example.com",
  type: "WITH_LANDING",
  createdDateTime: "2026-01-01T00:00:00Z",
  lastUpdateDateTime: "2026-01-01T00:00:00Z",
  options: { activation: null, landing: { templateId: "t1" }, expiry: { expiryDate: "2026-12-31" }, password: { enabled: true } },
  metadata: { campaign: "spring" },
}

describe("formToLinkInput", () => {
  it("omits the password on edit unless it changes, so the API keeps it", () => {
    const input = formToLinkInput(linkToForm(link, "d1"), link)
    expect(input.options.password).toBeUndefined()
    expect(input.id).toBe("l1")
  })
  it("sends a new password, or asks for its removal", () => {
    expect(formToLinkInput({ ...linkToForm(link, "d1"), passwordMode: "set", password: "x" }, link).options.password).toEqual({ password: "x" })
    expect(formToLinkInput({ ...linkToForm(link, "d1"), passwordMode: "remove" }, link).options.password).toEqual({ remove: true })
  })
  it("keeps the fields the form does not show", () => {
    const input = formToLinkInput(linkToForm(link, "d1"), link)
    expect(input.type).toBe("WITH_LANDING")
    expect(input.options.landing).toEqual({ templateId: "t1" })
    expect(input.options.expiry).toEqual({ expiryDate: "2026-12-31" })
    expect(input.metadata).toEqual({ campaign: "spring" })
  })
  it("leaves the slug out when empty, for the API to generate one", () => {
    const input = formToLinkInput({ ...emptyLinkForm("d1"), targetUrl: "https://example.com" })
    expect(input).not.toHaveProperty("slug")
    expect(input.type).toBe("SIMPLE")
  })
  it("drops metadata rows without a key", () => {
    const input = formToLinkInput({ ...emptyLinkForm("d1"), targetUrl: "https://e.com", metadata: [{ key: " ", value: "x" }, { key: " k ", value: "v" }] })
    expect(input.metadata).toEqual({ k: "v" })
  })
})

describe("linkFormSchema", () => {
  it("requires a password when setting one, and dates when enabled", () => {
    const result = linkFormSchema.safeParse({ ...emptyLinkForm("d1"), targetUrl: "https://e.com", passwordMode: "set", scheduled: true, expires: true })
    expect(result.success).toBe(false)
    const paths = result.error!.issues.map((i) => i.path.join("."))
    expect(paths).toEqual(expect.arrayContaining(["password", "activationInstant", "expiryDate"]))
  })
  it("rejects slugs with other characters", () => {
    expect(linkFormSchema.safeParse({ ...emptyLinkForm("d1"), targetUrl: "https://e.com", slug: "a/b" }).success).toBe(false)
  })
})
