/**
 * Fails fast at startup when the container is misconfigured, rather than on the first request.
 */
export async function register() {
  if (process.env.NEXT_RUNTIME === "nodejs" && process.env.NEXT_PHASE !== "phase-production-build") {
    const { checkConfiguration } = await import("@/lib/startup-check")
    checkConfiguration()
  }
}
