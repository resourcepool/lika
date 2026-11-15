"use server"

import { headers } from "next/headers"
import { redirect } from "next/navigation"
import { PASSWORD_MIN_LENGTH, authConfig, needsRenewal, renewSession } from "@/lib/auth"
import { clearSession, getSession, saveSession, sessionFromTokens, type Session } from "@/lib/session"
import { likaJson, type ApiFailure } from "@/lib/lika-api"
import { isSecureRequest } from "@/lib/session-cookie"
import type { AuthTokens } from "@/lib/types"

export type FormState = { error?: string; done?: boolean }

const field = (formData: FormData, name: string) => String(formData.get(name) ?? "").trim()

/** Same-site path only, to avoid open redirects */
function safeNext(value: string): string {
  return value.startsWith("/") && !value.startsWith("//") ? value : "/links"
}

async function open(tokens: AuthTokens) {
  await saveSession(sessionFromTokens(tokens), isSecureRequest(await headers()))
}

function failure(r: ApiFailure, fallback?: string): FormState {
  if (r.status >= 500) {
    return { error: `The Lika API answered ${r.status}. Try again later.` }
  }
  return { error: fallback ?? r.message }
}

function checkNewPassword(password: string, confirmation: string): string | undefined {
  if (password.length < PASSWORD_MIN_LENGTH) return `Use at least ${PASSWORD_MIN_LENGTH} characters.`
  if (password !== confirmation) return "The passwords do not match."
}

/** With an external identity provider, an existing account keeps its password: none is typed */
function checkOptionalPassword(password: string, confirmation: string): string | undefined {
  return password || confirmation ? checkNewPassword(password, confirmation) : undefined
}

/**
 * Without tokens, the user now signs in with the identity provider: directly with single sign-on only,
 * from the sign-in page otherwise.
 */
async function openOrSignIn(tokens: AuthTokens | undefined, next: string): Promise<never> {
  if (tokens) {
    await open(tokens)
    redirect(next)
  }
  const config = await authConfig()
  redirect(config?.sso && !config.passwordSignIn ? `/auth/sso?next=${encodeURIComponent(next)}` : "/signin?joined=1")
}

async function unreachable(e: unknown): Promise<FormState> {
  console.error("Lika API call failed", e)
  return { error: "The Lika API is unreachable." }
}

// --- Sign-in

export async function signIn(_: FormState, formData: FormData): Promise<FormState> {
  const email = field(formData, "email")
  const password = String(formData.get("password") ?? "")
  if (!email || !password) {
    return { error: "Enter your email and password." }
  }
  try {
    const r = await likaJson<AuthTokens>("/api/auth/login", { body: { email, password } })
    if (!r.ok) return failure(r)
    await open(r.data)
  } catch (e) {
    return unreachable(e)
  }
  redirect(safeNext(field(formData, "next")))
}

export async function signOut() {
  const session = await getSession()
  if (session) {
    // Best effort: the cookie goes anyway
    await likaJson("/api/auth/logout", { body: { refreshToken: session.refreshToken } }).catch(() => undefined)
  }
  await clearSession()
  redirect("/signin")
}

// --- Links received by email or from an admin

export async function setup(_: FormState, formData: FormData): Promise<FormState> {
  const password = String(formData.get("password") ?? "")
  const invalid = checkOptionalPassword(password, String(formData.get("confirmation") ?? ""))
  if (invalid) return { error: invalid }
  let tokens: AuthTokens | undefined
  try {
    const r = await likaJson<AuthTokens>("/api/auth/setup", {
      body: { setupToken: field(formData, "setupToken"), email: field(formData, "email"), name: field(formData, "name"), password },
    })
    if (!r.ok) return failure(r)
    tokens = r.data
  } catch (e) {
    return unreachable(e)
  }
  return openOrSignIn(tokens, "/admin/domains")
}

export async function acceptInvitation(token: string, _: FormState, formData: FormData): Promise<FormState> {
  const password = String(formData.get("password") ?? "")
  const invalid = checkOptionalPassword(password, String(formData.get("confirmation") ?? ""))
  if (invalid) return { error: invalid }
  let tokens: AuthTokens | undefined
  try {
    const r = await likaJson<AuthTokens>(`/api/auth/invitations/${encodeURIComponent(token)}/accept`, { body: { name: field(formData, "name"), password } })
    if (!r.ok) return failure(r)
    tokens = r.data
  } catch (e) {
    return unreachable(e)
  }
  return openOrSignIn(tokens, "/links")
}

export async function forgotPassword(_: FormState, formData: FormData): Promise<FormState> {
  const email = field(formData, "email")
  if (!email) return { error: "Enter your email." }
  try {
    const r = await likaJson("/api/auth/password/forgot", { body: { email } })
    if (!r.ok) return failure(r)
  } catch (e) {
    return unreachable(e)
  }
  return { done: true }
}

export async function resetPassword(token: string, _: FormState, formData: FormData): Promise<FormState> {
  const password = String(formData.get("password") ?? "")
  const invalid = checkNewPassword(password, String(formData.get("confirmation") ?? ""))
  if (invalid) return { error: invalid }
  try {
    const r = await likaJson("/api/auth/password/reset", { body: { token, password } })
    if (!r.ok) return failure(r)
  } catch (e) {
    return unreachable(e)
  }
  // Every session of the user was signed out, this browser's included
  await clearSession()
  redirect("/signin?reset=1")
}

// --- The signed-in user

async function currentSession(): Promise<Session> {
  let session = await getSession()
  if (session && needsRenewal(session)) {
    session = await renewSession(session)
  }
  if (!session) {
    await clearSession()
    redirect("/signin?expired=1")
  }
  return session
}

export async function changePassword(_: FormState, formData: FormData): Promise<FormState> {
  const password = String(formData.get("password") ?? "")
  const invalid = checkNewPassword(password, String(formData.get("confirmation") ?? ""))
  if (invalid) return { error: invalid }
  const session = await currentSession()
  try {
    const r = await likaJson<AuthTokens>("/api/me/password", {
      token: session.accessToken,
      body: { currentPassword: String(formData.get("currentPassword") ?? ""), newPassword: password },
    })
    if (!r.ok) return failure(r)
    // The other sessions are signed out; this one continues with the new tokens
    await open(r.data)
  } catch (e) {
    return unreachable(e)
  }
  return { done: true }
}

export async function signOutEverywhere() {
  const session = await currentSession()
  await likaJson("/api/me/sessions/revoke", { token: session.accessToken, method: "POST" }).catch(() => undefined)
  await clearSession()
  redirect("/signin")
}
