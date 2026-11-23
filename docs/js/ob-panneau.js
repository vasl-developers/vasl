// ═══════════════════════════════════════════════════════════════
// OB DU SCÉNARIO — préparer une partie d'après le PDF de sa fiche
// « Depuis un PDF… » (dialogue des boards) : la page de la fiche est lue,
// la grille des boards pré-remplie (schéma, sens, overlays propres au
// scénario), puis l'OB est lu en tâche de fond. Après Ok, le panneau
// « OB du scénario » montre, camp par camp, chaque pion reconnu avec sa
// quantité, à glisser sur la carte comme depuis la palette.
// Rien n'est posé tout seul ; rien ne va au log tant que rien n'est posé.
// Un pion ambigu garde ses candidats, un pion illisible montre sa découpe
// et ouvre la palette sur ce qui en a été lu.
// Chargé à la demande (chargerModulesOb, js/boards.js), avec ob-image.js,
// ob-pdf.js, ob-ocr.js, ob-lecture.js, ob-carte.js et ob-reference.js.
// ═══════════════════════════════════════════════════════════════
let _ob = null;   // scénario en cours : {nom, fiche, num, sep, cases, arret, fen, lecture}

// Une lecture en cours est abandonnée (nouveau PDF, Cancel).
function obAbandonner(){
  if(!_ob) return;
  _ob.arret = true;
  obOcr.arreter();
  if(_ob.fen) _ob.fen.remove();
  if(_ob.rappel) _ob.rappel.remove();
  _ob = null;
}

// ── 1. Le PDF, depuis le dialogue des boards ────────────────────
async function obDepuisPdf(fichier){
  if(!fichier || !_bp) return;
  const bp = _bp;
  obAbandonner();
  // Les messages vont à la ligne d'état et, pour rester lisibles quand les
  // boards se chargent, sous la vignette de la fiche.
  const etat = t => {
    if(_bp !== bp) return;
    _bpEtat(t);
    const n = bp.w.querySelector('.bpPdfNote');
    if(n) n.replaceChildren(...t.split(' · ').map(x => Object.assign(document.createElement('div'), {textContent: x})));
  };
  const ligne = bp.w.querySelector('.bpEtat');
  if(ligne) ligne.classList.add('long');
  etat('Lecture du PDF…');
  let fiche;
  try { fiche = await obImageDuPdf(fichier); }
  catch(e){ etat('⚠️ ' + e.message + ' — la grille se remplit à la main.'); return; }
  if(_bp !== bp) return;
  _obVignettePdf(bp, fiche);
  etat('Fiche lue : ' + fichier.name);
  const ob = _ob = {nom: fichier.name, fiche, num: obCarte.numeroScenario(fichier.name, fiche.texte),
                    sep: null, cases: null, arret: false, fen: null, lecture: null, valide: false};
  etat('Chargement du moteur d’OCR… (la première fois, quelques Mo à télécharger)');
  try { await obOcr.demarrer({surProgression: _obProgression}); }
  catch(e){ etat('⚠️ ' + e.message + ' La grille se remplit à la main.'); if(_ob === ob) _ob = null; return; }
  if(_bp !== bp || _ob !== ob) return;
  etat('Lecture du schéma des boards…');
  const g = fiche.gris, cad = obLecture.cadresOb(g, obLecture.casesPions(g));
  let carte = null;
  try { carte = await obCarte.lireSchema(fiche, {limiteY: cad.cadres.length ? cad.cadres[0][1] : null}); }
  catch(e){ if(e && e.message === OB_ARRET) return; }
  if(_bp !== bp || _ob !== ob) return;
  const msgs = await _obPoserCarte(carte);
  if(_bp !== bp || _ob !== ob) return;
  etat(msgs.concat('overlays du scénario…').join(' · '));
  msgs.push(...await _obSso(ob.num));
  if(_bp !== bp || _ob !== ob) return;
  etat(msgs.join(' · ') + ' · OB en cours de lecture, panneau après Ok.');
  ob.lecture = _obLireOb(ob);
}

