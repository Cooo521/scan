const LEVELS = [
  {id:'4', name:'4級レベル', words:[
  {en:'hot', ja:['あつい','暑い','熱い','厚い','暑','熱'], main:'あつい', emoji:'🔥', theme:'ようす・てんき', tc:'#FAEEDA', tt:'#633806',
   ex:["It's ", ' today.'], exJa:['きょうは ', 'あつい', ' ね。'], pair:{lbl:'はんたい', emoji:'🧊', en:'cold', ja:'さむい・つめたい'}},
  {en:'apple', ja:['りんご','林檎'], main:'りんご', emoji:'🍎', theme:'たべもの', tc:'#FBEAF0', tt:'#72243E',
   ex:['I eat an ', '.'], exJa:['わたしは ', 'りんご', ' を たべます。'], pair:{lbl:'なかま', emoji:'🍌', en:'banana', ja:'バナナ'}},
  {en:'dog', ja:['いぬ','犬','わんちゃん','わんこ'], main:'いぬ', emoji:'🐶', theme:'どうぶつ', tc:'#E1F5EE', tt:'#085041',
   ex:['I have a ', '.'], exJa:['わたしは ', 'いぬ', ' を かっています。'], pair:{lbl:'なかま', emoji:'🐱', en:'cat', ja:'ねこ'}},
  {en:'big', ja:['おおきい','大きい','大き','でかい'], main:'おおきい', emoji:'🐘', theme:'ようす・てんき', tc:'#FAEEDA', tt:'#633806',
   ex:['This box is ', '.'], exJa:['この はこは ', 'おおきい', ' です。'], pair:{lbl:'はんたい', emoji:'🐜', en:'small', ja:'ちいさい'}},
  {en:'borrow', ja:['かりる','借りる','借り','借'], main:'かりる', emoji:'📚', theme:'がっこう・ともだち', tc:'#E6F1FB', tt:'#0C447C',
   ex:['Can I ', ' your pen?'], exJa:['ペンを ', 'かりても', ' いい？'], pair:{lbl:'はんたい', emoji:'🤲', en:'lend', ja:'かす'}, illust:true}
]},
  {id:'3', name:'3級レベル', words:[
  {en:'forget', ja:['わすれる','忘れる','忘れ','わすれ'], main:'わすれる', emoji:'💭', theme:'きもち・あたま', tc:'#EEEDFE', tt:'#3C3489',
   ex:["Don't ", ' your bag.'], exJa:['かばんを ', 'わすれないで', 'ね。'], pair:{lbl:'はんたい', emoji:'🧠', en:'remember', ja:'おぼえている'}},
  {en:'quiet', ja:['しずか','静か'], main:'しずかな', emoji:'🤫', theme:'ようす・てんき', tc:'#FAEEDA', tt:'#633806',
   ex:['The library is ', '.'], exJa:['としょかんは ', 'しずか', ' です。'], pair:{lbl:'はんたい', emoji:'📢', en:'noisy', ja:'うるさい'}},
  {en:'village', ja:['むら','村'], main:'むら', emoji:'🏡', theme:'まち', tc:'#EAF3DE', tt:'#27500A',
   ex:['I live in a small ', '.'], exJa:['わたしは ちいさな ', 'むら', ' に すんでいます。'], pair:{lbl:'なかま', emoji:'🏙️', en:'town', ja:'まち'}},
  {en:'climb', ja:['のぼる','登る','上る','のぼ','登'], main:'のぼる', emoji:'🧗', theme:'うごき', tc:'#FAECE7', tt:'#712B13',
   ex:["Let's ", ' the mountain.'], exJa:['やまに ', 'のぼろう', '。'], pair:null},
  {en:'hospital', ja:['びょういん','病院'], main:'びょういん', emoji:'🏥', theme:'まち', tc:'#EAF3DE', tt:'#27500A',
   ex:['My mom works at a ', '.'], exJa:['ママは ', 'びょういん', ' で はたらいています。'], pair:{lbl:'なかま', emoji:'🏫', en:'school', ja:'がっこう'}}
]},
  {id:'p2', name:'準2級レベル', locked:true},
  {id:'2', name:'2級レベル', locked:true}
];
LEVELS.forEach(l => (l.words || []).forEach(w => { if (w.ex.length === 2) w.ex = [w.ex[0], w.en, w.ex[1]]; }));
const lvlOf = id => LEVELS.find(l => l.id === id);

/* ---------- Google（受け取り係）とのやりとり ---------- */
const GAS_KEY = 'eitango-gas', WCACHE_KEY = 'eitango-words-cache';
const THEMES = {'ようす・てんき':['#FAEEDA','#633806'], 'たべもの':['#FBEAF0','#72243E'], 'どうぶつ':['#E1F5EE','#085041'],
  'がっこう・ともだち':['#E6F1FB','#0C447C'], 'きもち・あたま':['#EEEDFE','#3C3489'], 'まち':['#EAF3DE','#27500A'], 'うごき':['#FAECE7','#712B13']};
