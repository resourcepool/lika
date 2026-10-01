export function EmptyState({ title, description, children }: { title: string; description: string; children?: React.ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-1 py-12 text-center">
      <p className="font-medium">{title}</p>
      <p className="text-sm text-muted-foreground">{description}</p>
      {children}
    </div>
  )
}
