import type { Metadata } from "next"
import { Suspense } from "react"
import { LinksView } from "@/components/links/links-view"

export const metadata: Metadata = { title: "Links" }

export default function LinksPage() {
  return (
    <Suspense>
      <LinksView />
    </Suspense>
  )
}