// Vignette de la page à côté de la grille (agrandie d'un clic).
function _obVignettePdf(bp, fiche){
  const box = bp.w.querySelector('.bpPdf');
  if(!box) return;
  const k = Math.min(1, 600 / fiche.rgba.w);
  const cv = document.createElement('canvas');
  cv.width = Math.round(fiche.rgba.w * k); cv.height = Math.round(fiche.rgba.h * k);
  const src = document.createElement('canvas');
  src.width = fiche.rgba.w; src.height = fiche.rgba.h;
  src.getContext('2d').putImageData(new ImageData(new Uint8ClampedArray(fiche.rgba.d), fiche.rgba.w, fiche.rgba.h), 0, 0);
  cv.getContext('2d').drawImage(src, 0, 0, cv.width, cv.height);
  cv.title = 'Fiche du scénario — cliquer pour agrandir ou réduire';
  cv.onclick = () => box.classList.toggle('grand');
  box.replaceChildren(cv, Object.assign(document.createElement('div'), {className: 'bpPdfNote'}));
  box.hidden = false;
}

// Grille : boards lus posés, cases non lues laissées vides et signalées.
async function _obPoserCarte(carte){
  if(!carte || (!carte.boards.length && !carte.vides.length))
    return ['schéma des boards non lu : la grille se remplit à la main'];
  let connus = null;
  try { connus = new Set((await listeBoards()).map(nomDuLibelle)); } catch(e){}
  if(!_bp) return [];
  const msgs = [], boards = [], vides = carte.vides.slice();
  for(const b of carte.boards){
    if(connus && !connus.has(b.nom)){ vides.push(b); msgs.push(`board ${b.numero} lu, absent de la liste VASL : à choisir`); continue; }
    boards.push(Object.assign(_boardNeuf(b.nom), {col: b.col, row: b.row, reversed: b.reversed}));
    if(b.sens === 'incertain') msgs.push(`board ${b.numero} : sens incertain, à vérifier`);
  }
  _bpPoserBoards(boards);
  while(_bp.nx < carte.nx) _bpAjouterColonne();
  while(_bp.ny < carte.ny) _bpAjouterLigne();
  _bpRendre();
  msgs.unshift(`${boards.length} board${boards.length > 1 ? 's' : ''} lu${boards.length > 1 ? 's' : ''} sur la fiche`);
  for(const v of vides) if(!v.numero) msgs.push(`board de la case ${v.col + 1},${v.row + 1} illisible : à choisir`);
  return msgs;
}

// Overlays propres au scénario : l'entrée « NN: Nom » des SSRControls d'un
// board posé, cochée et appliquée à ce board comme dans Terrain SSR.
async function _obSso(num){
  if(!num) return ['n° de scénario non lu : overlays du scénario non cherchés'];
  const boards = _bpBoardsControles();
  if(!boards.length) return [];
  let ed;
  try { ({ed} = await _teEditeur(boards)); } catch(e){ return ['overlays du scénario : ' + e.message]; }
  if(!_bp) return [];
  _teReset(ed, true);
  const faits = [];
  for(const o of ed.groupe){
    if(o.type !== 'Checkbox' || o.rangBoard === undefined) continue;
    const e = o.entrees.find(n => obCarte.idEntreeSso(n) === num);
    if(!e) continue;
    o.activer(e, true);
    try { _teAppliquerAuBoard(ed, o.rangBoard); faits.push(`${o.regles.get(e)} (${e})`); }
    catch(err){ _teReset(ed, false); faits.push(`${o.regles.get(e)} non appliqué (grille incomplète)`); }
  }
  return faits.length ? ['overlays du scénario ' + num + ' cochés : ' + faits.join(', ')] : [];
}

// ── 2. L'OB, lu en tâche de fond ────────────────────────────────
async function _obLireOb(ob){
  try {
    await obLecture.lireFiche(ob.fiche, {
      arret: () => ob.arret,
      surSeparation: sep => { if(_ob !== ob) return; ob.sep = sep; ob.cases = sep.cases; if(ob.fen) _obRendre(); },
      surCase: c => { if(_ob === ob && ob.fen) _obRendreCase(c); _obMajEtat(); },
    });
  } catch(e){
    if(!(e && e.message === OB_ARRET) && _ob === ob) ob.erreur = e.message || String(e);
  }
  if(_ob === ob){ ob.fini = true; _obMajEtat(); }
}
function _obProgression(){ _obMajEtat(); }

