import type { DomainRole, LikaClaims } from "@/lib/types"

export const ROLES: DomainRole[] = ["viewer", "editor", "manager"]

export const ROLE_LABELS: Record<DomainRole, string> = {
  viewer: "Viewer",
  editor: "Editor",
  manager: "Manager",
}

export const ROLE_DESCRIPTIONS: Record<DomainRole, string> = {
  viewer: "Reads and searches links",
  editor: "Creates, edits and deletes links",
  manager: "Also invites users and manages API keys",
}

/** Whether [role] includes [required]: each role includes the previous ones */
export function roleIncludes(role: DomainRole | undefined, required: DomainRole): boolean {
  return role !== undefined && ROLES.indexOf(role) >= ROLES.indexOf(required)
}

export function managedDomainIds(claims: LikaClaims): string[] {
  return Object.entries(claims.domains)
    .filter(([, role]) => role === "manager")
    .map(([id]) => id)
}

export function canManageSomething(claims: LikaClaims): boolean {
  return claims.admin || managedDomainIds(claims).length > 0
}