const G = {url:'', status:'off', msg:''};
function lsGet(k){ try { return localStorage.getItem(k); } catch (e) { return null; } }
function lsSet(k, v){ try { localStorage.setItem(k, v); } catch (e) {} }
function splitMark(t){
  const m = /^(.*?)【(.*?)】(.*)$/.exec(t || '');
  return m ? [m[1], m[2], m[3]] : null;
}
function rowToWord(r){
  const en = String(r.en || '').trim(); if (!en) return null;
  const ja = String(r.ja || '').split(/[、,，\s]+/).map(x => x.trim()).filter(Boolean);
  let ex = splitMark(r.ex);
  if (!ex && r.ex) {
    const i = r.ex.toLowerCase().indexOf(en.toLowerCase());
    ex = i >= 0 ? [r.ex.slice(0, i), r.ex.slice(i, i + en.length), r.ex.slice(i + en.length)] : [r.ex, '', ''];
  }
  const exJa = splitMark(r.exJa) || [r.exJa || '', '', ''];
  const th = THEMES[r.theme] || ['#F1EFE8', '#444441'];
  return {en, ja:ja.length ? ja : [en], main:r.main || ja[0] || '', emoji:r.emoji || '⭐', theme:r.theme || 'たんご', tc:th[0], tt:th[1],
    ex:ex || [en, '', ''], exJa, pair:r.pairEn ? {lbl:r.pairLbl || 'なかま', emoji:r.pairEmoji || '', en:r.pairEn, ja:r.pairJa || ''} : null,
    illust:en.toLowerCase() === 'borrow'};
}
function applyWords(levels){
  let n = 0;
  LEVELS.forEach(l => {
    const rows = (levels || {})[l.id];
    if (l.locked || !rows || !rows.length) return;
    const ws = rows.map(rowToWord).filter(Boolean);
    if (ws.length) { l.words = ws; n += ws.length; }
  });
  return n;
}
function gasGet(params){
  const q = Object.keys(params).map(k => k + '=' + encodeURIComponent(params[k])).join('&');
  return fetch(G.url + (G.url.includes('?') ? '&' : '?') + q, {redirect:'follow'}).then(r => r.json());
}
function gasPost(body){
  return fetch(G.url, {method:'POST', body:JSON.stringify(body), redirect:'follow'}).then(r => r.json());
}
function summary(){
  const L4 = lvInfo(S.lvExp['4'] || 0), L3 = lvInfo(S.lvExp['3'] || 0);
  const k = id => `${knownCount(id)}/${(lvlOf(id).words || []).length}語`;
  return {exp:S.exp, lv4:L4.l, known4:k('4'), lv3:L3.l, known3:k('3'), stamps:S.stamps};
}
function pushSave(){
  if (!G.url) return Promise.resolve(false);
  lsSet(SAVE_KEY + ':pending', '1');
  return gasPost({a:'save', c:USER.id, data:{v:1, savedAt:S.savedAt, exp:S.exp, lvExp:S.lvExp, known:S.known, stamps:S.stamps, level:S.level, summary:summary()}})
    .then(j => { if (j && j.ok) { try { localStorage.removeItem(SAVE_KEY + ':pending'); } catch (e) {} G.status = 'ok'; return true; } throw new Error((j && j.error) || 'save'); })
    .catch(() => { G.status = 'err'; return false; });
}
async function syncStart(){
  try { const c = JSON.parse(lsGet(WCACHE_KEY) || 'null'); if (c) applyWords(c); } catch (e) {}
  G.url = lsGet(GAS_KEY) || '';
  if (!G.url) { G.status = 'off'; return; }
  G.status = 'wait';
  try {
    const [w, r] = await Promise.all([gasGet({a:'words'}), gasGet({a:'load', c:USER.id})]);
    if (w && w.ok) { G.count = applyWords(w.levels); lsSet(WCACHE_KEY, JSON.stringify(w.levels)); }
    if (r && r.ok && r.rec && (r.rec.savedAt || 0) > (S.savedAt || 0)) { adoptSave(r.rec); save(true); }
    G.status = 'ok';
    if (lsGet(SAVE_KEY + ':pending')) await pushSave();
  } catch (e) { G.status = 'err'; }
}
function gasLine(){
  if (G.status === 'off') return 'Google：まだ つないでいません（アプリの中の単語で動きます）';
  if (G.status === 'wait') return 'Google：よみこみ中…';
  if (G.status === 'ok') return `Google：つながっています（単語 4級${(lvlOf('4').words || []).length}・3級${(lvlOf('3').words || []).length}）`;
  return 'Google：つながりませんでした（端末の中の控えで動きます。記録はあとで送ります）';
}
function words(){ return lvlOf(S.level).words; }

const IN_FRAME = (() => { try { return window.self !== window.top; } catch (e) { return true; } })();
const SR = IN_FRAME ? null : (window.SpeechRecognition || window.webkitSpeechRecognition);
const $app = document.getElementById('app');
const $ = s => document.querySelector(s);
const sleep = ms => new Promise(r => setTimeout(r, ms));
const USER = window.EITANGO_USER || {id:'rio', name:'リオ'};
const esc = t => String(t).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'})[c]);
const NAME = esc(USER.name);
const SAVE_KEY = 'eitango-test-v1:' + USER.id;
const PRAISE = [`さすが ${NAME}！`, `${NAME}、すごい！`, `その ちょうし、${NAME}！`, `${NAME}、ばっちり！`];
let praiseN = 0;
const S = {exp:0, lvExp:{'4':0, '3':0}, known:{'4':[], '3':[]}, stamps:4, level:'4', tok:0, run:null, resetArm:false};
function loadSave(){
  try {
    let raw = localStorage.getItem(SAVE_KEY);
    if (!raw && USER.id === 'rio') raw = localStorage.getItem('eitango-test-v1');
    const d = JSON.parse(raw || 'null');
    if (d) adoptSave(d);
  } catch (e) {}
}
function adoptSave(d){
  S.exp = d.exp || 0; S.lvExp = Object.assign({'4':0, '3':0}, d.lvExp || {}); S.known = Object.assign({'4':[], '3':[]}, d.known || {});
  S.stamps = d.stamps != null ? d.stamps : 4; S.level = d.level || '4'; S.savedAt = d.savedAt || 0;
}
function save(keepTime){
  if (!keepTime) S.savedAt = Date.now();
  try { localStorage.setItem(SAVE_KEY, JSON.stringify({savedAt:S.savedAt, exp:S.exp, lvExp:S.lvExp, known:S.known, stamps:S.stamps, level:S.level})); } catch (e) {}
}
function addExp(n){ S.exp += n; S.lvExp[S.level] = (S.lvExp[S.level] || 0) + n; }
function knownCount(id){ const l = lvlOf(id), k = new Set(S.known[id] || []); return (l.words || []).filter(w => k.has(w.en)).length; }
function knownPct(id){ const l = lvlOf(id); return l.words && l.words.length ? Math.round(knownCount(id) / l.words.length * 100) : 0; }

