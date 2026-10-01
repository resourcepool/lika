import { SidebarTrigger } from "@/components/ui/sidebar"
import { Separator } from "@/components/ui/separator"

export function PageHeader({ title, description, children }: { title: string; description?: string; children?: React.ReactNode }) {
  return (
    <header className="relative border-b">
      <div className="bg-grid pointer-events-none absolute inset-0 opacity-60" />
      <div className="relative flex flex-wrap items-center gap-3 px-4 py-4 md:px-6">
        <SidebarTrigger className="-ml-1" />
        <Separator orientation="vertical" className="h-5" />
        <div className="min-w-0 flex-1">
          <h1 className="text-lg font-semibold tracking-tight">{title}</h1>
          {description && <p className="truncate text-sm text-muted-foreground">{description}</p>}
        </div>
        <div className="flex flex-wrap items-center gap-2">{children}</div>
      </div>
    </header>
  )
}
