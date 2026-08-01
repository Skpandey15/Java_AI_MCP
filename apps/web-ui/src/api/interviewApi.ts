import { keycloak } from '../auth/keycloak'
import { appConfig } from '../config'

const apiBaseUrl = appConfig.apiBaseUrl

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
  questionComposition: {
    mcqSingle: number; mcqMultiple: number; shortText: number; longText: number
  }
  passingPercentage: number
  knowledgeCollectionId?: string
  status: string
  createdAt: string
}
export type KnowledgeCollection = {
  id: string; name: string; description: string; createdAt: string
}
export type KnowledgeDocument = { id: string; fileName: string; status: string }
export type QuestionCitation = {
  chunkId: string; documentId: string; fileName: string; chunkIndex: number
  excerpt: string; score: number
}

export type QuestionType = 'MCQ_SINGLE' | 'MCQ_MULTIPLE' | 'SHORT_TEXT' | 'LONG_TEXT'
export type Question = {
  id: string; order: number; prompt: string; maxScore: number
  type: QuestionType; options: string[]
}
export type AdminQuestion = Question & {
  correctAnswers: string[]; source: string; citations: QuestionCitation[]
}
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

export type SubmissionSummary = {
  sessionId: string; interviewTitle: string; candidateName: string; candidateEmail: string
  submittedAt: string; reviewStatus: string; totalScore?: number; maxScore: number
  percentage?: number; outcome?: 'PASSED' | 'NOT_SELECTED'
}
export type PageResponse<T> = {
  content: T[]; page: number; size: number; totalElements: number; totalPages: number
}
export type ReviewQuestion = {
  questionId: string; answerId?: string; order: number; type: QuestionType; prompt: string
  options: string[]; correctAnswers: string[]; content: string; maxScore: number
  awardedScore?: number; feedback?: string; autoScored: boolean; citations: QuestionCitation[]
}
export type SubmissionDetail = {
  sessionId: string; interviewTitle: string; candidateName: string; candidateEmail: string
  submittedAt: string; reviewStatus: string; objectiveScore: number; totalScore?: number
  maxScore: number; passingPercentage: number; percentage?: number
  outcome?: 'PASSED' | 'NOT_SELECTED'; feedback?: string; questions: ReviewQuestion[]
}
export type CandidateResult = {
  sessionId: string; interviewTitle: string; submittedAt: string; reviewStatus: string
  totalScore?: number; maxScore: number; passingPercentage: number; percentage?: number
  outcome?: 'PASSED' | 'NOT_SELECTED'; feedback?: string
  answers: Array<{ order: number; type: QuestionType; prompt: string; content: string
    maxScore: number; awardedScore?: number; feedback?: string }>
  coachingFeedback?: string
}
export type AnswerSuggestion = { answerId: string; suggestedScore: number; confidence: number; justification: string }
export type CoachingResponse = { status: string; leakageSafe: boolean; leakageFlags: string[]; content: string }

export type Assignment = {
  id: string
  interviewId: string
  interviewTitle: string
  candidateId: string
  startsAt: string
  endsAt: string
  maxAttempts: number
  status: string
  sessionId?: string
  sessionState?: string
  reviewStatus?: string
}

