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
export type KnowledgeCitation = {
  chunkId: string; documentId: string; fileName: string; chunkIndex: number
  content: string; score: number
}
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
export type ComposeJob = {
  jobId: string; status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'
  questionCount: number; rounds: number; error?: string
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
  modelAnswer?: string; detailedAnswer?: string
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
    maxScore: number; awardedScore?: number; feedback?: string; modelAnswer?: string }>
  coachingFeedback?: string
}
export type AdaptiveTranscriptTurn = {
  ordinal: number; skill: string; difficulty: string; source: string
  question: string; answer: string | null; score: number | null
  confidence: number | null; rationale: string | null
}
export type AdaptiveTranscript = {
  sessionId: string; adaptive: boolean; phase: string; overallScore: number | null
  turnsUsed: number; maxTurns: number; turns: AdaptiveTranscriptTurn[]
}
export type AnswerSuggestion = { answerId: string; suggestedScore: number; confidence: number; justification: string }
export type CoachingResponse = { status: string; leakageSafe: boolean; leakageFlags: string[]; content: string }
export type TopicDetails = { title: string; content: string }

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
  questionMode?: string
}

export type AdaptiveQuestion = {
  ordinal: number; skill: string; difficulty: string; prompt: string
}
export type AdaptiveView = {
  sessionId: string; phase: string; turnsUsed: number; maxTurns: number
  done: boolean; currentQuestion: AdaptiveQuestion | null
}
export type AdaptiveResult = {
  sessionId: string; interviewTitle: string; done: boolean
  overallScore: number | null; passingPercentage: number; passed: boolean
  turns: AdaptiveTranscriptTurn[]
}

export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message) }
}

async function ensureToken(): Promise<void> {
  try {
    await keycloak.updateToken(30)
  } catch {
    // Refresh token expired or the SSO session is gone — send the user to re-authenticate
    // instead of surfacing a cryptic non-Error rejection.
    void keycloak.login()
    throw new Error('Your session has expired. Redirecting you to sign in again…')
  }
  if (!keycloak.token) throw new Error('Authentication token is unavailable')
}

// fetch() rejects with a TypeError only for network-level failures — the backend being
// down, a refused/timed-out connection, DNS failure, or a blocked CORS preflight — never
// for an HTTP error status (those resolve with response.ok === false). Convert those into
// a friendly, actionable ApiError so every page shows a "service is unavailable" message
// instead of the raw browser "Failed to fetch". A deliberate AbortController cancel is
// re-thrown untouched so callers can tell it apart.
const SERVICE_DOWN_MESSAGE =
  'The service is currently unavailable. It should be back in a few minutes — please try again shortly.'

