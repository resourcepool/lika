"use client"

import { useState } from "react"
import { axisLabel, formatCount, labelledIndexes, periodLabel, valueAxis } from "@/lib/engagement"
import type { EngagementPoint, EngagementResolution } from "@/lib/types"

/**
 * Visits per period, as columns. One series: the card title names it, and each column tells its period
 * and visits on hover. The table view of the page gives the same values without a pointer.
 */
export function EngagementChart({ series, resolution }: { series: EngagementPoint[]; resolution: EngagementResolution }) {
  const [hovered, setHovered] = useState<number>()
  const axis = valueAxis(Math.max(0, ...series.map((p) => p.count)))
  const labelled = labelledIndexes(series.length, series.length > 30 ? 7 : 8)
  const sparse = new Set([...labelled].sort((a, b) => b - a).filter((_, rank) => rank % 2 === 0))
  const point = hovered === undefined ? undefined : series[hovered]
  const total = series.reduce((sum, p) => sum + p.count, 0)

  return (
    <div
      className="grid grid-cols-[auto_1fr] gap-x-2"
      role="img"
      aria-label={`${formatCount(total)} visits over ${series.length} ${resolution}s. The table view lists them.`}
    >
      {/* Value axis: ticks from the top down */}
      <div className="relative h-56 w-8" aria-hidden>
        {axis.ticks.map((tick) => (
          <span
            key={tick}
            className="absolute right-0 translate-y-1/2 font-mono text-[10px] leading-none text-muted-foreground tabular-nums"
            style={{ bottom: `${(tick / axis.top) * 100}%` }}
          >
            {formatCount(tick)}
          </span>
        ))}
      </div>

      <div className="relative h-56" onPointerLeave={() => setHovered(undefined)}>
        {axis.ticks.map((tick) => (
          <div key={tick} className="absolute inset-x-0 border-t border-border" style={{ bottom: `${(tick / axis.top) * 100}%` }} aria-hidden />
        ))}
        <div className="absolute inset-0 flex items-stretch">
          {series.map((p, index) => (
            // The whole slot is the hover target, not only the column
            <div
              key={p.start}
              className="flex min-w-0 flex-1 items-end justify-center px-px data-[hovered=true]:bg-foreground/5"
              data-hovered={hovered === index}
              onPointerEnter={() => setHovered(index)}
              onPointerDown={() => setHovered(index)}
            >
              <div
                className="w-full max-w-6 rounded-t-[4px] bg-chart-visits transition-[filter] data-[hovered=true]:brightness-110"
                data-hovered={hovered === index}
                style={{ height: `${(p.count / axis.top) * 100}%`, minHeight: p.count > 0 ? 2 : 0 }}
              />
            </div>
          ))}
        </div>
        {point && hovered !== undefined && (
          <div
            className="pointer-events-none absolute top-0 z-10 w-max max-w-56 -translate-y-2 rounded-md border bg-popover px-2.5 py-1.5 text-popover-foreground shadow-md"
            style={
              hovered < series.length / 2
                ? { left: `${((hovered + 0.5) / series.length) * 100}%`, marginLeft: 8 }
                : { right: `${(1 - (hovered + 0.5) / series.length) * 100}%`, marginRight: 8 }
            }
          >
            <p className="text-sm font-semibold tabular-nums">
              {formatCount(point.count)} <span className="font-normal text-muted-foreground">visit{point.count === 1 ? "" : "s"}</span>
            </p>
            <p className="text-xs text-muted-foreground">{periodLabel(point, resolution)}</p>
          </div>
        )}
      </div>

      <div />
      <div className="relative mt-1.5 h-4" aria-hidden>
        {series.map(
          (p, index) =>
            labelled.has(index) && (
              <span
                key={p.start}
                // Every other label on narrow screens, keeping the last one
                data-sparse={sparse.has(index)}
                className="absolute -translate-x-1/2 font-mono text-[10px] leading-none whitespace-nowrap text-muted-foreground max-sm:data-[sparse=false]:hidden"
                style={{ left: `${((index + 0.5) / series.length) * 100}%` }}
              >
                {axisLabel(p, resolution)}
              </span>
            ),
        )}
      </div>
    </div>
  )
}
