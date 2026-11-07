const puppeteer = require('puppeteer-core');
const FILE = 'file://' + process.argv[2];
(async () => {
  const b = await puppeteer.launch({ executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: 'new', args: ['--window-size=1200,860'] });
  const p = await b.newPage(); await p.setViewport({ width: 1200, height: 860 });
  const errs = []; p.on('pageerror', e => errs.push(e.message)); p.on('console', m => { if (m.type() === 'error') errs.push(m.text()); });
  const sleep = ms => new Promise(r => setTimeout(r, ms));
  const cur = () => p.evaluate(() => document.querySelector('.pscreen.on').dataset.screen);
  let fails = 0, steps = 0;
  async function click(t) {
    const ok = await p.evaluate((t) => {
      const s = document.querySelector('.pscreen.on');
      const els = [...s.querySelectorAll('button, a, .bo-row, .bo-homecard, .bo-plan, .bo-pick, .bo-slot, .bo-session, label.bo-check, .bo-pair, .bo-mic, .bo-rec__btn, [data-scrim]')];
      const lab = el => { const a = el.getAttribute('aria-label'); if (a) return a.toLowerCase(); const c = el.cloneNode(true); c.querySelectorAll('.bo-icon').forEach(i => i.remove()); return c.textContent.replace(/\s+/g, ' ').trim().toLowerCase(); };
      const el = t.startsWith('.') || t.startsWith('[') ? s.querySelector(t) : els.find(e => lab(e).includes(t.toLowerCase()));
      if (!el) return false; el.click(); return true; }, t);
    if (!ok) { console.log('  ✗ no element:', t, 'on', await cur()); fails++; }
  }
  async function expect(id, label) { steps++; const c = await cur(); if (c !== id) { console.log(`  ✗ ${label}: expected ${id}, got ${c}`); fails++; } }
  async function jump(id) { await p.evaluate(id => { location.hash = id; }, id); await sleep(250); }
  async function waitTurn() { for (let i = 0; i < 40; i++) { const st = await p.evaluate(() => { const r = document.querySelector('.pscreen.on .bo-mic'); return r && r.className; }); if (st && st.includes('turn')) return true; await sleep(250); } return false; }
  await p.goto(FILE + '#splash'); await sleep(2000); await expect('welcome', 'splash → welcome');
  await click('Skip intro'); await expect('consent', 'skip intro');
  await click('Yes, keep them'); await expect('mic', 'consent');
  await click('Allow microphone'); await sleep(1900); await expect('q1', 'mic → q1');
  const tm = await p.$eval('#timer', e => e.textContent); console.log('  first-word timer:', tm);
  await click('Job interviews'); await click('Next'); await expect('q2', 'q1 next');
  await click('Next'); await expect('q3', 'q2 next'); await click('Next'); await expect('miccheck', 'q3 next');
  await sleep(4600); await expect('day1', 'mic check auto');
  await click('.bo-rec__btn'); await sleep(2200); await expect('talk', 'day1 → talk');
  for (let i = 0; i < 4; i++) { if (!await waitTurn()) { console.log('  ✗ no turn', i); fails++; } await click('done, end my turn'); }
  await sleep(6500); await expect('scoring', 'bye → scoring'); await sleep(3500); await expect('strength', 'scoring → strength');
  await p.keyboard.press('ArrowRight'); await expect('fix1', 'swipe → fix1');
  await click('Try it'); await click('Try it'); const pill = await p.$eval('.pscreen.on .bo-result', e => e.textContent); console.log('  fix1 pill after 2 tries:', pill.trim());
  for (let i = 0; i < 3; i++) await p.keyboard.press('ArrowRight'); await expect('skills', 'swipe → skills');
  await click('Practise (3 min)'); await expect('drill1', 'practise');
  await click('very'); await sleep(1200); await expect('drill2', 'pairs → shadow');
  await click('Next drill'); await expect('drill3', 'shadow → say better'); await click('.bo-mic'); await expect('drilldone', 'say better → done');
  await click('Done'); await expect('save', 'done → save'); await click('Not now'); await expect('notnow', 'not now');
  await click('Stay a guest'); await expect('reminder', 'stay guest');
  await click('Night'); const rb = await p.$eval('.pscreen.on .bo-btn--primary', e => e.textContent); console.log('  reminder button:', rb.trim());
  await click('Remind me'); await sleep(1900); await expect('home', 'reminder → home');
  await click('Change'); await expect('scenario', 'change'); await click('Realistic'); await sleep(1200); await expect('home', 'scenario → home');
  await click('.bo-tab:nth-child(2)'); await expect('progress', 'tab progress'); await click('.bo-tab:nth-child(3)'); await expect('me', 'tab me');
  await click('Your plan'); await expect('plans', 'me → plans'); await click('Premium'); const pb = await p.$eval('.pscreen.on .bo-btn--primary', e => e.textContent); console.log('  plan button:', pb.trim());
  await click('Get '); await expect('pay', 'plans → pay'); await click('Pay with'); await sleep(1700); await expect('home', 'pay → home');
  await jump('me'); await click('Settings'); await expect('settings', 'settings'); await click('.bo-back'); await expect('me', 'settings back');
  await click('Privacy'); await expect('privacy', 'privacy'); await click('.bo-back'); await expect('me', 'privacy back');
  await click('Invite friends'); await expect('invite', 'invite'); await click('.bo-back'); await expect('me', 'invite back');
  await jump('progress21'); await click('[data-go]'); await expect('history', 'see all history'); await click('.bo-session'); await expect('hsession', 'history row');
  await click('Practise these'); await expect('drill1', 'practise these');
  await jump('talk'); await sleep(300); await click('End'); await expect('endsheet', 'end'); await click('End and get feedback'); await expect('scoring', 'end → scoring');
  await jump('talk'); await click('Change scene'); await expect('scenepick', 'scene btn'); await click('Download now'); await sleep(1700); await expect('talkdrive', 'download → drive');
  await jump('freelimit'); await click('Invite 3 friends'); await expect('invite', 'freelimit → invite');
  await jump('meplan'); await click('Cancel plan'); await expect('cancel', 'cancel'); await click('Keep Premium'); await expect('meplan', 'keep');
  await jump('welcome'); await click('I already have an account'); await expect('restore', 'restore'); await click('Go to Home'); await expect('home', 'restore → home');
  await jump('save'); await click('Use phone number'); await expect('phone', 'phone'); await click('Send code'); await expect('code', 'code'); await sleep(4000); await expect('reminder', 'code → reminder');
  await jump('guestlimit'); await click('[data-scrim]'); await expect('home', 'scrim close');
  await p.setViewport({ width: 400, height: 860, isMobile: true, hasTouch: true }); await jump('home'); await sleep(300);
  const mob = await p.evaluate(() => { const r = document.querySelector('.pscreen.on').getBoundingClientRect(); return [Math.round(r.width), Math.round(r.height)]; }); console.log('  mobile screen size:', mob.join('×'));
  await p.screenshot({ path: process.argv[3] });
  console.log(`steps ${steps}, failures ${fails}, page errors ${errs.length}`); errs.slice(0, 5).forEach(e => console.log('  err:', e));
  await b.close();
})();
