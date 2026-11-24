import { expect, type Page } from '@playwright/test'

export const PASSWORD = 'password123'

/** A unique suffix per test run, so runs never collide with each other's data. */
export const runId = () => `${Date.now().toString(36)}${Math.floor(Math.random() * 1000)}`

export async function register(page: Page, name: string, email: string) {
  await page.goto('/register')
  await page.getByLabel('暱稱').fill(name)
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('密碼').fill(PASSWORD)
  await page.getByRole('button', { name: '建立帳號' }).click()
  await expect(page.getByRole('button', { name: '登出' })).toBeVisible()
}

export async function login(page: Page, email: string) {
  await page.goto('/login')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('密碼').fill(PASSWORD)
  await page.getByRole('button', { name: '登入' }).click()
  await expect(page.getByRole('button', { name: '登出' })).toBeVisible()
}

export async function logout(page: Page) {
  await page.getByRole('button', { name: '登出' }).click()
  await expect(page.getByRole('banner').getByRole('link', { name: '登入' })).toBeVisible()
}

/** A 64×48 PNG made in the browser, so the test needs no image files. */
export async function makePng(page: Page): Promise<Buffer> {
  const base64 = await page.evaluate(() => {
    const canvas = document.createElement('canvas')
    canvas.width = 64
    canvas.height = 48
    const context = canvas.getContext('2d')!
    context.fillStyle = '#8b5a2b'
    context.fillRect(0, 0, 64, 48)
    return canvas.toDataURL('image/png').split(',')[1]
  })
  return Buffer.from(base64, 'base64')
}