// Boards : Ok ouvre le panneau, Cancel abandonne la lecture.
function obFermetureBoards(valide){
  if(_ob && !valide && !_ob.fen) obAbandonner();
}
function obApresBoards(){
  if(_ob && !_ob.fen) ouvrirPanneauOb();
}

// ── 3. Le panneau « OB du scénario » ────────────────────────────
async function ouvrirPanneauOb(){
  const ob = _ob;
  if(!ob) return;
  if(ob.fen){ ob.fen.hidden = false; if(ob.rappel) ob.rappel.hidden = true; return; }
  const w = document.createElement('div');
  w.className = 'fenetre fenOb';
  w.setAttribute('role', 'dialog');
  w.setAttribute('aria-label', 'OB du scénario');
  w.innerHTML = `<div class="ft">OB du scénario<span class="obBoutons"><button type="button" class="obFermer" data-ob="reduire" aria-label="Réduire" title="Réduire (le panneau se rouvre par le bouton « OB du scénario »)">–</button><button type="button" class="obFermer" data-ob="quitter" aria-label="Fermer" title="Fermer l’OB du scénario">×</button></span></div>
    <div class="fc"><div class="obEtat"><progress value="0" max="1"></progress><span></span>
      <button type="button" data-ob="arreter">Arrêter</button></div>
      <div class="obCorps"></div>
      <p class="fnote">Glisser un pion vers la carte, ou cliquer pour le poser au centre ; au doigt, toucher puis toucher la carte.
      Le compteur baisse à chaque pose. Rien n'est posé tout seul.</p></div>`;
  document.body.appendChild(w);
  ob.fen = w;
  w.addEventListener('pointerdown', e => e.stopPropagation());
  w.addEventListener('keydown', e => e.stopPropagation());
  _deplacableParTitre(w);
  // Les boutons de la barre de titre ne la déplacent pas (sinon la capture
  // du pointeur par le titre avale leur clic).
  for(const b of w.querySelectorAll('.ft button')) b.addEventListener('pointerdown', e => e.stopPropagation());
  w.querySelector('[data-ob="reduire"]').onclick = () => _obReduire();
  w.querySelector('[data-ob="quitter"]').onclick = () => _obQuitter();
  // Échap : comme les autres fenêtres
  w.addEventListener('keydown', e => { if(e.key === 'Escape'){ e.preventDefault(); _obQuitter(); } });
  w.querySelector('[data-ob="arreter"]').onclick = () => { ob.arret = true; obOcr.arreter(); _obMajEtat(); };
  try { await chargerBDD(); } catch(e){}
  if(_ob !== ob) return;
  _obRendre();
  _obMajEtat();
}
// Fermer : la lecture s'arrête, le panneau disparaît. Les pions déjà posés
// restent sur la carte ; une confirmation évite de perdre l'OB par mégarde.
function _obQuitter(){
  const ob = _ob;
  if(!ob) return;
  if(!confirm('Fermer l’OB du scénario ? Les pions déjà posés restent sur la carte ; pour le retrouver, il faudra relire le PDF.')) return;
  obAbandonner();
}
// Réduit : un bouton flottant le rouvre (en tactile, la carte reste libre).
function _obReduire(){
  const ob = _ob;
  if(!ob || !ob.fen) return;
  ob.fen.hidden = true;
  if(!ob.rappel){
    ob.rappel = document.createElement('button');
    ob.rappel.type = 'button';
    ob.rappel.className = 'obRappel';
    ob.rappel.textContent = '♟ OB du scénario';
    ob.rappel.onclick = () => ouvrirPanneauOb();
    document.body.appendChild(ob.rappel);
  }
  ob.rappel.hidden = false;
}
function _obMajEtat(){
  const ob = _ob;
  if(!ob || !ob.fen) return;
  const p = obOcr.progression(), lues = (ob.cases || []).filter(c => c.verdict).length, n = (ob.cases || []).length;
  const pr = ob.fen.querySelector('.obEtat progress'), t = ob.fen.querySelector('.obEtat span');
  const fini = ob.fini || ob.arret;
  pr.hidden = fini;
  pr.max = Math.max(1, p.total); pr.value = p.faits;
  t.textContent = ob.erreur ? '⚠️ ' + ob.erreur
    : ob.arret && !ob.fini ? `Lecture arrêtée : ${lues} case(s) sur ${n} lue(s).`
    : fini ? `${ob.nom} : ${n} case(s) lue(s).`
    : `Lecture de l’OB… ${lues} / ${n || '?'} case(s)`;
  ob.fen.querySelector('[data-ob="arreter"]').hidden = fini;
}