function need(l){ return 40 + l * 20; }
function lvInfo(exp){ let l = 1, e = exp; while (e >= need(l)) { e -= need(l); l++; } return {l, cur:e, need:need(l)}; }
function pct(L){ return Math.round(L.cur / L.need * 100); }
function shuffle(a){ for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; } return a; }

/* ---------- sound ---------- */
let ac = null;
function audio(){
  if (!ac) { try { ac = new (window.AudioContext || window.webkitAudioContext)(); } catch (e) { ac = null; } }
  if (ac && ac.state === 'suspended') ac.resume();
  return ac;
}
function tone(freq, start, dur, type, vol){
  const a = audio(); if (!a) return;
  const o = a.createOscillator(), g = a.createGain(), t = a.currentTime + start;
  o.type = type || 'sine'; o.frequency.value = freq;
  g.gain.setValueAtTime(0.0001, t);
  g.gain.exponentialRampToValueAtTime(vol || 0.2, t + 0.02);
  g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
  o.connect(g); g.connect(a.destination); o.start(t); o.stop(t + dur + 0.05);
}
const sfx = {
  tap(){ tone(660, 0, .06, 'sine', .06); },
  ok(){ tone(988, 0, .15, 'triangle', .2); tone(1319, .12, .32, 'triangle', .2); },
  ng(){ tone(392, 0, .22, 'sine', .14); tone(330, .18, .35, 'sine', .12); },
  stamp(){ tone(110, 0, .2, 'square', .12); tone(70, .02, .25, 'sine', .3); },
  lvup(){ [523, 659, 784, 1047].forEach((f, i) => tone(f, i * .1, .28, 'triangle', .18)); },
  good(){ tone(784, 0, .12, 'triangle', .15); tone(1047, .1, .2, 'triangle', .15); },
  beep(){ tone(880, 0, .12, 'sine', .12); }
};

/* ---------- speech out ---------- */
let voices = [];
function loadVoices(){ try { voices = speechSynthesis.getVoices(); } catch (e) { voices = []; } }
if ('speechSynthesis' in window) { loadVoices(); speechSynthesis.onvoiceschanged = loadVoices; }
function pickVoice(lang){
  const norm = v => v.lang.replace('_', '-');
  return voices.find(v => norm(v) === lang && /Google/.test(v.name)) || voices.find(v => norm(v) === lang) || voices.find(v => norm(v).startsWith(lang.slice(0, 2)));
}
function say(text, lang, rate){
  return new Promise(res => {
    if (!('speechSynthesis' in window)) { res(); return; }
    const u = new SpeechSynthesisUtterance(text);
    u.lang = lang || 'en-US'; u.rate = rate || 0.9;
    const v = pickVoice(u.lang); if (v) u.voice = v;
    let done = false; const fin = () => { if (!done) { done = true; res(); } };
    u.onend = fin; u.onerror = fin;
    speechSynthesis.speak(u);
    setTimeout(fin, 1500 + text.length * 150);
  });
}
async function speakWord(w){
  const tk = S.tok;
  try { speechSynthesis.cancel(); } catch (e) {}
  await sleep(250); if (tk !== S.tok) return;
  await say(w.en, 'en-US', 0.7); if (tk !== S.tok) return;
  await sleep(350); if (tk !== S.tok) return;
  await say(w.en, 'en-US', 0.95);
}
function exText(w){ return w.ex.join(''); }
async function speakEx(w, slow){
  const tk = S.tok;
  try { speechSynthesis.cancel(); } catch (e) {}
  await sleep(500); if (tk !== S.tok) return;
  if (slow) { await say(w.en, 'en-US', 0.7); if (tk !== S.tok) return; await sleep(300); if (tk !== S.tok) return; }
  await say(exText(w), 'en-US', slow ? 0.7 : 0.9);
}

