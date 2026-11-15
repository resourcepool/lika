import { redirect } from "next/navigation"

/** API keys moved out of the admin section in 0.7.0, managers using them too */
export default function LegacyKeysPage() {
  redirect("/keys")
}
