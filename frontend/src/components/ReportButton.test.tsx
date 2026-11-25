import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import { api } from '../api/endpoints'
import { renderPage } from '../test/render'
import { ReportButton } from './ReportButton'

describe('ReportButton', () => {
  it('sends the chosen reason and details, then thanks the user', async () => {
    const report = vi.spyOn(api, 'reportItem').mockResolvedValue(undefined)
    renderPage(<ReportButton itemId={9} />)

    await userEvent.click(screen.getByRole('button', { name: '檢舉這篇貼文' }))
    await userEvent.click(screen.getByLabelText('詐騙'))
    await userEvent.type(screen.getByLabelText('補充說明（選填）'), '要求先匯款')
    await userEvent.click(screen.getByRole('button', { name: '送出檢舉' }))

    expect(report).toHaveBeenCalledWith(9, 'SCAM', '要求先匯款')
    expect(await screen.findByText(/已收到你的檢舉/)).toBeInTheDocument()
  })

  it('shows why a report was refused', async () => {
    vi.spyOn(api, 'reportItem').mockRejectedValue(new ApiError(409, '你已經檢舉過這篇貼文了'))
    renderPage(<ReportButton itemId={9} />)

    await userEvent.click(screen.getByRole('button', { name: '檢舉這篇貼文' }))
    await userEvent.click(screen.getByRole('button', { name: '送出檢舉' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('你已經檢舉過這篇貼文了')
  })
})
