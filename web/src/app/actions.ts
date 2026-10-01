"use server"

import { headers } from "next/headers"
import { redirect } from "next/navigation"
import { clearSession, saveSession, type Session } from "@/lib/session"
import { likaFetch } from "@/lib/lika-api"
import { isSecureRequest } from "@/lib/session-cookie"
import type { Account } from "@/lib/types"

export type SignInState = { error?: string }

export async function signIn(_: SignInState, formData: FormData): Promise<SignInState> {
  const token = String(formData.get("token") ?? "").trim()
  if (!token) {
    return { error: "Paste your API key." }
  }

  let session: Session
  try {
    const domains = await likaFetch("/api/domains", token)
    if (domains.status === 401) {
      return { error: "This key is not valid." }
    }
    if (domains.status === 403) {
      return { error: "This is a read-only key. Sign in with the read-write key of the account." }
    }
    if (!domains.ok) {
      return { error: `The API answered ${domains.status}. Try again later.` }
    }
    // Admin keys can list accounts, and find their own
    const accounts = await likaFetch("/api/admin/accounts", token)
    const admin = accounts.ok
    const self = admin ? ((await accounts.json()) as Account[]).find((a) => a.defaultAccessToken === token) : undefined
    session = { token, admin, accountId: self?.id, accountName: self?.name }
  } catch (e) {
    console.error("Sign-in failed", e)
    return { error: "The Lika API is unreachable." }
  }

  await saveSession(session, isSecureRequest(await headers()))
  redirect("/links")
}

export async function signOut() {
  await clearSession()
  redirect("/signin")
}
