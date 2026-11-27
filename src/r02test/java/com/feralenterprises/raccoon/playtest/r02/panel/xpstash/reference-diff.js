// Differential oracle for the Lane C XP Stash port. Runs the frozen reference engine (C/reference-xp.js.txt from the
// private Lane Inputs, verified by the Java side) on seeded scenarios and writes scenarios plus reference results.
// The only change to the reference is an injected hook to set its page options; its arithmetic is untouched.
// Usage: node reference-diff.js <extracted R02-Lane-Inputs dir> <out.json> [count]
'use strict';
const fs = require('fs'), path = require('path');
const [dir, out, countArg] = process.argv.slice(2);
const count = Number(countArg || 400);
const read = f => fs.readFileSync(path.join(dir, f), 'utf8');
const recipesText = read('C/xp-recipes.json');
const data = JSON.parse(recipesText);
const modifiers = JSON.parse(read('C/xp-modifiers.json'));
const names = JSON.parse(read('shared/item-names.json'));
let src = read('C/reference-xp.js.txt');
const anchor = 'let ui=defaults(), scope;';
if (!src.includes(anchor)) throw new Error('reference anchor missing');
src = src.replace(anchor, anchor + 'globalThis.__setUi=u=>{ui=u};globalThis.__defaults=defaults;');
const element = {textContent: recipesText, addEventListener() {}, querySelectorAll: () => [], querySelector: () => null};
global.window = global;
global.q = () => element;
global.r02Data = {xpModifiers: modifiers};
global.ofData = {items: names};
global.ofSprite = () => '';
global.ofEsc = s => String(s);
global.ofAssets = {};
global.document = {getElementById: () => null};
eval(src);
if (!global.r02Xp) throw new Error('reference did not load');

let seed = 20261127;
const rnd = () => { seed = (seed * 1103515245 + 12345) % 2147483648; return seed / 2147483648; };
const pick = a => a[Math.floor(rnd() * a.length)];
const skills = global.r02Xp.skills;
const ids = new Set();
for (const r of data.recipes) {
  r.ids.forEach(i => ids.add(i));
  r.secondaries.forEach(s => ids.add(s.id));
  if (r.special) r.special.ids.forEach(i => ids.add(i));
  if (r.output) ids.add(r.output.id);
}
const allIds = [...ids];
const items = n => { const m = new Map(); for (let i = 0; i < n; i++) m.set(pick(allIds), 1 + Math.floor(rnd() * (rnd() < 0.3 ? 30 : 3000))); return [...m].map(([id, quantity]) => ({id, quantity})); };
const groupsBySkill = {};
for (const r of data.recipes) (groupsBySkill[r.skill] ||= new Set()).add(r.skill + '|' + r.input);
const recipesByGroup = {};
for (const r of data.recipes) (recipesByGroup[r.skill + '|' + r.input] ||= []).push(r.key);
const mods = {Prayer: ['', 'demonic', 'sinister', 'gilded', 'ecto', 'wild'], Sailing: ['', 'lure']};

const scenarios = [];
for (let n = 0; n < count; n++) {
  const skill = n < skills.length * 3 ? skills[n % skills.length] : pick(skills);
  const groups = [...groupsBySkill[skill]];
  const lvlMode = pick(['none', 'level', 'xp', 'both']);
  const xp = Math.floor(rnd() * 14000000);
  const level = 1 + Math.floor(rnd() * 98);
  const skillObs = lvlMode === 'none' ? null : {level: lvlMode === 'xp' ? null : level, xp: lvlMode === 'level' ? null : xp};
  // seed the skill's own inputs so its groups are exercised
  const own = items(25);
  for (const g of groups) if (rnd() < 0.6) { const r = data.recipes.find(x => x.skill + '|' + x.input === g); own.push({id: r.ids[0], quantity: 1 + Math.floor(rnd() * 2000)}); }
  const dedupe = l => { const m = new Map(); for (const i of l) m.set(i.id, (m.get(i.id) || 0) + i.quantity); return [...m].map(([id, quantity]) => ({id, quantity})); };
  const bankState = pick(['current', 'current', 'historical', 'none']);
  const scenario = {
    skill, skillObs, bankState,
    bank: bankState === 'none' ? null : dedupe(own),
    vault: rnd() < 0.4 ? dedupe(items(6)) : null,
    inventory: rnd() < 0.4 ? dedupe(items(6)) : null,
    bag: rnd() < 0.3 ? dedupe(items(4)) : null,
    options: {
      cascade: rnd() < 0.8, respect: rnd() < 0.7, limit: rnd() < 0.5, boost: Math.floor(rnd() * 11) - 5,
      sources: {bank: rnd() < 0.9, vault: rnd() < 0.5, inventory: rnd() < 0.4, bag: rnd() < 0.3},
      choices: Object.fromEntries(groups.filter(() => rnd() < 0.4).map(g => [g, pick(recipesByGroup[g])])),
      ignored: groups.filter(() => rnd() < 0.1),
      modifier: mods[skill] ? pick(mods[skill]) : '',
      pieces: [0, 1, 2, 3].filter(() => rnd() < 0.4),
    },
  };
  const ui = global.__defaults();
  Object.assign(ui, {skill, cascade: scenario.options.cascade, respect: scenario.options.respect, limit: scenario.options.limit,
    boost: scenario.options.boost, sources: scenario.options.sources});
  ui.choices = new Map(Object.entries(scenario.options.choices));
  ui.ignored = new Set(scenario.options.ignored);
  ui.modifiers = new Map([[skill, scenario.options.modifier]]);
  ui.pieces = new Map([[skill, new Set(scenario.options.pieces)]]);
  global.__setUi(ui);
  const readModel = {
    skills: skillObs ? {[skill]: skillObs} : null,
    bank: {snapshot: scenario.bank ? {items: scenario.bank} : null, current: bankState === 'current'},
    containers: Object.fromEntries(['vault', 'inventory', 'bag'].filter(k => scenario[k]).map(k => [k, scenario[k]])),
  };
  const res = global.r02Xp.calculate(readModel);
  scenario.expected = {
    known: res.known, complete: res.complete, cyclic: res.cyclic, currentXp: res.currentXp, currentLevel: res.currentLevel,
    total: res.total, projectedXp: res.projectedXp,
    entries: res.entries.map(e => ({key: e.key, chosen: e.chosen ? e.chosen.key : null, original: e.original, piped: e.piped, runs: e.runs, xp: e.xp,
      needs: e.needs.map(x => [x.label, x.wanted, x.available])})),
  };
  scenarios.push(scenario);
}
fs.writeFileSync(out, JSON.stringify({count: scenarios.length, scenarios}));
console.log('reference scenarios: ' + scenarios.length);
