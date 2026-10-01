"use client"

import { useQuery } from "@tanstack/react-query"
import { api } from "@/lib/client"
import type { Account, Domain } from "@/lib/types"

/** Domains the signed-in key can manage (all of them for an admin) */
export function useDomains() {
  return useQuery({ queryKey: ["domains"], queryFn: () => api<Domain[]>("/api/domains") })
}

export function useAccounts(enabled = true) {
  return useQuery({ queryKey: ["accounts"], queryFn: () => api<Account[]>("/api/admin/accounts"), enabled })
}