export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message) }
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
  if (!response.ok) {
    const payload = await response.json().catch(() => ({})) as {
      detail?: string; message?: string; requestId?: string; errors?: Record<string, string>
    }
    const fieldError = payload.errors && Object.entries(payload.errors)[0]
    const message = fieldError
      ? `${fieldError[0]}: ${fieldError[1]}`
      : payload.detail ?? payload.message ?? `Request failed with status ${response.status}`
    throw new ApiError(response.status, payload.requestId ? `${message} (Request ID: ${payload.requestId})` : message)
  }
  if (response.status === 204) return undefined as T
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
  listKnowledgeCollections: () =>
    request<KnowledgeCollection[]>('/api/v1/knowledge/collections'),
  createCollection: (name: string, description: string) =>
    request<KnowledgeCollection>('/api/v1/knowledge/collections', {
      method: 'POST', body: JSON.stringify({ name, description }) }),
  listCollectionDocuments: (collectionId: string) =>
    request<KnowledgeDocument[]>(`/api/v1/knowledge/collections/${collectionId}/documents`),
  addDocument: (collectionId: string, fileName: string, content: string) =>
    request<KnowledgeDocument>(`/api/v1/knowledge/collections/${collectionId}/documents`, {
      method: 'POST', body: JSON.stringify({ fileName, mediaType: 'text/markdown', content }) }),
  ingestDocument: (documentId: string) =>
    request<KnowledgeDocument>(`/api/v1/knowledge/documents/${documentId}:ingest`, { method: 'POST' }),
  uploadDocument: async (collectionId: string, file: File): Promise<KnowledgeDocument> => {
    await keycloak.updateToken(30)
    const form = new FormData()
    form.append('file', file)
    const res = await fetch(`${apiBaseUrl}/api/v1/knowledge/collections/${collectionId}/documents:upload`, {
      method: 'POST', headers: { Authorization: `Bearer ${keycloak.token}` }, body: form,
    })
    if (!res.ok) {
      const p = await res.json().catch(() => ({})) as { detail?: string; message?: string }
      throw new ApiError(res.status, p.detail ?? p.message ?? `Upload failed (${res.status})`)
    }
    return res.json() as Promise<KnowledgeDocument>
  },
  candidates: () => request<Profile[]>('/api/v1/candidates'),
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
  listAssignments: (id: string) => request<Assignment[]>(
    `/api/v1/interviews/${id}/assignments`,
  ),
  unassign: (id: string, assignmentId: string) => request<void>(
    `/api/v1/interviews/${id}/assignments/${assignmentId}`, { method: 'DELETE' },
  ),
  archiveInterview: (id: string) => request<void>(
    `/api/v1/interviews/${id}`, { method: 'DELETE' },
  ),
  candidateAssignments: () => request<Assignment[]>('/api/v1/candidate/interviews'),
  addQuestion: (interviewId: string, body: object) => request<AdminQuestion>(
    `/api/v1/interviews/${interviewId}/questions`,
    { method: 'POST', body: JSON.stringify(body) },
  ),
  generateQuestions: (interviewId: string) => request<Question[]>(
    `/api/v1/interviews/${interviewId}/questions:generate`,
    { method: 'POST', headers: { 'Idempotency-Key': crypto.randomUUID() } },
  ),
  listQuestions: (interviewId: string) => request<AdminQuestion[]>(
    `/api/v1/interviews/${interviewId}/questions`,
  ),
  updateQuestion: (interviewId: string, questionId: string, body: object) =>
    request<AdminQuestion>(`/api/v1/interviews/${interviewId}/questions/${questionId}`, {
      method: 'PUT', body: JSON.stringify(body),
    }),
  deleteQuestion: (interviewId: string, questionId: string) =>
    request<void>(`/api/v1/interviews/${interviewId}/questions/${questionId}`, {
      method: 'DELETE',
    }),
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
  submissions: (page = 0, size = 20) => request<PageResponse<SubmissionSummary>>(
    `/api/v1/interviewer/submissions?page=${page}&size=${size}`,
  ),
  submission: (sessionId: string) => request<SubmissionDetail>(
    `/api/v1/interviewer/submissions/${sessionId}`,
  ),
  scoreAnswer: (sessionId: string, answerId: string, score: number, feedback: string) =>
    request<SubmissionDetail>(`/api/v1/interviewer/submissions/${sessionId}/answers/${answerId}/score`, {
      method: 'PUT', body: JSON.stringify({ score, feedback }),
    }),
  finalizeReview: (sessionId: string, feedback: string) =>
    request<SubmissionDetail>(`/api/v1/interviewer/submissions/${sessionId}/finalize`, {
      method: 'POST', body: JSON.stringify({ feedback }),
    }),
  aiSuggest: (sessionId: string) => request<AnswerSuggestion[]>(
    `/api/v1/interviewer/submissions/${sessionId}/ai-suggest`, { method: 'POST' }),
  draftCoaching: (sessionId: string) => request<CoachingResponse>(
    `/api/v1/interviewer/submissions/${sessionId}/coaching`, { method: 'POST' }),
  approveCoaching: (sessionId: string) => request<CoachingResponse>(
    `/api/v1/interviewer/submissions/${sessionId}/coaching:approve`, { method: 'POST' }),
  candidateResult: (sessionId: string) => request<CandidateResult>(
    `/api/v1/candidate/sessions/${sessionId}/result`,
  ),
  submitSession: (sessionId: string) => request<InterviewSession>(
    `/api/v1/candidate/sessions/${sessionId}/submit`, { method: 'POST' },
  ),
}
