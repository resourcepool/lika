"use client"

import { Moon, Sun } from "lucide-react"
import { useTheme } from "next-themes"
import { SidebarMenuButton } from "@/components/ui/sidebar"

export function ThemeToggle() {
  const { resolvedTheme, setTheme } = useTheme()
  const next = resolvedTheme === "dark" ? "light" : "dark"
  return (
    <SidebarMenuButton size="sm" tooltip={`Switch to ${next} mode`} className="size-8 justify-center p-0" onClick={() => setTheme(next)}>
      <Sun className="hidden dark:block" />
      <Moon className="dark:hidden" />
      <span className="sr-only">Switch to {next} mode</span>
    </SidebarMenuButton>
  )
}
