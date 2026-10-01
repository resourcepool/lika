import type { Metadata } from "next"
import { KeysView } from "@/components/keys/keys-view"

export const metadata: Metadata = { title: "API Keys" }

export default function KeysPage() {
  return <KeysView />
}
