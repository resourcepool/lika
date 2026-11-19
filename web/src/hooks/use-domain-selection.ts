"use client"

import { useSyncExternalStore } from "react"
import { usePathname, useRouter, useSearchParams } from "next/navigation"
import { withQuery } from "@/lib/client"
import type { Domain } from "@/lib/types"

export const ALL_DOMAINS = "all"
const DOMAIN_STORAGE_KEY = "lika.domain"

// The last chosen domain only changes through selectDomain, which also updates the URL
const subscribeToNothing = () => () => {}
function readStoredDomain(): string | null {
  try {
    return localStorage.getItem(DOMAIN_STORAGE_KEY)
  } catch {
    return null
  }
}

/**
 * The domain a page shows: the `domain` parameter of its URL, else the last one chosen, else all of them.
 * @param keep other URL parameters to keep when the domain changes
 */
export function useDomainSelection(domains: Domain[] | undefined, keep: Record<string, string | undefined> = {}) {
  const router = useRouter()
  const pathname = usePathname()
  const param = useSearchParams().get("domain")
  const stored = useSyncExternalStore(subscribeToNothing, readStoredDomain, () => null)
  const known = (id: string | null) => !!id && (id === ALL_DOMAINS || !!domains?.some((d) => d.id === id))
  const domainId = known(param) ? param! : known(stored) ? stored! : ALL_DOMAINS

  function selectDomain(id: string) {
    try {
      localStorage.setItem(DOMAIN_STORAGE_KEY, id)
    } catch {}
    router.replace(withQuery(pathname, { ...keep, domain: id === ALL_DOMAINS ? undefined : id }))
  }

  return { domainId, selectDomain }
}
