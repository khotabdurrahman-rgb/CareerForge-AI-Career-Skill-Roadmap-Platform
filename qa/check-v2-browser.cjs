const { chromium } = require('../.tools/node_modules/playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { randomUUID } = require('node:crypto');
const base = process.env.CAREERFORGE_URL || 'http://localhost:8090';
const screenshots = path.resolve(__dirname, '../docs/screenshots');
const failures = [];
let checks = 0;
const check = (value, label) => { assert.ok(value, label); checks++; };
const routes = ['dashboard', 'skills', 'careers', 'gap', 'roadmap', 'projects', 'resources',
  'assessments', 'planner', 'resume', 'compare', 'recommendations', 'history', 'mentor', 'profile'];

async function ready(page) {
  await page.locator('#main h1').waitFor();
  await page.waitForFunction(() => !document.querySelector('#main [aria-busy="true"], #main .v2-loading'));
}
async function route(page, name) {
  const link = page.locator(`[data-route="${name}"]`);
  if (!(await link.isVisible())) await page.getByRole('button', { name: 'Open navigation', exact: true }).click();
  await link.click();
  await page.waitForFunction(value => location.hash === '#' + value, name);
  await page.locator(`[data-route="${name}"][aria-current="page"]`).waitFor({ state: 'attached' });
  await ready(page);
  check((await page.locator('#main h1').innerText()).trim().length > 0, `${name} has heading`);
  check(await page.locator('#main .error-box:visible').count() === 0, `${name} loads without API error`);
}
async function layout(page, label) {
  const result = await page.evaluate(() => {
    const width = window.innerWidth;
    const visible = element => { const r = element.getBoundingClientRect(); const css = getComputedStyle(element); return r.width && r.height && css.display !== 'none' && css.visibility !== 'hidden'; };
    const offenders = [...document.querySelectorAll('#main input, #main select, #main button, #main h1, #main h2, #main p, #main textarea')]
      .filter(visible).filter(element => {
        const r = element.getBoundingClientRect();
        const scrollRegion = element.closest('.table-responsive');
        if (scrollRegion && ['auto', 'scroll'].includes(getComputedStyle(scrollRegion).overflowX)) {
          const region = scrollRegion.getBoundingClientRect();
          if (region.left >= -1 && region.right <= width + 1) return false;
        }
        return r.left < -1 || r.right > width + 1;
      })
      .map(element => `${element.tagName}: ${element.textContent.trim().slice(0, 80)}`);
    return { fits: document.documentElement.scrollWidth <= width + 1, offenders };
  });
  check(result.fits && result.offenders.length === 0, `${label} fits viewport: ${result.offenders.join('; ')}`);
}
async function saveDialog(page) {
  await page.locator('dialog').getByRole('button', { name: 'Save', exact: true }).click();
  await page.locator('dialog').waitFor({ state: 'hidden' });
  await ready(page);
}

async function main() {
  fs.mkdirSync(screenshots, { recursive: true });
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1050 }, acceptDownloads: true });
  const page = await context.newPage();
  page.on('pageerror', error => failures.push(error.message));
  page.on('response', response => { if (response.status() >= 500) failures.push(`${response.status()} ${response.url()}`); });
  try {
    await page.goto(base, { waitUntil: 'networkidle' });
    await page.getByRole('button', { name: 'Student demo', exact: true }).click();
    await page.locator('.metrics').waitFor();
    check((await context.request.get(base + '/api/me')).ok(), 'Demo login uses actual session');
    for (const width of [1440, 390, 320]) {
      await page.setViewportSize({ width, height: width === 1440 ? 1050 : 844 });
      for (const name of routes) {
        await route(page, name);
        await layout(page, `${name} at ${width}px`);
        await page.screenshot({ path: path.join(screenshots, `v2-${name}-${width}.png`), fullPage: true });
      }
    }
    await page.setViewportSize({ width: 1440, height: 1050 });
    const qaContext = await browser.newContext({ viewport: { width: 1440, height: 1050 }, acceptDownloads: true });
    try {
      const registration = await qaContext.request.post(base + '/api/auth/register', { data: {
        name: 'V2 Browser QA', email: `browser-v2-${randomUUID()}@example.org`, password: 'BrowserV2Password123!'
      } });
      check(registration.ok(), 'Isolated workflow account registered');
      const work = await qaContext.newPage();
      work.on('pageerror', error => failures.push(error.message));
      await work.goto(base + '/#assessments', { waitUntil: 'networkidle' });
      await ready(work);
      await work.getByRole('button', { name: 'Start assessment', exact: true }).first().click();
      await work.locator('#quiz-form').waitFor();
      while (true) {
        await work.locator('input[name="quiz-answer"]').first().check();
        const next = work.getByRole('button', { name: 'Next question', exact: true });
        if (!await next.count()) break;
        await next.click();
      }
      const submittedResponse = work.waitForResponse(response => response.url().includes('/api/assessments/sessions/') && response.request().method() === 'POST');
      await work.getByRole('button', { name: 'Submit assessment', exact: true }).click();
      const submitted = await (await submittedResponse).json();
      await work.getByRole('heading', { name: 'Assessment results', exact: true }).waitFor();
      check((await work.locator('.feedback-row').count()) >= 3, 'Submitted quiz renders question feedback');
      const results = work.locator('.v2-section').filter({ has: work.getByRole('heading', { name: 'Assessment results', exact: true }) });
      const resultText = await results.innerText();
      check(resultText.includes(submitted.attempt.skillName) && resultText.includes(`${submitted.attempt.score}%`) && !resultText.includes('undefined'), 'Result displays actual API score and skill');
      await work.screenshot({ path: path.join(screenshots, 'v2-assessment-result.png'), fullPage: true });

      await route(work, 'planner');
      await work.locator('[data-action="v2-add-goal"]').first().click();
      await work.getByLabel('Title', { exact: true }).fill('Browser QA goal');
      await work.getByLabel('Target minutes', { exact: true }).fill('90');
      await saveDialog(work);
      await work.getByText('Browser QA goal', { exact: true }).waitFor();
      await work.getByRole('button', { name: 'Edit Browser QA goal', exact: true }).click();
      await work.getByLabel('Title', { exact: true }).fill('Browser QA updated goal');
      await saveDialog(work);
      await work.getByText('Browser QA updated goal', { exact: true }).waitFor();
      await work.locator('[data-action="v2-add-task"]').first().click();
      await work.getByLabel('Title', { exact: true }).fill('Browser QA task');
      await work.getByLabel('Estimated minutes', { exact: true }).fill('35');
      await saveDialog(work);
      await work.getByLabel('Status for Browser QA task', { exact: true }).first().selectOption('COMPLETED');
      await work.waitForFunction(() => document.querySelector('[data-task-status]')?.value === 'COMPLETED' && !document.querySelector('[data-task-status]')?.disabled);
      check(true, 'Planner goal edit and task completion persist');
      for (const title of ['Browser QA task', 'Browser QA updated goal']) {
        await work.getByRole('button', { name: 'Delete ' + title, exact: true }).first().click();
        await work.locator('dialog').getByRole('button', { name: 'Delete', exact: true }).click();
        await work.locator('dialog').waitFor({ state: 'hidden' });
        await work.getByText(title, { exact: true }).waitFor({ state: 'hidden' });
      }

      await route(work, 'resume');
      await work.getByLabel('Professional headline', { exact: true }).fill('Browser QA Engineer');
      await work.getByLabel('Summary', { exact: true }).fill('A resume saved through the browser.');
      await work.getByRole('textbox', { name: 'Experience', exact: true }).fill('QA experience toggle marker');
      await work.getByRole('checkbox', { name: 'Experience', exact: true }).uncheck();
      check(!(await work.locator('#resume-preview').innerText()).includes('QA experience toggle marker'), 'Preview respects section toggle');
      await work.getByRole('button', { name: 'Save resume', exact: true }).click();
      await work.locator('#resume-save-state').getByText('All changes saved', { exact: true }).waitFor();
      await work.reload({ waitUntil: 'networkidle' });
      await ready(work);
      check(await work.getByLabel('Professional headline', { exact: true }).inputValue() === 'Browser QA Engineer', 'Resume persists after reload');
      const downloadPromise = work.waitForEvent('download');
      await work.getByRole('button', { name: 'Download PDF', exact: true }).click();
      const download = await downloadPromise;
      check(download.suggestedFilename().endsWith('.pdf') && await download.failure() === null, 'PDF downloads successfully');
      const stream = await download.createReadStream();
      const chunks = []; for await (const chunk of stream) chunks.push(chunk);
      check(Buffer.concat(chunks).subarray(0, 5).toString() === '%PDF-', 'Downloaded bytes are PDF');

      await route(work, 'compare');
      await work.getByRole('button', { name: 'Compare', exact: true }).click();
      await ready(work);
      check(await work.locator('.compare-stats').count() === 2, 'Both comparison sides render');
      await route(work, 'recommendations');
      await work.getByRole('button', { name: 'Save project idea', exact: true }).first().click();
      await work.getByRole('button', { name: 'Unsave project idea', exact: true }).first().waitFor();
      await work.getByRole('button', { name: 'Add to portfolio', exact: true }).first().click();
      await work.getByRole('link', { name: 'View portfolio', exact: true }).first().waitFor();
      check(true, 'Recommendation saves and adds to portfolio');
      await route(work, 'history');
      await work.getByRole('heading', { name: 'Activity timeline', exact: true }).waitFor();
      check(true, 'Progress timeline renders');
      await route(work, 'mentor');
      const mentor = await (await qaContext.request.get(base + '/api/mentor')).json();
      if (!mentor.available) check(await work.getByRole('button', { name: 'Send', exact: true }).isDisabled(), 'Unconfigured mentor cannot send');
    } finally { await qaContext.close(); }

    for (const width of [1440, 390, 320]) {
      await page.setViewportSize({ width, height: width === 1440 ? 1050 : 844 });
      for (const route of ['forgot-password', 'verification', 'reset-password', 'verify-email']) {
        await page.goto(base + '/#' + route, { waitUntil: 'networkidle' });
        await page.locator('#main h1').waitFor();
        check(await page.locator('input[name="token"]').count() === 0, 'No raw token input in recovery UI');
        await layout(page, `${route} at ${width}px`);
        await page.screenshot({ path: path.join(screenshots, `v2-${route}-${width}.png`), fullPage: true });
      }
    }
    check(failures.length === 0, 'No browser exceptions or server errors: ' + failures.join('; '));
    console.log(JSON.stringify({ base, checks, result: 'PASS', screenshots, paidAiRequests: 0 }, null, 2));
  } catch (error) {
    await page.screenshot({ path: path.join(screenshots, 'v2-failure.png'), fullPage: true });
    throw error;
  } finally { await browser.close(); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
