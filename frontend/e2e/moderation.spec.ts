import { expect, test } from '@playwright/test'
import { login, logout, register, runId } from './helpers'

// Needs an admin account, made with scripts/make-admin.sh (CI does this before running the tests).
const ADMIN_EMAIL = process.env.E2E_ADMIN_EMAIL

test('a reported post is hidden by an admin and disappears for everyone else', async ({ page }) => {
  test.skip(!ADMIN_EMAIL, 'Set E2E_ADMIN_EMAIL to an admin account (see scripts/make-admin.sh)')
  const id = runId()
  const author = { name: `Seller ${id}`, email: `seller-${id}@e2e.test` }
  const title = `E2E 便宜名牌包 ${id}`
  page.on('dialog', (dialog) => dialog.accept('廣告，不是失物')) // the "reason for hiding" prompt

  await test.step('someone posts an ad disguised as a found item', async () => {
    await register(page, author.name, author.email)
    await page.getByRole('link', { name: '＋ 發布' }).click()
    await page.getByText('我撿到了東西').click()
    await page.getByLabel('標題').fill(title)
    await page.getByLabel('分類').selectOption({ label: '包包' })
    await page.getByLabel('地點').fill('加 LINE 私訊')
    await page.getByRole('button', { name: '發布' }).click()
    await expect(page.getByRole('heading', { name: title })).toBeVisible()
    await logout(page)
  })

  await test.step('another user reports it', async () => {
    await register(page, `Reporter ${id}`, `reporter-${id}@e2e.test`)
    await page.getByLabel('搜尋').fill(id)
    await page.getByRole('button', { name: '搜尋' }).click()
    await page.getByRole('link', { name: new RegExp(title) }).click()
    await page.getByRole('button', { name: '檢舉這篇貼文' }).click()
    await page.getByLabel('廣告或垃圾訊息').check()
    await page.getByRole('button', { name: '送出檢舉' }).click()
    await expect(page.getByText(/已收到你的檢舉/)).toBeVisible()
    await logout(page)
  })

  await test.step('the admin sees the report and hides the post', async () => {
    await login(page, ADMIN_EMAIL!)
    await page.getByRole('link', { name: '管理' }).click()
    const card = page.getByRole('listitem').filter({ has: page.getByRole('link', { name: title }) })
    await expect(card.getByText('廣告或垃圾訊息')).toBeVisible()
    await card.getByRole('button', { name: '隱藏貼文' }).click()
    await expect(card).toHaveCount(0) // resolved: no longer waiting

    await page.getByRole('tab', { name: '已隱藏的貼文' }).click()
    const hidden = page.getByRole('listitem').filter({ has: page.getByRole('link', { name: title }) })
    await expect(hidden.getByText(/原因：廣告，不是失物/)).toBeVisible()
    await logout(page)
  })

  await test.step('the post is gone for the public', async () => {
    await page.getByLabel('搜尋').fill(id)
    await page.getByRole('button', { name: '搜尋' }).click()
    await expect(page.getByText('找不到符合條件的貼文')).toBeVisible()
  })

  await test.step('the author is told why, and still sees their own post', async () => {
    await login(page, author.email)
    await page.getByRole('link', { name: /通知/ }).click()
    await page.getByRole('button', { name: /已被管理員隱藏/ }).click()
    await expect(page.getByText('這篇貼文已被管理員隱藏')).toBeVisible()
    await expect(page.getByText('原因：廣告，不是失物')).toBeVisible()
  })
})
