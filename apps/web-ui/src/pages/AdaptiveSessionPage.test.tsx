import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { interviewApi } from '../api/interviewApi'
import { AdaptiveSessionPage } from './AdaptiveSessionPage'

vi.mock('../api/interviewApi', async (importOriginal) => {
  const original = await importOriginal<typeof import('../api/interviewApi')>()
  return {
    ...original,
    interviewApi: {
      ...original.interviewApi,
      startAdaptiveSession: vi.fn(),
      answerAdaptive: vi.fn(),
      adaptiveResult: vi.fn(),
    },
  }
})

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/candidate/adaptive/a1']}>
      <Routes>
        <Route path="/candidate/adaptive/:assignmentId" element={<AdaptiveSessionPage />} />
        <Route path="/candidate" element={<div>Candidate dashboard</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('AdaptiveSessionPage', () => {
  beforeEach(() => cleanup())

  it('shows the first question, submits an answer, then completes', async () => {
    vi.mocked(interviewApi.startAdaptiveSession).mockResolvedValue({
      sessionId: 's1', phase: 'RUNNING', turnsUsed: 1, maxTurns: 12, done: false,
      currentQuestion: {ordinal: 1, skill: 'Concurrency', difficulty: 'HARD',
        prompt: 'Explain the JMM.'},
    })
    vi.mocked(interviewApi.answerAdaptive).mockResolvedValue({
      sessionId: 's1', phase: 'DONE', turnsUsed: 1, maxTurns: 12, done: true,
      currentQuestion: null,
    })
    vi.mocked(interviewApi.adaptiveResult).mockResolvedValue({
      sessionId: 's1', interviewTitle: 'Adaptive', done: true, overallScore: 82,
      passingPercentage: 70, passed: true,
      turns: [{ ordinal: 1, skill: 'Concurrency', difficulty: 'HARD', source: 'GENERATED',
        question: 'Explain the JMM.', answer: 'my answer', score: 82, confidence: 70,
        rationale: 'Solid.' }],
    })

    renderPage()

    expect(await screen.findByText('Explain the JMM.')).toBeInTheDocument()
    expect(screen.getByText('Concurrency · HARD')).toBeInTheDocument()

    fireEvent.change(screen.getByRole('textbox', {name: 'Your answer'}),
      {target: {value: 'my answer'}})
    fireEvent.click(screen.getByRole('button', {name: 'Submit answer'}))

    await waitFor(() => expect(interviewApi.answerAdaptive)
      .toHaveBeenCalledWith('s1', 'my answer'))
    expect(await screen.findByText('Interview complete')).toBeInTheDocument()
    expect(await screen.findByText('Passed')).toBeInTheDocument()
    expect(screen.getByText(/82 \/ 100/)).toBeInTheDocument()
  })

  it('surfaces a start failure', async () => {
    vi.mocked(interviewApi.startAdaptiveSession).mockRejectedValue(new Error('window closed'))
    renderPage()
    expect(await screen.findByRole('alert')).toHaveTextContent('window closed')
  })
})
