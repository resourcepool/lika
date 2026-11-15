import { redirect } from "next/navigation"
import { getSession } from "@/lib/session"
import { likaFetch } from "@/lib/lika-api"
import { AppSidebar } from "@/components/app/app-sidebar"
import { SessionProvider } from "@/components/app/session-context"
import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar"

async function apiVersion(): Promise<string | undefined> {
  try {
    const response = await likaFetch("/public/build")
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
  const version = await apiVersion()
  return (
    <SessionProvider user={session.user} claims={session.claims}>
      <SidebarProvider>
        <AppSidebar version={version} />
        <SidebarInset className="min-w-0">{children}</SidebarInset>
      </SidebarProvider>
    </SessionProvider>
  )
}
