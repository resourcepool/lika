import { redirect } from "next/navigation"
import { getSession } from "@/lib/session"
import { likaFetch } from "@/lib/lika-api"
import { AppSidebar } from "@/components/app/app-sidebar"
import { SessionProvider } from "@/components/app/session-context"
import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar"

async function apiVersion(token: string): Promise<string | undefined> {
  try {
    const response = await likaFetch("/public/build", token)
    return response.ok ? ((await response.json()) as { version?: string }).version : undefined
  } catch {
    return undefined
  }
}

export default async function AppLayout({ children }: LayoutProps<"/">) {
  const session = await getSession()
  if (!session) {
    redirect("/signin")
  }
  const version = await apiVersion(session.token)
  return (
    <SessionProvider value={{ admin: session.admin, accountId: session.accountId, accountName: session.accountName }}>
      <SidebarProvider>
        <AppSidebar version={version} />
        <SidebarInset className="min-w-0">{children}</SidebarInset>
      </SidebarProvider>
    </SessionProvider>
  )
}
