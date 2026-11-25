import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { api } from '../api/endpoints'
import type { Claim, Item } from '../api/types'
import { ALICE, BOB, fakeAuth, renderPage } from '../test/render'
import { ClaimSection } from './ClaimSection'

// Alice found a wallet and asks a verification question.
const wallet: Item = {
  id: 7,
  type: 'FOUND',
  title: '咖啡色皮夾',
  description: null,
  categoryId: 2,
  categoryName: 'Wallet',
  locationText: '餐廳',
  lat: null,
  lng: null,
  eventDate: '2026-11-20',
  status: 'OPEN',
  verificationQuestion: '裡面有幾張卡？',
  ownerId: ALICE.id,
  ownerDisplayName: 'Alice',
  createdAt: '2026-11-20T10:00:00Z',
  updatedAt: '2026-11-20T10:00:00Z',
  distanceKm: null,
  photos: [],
  hidden: false,
  hiddenReason: null,
}

const bobsClaim: Claim = {
  id: 3,
  itemId: wallet.id,
  itemTitle: wallet.title,
  itemType: 'FOUND',
  itemStatus: 'OPEN',
  status: 'PENDING',
  answer: '兩張',
  message: '這是我的',
  claimantId: BOB.id,
  claimantDisplayName: 'Bob',
  contactEmail: null,
  createdAt: '2026-11-21T10:00:00Z',
  decidedAt: null,
}

describe('ClaimSection', () => {
  it('asks logged-out visitors to log in', () => {
    renderPage(<ClaimSection item={wallet} />)

    expect(screen.getByText(/這是你的東西嗎？/)).toHaveTextContent('登入後就可以回應')
    expect(screen.getByRole('link', { name: '登入' })).toBeInTheDocument()
  })

  it('requires an answer to the verification question before sending', async () => {
    vi.spyOn(api, 'myClaims').mockResolvedValue([])
    const submit = vi.spyOn(api, 'submitClaim').mockResolvedValue(bobsClaim)
    renderPage(<ClaimSection item={wallet} />, { auth: fakeAuth(BOB) })

    await userEvent.type(await screen.findByLabelText('留言給發文者'), '這是我上週掉的')
    await userEvent.click(screen.getByRole('button', { name: '送出' }))
    expect(screen.getByRole('alert')).toHaveTextContent('請回答驗證問題')
    expect(submit).not.toHaveBeenCalled()

    await userEvent.type(screen.getByLabelText(/驗證問題/), '兩張')
    await userEvent.click(screen.getByRole('button', { name: '送出' }))
    expect(submit).toHaveBeenCalledWith(wallet.id, '兩張', '這是我上週掉的')
  })

  it('shows a visitor their earlier claim instead of the form', async () => {
    vi.spyOn(api, 'myClaims').mockResolvedValue([bobsClaim])
    renderPage(<ClaimSection item={wallet} />, { auth: fakeAuth(BOB) })

    expect(await screen.findByText('你的回應')).toBeInTheDocument()
    expect(screen.getByText('等待回覆')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '送出' })).not.toBeInTheDocument()
  })

  it('shows the poster the claims with their answers, and lets them accept or decline', async () => {
    vi.spyOn(api, 'itemClaims').mockResolvedValue([bobsClaim])
    renderPage(<ClaimSection item={wallet} />, { auth: fakeAuth(ALICE) })

    expect(await screen.findByText('Bob')).toBeInTheDocument()
    expect(screen.getByText('兩張')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '接受' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '婉拒' })).toBeInTheDocument()
  })

  it('reveals the contact email only once a claim is approved', async () => {
    vi.spyOn(api, 'myClaims').mockResolvedValue([{ ...bobsClaim, status: 'APPROVED', contactEmail: 'alice@test.com' }])
    renderPage(<ClaimSection item={{ ...wallet, status: 'CLAIMED' }} />, { auth: fakeAuth(BOB) })

    expect(await screen.findByRole('link', { name: 'alice@test.com' })).toHaveAttribute('href', 'mailto:alice@test.com')
  })
})
