import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ALICE, fakeAuth, renderPage } from '../test/render'
import { RequireAdmin } from './RequireAdmin'

const page = (
  <RequireAdmin>
    <p>admin dashboard</p>
  </RequireAdmin>
)

describe('RequireAdmin', () => {
  it('shows the page to admins', () => {
    renderPage(page, { auth: fakeAuth({ ...ALICE, role: 'ADMIN' }) })
    expect(screen.getByText('admin dashboard')).toBeInTheDocument()
  })

  it('tells normal users they are not allowed', () => {
    renderPage(page, { auth: fakeAuth(ALICE) })
    expect(screen.getByRole('alert')).toHaveTextContent('只有管理員')
    expect(screen.queryByText('admin dashboard')).not.toBeInTheDocument()
  })

  it('sends logged-out visitors to log in', async () => {
    renderPage(page, { path: '/admin', routes: { '/login': <p>login page</p> } })
    expect(await screen.findByText('login page')).toBeInTheDocument()
  })
})
