const puppeteer = require('puppeteer-core');
(async () => {
  const b = await puppeteer.launch({ executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: 'new' });
  const p = await b.newPage(); await p.setViewport({ width: 1440, height: 900 });
  const errs = []; p.on('pageerror', e => errs.push(e.message));
  const sleep = ms => new Promise(r => setTimeout(r, ms)); let fails = 0, steps = 0;
  const cur = () => p.evaluate(() => document.querySelector('.dk-screen.on').dataset.screen);
  async function exp(id, l) { steps++; const c = await cur(); if (c !== id) { fails++; console.log(`  ✗ ${l}: want ${id}, got ${c}`); } }
  async function click(sel) { const ok = await p.evaluate(sel => { const s = document.querySelector('.dk-screen.on'); let el = null;
      if (sel.startsWith('text=')) { const t = sel.slice(5).toLowerCase(); el = [...s.querySelectorAll('button, a, [data-go], [data-act], .dk-item, .bo-chip')].find(e => e.textContent.replace(/\s+/g, ' ').toLowerCase().includes(t)); }
      else el = s.querySelector(sel); if (!el) return false; el.click(); return true; }, sel); if (!ok) { fails++; console.log('  ✗ missing', sel, 'on', await cur()); } }
  async function waitTurn() { for (let i = 0; i < 40; i++) { if (await p.evaluate(() => document.querySelector('[data-screen="talk"] .bo-mic').className.includes('turn'))) return; await sleep(250); } fails++; console.log('  ✗ no turn'); }
  await p.goto('file://' + process.argv[2]); await sleep(400); await exp('start', 'opens on start');
  await click('text=Meet Bolo'); await exp('welcome', 'meet');
  await click('text=Skip intro'); await exp('consent', 'skip');
  await click('text=Yes, keep them'); await sleep(1700); await exp('mic', 'consent → mic');
  await click('text=Sounds good'); await exp('setup', 'mic → setup');
  for (let i = 0; i < 3; i++) { await p.keyboard.press('1'); await p.keyboard.press('Enter'); } await exp('day1', 'setup ×3 (keys)');
  await p.keyboard.press('Space'); await sleep(2000); await exp('talk', 'space stops recording');
  for (let i = 0; i < 4; i++) { await waitTurn(); await p.keyboard.press('Space'); }
  await sleep(6500); await exp('scoring', 'conversation ends'); await sleep(3400); await exp('debrief', 'scoring → debrief');
  await p.keyboard.press('ArrowDown'); await p.keyboard.press('t'); await p.keyboard.press('t');
  const pill = await p.evaluate(() => document.querySelector('.dk-detail.on [data-pill]').textContent); console.log('  pill:', pill.trim());
  for (let i = 0; i < 3; i++) await p.keyboard.press('ArrowDown'); await click('text=Practise (3 min)'); await exp('drill', 'practise');
  await p.keyboard.press('1'); await sleep(1300); await exp('drilldone', 'key 1 answers');
  await click('text=Done'); await exp('save', 'done → save'); await click('text=Continue with Google'); await exp('reminder', 'google');
  await click('[data-act="slot"]'); await click('[data-act="privnote"]');
  if (!(await p.evaluate(() => document.querySelector('[data-notif-text]').textContent.includes("today's talk")))) { fails++; console.log('  ✗ private text'); } steps++;
  await click('text=Turn on reminders'); await exp('reminder', 'prompt shows'); await click('[data-act="perm-block"]'); await exp('reminder', 'block stays');
  await click('text=Turn on reminders'); await click('[data-act="perm-allow"]'); await sleep(1900); await exp('home', 'allow → home');
  await p.keyboard.press('Space'); await exp('talk', 'space on home talks'); await p.keyboard.press('Escape'); await exp('endsheet', 'esc ends'); await p.keyboard.press('Escape'); await exp('talk', 'esc keeps talking');
  await click('text=End'); await click('text=End and get feedback'); await exp('scoring', 'end → feedback');
  await p.evaluate(() => location.hash = 'home'); await sleep(300); await click('.dk-nav:nth-of-type(2)'); await exp('progress', 'sidebar progress');
  await click('text=Me'); await exp('me', 'sidebar me'); await click('[data-act="toggle"]');
  await click('text=See plans'); await exp('plans', 'side see plans'); await click('[data-name="Premium"]');
  const btn = await p.evaluate(() => document.querySelector('[data-plan-btn]').textContent); console.log('  plan button:', btn);
  await click('[data-plan-btn]'); await sleep(2400); await exp('home', 'buy → home');
  await p.setViewport({ width: 1024, height: 700 }); await sleep(300); const sc = await p.evaluate(() => document.getElementById('root').style.transform); console.log('  1024×700 scale:', sc);
  console.log(`steps ${steps}, failures ${fails}, page errors ${errs.length}`, errs.slice(0, 3)); await b.close();
})();
