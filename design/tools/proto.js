(function () {
  var dev = document.getElementById('device');
  var screens = {};
  dev.querySelectorAll('.pscreen').forEach(function (s) { screens[s.dataset.screen] = s; });
  var cur = null, timers = [], t0 = null, firstWord = null, voiceOn = false;
  var toastEl = document.getElementById('toast'), timerEl = document.getElementById('timer');
  function later(fn, ms) { var t = setTimeout(fn, ms); timers.push(t); return t; }
  function clearTimers() { timers.forEach(clearTimeout); timers = []; }
  function toast(msg, ms) { toastEl.textContent = msg; toastEl.classList.add('show'); clearTimeout(toast._t); toast._t = setTimeout(function () { toastEl.classList.remove('show'); }, ms || 2000); }
  function say(text) { if (!voiceOn) return; try { speechSynthesis.cancel(); var u = new SpeechSynthesisUtterance(text); u.lang = 'en-GB'; u.rate = 0.95; speechSynthesis.speak(u); } catch (e) {} }
  function hush() { try { speechSynthesis.cancel(); } catch (e) {} }
  function label(el) {
    var a = el.getAttribute('aria-label'); if (a) return a.toLowerCase();
    var c = el.cloneNode(true); c.querySelectorAll('.bo-icon').forEach(function (i) { i.remove(); });
    return c.textContent.replace(/\s+/g, ' ').trim().toLowerCase();
  }
  function go(id) {
    if (!screens[id]) return;
    clearTimers(); hush();
    if (cur) screens[cur].classList.remove('on');
    cur = id; screens[id].classList.add('on');
    if (location.hash !== '#' + id) location.hash = id;
    document.querySelectorAll('[data-jump]').forEach(function (a) { a.classList.toggle('cur', a.dataset.jump === id); });
    if (ENTER[id]) ENTER[id](screens[id]);
  }
  function typeCaption(el, text, done) {
    var words = text.split(' ');
    el.innerHTML = words.map(function (w) { return '<span class="w">' + w + '</span>'; }).join(' ');
    var spans = el.querySelectorAll('.w'), i = 0;
    (function step() { if (i < spans.length) { spans[i++].classList.add('on'); later(step, 170); } else if (done) later(done, 450); })();
  }
  // first-word timer: from the welcome to the first question's open mic
  setInterval(function () { if (t0 && firstWord === null) timerEl.textContent = fmt((performance.now() - t0) / 1000); }, 250);
  function fmt(s) { s = Math.round(s); return Math.floor(s / 60) + ':' + ('0' + (s % 60)).slice(-2); }

  // ---------- conversation ----------
  var LINES = ['So, Rafi, what did you work on today?', 'Oh nice. What was the bug doing before you fixed it?', 'That sounds stressful. How did you find it?', 'Smart. Do you think the fix will go live tomorrow?'];
  var BYE = "I have to go now. Tell me tomorrow how it went.";
  function convo(s) {
    var cap = s.querySelector('.bo-caption__text'), ring = s.querySelector('.bo-mic'), core = ring.querySelector('.bo-mic__core .bo-icon'), arc = ring.querySelector('circle.left');
    function setRing(st) { ring.className = 'bo-mic bo-mic--' + st; core.textContent = { buddy: 'graphic_eq', turn: 'mic', thinking: 'more_horiz', paused: 'pause' }[st];
      ring.setAttribute('aria-label', { buddy: 'Bolo is speaking', turn: 'Your turn', thinking: 'Bolo is thinking', paused: 'Paused' }[st]); s._state = st; }
    function setArc(f) { arc.setAttribute('stroke-dasharray', (289 * f).toFixed(1) + ' 289.0'); }
    function bolo(i) { var line = i < LINES.length ? LINES[i] : BYE; setRing('buddy'); s._line = line; say(line);
      typeCaption(cap, line, function () { if (i < LINES.length) setRing('turn'); else { s._ended = true; later(function () { go('scoring'); }, 1600); } }); }
    s._next = function () { if (s._state !== 'turn') return; s._turn++; setArc(Math.max(0.08, 1 - s._turn * 0.22)); setRing('thinking'); later(function () { bolo(s._turn); }, 900); };
    s._pause = function () { if (s._state === 'paused') { setRing('turn'); toast('Back to it'); } else if (s._state === 'turn') { setRing('paused'); toast('Paused. Take your time.'); } };
    s._repeat = function () { if (s._line) { say(s._line); toast('Bolo says it again'); } };
    if (!s._started || s._ended) { s._started = true; s._ended = false; s._turn = 0; setArc(1); setRing('buddy'); cap.textContent = ''; later(function () { bolo(0); }, 600); }
    else if (s._state === 'thinking' || s._state === 'buddy') { later(function () { bolo(s._turn); }, 400); }
  }
  // ---------- screen entry ----------
  var ENTER = {
    splash: function () { t0 = null; firstWord = null; timerEl.textContent = '–'; later(function () { go('welcome'); }, 1600); },
    welcome: function (s) { if (!t0) t0 = performance.now(); var cap = s.querySelector('.bo-caption__text'); var line = "Hi, I'm Bolo. We'll just talk, like friends. No one is grading you."; say(line);
      typeCaption(cap, line, function () { later(function () { go('consent'); }, 1400); }); },
    q1: function () { if (t0 && firstWord === null) { firstWord = (performance.now() - t0) / 1000; timerEl.textContent = fmt(firstWord); } },
    miccheck: function (s) { var bars = s.querySelectorAll('.bo-level i'), n = 0;
      (function tick() { bars.forEach(function (b, i) { var h = 6 + Math.round(Math.random() * 60 * Math.sin(Math.PI * (i + 1) / (bars.length + 1))); b.style.height = h + 'px'; b.classList.toggle('on', h > 14); }); if (++n < 22) later(tick, 140); else { toast('Sounds great', 1200); later(function () { go('day1'); }, 1300); } })(); },
    day1: function (s) { var btn = s.querySelector('.bo-rec__btn'), arc = s.querySelector('.bo-rec circle.left'), left = 18;
      (function tick() { btn.firstChild.textContent = '0:' + ('0' + left).slice(-2); arc.setAttribute('stroke-dasharray', (502.7 * left / 30).toFixed(1) + ' 502.7');
        if (left-- > 0) later(tick, 1000); else saved(); })(); },
    talk: convo, talkdrive: convo,
    scoring: function () { later(function () { go('strength'); }, 3200); },
    code: function (s) { var cells = s.querySelectorAll('.bo-code__cell'), d = '481927', i = 3;
      (function tick() { if (i < 6) { cells[i].textContent = d[i]; cells[i].className = 'bo-code__cell bo-code__cell--filled'; if (cells[i + 1]) cells[i + 1].className = 'bo-code__cell bo-code__cell--next'; i++; later(tick, 380); }
        else { toast('Signed in. Your progress is saved.'); later(function () { go('reminder'); }, 1400); } })(); },
    drill3: function () { later(function () { go('drilldone'); }, 6000); }
  };
  function saved() { toast("Saved. We'll play this back to you on Day 7.", 1800); later(function () { go('talk'); }, 1900); }
  function tryFix(s) { var pill = s.querySelector('.bo-result'); if (!pill) return; s._tries = (s._tries || 0) + 1; pill.style.visibility = 'visible';
    if (s._tries === 1) { pill.className = 'bo-result bo-result--closer'; pill.innerHTML = '<span class="bo-icon" aria-hidden="true">north_east</span>Closer'; }
    else { pill.className = 'bo-result bo-result--nailed'; pill.innerHTML = '<span class="bo-icon bo-icon--fill" aria-hidden="true">check_circle</span>Nailed it'; } }
  var DEB = ['strength', 'fix1', 'fix2', 'fix3', 'skills'];
  function deb(step) { var i = DEB.indexOf(cur); if (i < 0) return; var j = i + step; if (j >= 0 && j < DEB.length) go(DEB[j]); }
  // ---------- routes: [match, target] where match is a CSS selector (starts with . or [) or label text ----------
  var R = {
    welcome: [['skip intro', 'consent'], ['i already have an account', 'restore']],
    restore: [['go to home', 'home']],
    consent: [['yes, keep them', consentGo], ['no, just give feedback', consentGo], ["i'm under 13", function () { toast('Bolo is for ages 13 and up'); }]],
    mic: [['allow microphone', function () { toast('Android: Allow Bolo to record audio? → While using the app', 1600); later(function () { go('q1'); }, 1700); }]],
    q1: [['next', 'q2'], ['back', 'mic'], ['.bo-mic', function () { toast('Listening… (the tester answers out loud)'); }]],
    q2: [['next', 'q3'], ['back', 'q1'], ['.bo-mic', function () { toast('Listening… (the tester answers out loud)'); }]],
    q3: [['next', 'miccheck'], ['back', 'q2'], ['.bo-mic', function () { toast('Listening… (the tester answers out loud)'); }]],
    miccheck: [['back', 'q3']], day1: [['back', 'miccheck'], ['.bo-rec__btn', function () { clearTimers(); saved(); }]],
    talk: CONVO(), talkdrive: CONVO(),
    endsheet: [['keep talking', 'talk'], ['end and get feedback', 'scoring']],
    scenepick: [['download now', function () { toast('Downloading Evening drive · 2.8 MB'); later(function () { go('talkdrive'); }, 1500); }], ['wait for wi-fi', 'talk'], ['hilltop', 'talk'], ['voice only', function () { toast('Voice only: just the mic ring, no scene'); }], ['.bo-pick', function () { toast('Companion changed'); }]],
    strength: DEBR(), fix1: DEBR(), fix2: DEBR(), fix3: DEBR(),
    skills: DEBR().concat([['practise (3 min)', 'drill1'], ['later', 'save']]),
    drill1: [['very', function (s, el) { el.classList.add('bo-pair--right'); toast("That's it", 900); later(function () { go('drill2'); }, 1000); }], ['berry', function () { toast('Listen once more, slowly'); }], ['hear it again', function () { say('very'); toast('Bolo plays one word'); }], ['skip', 'drill2'], ['close drills', 'save']],
    drill2: [['try again', function (s) { tryFix(s); }], ['next drill', 'drill3'], ['skip', 'drill3'], ['close drills', 'save']],
    drill3: [['.bo-mic', 'drilldone'], ['skip', 'drilldone'], ['close drills', 'save']],
    drilldone: [['done', 'save']],
    save: [['continue with google', function () { toast('Signed in as rafi.ahmed@gmail.com'); later(function () { go('reminder'); }, 1400); }], ['use phone number', 'phone'], ['not now', 'notnow']],
    notnow: [['save my progress', 'save'], ['stay a guest', 'reminder']],
    phone: [['back', 'save'], ['send code', 'code']], code: [['back', 'phone'], ['change', 'phone']],
    reminder: [['remind me', function () { toast('Android: Allow Bolo to send notifications? → Allow', 1600); later(function () { go('home'); }, 1700); }], ['not now', 'home'], ['pick an exact time', function () { toast("Android's time picker opens here"); }]],
    home: [['talk', 'talk'], ['change', 'scenario'], ['.bo-homecard', 'reminder']],
    scenario: [['free talk', function () { toast('Free talk · 5 min'); later(function () { go('home'); }, 900); }], ['friendly', standup], ['realistic', standup]],
    progress21: [], history: [['.bo-back', 'progress21'], ['.bo-session', 'hsession']],
    hsession: [['.bo-back', 'history'], ['practise these', 'drill1'], ['delete recording', function () { toast('A sheet asks: Delete this recording? Keep it / Delete'); }]],
    me: [['your plan', 'plans'], ['invite friends', 'invite'], ['settings', 'settings'], ['privacy', 'privacy']],
    settings: [['.bo-back', 'me'], ['scene and companion', 'scenepick']], privacy: [['.bo-back', 'me'], ['delete everything', function () { toast('A sheet asks you to type DELETE to confirm'); }]],
    plans: [['back', 'me'], ['get ', 'pay']],
    pay: [['pay with', function () { toast('bKash checkout opens here'); later(function () { go('home'); later(function () { toast('Basic is on. More time to talk.'); }, 300); }, 1500); }]],
    meplan: [['.bo-back', 'me'], ['change plan', 'plans'], ['cancel plan', 'cancel']],
    cancel: [['keep premium', 'meplan'], ['cancel plan', function () { toast('Cancelled. Premium stays until 6 December.'); later(function () { go('meplan'); }, 1500); }]],
    invite: [['.bo-back', 'me'], ['share invite link', function () { toast("Android's share sheet: WhatsApp, Messenger, imo…"); }], ['copy', function () { toast('Link copied'); }]],
    freelimit: [['see plans', 'plans'], ['invite 3 friends', 'invite'], ['wait for', 'home']],
    guestlimit: [['continue with google', function () { toast('Signed in. Your 3 talks are saved.'); later(function () { go('talk'); }, 1400); }], ['use phone number', 'phone'], ['not now', 'home']]
  };
  var SCRIM = { endsheet: 'talk', scenepick: 'talk', notnow: 'save', freelimit: 'home', guestlimit: 'home', scenario: 'home', pay: 'plans', cancel: 'meplan' };
  function CONVO() { return [['end', 'endsheet'], ['change scene', 'scenepick'], ['done, end my turn', function (s) { s._next && s._next(); }], ['pause', function (s) { s._pause && s._pause(); }], ['repeat that', function (s) { s._repeat && s._repeat(); }], ['.bo-mic', function (s) { s._next && s._next(); }]]; }
  function DEBR() { return [['try it', function (s) { tryFix(s); }], ['try again', function (s) { tryFix(s); }], ["that's not what i said", function () { toast("Thanks. We'll leave this one out."); }], ['close feedback', 'skills'], ['bangla', function () { toast('উপরের দাঁত নিচের ঠোঁটে ছুঁইয়ে v বলুন।'); }], ['play', function () { toast('Plays the clip'); }], ['hear that moment', function () { toast('Plays your 40 seconds'); }]]; }
  function consentGo(s) { if (!s.querySelector('.bo-check--on')) { toast("Tick “I'm 13 or older” first"); return; } go('mic'); }
  function standup(s, el) { toast('Standup rehearsal · ' + label(el).replace(/^./, function (c) { return c.toUpperCase(); })); later(function () { go('home'); }, 1000); }

  dev.addEventListener('click', function (e) {
    var s = screens[cur];
    var dg = e.target.closest('[data-go]'); if (dg) { e.preventDefault(); go(dg.dataset.go); return; }
    if (e.target.closest('[data-scrim]')) { if (SCRIM[cur]) go(SCRIM[cur]); return; }
    var el = e.target.closest('button, a, .bo-row, .bo-homecard, .bo-plan, .bo-pick, .bo-slot, .bo-session, label.bo-check, .bo-pair, .bo-mic, .bo-rec__btn, .bo-caption__replay');
    if (!el) { if (s.classList.contains('debrief')) { var r = s.getBoundingClientRect(), x = (e.clientX - r.left) / r.width; if (x > 0.66) deb(1); else if (x < 0.2) deb(-1); } return; }
    e.preventDefault();
    // generic behaviours
    if (el.classList.contains('bo-tab')) { var t = label(el); go(t === 'progress' ? (s.dataset.screen === 'progress21' ? 'progress21' : 'progress') : t === 'me' ? 'me' : 'home'); return; }
    if (el.classList.contains('bo-chip')) { el.parentElement.querySelectorAll('.bo-chip').forEach(function (c) { var on = c === el; c.setAttribute('aria-pressed', on); var ic = c.querySelector('.bo-icon'); if (ic) ic.remove(); if (on) c.insertAdjacentHTML('afterbegin', '<span class="bo-icon bo-icon--sm" aria-hidden="true">check</span>'); }); }
    if (el.classList.contains('bo-toggle')) { el.setAttribute('aria-checked', el.getAttribute('aria-checked') !== 'true'); return; }
    if (el.classList.contains('bo-check')) { el.classList.toggle('bo-check--on'); return; }
    if (el.classList.contains('bo-slot')) { s.querySelectorAll('.bo-slot').forEach(function (c) { c.setAttribute('aria-checked', c === el); }); var tm = el.querySelector('.bo-slot__time').textContent;
      s.querySelectorAll('.bo-btn--primary').forEach(function (b) { b.textContent = 'Remind me at ' + tm; }); s.querySelector('.bo-notif__meta').textContent = 'Bolo · ' + tm; return; }
    if (el.classList.contains('bo-plan') && !el.classList.contains('bo-plan--current') && cur === 'plans') { s.querySelectorAll('.bo-plan').forEach(function (c) { if (!c.classList.contains('bo-plan--current')) c.setAttribute('aria-checked', c === el); });
      var nm = el.querySelector('.bo-plan__name').textContent.trim(), pr = el.querySelector('.bo-plan__price').firstChild.textContent.trim(); s.querySelector('.bo-btn--primary').textContent = 'Get ' + nm + ' · ' + pr + ' a month'; return; }
    if (el.classList.contains('bo-pick')) { el.parentElement.querySelectorAll('.bo-pick').forEach(function (c) { c.setAttribute('aria-checked', c === el); }); }
    var txt = label(el), routes = R[cur] || [];
    // exact label first, then partial label, then selectors
    for (var pass = 0; pass < 3; pass++) {
      for (var i = 0; i < routes.length; i++) {
        var m = routes[i][0], tgt = routes[i][1], sel = (m[0] === '.' || m[0] === '[');
        var hit = pass === 0 ? (!sel && txt === m) : pass === 1 ? (!sel && txt.indexOf(m) !== -1) : (sel && (el.matches(m) || !!el.closest(m)));
        if (hit) { if (typeof tgt === 'function') tgt(s, el); else go(tgt); return; }
      }
    }
    if (el.classList.contains('bo-row') || el.classList.contains('bo-homecard')) toast('Not part of this prototype');
  });
  // swipe between feedback cards
  var sx = null;
  dev.addEventListener('touchstart', function (e) { sx = e.touches[0].clientX; }, { passive: true });
  dev.addEventListener('touchend', function (e) { if (sx === null) return; var dx = e.changedTouches[0].clientX - sx; sx = null; if (Math.abs(dx) > 50) deb(dx < 0 ? 1 : -1); });
  document.addEventListener('keydown', function (e) { if (e.key === 'ArrowRight') deb(1); if (e.key === 'ArrowLeft') deb(-1); });
  // panel
  var panel = document.getElementById('panel');
  document.querySelectorAll('[data-jump]').forEach(function (a) { a.addEventListener('click', function (e) { e.preventDefault(); go(a.dataset.jump); panel.classList.remove('open'); }); });
  document.getElementById('restart').onclick = function () { Object.keys(screens).forEach(function (k) { screens[k]._started = false; screens[k]._tries = 0; }); go('splash'); panel.classList.remove('open'); };
  var themeBtn = document.getElementById('theme');
  themeBtn.onclick = function () { var n = dev.dataset.theme === 'daylight' ? 'dusk' : 'daylight'; dev.dataset.theme = n; themeBtn.textContent = n === 'dusk' ? 'Day theme' : 'Night theme'; };
  document.getElementById('voice').onchange = function (e) { voiceOn = e.target.checked; if (!voiceOn) hush(); };
  document.getElementById('panelOpen').onclick = function () { panel.classList.add('open'); };
  document.getElementById('panelClose').onclick = function () { panel.classList.remove('open'); };
  window.addEventListener('hashchange', function () { var id = location.hash.slice(1); if (id && id !== cur && screens[id]) go(id); });
  function fit() { var st = dev.parentElement; if (innerWidth <= 560) { dev.style.transform = ''; st.style.width = ''; st.style.height = ''; return; }
    var k = Math.min(1, (innerHeight - 32) / 780); dev.style.transform = 'scale(' + k + ')'; st.style.width = (380 * k) + 'px'; st.style.height = (780 * k) + 'px'; }
  addEventListener('resize', fit); fit();
  var start = location.hash.slice(1); go(screens[start] ? start : 'splash');
})();