/* ---------- speech in ---------- */
let curRec = null;
const HARD = ['not-allowed', 'service-not-allowed', 'audio-capture', 'network', 'nosr', 'start', 'language-not-supported'];
function stopRec(){ if (curRec) { const r = curRec; curRec = null; try { r.abort(); } catch (e) {} } }
function earOn(on){ const e = $('#ear'); if (e) e.classList.toggle('on', on); }
function setCount(n){ const c = $('#count'); if (c) c.textContent = n; }
/* ピコの合図で自動で聞きはじめ、ms ミリ秒で止める */
function listenFor(lang, ms){
  return new Promise(resolve => {
    if (!SR) { resolve({err:'nosr'}); return; }
    stopRec();
    try { speechSynthesis.cancel(); } catch (e) {}
    const r = new SR(); curRec = r;
    r.lang = lang; r.interimResults = false; r.maxAlternatives = 5; r.continuous = false;
    let done = false, started = false, timer = null, cd = null, fallbackStart = null, hard = null;
    const fin = res => {
      if (done) return; done = true;
      clearTimeout(timer); clearInterval(cd); clearTimeout(fallbackStart); clearTimeout(hard);
      if (curRec === r) curRec = null;
      earOn(false); setCount('');
      resolve(res);
    };
    const begin = () => {
      if (started || done) return; started = true;
      sfx.beep(); earOn(true);
      let n = Math.round(ms / 1000); setCount(n);
      cd = setInterval(() => { n--; setCount(n > 0 ? n : ''); }, 1000);
      timer = setTimeout(() => { try { r.stop(); } catch (e) {} }, ms);
      hard = setTimeout(() => fin({err:'no-speech'}), ms + 4000);
    };
    r.onaudiostart = begin;
    r.onresult = e => {
      const res = e.results[0], alts = [];
      for (let i = 0; i < res.length; i++) alts.push(res[i].transcript.trim());
      fin({alts});
    };
    r.onerror = e => fin({err:e.error});
    r.onend = () => fin({err:'no-speech'});
    fallbackStart = setTimeout(begin, 1500);
    try { r.start(); } catch (err) { fin({err:'start'}); }
  });
}
function errMsg(e){
  return ({
    'not-allowed':'マイクが ゆるされていないよ（ブラウザの設定で マイクを「許可」にしてね）',
    'service-not-allowed':'マイクが ゆるされていないよ（ブラウザの設定で マイクを「許可」にしてね）',
    'no-speech':'きこえなかったよ。もう一度 おしてね',
    'audio-capture':'マイクが みつからないよ',
    'network':'ネットに つながっていないみたい',
    'nosr':'この ブラウザでは こえが つかえないよ。したの ボタンで こたえてね'
  })[e] || 'もう一度 おしてね';
}
function lev(a, b){
  const m = a.length, n = b.length, d = [];
  for (let i = 0; i <= m; i++) { d[i] = [i]; }
  for (let j = 1; j <= n; j++) d[0][j] = j;
  for (let i = 1; i <= m; i++) for (let j = 1; j <= n; j++)
    d[i][j] = Math.min(d[i-1][j] + 1, d[i][j-1] + 1, d[i-1][j-1] + (a[i-1] === b[j-1] ? 0 : 1));
  return d[m][n];
}
function mimicOk(alts, target){
  const t = target.toLowerCase();
  return alts.some(a => {
    const ws = a.toLowerCase().replace(/[^a-z' ]/g, ' ').split(/\s+/).filter(Boolean);
    return ws.join('') === t || ws.some(x => x === t || lev(x, t) <= (t.length > 4 ? 2 : 1));
  });
}
function toHira(s){ return s.replace(/[ァ-ヶ]/g, c => String.fromCharCode(c.charCodeAt(0) - 0x60)); }
function norm(s){ return toHira(s).replace(/[\s、。，．,.！？!?・「」『』ー〜~]/g, ''); }
function jaOk(alts, w){
  const ans = w.ja.map(norm);
  return alts.some(a => { const n = norm(a); return ans.some(x => x && n.includes(x)); });
}

/* ---------- pictures ---------- */
function pico(type, size, cls){
  const wingsN = '<ellipse cx="13" cy="60" rx="8" ry="13" fill="#EF9F27"/><ellipse cx="87" cy="60" rx="8" ry="13" fill="#EF9F27"/>';
  const wingsH = '<ellipse cx="11" cy="46" rx="8" ry="13" fill="#EF9F27" transform="rotate(-35 11 46)"/><ellipse cx="89" cy="46" rx="8" ry="13" fill="#EF9F27" transform="rotate(35 89 46)"/>';
  const body = '<ellipse cx="50" cy="56" rx="34" ry="32" fill="#FAC775" stroke="#BA7517" stroke-width="2"/><polygon points="66,24 52,13 54,33" fill="#ED93B1"/><polygon points="66,24 80,13 78,33" fill="#ED93B1"/><circle cx="66" cy="24" r="4.5" fill="#D4537E"/>';
  const eyesN = '<circle cx="38" cy="51" r="7" fill="#FFFFFF"/><circle cx="39" cy="52" r="4.2" fill="#2C2C2A"/><circle cx="41" cy="50" r="1.6" fill="#FFFFFF"/><circle cx="62" cy="51" r="7" fill="#FFFFFF"/><circle cx="61" cy="52" r="4.2" fill="#2C2C2A"/><circle cx="63" cy="50" r="1.6" fill="#FFFFFF"/><polygon points="44,61 56,61 50,69" fill="#D85A30"/>';
  const eyesH = '<path d="M31 53 Q38 44 45 53" stroke="#2C2C2A" stroke-width="3.2" fill="none" stroke-linecap="round"/><path d="M55 53 Q62 44 69 53" stroke="#2C2C2A" stroke-width="3.2" fill="none" stroke-linecap="round"/><polygon points="43,60 57,60 50,72" fill="#D85A30"/>';
  const cheeks = '<ellipse cx="27" cy="65" rx="6.5" ry="3.8" fill="#ED93B1"/><ellipse cx="73" cy="65" rx="6.5" ry="3.8" fill="#ED93B1"/>';
  const h = type === 'h';
  return `<svg class="pico ${cls || ''}" viewBox="0 0 100 95" width="${size}" height="${Math.round(size * .95)}" aria-hidden="true">${h ? wingsH : wingsN}${body}${h ? eyesH : eyesN}${cheeks}</svg>`;
}
function borrowSvg(width){
  return `<svg viewBox="0 0 200 104" width="${width}" aria-hidden="true">
<defs><marker id="ah" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="#BA7517"/></marker></defs>
<circle cx="36" cy="26" r="13" fill="#9FE1CB" stroke="#0F6E56" stroke-width="1.5"/>
<rect x="22" y="42" width="28" height="36" rx="10" fill="#9FE1CB" stroke="#0F6E56" stroke-width="1.5"/>
<circle cx="164" cy="26" r="13" fill="#F4C0D1" stroke="#993556" stroke-width="1.5"/>
<rect x="150" y="42" width="28" height="36" rx="10" fill="#F4C0D1" stroke="#993556" stroke-width="1.5"/>
<rect x="88" y="38" width="24" height="18" rx="2" fill="#FAC775" stroke="#BA7517" stroke-width="1.5"/>
<path d="M60 66 L140 66" stroke="#BA7517" stroke-width="3" fill="none" marker-end="url(#ah)"/>
<text x="100" y="84" text-anchor="middle" font-size="11" fill="#4B1528">わたしの ところへ</text>
<text x="36" y="98" text-anchor="middle" font-size="11" fill="#4B1528">ともだち</text>
<text x="164" y="98" text-anchor="middle" font-size="11" fill="#4B1528">わたし</text></svg>`;
}
function hintPic(w){ return w.illust ? borrowSvg(170) : `<span class="hintem">${esc(w.emoji)}</span>`; }
function confetti(){
  const cols = ['#ED93B1', '#5DCAA5', '#EF9F27', '#AFA9EC', '#F4C0D1', '#FAC775'];
  for (let i = 0; i < 26; i++) {
    const c = document.createElement('span');
    c.className = 'cf';
    c.style.left = (Math.random() * 100) + '%';
    c.style.background = cols[i % cols.length];
    c.style.animationDuration = (1.5 + Math.random() * 1.2) + 's';
    c.style.animationDelay = (Math.random() * .4) + 's';
    if (i % 3 === 0) c.style.borderRadius = '50%';
    $app.appendChild(c);
    setTimeout(() => c.remove(), 3200);
  }
}
function stampCard(n, anim){
  let s = '';
  for (let i = 0; i < 7; i++) {
    if (i < n) s += `<div class="slot"><div class="st ${i === anim ? 'drop' : ''}" ${i === anim ? 'id="newst"' : ''}><span class="sf">🌸</span><span>よく</span><span>できました</span></div></div>`;
    else s += '<div class="slot empty"></div>';
  }
  return `<p class="lbl">${NAME}の スタンプカード</p><div class="slots">${s}</div>`;
}

/* ---------- screens ---------- */
function render(html, bg){
  stopRec();
  S.tok++;
  document.body.style.background = bg;
  $app.innerHTML = `<div class="scr">${html}</div>`;
  window.scrollTo(0, 0);
}

function home(){
  const L = lvInfo(S.lvExp[S.level]);
  render(`
<div class="top"><button class="sbtn" data-a="select">← レベルを かえる</button><span class="pill lvp">${lvlOf(S.level).name}　Lv ${L.l}</span></div>
<div class="bar"><i style="width:${pct(L)}%"></i></div>
<div class="center"><div class="bub">${NAME}、きょうは ${words().length}まい！<br>いっしょに がんばろ♪</div>${pico('n', 140)}</div>
<button class="btn pink" data-a="start">▶ はじめる</button>
<div class="panel">${stampCard(S.stamps, -1)}</div>
<p class="note">【おうちの人へ】テスト版です（単語5つ）。マイクの許可を聞かれたら「許可」を押してください。ボタンを押さなくても、発音のあとにピコの合図で自動で聞き取りが始まります（発音を2回聞いたら、すぐ日本語で答える3秒）。聞き取れた言葉が小さく表示されるので、聞き取りの正確さを確かめられます。${SR ? '' : (IN_FRAME ? '<br><b>この画面（Claudeの中）では声の聞き取りが使えません。画面のボタンで答えて、流れを確かめてください。声で試すときは、GitHubに置いたテスト版を開いてください。</b>' : '<br><b>この端末・ブラウザでは声の聞き取りが使えません。Chromeで開いてください（それまでは画面のボタンで答えられます）。</b>')}</p>`, '#FBEAF0');
}

function select(){
  S.resetArm = false;
  const rows = LEVELS.map(l => {
    if (l.locked) return `<button class="lvbtn locked" data-a="locked"><span class="lvname">${l.name}</span><span class="lvside">🔒 じゅんびちゅう</span></button>`;
    const L = lvInfo(S.lvExp[l.id] || 0), p = knownPct(l.id);
    return `<button class="lvbtn ${l.id === S.level ? 'sel' : ''}" data-a="pick" data-l="${l.id}">
<span class="lvname">${p >= 100 ? '👑 ' : ''}${l.name}</span>
<span class="lvside"><span class="lvnum">Lv ${L.l}</span><span class="minibar"><i style="width:${p}%"></i></span><span class="lvpct">おぼえた ${p}%</span></span></button>`;
  }).join('');
  render(`
<div class="top"><span class="ttl">${NAME}の えいたんご <small>テスト版 v6</small></span><span class="pill lvp">ピコ　けいけんち ${S.exp}</span></div>
<div class="center" style="margin-top:10px;"><div class="bub" id="bub">${NAME}、どの レベルを やる？</div>${pico('n', 110)}</div>
<div style="margin-top:10px;">${rows}</div>
<div class="note">【おうちの人へ】テスト版です（${NAME}用のリンク）。記録はこの端末と、つないだGoogleの「きろく」に${NAME}の分として保存されます。<br><span id="gasline">${gasLine()}</span>
<div id="gasbox" style="display:none;margin-top:8px;"><input id="gasurl" type="url" placeholder="https://script.google.com/macros/s/…/exec" style="width:100%;font-size:16px;padding:8px;border-radius:10px;border:1px solid #ED93B1;box-sizing:border-box;"><button class="sbtn" data-a="gasok" style="margin-top:6px;">つなぐ</button> <span id="gasmsg"></span></div>
<div style="margin-top:8px;display:flex;gap:6px;flex-wrap:wrap;"><button class="sbtn" data-a="gasopen" style="font-size:12px;padding:4px 10px;">Googleと つなぐ</button><button class="sbtn" data-a="reset" id="resetb" style="font-size:12px;padding:4px 10px;">きろくを けす</button></div></div>`, '#FBEAF0');
}

function startRun(){
  audio();
  S.run = {phase:'main', round:0, queue:shuffle(words().slice()), idx:0, first:{}, retryFailed:[], recovered:0,
           combo:0, maxCombo:0, gains:{o:0, d:0, x:0, combo:0, retry:0, stamp:0, full:0}, cnt:{o:0, d:0, x:0}, expStart:S.lvExp[S.level]};
  showCard();
}
function cur(){ const R = S.run; return R.queue[R.idx]; }
function progressHtml(){
  const R = S.run;
  if (R.phase === 'main') {
    const n = R.queue.length;
    return `<span class="stars">${'★'.repeat(R.idx)}<span class="rest">${'☆'.repeat(n - R.idx)}</span></span>`;
  }
  return `<span style="font-size:14px;font-weight:700;">もう1回タイム ${R.round}しゅうめ（${R.idx + 1}/${R.queue.length}）</span>`;
}

function showCard(){
  const R = S.run, w = cur();
  R.step = 0; R.hint = (R.phase === 'retry' && R.round >= 3);
  R.heard = ''; R.heardAll = ''; R.byVoice = false;
  render(`
<div class="top">${progressHtml()}${R.phase === 'main' && R.combo >= 2 ? `<span class="pill cmb">🔥 ${R.combo}コンボ</span>` : ''}</div>
<div class="card"><div class="tape"></div>
<span class="pill" style="background:${w.tc};color:${w.tt};font-size:12px;font-weight:500;">${esc(w.theme)}</span>
<div class="word">${esc(w.en)}</div>
<div id="hintbox" class="hintbox ${R.hint ? 'show' : ''}">${hintPic(w)}</div>
<button class="round" data-a="again" aria-label="もう一度きく">🔊</button>
</div>
<div class="center" style="margin-top:16px;"><div class="bub" id="bub">よく きいてね♪</div></div>
<div class="microw">${pico('n', 64)}<div class="ear" id="ear"><span class="earic">🎤</span><span class="count" id="count"></span></div><div style="width:64px"></div></div>
<p class="heard" id="heard"></p>
<div class="center"><button class="sbtn hintb" id="hintbtn" data-a="hint" ${R.hint ? 'style="display:none"' : ''}>💡 ヒント</button></div>
<div class="fb"><span>こえが うまく いかないとき：</span><button class="sbtn" data-a="selfok">○ いえた</button><button class="sbtn" data-a="selfng">× わからない</button></div>`, '#E1F5EE');
  runCard();
}
function setBub(t){ const b = $('#bub'); if (b) b.textContent = t; }
function setHeard(t){ const h = $('#heard'); if (h) h.textContent = t; }

/* 1ページで自動：発音2回 → すぐ日本語で答える(3秒) */
async function runCard(){
  const R = S.run, w = cur(), tk = S.tok;
  const alive = () => tk === S.tok && S.run === R;
  setBub('よく きいてね♪');
  await speakWord(w); if (!alive()) return;
  if (!SR) { setBub('こたえを いったら したの ボタンを おしてね'); return; }
  R.step = 2;
  await answerPhase(tk, 0);
}
async function answerPhase(tk, attempt){
  const R = S.run, w = cur();
  const alive = () => tk === S.tok && S.run === R;
  setBub(attempt ? 'もう1回 いくよ！ にほんごで いうと？' : 'にほんごで いうと？ せーの！');
  await sleep(500); if (!alive()) return;
  setHeard('');
  const a = await listenFor('ja-JP', 3000); if (!alive()) return;
  if (a.alts) { R.heard = a.alts[0]; R.heardAll = a.alts.join(' / '); judge(jaOk(a.alts, w), true); return; }
  if (HARD.includes(a.err)) { setHeard(errMsg(a.err)); setBub('したの ボタンで こたえてね'); return; }
  if (attempt === 0) { setBub('きこえなかったよ'); await sleep(900); if (!alive()) return; return answerPhase(tk, 1); }
  judge(false, true);
}

function judge(ok, byVoice){
  const R = S.run;
  R.byVoice = byVoice;
  applyResult(ok ? (R.hint ? 'd' : 'o') : 'x');
  showAnswer();
}
function applyResult(out){
  const R = S.run, w = cur();
  const rec = {out, comboBefore:R.combo, add:0, comboBonus:false};
  if (R.phase === 'main') {
    R.first[w.en] = out; R.cnt[out]++;
    if (out === 'o') {
      rec.add = 10; R.gains.o += 10; R.combo++; R.maxCombo = Math.max(R.maxCombo, R.combo);
      if (R.combo % 3 === 0) { rec.add += 5; R.gains.combo += 5; rec.comboBonus = true; }
    } else if (out === 'd') { rec.add = 5; R.gains.d += 5; R.combo = 0; }
    else { rec.add = 2; R.gains.x += 2; R.combo = 0; }
  } else {
    if (out === 'x') R.retryFailed.push(w);
    else { rec.add = 5; R.gains.retry += 5; R.recovered++; }
  }
  addExp(rec.add);
  R.last = rec;
}
function revertResult(){
  const R = S.run, w = cur(), rec = R.last;
  addExp(-rec.add); R.combo = rec.comboBefore;
  if (R.phase === 'main') {
    R.cnt[rec.out]--; delete R.first[w.en];
    if (rec.out === 'o') { R.gains.o -= 10; if (rec.comboBonus) R.gains.combo -= 5; }
    else if (rec.out === 'd') R.gains.d -= 5;
    else R.gains.x -= 2;
  } else {
    if (rec.out === 'x') R.retryFailed.pop();
    else { R.recovered--; R.gains.retry -= 5; }
  }
}

function exHtml(w){
  return `<div class="exbox"><span class="pill exl">れいぶん</span>
<div class="exrow"><p class="exen">${esc(w.ex[0])}<span class="hw">${esc(w.ex[1])}</span>${esc(w.ex[2])}</p><button class="round sm" data-a="sayex" aria-label="れいぶんをきく">🔊</button></div>
<p class="exja">${esc(w.exJa[0])}<span class="hw">${esc(w.exJa[1])}</span>${esc(w.exJa[2])}</p></div>
${w.pair ? `<p class="pair">${esc(w.pair.lbl)}　${esc(w.pair.emoji)} ${esc(w.pair.en)} ＝ ${esc(w.pair.ja)}</p>` : ''}`;
}
function illuHtml(w){
  return w.illust ? `<div class="illu">${borrowSvg(190)}<p>（苦手単語になると、こんな絵がつきます）</p></div>` : '';
}

function showAnswer(){
  const R = S.run, w = cur(), out = R.last.out;
  const heardLine = R.byVoice && R.heard ? `<p class="heard">きこえた：${R.heardAll || R.heard}</p>` : '';
  if (out === 'o' || out === 'd') {
    const title = R.phase === 'retry' ? 'とりかえした！' : (out === 'd' ? 'ヒントで せいかい！' : 'せいかい！');
    const pills = [`<span class="pill xp">+${R.last.add} けいけんち</span>`];
    if (R.phase === 'main' && out === 'o' && R.combo >= 2) pills.push(`<span class="pill cmb">${R.combo}コンボ！</span>`);
    render(`
<p class="res-t" style="color:#D4537E;">${title}</p>
<p class="small" style="font-size:16px;font-weight:700;margin-top:2px;">${PRAISE[praiseN++ % PRAISE.length]}</p>
${pico('h', 120, 'bounce')}
<div class="panel"><div class="answ"><span class="em">${esc(w.emoji)}</span> ${esc(w.en)} ＝ ${esc(w.main)}</div>${illuHtml(w)}</div>
${exHtml(w)}
<div class="pills">${pills.join('')}</div>
${R.phase === 'main' && out === 'd' ? '<p class="small">もう1回タイムで もう一度 でるよ</p>' : ''}
${heardLine}
<button class="btn lav" data-a="next">つぎへ →</button>`, '#EEEDFE');
    sfx.ok(); confetti(); speakEx(w, false);
  } else {
    render(`
<p class="res-t" style="color:#854F0B;">おしい！</p>
<div class="center" style="margin-top:8px;"><div class="bub">だいじょうぶ、${NAME}！<br>あとで もう1回 でるよ</div>${pico('n', 90)}</div>
<div class="panel"><div class="bigem">${esc(w.emoji)}</div><div class="answ">${esc(w.en)} ＝ ${esc(w.main)}</div>${illuHtml(w)}</div>
${exHtml(w)}
${R.phase === 'main' ? '<div class="pills"><span class="pill xp">+2 チャレンジ</span></div>' : ''}
${heardLine}
${R.byVoice ? '<div class="center"><button class="sbtn" data-a="override">いまのは あってた（○にする）</button></div>' : ''}
<button class="btn mintb" data-a="next">つぎへ →</button>`, '#FAEEDA');
    sfx.ng(); speakEx(w, true);
  }
}

function nextCard(){
  const R = S.run;
  R.idx++;
  if (R.idx < R.queue.length) showCard(); else endRound();
}
function endRound(){
  const R = S.run;
  if (R.phase === 'main') {
    const rem = words().filter(w => R.first[w.en] !== 'o');
    if (!rem.length) { finish(); return; }
    R.phase = 'retry'; R.round = 1; R.queue = shuffle(rem.slice());
  } else {
    if (!R.retryFailed.length) { finish(); return; }
    R.round++; R.queue = shuffle(R.retryFailed.slice());
  }
  R.retryFailed = []; R.idx = 0;
  retryIntro();
}
function retryIntro(){
  const R = S.run, n = R.queue.length;
  const msg = R.round === 1 ? `${NAME}、さいごに もう1回タイム！<br>${n}まい とりかえそう` : `${R.round}しゅうめ！ あと ${n}まい<br>ぜんぶ ○に しよう`;
  render(`
<div class="center" style="margin-top:60px;"><div class="bub">${msg}</div>${pico('h', 150, 'bounce')}</div>
${R.round >= 3 ? '<p class="small">こんどは ヒントの え が はじめから でるよ</p>' : ''}
<button class="btn pink" data-a="go" style="margin-top:24px;">いくよ！</button>`, '#FBEAF0');
  sfx.good();
}

function finish(){
  const R = S.run;
  S.stamps++;
  R.gains.stamp = 50; addExp(50);
  const full = S.stamps >= 7;
  if (full) { R.gains.full = 200; addExp(200); }
  const kn = new Set(S.known[S.level] || []);
  words().forEach(w => { if (R.first[w.en] === 'o') kn.add(w.en); });
  S.known[S.level] = [...kn];
  save();
  pushSave().then(ok => { const g = $('#savemsg'); if (g) g.textContent = ok ? 'きろくを Googleに ほぞんしました' : (G.url ? 'Googleには あとで ほぞんします' : ''); });
  const g = R.gains, from = lvInfo(R.expStart);
  const rows = [
    ['○ せいかい', g.o, true], ['△ ヒントで せいかい', g.d], ['× チャレンジ', g.x], ['コンボ ボーナス', g.combo],
    ['もう1回タイム', g.retry], ['きょうの スタンプ', g.stamp, true], ['カード いっぱい ボーナス', g.full]
  ].filter(r => r[2] || r[1] > 0).map(r => `<div class="gr"><span>${r[0]}</span><span>+${r[1]}</span></div>`).join('');
  const total = S.lvExp[S.level] - R.expStart;
  render(`
<div class="center"><p class="big-t">${NAME}、ぜんぶ ○に なったね！</p>
${R.recovered ? `<p class="small">もう1回タイムで ${R.recovered}こ とりかえしたよ</p>` : ''}
${pico('h', 110, 'bounce')}</div>
<div class="panel"><p class="lbl">さいしょの こたえ</p><div class="cnts">
<div class="c o">○<b>${R.cnt.o}</b></div><div class="c d">△ ヒント<b>${R.cnt.d}</b></div><div class="c x">×<b>${R.cnt.x}</b></div></div>
${R.maxCombo >= 2 ? `<p class="small">さいこう ${R.maxCombo}コンボ</p>` : ''}</div>
<div class="panel">${stampCard(S.stamps, S.stamps - 1)}<p class="pon" id="pon">ポンッ！</p>
<p class="small">${full ? 'スタンプカード いっぱい！ ボーナス +200' : `あと ${7 - S.stamps}こで いっぱい！ ボーナス +200`}</p></div>
<div class="panel"><p class="lbl">${lvlOf(S.level).name} の レベル</p><div class="lvrow"><span>Lv <b id="lvn">${from.l}</b></span><span class="pill lvup" id="lvup">⬆ レベルアップ！</span></div>
<div class="bar big"><i id="xbar" style="width:${pct(from)}%"></i></div>
<p class="small" id="xnext" style="text-align:right;">&nbsp;</p>
<div class="gains">${rows}<div class="gr tot"><span>ごうけい</span><span>+${total}</span></div></div></div>
<p class="small" id="savemsg" style="font-size:12px;color:#5F5E5A;"></p>
<button class="btn pink" data-a="home">ホームへ</button>`, '#FBEAF0');
  confetti();
  const tk = S.tok;
  setTimeout(() => {
    if (tk !== S.tok) return;
    const e = $('#newst'); if (e) e.classList.add('on');
    const p = $('#pon'); if (p) p.classList.add('on');
    sfx.stamp();
  }, 700);
  setTimeout(() => { if (tk === S.tok) animateExp(R.expStart, S.lvExp[S.level], tk); }, 1500);
}
async function animateExp(fromExp, toExp, tk){
  let a = lvInfo(fromExp);
  const b = lvInfo(toExp);
  const bar = $('#xbar'), lvn = $('#lvn'), up = $('#lvup'), nx = $('#xnext');
  if (!bar) return;
  while (a.l < b.l) {
    bar.style.transition = 'width .7s ease-out'; bar.style.width = '100%';
    await sleep(750); if (tk !== S.tok) return;
    bar.style.transition = 'none'; bar.style.width = '0%';
    a = {l:a.l + 1}; lvn.textContent = a.l; up.classList.add('on'); sfx.lvup();
    await sleep(200); if (tk !== S.tok) return;
  }
  void bar.offsetWidth;
  bar.style.transition = 'width .7s ease-out'; bar.style.width = pct(b) + '%';
  nx.textContent = `つぎの レベルまで あと ${b.need - b.cur}`;
}

/* ---------- actions ---------- */
const ACT = {
  start: startRun,
  select: select,
  pick(d){ S.level = d.l; save(); pushSave(); home(); },
  gasopen(){ const b = $('#gasbox'); if (b) b.style.display = 'block'; const i = $('#gasurl'); if (i) i.value = G.url || ''; },
  async gasok(){
    const i = $('#gasurl'), m = $('#gasmsg'); const u = (i ? i.value : '').trim();
    if (!/^https:\/\/script\.google(usercontent)?\.com\//.test(u)) { if (m) m.textContent = 'URLが ちがうみたい（https://script.google.com/… を はりつけてね）'; return; }
    if (m) m.textContent = 'たしかめています…';
    G.url = u;
    try { const j = await gasGet({a:'words'}); if (!j || !j.ok) throw new Error('ng'); }
    catch (e) { if (m) m.textContent = 'つながりませんでした。URLと「アクセスできるユーザー：全員」を たしかめてね'; G.url = lsGet(GAS_KEY) || ''; return; }
    lsSet(GAS_KEY, u); await syncStart(); await pushSave(); select();
  },
  locked(){ setBub('もうすぐ できるよ！ まっててね'); },
  reset(){
    const b = $('#resetb');
    if (!S.resetArm) { S.resetArm = true; if (b) b.textContent = 'もう一度 おすと けします'; return; }
    try { localStorage.removeItem(SAVE_KEY); } catch (e) {}
    S.exp = 0; S.lvExp = {'4':0, '3':0}; S.known = {'4':[], '3':[]}; S.stamps = 4; S.level = '4';
    save(); pushSave();
    select();
  },
  again(){ showCard(); },
  hint(){ const R = S.run; R.hint = true; const h = $('#hintbox'); if (h) h.classList.add('show'); const b = $('#hintbtn'); if (b) b.style.display = 'none'; },
  selfok(){ stopRec(); judge(true, false); },
  selfng(){ stopRec(); judge(false, false); },
  override(){ const R = S.run; revertResult(); applyResult(R.hint ? 'd' : 'o'); R.byVoice = false; showAnswer(); },
  sayex(){ speakEx(cur(), false); },
  next: nextCard,
  go: showCard,
  home(){ if (S.stamps >= 7) { S.stamps = 0; save(); } S.run = null; home(); }
};
$app.addEventListener('click', e => {
  const b = e.target.closest('[data-a]'); if (!b) return;
  sfx.tap();
  const f = ACT[b.dataset.a]; if (f) f(b.dataset);
});

(function setupFromHash(){
  const m = /[#&]gas=([^&]+)/.exec(location.hash || '');
  if (m) { lsSet(GAS_KEY, decodeURIComponent(m[1])); history.replaceState(null, '', location.pathname + location.search); }
})();
loadSave();
select();
syncStart().then(() => { if (!S.run && document.getElementById('gasline')) select(); });
