// ═══════════════════════════════════════════════════════════════
// LECTURE D'UNE PIÈCE VASSAL
// Un segment AddPiece : +/UID/TYPE/ÉTAT. TYPE et ÉTAT sont des listes de
// traits séparées par des tabulations ; le dernier trait est le
// BasicPiece (« piece;clone;suppr;image;nom »), dont l'état commence par
// « carte;x;y; ».
// ═══════════════════════════════════════════════════════════════
function _splitUnescaped(s, sep){
  const out = []; let cur = '';
  for(let i = 0; i < s.length; i++){
    if(s[i] === '\\' && i+1 < s.length){ cur += s[i] + s[i+1]; i++; continue; }
    if(s[i] === sep){ out.push(cur); cur = ''; continue; }
    cur += s[i];
  }
  out.push(cur);
  return out;
}

// Traits d'un TYPE ou d'un ÉTAT. VASSAL emboîte les décorateurs : chaque
// niveau échappe d'une contre-oblique les tabulations du suivant, si bien
// que la tabulation qui ferme le trait n° i est précédée d'exactement i
// contre-obliques. Une tabulation qui en a d'autres appartient à un
// contenu imbriqué — la définition du DM que pose une escouade, par
// exemple — et ne sépare rien.
function _traits(s){
  const out = [];
  let debut = 0;
  for(let k = 0; k < s.length; k++){
    if(s[k] !== '\t') continue;
    let b = 0;
    while(k - 1 - b >= debut && s[k - 1 - b] === '\\') b++;
    if(b === out.length){ out.push(s.substring(debut, k - b)); debut = k + 1; }
  }
  out.push(s.substring(debut));
  return out;
}

function _splitTypeState(seg){
  const te = _typeEtEtat(seg);
  if(te){
    const typ = _traits(te.type), eta = _traits(te.etat);
    if(typ.length === eta.length) return {typ, eta};
  }
  return _splitTypeStateEquilibre(seg);
}

// Repli : TYPE et ÉTAT ont le même nombre de champs tabulés ; on cherche
// la frontière qui les équilibre.
function _splitTypeStateEquilibre(seg){
  const parts = _splitUnescaped(seg, '/');
  if(parts.length < 4) return null;
  for(let k = parts.length - 1; k > 2; k--){
    const typ = parts.slice(2, k).join('/').split('\t');
    const eta = parts.slice(k).join('/').split('\t');
    if(typ.length === eta.length) return {typ, eta};
  }
  return null;
}

