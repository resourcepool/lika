import type { Metadata } from "next"
import { DomainsView } from "@/components/domains/domains-view"

export const metadata: Metadata = { title: "Domains" }

export default function DomainsPage() {
  return <DomainsView />
}
