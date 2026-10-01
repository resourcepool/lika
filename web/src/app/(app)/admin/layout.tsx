import { redirect } from "next/navigation"
import { getSession } from "@/lib/session"

export default async function AdminLayout({ children }: LayoutProps<"/admin">) {
  const session = await getSession()
  if (!session?.admin) {
    redirect("/links")
  }
  return children
}
