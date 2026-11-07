(function () {
  var root = document.getElementById('root'), screens = {}, cur = null, timers = [], voiceOn = false;
  root.querySelectorAll('.dk-screen').forEach(function (s) { screens[s.dataset.screen] = s; });
  var toastEl = document.getElementById('toast');
  function later(f, ms) { var t = setTimeout(f, ms); timers.push(t); return t; }
  function toast(m, ms) { toastEl.textContent = m; toastEl.classList.add('show'); clearTimeout(toast._t); toast._t = setTimeout(function () { toastEl.classList.remove('show'); }, ms || 2200); }
  function say(t) { if (!voiceOn) return; try { speechSynthesis.cancel(); var u = new SpeechSynthesisUtterance(t); u.lang = 'en-GB'; u.rate = .95; speechSynthesis.speak(u); } catch (e) {} }
  function hush() { try { speechSynthesis.cancel(); } catch (e) {} }
  function go(id) { if (!screens[id]) return; timers.forEach(clearTimeout); timers = []; hush();
    if (cur) screens[cur].classList.remove('on'); cur = id; screens[id].classList.add('on'); document.title = 'Bolo';
    if (location.hash !== '#' + id) location.hash = id;
    document.querySelectorAll('[data-jump]').forEach(function (a) { a.classList.toggle('cur', a.dataset.jump === id); });
    if (ENTER[id]) ENTER[id](screens[id]); }
  function type(el, text, done) { var w = text.split(' '); el.innerHTML = w.map(function (x) { return '<span class="w">' + x + '</span>'; }).join(' ');
    var sp = el.querySelectorAll('.w'), i = 0; (function step() { if (i < sp.length) { sp[i++].classList.add('on'); later(step, 170); } else if (done) later(done, 450); })(); }
  // conversation
  var LINES = ['So, Rafi, what did you work on today?', 'Oh nice. What was the bug doing before you fixed it?', 'That sounds stressful. How did you find it?', 'Smart. Do you think the fix will go live tomorrow?'], BYE = 'I have to go now. Tell me tomorrow how it went.';
  var C = { s: null, turn: 0, state: 'idle', ended: true };
  function ring(st) { var s = screens.talk, r = s.querySelector('.bo-mic'), core = r.querySelector('.bo-mic__core .bo-icon');
    r.className = 'bo-mic bo-mic--' + st; core.textContent = { buddy: 'graphic_eq', turn: 'mic', thinking: 'more_horiz', paused: 'pause' }[st];
    r.setAttribute('aria-label', { buddy: 'Bolo is speaking', turn: 'Your turn', thinking: 'Bolo is thinking', paused: 'Paused' }[st]); C.state = st; }
  function arc(f) { screens.talk.querySelector('.bo-mic circle.left').setAttribute('stroke-dasharray', (289 * f).toFixed(1) + ' 289.0'); }
  function bolo(i) { var line = i < LINES.length ? LINES[i] : BYE; C.line = line; ring('buddy'); say(line);
    type(screens.talk.querySelector('[data-cap]'), line, function () { if (i < LINES.length) ring('turn'); else { C.ended = true; later(function () { go('scoring'); }, 1600); } }); }
  function next() { if (C.state !== 'turn') return; C.turn++; arc(Math.max(.08, 1 - C.turn * .22)); ring('thinking'); later(function () { bolo(C.turn); }, 900); }
  function pause() { if (C.state === 'paused') { ring('turn'); toast('Back to it'); } else if (C.state === 'turn') { ring('paused'); toast('Paused. Take your time.'); } }
  // screen entry
  var Q = [['What do you need English for most?', 'ইংরেজি আপনার সবচেয়ে বেশি কোথায় দরকার?', ['Work meetings', 'Job interviews', 'Study abroad', 'Daily confidence']],
           ['Which moment feels hardest?', 'কোন মুহূর্তটা সবচেয়ে কঠিন লাগে?', ['Speaking up in meetings', 'Interviews', 'Phone calls', 'Small talk']],
           ['How long do you want to talk each day?', 'প্রতিদিন কতক্ষণ কথা বলতে চান?', ['5 minutes', '10 minutes']]], qi = 0;
  function showQ(s) { var q = Q[qi]; s.querySelector('[data-q]').textContent = q[0]; s.querySelector('[data-qbn]').textContent = q[1]; s.querySelector('[data-qn]').textContent = (qi + 3) + ' of 5';
    s.querySelector('[data-chips]').innerHTML = q[2].map(function (c) { return '<button class="bo-chip" aria-pressed="false">' + c + '</button>'; }).join(''); }
  var ENTER = {
    welcome: function (s) { var line = "Hi, I'm Bolo. We'll just talk, like friends. No one is grading you."; say(line); type(s.querySelector('[data-cap]'), line, function () { later(function () { go('consent'); }, 1500); }); },
    mic: function (s) { var b = s.querySelectorAll('[data-level] i'), n = 0; (function t() { b.forEach(function (x, i) { var h = 6 + Math.round(Math.random() * 60 * Math.sin(Math.PI * (i + 1) / (b.length + 1))); x.style.height = h + 'px'; x.classList.toggle('on', h > 14); }); if (++n < 400) later(t, 140); })(); },
    setup: function (s) { qi = 0; showQ(s); },
    day1: function (s) { var left = 18; (function t() { s.querySelector('[data-rect]').textContent = '0:' + ('0' + left).slice(-2); s.querySelector('[data-recarc]').setAttribute('stroke-dasharray', (502.7 * left / 30).toFixed(1) + ' 502.7'); if (left-- > 0) later(t, 1000); else saved(); })(); },
    talk: function () { if (C.ended) { C.ended = false; C.turn = 0; arc(1); screens.talk.querySelector('[data-cap]').textContent = ''; ring('buddy'); later(function () { bolo(0); }, 700); } else if (C.state !== 'turn' && C.state !== 'paused') later(function () { bolo(C.turn); }, 400); },
    scoring: function () { later(function () { go('debrief'); }, 3200); },
    debrief: function (s) { showDeb(s, 0); }
  };
  function saved() { toast("Saved. We'll play this back to you on Day 7."); later(function () { go('talk'); }, 1800); }
  function showDeb(s, i) { s._i = i; s.querySelectorAll('[data-deb]').forEach(function (e) { e.toggleAttribute('aria-current', +e.dataset.deb === i); if (+e.dataset.deb === i) e.setAttribute('aria-current', 'true'); });
    s.querySelectorAll('[data-detail]').forEach(function (d) { d.classList.toggle('on', +d.dataset.detail === i); }); }
  function tryIt(btn) { var box = btn.closest('.dk-detail') || btn.closest('article'), pill = box.querySelector('[data-pill]'); box._n = (box._n || 0) + 1; pill.style.visibility = 'visible';
    if (box._n === 1) { pill.className = 'bo-result bo-result--closer'; pill.innerHTML = '<span class="bo-icon" aria-hidden="true">north_east</span>Closer'; }
    else { pill.className = 'bo-result bo-result--nailed'; pill.innerHTML = '<span class="bo-icon bo-icon--fill" aria-hidden="true">check_circle</span>Nailed it'; } }
  root.addEventListener('click', function (e) {
    var s = screens[cur], el;
    if ((el = e.target.closest('.bo-chip'))) { el.parentElement.querySelectorAll('.bo-chip').forEach(function (c) { c.setAttribute('aria-pressed', c === el); }); return; }
    if ((el = e.target.closest('[data-deb]'))) { showDeb(s, +el.dataset.deb); return; }
    if ((el = e.target.closest('[data-act]'))) { var a = el.dataset.act;
      if (a === 'check') { el.classList.toggle('bo-check--on'); return; }
      if (a === 'nextq') { if (qi < 2) { qi++; showQ(s); } else go('day1'); return; }
      if (a === 'rec') { timers.forEach(clearTimeout); saved(); return; }
      if (a === 'ring' || a === 'done') { if (cur === 'talk') next(); return; }
      if (a === 'pause') { pause(); return; } if (a === 'repeat') { if (C.line) { say(C.line); toast('Bolo says it again'); } return; }
      if (a === 'try') { tryIt(el); return; }
      if (a === 'pair-right') { el.classList.add('bo-pair--right'); toast("That's it"); later(function () { go('drilldone'); }, 1100); return; }
      if (a === 'pair-other') { toast('Listen once more, slowly'); return; }
      if (a === 'slot') { s.querySelectorAll('.bo-slot').forEach(function (c) { c.setAttribute('aria-checked', c === el); }); var tt = s.querySelector('[data-slot-time]'); if (tt) tt.textContent = el.querySelector('.bo-slot__time').textContent; return; }
      if (a === 'privnote') { var on = el.getAttribute('aria-checked') !== 'true'; el.setAttribute('aria-checked', on); var nt = s.querySelector('[data-notif-text]'); nt.textContent = on ? nt.dataset.short : nt.dataset.full; return; }
      if (a === 'notify') { s.querySelector('.dk-perm').hidden = false; return; }
      if (a === 'perm-allow') { s.querySelector('.dk-perm').hidden = true; var tm = s.querySelector('[data-slot-time]').textContent; toast('Reminders on. Bolo checks in at ' + tm + ' if you haven\u2019t talked yet.'); later(function () { go('home'); }, 1600); return; }
      if (a === 'perm-block') { s.querySelector('.dk-perm').hidden = true; toast('No problem. Turn them on later from the icon beside the web address, or add Bolo to your calendar.', 3200); return; }
      if (a === 'toggle') { el.setAttribute('aria-checked', el.getAttribute('aria-checked') !== 'true'); return; }
      if (a === 'plan') { s.querySelectorAll('[data-act="plan"]').forEach(function (c) { c.setAttribute('aria-checked', c === el); }); s.querySelector('[data-plan-btn]').textContent = 'Get ' + el.dataset.name + ' · ৳' + el.dataset.price + ' a month'; return; }
      if (a === 'buy') { toast('bKash shows a QR code to scan with your bKash app'); later(function () { go('home'); later(function () { toast('Basic is on. More time to talk.'); }, 300); }, 2000); return; }
    }
    if ((el = e.target.closest('[data-go]'))) { e.preventDefault();
      if (el.hasAttribute('data-need-check') && !s.querySelector('.bo-check--on')) { toast("Tick “I'm 13 or older” first"); return; }
      if (el.dataset.toast) toast(el.dataset.toast);
      var g = el.dataset.go; if (g === 'scenario-dlg') { toast('Choose: Free talk, or Standup rehearsal (Friendly / Realistic)'); return; }
      if (g === 'mic' && cur === 'consent') { toast('Your browser asks: Allow bolo.app to use your microphone? → Allow', 1800); later(function () { go('mic'); }, 1500); return; }
      go(g); return; }
    if ((el = e.target.closest('[data-toast]'))) { toast(el.dataset.toast); return; }
    if ((el = e.target.closest('.bo-row'))) toast('Not part of this prototype');
  });
  document.addEventListener('keydown', function (e) {
    if (e.target.closest && e.target.closest('input, textarea')) return;
    var k = e.key, s = screens[cur];
    if (k === '?') { toast('Space: talk / done · P: pause · R: repeat · Esc: end · ↑↓: feedback cards · 1–4: answers', 3500); return; }
    if (cur === 'talk') { if (k === ' ' || k === 'Enter') { e.preventDefault(); next(); } else if (k === 'p' || k === 'P') pause(); else if (k === 'r' || k === 'R') { if (C.line) { say(C.line); toast('Bolo says it again'); } } else if (k === 'Escape') go('endsheet'); return; }
    if (cur === 'endsheet' && k === 'Escape') { go('talk'); return; }
    if (cur === 'home' && k === ' ') { e.preventDefault(); go('talk'); return; }
    if (cur === 'day1' && k === ' ') { e.preventDefault(); timers.forEach(clearTimeout); saved(); return; }
    if (cur === 'setup' && /^[1-4]$/.test(k)) { var c = s.querySelectorAll('.bo-chip')[+k - 1]; if (c) c.click(); return; }
    if (cur === 'setup' && k === 'Enter') { s.querySelector('[data-act="nextq"]').click(); return; }
    if (cur === 'debrief') { if (k === 'ArrowDown' || k === 'ArrowRight') { e.preventDefault(); showDeb(s, Math.min(4, (s._i || 0) + 1)); } else if (k === 'ArrowUp' || k === 'ArrowLeft') { e.preventDefault(); showDeb(s, Math.max(0, (s._i || 0) - 1)); }
      else if (k === 't' || k === 'T') { var b = s.querySelector('.dk-detail.on [data-act="try"]'); if (b) tryIt(b); } else if (k === ' ') { e.preventDefault(); toast('Plays your clip'); } else if (k === 'Escape') go('home'); return; }
    if (cur === 'drill') { if (k === '1') s.querySelector('[data-act="pair-right"]').click(); if (k === '2') s.querySelector('[data-act="pair-other"]').click(); if (k === 'l' || k === 'L') toast('Bolo plays one word'); return; }
  });
  document.addEventListener('visibilitychange', function () { if (document.hidden && cur === 'talk' && C.state === 'turn') { ring('paused'); later(function () {}, 0); toast('Paused while you were away'); } });
  // facilitator panel
  var pnl = document.getElementById('ppanel');
  document.getElementById('pbtn').onclick = function () { pnl.classList.toggle('open'); };
  document.querySelectorAll('[data-jump]').forEach(function (a) { a.onclick = function (e) { e.preventDefault(); C.ended = true; go(a.dataset.jump); }; });
  document.getElementById('theme').onclick = function () { var n = root.dataset.theme === 'daylight' ? 'dusk' : 'daylight'; root.dataset.theme = n; this.textContent = n === 'dusk' ? 'Day theme' : 'Night theme'; };
  document.getElementById('restart').onclick = function () { C.ended = true; pnl.classList.remove('open'); go('start'); };
  document.getElementById('voice').onchange = function (e) { voiceOn = e.target.checked; if (!voiceOn) hush(); };
  window.addEventListener('hashchange', function () { var id = location.hash.slice(1); if (id && id !== cur && screens[id]) go(id); });
  // fit: below 1180 × 720 the 1280 × 800 canvas is scaled to fit
  function fit() { var w = innerWidth, h = innerHeight;
    if (w >= 1180 && h >= 700) { root.style.width = w + 'px'; root.style.height = h + 'px'; root.style.transform = ''; root.style.left = '0'; root.style.top = '0'; return; }
    var k = Math.min(w / 1280, h / 800); root.style.width = '1280px'; root.style.height = '800px'; root.style.transform = 'scale(' + k + ')';
    root.style.left = ((w - 1280 * k) / 2) + 'px'; root.style.top = ((h - 800 * k) / 2) + 'px'; }
  addEventListener('resize', fit); fit();
  var start = location.hash.slice(1); go(screens[start] ? start : 'start');
})();
