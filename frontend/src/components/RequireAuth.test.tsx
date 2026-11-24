import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ALICE, fakeAuth, renderPage } from '../test/render'
import { RequireAuth } from './RequireAuth'

const page = (
  <RequireAuth>
    <p>secret page</p>
  </RequireAuth>
)

describe('RequireAuth', () => {
  it('sends logged-out visitors to the login page', async () => {
    renderPage(page, { path: '/me/items', routes: { '/login': <p>login page</p> } })

    expect(await screen.findByText('login page')).toBeInTheDocument()
    expect(screen.queryByText('secret page')).not.toBeInTheDocument()
  })

  it('remembers where to come back to after logging in', async () => {
    const { router } = renderPage(page, { path: '/me/items', routes: { '/login': <p>login page</p> } })

    await screen.findByText('login page')
    expect(router.state.location.state).toEqual({ from: '/me/items' })
  })

  it('waits while a previous login is being restored, instead of redirecting', () => {
    renderPage(page, { auth: fakeAuth(null, { isRestoring: true }), routes: { '/login': <p>login page</p> } })

    expect(screen.getByText('恢復登入中…')).toBeInTheDocument()
    expect(screen.queryByText('login page')).not.toBeInTheDocument()
  })

  it('shows the page to logged-in users', () => {
    renderPage(page, { auth: fakeAuth(ALICE) })

    expect(screen.getByText('secret page')).toBeInTheDocument()
  })
})
