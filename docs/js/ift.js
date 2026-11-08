// ═══════════════════════════════════════════════════════════════
// LANCER IFT (affichage tactile)
// Au doigt, le bouton IFT ouvre une feuille à deux roues : la colonne de
// FP de l'IFT (4 par défaut) et le modificateur du DR, de -6 à +8 (0 par
// défaut), avec deux cases, Leader et Green. À la validation, le DR est
// lancé, modifié, puis croisé avec la colonne sur la table A7 ; le
// résultat part au chat : « FP 4/37 : (5) + 2 = 7 > PTC », NE pour un « — ».
// Cowering (A7.9) : un DR original double décale le tir d'une colonne à
// gauche, de deux si Green (A19.33), d'aucune si un leader le dirige ;
// sous la colonne 1 FP, sans effet.
// Symboles de case, d'après la légende de l'IFT : ceux qui portent sur
// l'« Original … DR » se lisent dans la case du DR original, ceux de
// l'attaque de mine A-T (orange) dans la case du DR final.
// Au PC, et au raccourci Ctrl+Maj+I, IFT reste un simple jet.
// ═══════════════════════════════════════════════════════════════
const IFT_COLS = [
  {fp: '1', mm: '20'}, {fp: '2', mm: '30'}, {fp: '4', mm: '37'}, {fp: '6', mm: '50'},
  {fp: '8', mm: '60'}, {fp: '12', mm: '70'}, {fp: '16', mm: '80'}, {fp: '20', mm: '100'},
  {fp: '24', mm: '120'}, {fp: '30', mm: '150'}, {fp: '36+', mm: '200+'}
];

// Une case : symboles éventuels, une espace, le résultat. Ligne 0 = DR ≤ 0,
// ligne 15 = DR ≥ 15.
const IFT_TABLE = [
  ['1KIA','2KIA','2KIA','3KIA','3KIA','3KIA','4KIA','4KIA','5KIA','6KIA','7KIA'],
  ['K/1','1KIA','1KIA','2KIA','2KIA','2KIA','3KIA','3KIA','4KIA','5KIA','6KIA'],
  ['1MC','f K/1','f K/2','wFf 1KIA','wFf 1KIA','mwFf 1KIA','mRFf 2KIA','mRFf 2KIA','mRFf 3KIA','smRFf 4KIA','smRFfa 5KIA'],
  ['1MC','1MC','2MC','f K/2','f K/2','Mf K/3','mwFf 1KIA','mwFf 1KIA','mRFf 2KIA','smRFf 3KIA','smRFfa 4KIA'],
  ['NMC','1MC','1MC','2MC','2MC','3MC','Mf K/3','Mf K/4','mwFf 1KIA','smRFf 2KIA','smRFfa 3KIA'],
  ['PTC','NMC','1MC','1MC','2MC','2MC','3MC','3MC','Mf K/4','smwFf 1KIA','smRFfa 2KIA'],
  ['—','PTC','NMC','1MC','1MC','2MC','2MC','3MC','4MC','Mf K/4','smwFfa 1KIA'],
  ['—','—','PTC','NMC','1MC','1MC','2MC','2MC','3MC','4MC','MfA K/4'],
  ['—','—','—','PTC','NMC','1MC','1MC','2MC','2MC','3MC','A 4MC'],
  ['—','—','—','—','PTC','NMC','1MC','1MC','2MC','2MC','A 3MC'],
  ['—','—','—','—','—','PTC','NMC','1MC','1MC','2MC','A 2MC'],
  ['—','—','—','—','—','—','PTC','NMC','1MC','1MC','A 2MC'],
  ['—','—','—','—','—','—','—','PTC','NMC','1MC','A 1MC'],
  ['—','—','—','—','—','—','—','—','PTC','NMC','A 1MC'],
  ['—','—','—','—','—','—','—','—','—','PTC','A NMC'],
  ['—','—','—','—','—','—','—','—','—','—','A PTC']
];

// Légende des symboles, comme les annonces d'ASLChatter. `fin` : lu sur
// le DR final (attaque de mine A-T).
const IFT_SYMB = {
  f: {s: '•', c: '#e44', t: 'P. Flame if FT (B25.12)'},
  F: {s: '‡', c: '#e44', t: 'P. Flame if HE/HEAT (B25.13)'},
  w: {s: '•', c: '#ddd', t: 'P. wooden rubble if HE ≥ 70mm (B24.11)'},
  R: {s: '‡', c: '#ddd', t: 'P. rubble if HE ≥ 70mm (B24.11)'},
  m: {s: '•', c: '#6af', t: 'Conc. HE FFE/Aerial Bomb removes A-P/A-T minefield and/or Wire (B28.62, B26.52)'},
  M: {s: '‡', c: '#5c5', t: 'Conc. HE FFE/Aerial Bomb reduces minefield strength (B28.62)'},
  s: {s: '•', c: '#5c5', t: 'Conc. HE FFE/Aerial Bomb creates shellhole (B2.1)'},
  a: {s: '•', c: '#f90', fin: true, t: 'A-T Mine: eliminates AFV / burns ★ vehicle (B28.52)'},
  A: {s: '‡', c: '#f90', fin: true, t: 'A-T Mine: immobilizes AFV / eliminates ★ vehicle (B28.52)'}
};

