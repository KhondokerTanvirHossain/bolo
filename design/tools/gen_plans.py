import os, re, sys
S = sys.argv[1]; C = S + '/ds/project/components'
home = open(S + '/ds/home.html').read()
PHONE_INNER = re.search(r'<div class="bo-phone">(.*)</div></div>\s*</body>', home, re.S).group(1)
def icon(n, fill=False, cls=''):
    return f'<span class="bo-icon{" bo-icon--fill" if fill else ""}{(" " + cls) if cls else ""}" aria-hidden="true">{n}</span>'
def status(t='9:41'):
    return f'<div class="bo-statusbar"><span>{t}</span><span>{icon("signal_cellular_alt", cls="bo-icon--sm")} {icon("battery_5_bar", cls="bo-icon--sm")}</span></div>'
def page(title, card, body):
    return f'''{card}
<!doctype html>
<html lang="en">
<head><meta charset="utf-8"><title>{title} — Bolo</title></head>
<body>
<div class="bo-stage bo-stage--center" style="padding:24px 0">
{body}
</div>
</body>
</html>
'''
def phone(*parts): return '<div class="bo-phone">\n' + '\n'.join(parts) + '\n</div>'
def card(sub): return f'<!-- @dsCard group="Screens" height=820 subtitle="{sub}" page -->'
TK = '৳'
PLANS = [('basic', 'Basic', '20 talks a month', 300), ('premium', 'Premium', '50 talks a month', 500), ('ultimate', 'Ultimate', '100 talks a month', 1000)]
def plan_opt(key, name, meta, price, checked=False):
    return (f'<button class="bo-plan" role="radio" aria-checked="{"true" if checked else "false"}"><span class="bo-radio"></span>'
            f'<span class="bo-plan__body"><span class="bo-plan__name">{name}</span><span class="bo-plan__meta">{meta}</span></span>'
            f'<span class="bo-plan__price">{TK}{price}<small>a month</small></span></button>')
FREE_ROW = ('<div class="bo-plan bo-plan--current" role="radio" aria-checked="false" aria-disabled="true"><span class="bo-plan__body"><span class="bo-plan__name">Free <span class="bo-tag bo-tag--steady">Your plan</span></span>'
            '<span class="bo-plan__meta">7 talks in your first week, then 2 a month</span></span><span class="bo-plan__price">' + TK + '0</span></div>')
files = {}

# 1. Home with the free counter
h = PHONE_INNER.replace('Free talk · 5 min · <a href="#">Change</a></div>', 'Free talk · 5 min · <a href="#">Change</a></div>\n      <div class="footnote" style="color:var(--ink-secondary);margin-top:var(--space-1)">4 of 7 free talks left in your first week</div>')
assert '4 of 7 free talks' in h
files['ScreenHomeFree'] = (page('ScreenHomeFree', card('Free plan: a quiet counter, never a banner (first week shown)'), phone(h.strip())), '''# ScreenHomeFree

Home on the free plan (ACC-5, US-8.7). Everything is as on ScreenHome; one `footnote` line under the scenario line counts what's left. This preview shows the first week: "4 of 7 free talks left in your first week".

- A talk is one 5-minute conversation followed by the coach's feedback. Everyone gets **7 free talks in their first 7 days** (counted from the first talk), then **2 free talks every month**.
- After the first week, the line reads "1 of 2 free talks left this month". The day the week ends, it says once: "Your free week is over. From now on you get 2 free talks a month."
- The counter appears only on the free plan and never turns into a banner, a badge or a countdown. At 0 it reads "Free talks come back on 1 December".
- Paid plans show nothing here; their usage lives in Me → Plan. Near a fair-use limit, the same footnote line says so calmly.
''')

# 2. Limit sheet
lim = PHONE_INNER.replace('Free talk · 5 min · <a href="#">Change</a></div>', 'Free talk · 5 min · <a href="#">Change</a></div>\n      <div class="footnote" style="color:var(--ink-secondary);margin-top:var(--space-1)">Free talks come back on 1 December</div>')
sheet = f'''<div class="bo-sheet-scrim"></div>
  <div class="bo-sheet" role="dialog" aria-modal="true" aria-labelledby="fl-t">
    <div class="bo-sheet__grabber"></div>
    <h2 class="bo-sheet__title" id="fl-t">You've had this month's 2 free talks</h2>
    <p class="bo-sheet__body" style="margin-bottom:0">They come back on 1 December. To keep talking before then, pick a plan from {TK}300 a month.</p>
    <p class="bo-flow__bn" style="margin:var(--space-1) 0 var(--space-4)">১ ডিসেম্বর আবার ফ্রি টক পাবেন।</p>
    <button class="bo-btn bo-btn--primary bo-btn--block">See plans</button>
    <button class="bo-btn bo-btn--secondary bo-btn--block">{icon('group_add')}Invite 3 friends, get a free month</button>
    <button class="bo-btn bo-btn--quiet-neutral" style="align-self:center">Wait for 1 December</button>
  </div>'''
