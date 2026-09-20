import type { VoiceStatus } from '@/shared/ui/prompt-bar'

/** The one mapping from voice state to the words the composer shows. Recording wins:
 *  while the microphone is open the listener needs to know that, not the connection state. */
export function voiceStatusLabel(status: VoiceStatus, recording: boolean): string {
  if (recording) return '正在聆听'
  if (status === 'processing') return '正在处理'
  if (status === 'speaking') return '面试官正在回答'
  return '语音模式已连接'
}
