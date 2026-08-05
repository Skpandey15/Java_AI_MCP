import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { interviewApi, type AdminQuestion, type Interview } from '../api/interviewApi'
import { InterviewCard, InterviewerDashboard } from './InterviewerDashboard'

vi.mock('../auth/AuthProvider', () => ({
  useAuth: () => ({logout: vi.fn()}),
}))

vi.mock('../api/interviewApi', async (importOriginal) => {
  const original = await importOriginal<typeof import('../api/interviewApi')>()
  return {
    ...original,
    interviewApi: {
      ...original.interviewApi,
      listQuestions: vi.fn(),
      listOwned: vi.fn(),
      candidates: vi.fn(),
      listKnowledgeCollections: vi.fn(),
      create: vi.fn(),
    },
  }
})

const interview: Interview = {
  id: 'interview-1',
  title: 'AI-generated interview',
  description: 'Regression test',
  skills: ['Java'],
  difficulty: 'EASY',
  questionMode: 'DIRECT_LLM',
  durationMinutes: 10,
  questionCount: 1,
  questionComposition: {mcqSingle: 0, mcqMultiple: 0, shortText: 0, longText: 1},
  passingPercentage: 70,
  status: 'DRAFT',
  createdAt: '2026-07-29T00:00:00Z',
}

const question: AdminQuestion = {
  id: 'question-1',
  order: 1,
  prompt: 'Explain Spring Boot auto-configuration.',
  maxScore: 10,
  type: 'LONG_TEXT',
  options: [],
  correctAnswers: [],
  source: 'AI',
  citations: [],
}

describe('InterviewerDashboard question editing', () => {
  beforeEach(() => {
    cleanup()
    vi.mocked(interviewApi.listQuestions).mockResolvedValue([question])
    vi.mocked(interviewApi.listOwned).mockResolvedValue([])
    vi.mocked(interviewApi.candidates).mockResolvedValue([])
    vi.mocked(interviewApi.listKnowledgeCollections).mockResolvedValue([])
    Element.prototype.scrollIntoView = vi.fn()
  })

  it('shows the edit form for an existing DIRECT_LLM question', async () => {
    render(<InterviewCard interview={interview} candidates={[]} notify={vi.fn()} reload={vi.fn()} />)

    const editButton = await screen.findByRole('button', {name: 'Edit'})
    fireEvent.click(editButton)

    expect(screen.getByRole('heading', {name: 'Edit question'})).toBeInTheDocument()
    expect(screen.getByRole('textbox', {name: 'Prompt'})).toHaveValue(question.prompt)
    expect(screen.getByRole('button', {name: 'Save changes'})).toBeInTheDocument()
    await waitFor(() => expect(Element.prototype.scrollIntoView).toHaveBeenCalled())
  })

  it('derives the interview total from the mixed question counts', async () => {
    render(
      <MemoryRouter>
        <InterviewerDashboard />
      </MemoryRouter>,
    )
    await waitFor(() => expect(interviewApi.listOwned).toHaveBeenCalled())

    fireEvent.change(screen.getByRole('spinbutton', {name: 'MCQ (one answer)'}), {
      target: {value: '2'},
    })
    fireEvent.change(screen.getByRole('spinbutton', {name: 'Short answer (one line)'}), {
      target: {value: '3'},
    })

    expect(screen.getByText('Total questions: 5')).toBeInTheDocument()
  })

  it('populates technologies from the selected ecosystem', async () => {
    render(
      <MemoryRouter>
        <InterviewerDashboard />
      </MemoryRouter>,
    )
    await waitFor(() => expect(interviewApi.listOwned).toHaveBeenCalled())

    fireEvent.change(screen.getByRole('combobox', {name: 'Ecosystem'}), {
      target: {value: 'PYTHON'},
    })
    fireEvent.click(screen.getByText('1 selected'))

    expect(screen.getByRole('checkbox', {name: 'Django'})).toBeInTheDocument()
    expect(screen.queryByRole('checkbox', {name: 'Spring Boot'})).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('checkbox', {name: 'Django'}))
    expect(screen.getByText(/general-purpose JVM language widely used for enterprise/)).toBeInTheDocument()
    expect(screen.getByText(/batteries-included Python web framework/)).toBeInTheDocument()
  })

  it('offers cross-platform technologies in the miscellaneous ecosystem', async () => {
    render(
      <MemoryRouter>
        <InterviewerDashboard />
      </MemoryRouter>,
    )
    await waitFor(() => expect(interviewApi.listOwned).toHaveBeenCalled())

    fireEvent.change(screen.getByRole('combobox', {name: 'Ecosystem'}), {
      target: {value: 'MISCELLANEOUS'},
    })
    fireEvent.click(screen.getByText('1 selected'))

    expect(screen.getByRole('checkbox', {name: 'GraphQL'})).toBeInTheDocument()
    expect(screen.getByRole('checkbox', {name: 'Jolt'})).toBeInTheDocument()
    expect(screen.getByRole('checkbox', {name: 'HashiCorp Vault'})).toBeInTheDocument()
  })
})