files['ScreenFreeLimit'] = (page('ScreenFreeLimit', card('The free talks are used: say when they return'), phone(lim.strip(), sheet)), '''# ScreenFreeLimit

What tapping **Talk** does once this month's 2 free talks are used (US-8.7). It is a Sheet over Home.

- The title states the fact; the body says when free talks return first, and the price second. No "Upgrade now", no countdown, no crossed-out prices.
- **See plans** opens ScreenPlans. **Invite 3 friends, get a free month** opens ScreenInvite (Bolo's first year only; hide it after). **Wait for 1 December** closes the sheet; Home's footnote keeps the date.
- Drills from past feedback stay free and open, so the learner can always practise something.
- A talk already in progress is never cut off by the limit; the limit is checked only when a new talk starts.
''')

# 3. Plans
plans = phone(status(), f'''<div class="bo-flow">
    <div class="bo-flow__top"><button class="bo-back" aria-label="Back">{icon('arrow_back')}</button></div>
    <h1 class="bo-flow__title" style="margin-top:var(--space-2)">Choose a plan</h1>
    <p class="bo-flow__body">Each talk is up to 5 minutes, then your coach's feedback.</p>
    <div style="height:var(--space-5)"></div>
    <div class="bo-plans" role="radiogroup" aria-label="Plan">
      {FREE_ROW}
      {plan_opt(*PLANS[0], checked=True)}
      {plan_opt(*PLANS[1])}
      {plan_opt(*PLANS[2])}
    </div>
    <p class="bo-footnote" style="text-align:left;margin-top:var(--space-4)">Every plan has both scenes, Standup Rehearsal, drills and your full history.</p>
    <div style="flex:1"></div>
    <div class="bo-flow__actions" style="gap:var(--space-2)">
      <button class="bo-btn bo-btn--primary bo-btn--block">Get Basic · {TK}300 a month</button>
      <p class="bo-footnote">Renews monthly. Cancel any time in 2 taps.</p>
    </div>
  </div>''')
files['ScreenPlans'] = (page('ScreenPlans', card('Three plans, one decision: how much you want to talk'), plans), f'''# ScreenPlans

The plan choice (ACC-5, ACC-6), from ScreenFreeLimit or Me → Plan → Change plan.

- **Free** sits first, dashed, marked "Your plan", so the learner sees where they stand: 7 talks in the first week, then 2 a month. It can't be selected.
- **Basic {TK}300, Premium {TK}500, Ultimate {TK}1000**, monthly. The plans differ only in how many talks you get; one line underneath says every plan has every feature.
- **Every plan states its limit as a number.** The talk counts shown (20, 50, 100 a month) are placeholders until the cost test. No plan is called "Unlimited", because every plan has a limit.
- Preselect **Basic**, the smallest step up; never preselect the most expensive. The primary button repeats the choice and price ("Get Basic · {TK}300 a month").
- No "Most popular" badge, no crossed-out prices, no timers. Prices are in taka with the ৳ sign.
''')

# 4. Payment sheet over plans
pay = f'''<div class="bo-sheet-scrim"></div>
  <div class="bo-sheet" role="dialog" aria-modal="true" aria-labelledby="pay-t">
    <div class="bo-sheet__grabber"></div>
    <h2 class="bo-sheet__title" id="pay-t">Pay {TK}300 a month</h2>
    <p class="bo-sheet__body" style="margin-bottom:var(--space-3)">Basic · 20 talks a month</p>
    <div class="bo-group" role="radiogroup" aria-label="Payment method" style="box-shadow:none;background:var(--surface-sunken)">
      <div class="bo-row" role="radio" aria-checked="true"><span class="bo-radio"></span><div class="bo-row__body"><div class="bo-row__title">bKash</div><div class="bo-row__sub">Pay from your bKash account</div></div></div>
      <div class="bo-row" role="radio" aria-checked="false"><span class="bo-radio"></span><div class="bo-row__body"><div class="bo-row__title">Card</div><div class="bo-row__sub">Visa, Mastercard or Amex</div></div>{icon('credit_card', cls='bo-chev')}</div>
    </div>
    <div style="height:var(--space-3)"></div>
    <button class="bo-btn bo-btn--primary bo-btn--block">Pay with bKash</button>
    <p class="bo-footnote" style="margin-top:var(--space-2)">Renews on the 6th of each month. Cancel any time in Me → Plan.</p>
  </div>'''
