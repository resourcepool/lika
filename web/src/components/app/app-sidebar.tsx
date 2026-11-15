"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import { ArrowLeftRight, Globe, KeyRound, Link2, LogOut, Users } from "lucide-react"
import { signOut } from "@/app/actions"
import { Logo } from "@/components/app/logo"
import { ThemeToggle } from "@/components/app/theme-toggle"
import { useSession } from "@/components/app/session-context"
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from "@/components/ui/sidebar"

const workspace = [{ href: "/links", label: "Links", icon: Link2 }]

const manage = [
  { href: "/users", label: "Users", icon: Users },
  { href: "/keys", label: "API Keys", icon: KeyRound },
]

const admin = [
  { href: "/admin/domains", label: "Domains", icon: Globe },
  { href: "/admin/archive", label: "Import / Export", icon: ArrowLeftRight },
]

function NavGroup({ label, items }: { label: string; items: typeof workspace }) {
  const pathname = usePathname()
  return (
    <SidebarGroup>
      <SidebarGroupLabel className="font-mono text-[11px] tracking-wider uppercase">{label}</SidebarGroupLabel>
      <SidebarGroupContent>
        <SidebarMenu>
          {items.map((item) => (
            <SidebarMenuItem key={item.href}>
              <SidebarMenuButton asChild isActive={pathname.startsWith(item.href)} tooltip={item.label}>
                <Link href={item.href}>
                  <item.icon />
                  <span>{item.label}</span>
                </Link>
              </SidebarMenuButton>
            </SidebarMenuItem>
          ))}
        </SidebarMenu>
      </SidebarGroupContent>
    </SidebarGroup>
  )
}

export function AppSidebar({ version }: { version?: string }) {
  const session = useSession()
  return (
    <Sidebar collapsible="icon">
      <SidebarHeader>
        <Link href="/links" className="flex items-center gap-2 px-1 py-1.5">
          <Logo className="size-7 shrink-0" />
          <span className="font-semibold tracking-tight group-data-[collapsible=icon]:hidden">lika</span>
        </Link>
      </SidebarHeader>
      <SidebarContent>
        <NavGroup label="Workspace" items={workspace} />
        {session.canManage && <NavGroup label="Manage" items={manage} />}
        {session.admin && <NavGroup label="Admin" items={admin} />}
      </SidebarContent>
      <SidebarFooter>
        <div className="flex items-center justify-between gap-2 px-2 group-data-[collapsible=icon]:flex-col">
          <Link href="/profile" className="min-w-0 rounded-md px-1 py-0.5 hover:bg-sidebar-accent group-data-[collapsible=icon]:hidden" title="Your profile">
            <p className="truncate text-sm font-medium">{session.user.name}</p>
            <p className="truncate font-mono text-[11px] text-muted-foreground">{session.admin ? "admin" : session.user.email}</p>
          </Link>
          <div className="flex items-center gap-1 group-data-[collapsible=icon]:flex-col">
            <ThemeToggle />
            <form action={signOut}>
              <SidebarMenuButton type="submit" size="sm" tooltip="Sign out" className="size-8 justify-center p-0">
                <LogOut />
                <span className="sr-only">Sign out</span>
              </SidebarMenuButton>
            </form>
          </div>
        </div>
        <p className="px-2 pb-1 font-mono text-[11px] text-muted-foreground group-data-[collapsible=icon]:hidden">
          api {version ? `v${version}` : "unknown"}
        </p>
      </SidebarFooter>
    </Sidebar>
  )
}
