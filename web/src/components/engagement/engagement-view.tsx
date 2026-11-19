"use client"

import { useState } from "react"
import Link from "next/link"
import { usePathname, useRouter, useSearchParams } from "next/navigation"
import { keepPreviousData, useQuery } from "@tanstack/react-query"
import { format } from "date-fns"
import { ArrowLeft, ChartColumn, ExternalLink, Table2 } from "lucide-react"
import { api, withQuery } from "@/lib/client"
import { ENGAGEMENT_RANGES, PERIOD_NAMES, busiest, formatCount, parseRange, periodLabel } from "@/lib/engagement"
import type { EngagementRange, EngagementReport } from "@/lib/types"
import { useDomains } from "@/hooks/use-lika"
import { ALL_DOMAINS, useDomainSelection } from "@/hooks/use-domain-selection"
import { CopyButton } from "@/components/app/copy-button"
import { EmptyState } from "@/components/app/empty-state"
import { PageHeader } from "@/components/app/page-header"
import { EngagementChart } from "@/components/engagement/engagement-chart"
import { DomainSwitcher } from "@/components/links/domain-switcher"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"

/**
 * Visits of the short links: of every domain, of one domain, or of one link (`link` parameter of the URL).
 */
export function EngagementView() {
  const router = useRouter()
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const domains = useDomains()
  const range = parseRange(searchParams.get("range"))
  const linkId = searchParams.get("link") ?? undefined
  const rangeParam = searchParams.get("range") ?? undefined
  const { domainId, selectDomain } = useDomainSelection(domains.data, { range: rangeParam })
  const domainParam = domainId === ALL_DOMAINS ? undefined : domainId
  const [view, setView] = useState<"chart" | "table">("chart")

  const report = useQuery({
    queryKey: ["engagement", linkId ?? domainId, range],
    queryFn: () => api<EngagementReport>("/api/engagement", { query: linkId ? { linkId, range } : { domainId: domainParam, range } }),
    enabled: domains.isSuccess,
    // The previous report stays in place while the next one loads
    placeholderData: keepPreviousData,
    // Live
    refetchInterval: range === "24h" ? 30_000 : false,
  })

  const go = (params: { range?: EngagementRange; link?: string }) =>
    router.replace(withQuery(pathname, { domain: domainParam, range: params.range ?? rangeParam, link: "link" in params ? params.link : linkId }))

  const data = report.data
  const link = linkId ? data?.link : undefined
  const period = PERIOD_NAMES[data?.resolution ?? "day"]
  const peak = data && busiest(data.series)
  const title = ENGAGEMENT_RANGES.find((r) => r.id === range)!.title

  return (
    <>
      <PageHeader title="Engagement" description={link ? `Visits of ${link.shortenedUrl}` : "Visits of your short links"}>
        {!linkId && domains.data && domains.data.length > 0 && <DomainSwitcher domains={domains.data} value={domainId} onChange={selectDomain} />}
      </PageHeader>

      <div className="space-y-4 p-4 md:p-6">
        {/* One row of filters, scoping everything below */}
        <div className="flex flex-wrap items-center gap-2">
          {linkId && (
            <Button variant="outline" size="sm" onClick={() => go({ link: undefined })}>
              <ArrowLeft /> All links
            </Button>
          )}
          <div className="flex flex-wrap gap-1" role="tablist" aria-label="Period">
            {ENGAGEMENT_RANGES.map((r) => (
              <Button key={r.id} role="tab" aria-selected={range === r.id} variant={range === r.id ? "secondary" : "ghost"} size="sm" onClick={() => go({ range: r.id })}>
                {r.label}
                {r.id === "24h" && <span className="size-1.5 rounded-full bg-brand" title="Live" />}
              </Button>
            ))}
          </div>
        </div>

        {report.isError && <EmptyState title={linkId ? "Link not found" : "Could not load the engagement"} description={report.error.message} />}
        {report.isPending && <Skeleton className="h-80 w-full" />}

        {data && (
          <div className="space-y-4 transition-opacity data-[stale=true]:opacity-60" data-stale={report.isPlaceholderData}>
            {link && (
              <Card>
                <CardContent className="flex flex-wrap items-center gap-x-6 gap-y-2">
                  <div className="min-w-0">
                    <p className="flex items-center gap-1 font-mono text-sm font-medium">
                      <span className="truncate">{link.shortenedUrl}</span>
                      <CopyButton value={link.shortenedUrl} label="Copy short URL" />
                    </p>
                    <a href={link.targetUrl} target="_blank" rel="noreferrer" className="inline-flex max-w-full items-center gap-1 font-mono text-xs text-muted-foreground hover:text-foreground">
                      <span className="truncate">{link.targetUrl}</span>
                      <ExternalLink className="size-3 shrink-0" />
                    </a>
                  </div>
                  <p className="ml-auto font-mono text-xs text-muted-foreground">created {format(new Date(link.createdDateTime), "PP")}</p>
                </CardContent>
              </Card>
            )}

            <div className="grid gap-4 sm:grid-cols-3">
              <Stat label="Visits" value={formatCount(data.total)} detail={title.split(",")[0].toLowerCase()} />
              <Stat label={`Busiest ${period}`} value={peak ? formatCount(peak.count) : "–"} detail={peak ? periodLabel(peak, data.resolution) : "no visit yet"} />
              <Stat label={`Average per ${period}`} value={formatCount(Math.round((data.total / data.series.length) * 10) / 10)} detail={`over ${data.series.length} ${period}s`} />
            </div>

            <Card>
              <CardHeader className="flex flex-row items-start justify-between gap-4">
                <div className="space-y-1.5">
                  <CardTitle>Visits per {period}</CardTitle>
                  <CardDescription>
                    {title}
                    {data.resolution === "hour" ? ", in your time zone. Updated every 30 seconds." : ". Days and weeks are in UTC."}
                  </CardDescription>
                </div>
                <div className="flex gap-1" role="tablist" aria-label="View">
                  <Button role="tab" aria-selected={view === "chart"} aria-label="Chart" variant={view === "chart" ? "secondary" : "ghost"} size="icon" className="size-8" onClick={() => setView("chart")}>
                    <ChartColumn />
                  </Button>
                  <Button role="tab" aria-selected={view === "table"} aria-label="Table" variant={view === "table" ? "secondary" : "ghost"} size="icon" className="size-8" onClick={() => setView("table")}>
                    <Table2 />
                  </Button>
                </div>
              </CardHeader>
              <CardContent>
                {view === "chart" ? (
                  <EngagementChart series={data.series} resolution={data.resolution} />
                ) : (
                  <div className="max-h-72 overflow-y-auto rounded-md border">
                    <table className="w-full text-sm" aria-label={`Visits per ${period}`}>
                      <thead className="sticky top-0 bg-muted text-left font-mono text-[11px] tracking-wider text-muted-foreground uppercase">
                        <tr>
                          <th className="p-2 pl-3 font-normal">{period}</th>
                          <th className="p-2 pr-3 text-right font-normal">Visits</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y">
                        {[...data.series].reverse().map((point) => (
                          <tr key={point.start}>
                            <td className="p-2 pl-3">{periodLabel(point, data.resolution)}</td>
                            <td className="p-2 pr-3 text-right font-mono tabular-nums">{formatCount(point.count)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </CardContent>
            </Card>

            {!linkId && (
              <div className="grid items-start gap-4 lg:grid-cols-3">
                <Card className="lg:col-span-2">
                  <CardHeader>
                    <CardTitle>Most visited links</CardTitle>
                    <CardDescription>Select a link to see its visits.</CardDescription>
                  </CardHeader>
                  <CardContent>
                    {data.links.length === 0 ? (
                      <EmptyState title="No visit yet" description="Visits show here as soon as someone follows a short link." />
                    ) : (
                      <ol className="space-y-1" aria-label="Most visited links">
                        {data.links.map(({ link: top, count }) => (
                          <li key={top.id}>
                            <button
                              type="button"
                              className="grid w-full grid-cols-[1fr_auto] items-center gap-x-4 gap-y-1 rounded-md px-2 py-1.5 text-left hover:bg-muted"
                              onClick={() => go({ link: top.id })}
                            >
                              <span className="min-w-0">
                                <span className="block truncate font-mono text-sm font-medium">
                                  /{top.slug} <span className="font-normal text-muted-foreground">{top.host}</span>
                                </span>
                                <span className="block truncate font-mono text-xs text-muted-foreground">{top.targetUrl}</span>
                              </span>
                              <span className="font-mono text-sm tabular-nums">{formatCount(count)}</span>
                              <Share value={count} of={data.links[0].count} />
                            </button>
                          </li>
                        ))}
                      </ol>
                    )}
                  </CardContent>
                </Card>

                <Card>
                  <CardHeader>
                    <CardTitle>Domains</CardTitle>
                    <CardDescription>Visits per domain.</CardDescription>
                  </CardHeader>
                  <CardContent>
                    <ul className="space-y-1" aria-label="Visits per domain">
                      {data.domains.map((domain) => (
                        <li key={domain.domainId}>
                          <button
                            type="button"
                            className="grid w-full grid-cols-[1fr_auto] items-center gap-x-4 gap-y-1 rounded-md px-2 py-1.5 text-left hover:bg-muted disabled:hover:bg-transparent"
                            disabled={domainId === domain.domainId}
                            onClick={() => selectDomain(domain.domainId)}
                          >
                            <span className="truncate font-mono text-sm">{domain.host}</span>
                            <span className="font-mono text-sm tabular-nums">{formatCount(domain.count)}</span>
                            <Share value={domain.count} of={Math.max(1, data.domains[0]?.count ?? 0)} />
                          </button>
                        </li>
                      ))}
                    </ul>
                  </CardContent>
                </Card>
              </div>
            )}

            <p className="text-xs text-muted-foreground">
              {data.trackedSince && `Visits are counted since ${format(new Date(data.trackedSince), "PP")}. `}
              They are kept per hour for 24 hours, then per day until the end of the following month, then per week, for a year.{" "}
              {linkId ? (
                <Link href="/links" className="underline-offset-4 hover:underline">
                  Back to links
                </Link>
              ) : null}
            </p>
          </div>
        )}
      </div>
    </>
  )
}

function Stat({ label, value, detail }: { label: string; value: string; detail: string }) {
  return (
    <Card>
      <CardContent className="space-y-1">
        <p className="font-mono text-[11px] tracking-wider text-muted-foreground uppercase">{label}</p>
        <p className="text-2xl font-semibold tracking-tight tabular-nums">{value}</p>
        <p className="truncate text-xs text-muted-foreground">{detail}</p>
      </CardContent>
    </Card>
  )
}

/** Thin bar comparing a count to the largest of its list */
function Share({ value, of }: { value: number; of: number }) {
  return (
    <span className="col-span-2 block h-1 rounded-full bg-muted" aria-hidden>
      <span className="block h-1 rounded-full bg-chart-visits" style={{ width: `${of > 0 ? (value / of) * 100 : 0}%` }} />
    </span>
  )
}