files['ScreenPlanPay'] = (page('ScreenPlanPay', card('bKash or card, then straight back to talking'), phone(status(), plans.split('\n', 2)[2].rsplit('</div>', 1)[0], pay)), '''# ScreenPlanPay

The payment step (ACC-6), a Sheet over ScreenPlans after tapping **Get Basic**.

- Two methods: **bKash** (preselected, because most learners have it) and **Card**. Show bKash's official logo here, taken from bKash's merchant brand kit; the preview uses the name only.
- The primary button names the method ("Pay with bKash"). bKash opens its own checkout; card opens the payment provider's secure page. Bolo never collects card numbers itself.
- One line states the renewal date and where to cancel, before paying, not after.
- **After paying:** close the sheet, show Home with the Buddy saying something warm on the scene card ("More time to talk. Where were we?"), and start nothing automatically.
- **If payment doesn't go through:** stay on this sheet with one calm line under the button, "bKash didn't confirm the payment. You haven't been charged.", and the same button. Never red.
''')

# 5. Me → Plan
meplan = phone(status(), f'''<div class="bo-flow">
    <div class="bo-flow__top"><button class="bo-back">{icon('arrow_back')}Me</button></div>
    <h1 class="large-title" style="margin:var(--space-2) 0 var(--space-5)">Your plan</h1>
    <div class="bo-card">
      <p class="bo-planhead">Premium</p>
      <p class="bo-planprice">{TK}500 <small>a month</small></p>
      <p class="bo-row__sub" style="margin:var(--space-1) 0 var(--space-5);font-size:14px">Renews on 6 December · bKash</p>
      <div class="bo-usage"><div class="bo-usage__label"><span>Talks this month</span><span>12 of 50</span></div><div class="bo-usage__track"><div class="bo-usage__fill" style="width:24%"></div></div></div>
    </div>
    <div style="height:var(--space-6)"></div>
    <div class="bo-group">
      <div class="bo-row"><div class="bo-row__body"><div class="bo-row__title">Change plan</div></div>{icon('chevron_right', cls='bo-chev')}</div>
      <div class="bo-row"><div class="bo-row__body"><div class="bo-row__title">Payment method</div></div><span class="bo-row__value">bKash</span>{icon('chevron_right', cls='bo-chev')}</div>
      <div class="bo-row"><div class="bo-row__body"><div class="bo-row__title">Payments</div></div>{icon('chevron_right', cls='bo-chev')}</div>
    </div>
    <div style="flex:1"></div>
    <button class="bo-btn bo-btn--quiet-neutral" style="align-self:center">Cancel plan</button>
  </div>''')
files['ScreenMePlan'] = (page('ScreenMePlan', card('What you pay, what you have used, how to leave'), meplan), '''# ScreenMePlan

Me → Plan for a paying learner (ACC-5, ACC-6).

- **The plan card:** the plan name in `hills-ink`, the price, the renewal date and method, and a UsageMeter for this month's talks against the plan's limit.
- **Rows:** Change plan (opens ScreenPlans), Payment method, Payments (a plain list of receipts).
- **Cancel plan** is a quiet button at the bottom, always visible, never hidden behind "Manage". Tapping it opens ScreenCancelPlan; confirming there is the second and last tap (ACC-6).
- **Changing plan:** moving up takes effect now and charges the difference for the rest of the month; moving down takes effect at renewal. Say which in one line before confirming.
- On the free plan this screen shows "Free · 2 talks a month", the usage meter, and **See plans** as its one primary button.
''')

# 6. Cancel sheet
cancel = f'''<div class="bo-sheet-scrim"></div>
  <div class="bo-sheet" role="dialog" aria-modal="true" aria-labelledby="cp-t">
    <div class="bo-sheet__grabber"></div>
    <h2 class="bo-sheet__title" id="cp-t">Cancel Premium?</h2>
    <p class="bo-sheet__body">You keep Premium until 6 December. After that you're on the free plan, with 2 talks a month. Your history and drills stay.</p>
    <button class="bo-btn bo-btn--primary bo-btn--block">Keep Premium</button>
    <button class="bo-btn bo-btn--secondary bo-btn--block">Cancel plan</button>
  </div>'''
