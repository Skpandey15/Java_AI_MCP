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

import { App, dashboardPath } from './App'

describe('App', () => {
  it('selects the dashboard from authenticated realm roles', () => {
    expect(dashboardPath(['candidate'])).toBe('/candidate')
    expect(dashboardPath(['interviewer'])).toBe('/interviewer')
    expect(dashboardPath([])).toBe('/unauthorized')
  })

  it('shows the platform entry points after authentication initializes', async () => {
    render(<App />)
    expect(await screen.findByRole('heading', { name: 'Online Interview' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Login' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Candidate registration' })).toBeInTheDocument()
  })
})
