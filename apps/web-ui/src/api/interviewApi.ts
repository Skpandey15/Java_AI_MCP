import { keycloak } from '../auth/keycloak'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

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
}
