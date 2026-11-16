"use client"

import { useEffect, useMemo, useState, useSyncExternalStore } from "react"
import { usePathname, useRouter, useSearchParams } from "next/navigation"
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { createColumnHelper, tableFeatures, useTable } from "@tanstack/react-table"
import { formatDistanceToNowStrict } from "date-fns"
import { ArrowDown, ArrowUp, ChevronLeft, ChevronRight, ChevronsUpDown, ExternalLink, Link2, MoreHorizontal, Pencil, Plus, Search, Trash2 } from "lucide-react"
import { toast } from "sonner"
import { api, withQuery } from "@/lib/client"
import { metadataParams, type MetadataFilter } from "@/lib/link-batch"
import type { Link, MetadataKeyValues, Page, SortDirection, SortProperty } from "@/lib/types"
import { useDomains } from "@/hooks/use-lika"
import { PageHeader } from "@/components/app/page-header"
import { CopyButton } from "@/components/app/copy-button"
import { ALL_DOMAINS, DomainSwitcher } from "@/components/links/domain-switcher"
import { LinkStatusBadges } from "@/components/links/link-status-badge"
import { LinkFormDialog } from "@/components/links/link-form-dialog"
import { LinkDetailsSheet } from "@/components/links/link-details-sheet"
import { MetadataFilterBar } from "@/components/links/metadata-filter-bar"
import { LinkBatchBar } from "@/components/links/link-batch-bar"
import { Checkbox } from "@/components/ui/checkbox"
import { ConfirmDialog } from "@/components/app/confirm-dialog"
import { EmptyState } from "@/components/app/empty-state"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"

const PAGE_SIZES = [20, 50]
const DOMAIN_STORAGE_KEY = "lika.domain"

type Sort = { by: SortProperty; direction: SortDirection }

const features = tableFeatures({})

// The last chosen domain only changes through selectDomain, which also updates the URL
const subscribeToNothing = () => () => {}
function readStoredDomain(): string | null {
  try {
    return localStorage.getItem(DOMAIN_STORAGE_KEY)
  } catch {
    return null
  }
}
const helper = createColumnHelper<typeof features, Link>()

