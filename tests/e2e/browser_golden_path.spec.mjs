import assert from 'node:assert/strict';
import puppeteer from 'puppeteer';

const BASE = (process.env.PULSE_WEB_BASE_URL || '').trim().replace(/\/$/, '');
const TOKEN = process.env.PULSE_WEB_ACCESS_TOKEN;
const ORG_ID = process.env.PULSE_WEB_ORG_ID;
const DEVICE_ID = process.env.PULSE_WEB_DEVICE_ID;

for (const [name, value] of Object.entries({ PULSE_WEB_BASE_URL: BASE, PULSE_WEB_ACCESS_TOKEN: TOKEN, PULSE_WEB_ORG_ID: ORG_ID, PULSE_WEB_DEVICE_ID: DEVICE_ID })) {
  assert.ok(value, `${name} is required`);
}

const browser = await puppeteer.launch({ headless: true, args: ['--no-sandbox', '--disable-dev-shm-usage'] });
try {
  const page = await browser.newPage();
  const apiOrigins = new Set();
  page.on('request', request => {
    const url = request.url();
    if (url.includes('/api/v1/')) apiOrigins.add(new URL(url).origin);
    assert.ok(!url.includes('localhost:8081'), `browser requested forbidden localhost API: ${url}`);
  });

  await page.goto(BASE, { waitUntil: 'networkidle0', timeout: 60000 });
  await page.evaluate(({ token, org }) => {
    localStorage.setItem('pulse_access_token', token);
    localStorage.setItem('pulse_org_id', org);
  }, { token: TOKEN, org: ORG_ID });
  await page.reload({ waitUntil: 'networkidle0', timeout: 60000 });

  await page.waitForFunction(() => document.body.innerText.includes('Machine Live'), { timeout: 30000 });
  const inputs = await page.$$('input[placeholder="Device UUID"]');
  assert.ok(inputs.length >= 1, 'Machine Live device input is missing');
  await inputs[0].click();
  await inputs[0].type(DEVICE_ID);

  await page.waitForFunction(
    id => document.body.innerText.includes(id) || document.body.innerText.includes('online'),
    { timeout: 30000 },
    DEVICE_ID,
  );

  const body = await page.evaluate(() => document.body.innerText);
  assert.match(body, /Machine Live/);
  assert.match(body, /online/i);
  assert.ok(apiOrigins.size === 1, `browser used ${apiOrigins.size} API origins; exactly one canonical origin is required`);
  assert.equal([...apiOrigins][0], new URL(BASE).origin, 'browser API calls must use the deployed canonical origin');

  console.log(`BLOCK 1 BROWSER GOLDEN PATH: PASS — authenticated UI rendered Machine Live and projected device ${DEVICE_ID}`);
} finally {
  await browser.close();
}
