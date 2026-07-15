<script setup lang="ts">
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/shared/ui/alert-dialog'
import { useConfirmDialog } from '@/shared/ui/confirm-dialog/useConfirmDialog'

const { isOpen, options, handleConfirm, handleCancel, handleOpenChange } = useConfirmDialog()
</script>

<template>
  <AlertDialog :open="isOpen" @update:open="handleOpenChange">
    <AlertDialogContent>
      <AlertDialogHeader>
        <AlertDialogTitle>{{ options.title }}</AlertDialogTitle>
        <AlertDialogDescription>{{ options.message }}</AlertDialogDescription>
      </AlertDialogHeader>
      <AlertDialogFooter>
        <AlertDialogCancel @click="handleCancel">
          {{ options.cancelText || '取消' }}
        </AlertDialogCancel>
        <AlertDialogAction
          @click="handleConfirm"
          :class="{
            'bg-destructive text-destructive-foreground hover:bg-destructive/90':
              options.variant === 'destructive',
          }"
        >
          {{ options.confirmText || '确定' }}
        </AlertDialogAction>
      </AlertDialogFooter>
    </AlertDialogContent>
  </AlertDialog>
</template>
