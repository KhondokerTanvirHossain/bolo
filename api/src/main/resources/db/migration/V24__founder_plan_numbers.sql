-- The founder's plan numbers (2026-11-12), replacing the placeholders: talks a month and taka price.
-- Dollar prices are unchanged (3, 5, 10). Still editable in /admin → Settings.
update plan_setting set monthly_talks = 30,  price_bdt = 300  where plan = 'basic';
update plan_setting set monthly_talks = 50,  price_bdt = 500  where plan = 'premium';
update plan_setting set monthly_talks = 100, price_bdt = 1000 where plan = 'ultimate';