const OB_NOMS_NAT = Object.freeze({Army: 'AMERICAN (Army)', 'U.S.M.C.': 'AMERICAN (U.S.M.C.)'});
const _obNomNat = n => n === '?' ? '? (à choisir)' : (OB_NOMS_NAT[n] || n);

// Groupes : un par cadre d'OB (nation lue, ou à choisir), dans l'ordre.
function _obRendre(){
  const ob = _ob;
  if(!ob || !ob.fen) return;
  const corps = ob.fen.querySelector('.obCorps');
  if(!ob.sep){ corps.textContent = 'Séparation des OB…'; return; }
  corps.replaceChildren();
  const groupes = new Map();
  for(const c of ob.cases){
    const k = String(c.nat) + '|' + c.groupe;
    if(!groupes.has(k)) groupes.set(k, {nat: c.nat, groupe: c.groupe, cases: []});
    groupes.get(k).cases.push(c);
  }
  for(const gr of groupes.values()){
    // Un cadre où rien n'est reconnu (piste des tours, règles…) est replié.
    const boite = document.createElement('details');
    boite.className = 'obGroupe';
    boite.open = true;
    gr.boite = boite;
    const titre = document.createElement('summary');
    titre.className = 'obTitre';
    const sel = document.createElement('select');
    sel.setAttribute('aria-label', 'Nation de cet OB');
    for(const n of ['?', ...Object.keys(OB_REF.chart)]) sel.add(new Option(_obNomNat(n), n, false, n === gr.nat));
    sel.onchange = () => _obChangerNation(gr, sel.value);
    titre.append(sel);
    const liste = document.createElement('div');
    liste.className = 'obListe';
    boite.append(titre, liste);
    corps.appendChild(boite);
    for(const c of gr.cases){
      c.el = document.createElement('div');
      c.el.className = 'obCase';
      c.gr = gr;
      liste.appendChild(c.el);
      _obRendreCase(c);
    }
    _obReplier(gr);
  }
}
function _obReplier(gr){
  if(!gr.boite) return;
  const lues = gr.cases.filter(c => c.verdict), vues = lues.filter(c => c.el && !c.el.classList.contains('illisible'));
  const rien = lues.length === gr.cases.length && !vues.length;
  gr.boite.open = !rien;
  gr.boite.classList.toggle('vide', rien);
  gr.boite.title = rien ? `${gr.cases.length} case(s), aucun pion reconnu` : '';
}

// Nation choisie par le joueur : les cases de ce cadre sont relues.
async function _obChangerNation(gr, nat){
  const ob = _ob;
  for(const c of gr.cases){ c.nat = nat; c.verdict = null; c.pion = null; _obRendreCase(c); }
  _obMajEtat();
  try { await obOcr.demarrer({surProgression: _obProgression}); } catch(e){ return; }
  ob.arret = false; ob.fini = false;
  await Promise.all(gr.cases.map(async c => {
    try { Object.assign(c, await obLecture.lireCase({g: ob.fiche.gris, rgba: ob.fiche.rgba}, c.cas, nat)); }
    catch(e){ c.verdict = 'erreur de lecture'; c.pion = {genre: 'inconnu'}; }
    if(_ob === ob) { _obRendreCase(c); _obMajEtat(); }
  }));
  if(_ob === ob){ ob.fini = true; _obMajEtat(); }
}

// Découpe du scan (la case et ses chiffres rouges dessous).
function _obDecoupe(c){
  const f = _ob.fiche.rgba, [x, y, w, h] = c.cas;
  const z = obImage.trancheCouleur(f, y, y + Math.round(h * 1.5), x, x + w);
  const cv = document.createElement('canvas');
  cv.width = z.w; cv.height = z.h;
  cv.getContext('2d').putImageData(new ImageData(new Uint8ClampedArray(z.d), z.w, z.h), 0, 0);
  cv.className = 'obScan';
  cv.title = 'Case lue sur la fiche';
  return cv;
}

