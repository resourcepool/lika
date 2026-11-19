import type { Metadata } from "next"
import { Suspense } from "react"
import { EngagementView } from "@/components/engagement/engagement-view"

export const metadata: Metadata = { title: "Engagement" }

export default function EngagementPage() {
  return (
    <Suspense>
      <EngagementView />
    </Suspense>
  )
}