export function LinksView() {
  const router = useRouter()
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const queryClient = useQueryClient()
  const domains = useDomains()

  const [sort, setSort] = useState<Sort>({ by: "creation_date", direction: "desc" })
  const [pageIndex, setPageIndex] = useState(0)
  const [pageSize, setPageSize] = useState(PAGE_SIZES[0])

  const domainParam = searchParams.get("domain")
  const storedDomain = useSyncExternalStore(subscribeToNothing, readStoredDomain, () => null)
  const known = (id: string | null) => !!id && (id === ALL_DOMAINS || !!domains.data?.some((d) => d.id === id))
  const domainId = known(domainParam) ? domainParam! : known(storedDomain) ? storedDomain! : ALL_DOMAINS

  function selectDomain(id: string) {
    try {
      localStorage.setItem(DOMAIN_STORAGE_KEY, id)
    } catch {}
    setPageIndex(0)
    router.replace(withQuery(pathname, { domain: id === ALL_DOMAINS ? undefined : id }))
  }

  const [search, setSearch] = useState("")
  const [filter, setFilter] = useState("")
  useEffect(() => {
    const timer = setTimeout(() => {
      setFilter(search.trim())
      setPageIndex(0)
    }, 300)
    return () => clearTimeout(timer)
  }, [search])

  const [metaFilters, setMetaFilters] = useState<MetadataFilter[]>([])
  function changeMetaFilters(filters: MetadataFilter[]) {
    setMetaFilters(filters)
    setPageIndex(0)
  }

  // Selected links, kept across pages; a new search starts a new selection
  const [selected, setSelected] = useState<Map<string, Link>>(new Map())
  const selectionScope = JSON.stringify([domainId, filter, metaFilters])
  const [scope, setScope] = useState(selectionScope)
  if (scope !== selectionScope) {
    setScope(selectionScope)
    setSelected(new Map())
  }
  function toggle(links: Link[], on: boolean) {
    setSelected((current) => {
      const next = new Map(current)
      links.forEach((link) => (on ? next.set(link.id, link) : next.delete(link.id)))
      return next
    })
  }

  const metadataValues = useQuery({
    queryKey: ["link-metadata", domainId],
    queryFn: () => api<MetadataKeyValues[]>("/api/links/metadata", { query: { domainId: domainId === ALL_DOMAINS ? undefined : domainId } }),
    enabled: domains.isSuccess,
    staleTime: 60_000,
  })

  const links = useQuery({
    queryKey: ["links", domainId, filter, metaFilters, sort, pageIndex, pageSize],
    queryFn: () =>
      api<Page<Link>>("/api/links", {
        query: {
          domainId: domainId === ALL_DOMAINS ? undefined : domainId,
          filter,
          meta: metadataParams(metaFilters),
          sort_by: sort.by,
          sort_direction: sort.direction,
          limit: pageSize,
          offset: pageIndex * pageSize,
        },
      }),
    placeholderData: keepPreviousData,
    enabled: domains.isSuccess,
  })

  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<Link | undefined>()
  const [details, setDetails] = useState<Link | undefined>()
  const [deleting, setDeleting] = useState<Link | undefined>()

  const remove = useMutation({
    mutationFn: (link: Link) => api(`/api/links/${link.id}`, { method: "DELETE" }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["links"] })
      toast.success("Link deleted")
      setDeleting(undefined)
    },
    onError: (error) => toast.error(error.message),
  })

  function openCreate() {
    setEditing(undefined)
    setFormOpen(true)
  }
  function openEdit(link: Link) {
    setDetails(undefined)
    setEditing(link)
    setFormOpen(true)
  }

  const columns = useMemo(
    () =>
      helper.columns([
        helper.display({
          id: "select",
          header: () => {
            const rows = links.data?.items ?? []
            const count = rows.filter((l) => selected.has(l.id)).length
            return (
              <Checkbox
                aria-label="Select the links of this page"
                disabled={rows.length === 0}
                checked={count > 0 && count === rows.length ? true : count > 0 ? "indeterminate" : false}
                onCheckedChange={() => toggle(rows, count < rows.length)}
              />
            )
          },
          cell: (info) => {
            const link = info.row.original
            return (
              <span onClick={(e) => e.stopPropagation()} className="flex">
                <Checkbox aria-label={`Select /${link.slug}`} checked={selected.has(link.id)} onCheckedChange={(on) => toggle([link], on === true)} />
              </span>
            )
          },
        }),
        helper.accessor("slug", {
          header: () => <SortHeader label="Short link" property="slug" sort={sort} onSort={setSort} />,
          cell: (info) => {
            const link = info.row.original
            return (
              <div className="flex min-w-0 items-center gap-1">
                <div className="min-w-0">
                  <div className="truncate font-mono text-sm font-medium">/{link.slug}</div>
                  <div className="truncate font-mono text-xs text-muted-foreground">{link.host}</div>
                </div>
                <span onClick={(e) => e.stopPropagation()}>
                  <CopyButton value={link.shortenedUrl} label="Copy short URL" />
                </span>
              </div>
            )
          },
        }),
        helper.accessor("targetUrl", {
          header: "Destination",
          cell: (info) => <span className="block max-w-[28rem] truncate font-mono text-xs text-muted-foreground">{info.getValue()}</span>,
        }),
        helper.display({ id: "status", header: "Status", cell: (info) => <LinkStatusBadges link={info.row.original} /> }),
        helper.accessor("createdDateTime", {
          header: () => <SortHeader label="Created" property="creation_date" sort={sort} onSort={setSort} />,
          cell: (info) => <RelativeDate iso={info.getValue()} />,
        }),
        helper.accessor("lastUpdateDateTime", {
          header: () => <SortHeader label="Updated" property="last_activity" sort={sort} onSort={setSort} />,
          cell: (info) => <RelativeDate iso={info.getValue()} />,
        }),
        helper.display({
          id: "actions",
          header: () => <span className="sr-only">Actions</span>,
          cell: (info) => (
            <LinkActions link={info.row.original} onEdit={() => openEdit(info.row.original)} onDelete={() => setDeleting(info.row.original)} />
          ),
        }),
      ]),
    [sort, selected, links.data],
  )

  const table = useTable({ features, columns, data: links.data?.items ?? [] })
  const total = links.data?.totalItemCount ?? 0
  const pageCount = Math.max(1, Math.ceil(total / pageSize))
  const noDomain = domains.isSuccess && domains.data.length === 0

  return (
    <>
      <PageHeader title="Links" description={total ? `${total} link${total > 1 ? "s" : ""}` : "Short links of your domains"}>
        {domains.data && domains.data.length > 0 && <DomainSwitcher domains={domains.data} value={domainId} onChange={selectDomain} />}
        <Button onClick={openCreate} disabled={!domains.data?.length}>
          <Plus />
          New link
        </Button>
      </PageHeader>

      <div className="space-y-4 p-4 md:p-6">
        <div className="flex flex-wrap items-center gap-2">
          <div className="relative w-full max-w-sm">
            <Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search slug, URL or host" className="pl-9" aria-label="Search links" />
          </div>
          <MetadataFilterBar filters={metaFilters} onChange={changeMetaFilters} known={metadataValues.data ?? []} />
        </div>

        {selected.size > 0 && (
          <LinkBatchBar
            selected={[...selected.values()]}
            onClear={() => setSelected(new Map())}
            onUpdated={(updated) => setSelected(new Map(updated.map((link) => [link.id, link])))}
          />
        )}

        {noDomain ? (
          <EmptyState title="No domain yet" description="This key has no domain to manage. Ask an admin to grant it one." />
        ) : (
          <div className="overflow-hidden rounded-lg border">
            <Table>
              <TableHeader className="bg-muted/40">
                {table.getHeaderGroups().map((group) => (
                  <TableRow key={group.id} className="hover:bg-transparent">
                    {group.headers.map((header) => (
                      <TableHead key={header.id} className="font-mono text-[11px] tracking-wider uppercase">
                        {header.isPlaceholder ? null : <table.FlexRender header={header} />}
                      </TableHead>
                    ))}
                  </TableRow>
                ))}
              </TableHeader>
              <TableBody>
                {links.isPending &&
                  Array.from({ length: 5 }).map((_, i) => (
                    <TableRow key={i}>
                      <TableCell colSpan={columns.length}>
                        <Skeleton className="h-8 w-full" />
                      </TableCell>
                    </TableRow>
                  ))}
                {table.getRowModel().rows.map((row) => (
                  <TableRow
                    key={row.id}
                    className="cursor-pointer"
                    data-state={selected.has(row.original.id) ? "selected" : undefined}
                    onClick={() => setDetails(row.original)}
                  >
                    {row.getAllCells().map((cell) => (
                      <TableCell key={cell.id} className="py-2.5">
                        <table.FlexRender cell={cell} />
                      </TableCell>
                    ))}
                  </TableRow>
                ))}
                {links.isSuccess && total === 0 && (
                  <TableRow className="hover:bg-transparent">
                    <TableCell colSpan={columns.length}>
                      <EmptyState
                        title={filter || metaFilters.length ? "No match" : "No link yet"}
                        description={filter || metaFilters.length ? "No link matches these filters." : "Create your first short link."}
                      />
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </div>
        )}

        {total > 0 && (
          <div className="flex flex-wrap items-center justify-between gap-3 text-sm text-muted-foreground">
            <div className="flex items-center gap-2">
              <span>Rows</span>
              <Select
                value={String(pageSize)}
                onValueChange={(v) => {
                  setPageSize(Number(v))
                  setPageIndex(0)
                }}
              >
                <SelectTrigger size="sm" className="w-20" aria-label="Rows per page">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {PAGE_SIZES.map((size) => (
                    <SelectItem key={size} value={String(size)}>
                      {size}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="flex items-center gap-2">
              <span className="font-mono text-xs">
                {pageIndex + 1} / {pageCount}
              </span>
              <Button variant="outline" size="icon" className="size-8" disabled={pageIndex === 0} onClick={() => setPageIndex(pageIndex - 1)} aria-label="Previous page">
                <ChevronLeft />
              </Button>
              <Button variant="outline" size="icon" className="size-8" disabled={pageIndex + 1 >= pageCount} onClick={() => setPageIndex(pageIndex + 1)} aria-label="Next page">
                <ChevronRight />
              </Button>
            </div>
          </div>
        )}
      </div>

      {domains.data && (
        <LinkFormDialog
          open={formOpen}
          onOpenChange={setFormOpen}
          domains={domains.data}
          defaultDomainId={domainId === ALL_DOMAINS ? (domains.data[0]?.id ?? "") : domainId}
          link={editing}
        />
      )}
      <LinkDetailsSheet link={details} onOpenChange={(open) => !open && setDetails(undefined)} onEdit={openEdit} />
      <ConfirmDialog
        open={!!deleting}
        onOpenChange={(open) => !open && setDeleting(undefined)}
        title="Delete this link?"
        description={deleting ? `${deleting.shortenedUrl} will stop working immediately. This cannot be undone.` : ""}
        confirmLabel="Delete"
        pending={remove.isPending}
        onConfirm={() => deleting && remove.mutate(deleting)}
      />
    </>
  )
}

function SortHeader({ label, property, sort, onSort }: { label: string; property: SortProperty; sort: Sort; onSort: (sort: Sort) => void }) {
  const active = sort.by === property
  const Icon = !active ? ChevronsUpDown : sort.direction === "asc" ? ArrowUp : ArrowDown
  return (
    <button
      type="button"
      className="inline-flex items-center gap-1 uppercase hover:text-foreground data-[active=true]:text-foreground"
      data-active={active}
      onClick={() => onSort({ by: property, direction: active && sort.direction === "desc" ? "asc" : "desc" })}
    >
      {label}
      <Icon className="size-3" />
    </button>
  )
}

function RelativeDate({ iso }: { iso: string }) {
  const date = new Date(iso)
  return (
    <time dateTime={iso} title={date.toLocaleString()} className="font-mono text-xs whitespace-nowrap text-muted-foreground">
      {formatDistanceToNowStrict(date, { addSuffix: true })}
    </time>
  )
}

function LinkActions({ link, onEdit, onDelete }: { link: Link; onEdit: () => void; onDelete: () => void }) {
  return (
    <div onClick={(e) => e.stopPropagation()} className="flex justify-end">
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="ghost" size="icon" className="size-8" aria-label={`Actions for /${link.slug}`}>
            <MoreHorizontal />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem asChild>
            <a href={link.shortenedUrl} target="_blank" rel="noreferrer">
              <Link2 /> Open short link
            </a>
          </DropdownMenuItem>
          <DropdownMenuItem asChild>
            <a href={link.targetUrl} target="_blank" rel="noreferrer">
              <ExternalLink /> Open destination
            </a>
          </DropdownMenuItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem onSelect={onEdit}>
            <Pencil /> Edit
          </DropdownMenuItem>
          <DropdownMenuItem variant="destructive" onSelect={onDelete}>
            <Trash2 /> Delete
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  )
}