async function safeFetch(input: string, init?: RequestInit): Promise<Response> {
  try {
    return await fetch(input, init)
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ApiError(0, SERVICE_DOWN_MESSAGE)
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  await ensureToken()
  const response = await safeFetch(`${apiBaseUrl}${path}`, {
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
    await ensureToken()
    const form = new FormData()
    form.append('file', file)
    const res = await safeFetch(`${apiBaseUrl}/api/v1/knowledge/collections/${collectionId}/documents:upload`, {
      method: 'POST', headers: { Authorization: `Bearer ${keycloak.token}` }, body: form,
    })
    if (!res.ok) {
      const p = await res.json().catch(() => ({})) as { detail?: string; message?: string }
      throw new ApiError(res.status, p.detail ?? p.message ?? `Upload failed (${res.status})`)
    }
    return res.json() as Promise<KnowledgeDocument>
  },
  searchCollection: (collectionId: string, query: string, limit = 8) =>
    request<{ citations: KnowledgeCitation[] }>(
      `/api/v1/knowledge/collections/${collectionId}:search`,
      { method: 'POST', body: JSON.stringify({ query, limit }) }),
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
  suggestTopics: (technologies: string[], difficulty: string) => request<{ topics: string[] }>(
    '/api/v1/topics:suggest',
    { method: 'POST', body: JSON.stringify({ technologies, difficulty }) },
  ),
  topicDetails: (ecosystem: string, technology: string, topic: string) => request<TopicDetails>(
    '/api/v1/education/details',
    { method: 'POST', body: JSON.stringify({ ecosystem, technology, topic }) },
  ),
  generateQuestions: (interviewId: string) => request<Question[]>(
    `/api/v1/interviews/${interviewId}/questions:generate`,
    { method: 'POST', headers: { 'Idempotency-Key': crypto.randomUUID() } },
  ),
  composeQuestions: (interviewId: string) => request<ComposeJob>(
    `/api/v1/interviews/${interviewId}/questions:compose`,
    { method: 'POST', headers: { 'Idempotency-Key': crypto.randomUUID() } },
  ),
  composeJob: (jobId: string) => request<ComposeJob>(
    `/api/v1/interviews/composition-jobs/${jobId}`,
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
  startAdaptiveSession: (assignmentId: string) => request<AdaptiveView>(
    `/api/v1/candidate/adaptive-sessions/${assignmentId}`, { method: 'POST' },
  ),
  getAdaptiveSession: (sessionId: string) => request<AdaptiveView>(
    `/api/v1/candidate/adaptive-sessions/${sessionId}`,
  ),
  answerAdaptive: (sessionId: string, answer: string) => request<AdaptiveView>(
    `/api/v1/candidate/adaptive-sessions/${sessionId}/answer`,
    { method: 'POST', body: JSON.stringify({ answer }) },
  ),
  adaptiveResult: (sessionId: string) => request<AdaptiveResult>(
    `/api/v1/candidate/adaptive-sessions/${sessionId}/result`,
  ),
  loadSession: (sessionId: string) => request<InterviewSession>(
    `/api/v1/candidate/sessions/${sessionId}`,
  ),
  saveAnswer: (sessionId: string, questionId: string, content: string, expectedVersion: number) =>
    request<SavedAnswer>(`/api/v1/candidate/sessions/${sessionId}/answers/${questionId}`, {
      method: 'PUT', body: JSON.stringify({ content, expectedVersion }),
    }),
  submissions: (page = 0, size = 20, candidateId = '') => request<PageResponse<SubmissionSummary>>(
    `/api/v1/interviewer/submissions?page=${page}&size=${size}${candidateId ? `&candidateId=${candidateId}` : ''}`,
  ),
  submission: (sessionId: string) => request<SubmissionDetail>(
    `/api/v1/interviewer/submissions/${sessionId}`,
  ),
  adaptiveTranscript: (sessionId: string) => request<AdaptiveTranscript>(
    `/api/v1/interviewer/adaptive-sessions/${sessionId}/transcript`,
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
  generateAnswerKey: (sessionId: string) => request<SubmissionDetail>(
    `/api/v1/interviewer/submissions/${sessionId}/answer-key`, { method: 'POST' }),
  explainAnswer: (sessionId: string, answerId: string) => request<SubmissionDetail>(
    `/api/v1/interviewer/submissions/${sessionId}/answers/${answerId}/explain`, { method: 'POST' }),
  emailResult: (sessionId: string) => request<{ sentTo: string }>(
    `/api/v1/interviewer/submissions/${sessionId}/email-result`, { method: 'POST' }),
  draftCoaching: (sessionId: string) => request<CoachingResponse>(
      `/api/v1/interviewer/submissions/${sessionId}/coaching`, { method: 'POST' }),
  currentCoaching: (sessionId: string) => request<CoachingResponse | undefined>(
      `/api/v1/interviewer/submissions/${sessionId}/coaching`),
  approveCoaching: (sessionId: string) => request<CoachingResponse>(
    `/api/v1/interviewer/submissions/${sessionId}/coaching:approve`, { method: 'POST' }),
  candidateResult: (sessionId: string) => request<CandidateResult>(
    `/api/v1/candidate/sessions/${sessionId}/result`,
  ),
  submitSession: (sessionId: string) => request<InterviewSession>(
    `/api/v1/candidate/sessions/${sessionId}/submit`, { method: 'POST' },
  ),
}
