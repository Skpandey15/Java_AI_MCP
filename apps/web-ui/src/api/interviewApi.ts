import { keycloak } from '../auth/keycloak'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export type Profile = {
  id: string
  email: string
  displayName: string
  role: string
  status: string
}

export type Interview = {
  id: string
  title: string
  description: string
  skills: string[]
  difficulty: string
  questionMode: string
  durationMinutes: number
  questionCount: number
  status: string
  createdAt: string
}

export type Question = { id: string; order: number; prompt: string; maxScore: number }
export type SavedAnswer = { id: string; questionId: string; content: string; updatedAt: string; version: number }
export type InterviewSession = {
  id: string
  assignmentId: string
  state: string
  startedAt: string
  expiresAt: string
  serverTime: string
  questions: Question[]
  answers: SavedAnswer[]
}

export type Assignment = {
  id: string
  interviewId: string
  interviewTitle: string
  candidateId: string
  startsAt: string
  endsAt: string
  maxAttempts: number
  status: string
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  await keycloak.updateToken(30)
  if (!keycloak.token) throw new Error('Authentication token is unavailable')
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...init,
    headers: {
      Authorization: `Bearer ${keycloak.token}`,
      'Content-Type': 'application/json',
      ...init?.headers,
    },
  })
  if (!response.ok) throw new Error(`Request failed with status ${response.status}`)
  return response.json() as Promise<T>
}

export const interviewApi = {
  completeCandidateProfile: () => {
    const claims = keycloak.tokenParsed
    const displayName = claims?.name ?? claims?.preferred_username ?? claims?.email ?? 'Candidate'
    return request<Profile>('/api/v1/profiles/registration-complete', {
      method: 'POST', body: JSON.stringify({ displayName }),
    })
  },
  listOwned: () => request<Interview[]>('/api/v1/interviews'),
  create: (body: object) => request<Interview>('/api/v1/interviews', {
    method: 'POST', body: JSON.stringify(body),
  }),
  publish: (id: string) => request<Interview>(`/api/v1/interviews/${id}/publish`, {
    method: 'POST',
  }),
  assign: (id: string, body: object) => request<Assignment>(
    `/api/v1/interviews/${id}/assignments`,
    { method: 'POST', body: JSON.stringify(body) },
  ),
  candidateAssignments: () => request<Assignment[]>('/api/v1/candidate/interviews'),
  addQuestion: (interviewId: string, body: object) => request<Question>(
    `/api/v1/interviews/${interviewId}/questions`,
    { method: 'POST', body: JSON.stringify(body) },
  ),
  generateQuestions: (interviewId: string) => request<Question[]>(
    `/api/v1/interviews/${interviewId}/questions:generate`,
    { method: 'POST', headers: { 'Idempotency-Key': crypto.randomUUID() } },
  ),
  listQuestions: (interviewId: string) => request<Question[]>(
    `/api/v1/interviews/${interviewId}/questions`,
  ),
  startSession: (assignmentId: string) => request<InterviewSession>(
    `/api/v1/candidate/assignments/${assignmentId}/sessions`, { method: 'POST' },
  ),
  loadSession: (sessionId: string) => request<InterviewSession>(
    `/api/v1/candidate/sessions/${sessionId}`,
  ),
  saveAnswer: (sessionId: string, questionId: string, content: string, expectedVersion: number) =>
    request<SavedAnswer>(`/api/v1/candidate/sessions/${sessionId}/answers/${questionId}`, {
      method: 'PUT', body: JSON.stringify({ content, expectedVersion }),
    }),
  submitSession: (sessionId: string) => request<InterviewSession>(
    `/api/v1/candidate/sessions/${sessionId}/submit`, { method: 'POST' },
  ),
}