// Case de palette du pion : définition développée, couche de l'image
// voulue au premier plan (niveau du trait emb2 = rang de l'image), comme
// le joueur qui ferait défiler les couches de la case de la palette.
function _obSlot(gpid, image){
  const x = _bdd && _bdd.parGpid.get(String(gpid));
  if(!x) return null;
  const def = developperDefinition(x.cmd);
  if(!def) return null;
  const S = def.S.slice();
  let nom = x.n;
  if(image){
    for(const l of _parseLayers({typ: def.T, eta: S})){
      const k = l.images.findIndex(s => s.replace(/^.*\//, '').replace(/\.(svg|gif|png)$/i, '') === image);
      if(k < 0) continue;
      const v = String(S[l.i] || '');
      S[l.i] = /^-?\d+/.test(v) ? v.replace(/^-?\d+/, String(k + 1)) : String(k + 1);
      break;
    }
  }
  return {n: nom, g: x.g, cmd: '+/null/' + encoderTraits(def.T) + '/' + encoderTraits(S)};
}
// Candidats VASL d'un pion lu : [{gpid, image, libelle}]
function _obCandidats(c){
  const p = c.pion || {}, out = [], vu = new Set();
  const aj = (gpid, image, libelle) => {
    const k = gpid + '|' + image;
    if(!gpid || vu.has(k)) return;
    vu.add(k); out.push({gpid, image, libelle});
  };
  if(['squad', 'HS', 'crew'].includes(p.genre) && p.image) aj(p.cases && p.cases[0] && p.cases[0][0], p.image, `${p.genre} ${p.valeur}`);
  if(p.genre === 'leader'){
    const l = ((OB_REF.leaders || {})[c.nat] || {})[p.valeur];
    if(l){ const im = Object.keys(l)[0]; aj(l[im][0][0], im, `leader ${p.valeur}`); }
  }
  if(p.ref) aj(p.ref.gpid, p.ref.image, _obLibelle(p.genre, p.ref));
  if(p.choisi) aj(p.choisi.gpid, p.choisi.image, _obLibelle(p.genre, p.choisi));
  if(!p.ref && !p.choisi && p.candidats) for(const r of p.candidats) if(r && r.gpid) aj(r.gpid, r.image, _obLibelle(p.genre, r));
  return out;
}
function _obLibelle(genre, r){
  if(genre === 'sw') return `${r.type || ''} ${r.valeurs || ''}`.trim();
  if(genre === 'canon') return `${r.type || ''} ${r.calibre || ''} ${r.nom_vt || r.nom || ''}`.trim();
  if(genre === 'vehicule') return `${r.nom_vt || r.nom || ''} ${r.calibre || ''}`.trim();
  return r.nom || '';
}
const _obQte = c => c.q === null || c.q === undefined ? null : c.q;

function _obRendreCase(c){
  if(!c.el) return;
  const el = c.el;
  el.replaceChildren();
  el.className = 'obCase';
  const p = c.pion;
  if(!c.verdict){
    el.classList.add('attente');
    el.append(_obDecoupe(c), Object.assign(document.createElement('span'), {className: 'obTexte', textContent: _ob && _ob.arret ? 'non lu (arrêté)' : 'lecture…'}));
    return;
  }
  const cands = _obCandidats(c);
  const ambigu = !!(p && (p.ambigu || (p.candidats && !p.ref && !p.choisi && cands.length > 1)));
  const identifie = cands.length === 1 && !ambigu;
  if(c.reste === undefined) c.reste = _obQte(c);
  const texte = document.createElement('span');
  texte.className = 'obTexte';
  const qte = c.q === null ? (c.qlu ? `×${c.qlu} ?` : '×?') : `×${c.reste}`;
  texte.textContent = (identifie ? cands[0].libelle + (p.probable ? ' (probable)' : '') : _obResume(c)) + '  ' + qte;
  texte.title = c.verdict + (c.qlu ? `  — quantité lue « ${c.qlu} »` : '');
  if(identifie){
    el.classList.add('identifie');
    if(c.reste === 0) el.classList.add('epuise');
    el.append(_obVignette(c, cands[0]), texte);
  } else if(ambigu && cands.length){
    el.classList.add('ambigu');
    const choix = document.createElement('div');
    choix.className = 'obChoix';
    for(const k of cands){
      const b = document.createElement('button');
      b.type = 'button'; b.className = 'obCand'; b.title = 'Choisir : ' + k.libelle;
      const mini = document.createElement('div'); mini.className = 'mini';
      const lib = Object.assign(document.createElement('span'), {textContent: k.libelle});
      b.append(mini, lib);
      const x = _obSlot(k.gpid, k.image);
      if(x) composerVignette(mini, lireSlot(x), 42);
      b.onclick = () => { c.pion = Object.assign({}, p, {choisi: Object.assign({}, k, {nom: k.libelle}), ambigu: false, candidats: null}); _obRendreCase(c); };
      choix.appendChild(b);
    }
    el.append(_obDecoupe(c), texte, choix);
  } else {
    el.classList.add('illisible');
    const b = document.createElement('button');
    b.type = 'button';
    b.textContent = 'Palette…';
    b.title = 'Ouvrir la palette, recherche pré-remplie avec ce qui a été lu';
    b.onclick = () => _obChercher(_obRecherche(c));
    el.append(_obDecoupe(c), texte, b);
  }
  if(c.gr) _obReplier(c.gr);
}
function _obResume(c){
  const p = c.pion || {};
  if(p.genre === 'leurre') return '? (leurre)';
  if(p.genre === 'fortification') return 'fortification (non traitée)';
  if(p.ambigu || (p.candidats && p.candidats.length > 1)) return 'ambigu, au choix :';
  return c.verdict.replace(/\s*\{.*\}\s*$/, '').replace(/ → .*$/, '');
}
function _obRecherche(c){
  const p = c.pion || {};
  if(p.lu && Array.isArray(p.lu) && p.lu[0]) return String(p.lu[0]);
  if(p.lu && p.lu.typ && p.lu.typ.length) return p.lu.typ[0];
  const m = /(\d{1,2}-\d{1,2}(?:-\d)?)/.exec(c.verdict || '');
  return m ? m[1] : '';
}
// Palette ouverte, recherche pré-remplie.
function _obChercher(texte){
  const r = document.getElementById('palRech');
  if(r){ r.value = texte || ''; r.dispatchEvent(new Event('input')); }
  if(typeof montrerOnglet === 'function') montrerOnglet('palette');
  if(_telLike()) _obReduire();
}

// Vignette d'un pion reconnu : glisser, cliquer, toucher-toucher, comme la
// palette (remplirGrille) ; chaque pose fait baisser le compteur.
function _obVignette(c, k){
  const d = document.createElement('div');
  d.className = 'vig';
  const x0 = _obSlot(k.gpid, k.image);
  if(!x0){
    d.textContent = '?';
    d.title = `gpid ${k.gpid} absent de la palette chargée`;
    return d;
  }
  const x = Object.assign({}, x0, {apresPose: () => {
    if(typeof c.reste === 'number' && c.reste > 0) c.reste--;
    _obRendreCase(c);
  }});
  d.title = `${k.libelle} (gpid ${k.gpid}) — glisser vers la carte, ou cliquer pour poser au centre ; au doigt, toucher puis toucher la carte`;
  d.setAttribute('aria-label', k.libelle);
  const mini = document.createElement('div');
  mini.className = 'mini';
  d.appendChild(mini);
  composerVignette(mini, lireSlot(x), 42);
  d.addEventListener('pointerdown', e => glisser(e, (mini.querySelector('img') || {}).src, 48 * SC * zoom, (cx, cy) => {
    const pt = clientVersCarte(cx, cy);
    if(pt) poserDepuisPalette(x, pt.x, pt.y);
    else chatLog('Lâché hors de la carte : rien posé');
  }, ev => {
    if(ev && ev.pointerType && ev.pointerType !== 'mouse'){ armerPose(x); _obReduire(); }
    else _poserAuCentre(x);
  }, {appuiLong: 300}));
  return d;
}
