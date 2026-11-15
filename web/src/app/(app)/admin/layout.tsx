import { redirect } from "next/navigation"
import { getSession } from "@/lib/session"

/** Rights in the cookie may be a few minutes old: the API enforces the current ones */
export default async function AdminLayout({ children }: LayoutProps<"/admin">) {
  const session = await getSession()
  if (!session?.claims.admin) {
    redirect("/links")
  }
  return children
}