const IFT_MODS = Array.from({length: 15}, (_, i) => i - 6);
const _IFT_H = 44;                // hauteur d'une ligne de roue (cf. .iftItem)
const _ift = {col: 2, mod: 6, leader: false, green: false};   // dernier réglage

function _iftCase(dr, col){
  const c = IFT_TABLE[Math.max(0, Math.min(15, dr))][col];
  const i = c.indexOf(' ');
  return i < 0 ? {sym: '', res: c} : {sym: c.slice(0, i), res: c.slice(i + 1)};
}

function boutonIFT(){
  if(_profil && _profil !== 'pc') ouvrirIFT();
  else lancerDR('IFT');
}

// Une roue à faire défiler au doigt ; un appui sur une ligne la choisit.
// La ligne choisie est celle qui se trouve sous le bandeau, mesurée à
// l'écran plutôt que déduite de scrollTop. immobile() dit si la roue a
// fini de glisser : au doigt, l'élan la fait encore tourner après le
// lâcher, et un jet validé entre-temps lisait une autre ligne.
function _iftRoue(libelles, i0, change){
  const r = document.createElement('div');
  r.className = 'iftRoue';
  r.innerHTML = '<div class="iftPad"></div>'
    + libelles.map((l, i) => `<div class="iftItem" data-i="${i}">${l}</div>`).join('')
    + '<div class="iftPad"></div>';
  const items = [...r.querySelectorAll('.iftItem')];
  // Écart, en px, entre le centre d'une ligne et celui du bandeau.
  const ecart = it => {
    const R = r.getBoundingClientRect(), q = it.getBoundingClientRect();
    return (q.top + q.height / 2) - (R.top + r.clientTop + r.clientHeight / 2);
  };
  r.index = () => {
    let k = 0, d = Infinity;
    items.forEach((it, i) => { const e = Math.abs(ecart(it)); if(e < d){ d = e; k = i; } });
    return k;
  };
  const marquer = () => {
    const i = r.index();
    items.forEach((it, k) => it.classList.toggle('on', k === i));
    return i;
  };
  let dernier = i0, bouge = 0;
  r.immobile = () => performance.now() - bouge > 150 && Math.abs(ecart(items[r.index()])) < 8;
  r.addEventListener('scroll', () => {
    bouge = performance.now();
    const i = marquer();
    if(i !== dernier){ dernier = i; change(i); }
  }, {passive: true});
  r.addEventListener('click', e => {
    const it = e.target.closest('.iftItem');
    if(it) r.scrollBy({top: ecart(it), behavior: 'smooth'});
  });
  // La position ne prend qu'une fois la roue affichée.
  r.placer = () => { r.scrollTop += ecart(items[i0]); marquer(); };
  return r;
}

