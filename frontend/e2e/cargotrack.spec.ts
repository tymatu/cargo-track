import { expect, test } from '@playwright/test';

const demoPassword = 'CargoTrack123!';

test('public tracking shows seeded parcel and refresh metadata', async ({ page }) => {
  await page.goto('/track/ct-demo00001');

  await expect(page.getByTestId('tracking-number')).toContainText('CT-DEMO00001');
  await expect(page.getByTestId('tracking-refresh-state')).toBeVisible();
});

test('demo driver can open assigned shipments', async ({ page }) => {
  await page.goto('/login');
  await page.getByTestId('demo-login-driver').click();

  await expect(page).toHaveURL(/\/driver$/);
  await expect(page.getByTestId('driver-shipments-title')).toBeVisible();
  await expect(page.getByTestId('driver-shipments-grid')).toBeVisible();
});

test('demo dispatcher can open parcel queue and shipments', async ({ page }) => {
  await page.goto('/login');
  await page.getByTestId('demo-login-dispatcher').click();

  await expect(page).toHaveURL(/\/dispatcher$/);
  await expect(page.getByTestId('dispatcher-dashboard-title')).toBeVisible();
  await expect(page.getByTestId('dispatcher-parcels-table')).toBeVisible();
  await expect(page.getByTestId('dispatcher-shipments-list')).toBeVisible();
});

test('new user can register and log in', async ({ page }) => {
  const email = `e2e-${Date.now()}@example.test`;

  await page.goto('/register');
  await page.getByTestId('register-first-name').fill('E2E');
  await page.getByTestId('register-last-name').fill('Customer');
  await page.getByTestId('register-email').fill(email);
  await page.getByTestId('register-phone').fill('+420700123456');
  await page.getByTestId('register-password').fill(demoPassword);
  await page.getByTestId('register-submit').click();

  await expect(page).toHaveURL(/\/login$/);
  await page.getByTestId('login-email').fill(email);
  await page.getByTestId('login-password').fill(demoPassword);
  await page.getByTestId('login-submit').click();

  await expect(page).toHaveURL(/\/parcels$/);
});

test('demo admin can open dashboard, shipments and audit log', async ({ page }) => {
  await page.goto('/login');
  await page.getByTestId('demo-login-admin').click();

  await expect(page).toHaveURL(/\/admin$/);
  await expect(page.getByTestId('admin-dashboard-title')).toBeVisible();

  await page.getByTestId('admin-nav-shipments').click();
  await expect(page).toHaveURL(/\/admin\/shipments$/);
  await expect(page.getByTestId('admin-shipments-table')).toBeVisible();

  const filteredShipments = page.waitForResponse(
    (response) =>
      response.url().includes('/api/v1/admin/shipments') &&
      response.url().includes('status=') &&
      response.status() === 200,
  );
  await page.getByTestId('admin-shipments-status-filter').click();
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('Enter');
  await filteredShipments;

  await page.getByTestId('admin-nav-audit').click();
  await expect(page).toHaveURL(/\/admin\/audit$/);
  await expect(page.getByTestId('admin-audit-title')).toBeVisible();
});
