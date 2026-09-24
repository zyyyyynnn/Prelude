import { useSearchParams } from 'react-router'
import { InterviewSession } from './components/InterviewSession'
import { InterviewSetup } from './components/InterviewSetup'

export function InterviewPage() {
  const [params] = useSearchParams()
  const sessionId = Number(params.get('session')) || null
  return sessionId ? <InterviewSession key={sessionId} sessionId={sessionId} /> : <InterviewSetup />
}
