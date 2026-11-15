import { describe, expect, it } from "vitest"
import { canManageSomething, managedDomainIds, roleIncludes } from "@/lib/access"

describe("access", () => {
  it("orders roles, each including the previous ones", () => {
    expect(roleIncludes("manager", "editor")).toBe(true)
    expect(roleIncludes("editor", "editor")).toBe(true)
    expect(roleIncludes("viewer", "editor")).toBe(false)
    expect(roleIncludes(undefined, "viewer")).toBe(false)
  })

  it("lists managed domains, admins managing everything", () => {
    const claims = { v: 1, admin: false, domains: { a: "manager" as const, b: "editor" as const } }
    expect(managedDomainIds(claims)).toEqual(["a"])
    expect(canManageSomething(claims)).toBe(true)
    expect(canManageSomething({ v: 1, admin: false, domains: { b: "editor" } })).toBe(false)
    expect(canManageSomething({ v: 1, admin: true, domains: {} })).toBe(true)
  })
})
