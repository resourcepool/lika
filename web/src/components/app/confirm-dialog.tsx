"use client"

import { Loader2 } from "lucide-react"
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import { Button } from "@/components/ui/button"

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description: React.ReactNode
  confirmLabel: string
  onConfirm: () => void
  pending?: boolean
  /** Blocks confirmation, e.g. until a name is typed */
  disabled?: boolean
  destructive?: boolean
  children?: React.ReactNode
}

export function ConfirmDialog({ open, onOpenChange, title, description, confirmLabel, onConfirm, pending, disabled, destructive = true, children }: Props) {
  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{title}</AlertDialogTitle>
          <AlertDialogDescription asChild>
            <div>{description}</div>
          </AlertDialogDescription>
        </AlertDialogHeader>
        {children}
        <AlertDialogFooter>
          <AlertDialogCancel>Cancel</AlertDialogCancel>
          {/* Not AlertDialogAction: the dialog stays open until the request succeeds */}
          <Button variant={destructive ? "destructive" : "default"} disabled={pending || disabled} onClick={onConfirm}>
            {pending && <Loader2 className="animate-spin" />}
            {confirmLabel}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
