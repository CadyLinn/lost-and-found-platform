import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/endpoints'
import { renderPage } from '../test/render'
import { ItemForm } from './ItemForm'

describe('ItemForm', () => {
  beforeEach(() => {
    vi.spyOn(api, 'categories').mockResolvedValue([
      { id: 1, name: 'Phone' },
      { id: 2, name: 'Wallet' },
    ])
  })

  function renderForm(onSubmit = vi.fn()) {
    renderPage(<ItemForm submitLabel="發布" isSubmitting={false} onSubmit={onSubmit} onCancel={() => {}} />)
    return onSubmit
  }

  it('shows what is missing instead of submitting an empty form', async () => {
    const onSubmit = renderForm()

    await userEvent.click(screen.getByRole('button', { name: '發布' }))

    expect(await screen.findByText('請輸入標題')).toBeInTheDocument()
    expect(screen.getByText('請選擇分類')).toBeInTheDocument()
    expect(screen.getByText('請輸入地點')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('sends a clean request: trimmed text, numeric category, empty optional fields as null', async () => {
    const onSubmit = renderForm()

    await userEvent.type(screen.getByLabelText('標題'), '  黑色錢包  ')
    await userEvent.selectOptions(await screen.findByLabelText('分類'), '錢包')
    await userEvent.type(screen.getByLabelText('地點'), '圖書館')
    await userEvent.click(screen.getByRole('button', { name: '發布' }))

    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        type: 'LOST',
        title: '黑色錢包',
        categoryId: 2,
        locationText: '圖書館',
        description: null,
        lat: null,
        lng: null,
        verificationQuestion: null,
      }),
    )
  })

  it('asks for a verification question only on "found" posts', async () => {
    renderForm()
    expect(screen.queryByLabelText(/驗證問題/)).not.toBeInTheDocument()

    await userEvent.click(screen.getByText('我撿到了東西'))

    expect(screen.getByLabelText(/驗證問題/)).toBeInTheDocument()
  })
})
