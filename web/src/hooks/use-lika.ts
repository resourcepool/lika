"use client"

import { useQuery } from "@tanstack/react-query"
import { api } from "@/lib/client"
import type { ApiKey, Domain, User } from "@/lib/types"

/** Domains the signed-in user has a role on (all of them for an admin) */
export function useDomains() {
  return useQuery({ queryKey: ["domains"], queryFn: () => api<Domain[]>("/api/domains") })
}

/** Users the signed-in user may see: everyone for an admin, the users of their domains for a manager */
export function useUsers(enabled = true) {
  return useQuery({ queryKey: ["users"], queryFn: () => api<User[]>("/api/users"), enabled })
}

export function useApiKeys(enabled = true) {
  return useQuery({ queryKey: ["keys"], queryFn: () => api<ApiKey[]>("/api/keys"), enabled })
}
