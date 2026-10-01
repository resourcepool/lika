import type { Metadata } from "next"
import { redirect } from "next/navigation"
import { getSession } from "@/lib/session"
import { SignInForm } from "@/app/signin/sign-in-form"
import { Logo } from "@/components/app/logo"

export const metadata: Metadata = { title: "Sign in" }

export default async function SignInPage({ searchParams }: PageProps<"/signin">) {
  const { expired } = await searchParams
  // After a revocation the cookie may still be there, holding the dead key
  if (expired !== "1" && (await getSession())) {
    redirect("/links")
  }
  return (
    <main className="relative flex min-h-screen items-center justify-center overflow-hidden px-4">
      <div className="bg-grid pointer-events-none absolute inset-0" />
      <div className="pointer-events-none absolute -top-40 left-1/2 size-[480px] -translate-x-1/2 rounded-full bg-brand/10 blur-3xl" />
      <div className="relative w-full max-w-sm">
        <div className="mb-8 flex flex-col items-center gap-3 text-center">
          <Logo className="size-10" />
          <h1 className="text-2xl font-semibold tracking-tight">Sign in to Lika</h1>
          <p className="text-sm text-muted-foreground">Paste the read-write API key of your account. You stay signed in on this browser.</p>
        </div>
        <SignInForm expired={expired === "1"} />
        <p className="mt-6 text-center font-mono text-xs text-muted-foreground">
          Keys are created by an admin under <span className="text-foreground">API Keys</span>.
        </p>
      </div>
    </main>
  )
}
