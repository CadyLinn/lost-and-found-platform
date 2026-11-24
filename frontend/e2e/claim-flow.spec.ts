import { expect, test } from '@playwright/test'
import { login, logout, makePng, register, runId } from './helpers'

test('a found item goes from post to claim to returned', async ({ page }) => {
  const id = runId()
  const finder = { name: `Finder ${id}`, email: `finder-${id}@e2e.test` }
  const owner = { name: `Owner ${id}`, email: `owner-${id}@e2e.test` }
  const title = `E2E 咖啡色皮夾 ${id}`
  page.on('dialog', (dialog) => dialog.accept()) // "accept this claim?" confirmations

  await test.step('the finder posts what they found, with a photo', async () => {
    await register(page, finder.name, finder.email)
    await page.getByRole('link', { name: '＋ 發布' }).click()

    await page.getByText('我撿到了東西').click()
    await page.getByLabel('標題').fill(title)
    await page.getByLabel('分類').selectOption({ label: '錢包' })
    await page.getByLabel('地點').fill('學生餐廳二樓')
    await page.getByLabel(/驗證問題/).fill('裡面有幾張悠遊卡？')
    await page.locator('input[type=file]').setInputFiles({ name: 'wallet.png', mimeType: 'image/png', buffer: await makePng(page) })
    await expect(page.getByText('待上傳')).toBeVisible()
    await page.getByRole('button', { name: '發布' }).click()

    await expect(page.getByRole('heading', { name: title })).toBeVisible()
    const photo = page.getByRole('img', { name: `${title}的照片 1` })
    await expect(photo).toBeVisible()
    expect(await photo.evaluate((img: HTMLImageElement) => img.naturalWidth)).toBeGreaterThan(0)
    await logout(page)
  })

  await test.step('the owner finds it by searching and claims it', async () => {
    await register(page, owner.name, owner.email)
    await page.getByLabel('搜尋').fill(id)
    await page.getByRole('button', { name: '搜尋' }).click()
    await page.getByRole('link', { name: new RegExp(title) }).click()

    await page.getByLabel(/驗證問題/).fill('兩張')
    await page.getByLabel('留言給發文者').fill('是我上週二在餐廳掉的，裡面有我的學生證')
    await page.getByRole('button', { name: '送出' }).click()
    await expect(page.getByText('你的回應')).toBeVisible()
    await expect(page.getByText('等待回覆')).toBeVisible()
    await logout(page)
  })

  await test.step('the finder is notified, accepts the claim and sees how to reach the owner', async () => {
    await login(page, finder.email)
    await expect(page.getByRole('link', { name: /未讀/ })).toBeVisible()
    await page.getByRole('link', { name: /通知/ }).click()
    await page.getByRole('button', { name: new RegExp(`有人回應了你的貼文「${title}」`) }).click()

    await expect(page.getByText(owner.name)).toBeVisible()
    await expect(page.getByText('兩張')).toBeVisible()
    await page.getByRole('button', { name: '接受' }).click()
    await expect(page.getByRole('link', { name: owner.email })).toBeVisible()

    await page.getByRole('button', { name: '標記為已歸還' }).click()
    await expect(page.getByText('這件物品已經歸還')).toBeVisible()
    await logout(page)
  })

  await test.step('the owner sees the claim was accepted, with the finder\'s email', async () => {
    await login(page, owner.email)
    await page.getByRole('link', { name: '我的認領' }).click()
    await expect(page.getByText(title)).toBeVisible()
    await expect(page.getByText('已接受')).toBeVisible()
    await expect(page.getByRole('link', { name: finder.email })).toBeVisible()
  })
})

test('a protected page sends you to log in, then back where you were', async ({ page }) => {
  const id = runId()
  const email = `return-${id}@e2e.test`
  await register(page, `Return ${id}`, email)
  await logout(page)

  await page.goto('/items/new')
  await expect(page).toHaveURL(/\/login$/)
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('密碼').fill('password123')
  await page.getByRole('button', { name: '登入' }).click()

  await expect(page).toHaveURL(/\/items\/new$/)
  await expect(page.getByRole('heading', { name: '發布貼文' })).toBeVisible()
})

test('a wrong password shows a clear message', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('Email').fill(`nobody-${runId()}@e2e.test`)
  await page.getByLabel('密碼').fill('wrong-password')
  await page.getByRole('button', { name: '登入' }).click()

  await expect(page.getByRole('alert')).toHaveText('Email 或密碼錯誤')
})
