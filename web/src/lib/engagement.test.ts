import { describe, expect, it } from "vitest"
import { busiest, formatCount, labelledIndexes, parseRange, periodLabel, valueAxis } from "@/lib/engagement"

describe("valueAxis", () => {
  it("rounds the top to a clean number, with whole ticks", () => {
    expect(valueAxis(0)).toEqual({ top: 1, ticks: [0, 1] })
    expect(valueAxis(3)).toEqual({ top: 3, ticks: [0, 1, 2, 3] })
    expect(valueAxis(7)).toEqual({ top: 8, ticks: [0, 2, 4, 6, 8] })
    expect(valueAxis(87)).toEqual({ top: 100, ticks: [0, 25, 50, 75, 100] })
    expect(valueAxis(1284).top).toBe(1500)
    expect(valueAxis(9).ticks.every(Number.isInteger)).toBe(true)
  })
})

describe("labelledIndexes", () => {
  it("always labels the last period, and spreads the others", () => {
    expect([...labelledIndexes(24, 6)].sort((a, b) => a - b)).toEqual([3, 7, 11, 15, 19, 23])
    expect(labelledIndexes(7, 10).size).toBe(7)
    expect(labelledIndexes(52, 7).has(51)).toBe(true)
  })
})

describe("labels", () => {
  it("shows days and weeks on their UTC date, whatever the time zone", () => {
    expect(periodLabel({ start: "2026-11-16T00:00:00Z", count: 1 }, "week")).toBe("Week of Nov 16, 2026")
    expect(periodLabel({ start: "2026-11-19T00:00:00Z", count: 1 }, "day")).toBe("Thursday, Nov 19, 2026")
  })
  it("formats counts in full, compact beyond ten thousand", () => {
    expect(formatCount(1284)).toBe("1,284")
    expect(formatCount(12940)).toBe("12.9K")
  })
})

describe("busiest", () => {
  it("is the first period with the most visits, none without visits", () => {
    const series = [{ start: "a", count: 2 }, { start: "b", count: 5 }, { start: "c", count: 5 }]
    expect(busiest(series)?.start).toBe("b")
    expect(busiest([{ start: "a", count: 0 }])).toBeUndefined()
  })
})

describe("parseRange", () => {
  it("falls back to 30 days", () => {
    expect(parseRange("24h")).toBe("24h")
    expect(parseRange("1y")).toBe("30d")
    expect(parseRange(null)).toBe("30d")
  })
})