// Les deux moitiés brutes d'un segment, telles qu'on les réécrit.
// Le type se termine au BasicPiece ; le « / » qui suit est le séparateur.
// Le nom peut contenir des « / » échappés (« MA\/SA Malf », « BU \/ CE ») :
// les refuser rendait 519 pions de la palette impossibles à poser.
function _typeEtEtat(seg){
  if(!seg) return null;
  const m = seg.match(/piece;[^;]*;[^;]*;[^;]*;(?:\\.|[^\\\t\/])*\//);
  if(!m) return null;
  const debut = seg.indexOf('/', 2) + 1;
  return {type: seg.substring(debut, m.index + m[0].length - 1),
          etat: seg.substring(m.index + m[0].length)};
}

// ═══ REGISTRE DES TYPE ═══
// Un même modèle de pion (une 4-6-7 allemande, un « ? », un DM…) porte le
// même TYPE VASSAL, souvent long de plusieurs centaines de caractères. Il
// est gardé une fois ici ; le pion n'en tient que la référence (typeRef)
// et son ÉTAT (etat). p.seg recompose le segment, octet pour octet, pour
// l'export et tout ce qui le lit. Le texte gardé est la sous-chaîne exacte
// lue dans le fichier, sans normalisation.
//   TYPES   texte du TYPE → {id, type}
// La frontière TYPE / ÉTAT est fixée une fois, à la création du pion,
// par _typeEtEtat (la règle de toujours) ; si elle échoue, le pion reste
// en « segment brut » (typeRef nul, _seg tel quel).
const TYPES = new Map();
function entreeType(type){
  let e = TYPES.get(type);
  if(!e){ e = {id: TYPES.size, type}; TYPES.set(type, e); }
  return e;
}

// Ce que tout pion hérite : p.seg en lecture seule. Une écriture oubliée
// échoue bruyamment plutôt que de perdre l'état sans rien dire.
const PION = {
  get seg(){ return this.typeRef ? '+/' + this.uid + '/' + this.typeRef.type + '/' + this.etat : this._seg; },
  set seg(v){ throw new TypeError('p.seg est en lecture seule : fixerEtat(p, état)'); }
};

// Attache au registre le segment s du pion p. Le segment recomposé doit
// redonner s à l'identique, sinon le pion le garde brut.
function _attacherSeg(p, s){
  const te = _typeEtEtat(s);
  if(te && p.uid && '+/' + p.uid + '/' + te.type + '/' + te.etat === s){
    p.typeRef = entreeType(te.type); p.etat = te.etat; p._seg = null;
  } else {
    p.typeRef = null; p.etat = null; p._seg = s;
  }
}

// TYPE et ÉTAT d'un pion, comme _typeEtEtat(p.seg), sans redécouper.
// Un objet qui ne porte qu'un seg (pièce de la palette, essai) y passe.
function tePion(p){
  if(!p) return null;
  return p.typeRef ? {type: p.typeRef.type, etat: p.etat} : _typeEtEtat(p.seg);
}

// Le seul endroit qui change l'ÉTAT d'un pion. Un pion brut dont le
// segment se découpe pourtant (identifiant à part) est réécrit comme
// avant, puis rattaché au registre.
function fixerEtat(p, etat){
  if(p.typeRef){ p.etat = etat; return; }
  const te = _typeEtEtat(p._seg);
  if(te) _attacherSeg(p, '+/' + p.uid + '/' + te.type + '/' + etat);
}

// Empreinte de l'état d'un pion, pour les caches qui ne doivent se refaire
// que s'il a changé (comparaison sans recomposer le segment).
const empreinteEtat = p => p.typeRef ? {r: p.typeRef, e: p.etat} : {r: null, e: p.seg};
const memeEtat = (a, p) => !!a && (p.typeRef ? a.r === p.typeRef && a.e === p.etat : a.r === null && a.e === p.seg);

// Liste de noms d'images nus (« mofan », « a,b ») : au moins un nom, et
// rien qu'un « ; » échappé aurait pu y glisser.
const _listeNoms = s => /\w/.test(s || '') && s.split(',').every(n => /^[\w()+. -]*$/.test(n.trim()));

// Couches d'images (traits emb2 / Embellishment). L'état est le niveau :
// positif la couche est dessinée, négatif elle est en sommeil.
function _parseLayers(ts){
  const out = [];
  if(!ts) return out;
  let props = null;
  for(let i = 0; i < ts.typ.length; i++){
    if(!/^emb2;/.test(ts.typ[i])) continue;
    const ch = ts.typ[i].split(';');
    // La liste d'images est le 17e champ d'un emb2. Ses chemins n'ont pas
    // toujours d'extension ni de dossier à deux lettres (« tables\/turn_axis »,
    // ou « mofan », « mober », « mohro » : Fanatic, Berserk, Heroic des
    // pions posés avec un module plus ancien).
    let liste = ch.length > 17 && (/\//.test(ch[16]) || _listeNoms(ch[16])) ? ch[16] : null;
    if(!liste) liste = ch.find(c => /\.(svg|gif|png)/i.test(c));
    if(!liste) liste = ch.find(c => /(^|,)\s*\\?[A-Za-z]{2}\\?\/[\w()+.-]+/.test(c));
    if(!liste) continue;
    const images = liste.replace(/\\/g,'').split(',').map(s => s.trim());
    const iL = ch.indexOf(liste);
    const dx = parseInt(ch[iL-2], 10), dy = parseInt(ch[iL-1], 10);
    const niveau = parseInt((ts.eta[i] || '').replace(/\\+$/,'').trim(), 10);
    let actif = !isNaN(niveau) && niveau > 0;
    // Couche qui suit une propriété, dessinée seulement selon une autre
    // (onlyPropertyName / onlyPropertyState, champs 32-33) : comme
    // Embellishment.draw(), elle n'est dessinée que si checkProperty(nom)
    // vaut l'état attendu. La lettre de classe des escouades VASL
    // (classId : CIDs/class_E.svg) ne paraît ainsi que sur la face brisée
    // (BRK_Active) ; la face saine porte déjà la sienne dans son image.
    const seul = ch[22] === 'true' ? (ch[32] || '').replace(/\\/g, '').trim() : '';
    if(actif && seul){
      if(!props) props = _proprietes(ts);
      const v = props[seul];
      const vrai = v !== undefined && v !== '' && v !== 'false' && v !== '0';
      if(vrai !== ((ch[33] || 'true').replace(/\\/g, '').trim() === 'true')) actif = false;
    }
    out.push({i, images, dx: isNaN(dx) ? 0 : dx, dy: isNaN(dy) ? 0 : dy,
      niveau: isNaN(niveau) ? 0 : Math.abs(niveau), actif});
  }
  return out;
}

const _imgCouche = e => e.images[Math.max(0, Math.min(e.images.length-1, e.niveau-1))];

// Visage du pion : la couche active la plus externe qui porte un pion
// complet (dossier de nationalité en minuscules : ge/, am/…). Les
// dossiers ML/ et MS/ sont des marqueurs, superposés par-dessus.
function _faceFromLayers(etats){
  for(const e of etats){
    if(!e.actif || !e.images.length) continue;
    const img = _imgCouche(e);
    if(img && /^[a-z]{2}\//.test(img)) return img;
  }
  for(const e of etats){
    if(!e.actif) continue;
    const img = _imgCouche(e);
    if(img && img.trim()) return img;
  }
  return null;
}

// Trait d'un type donné : son type et son état, ou null.
function _trait(ts, re){
  if(!ts) return null;
  for(let i = 0; i < ts.typ.length; i++)
    if(re.test(ts.typ[i].replace(/\\/g,''))) return {t: ts.typ[i].replace(/\\/g,''), e: (ts.eta[i] || '').replace(/\\/g,'')};
  return null;
}

// Tout ce que l'affichage tire d'un segment AddPiece.
// Un pion tiré de la palette garde ses traits « prototype;Nom » : VASSAL
// les développe à l'usage, avec l'état par défaut du prototype. Pour
// l'affichage on fait de même — c'est là que vivent, par exemple, les
// couches de SAN et de brisement du sniper. L'export, lui, garde le
// segment tel quel.
function _developperPrototypes(ts, prof){
  if(!ts || !_bdd || !_bdd.protos || prof > 6) return ts;
  const typ = [], eta = [];
  for(let i = 0; i < ts.typ.length; i++){
    const m = ts.typ[i].replace(/\\/g, '').match(/^prototype;([^;]*)/);
    const def = m && _bdd.protos.get(m[1]);
    const pts = def && _developperPrototypes(_splitTypeState(def), prof + 1);
    if(pts){
      // Sans le BasicPiece du prototype, dernier trait de sa définition.
      typ.push(...pts.typ.slice(0, -1));
      eta.push(...pts.eta.slice(0, -1));
    } else {
      typ.push(ts.typ[i]); eta.push(ts.eta[i]);
    }
  }
  return {typ, eta};
}

// ── Plan de développement d'un TYPE du registre, fait une fois : les
// traits développés (typ) et, pour chacun, d'où vient son état — l'indice
// du trait dans l'ÉTAT du pion, ou l'état par défaut du prototype,
// constant. Même résultat que _developperPrototypes(_splitTypeState(seg), 0)
// quand TYPE et ÉTAT ont autant de traits (sinon on y revient).
// Le développement dépend de _bdd, qui arrive plus tard : le plan note
// les prototypes avec lesquels il a été fait, et se refait s'ils changent
// (chargerBDD vide aussi les plans : _viderPlans).
function _planType(e){
  const protos = (_bdd && _bdd.protos) || null;
  if(e.plan && e.plan.protos === protos) return e.plan;
  const typ0 = _traits(e.type), typ = [], src = [];
  for(let i = 0; i < typ0.length; i++){
    const m = protos && typ0[i].replace(/\\/g, '').match(/^prototype;([^;]*)/);
    const def = m && protos.get(m[1]);
    const pts = def && _developperPrototypes(_splitTypeState(def), 1);
    if(pts){
      typ.push(...pts.typ.slice(0, -1));
      for(const x of pts.eta.slice(0, -1)) src.push({e: x});
    } else { typ.push(typ0[i]); src.push(i); }
  }
  return e.plan = {protos, n: typ0.length, typ, src};
}
function _viderPlans(){ for(const e of TYPES.values()) e.plan = null; }
// Nombre de plans faits (mesure, tests).
let _plansFaits = 0;

// Traits développés d'un TYPE du registre pour un ÉTAT donné, ou null
// (découpe en repli : l'appelant reprend le chemin complet).
function _tsPlan(e, etat){
  const avant = e.plan;
  const pl = _planType(e);
  if(pl !== avant) _plansFaits++;
  const eta0 = _traits(etat);
  if(eta0.length !== pl.n) return null;
  return {typ: pl.typ, eta: pl.src.map(s => typeof s === 'number' ? eta0[s] : s.e)};
}

// Traits développés d'un pion : _developperPrototypes(_splitTypeState(p.seg), 0)
// sans redécouper le TYPE ni redévelopper ses prototypes. Les tableaux
// typ sont partagés entre les pions d'un même TYPE : ne pas les modifier.
function tsPion(p){
  if(p && p.typeRef){
    const ts = _tsPlan(p.typeRef, p.etat);
    if(ts) return ts;
  }
  return _developperPrototypes(_splitTypeState(p.seg), 0);
}
// Même chose pour un segment brut (palette, réseau) : par le plan si son
// TYPE est déjà au registre.
function tsSeg(seg){
  const te = _typeEtEtat(seg), e = te && TYPES.get(te.type);
  const ts = e && _tsPlan(e, te.etat);
  return ts || _developperPrototypes(_splitTypeState(seg), 0);
}

// Texte des traits Label (TextLabel) : l'état est le texte saisi.
//   label;touche;menu;taille;couleur;fond;posV;décV;posH;décH;…
// ── Labels (trait TextLabel de VASSAL, « label; ») :
//   label;touche;menu;taille;fond;texte;posV;décV;posH;décH;justV;justH;
//         format du nom;police;style;rotation;propriété;description
// L'état est le texte saisi. Un pion en porte souvent plusieurs : Label,
// Name des chefs (écrit à 270°), ROF, Gun, MH, Strength, Cloak ID…
const _texteLabel = v => (v || '').replace(/\\+$/, '').replace(/\\(.)/g, '$1').trim();

// Propriétés d'un pion, pour les « $Nom$ » d'un label. VASSAL évalue le
// texte d'un label : la roue des phases affiche « $TurnName_Level$ »,
// c'est-à-dire le niveau de sa couche TurnName, soit le numéro du tour.
//   emb2 : <nom>_Level, <nom>_Name, <nom>_Image, <nom>_Active
//   PROP (DynamicProperty) et mark (Marker) : nom → valeur
function _proprietes(ts){
  const pr = {};
  if(!ts) return pr;
  const net = v => (v || '').replace(/\\+$/, '').replace(/\\(.)/g, '$1');
  // Comme getProperty, le trait le plus externe répond le premier : un
  // nom porté deux fois (DBCounterType) garde la valeur de l'extérieur.
  const def = (k, v) => { if(!(k in pr)) pr[k] = v; };
  const calc = [];
  for(let i = 0; i < ts.typ.length; i++){
    const ch = ts.typ[i].split(';');
    if(ch[0] === 'emb2'){
      const nom = net(ch[19]);
      if(!nom) continue;
      const v = parseInt(net(ts.eta[i]), 10);
      const niv = isNaN(v) ? 1 : Math.abs(v);
      const premier = parseInt(ch[24], 10);
      const noms = (ch[17] || '').split(/(?<!\\),/).map(net);
      const ims = net(ch[16]).split(',');
      def(nom + '_Level', String(niv - 1 + (isNaN(premier) ? 1 : premier)));
      def(nom + '_Name', (noms[niv - 1] || '').replace(/^\+\s*|\s*\+$/g, '').trim());
      def(nom + '_Image', ims[niv - 1] || '');
      def(nom + '_Active', String(v > 0));
    } else if(ch[0] === 'rotate' && net(ch[8])){
      // FreeRotator : <nom>_Facing, de 1 au nombre de faces.
      def(net(ch[8]) + '_Facing', String((parseInt(net(ts.eta[i]), 10) || 0) + 1));
    } else if(ch[0] === 'PROP' && ch[1]){
      def(net(ch[1]), net(ts.eta[i]));
    } else if(ch[0] === 'mark' && ch[1]){
      def(net(ch[1]), net(ts.eta[i]));
    } else if(ch[0] === 'calcProp' && ch[1] && !(net(ch[1]) in pr)){
      calc.push([net(ch[1]), _seq(ts.typ[i], ';')[2] || '']);
    }
  }
  // CalculatedProperty : une expression sur les autres propriétés, qui
  // peut en citer une autre calculée (« finalammo » cite « ammoText ») ;
  // autant de passes que de propriétés calculées résout leurs chaînes.
  for(let n = 0; n < calc.length; n++)
    for(const [k, e] of calc){
      const v = evaluerExpression(e, pr);
      pr[k] = v === undefined ? '' : String(v);
    }
  return pr;
}

// Texte visible d'un fragment HTML de label.
const _texteHtml = s => s.replace(/<[^>]*>/g, '').replace(/&nbsp;?/g, '\u00a0')
  .replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&amp;/g, '&');

// Label HTML (JLabel de Swing) : seules les balises de texte passent —
// couleur, barré, gras, italique, souligné, retour à la ligne. Le texte
// vient du fichier : rien d'autre n'en est repris (DOMParser n'exécute
// ni ne charge rien).
function _htmlLabel(html){
  const doc = new DOMParser().parseFromString(html, 'text/html');
  const copier = n => {
    if(n.nodeType === 3) return [document.createTextNode(n.nodeValue)];
    if(n.nodeType !== 1) return [];
    const enfants = [...n.childNodes].flatMap(copier);
    const tag = n.tagName.toLowerCase();
    let e = null;
    if(tag === 'br') return [document.createElement('br')];
    if(tag === 'font'){
      e = document.createElement('span');
      const c = n.getAttribute('color');
      if(c && /^#?\w+$/.test(c)) e.style.color = c;
    } else if(tag === 'strike' || tag === 's' || tag === 'del') e = document.createElement('s');
    else if(['b', 'i', 'u', 'sub', 'sup'].includes(tag)) e = document.createElement(tag);
    if(!e) return enfants;
    e.append(...enfants);
    return [e];
  };
  return [...doc.body.childNodes].flatMap(copier);
}
function remplirLabel(e, t){
  if(t.html) e.replaceChildren(..._htmlLabel(t.html));
  else e.textContent = t.texte;
}

function _etiquettes(ts){
  const out = [];
  if(!ts) return out;
  const pr = _proprietes(ts);
  for(let i = 0; i < ts.typ.length; i++){
    if(!/^label;/.test(ts.typ[i])) continue;
    // « $nom$ » prend la valeur de la propriété. Un texte « <html>… »
    // (munitions épuisées des véhicules : « s WP », barrés en rouge) garde
    // sa mise en forme, réduite par _htmlLabel aux balises de texte.
    const brut = _texteLabel(ts.eta[i]).replace(/\$([^$\s]+)\$/g, (m, n) => pr[n] !== undefined ? pr[n] : '');
    const html = /^\s*<html>/i.test(brut) ? brut : null;
    if(_estPageHtml(html)) continue;
    const texte = _texteHtml(brut).trim();
    if(!texte) continue;
    const ch = ts.typ[i].split(';');
    const rgb = v => /^\d+,\d+,\d+$/.test(v || '') ? `rgb(${v})` : null;
    out.push({i, texte, html, taille: parseInt(ch[3], 10) || 10,
              fond: rgb(ch[4]), couleur: rgb(ch[5]) || '#000',
              posV: ch[6] || 't', decV: parseInt(ch[7], 10) || 0,
              posH: ch[8] || 'c', decH: parseInt(ch[9], 10) || 0,
              justV: ch[10] || 'b', justH: ch[11] || 'c',
              police: ch[13] || 'Dialog', style: parseInt(ch[14], 10) || 0,
              rotation: parseInt(ch[15], 10) || 0});
  }
  return out;
}

// Nom du pion, comme Labeler.getName() : chaque label non vide applique
// son format (« $pieceName$ ($label$) ») au nom des traits intérieurs.
function _nomAvecLabels(ts, nom){
  if(!ts) return nom;
  for(let i = ts.typ.length - 1; i >= 0; i--){
    // Couche active (Embellishment.getName) : le nom de son niveau
    // remplace le nom, ou le complète s'il contient « + » — « broken + »
    // donne « broken 6-6-7 Esq », « + Dispersed » un suffixe.
    if(/^emb2;/.test(ts.typ[i])){
      const v = parseInt((ts.eta[i] || '').replace(/\\+$/, ''), 10);
      if(!(v > 0)) continue;
      // « \+ » est un « + » littéral : « White \+3 Smoke » reste entier.
      const noms = (ts.typ[i].split(';')[17] || '').split(/(?<!\\),/);
      const n = noms[v - 1];
      if(!n) continue;
      const net = x => x.replace(/\\(.)/g, '$1');
      const k = n.search(/(?<!\\)\+/);
      nom = k < 0 ? net(n) : (k === 0 ? nom + net(n.substring(1)) : net(n.substring(0, k)) + nom);
      continue;
    }
    if(!/^label;/.test(ts.typ[i])) continue;
    const texte = _texteLabel(ts.eta[i]);
    if(!texte) continue;
    const format = (ts.typ[i].split(';')[12] || '$pieceName$ ($label$)').replace(/\\(.)/g, '$1');
    nom = format.replace(/\$pieceName\$/g, nom).replace(/\$label\$/g, texte)
                .replace(/\$[^$]*\$/g, '').replace(/\(\s*\)/g, '').trim() || nom;
  }
  return nom;
}
// Marque « déplacé » (trait moved) : icône posée par son coin haut-gauche
// au centre du pion, décalée de (dx, dy), comme MovementMarkable.
function _deplace(ts){
  if(!ts) return null;
  for(let i = 0; i < ts.typ.length; i++){
    const id = ts.typ[i].split(';')[0];
    if(id !== 'moved' && id !== 'markmoved') continue;
    if(!/^true/.test((ts.eta[i] || '').replace(/\\/g, ''))) continue;
    const ch = ts.typ[i].replace(/\\/g, '').split(';');
    // VASL (MarkMoved) : « moved;image » — bandeau <image>.svg contre le
    // bord droit du pion, aligné en haut.
    if(id === 'moved') return {i, img: (ch[1] || 'moved') + '.svg', droite: true};
    // VASSAL (MovementMarkable) : icône posée au coin haut-gauche du pion,
    // décalée de (dx, dy).
    return {i, img: ch[1] || 'moved', dx: parseInt(ch[2], 10) || 0, dy: parseInt(ch[3], 10) || 0};
  }
  return null;
}
// Tourelle (trait VASL « turr ») : image <avant|arrière><TCA>.svg.
//   turr;avant;arrière;touche cw;touche ccw[;r]  — état : « TCA;;flipped »
// Le TCA est relatif à la caisse : 3, c'est dans l'axe. Comme
// Turreted.isActive, rien n'est dessiné quand la tourelle est dans l'axe
// et l'équipage dans son état par défaut (image « avant ») : un char CT
// BU n'a pas de marqueur. Avec « r », getVehicleCA() vaut toujours 3 ;
// sans, c'est le niveau de la couche « Rotate » de la caisse.
// Turreted.getCurrentImageBounds : l'image (68 px) est centrée sur le
// pion, puis décalée selon le TCA, du côté où pointe la tourelle.
const _DECALAGE_TCA = {1: [20, -10], 2: [20, 5], 3: [20, 20], 4: [-10, 20], 5: [-10, 5], 6: [-10, -10]};
function _tourelle(ts){
  if(!ts) return null;
  for(let i = 0; i < ts.typ.length; i++){
    if(!/^turr;/.test(ts.typ[i])) continue;
    const ch = ts.typ[i].replace(/\\/g, '').split(';');
    const e = (ts.eta[i] || '').replace(/\\/g, '').split(';');
    const v = parseInt(e[0], 10);
    if(!(v > 0)) return null;
    const retourne = e[e.length - 1] === 'true';
    let ca = 3;
    if(ch[5] !== 'r'){
      ca = -1;
      for(let j = i + 1; j < ts.typ.length; j++){
        if(!/^emb2?;/.test(ts.typ[j]) || !/Rotate/.test(ts.typ[j])) continue;
        ca = Math.abs(parseInt(ts.eta[j], 10)) || -1;
        break;
      }
    }
    if(v === ca && !retourne) return null;
    const [dx, dy] = _DECALAGE_TCA[v] || [0, 0];
    return {i, img: (retourne ? ch[2] : ch[1]) + v + '.svg', dx, dy};
  }
  return null;
}

// ── Trait Immobilized de VASSAL (« immob; ») :
//   immob;sélection;mouvement;empilement;description
// Sélection : i (Maj+clic), t (Ctrl), c (Alt), n (jamais). Empilement :
// L la pièce s'empile, R (défaut) jamais — elle reste seule à sa place.
function _immobilise(ts){
  if(!ts) return null;
  const t = ts.typ.find(x => /^immob;/.test(x));
  if(!t) return null;
  const ch = t.replace(/\\(.)/g, '$1').split(';');
  const sel = ch[1] || '', emp = ch[3] === undefined || ch[3] === '' ? 'R' : ch[3];
  return {maj: /[itc]/.test(sel), jamais: sel.includes('n'), empile: emp.includes('L')};
}
// Maj enfoncée : les pièces à sélection Maj+clic deviennent saisissables.
const _toucheMaj = e => document.body && document.body.classList.toggle('maj', !!e.shiftKey);
window.addEventListener('keydown', _toucheMaj, true);
window.addEventListener('keyup', _toucheMaj, true);
window.addEventListener('blur', () => document.body && document.body.classList.remove('maj'));

// ── Orientation (trait FreeRotator de VASSAL, « rotate; ») :
//   rotate;facettes;touche cw;touche ccw;texte cw;texte ccw;…
// Facettes fixes (6 pour un Covered Arc d'hexagone, 8 pour la
// boussole) : l'état est l'indice de la facette, et chaque cran CW tourne
// de 360/n degrés en sens horaire — validAngles[i] = -i·360/n, dessiné
// à -angle. Rotation libre (1 facette) : l'état est l'angle lui-même,
// en degrés, compté en sens antihoraire (« -45.0 »).
function _rotations(ts){
  const out = [];
  if(!ts) return out;
  for(let i = 0; i < ts.typ.length; i++){
    if(!/^rotate;/.test(ts.typ[i])) continue;
    const n = parseInt(ts.typ[i].split(';')[1], 10) || 1;
    const e = (ts.eta[i] || '').replace(/\\/g, '').trim();
    let a;
    if(n <= 1) a = -(parseFloat(e) || 0);
    else { const v = parseInt(e, 10) || 0; a = ((v % n + n) % n) * (360 / n); }
    a = ((a % 360) + 360) % 360;
    // Gardé même à 0° : ce qui est à l'intérieur suit la vue tournée.
    out.push({i, angle: a});
  }
  return out;
}
// Angle d'un trait de rang i : la somme des rotate qui l'englobent.
const _angleInterieur = (rotations, i) =>
  ((rotations || []).reduce((a, r) => r.i < i ? a + r.angle : a, 0)) % 360;
// Le trait de rang i est-il dans un trait rotate ?
const _dansRotation = (rotations, i) => (rotations || []).some(r => r.i < i);
// Rotation CSS de ce qui est dans un trait rotate : son angle, plus celui de
// la vue, que le pion, droit à l'écran, annule par ailleurs.
const _rotCSS = a => `rotate(calc(${a}deg + var(--rotVue, 0deg)))`;

// Prend un segment ou un pion : un pion du registre passe par le plan de
// son TYPE (tsPion), sans recomposer son segment.
function lirePiece(seg){
  let pion = null;
  if(typeof seg !== 'string'){
    if(seg && seg.typeRef) pion = seg;
    else seg = seg ? seg.seg : '';
  }
  // Le segment entier d'un pion n'est recomposé qu'en repli.
  const S = () => pion ? pion.seg : seg;
  const ts = pion ? tsPion(pion) : tsSeg(seg);
  const etatBP = ts ? ts.eta[ts.eta.length - 1] : S().substring(S().lastIndexOf('\t') + 1);
  const sm = etatBP.match(/^([^;]*);(-?\d+);(-?\d+);/);
  let img = '', nom = '';
  // Le BasicPiece est le DERNIER trait du type ; une recherche dans tout
  // le segment pouvait tomber sur celui d'une définition imbriquée.
  const bp = ts && ts.typ[ts.typ.length - 1];
  const pm = (bp && /^piece;/.test(bp) ? bp + '/' : S()).match(/piece;[^;]*;[^;]*;([^;]*);((?:\\.|[^;\/\t\\])*)/);
  if(pm){ img = pm[1].replace(/\\/g,'').trim(); nom = pm[2].replace(/\\/g,'').trim(); }
  const couches = _parseLayers(ts);
  // BasicPiece sans image : le pion n'est fait que de ses couches, et sa
  // taille est celle de la plus grande (la roue des phases : base de
  // 120 px, marqueur de tour de 48 px).
  const faceCouche = !img;
  if(!img) img = _faceFromLayers(couches) || '';
  else {
    // Couche active de nationalité posée en (0, 0) : une autre face, qui
    // recouvre l'image de base (LMG malf : ge/geLMGb sur ge/geLMG).
    // imagesCouches ne la dessine pas en surimpression : sans cela, la
    // LMG restait intacte.
    const f = couches.find(c => c.actif && !c.dx && !c.dy && /^[a-z]{2}\//.test(_imgCouche(c) || ''));
    if(f) img = _imgCouche(f);
  }
  nom = _nomAvecLabels(ts, nom || img.split('/').pop());
  // Orientation : angle de chaque trait rotate (Covered Arc des canons et
  // véhicules, boussole…). Il s'applique à tout ce qui est à l'intérieur.
  const rotations = _rotations(ts);
  const rot = _angleInterieur(rotations, Infinity);
  // Dissimulation : l'état du trait conceal porte l'identifiant du joueur
  // qui a masqué la pièce, et son type l'image affichée à sa place.
  const c = _trait(ts, /^conceal;/);
  const masqueProprio = c ? c.e.split(';')[0].trim() : '';
  const masque = masqueProprio && masqueProprio !== 'null'
    ? ((c.t.split(';')[2] || '').trim() || '?') : null;
  const masqueNation = c ? (c.t.split(';')[3] || '').trim() : '';
  // HIP (trait Hideable « hide ») : la pièce n'est pas visible du tout.
  const h = _trait(ts, /^hide;/);
  const hipProprio = h ? h.e.split(';')[0].replace(/\\/g, '').trim() : '';
  const hip = !!(hipProprio && hipProprio !== 'null');
  // Propriétaire : la propriété Owner (prototype DBOwner), que VASL
  // remplit avec le nom du joueur qui pose ou déplace la pièce.
  const owner = (_proprietes(ts).Owner || '').trim();
  // Le « ? » lui-même (trait concealment;propriétaire;nation).
  const k = _trait(ts, /^concealment;/);
  const compteurProprio = k ? (k.t.split(';')[1] || '').replace(/\\/g, '').trim() : '';
  return {
    uid: pion ? pion.uid : (seg.match(/^\+\/([^\/]+)\//) || [])[1] || '',
    carte: sm ? sm[1] : null, x: sm ? +sm[2] : 0, y: sm ? +sm[3] : 0,
    img, nom: nom || img.split('/').pop(), nomBase: pm ? pm[2].replace(/\\/g,'').trim() : '', couches, faceCouche, rot, masque, hip, masqueNation,
    vt: _pieceVaslTools(ts), page: _pageHtml(ts), rotations, immob: _immobilise(ts),
    info: (() => { const t = ts && ts.typ.find(x => /^info;/.test(x));
                   return t ? t.substring(5).replace(/\\(.)/g, '$1') : null; })(),
    // Propriété « overlay » (minuscule) : seule visible en vue « carte
    // seule » de VASL — les overlays d'escalier (StairOverlayProperties).
    // Les overlays de terrain portent « Overlay », que VASL ne voit pas.
    overlay: !!ts && ts.typ.some(t => /^(mark|PROP);overlay(;|\\|$)/.test(t)),
    iMasque: ts ? ts.typ.findIndex(t => /^conceal;/.test(t)) : -1,
    masqueProprio: masque ? masqueProprio : null,
    hipProprio: hip ? hipProprio : null,
    compteurProprio: compteurProprio && compteurProprio !== 'null' ? compteurProprio : null,
    proprietaire: owner && owner !== 'null' && !/\$/.test(owner) ? owner : null,
    etiquettes: _etiquettes(ts), tourelle: _tourelle(ts), deplace: _deplace(ts)
  };
}

// ═══ VASL TOOLS — INFORMATIONS DE SCÉNARIO ═══
// L'extension VASL Tools (ASL Academy) laisse dans la sauvegarde des
// pièces à traits Java : la carte de scénario (« scenarioCardEdit; »),
// les panneaux de notes (« notePanel; ») et des outils — bande de
// véhicules, VP, dessins, images… VASSAL sans l'extension n'en dessine
// rien. On ne rend ici que l'information de scénario, en reprenant le
// gabarit HTML que l'extension fait elle-même dessiner par Swing
// (ScenarioCardButton.buildHtmlFor) ; les outils sont masqués. Les
// segments restent intacts : l'export les rend tels quels.
// Les logos de nationalité sont les marqueurs de tour du dépôt VASL.

// Code VASL Tools → [nom, couleur, style du texte, marqueur de tour VASL]
const VT_NATIONS = {
  alminor: ['Allied Minor Generic', '#82E3BD'], americans: ['Americans', '#CDF000', '', 'amTmkr'],
  americans82nd: ['82nd Airborne', '#CDF000', '', 'amTmkr'], anzac: ['ANZAC', '#E5CEA0', '', 'brTmrkr'],
  australians: ['Australians', '#E5CEA0', '', 'brTmrkr'], axminor: ['Axis Minor Generic', '#1DE256'],
  belgium: ['Belgians', '#82E3BD', '', 'beTmkr'], british: ['British', '#E5CEA0', '', 'brTmrkr'],
  bulgaria: ['Bulgarians', '#1DE256', '', 'buTmrkr'], canadians: ['Canadians', '#E5CEA0', '', 'brTmrkr'],
  commchina: ['Chinese Communist', '#D68D1A', '', 'chTmrkr'], chinagmd: ['Chinese GMD', '#D68D1A', '', 'chTmrkr'],
  commonwealth: ['Commonwealth', '#E5CEA0', '', 'brTmrkr'], croatia: ['Croatians', '#1DE256', '', 'crTmrkr'],
  denmark: ['Denmark', '#82E3BD', '', 'daTmkr'], eritrean: ['Eritreans', '#A6ADB2', '', 'itTmrkr'],
  ethiopians: ['Ethiopians', '#82E3BD', '', 'ethTmkr'], philippines: ['Filipinos', '#CDF000', '', 'amTmkr'],
  finland: ['Finns', '#CED3D3', '', 'fiTmrkr'], 'free-french': ['Free French', '#41A5FF', '', 'ffTmrkr'],
  french: ['French', '#41A5FF', '', 'frTmrkr'], 'french-vichy': ['French Vichy', '#41A5FF', '', 'vfTmrkr'],
  ss: ['German SS', '#333333', 'color:#ffffff;', 'ssTmkr'], germans: ['Germans', '#91CDF5', '', 'geTmkr'],
  greece: ['Greek', '#82E3BD', '', 'grTmkr'], hungary: ['Hungarians', '#1DE256', '', 'huTmrkr'],
  india: ['India', '#E5CEA0', '', 'brTmrkr'], italian: ['Italians', '#A6ADB2', '', 'itTmrkr'],
  japanese: ['Japanese', '#FFDB00', '', 'jaTmrkr'], netherlands: ['Netherlands', '#82E3BD', '', 'duTmkr'],
  newzealand: ['New Zealanders', '#E5CEA0', '', 'brTmrkr'], nk: ['North Korea', '#8CCDF5'],
  norway: ['Norwegians', '#82E3BD', '', 'noTmkr'], partisans: ['Partisans Generic', '#D68D1A', '', 'ruTmrkr'],
  poland: ['Polish', '#82E3BD', '', 'poTmkr'], romania: ['Romanians', '#1DE256', '', 'roTmrkr'],
  russian: ['Russians', '#D68D1A', '', 'ruTmrkr'], slovakia: ['Slovakians', '#1DE256', '', 'slTmrkr'],
  slovenia: ['Slovenians', '#1DE256'], southafrica: ['South Africans', '#E5CEA0', '', 'brTmrkr'],
  sk: ['South Korea', '#E5CEA0'], spanishnat: ['Spanish Nationalists', '#1DE256'],
  spanishrep: ['Spanish Republicans', '#D68D1A'], swedes: ['Swedes', '#8EA7DA', '', 'svTmrkr'],
  thailand: ['Thailand', '#1DE256', '', 'th1Tmrkr'], usmc: ['US Marine Corp', '#CDF000', '', 'amMarTmkr'],
  un: ['United Nations', '#CDF000', '', 'amTmkr'], yugoslavia: ['Yugoslavians', '#82E3BD', '', 'yuTmkr']
};
const _vtNation = c => VT_NATIONS[c] || VT_NATIONS.alminor;
const _vtLogo = c => { const n = VT_NATIONS[c]; return n && n[3] ? `MC/${n[3]}.png` : null; };

// Traits propres à l'extension : ceux qu'on dessine, et les outils.
const VT_INFO = /^(scenarioCardEdit|notePanel);/;
const VT_OUTILS = /^(vehicleStrip|noLvMarker|vpTracker|annotationCard|annotationArrow|imageBlock|lineDrawing|boxDrawing|circleDrawing|hexDrawing|hexSideDrawing|triangleDrawing|perimeterDrawing);/;

// Champs d'un état SequenceEncoder « ; » : les « \; » et « \/ » sont des
// séparateurs échappés, les « \ » finaux la marque d'imbrication.
function _vtChamps(etat){
  const s = (etat || '').replace(/\\+$/, '');
  const out = []; let cur = '';
  for(let i = 0; i < s.length; i++){
    if(s[i] === '\\' && i + 1 < s.length){ cur += s[++i]; continue; }
    if(s[i] === ';'){ out.push(cur); cur = ''; continue; }
    cur += s[i];
  }
  out.push(cur);
  return out;
}

// Pièce de l'extension : {genre: 'carte' | 'notes' | 'outil', champs}.
function _pieceVaslTools(ts){
  if(!ts) return null;
  for(let i = 0; i < ts.typ.length; i++){
    const m = ts.typ[i].match(VT_INFO);
    if(m) return {genre: m[1] === 'notePanel' ? 'notes' : 'carte', champs: _vtChamps(ts.eta[i])};
    if(VT_OUTILS.test(ts.typ[i])) return {genre: 'outil'};
  }
  // « Scenario Info Card » des versions récentes : plus de trait propre,
  // un Marker sans clé dont l'état porte « id;nom;date;lieu », et le
  // gpid « VASLTablesPiece » dans l'état du BasicPiece.
  const bp = ts.eta[ts.eta.length - 1] || '', nom = ts.typ[ts.typ.length - 1] || '';
  if(/^[^;]*;-?\d+;-?\d+;VASLTablesPiece;/.test(bp) || /^piece;[^;]*;[^;]*;[^;]*;Scenario Info Card$/.test(nom)){
    const i = ts.typ.findIndex(t => /^mark;\\*$/.test(t));
    if(i >= 0) return {genre: 'bandeau', champs: _vtChamps(ts.eta[i]).map(_vtUtf8)};
  }
  return null;
}

// Texte encodé deux fois en UTF-8 par l'extension (« VerdalsÃ¶ra ») :
// on défait l'encodage en trop tant qu'il redonne un UTF-8 valide.
function _vtUtf8(s){
  for(let n = 0; n < 3 && /[\u00c2-\u00f4][\u0080-\u00bf]/.test(s) && !/[^\u0000-\u00ff]/.test(s); n++){
    try { s = new TextDecoder('utf-8', {fatal: true}).decode(Uint8Array.from(s, c => c.charCodeAt(0))); }
    catch(e){ break; }
  }
  return s;
}

// Les champs viennent du fichier ou d'un autre joueur : échappés aussi
// pour un attribut (title="…").
const _vtEsc = s => String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

// Tours « 2,4,7 » ou « 2-5, 8 » (ScenarioCardButton.expandTurnSpec).
function _vtTours(spec){
  const t = new Set();
  for(const x of String(spec || '').split(',')){
    const m = x.trim().match(/^(\d+)\s*(?:-\s*(\d+))?$/);
    if(!m) continue;
    for(let n = +m[1]; n <= +(m[2] || m[1]); n++) t.add(n);
  }
  return t;
}

// État de ScenarioCardEditor (mySetState), dans l'ordre d'écriture.
function _vtCarte(c){
  const v = (i, d) => (c[i] !== undefined && c[i] !== '') ? c[i] : d;
  // Le code de nation entre dans des classes et la feuille de style :
  // seuls les codes connus passent, les autres valent « alminor ».
  const nat = (i, d) => { const x = v(i, d); return Object.prototype.hasOwnProperty.call(VT_NATIONS, x) ? x : 'alminor'; };
  return {
    id: v(0, ''), nom: v(1, ''), date: v(2, ''), lieu: v(3, ''), tours: v(4, '6'),
    toursLV: v(5, ''), toursNuit: v(6, ''),
    j: [
      {code: nat(7, 'germans'), nom: v(8, ''), elr: v(9, '1'), san: v(10, '1'), renforts: v(16, '')},
      {code: nat(11, 'americans'), nom: v(12, ''), elr: v(13, '1'), san: v(14, '1'), renforts: v(17, '')},
      {code: nat(50, 'russian'), nom: v(51, ''), elr: v(52, '1'), san: v(53, '1'), renforts: v(54, '')}
    ],
    vc: v(15, ''), police: v(18, 'Verdana'), disposition: v(20, 'horizontal'),
    nvr: v(36, '-'), joueurs: parseInt(v(49, '2'), 10) === 3 ? 3 : 2
  };
}

// Feuille de style de la carte (baseCss + nationCss), à l'échelle k.
function _vtCss(k, codes){
  const px = n => Math.max(1, Math.round(n * k)) + 'px';
  let css = `.vtc{color:#000;line-height:normal}.vtc table.main{border-collapse:separate;border-spacing:2px;border:0}`
    + `.vtc td{height:${px(60)};width:${px(60)};min-width:${px(60)};text-align:center;border:2px solid #666;`
    + `background-color:#fff;border-radius:${px(5)};padding:0;box-sizing:border-box;position:relative}`
    + `.vtc td.scenario{height:${px(180)};width:${px(180)};font-size:${px(22)};color:#fff;background-color:#000;`
    + `font-weight:600;padding:${px(5)};line-height:1.1em;vertical-align:middle}`
    + `.vtc td.phasewheel{width:${px(120)};height:${px(120)};color:#999;font-weight:bold;font-size:${px(12)}}`
    + `.vtc td.vc{width:${px(180)};max-width:${px(180)};font-size:${px(11)};text-align:left;font-family:arial;font-weight:normal;`
    + `background-color:#FCF3CF;vertical-align:top;padding:${px(5)};overflow:hidden}`
    // Le texte ne fixe pas la hauteur des lignes : il est coupé à la case,
    // comme fitVcText le fait (texte entier au survol).
    + `.vtc td.vc div{position:absolute;left:${px(5)};right:${px(5)};top:${px(5)};bottom:${px(5)};overflow:hidden}`
    + `.vtc td.vtrack,.vtc td.track{height:${px(20)};width:${px(20)};min-width:${px(20)};color:#fff;background-color:#333;font-size:${px(12)}}`
    + `.vtc td.token{font-weight:bold;font-size:${px(8)};color:#999}`
    + `.vtc td.turn,.vtc td.turnlv,.vtc td.turnnight,.vtc td.reinforce,.vtc td.endcell,.vtc td.half{font-weight:bold;font-size:${px(24)}}`
    + `.vtc td.turnlv{background-color:#c0c0c0}.vtc td.turnnight{color:#fff;background-color:#000}`
    + `.vtc td.players{height:${px(20)};background-color:#333;font-size:${px(12)};color:#fff;letter-spacing:.05em}`
    + `.vtc .vclabel{color:#f00;font-weight:bold}`
    + `.vtc td.filler{border:0;background:transparent}`
    + `.vtc .nvr{position:absolute;left:6%;bottom:6%;font:bold ${px(9)} Arial;color:#fff}`
    + `.vtc .coin{position:absolute;width:75%;height:75%;pointer-events:none}`
    + `.vtc .coin img{position:absolute;width:33%;height:33%}`;
  for(const code of codes){
    const [, coul, txt] = _vtNation(code);
    css += `.vtc td.n-${code}{background-color:${coul};height:${px(35)};width:${px(60)};font-weight:bold;`
      + `font-size:${px(12)};${txt || ''}}`;
  }
  return css;
}

// Coin de renfort d'une case de tour : dégradé de la couleur du camp et
// son marqueur de tour (rang 0 en haut à gauche, 1 en bas à droite, 2 en
// haut à droite).
function _vtCoin(code, rang, logos){
  const coul = _vtNation(code)[1];
  const pos = rang === 0 ? 'left:0;top:0' : rang === 2 ? 'right:0;top:0' : 'right:0;bottom:0';
  const dir = rang === 0 ? 'to bottom right' : rang === 2 ? 'to bottom left' : 'to top left';
  const clip = rang === 0 ? 'polygon(0 0,100% 0,0 100%)' : rang === 2 ? 'polygon(0 0,100% 0,100% 100%)' : 'polygon(100% 0,100% 100%,0 100%)';
  const u = logos.get(_vtLogo(code));
  const ipos = rang === 0 ? 'left:5%;top:5%' : rang === 2 ? 'right:5%;top:5%' : 'right:5%;bottom:5%';
  return `<span class="coin" style="${pos};clip-path:${clip};`
    + `background:linear-gradient(${dir},${coul} 0%,${coul}b3 33%,${coul}00 50%)">`
    + (u ? `<img src="${u}" style="${ipos}">` : '') + '</span>';
}

// Corps de la carte, d'après horizontalBody, verticalBody et leurs formes
// compactes. Le tableau à trois joueurs reprend la forme horizontale.
function _vtCorps(s, logos){
  // Le moteur HTML de Swing compte 1 px CSS pour 1,3 px à l'écran
  // (CSS.LengthUnit) : sans ce facteur, les compteurs que l'extension a
  // posés dans les cases tombaient à côté.
  const k = (s.joueurs === 3 ? 1 : 0.8833333333333333) * 1.3;
  const px = n => Math.max(1, Math.round(n * k)) + 'px';
  const j = s.j.slice(0, s.joueurs);
  const tours = parseFloat(String(s.tours).trim());
  const entiers = Math.floor(isNaN(tours) ? 6 : tours);
  const demi = (isNaN(tours) ? 6 : tours) - entiers >= 0.4999;
  const lv = _vtTours(s.toursLV), nuit = _vtTours(s.toursNuit);
  const nvr = /^[0-6]$/.test(String(s.nvr).trim()) ? +s.nvr : -1;
  const renf = j.map(x => new Set(String(x.renforts).split(',').map(t => t.trim()).filter(Boolean)));

  // Case d'un tour : couleur LV / nuit, coins de renfort par camp, NVR.
  const cases = [];
  const coin = (code, rang) => _vtCoin(code, rang, logos);
  for(let n = 1; n <= entiers; n++){
    const estLV = lv.has(n), estNuit = nuit.has(n);
    const fond = estNuit ? 'background-color:#000;color:#fff;' : estLV ? 'background-color:#c0c0c0;' : '';
    const coins = j.map((x, i) => renf[i].has(String(n)) ? coin(x.code, i) : '').join('');
    cases.push(`<td data-pt class="${coins ? 'reinforce' : estLV ? 'turnlv' : estNuit ? 'turnnight' : 'turn'}" style="${fond}">`
      + coins + n + (estNuit && nvr >= 0 ? `<span class="nvr">NVR: ${nvr}</span>` : '') + '</td>');
  }
  if(demi){
    const n = entiers + 1, estLV = lv.has(entiers), estNuit = nuit.has(entiers);
    const fond = estLV ? '#c0c0c0' : estNuit ? '#000' : '#fff';
    // Demi-tour : moitié inférieure droite en rouge (paintRedHalf).
    cases.push(`<td data-pt class="half" style="background:linear-gradient(to bottom right,${fond} 50%,#ff4444 50%);`
      + `${estNuit ? 'color:#fff;' : ''}">${n}`
      + (estNuit && nvr >= 0 ? `<span class="nvr">NVR: ${nvr}</span>` : '') + '</td>');
  }
  cases.push('<td data-pt class="endcell">END</td>');
  const compact = /^comp/.test(s.disposition) && s.joueurs !== 3;
  if(compact) cases.splice(9);

  const rs = (c, n) => n > 1 ? c.replace('<td ', `<td rowspan="${n}" `) : c;
  const rang = (a, n, r) => cases.slice(a, a + n).map(c => rs(c, r || 1)).join('');
  const titre = `<span style="color:#ffad2d;font-size:${px(24)}">${_vtEsc(s.id)}</span><br>`
    + `<span style="font-size:${px(20)}">${_vtEsc(s.nom)}</span><br>`
    + `<span style="color:#fff;font-size:${px(11)}">${_vtEsc(s.date)}<br>${_vtEsc(s.lieu)}</span>`;
  const vc = `<div><span class="vclabel">Victory Conditions:</span> ${_vtEsc(s.vc)}</div>`;
  const barre = x => `<td class="players" colspan="3">${_vtEsc(x.nom)}${x.nom ? ': ' : ''}${_vtEsc(_vtNation(x.code)[0])}</td>`;
  const infos = x => {
    const u = logos.get(_vtLogo(x.code));
    return `<td class="n-${x.code}" style="width:${px(60)}">`
      + (u ? `<img src="${u}" style="height:${px(32)};vertical-align:middle">` : '') + '</td>'
      + `<td class="n-${x.code}">ELR: ${_vtEsc(x.elr)}</td>`
      + `<td class="n-${x.code}" style="padding-left:2px;padding-right:4px">SAN: ${_vtEsc(x.san)}</td>`;
  };
  const scen = (span) => `<td valign="top" class="scenario" colspan="3"${span ? ` rowspan="${span}"` : ''}>${titre}</td>`;
  const vcc = (span) => `<td valign="top" class="vc" colspan="3"${span ? ` rowspan="${span}"` : ''} title="${_vtEsc(s.vc)}">${vc}</td>`;
  const roue = (span) => `<td data-pt class="phasewheel" data-case="PhaseWheel" colspan="2" rowspan="${span}">Phase<br>Wheel</td>`;
  const jetons = (noms) => noms.map(n => `<td data-pt class="token" data-case="${VT_ROLES[n] || ''}">${n}</td>`).join('');
  const piste = '<td class="track" rowspan="5">T<br>U<br>R<br>N<br><br>T<br>R<br>A<br>C<br>K</td>';
  const misc = `<td data-pt class="token">Wind Dir · Wind · Gusts · ECs · Weather · LV · NQ</td>`;

  let h = '<table class="main">';
  if(s.joueurs === 3 || !/^(vertical|comphorizontal|compvertical)$/.test(s.disposition)){
    const n = Math.ceil(cases.length / 3);
    h += '<tr>' + scen(5) + barre(j[0]) + vcc(4) + roue(4) + piste + rang(0, n, 2) + '</tr>'
      + '<tr>' + infos(j[0]) + '</tr>'
      + '<tr>' + barre(j[1]) + rang(n, n, 2) + '</tr>'
      + '<tr>' + infos(j[1]) + '</tr>'
      + '<tr>' + jetons(['Compass', 'Wind Dir', 'Wind', 'Gusts', 'ECs', 'Weather', 'LV', 'NQ'])
      + rang(n * 2, n) + '</tr>';
    if(j[2]) h += '<tr>' + barre(j[2]) + '</tr><tr>' + infos(j[2]) + '</tr>';
  } else if(s.disposition === 'comphorizontal'){
    h += '<tr>' + scen(5) + barre(j[0]) + vcc(4) + roue(4) + rs('<td data-pt class="token" data-case="Compass">Compass</td>', 2) + '</tr>'
      + '<tr>' + infos(j[0]) + '</tr>'
      + '<tr>' + barre(j[1]) + rs(misc, 2) + '</tr>'
      + '<tr>' + infos(j[1]) + '</tr>'
      + '<tr>' + cases.join('') + '<td class="filler"></td>'.repeat(Math.max(0, 9 - cases.length)) + '</tr>';
  } else {
    const comp = s.disposition === 'compvertical';
    h += '<tr>' + scen(0) + '</tr>'
      + '<tr>' + barre(j[0]) + '</tr><tr>' + infos(j[0]) + '</tr>'
      + '<tr>' + barre(j[1]) + '</tr><tr>' + infos(j[1]) + '</tr>'
      + '<tr>' + vcc(0) + '</tr>';
    if(comp){
      h += '<tr>' + roue(2) + '<td data-pt class="token" data-case="Compass">Compass</td></tr><tr>' + misc + '</tr>';
    } else {
      h += '<tr><td class="vtrack" colspan="3">STATUS</td></tr>'
        + '<tr>' + roue(2) + '<td data-pt class="token" data-case="Compass">Compass</td></tr>'
        + '<tr><td data-pt class="token" data-case="LV">LV</td></tr>'
        + '<tr>' + jetons(['NQ', 'Weather', 'Gusts']) + '</tr>'
        + '<tr>' + jetons(['ECs', 'Wind Dir', 'Wind']) + '</tr>'
        + '<tr><td class="vtrack" colspan="3">TURN TRACK</td></tr>';
    }
    for(let i = 0; i < cases.length; i += 3) h += '<tr>' + rang(i, 3) + '</tr>';
  }
  h += '</table>';
  return {html: h, css: _vtCss(k, [...new Set(j.map(x => x.code))])};
}

// Case de la carte → rôle des compteurs que l'extension y pose
// (« VASLTablesRole » de leur BasicPiece).
const VT_ROLES = {'Compass': 'Compass', 'Wind Dir': 'WindDir', 'Wind': 'WindForce', 'Gusts': 'Gusts',
                  'ECs': 'EC', 'Weather': 'Weather', 'LV': 'LV', 'NQ': 'NoQuarter'};

// Calage de la carte sur ses compteurs. L'extension dessine sa carte par
// le moteur HTML de Swing, dont les marges et arrondis ne se reproduisent
// pas au pixel près ; elle y a posé les compteurs au centre des cases.
// On décale donc la carte de l'écart médian entre chaque case et le
// compteur de même rôle — ce qui la recale aussi pour d'autres versions.
function _vtCaler(p, e){
  p.decX = p.decY = 0;
  const er = e.getBoundingClientRect();
  if(!er.width) return;
  const k = e.offsetWidth / er.width;
  const role = q => ((q.seg || '').match(/VASLTablesRole;([^;\\]+)/) || [])[1];
  const dx = [], dy = [];
  for(const td of e.querySelectorAll('[data-case]')){
    const q = vivants().find(x => x !== p && role(x) === td.dataset.case);
    if(!q) continue;
    const r = td.getBoundingClientRect();
    const cx = p.x - e.offsetWidth / 2 + (r.x + r.width / 2 - er.x) * k;
    const cy = p.y - e.offsetHeight / 2 + (r.y + r.height / 2 - er.y) * k;
    dx.push(q.x - cx); dy.push(q.y - cy);
  }
  if(dx.length < 2) return;
  const med = a => a.sort((u, v) => u - v)[a.length >> 1];
  const mx = med(dx), my = med(dy);
  // Au-delà d'une case d'écart, les compteurs ont été déplacés à la main :
  // on garde la carte centrée sur sa position, comme VASSAL.
  if(Math.abs(mx) > 150 || Math.abs(my) > 150) return;
  p.decX = mx; p.decY = my;
}

// Cases à pions d'une carte ou d'un bandeau (td[data-pt], ancrage
// invisible) : rectangles en
// pixels natifs, comptés depuis le centre du panneau. Un pion lâché dans
// une case se pose en son centre (positionDepot). Les offsets ne dépendent
// ni du zoom ni de la rotation de la vue.
function _vtCases(p, e){
  p.cases = [];
  for(const td of e.querySelectorAll('td[data-pt]')){
    let x = 0, y = 0, n = td;
    while(n && n !== e){ x += n.offsetLeft; y += n.offsetTop; n = n.offsetParent; }
    if(n !== e) continue;
    p.cases.push({x: x - e.offsetWidth / 2, y: y - e.offsetHeight / 2, w: td.offsetWidth, h: td.offsetHeight});
  }
}

// Centre de la case de panneau qui contient le point (pixels natifs), ou
// null. Les panneaux affichés seuls comptent ; le dernier posé gagne.
function caseDePanneau(x, y){
  let out = null;
  for(const p of vivants()){
    if(!p.cases || p.cache) continue;
    const ox = p.x + (p.decX || 0), oy = p.y + (p.decY || 0);
    for(const c of p.cases){
      if(x < ox + c.x || x > ox + c.x + c.w || y < oy + c.y || y > oy + c.y + c.h) continue;
      out = {x: Math.round(ox + c.x + c.w / 2), y: Math.round(oy + c.y + c.h / 2)};
      // Un pion au centre exact du panneau entrerait dans sa pile.
      if(out.x === p.x && out.y === p.y) out.x++;
    }
  }
  return out;
}

// Élément de la carte de scénario, prêt à poser sur le pion.
async function _vtElementCarte(champs){
  const s = _vtCarte(champs);
  const logos = new Map();
  for(const x of s.j.slice(0, s.joueurs)){
    const im = _vtLogo(x.code);
    if(im && !logos.has(im)) logos.set(im, await chargerImage(im));
  }
  const {html, css} = _vtCorps(s, logos);
  const e = document.createElement('div');
  e.className = 'vtc';
  e.style.fontFamily = `${s.police}, Verdana, sans-serif`;
  e.innerHTML = `<style>${css}</style>${html}`;
  return e;
}

// Panneau de notes (NotePanel.draw) : bandeau de la couleur du camp,
// corps teinté à mi-chemin du blanc, texte enroulé au fil de la largeur.
// Barre de la « Scenario Info Card » : une rangée de cases comme celles
// de la carte de scénario (60 px CSS, 1,3 px à l'écran sous Swing) — la
// boussole, la direction du vent, puis l'identifiant et le nom du
// scénario. Les compteurs que l'extension y a posés servent au calage.
function _vtElementBandeau(c){
  const [id, nom, date, lieu] = c;
  const k = 1.3, px = n => Math.round(n * k) + 'px';
  const e = document.createElement('div');
  e.className = 'vtc';
  e.title = [id, nom, date, lieu].filter(Boolean).join(' \u2014 ');
  const td = `height:${px(60)};width:${px(60)};min-width:${px(60)};border:2px solid #666;border-radius:${px(5)};`
    + `box-sizing:border-box;text-align:center;vertical-align:middle;padding:0;`;
  const jeton = (nom, role) => `<td data-pt data-case="${role}" style="${td}background:#fff;color:#999;font:bold ${px(8)} Arial">${nom}</td>`;
  e.innerHTML = `<table style="border-collapse:separate;border-spacing:2px;border:0;background:#fff"><tr>`
    + jeton('Compass', 'Compass') + jeton('Wind Dir', 'WindDir')
    + `<td style="${td}width:auto;background:#000;color:#fff;font:600 ${px(14)} Verdana,Arial,sans-serif;`
    + `padding:0 ${px(8)};white-space:nowrap;line-height:1.2">`
    + `<span style="color:#ffad2d">${_vtEsc(id)}</span> ${_vtEsc(nom)}`
    + `<br><span style="font-size:${px(10)};font-weight:normal">${_vtEsc([date, lieu].filter(Boolean).join(' \u00b7 '))}</span>`
    + '</td></tr></table>';
  return e;
}

function _vtElementNotes(c){
  const titre = c[0] || '', corps = c[1] || '';
  const coul = /^#[0-9a-f]{6}$/i.test(c[2] || '') ? c[2] : '#333333';
  const larg = Math.max(126, parseInt(c[5], 10) || 200);
  const haut = Math.max(70, parseInt(c[6], 10) || 200);
  const fond = /^#[0-9a-f]{6}$/i.test(c[8] || '') ? c[8]
    : '#' + [1, 3, 5].map(i => Math.round(parseInt(coul.substr(i, 2), 16) / 2 + 127.5).toString(16).padStart(2, '0')).join('');
  const taille = parseInt(c[9], 10) || 14;
  const e = document.createElement('div');
  e.className = 'vtn';
  e.style.cssText = `width:${larg}px;min-height:${haut}px;background:${fond};border:2px solid #666;`
    + 'border-radius:12px;overflow:hidden;box-sizing:border-box;font-family:Dialog,Arial,sans-serif';
  const h = document.createElement('div');
  h.style.cssText = `height:24px;line-height:24px;background:${coul};border-bottom:1px solid #666;`
    + `padding:0 6px;font-weight:bold;font-size:15px;white-space:nowrap;overflow:hidden;`
    + `color:${c[3] === 'ss' ? '#fff' : '#000'}`;
  h.textContent = titre;
  const b = document.createElement('div');
  b.style.cssText = `padding:6px;font-size:${taille}px;line-height:1.2;color:#000;white-space:pre-wrap;word-wrap:break-word`;
  b.textContent = corps;
  e.append(h, b);
  return e;
}

// ═══ PAGE HTML D'UN LABEL — VASL INFORMATION TABLES ═══
// Le gabarit d'asl-players.net (« VASL INFORMATION TABLES ») se colle
// dans le label d'un « HTML Placeholder » : VASSAL dessine la page par le
// moteur HTML de Swing, centrée sur la pièce. On la rend de même, à
// l'échelle de la carte et sous les pions posés dans ses cases.
//
// Texte d'un label qui est une page entière, et non un libellé enrichi
// (« <html><font color=red>… » des munitions).
function _estPageHtml(h){
  return !!h && h.length > 400 && /<(table|style|body)\b/i.test(h);
}
// Le label garde les tabulations du gabarit, échappées à chaque niveau
// d'imbrication : « \\\t » n'est qu'un blanc.
const _netPageHtml = h => h.replace(/\\*\t/g, ' ');

// Page portée par un label du pion, s'il y en a une.
function _pageHtml(ts){
  if(!ts) return null;
  for(let i = 0; i < ts.typ.length; i++){
    if(!/^label;/.test(ts.typ[i])) continue;
    const t = _texteLabel(ts.eta[i]);
    if(/^<html/i.test(t) && _estPageHtml(t)) return _netPageHtml(t);
  }
  return null;
}

// Balises et attributs repris de la page : la mise en forme d'un tableau,
// rien qui s'exécute ni ne charge (pas d'image, de lien ni de script).
const PAGE_BALISES = new Set(['table', 'thead', 'tbody', 'tfoot', 'tr', 'td', 'th', 'caption', 'col', 'colgroup',
  'div', 'span', 'p', 'br', 'hr', 'b', 'i', 'u', 's', 'strike', 'em', 'strong', 'font', 'center', 'small', 'big',
  'sub', 'sup', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'ul', 'ol', 'li']);
const PAGE_ATTRIBUTS = new Set(['class', 'id', 'style', 'colspan', 'rowspan', 'valign', 'align', 'width', 'height',
  'bgcolor', 'color', 'size', 'face', 'border', 'cellpadding', 'cellspacing']);
// Swing compte 1 px CSS pour 1,3 px à l'écran (CSS.LengthUnit).
const SWING_PX = 1.3;

// Élément de la page : un hôte dont l'ombre (Shadow DOM) cloisonne la
// feuille de style du gabarit — ses règles « td » ne débordent pas sur
// l'interface. Les logos de nationalité, que le gabarit va chercher sur
// asl-players.net, sont remplacés par les marqueurs de tour du dépôt
// VASL, comme sur la carte de VASL Tools.
async function _vtElementPage(html){
  const doc = new DOMParser().parseFromString(html, 'text/html');
  const e = document.createElement('div');
  e.className = 'vtp';
  const ombre = e.attachShadow({mode: 'open'});
  const css = [...doc.querySelectorAll('style')].map(s => s.textContent).join('\n');
  const copier = n => {
    if(n.nodeType === 3) return document.createTextNode(n.nodeValue);
    if(n.nodeType !== 1) return null;
    const tag = n.tagName.toLowerCase();
    const c = PAGE_BALISES.has(tag) ? document.createElement(tag) : document.createDocumentFragment();
    if(c.nodeType === 1)
      for(const a of n.attributes) if(PAGE_ATTRIBUTS.has(a.name.toLowerCase())) c.setAttribute(a.name, a.value);
    for(const x of n.childNodes){ const y = copier(x); if(y) c.appendChild(y); }
    return c;
  };
  const corps = document.createElement('div');
  corps.className = 'page';
  corps.setAttribute('style', doc.body.getAttribute('style') || '');
  for(const x of doc.body.childNodes){ const y = copier(x); if(y) corps.appendChild(y); }

  // Code de nation d'une classe du gabarit (« americanslogo »).
  const nation = c => Object.prototype.hasOwnProperty.call(VT_NATIONS, c) ? c : null;
  const logos = new Map();
  const logo = async code => {
    const im = _vtLogo(code);
    if(im && !logos.has(im)) logos.set(im, await chargerImage(im));
    return logos.get(im) || null;
  };
  // Cases de logo : le marqueur de tour, à la hauteur de la case.
  for(const td of corps.querySelectorAll('td')){
    const code = [...td.classList].map(c => /logo$/.test(c) ? nation(c.slice(0, -4)) : null).find(Boolean);
    if(!code) continue;
    const u = await logo(code);
    if(!u) continue;
    td.style.backgroundImage = 'none';
    td.replaceChildren(Object.assign(document.createElement('img'), {src: u, className: 'logo'}));
  }
  // Cases de renfort : « reinforce/americans-germans.png » nomme les deux
  // camps ; on dessine leurs coins comme VASL Tools. Les codes peuvent
  // contenir un tiret (« free-french ») : on essaie chaque coupure.
  for(const td of corps.querySelectorAll('td.reinforce')){
    const m = (td.getAttribute('style') || '').match(/reinforce\/([a-z0-9-]+)\.png/i);
    if(!m) continue;
    const t = m[1].toLowerCase().split('-');
    let codes = null;
    for(let i = 1; i < t.length && !codes; i++){
      const a = nation(t.slice(0, i).join('-')), b = nation(t.slice(i).join('-'));
      if(a && b) codes = [a, b];
    }
    if(!codes) continue;
    for(const c of codes) await logo(c);
    td.style.backgroundImage = 'none';
    td.insertAdjacentHTML('afterbegin', codes.map((c, i) => _vtCoin(c, i, logos)).join(''));
  }

  const style = document.createElement('style');
  // La feuille du gabarit d'abord ; ensuite ce que le moteur de Swing
  // fait sans qu'on le lui dise : pas de marge, et des cases de logo qui
  // contiennent leur image au lieu de s'étirer pour elle.
  style.textContent = css
    + '\n:host{display:block;color:#000;background:#fff;line-height:normal}'
    + '.page{display:inline-block;font-family:Arial,sans-serif}'
    + 'td{position:relative;box-sizing:content-box}'
    + 'td img.logo{display:block;margin:auto;height:32px;width:auto;max-width:100%}'
    + '.coin{position:absolute;width:75%;height:75%;pointer-events:none}'
    + '.coin img{position:absolute;width:33%;height:33%}';
  ombre.append(style, corps);
  return e;
}

// ── Images des pions, tirées du dépôt VASL.
// Les SVG VASL déclarent width/height sans viewBox : sans lui ils ne se
// mettent pas à l'échelle. Leur silhouette, en lien relatif, est
// incorporée ; leurs polices Nimbus remplacées par une pile système.
const VASL_IMAGES_URL =
  'https://raw.githubusercontent.com/vasl-developers/vasl/develop/dist/images/';
const _imgCache = new Map();      // chemin → Promise<URL de Blob | null>
// Exposant Smoke (A24.1) imprimé sur l'image d'une escouade : VASL ne le
// porte nulle part ailleurs que dans le SVG (<text id="SmokeExponent">).
// chemin → exposant (« 2 », « 3 »), '' si l'image n'en a pas.
const _exposantsSmoke = new Map();

function _ensureViewBox(svg){
  const m = svg.match(/<svg\b[^>]*>/i);
  if(!m || /\bviewBox\s*=/i.test(m[0])) return svg;
  const w = (m[0].match(/\bwidth\s*=\s*"([\d.]+)/i) || [])[1];
  const hh = (m[0].match(/\bheight\s*=\s*"([\d.]+)/i) || [])[1];
  if(!w || !hh) return svg;
  return svg.replace(m[0], m[0].replace(/<svg\b/i, `<svg viewBox="0 0 ${w} ${hh}"`));
}

function chargerImage(path){
  if(!path) return Promise.resolve(null);
  if(!_imgCache.has(path)) _imgCache.set(path, _chargerImage(path));
  return _imgCache.get(path);
}

async function _chargerImage(path){
  const demande = path;
  try {
    // Le module désigne bien des images sans extension (« MS/Gutted »,
    // « ML/NoMove ») et certaines n'existent qu'en SVG, d'autres qu'en GIF :
    // on essaie le chemin donné, puis les autres formats.
    // Une URL complète (icône des ressources de VASSAL) est prise telle quelle.
    const url = /^https?:/.test(path);
    const base = path.replace(/\.(svg|gif|png)$/i, '');
    const essais = url ? [path] : [...new Set([path, base + '.svg', base + '.gif', base + '.png'])];
    let r = null;
    for(const e of essais){
      r = await fetch(url ? e : VASL_IMAGES_URL + e);
      if(r.ok){ path = e; break; }
    }
    if(!r.ok){
      const ovr = await _imageArchiveOverlay(path);
      if(ovr) return ovr;
      throw new Error('HTTP ' + r.status);
    }
    if(!/\.svg$/i.test(path)){
      const mime = /\.gif$/i.test(path) ? 'image/gif' : /\.png$/i.test(path) ? 'image/png' : 'image/jpeg';
      return URL.createObjectURL(new Blob([await r.arrayBuffer()], {type: mime}));
    }
    let svg = await r.text();
    const se = svg.match(/<text\b[^>]*\bid="SmokeExponent"[^>]*>\s*([^<]*)/);
    _exposantsSmoke.set(demande, se ? se[1].trim() : '');
    _exposantsSmoke.set(path, se ? se[1].trim() : '');
    svg = svg.replace(/@font-face\s*\{[^}]*\}/g, '')
             .replace(/font-family\s*:\s*Nimbus Sans L/g, 'font-family:Helvetica,Arial,sans-serif');
    // Images incluses, relatives au dossier du pion : la silhouette
    // (« svg/ge468Ss.png ») et l'insigne de classe en haut à droite
    // (« ./svg/class_ss.svg » : runes SS, élite, 1re ligne…). Dans un
    // blob, un chemin relatif ne mène nulle part : chacune est intégrée.
    const dir = path.substring(0, path.lastIndexOf('/') + 1);
    const refs = new Map();
    for(const m of svg.matchAll(/href="((?:\.\/)?(svg\/[^"]+\.(?:png|svg|gif)))"/gi)) refs.set(m[1], m[2]);
    for(const [ref, rel] of refs){
      const ext = rel.split('.').pop().toLowerCase();
      const mime = ext === 'svg' ? 'image/svg+xml' : ext === 'gif' ? 'image/gif' : 'image/png';
      try {
        const pr = await fetch(VASL_IMAGES_URL + dir + rel);
        if(!pr.ok) throw 0;
        const buf = new Uint8Array(await pr.arrayBuffer());
        let bin = '';
        for(let i = 0; i < buf.length; i++) bin += String.fromCharCode(buf[i]);
        svg = svg.split('"' + ref + '"').join('"data:' + mime + ';base64,' + btoa(bin) + '"');
      } catch(e){
        svg = svg.replace(new RegExp('<image[^>]*href="' + ref.replace(/[.*+?^${}()|[\]\\\/]/g, '\\$&') + '"[^>]*\\/?>', 'g'), '');
      }
    }
    return URL.createObjectURL(new Blob([_ensureViewBox(svg)], {type: 'image/svg+xml'}));
  } catch(e){
    return null;
  }
}

// ═══ OVERLAYS DE L'EXTENSION « VASL OVERLAYS » ═══
// Une pièce de l'extension nomme l'image d'un overlay d'hexagones
// (« ovrst3c.gif » : overlay st3, orientation c). La page ne la dessine
// pas comme un pion : elle pose à sa place l'overlay du dépôt VASL
// (boards/bdFiles/overlays), comme s'il avait été posé par « Add
// overlays », dans l'image du board et dans la LOS. La pièce reste dans
// la partie, cachée et jamais sélectionnable : son segment repart tel quel
// dans la sauvegarde, et VASL avec l'extension la dessine comme avant.
//
// TABLE_OVR_EXT_TEXTE : js/donnees/overlays-ext.js
let _tableOvrExt = null;
function tableOvrExt(){
  if(!_tableOvrExt){
    _tableOvrExt = new Map();
    for(const e of TABLE_OVR_EXT_TEXTE.split(' ')){
      const m = /^([a-z0-9]+):([a-f])(-?\d+),(-?\d+),(\d+),(\d+)(?:,([a-f])(-?\d+),(-?\d+))?$/.exec(e);
      if(m) _tableOvrExt.set(m[1], {c: m[2], dx: +m[3], dy: +m[4], w: +m[5], h: +m[6],
                                    r: m[7] ? {c: m[7], dx: +m[8], dy: +m[9]} : null});
    }
  }
  return _tableOvrExt;
}
const _OVR_EXT = /^ovr([a-z0-9]+)([a-f])(-s)?\.(gif|png)$/i;
const _LETTRES_OVR = 'abcdef';

// Overlay du dépôt pour l'image d'une pièce : {nom, c (orientation de
// l'archive), a (archive), octets, cadre}, ou null. Sur un board retourné,
// la pièce reste droite alors que l'overlay tourne avec le board : c'est
// le cadre de l'image tournée de 180° qu'il faut poser. La variante
// enneigée de l'extension (« ovrst3c-s.png ») a le même cadre que l'image
// ordinaire, qui la remplace.
async function _overlayDuDepot(img, retourne){
  const m = _OVR_EXT.exec((img || '').split('/').pop());
  if(!m) return null;
  const nom = m[1].toLowerCase();
  let l = m[2].toLowerCase();
  const e = tableOvrExt().get(nom + l);
  let cadre = null;
  if(e){
    const r = retourne ? e.r : e;
    if(!r) return null;
    cadre = {c: r.c, dx: r.dx, dy: r.dy, w: e.w, h: e.h, retourne};
  } else if(retourne) l = _LETTRES_OVR[(_LETTRES_OVR.indexOf(l) + 3) % 6];
  const a = await chargerArchiveOverlay(archiveOverlay(nom));
  // Hors de la table (image seule de la pièce, sans pose) : deux crans plus
  // loin quand l'archive a les six orientations, l'image recadrée sur ses
  // pixels opaques.
  const six = [..._LETTRES_OVR].every(x => _fichier(a.files, imageOverlay(nom + x)));
  const c = cadre ? cadre.c : six ? _LETTRES_OVR[(_LETTRES_OVR.indexOf(l) + 2) % 6] : l;
  const octets = _fichier(a.files, imageOverlay(nom + c));
  return octets ? {nom, c, a, octets, cadre} : null;
}

// Image de la pièce tirée de l'archive : {canvas, ax, ay}, où (ax, ay) est,
// dans l'image de l'archive, le point posé sur la pièce : VASSAL dessine
// l'image à partir de floor(w/2) à sa gauche ; dans le cadre tourné de
// 180°, ce point est à w − floor(w/2) du coin (de même en hauteur). Avec
// la table, c'est le cadre de l'image de l'extension ; sans elle, l'image
// recadrée sur ses pixels opaques.
const _ovrRecadres = new Map();         // archive/orientation → Promise
function _ovrRecadre(o){
  const k = archiveOverlay(o.nom) + '/' + o.nom + o.c + (o.cadre ? ':' + [o.cadre.dx, o.cadre.dy, o.cadre.w, o.cadre.h, o.cadre.retourne] : '');
  if(!_ovrRecadres.has(k)) _ovrRecadres.set(k, (async () => {
    const bmp = await createImageBitmap(new Blob([o.octets], {type: 'image/gif'}));
    let x0, y0, w, h;
    if(o.cadre) ({dx: x0, dy: y0, w, h} = o.cadre);
    else {
      const cv = document.createElement('canvas');
      cv.width = bmp.width; cv.height = bmp.height;
      const g = cv.getContext('2d', {willReadFrequently: true});
      g.drawImage(bmp, 0, 0);
      const px = g.getImageData(0, 0, cv.width, cv.height).data;
      let x1 = -1, y1 = -1;
      x0 = cv.width; y0 = cv.height;
      for(let y = 0; y < cv.height; y++) for(let x = 0; x < cv.width; x++)
        if(px[(y * cv.width + x) * 4 + 3]){ if(x < x0) x0 = x; if(x > x1) x1 = x; if(y < y0) y0 = y; if(y > y1) y1 = y; }
      if(x1 < 0){ bmp.close(); return null; }
      w = x1 - x0 + 1; h = y1 - y0 + 1;
    }
    const out = document.createElement('canvas');
    out.width = w; out.height = h;
    out.getContext('2d').drawImage(bmp, -x0, -y0);
    bmp.close();
    const t = o.cadre && o.cadre.retourne;
    return {canvas: out, ax: x0 + (t ? w - Math.floor(w / 2) : Math.floor(w / 2)),
                         ay: y0 + (t ? h - Math.floor(h / 2) : Math.floor(h / 2))};
  })());
  return _ovrRecadres.get(k);
}

// Image de la pièce, quand elle n'est pas posée dans un board (hors des
// boards, ou overlay introuvable) : l'image de l'archive, recadrée.
async function _imageArchiveOverlay(path){
  try {
    const o = await _overlayDuDepot(path, false);
    const r = o && await _ovrRecadre(o);
    if(!r) return null;
    const blob = await new Promise(f => r.canvas.toBlob(f, 'image/png'));
    return blob ? URL.createObjectURL(blob) : null;
  } catch(e){
    return null;
  }
}

// Pose « Add overlays » d'une pièce de l'extension : {b, nom, hex1, hex2,
// ecart}. Le point (ax, ay) de l'image de l'archive tombe sur la pièce ;
// posé par VASL, l'overlay a son coin en centre(hex1) − centre(origine).
// Le hex1 retenu est celui qui s'en approche le plus : avec la table,
// l'écart n'est que d'un arrondi ; sans elle, l'overlay retombe ainsi sur
// la grille, comme s'il avait été posé par VASL.
async function _poseOverlayExt(p, img){
  const X = p.x - BOARD_MARGIN, Y = p.y - BOARD_MARGIN;
  const b = (_losBoards || []).find(b => b._cv && X >= b.px && X < b.px + b.dispW && Y >= b.py && Y < b.py + b.dispH);
  if(!b) throw new Error('hors des boards');
  const g = geoBoard(b), z = b.zoom || 1;
  let lx = X - b.px, ly = Y - b.py;
  if(b.reversed){ lx = b.dispW - lx; ly = b.dispH - ly; }
  const P = {x: lx / z + (b.crop ? b.crop.x || 0 : 0), y: ly / z + (b.crop ? b.crop.y || 0 : 0)};
  const o = await _overlayDuDepot(img, b.reversed);
  if(!o) throw new Error(img + (b.reversed ? ' sans correspondance sur un board retourné' : ' sans overlay dans le dépôt VASL'));
  // Hors de la table, la règle des deux crans n'est pas sûre : pas de pose.
  if(!o.cadre && tableOvrExt().size) throw new Error(img + ' absent de la table de correspondance (overlay manquant)');
  const r = await _ovrRecadre(o);
  const d = _lireDataOverlay(o.a.data, o.nom);
  const unHex = !!_fichier(o.a.files, imageOverlay(o.nom + 'a')) && !_fichier(o.a.files, imageOverlay(o.nom + 'd'));
  const c = unHex ? 'a' : o.c;
  const nomOrigine = _origine(d.origines, c, b.name), po = nomOrigine && centreHex(g, nomOrigine);
  if(!po) throw new Error(`orientation « ${c} » sans origine`);
  const T = {x: P.x - r.ax + po.x, y: P.y - r.ay + po.y};
  let h1 = null;
  for(let i = 0; i <= g.cols + 1; i++) for(let k = -1; k <= g.rows + 2; k++){
    const q = centreHex(g, hexrow(i) + k), e = Math.hypot(q.x - T.x, q.y - T.y);
    if(!h1 || e < h1.e) h1 = {nom: hexrow(i) + k, i, k, e, q};
  }
  // Plus d'un quart d'hexagone : aucun hex1 du board ne convient (grand
  // overlay débordant du bord, comme un océan) ; « Add overlays » ne le
  // pourrait pas non plus.
  if(h1.e > 16) throw new Error(`hors de la grille du board (${Math.round(h1.e)} px)`);
  let hex2 = h1.nom;
  if(!unHex){
    // Voisin de hex1 dans la direction de l'orientation (_orientation).
    hex2 = null;
    for(let i = Math.max(0, h1.i - 1); i <= h1.i + 1 && !hex2; i++) for(let k = h1.k - 1; k <= h1.k + 1; k++){
      const n = hexrow(i) + k, q = centreHex(g, n);
      if(n === h1.nom || Math.abs(q.x - h1.q.x) > 1.1 * g.hw || Math.abs(q.y - h1.q.y) > 1.1 * g.hh) continue;
      if(_orientation(g, h1.nom, n) === c){ hex2 = n; break; }
    }
    if(!hex2) throw new Error('pas de voisin de ' + h1.nom + ' en orientation ' + c);
  }
  return {b, nom: o.nom, hex1: h1.nom, hex2, ecart: Math.round(h1.e)};
}

// Remet les overlays de l'extension dans les boards quand une pièce
// apparaît, bouge, change d'orientation ou disparaît. Seuls les boards
// dont la liste change sont recomposés, puis la LOS est reconstruite.
let _ovrExtMinuteur = null, _ovrExtEnCours = false, _ovrExtRelance = false;
let _ovrExtSig = null, _ovrExtBoards = null;
function planifierOverlaysExt(){
  clearTimeout(_ovrExtMinuteur);
  _ovrExtMinuteur = setTimeout(majOverlaysExt, 60);
}
async function majOverlaysExt(){
  if(_ovrExtEnCours){ _ovrExtRelance = true; return; }
  // Seulement une fois tous les boards dessinés (_cv posé, null si
  // échec) : pendant le chargement d'une partie, les pions sont encore
  // ceux de la précédente.
  const boards = _losBoards;
  if(!boards || !boards.every(b => b._cv !== undefined)) return;
  const exts = vivants().filter(p => p.affichageSeul).map(p => ({p, img: lirePiece(p).img}));
  const sig = exts.map(e => `${e.p.uid}:${e.p.x},${e.p.y}:${e.img}`).join('|');
  if(sig === _ovrExtSig && boards === _ovrExtBoards) return;
  _ovrExtEnCours = true;
  try {
    const parBoard = new Map(boards.map(b => [b, []]));
    for(const {p, img} of exts){
      try {
        const o = await _poseOverlayExt(p, img);
        parBoard.get(o.b).push({nom: o.nom, hex1: o.hex1, hex2: o.hex2, piece: p.nom, uid: p.uid});
        p.ovrExtEcart = o.ecart;
      } catch(e){
        if(p.ovrExtPose !== false) chatLog(`⚠️ ${p.nom} (extension VASL Overlays) : ${e.message} — pièce affichée seule`);
        p.ovrExtPose = false;
      }
    }
    let change = false;
    for(const [b, l] of parBoard){
      const s = JSON.stringify(l.map(o => [o.nom, o.hex1, o.hex2]));
      if(s === (b._ovrExtSig || '[]')) continue;
      b.overlaysExt = l;
      b._ovrExtSig = s;
      if(b._cv) await _peindreBoard(b);
      change = true;
    }
    const poses = new Set([...parBoard.values()].flat().map(o => o.uid));
    for(const {p} of exts){
      if(poses.has(p.uid)) p.ovrExtPose = true;
      p.el.style.display = p.ovrExtPose || p.cache ? 'none' : '';
    }
    if(change){
      // Les voisins reprennent les débords des overlays recomposés.
      for(const b of boards) if(b._cv && b._comp) _dessinerBoard(b);
      oublierCarteLOS(boards);
    }
    _ovrExtSig = sig; _ovrExtBoards = boards;
  } finally {
    _ovrExtEnCours = false;
    if(_ovrExtRelance){ _ovrExtRelance = false; planifierOverlaysExt(); }
  }
}