files['ScreenCancelPlan'] = (page('ScreenCancelPlan', card('Two taps to cancel, and nothing is lost'), phone(meplan.split('\n', 1)[1].rsplit('</div>', 1)[0], cancel)), '''# ScreenCancelPlan

The second tap of "cancel in 2 taps" (ACC-6, US-8.7). A Sheet over ScreenMePlan.

- The body answers the three things people worry about: until when they keep the plan, what they move to, and that nothing is deleted.
- **Keep Premium** is the primary (the safe, no-change choice); **Cancel plan** is a full-size secondary button, never a hidden link, and one tap on it cancels. No survey, no discount offer, no "Are you sure?" after this.
- **After cancelling:** the sheet closes, the plan card reads "Premium until 6 December", and **Cancel plan** becomes **Restart Premium**.
- If the learner deletes their account with a plan active, explain how billing stops first (US-8.6).
''')

# 7. Invite friends (first year only)
def friend(initial=None):
    if initial:
        return f'<div class="bo-friend"><span class="bo-friend__av">{initial}</span>{icon("check_circle", True, "bo-friend__ok bo-icon--sm")}</div>'
    return f'<div class="bo-friend bo-friend--empty"><span class="bo-friend__av">{icon("person_add")}</span></div>'
invite = phone(status(), f'''<div class="bo-flow">
    <div class="bo-flow__top"><button class="bo-back">{icon('arrow_back')}Me</button></div>
    <h1 class="bo-flow__title" style="margin-top:var(--space-2)">Invite 3 friends, get a month of Basic</h1>
    <p class="bo-flow__body">When 3 friends you invite finish their first talk, Basic is yours free for a month.</p>
    <div class="bo-card" style="margin-top:var(--space-6)">
      <div class="bo-friends">{friend('N')}{friend('S')}{friend()}</div>
      <p class="bo-friends__label">2 of 3 friends have joined</p>
      <p class="bo-row__sub" style="text-align:center;margin:0">Nadia and Shuvo finished their first talk.</p>
    </div>
    <div class="bo-row" style="padding:var(--space-4) 0 0;min-height:auto"><div class="bo-row__body"><div class="bo-row__sub">Your invite link</div><div class="bo-row__title" style="font-variant-numeric:tabular-nums">bolo.app/i/rafi7</div></div><button class="bo-btn bo-btn--quiet">{icon('content_copy', cls='bo-icon--sm')}Copy</button></div>
    <div style="flex:1"></div>
    <div class="bo-flow__actions" style="gap:var(--space-2)">
      <button class="bo-btn bo-btn--primary bo-btn--block">{icon('share', cls='bo-icon--sm')}Share invite link</button>
      <p class="bo-footnote">For Bolo's first year. Bolo never reads your contacts or messages anyone.</p>
    </div>
  </div>''')
files['ScreenInvite'] = (page('ScreenInvite', card('Three friends talk once, you get a month of Basic'), invite), '''# ScreenInvite

The first-year referral offer: when 3 friends you invited finish their first talk, you get a month of Basic free. Reached from Me → Invite friends, from ScreenFreeLimit, and once from the end-of-free-week note. The offer, and every entry to it, disappears after Bolo's first year.

- **A friend counts when they finish their first talk**, not when they install or sign up, so the reward can't be farmed with spare SIMs.
- **Progress is three slots.** A joined friend shows their initial and first name; an empty slot is dashed. Never show who was invited but hasn't joined: no nudging friends, no social pressure.
- **Sharing is always by hand.** **Share invite link** opens Android's share sheet (WhatsApp, Messenger, imo, SMS). The prefilled message is neutral and editable ("I've been practising speaking with Bolo. Try it: bolo.app/i/rafi7") and never mentions anyone's level. Bolo never reads contacts and never sends anything itself.
- **Reward:** on the third friend, Home shows "A month of Basic is yours" on the scene card, and Me → Plan shows "Basic, free until 7 December". It doesn't renew and asks for no payment details. A learner who already pays gets the month as a credit on their next bill.
- How many free months one person can earn in the year is still open; the design works for once or for several.
''')

for name, (html, readme) in files.items():
    dd = f'{C}/{name}'; os.makedirs(dd, exist_ok=True)
    open(f'{dd}/preview.html', 'w').write(html); open(f'{dd}/README.md', 'w').write(readme)
print('ok', list(files))
