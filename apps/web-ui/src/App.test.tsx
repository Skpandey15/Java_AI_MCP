import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { App } from './App'

describe('App', () => {
  it('shows the platform entry points', () => {
    render(<App />)
    expect(screen.getByRole('heading', { name: 'Online Interview' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Candidate login' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Interviewer login' })).toBeInTheDocument()
  })
})
