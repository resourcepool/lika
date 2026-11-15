import { redirect } from "next/navigation"
import { getSession } from "@/lib/session"
import { canManageSomething } from "@/lib/access"

/** Users and API keys: admins, and managers of at least one domain */
export default async function ManageLayout({ children }: { children: React.ReactNode }) {
  const session = await getSession()
  if (!session || !canManageSomething(session.claims)) {
    redirect("/links")
  }
  return children
}
