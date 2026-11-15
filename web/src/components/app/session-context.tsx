"use client"

import { createContext, useContext, useMemo } from "react"
import { useQuery } from "@tanstack/react-query"
import { api } from "@/lib/client"
import { canManageSomething, managedDomainIds, roleIncludes } from "@/lib/access"
import type { DomainRole, LikaClaims, Me } from "@/lib/types"

export type SessionUser = { id: string; email: string; name: string }

type ClientSession = {
  user: SessionUser
  claims: LikaClaims
  admin: boolean
  /** Admin, or manager of at least one domain: sees users and API keys */
  canManage: boolean
  managedDomainIds: string[]
  can: (domainId: string, role: DomainRole) => boolean
}

const SessionContext = createContext<ClientSession | null>(null)

/**
 * The signed-in user and their rights. Starts from the session cookie, then follows the API,
 * so that a change of rights shows without signing in again.
 */
export function SessionProvider({ user, claims, children }: { user: SessionUser; claims: LikaClaims; children: React.ReactNode }) {
  const me = useQuery({ queryKey: ["me"], queryFn: () => api<Me>("/api/me"), staleTime: 60_000, refetchOnWindowFocus: true })
  const live = me.data
  const value = useMemo<ClientSession>(() => {
    const current = live?.claims ?? claims
    return {
      user: live ? { id: live.user.id, email: live.user.email, name: live.user.name } : user,
      claims: current,
      admin: current.admin,
      canManage: canManageSomething(current),
      managedDomainIds: managedDomainIds(current),
      can: (domainId, role) => current.admin || roleIncludes(current.domains[domainId], role),
    }
  }, [live, user, claims])
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
}

/** What the signed-in user may do. The API enforces it; this only shapes the UI. */
export function useSession(): ClientSession {
  const session = useContext(SessionContext)
  if (!session) {
    throw new Error("useSession must be used under SessionProvider")
  }
  return session
}