function ouvrirIFT(){
  // Déplaçable, sur un voile transparent : on peut la pousser pour voir
  // la carte en dessous.
  const h = ouvrirFeuille({id: 'ift', titre: 'Lancer IFT', voile: false, deplacable: true});
  const c = document.createElement('div');
  c.className = 'iftCorps';
  c.innerHTML = '<div class="iftRoues">'
    + '<div class="iftCol"><div class="iftLib">FP</div><div class="iftBoite"></div></div>'
    + '<div class="iftCol"><div class="iftLib">Modificateur DR</div><div class="iftBoite"></div></div></div>'
    + '<div class="iftOpts"><label><input type="checkbox" name="leader"> Leader</label>'
    + '<label><input type="checkbox" name="green"> Green</label></div>'
    + '<div class="iftRes">—</div>'
    + '<div class="iftBoutons"><button type="button" class="fermer">Fermer</button>'
    + '<button type="button" class="valider">Valider</button></div>';
  h.corps.appendChild(c);
  const res = c.querySelector('.iftRes'), valider = c.querySelector('.valider');
  // Changer un réglage après un jet prépare le jet suivant.
  const pret = () => { valider.textContent = T('Valider'); };
  const [bFP, bMod] = c.querySelectorAll('.iftBoite');
  const rFP = _iftRoue(IFT_COLS.map(x => `${x.fp}<small>/${x.mm}</small>`), _ift.col,
                       i => { _ift.col = i; pret(); });
  const rMod = _iftRoue(IFT_MODS.map(m => m > 0 ? '+' + m : m < 0 ? '−' + (-m) : '0'), _ift.mod,
                        i => { _ift.mod = i; pret(); });
  bFP.appendChild(rFP); bMod.appendChild(rMod);
  rFP.placer(); rMod.placer();
  for(const k of ['leader', 'green']){
    const cb = c.querySelector(`input[name=${k}]`);
    cb.checked = _ift[k];
    cb.addEventListener('change', () => { _ift[k] = cb.checked; pret(); });
  }
  c.querySelector('.fermer').addEventListener('click', () => h.fermer());
  // Le jet attend que les deux roues soient arrêtées sur une ligne : même
  // position à deux relevés espacés de 150 ms. Un élan qui vient de partir
  // n'a pas encore émis d'événement scroll ; seul le relevé le voit.
  let attente = 0, avant = null;
  const jouer = () => {
    const pos = rFP.scrollTop + ',' + rMod.scrollTop;
    const arret = pos === avant && rFP.immobile() && rMod.immobile();
    avant = pos;
    if(!arret && ++attente < 20){ setTimeout(jouer, 150); return; }
    attente = 0; avant = null; valider.disabled = false;
    const r = lancerIFT(rFP.index(), IFT_MODS[rMod.index()], _ift.leader, _ift.green);
    res.textContent = r.ligne;
    for(const x of r.sp){
      const d = document.createElement('span'); d.className = 'iftSp';
      const b = document.createElement('b'); b.style.color = x.c; b.textContent = x.s + ' ';
      d.append(b, x.t);
      res.appendChild(d);
    }
    valider.textContent = T('Relancer');
  };
  valider.addEventListener('click', () => { if(!valider.disabled){ valider.disabled = true; jouer(); } });
  h.placer();      // la hauteur réelle, une fois le contenu posé
  return h;
}

// Lance le DR, le croise avec la table et publie le résultat au chat.
function lancerIFT(col, mod, leader, green){
  const {c, b, total} = lancerDR('IFT');
  const dr = total + mod;
  // Détail du calcul : « (DR original) ± modificateur = DR final ».
  const calcul = `(${total}) ${mod < 0 ? '-' : '+'} ${Math.abs(mod)} = ${dr}`;
  const sp = [];
  const cower = c === b && !leader ? (green ? 2 : 1) : 0;
  const colT = col - cower;
  if(cower) sp.push({s: '⇐', c: '#f90',
    t: `Cowering ${cower} column${cower > 1 ? 's' : ''} (A7.9${green ? ', Green A19.33' : ''})`});
  let ligne;
  if(colT < 0) ligne = `FP ${IFT_COLS[col].fp}/${IFT_COLS[col].mm} : ${calcul} > NE`;
  else {
    const fin = _iftCase(dr, colT), orig = _iftCase(total, colT);
    ligne = `FP ${IFT_COLS[colT].fp}/${IFT_COLS[colT].mm} : ${calcul} > ${fin.res === '—' ? 'NE' : fin.res}`;
    for(const k of orig.sym) if(!IFT_SYMB[k].fin) sp.push(IFT_SYMB[k]);
    for(const k of fin.sym) if(IFT_SYMB[k].fin) sp.push(IFT_SYMB[k]);
  }
  envoyerChat(ligne + (sp.length ? ' — ' + sp.map(x => x.s + ' ' + x.t).join(', ') : ''));
  return {ligne, sp, dr, total};
}

// Raccourcis d'ASLChatter : F2 DR, F1 dr, Ctrl+Maj+lettre par catégorie.
function _brancherDes(){
  for(const id of ['sanAxis', 'sanAllied', 'bbAxis', 'bbAllied'])
    document.getElementById(id).addEventListener('change', changerInfo);
  const DR = {I: 'IFT', H: 'TH', K: 'TK', M: 'MC', R: 'Rally', C: 'CC', T: 'TC'};
  const dr = {S: 'SA', D: 'RS'};
  document.addEventListener('keydown', e => {
    if(/^(INPUT|SELECT|TEXTAREA)$/.test(e.target.tagName)) return;
    if(e.key === 'F2'){ e.preventDefault(); lancerDR('Other'); return; }
    if(e.key === 'F1'){ e.preventDefault(); lancerdr('Other'); return; }
    if(e.key === 'F6'){ e.preventDefault(); basculerOBA(); return; }
    if(e.ctrlKey && e.shiftKey){
      const k = e.key.toUpperCase();
      if(DR[k]){ e.preventDefault(); lancerDR(DR[k]); }
      else if(dr[k]){ e.preventDefault(); lancerdr(dr[k]); }
    }
  });
}

