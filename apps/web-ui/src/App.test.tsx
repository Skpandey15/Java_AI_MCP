import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { vi } from 'vitest'

vi.mock('./auth/keycloak', () => ({
  keycloak: {
    init: vi.fn().mockResolvedValue(false),
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    realmAccess: { roles: [] },
  },
}))

import { App } from './App'

describe('App', () => {
  it('shows the platform entry points', () => {
    render(<App />)
    expect(screen.getByRole('heading', { name: 'Online Interview' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Login' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Candidate registration' })).toBeInTheDocument()
  })
})
