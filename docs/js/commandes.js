// ═══════════════════════════════════════════════════════════════
// TRAITS VASSAL : DÉCODAGE, ENCODAGE, PROTOTYPES
// Le contenu d'un trait n° i porte i contre-obliques de trop devant
// chacune de ses tabulations (une par niveau d'emboîtement) : on les
// retire au décodage et on les remet à l'encodage, ce qui permet de
// déplacer un trait d'un rang à l'autre — c'est ce que fait le
// développement des prototypes.
// ═══════════════════════════════════════════════════════════════
function decoderTraits(s){
  return _traits(s).map((c, i) => i ? c.replace(new RegExp('\\\\{' + i + '}\\t', 'g'), '\t') : c);
}
function encoderTraits(l){
  return l.map((c, j) => {
    const b = '\\'.repeat(j);
    return c.replace(/\t/g, b + '\t') + (j < l.length - 1 ? b + '\t' : '');
  }).join('');
}

// Comme PieceCloner de VASSAL : chaque « prototype;Nom » est remplacé par
// les traits du prototype (sans son BasicPiece), avec leur état par
// défaut. Une pièce posée ainsi porte tous ses traits, et l'état de
// chacun peut changer et s'enregistrer.
function developperDefinition(cmd, prof){
  prof = prof || 0;
  const te = _typeEtEtat(cmd);
  if(!te) return null;
  const T = decoderTraits(te.type), S = decoderTraits(te.etat);
  if(T.length !== S.length) return null;
  const T2 = [], S2 = [];
  for(let i = 0; i < T.length; i++){
    const m = T[i].match(/^prototype;((?:\\.|[^;])*)/);
    if(m && prof < 8 && _bdd){
      const def = _bdd.protos.get(m[1].replace(/\\(.)/g, '$1'));
      const d = def && developperDefinition(def, prof + 1);
      if(d){ T2.push(...d.T.slice(0, -1)); S2.push(...d.S.slice(0, -1)); continue; }
    }
    T2.push(T[i]); S2.push(S[i]);
  }
  return {T: T2, S: S2};
}

// ═══════════════════════════════════════════════════════════════
// COMMANDES DES PIONS — le clic droit de VASL
// Comme dans VASSAL, le menu d'un pion est la liste des commandes que
// déclarent ses traits ; une commande envoie une touche, qui traverse
// tous les traits du plus externe au plus interne, et chacun réagit s'il
// la reconnaît. Les macros (Trigger Action) en déclenchent d'autres.
// Rien n'est écrit à la main par type de pion : tout vient de la base.
// ═══════════════════════════════════════════════════════════════

// ── Touches. VASSAL les note « code,modificateurs » (KeyStroke Java),
// ou par leur nom pour les touches nommées (« 57355,0,hc »), ou encore,
// dans les traits anciens, par un caractère et des modificateurs.
// _touche : js/outils.js
function lireTouche(v){
  if(!v) return null;
  const f = v.replace(/\\/g, '').split(',');
  if(f[2]) return 'n:' + f.slice(2).join(',');
  const code = parseInt(f[0], 10);
  return code ? _touche(code, parseInt(f[1], 10) || 0) : null;
}
function toucheAncienne(car, mods){
  const c = (car || '').replace(/\\/g, '');
  return c && c.trim() ? _touche(c.toUpperCase().charCodeAt(0), parseInt(mods, 10) || 0) : null;
}
// Listes de touches d'une macro : virgules échappées dans chaque touche.
function lireTouches(v){
  return (v || '').split(/(?<!\\),/).map(x => lireTouche(x.replace(/\\,/g, ','))).filter(Boolean);
}
function libelleTouche(k){
  if(!k || k.startsWith('n:')) return '';
  const [code, f] = k.split(':');
  const n = +code;
  const NOMS = {91:'[', 93:']', 44:',', 46:'.', 47:'/', 59:';', 61:'=', 45:'-', 92:'\\', 127:'Suppr', 10:'Entrée',
                37:'←', 38:'↑', 39:'→', 40:'↓', 32:'Espace'};
  const t = NOMS[n] || (n >= 96 && n <= 105 ? 'Num' + (n - 96) : '') || (n >= 112 && n <= 123 ? 'F' + (n - 111) : (n >= 32 && n < 127 ? String.fromCharCode(n) : '#' + n));
  return (f[0] === '1' ? 'Ctrl+' : '') + (f[2] === '1' ? 'Alt+' : '') + (f[1] === '1' ? 'Maj+' : '') + (f[3] === '1' ? 'Méta+' : '') + t;
}
// Touche du navigateur → KeyStroke Java (les codes diffèrent hors lettres).
const _JAVA = {219: 91, 221: 93, 186: 59, 187: 61, 188: 44, 189: 45, 190: 46, 191: 47, 220: 92, 46: 127, 13: 10};
function toucheClavier(e){
  // Pavé numérique : la touche physique fait foi, verrouillage numérique
  // ou non (sinon Num8 arrive comme une flèche).
  const num = /^Numpad(\d)$/.exec(e.code || '');
  const code = num ? 96 + +num[1] : (_JAVA[e.keyCode] || e.keyCode);
  return _touche(code, (e.ctrlKey ? 2 : 0) | (e.shiftKey ? 1 : 0) | (e.altKey ? 8 : 0) | (e.metaKey ? 4 : 0));
}

// ── Conditions des macros. Deux syntaxes, comme dans VASSAL :
//   « {HS_Active==false && GetProperty("SL On_Active")} » (BeanShell)
//   « CurrentPhase_Facing = 1 && Player_Level = 1 » (ancien filtre)
// Une propriété inconnue vaut "" ; une expression illisible est fausse.
function proprietesPion(p, T, S){
  const pr = Object.assign({}, _globales, _proprietes({typ: T, eta: S}));
  pr.BasicName = pr.PieceName = p.nom;
  pr.CurrentMap = pr.OldMap = p.ailleurs || _carteJeu;
  // Zone d'une pièce tenue sur une autre fenêtre (« Axis Casualties »).
  if(p.ailleurs){ const d = lirePiece(p), z = _zoneDe(p.ailleurs, d.x, d.y); if(z) pr.CurrentZone = z; }
  pr.PlayerName = pr.PlayerSide = joueur();
  return pr;
}
const _nombre = v => typeof v === 'number' || (typeof v === 'string' && v.trim() !== '' && !isNaN(Number(v)));
function _comparer(a, op, b){
  const na = Number(a), nb = Number(b);
  const num = _nombre(a) && _nombre(b);
  switch(op){
    case '==': case '=': return num ? na === nb : String(a) === String(b);
    case '!=': return num ? na !== nb : String(a) !== String(b);
    case '<': return num && na < nb;
    case '<=': return num && na <= nb;
    case '>': return num && na > nb;
    case '>=': return num && na >= nb;
    case '=~': try { return new RegExp('^(?:' + b + ')$').test(a); } catch(e){ return false; }
    case '!~': try { return !new RegExp('^(?:' + b + ')$').test(a); } catch(e){ return false; }
  }
  return false;
}
const _vrai = v => v === true || v === 'true';
function evaluerCondition(expr, pr){
  const e = expr.trim();
  if(!/^\{[^]*\}$/.test(e)){
    const val = n => (pr[n] !== undefined ? pr[n] : '');
    return e.split('||').some(ou => ou.split('&&').every(c => {
      const f = /^\s*(.+?)\s*(!=|<=|>=|=~|!~|=|<|>)\s*(.*?)\s*$/.exec(c);
      return f ? _comparer(val(f[1]), f[2], f[3]) : false;
    }));
  }
  return _vrai(evaluerExpression(e, pr));
}
// Expression BeanShell « {…} » de VASSAL, réduite à ce qu'emploie la base
// VASL : propriétés, GetProperty, If(c, a, b), c ? a : b, comparaisons,
// && || !, + - * /, et .contains("…"). Renvoie undefined si illisible ;
// un texte sans accolades est rendu tel quel.
function evaluerExpression(expr, pr){
  const m = /^\s*\{([^]*)\}\s*$/.exec(expr);
  if(!m) return expr;
  const val = n => (pr[n] !== undefined ? pr[n] : '');
  const toks = m[1].match(/"(?:\\.|[^"\\])*"|\d+(?:\.\d+)?|&&|\|\||==|!=|<=|>=|[<>!()?:,+\-*/.]|[A-Za-z_]\w*|\S/g) || [];
  let k = 0;
  const lit = () => toks[k], suivant = () => toks[k++];
  const attendre = t => { if(suivant() !== t) throw 0; };
  const num = v => _nombre(v) ? Number(v) : (() => { throw 0; })();
  const prim = () => {
    const t = suivant();
    if(t === undefined) throw 0;
    let v;
    if(t === '(') { v = expr3(); attendre(')'); }
    else if(t === '!') return !_vrai(prim());
    else if(t === '-') return -num(prim());
    else if(t[0] === '"') v = t.slice(1, -1).replace(/\\(.)/g, '$1');
    else if(/^\d/.test(t)) v = Number(t);
    else if(t === 'true' || t === 'false') v = t === 'true';
    else if(t === 'GetProperty'){ attendre('('); v = val(String(expr3())); attendre(')'); }
    else if(t === 'If'){
      attendre('('); const c = expr3(); attendre(','); const a = expr3(); attendre(','); const b = expr3(); attendre(')');
      v = _vrai(c) ? a : b;
    }
    else if(/^[A-Za-z_]/.test(t)) v = val(t);
    else throw 0;
    while(lit() === '.'){
      suivant();
      if(suivant() !== 'contains') throw 0;
      attendre('('); const x = expr3(); attendre(')');
      v = String(v).includes(String(x));
    }
    return v;
  };
  const mul = () => {
    let a = prim();
    while(lit() === '*' || lit() === '/'){ const op = suivant(), b = num(prim()); a = op === '*' ? num(a) * b : num(a) / b; }
    return a;
  };
  const add = () => {
    let a = mul();
    while(lit() === '+' || lit() === '-'){
      const op = suivant(), b = mul();
      a = op === '-' ? num(a) - num(b) : (_nombre(a) && _nombre(b) ? Number(a) + Number(b) : String(a) + String(b));
    }
    return a;
  };
  const cmp = () => {
    let a = add();
    while(['==', '!=', '<', '<=', '>', '>='].includes(lit())){ const op = suivant(); a = _comparer(a, op, add()); }
    return a;
  };
  const et = () => { let a = cmp(); while(lit() === '&&'){ suivant(); const b = cmp(); a = _vrai(a) && _vrai(b); } return a; };
  const ou = () => { let a = et(); while(lit() === '||'){ suivant(); const b = et(); a = _vrai(a) || _vrai(b); } return a; };
  const expr3 = () => {
    const c = ou();
    if(lit() !== '?') return c;
    suivant(); const a = expr3(); attendre(':'); const b = expr3();
    return _vrai(c) ? a : b;
  };
  try {
    const v = expr3();
    return k === toks.length ? v : undefined;
  } catch(e){ return undefined; }
}

// Propriétés globales du module (traits setprop). Comme VASSAL, la page
// les garde dans la partie : un changement part en « MutableProperty »
// (log, réseau) et la sauvegarde l'écrit (fixerGlobale, cvp.js). Les
// couches qui les suivent gardent aussi leur niveau dans leur état.
const _globales = {};

// Découpage d'une séquence VASSAL (SequenceEncoder) : un séparateur
// précédé d'une contre-oblique fait partie du texte, et perd celle-ci.
function _seq(s, sep){
  return (s || '').split(new RegExp('(?<!\\\\)' + sep.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')))
    .map(x => x.split('\\' + sep).join(sep));
}

// ── Commandes d'un type de pion, mises en cache par type : toutes les
// pièces d'un même pion de la palette partagent la même liste.
//   {nom, touche, i (rang du trait), faire(ctx)}   ctx = {S, p, effets, declencher}
const _cacheCommandes = new Map();

function commandesDe(type){
  if(_cacheCommandes.has(type)) return _cacheCommandes.get(type);
  const T = decoderTraits(type);
  const cmds = [], sousMenus = [], suivis = [];
  const ajouter = (i, nom, touche, faire) => cmds.push({i, nom: (nom || '').replace(/\\(.)/g, '$1').trim(), touche, faire});
  // Niveau signé d'une couche : « 3 », « -2\… ».
  const niveau = (S, i) => { const m = (S[i] || '').match(/^(-?\d+)([^]*)$/); return m ? [+m[1], m[2]] : [1, '']; };
  const premier = v => (v || '').split(';')[0].replace(/\\/g, '').trim();
  const basculerJoueur = (S, i) => {
    const f = (S[i] || '').split(';');
    const v = (f[0] || '').replace(/\\/g, '').trim();
    f[0] = (!v || v === 'null') ? idJoueur() : 'null';
    S[i] = f.join(';');
  };
  T.forEach((t, i) => {
    const ch = t.split(';');
    const id = ch[0];
    if(id === 'emb2' || id === 'emb'){
      // Au format emb2, le 17e champ est la liste d'images, même faite de
      // blancs : la couche du Covered Arc (« , , , , , » nommée ca1 à ca6)
      // n'a pas d'image mais bien six niveaux. Les compter d'après une
      // image réelle la faisait retomber à 1 à chaque cran.
      const liste = ch.length > 17 ? ch[16] : (ch.find(c => /\.(svg|gif|png)/i.test(c)) || '');
      const N = Math.max(1, liste.split(',').length);
      const boucle = ch[18] !== 'false';
      const regle = (S, f) => { const [v, r] = niveau(S, i); const n = f(Math.abs(v)); S[i] = String(v < 0 ? -n : n) + r; };
      const car = v => (v || '').replace(/\\/g, '');
      if(car(ch[3]).length > 1 || car(ch[6]).length > 1 || car(ch[9]).length > 1){
        // Embellishment0 (ancien format, plusieurs touches) : la couche n'est
        // active que lorsque TOUTES ses touches d'activation sont enclenchées
        // — « FV » : brisée (Ctrl+F) ET demi-escouade (Ctrl+V). L'état garde
        // les touches enclenchées : « -1;F », « 1;FV ».
        const act = car(ch[3]).toUpperCase(), up = car(ch[6]), down = car(ch[9]);
        const etat = S => {
          const f = (S[i] || '').replace(/\\+$/, '').split(';');
          const v = parseInt(f[0], 10) || 1;
          return {v, statut: f.length > 1 ? f[1] : (v < 0 ? '' : act)};
        };
        [...act].forEach((c, j) => ajouter(i, j ? '' : ch[1], toucheAncienne(c, ch[2]), ({S}) => {
          let {v, statut} = etat(S);
          statut = statut.includes(c) ? statut.replace(c, '') : statut + c;
          v = statut.length === act.length ? Math.abs(v) : -Math.abs(v);
          S[i] = v + ';' + statut;
        }));
        const pas = d => ({S}) => {
          const {v, statut} = etat(S);
          let n = Math.abs(v) + d;
          if(n > N) n = boucle ? 1 : N;
          if(n < 1) n = boucle ? N : 1;
          S[i] = (v > 0 ? n : -n) + ';' + statut;
        };
        [...up].forEach((c, j) => ajouter(i, j ? '' : ch[4], toucheAncienne(c, ch[5]), pas(+1)));
        [...down].forEach((c, j) => ajouter(i, j ? '' : ch[7], toucheAncienne(c, ch[8]), pas(-1)));
        return;
      }
      // Couche « toujours active » (alwaysActive, champ 26) : pas de
      // commande d'activation, comme Embellishment de VASSAL.
      if(ch[26] !== 'true') ajouter(i, ch[1], lireTouche(ch[27]) || toucheAncienne(ch[3], ch[2]), ({S}) => {
        const [v, r] = niveau(S, i); S[i] = String(v === 0 ? 1 : -v) + r;
      });
      // Couche qui suit une propriété (champs 22-24) : son niveau en
      // découle, elle n'a ni commande de niveau ni remise à zéro.
      if(id === 'emb2' && ch[22] === 'true' && ch[23]){
        suivis.push({i, prop: ch[23].replace(/\\(.)/g, '$1'), premier: parseInt(ch[24], 10) || 0, N});
        return;
      }
      ajouter(i, ch[4], lireTouche(ch[28]) || toucheAncienne(ch[6], ch[5]),
              ({S}) => regle(S, n => n + 1 > N ? (boucle ? 1 : N) : n + 1));
      ajouter(i, ch[7], lireTouche(ch[29]) || toucheAncienne(ch[9], ch[8]),
              ({S}) => regle(S, n => n - 1 < 1 ? (boucle ? N : 1) : n - 1));
      // Remise à un niveau (resetLevel) : sa valeur absolue donne le
      // niveau, son signe l'activation — « 0 » ou « -1 » : niveau 1 éteint.
      const raz = parseInt(ch[12], 10) || 0;
      if(ch[10] || ch[11]) ajouter(i, ch[10], lireTouche(ch[11]), ({S}) => {
        const [, r] = niveau(S, i);
        const n = Math.min(N, Math.max(1, Math.abs(raz)));
        S[i] = String(raz > 0 ? n : -n) + r;
      });
      // Niveau au hasard (rndKey, champ 20), activation inchangée.
      if(ch[20]) ajouter(i, ch[21], lireTouche(ch[20]), ({S}) => {
        const [v, r] = niveau(S, i);
        const n = 1 + Math.floor(Math.random() * N);
        S[i] = String(v < 0 ? -n : n) + r;
      });
    } else if(id === 'rotate'){
      // FreeRotator : rotate;facettes;touche cw;touche ccw;texte cw;texte ccw
      const N = parseInt(ch[1], 10) || 1;
      if(N > 1){
        const tourner = d => ({S}) => { const v = parseInt(S[i], 10) || 0; S[i] = String(((v + d) % N + N) % N); };
        ajouter(i, ch[4], lireTouche(ch[2]), tourner(+1));
        ajouter(i, ch[5], lireTouche(ch[3]), tourner(-1));
      }
    } else if(id === 'label'){
      ajouter(i, ch[2], lireTouche(ch[1]), ({p, effets, lot}) =>
        effets.push(() => editerLabel(p, i, (ch[2] || 'Label').replace(/\\(.)/g, '$1'), lot)));
    } else if(id === 'delete'){
      ajouter(i, ch[1], lireTouche(ch[2]), ({p, effets}) => effets.push(() => retirer(p)));
    } else if(id === 'clone'){
      ajouter(i, ch[1], lireTouche(ch[2]), ({p, effets}) => effets.push(() => cloner(p)));
    } else if(id === 'piece'){
      // BasicPiece : touches historiques de clonage et de suppression.
      const k = toucheAncienne(ch[1], 2), d = toucheAncienne(ch[2], 2);
      if(k) ajouter(i, 'Clone', k, ({p, effets}) => effets.push(() => cloner(p)));
      if(d) ajouter(i, 'Delete', d, ({p, effets}) => effets.push(() => retirer(p)));
    } else if(['replace', 'placemark', 'placeDM', 'placeACQ'].includes(id)){
      // PlaceMarker et ses dérivés VASL :
      //   commande;touche;chemin;définition;dx;dy;rotation;touche après;description;gpid;placement
      // Le chemin peut être une définition entière, pleine de « ; »
      // échappés : les champs se lisent comme SequenceEncoder.Decoder.
      const f = _seq(t, ';');
      const chemin = f[3] || '';
      const placement = parseInt(f[11], 10) || 0;
      const apres = lireTouche(f[8]);
      const acq = id === 'placeACQ' || /(^|[^a-z])ACQ($|[^a-z])/i.test(ch[1] || '');
      ajouter(i, ch[1], lireTouche(ch[2]), ({p, S, effets}) => {
        // PlaceDM ne pose rien sur une pièce dont le nom intérieur contient
        // « broken », lu avant que la touche n'atteigne les traits
        // intérieurs (VASL.counters.PlaceDM.myKeyEvent).
        if(id === 'placeDM'){
          const Ti = T.slice(i + 1), Si = S.slice(i + 1);
          if(/broken/.test(lirePiece('+/0/' + encoderTraits(Ti) + '/' + encoderTraits(Si)).nom)) return;
        }
        effets.push(() => {
          // … ni s'il y a déjà un DM au-dessus de l'unité.
          if(id === 'placeDM'){
            const s = pile(cle(p));
            if(s.slice(s.indexOf(p) + 1).some(q => q.nom === 'DM')) return;
          }
          const q = placerMarqueur(p, chemin, placement);
          if(q && apres) executerTouche(q, apres);
          // L'ACQ d'un véhicule ou d'un canon lui est liée, comme par
          // Ctrl+Alt+L. La plupart la posent par un PlaceMarker « + ACQ ».
          if(q && acq) lierPions(p, q);
          if(q && id === 'replace') retirer(p, true);
        });
      });
      // Le marqueur posé porte le nom de son PieceSlot (Prep Fire, Pin…) :
      // les barres rapides le cherchent au-dessus de l'unité.
      const slot = chemin.replace(/\\(.)/g, '$1').match(/PieceSlot:([^\/;]+)$/);
      if(id === 'placemark' && slot) cmds[cmds.length - 1].marqueur = {nom: slot[1].trim(), placement};
      // Son image sert d'icône aux barres rapides (le DM pour Break).
      if(id === 'placemark' || id === 'placeDM') cmds[cmds.length - 1].pose = chemin;
    } else if(id === 'macro'){
      // Trigger Action : macro;nom;commande;touche;condition;veille;actions;boucle…
      // La condition est évaluée sur les propriétés du pion au moment où
      // la touche lui parvient, comme TriggerAction.myKeyEvent. Les
      // boucles (absentes de la base VASL) ne sont pas gérées.
      if(ch[7] === 'true') return;
      const actions = lireTouches(ch[6]);
      const condition = (ch[4] || '').replace(/\\(.)/g, '$1').trim();
      const faire = ({S, p, declencher}) => {
        if(condition && !evaluerCondition(condition, proprietesPion(p, T, S))) return;
        actions.forEach(declencher);
      };
      ajouter(i, ch[2], lireTouche(ch[3]), faire);
      for(const k of lireTouches(ch[5])) ajouter(i, '', k, faire);
      // Une macro agit une fois la touche passée par les traits intérieurs :
      // la roue des phases change de camp quand PhaseLevel ATTEINT 9.
      for(const c of cmds) if(c.i === i) c.apres = true;
    } else if(id === 'hideCmd'){
      // RestrictCommands : hideCmd;nom;Hide|Disable;condition;touches. Si la
      // condition est vraie, ces touches n'atteignent pas les traits
      // intérieurs, et leurs commandes sont grisées ou cachées au menu.
      const touches = lireTouches(ch[4]);
      const cond = (ch[3] || '').replace(/\\(.)/g, '$1').trim();
      if(touches.length && cond) cmds.push({i, nom: '', touche: null, bloque: {touches, cond, cacher: ch[2] === 'Hide'}});
    } else if(id === 'hide'){
      // HIP : l'état est l'identifiant du joueur qui cache la pièce.
      ajouter(i, ch[2], lireTouche(ch[1]), ({S}) => basculerJoueur(S, i));
    } else if(id === 'conceal'){
      ajouter(i, 'Conceal', toucheAncienne(ch[1], 2), ({p, effets}) => effets.push(() => dissimuler(p)));
      cmds[cmds.length - 1].demasque = true;
    } else if(id === 'concealment'){
      // Le « ? » lui-même : sa commande réévalue la pile (Concealment.keyEvent).
      ajouter(i, 'Conceal', _touche(67, 2), ({p, effets}) => effets.push(() => ajusterDissimulation(cle(p))));
    } else if(id === 'obs'){
      ajouter(i, ch[3] || 'Mask', toucheAncienne(ch[1], 2), ({S}) => basculerJoueur(S, i));
    } else if(id === 'turr'){
      // Tourelle : TCA de 1 à 6 (touches cw / ccw), et BU/CE par Ctrl+B.
      const tca = d => ({S}) => {
        const f = (S[i] || '').split(';');
        const v = parseInt(f[0], 10) || 1;
        f[0] = String(((Math.abs(v) - 1 + d) % 6 + 6) % 6 + 1);
        S[i] = f.join(';');
      };
      ajouter(i, 'TCA cw', toucheAncienne(ch[3], 2), tca(+1));
      ajouter(i, 'TCA ccw', toucheAncienne(ch[4], 2), tca(-1));
      ajouter(i, 'BU', _touche(66, 2), ({S}) => {
        const f = (S[i] || '').split(';');
        f[f.length - 1] = f[f.length - 1] === 'true' ? 'false' : 'true';
        S[i] = f.length > 1 ? f.join(';') : f[0] + ';true';
      });
    } else if(id === 'translate'){
      // Déplacement d'un hexagone dans une direction. Comme Translate de
      // VASSAL, la distance verticale se compte vers le HAUT.
      const dx = parseFloat(ch[3]) || 0, dy = -(parseFloat(ch[4]) || 0);
      // Comme ASLTranslate, toute la sélection se déplace, une seule fois
      // par touche ; un pion hors de la sélection se déplace seul.
      ajouter(i, ch[1], lireTouche(ch[2]), ({p, effets, lot}) => effets.push(() => {
        if(lot.deplace) return;
        lot.deplace = true;
        const groupe = _sel.has(p) ? trierParPile([..._sel].filter(q => !q.cache)) : [p];
        deplacer(groupe, p.x + dx, p.y + dy);
      }));
    } else if(id === 'info'){
      // TextInfo de VASL (« info;texte ») : « Show Info », Ctrl+I, montre ou
      // cache la bande de caractéristiques du véhicule. Affichage local :
      // l'état ne change pas, rien ne part dans le log.
      ajouter(i, 'Show Info', _touche(73, 2), ({p, effets}) => effets.push(() => basculerInfo(p)));
    } else if(id === 'moved'){
      // MarkMoved de VASL : commande « Moved », Ctrl+M — état true/false.
      ajouter(i, 'Moved', _touche(77, 2), ({S}) => {
        S[i] = /^true/.test(S[i] || '') ? 'false' : 'true';
      });
    } else if(id === 'markmoved'){
      // MovementMarkable de VASSAL : markmoved;icône;dx;dy;commande;touche.
      ajouter(i, ch[4] || 'Mark Moved', lireTouche(ch[5]) || _touche(77, 2), ({S}) => {
        S[i] = /^true/.test(S[i] || '') ? 'false' : 'true';
      });
    } else if(id === 'PROP' || id === 'setprop'){
      // DynamicProperty (PROP, propriété du pion) et SetGlobalProperty
      // (setprop, propriété globale) : nom;contraintes;commandes, chaque
      // commande « menu:touche:type,valeur ». P fixe la valeur (une
      // expression {…} est évaluée), I l'incrémente, E la fait choisir
      // dans une liste ; R (saisie libre) est ignoré. Les contraintes
      // « numérique,min,max,boucle » bornent le résultat, comme VASSAL.
      const nom = (ch[1] || '').replace(/\\(.)/g, '$1');
      const [numerique, mini, maxi, enBoucle] = (ch[2] || '').split(',');
      const lire = S => id === 'PROP' ? (S[i] || '').replace(/\\+$/, '').replace(/\\(.)/g, '$1') : (_globales[nom] || '');
      const ecrire = (S, v) => {
        if(v === undefined) return;
        v = String(v);
        if(numerique === 'true'){
          let n = Number(v);
          if(v.trim() === '' || isNaN(n)) return;
          const lo = parseInt(mini, 10), hi = parseInt(maxi, 10);
          if(!isNaN(lo) && !isNaN(hi) && lo <= hi){
            if(enBoucle === 'true'){
              if(n > hi) n = lo + (n - hi) - 1;
              else if(n < lo) n = hi - (lo - n) + 1;
            }
            n = Math.min(hi, Math.max(lo, n));
          }
          v = String(n);
        }
        if(id === 'PROP') S[i] = v.replace(/\//g, '\\/');
        else fixerGlobale(nom, v);
      };
      const evaluer = (x, p, S) => evaluerExpression(x.replace(/\$PlayerName\$/g, joueur()), proprietesPion(p, T, S));
      _seq(ch[3], ',').forEach((cmd, j) => {
        const f = _seq(cmd, ':');
        if(f.length < 3) return;
        const touche = lireTouche(f[1]);
        const [type, ...val] = _seq(f[2], ',');
        const v = val.join(',').replace(/\\\//g, '/');
        if(type === 'P') ajouter(i, f[0], touche, ({S, p}) => ecrire(S, evaluer(v, p, S)));
        else if(type === 'I') ajouter(i, f[0], touche, ({S, p}) => {
          const d = Number(evaluer(v, p, S)), x = Number(lire(S)) || 0;
          if(!isNaN(d)) ecrire(S, x + d);
        });
        else if(type === 'E'){
          const options = val.slice(1).join(',').split(',').map(x => x.replace(/\\(.)/g, '$1').trim()).filter(Boolean);
          const cle = i + ':' + j;
          ajouter(i, f[0], touche, ({S, lot}) => {
            const r = lot.reponses && lot.reponses[cle];
            if(r !== undefined) ecrire(S, r);
          });
          cmds[cmds.length - 1].invite = {cle, titre: (val[0] || nom).replace(/\\(.)/g, '$1').replace(/:\s*$/, ''), options,
                                          actuel: S => lire(S)};
        }
      });
    } else if(id === 'sendto'){
      // SendToLocation : la pièce quitte la carte de jeu (Casualties…).
      // Champs : nom;touche;carte;board;x;y;retour;touche retour;xIndex;
      // yIndex;xOffset;yOffset;description;destination;zone;région…
      // La carte et la zone sont données par une expression {"Nom"} ou en
      // clair. Destination « L » (défaut) : le point x, y ; « Z » : le
      // centre de la zone (Zone.getCenter), introuvable : rien ne bouge,
      // comme dans VASSAL. Les autres destinations gardent le point x, y.
      // Puis le décalage xIndex × xOffset, yIndex × yOffset.
      const texte = v => (v || '').replace(/\\(.)/g, '$1').replace(/^\{"?|"?\}$/g, '').trim();
      const carte = texte(ch[3]);
      const ent = (v, d) => { const n = parseInt(texte(v), 10); return Number.isFinite(n) ? n : d; };
      const x = ent(ch[5], 0), y = ent(ch[6], 0);
      const dx = ent(ch[9], 0) * ent(ch[11], 1), dy = ent(ch[10], 0) * ent(ch[12], 1);
      const dest = texte(ch[14]).charAt(0) || 'L', zone = texte(ch[15]);
      if(carte) ajouter(i, ch[1], lireTouche(ch[2]), ({p, effets}) => {
        let pt = {x, y};
        if(dest === 'Z'){
          pt = _bdd && _bdd.zones && _bdd.zones.get(carte + '\u0001' + zone);
          if(!pt){ chatLog(`⚠️ ${p.nom} : zone « ${zone} » introuvable sur « ${carte} »`); return; }
        }
        effets.push(() => envoyer(p, carte, pt.x + dx, pt.y + dy));
      });
    } else if(id === 'submenu'){
      sousMenus.push({nom: (ch[1] || '').replace(/\\(.)/g, '$1'),
                      cmds: (ch[2] || '').split(/(?<!\\),/).map(x => x.replace(/\\(.)/g, '$1').trim())});
    }
  });
  const res = {cmds: cmds.filter(c => c.touche || c.nom || c.bloque), sousMenus, suivis, T};
  _cacheCommandes.set(type, res);
  return res;
}

// ── Passage d'une touche dans les traits d'un pion, l'état S modifié sur
// place. Une touche traverse les traits de l'extérieur vers l'intérieur.
// Une macro n'agit qu'après le passage de la touche dans les traits qui
// lui sont intérieurs (TriggerAction de VASSAL), et envoie alors chacune
// de ses touches au pion entier, aussitôt. Une restriction (hideCmd) dont
// la condition est vraie arrête la touche. Garde-fou contre les boucles.
// Les effets (poser, retirer, déplacer…) sont rendus, pas exécutés.
function _passerTouche(p, T, S, cmds, touche, cmdDirecte, lot){
  const effets = [], tirees = [];
  let agi = false, n = 0, prof = 0;
  const parcourir = (k, j) => {
    for(; j < cmds.length; j++){
      const c = cmds[j];
      if(c.bloque){
        if(c.bloque.touches.includes(k) && evaluerCondition(c.bloque.cond, proprietesPion(p, T, S))) return;
        continue;
      }
      if(c.touche !== k) continue;
      agi = true;
      if(c.apres){ parcourir(k, j + 1); c.faire(ctx); return; }
      c.faire(ctx);
    }
  };
  const transmettre = k => {
    if(!k || ++n > 200 || prof > 20) return;
    tirees.push(k);
    prof++;
    parcourir(k, 0);
    prof--;
  };
  const ctx = {S, p, effets, lot: lot || {}, declencher: transmettre};
  if(cmdDirecte){ cmdDirecte.faire(ctx); agi = true; if(cmdDirecte.touche) tirees.push(cmdDirecte.touche); }
  else transmettre(touche);
  return {agi, effets, tirees};
}

// Couches qui suivent une propriété : leur niveau, dans l'état S, prend
// la valeur de la propriété.
function _appliquerSuivis(p, T, S, suivis){
  if(!suivis.length) return;
  const pr = proprietesPion(p, T, S);
  for(const sv of suivis){
    const v = pr[sv.prop];
    if(!_nombre(v)) continue;
    const niv = Number(v) - sv.premier + 1;
    if(niv < 1 || niv > sv.N) continue;
    const m = (S[sv.i] || '').match(/^(-?\d+)([^]*)$/);
    const [x, r] = m ? [+m[1], m[2]] : [1, ''];
    S[sv.i] = String(x < 0 ? -niv : niv) + r;
  }
}

// ── Envoie une touche à un pion. Les états changés sont réécrits et
// consignés par un « D/ » ; les effets (poser, retirer, cloner,
// déplacer) passent après, chacun avec ses propres commandes de log.
function executerTouche(p, touche, cmdDirecte, lot){
  if(!p || p.deleted) return false;
  // Obscurable.keyEvent : sur l'unité d'un autre, seule la commande
  // Conceal agit, et elle démasque.
  const k = touche || (cmdDirecte && cmdDirecte.touche);
  if(accesRefuse(p) && !accesRefuse(p, k)){ dissimuler(p); return true; }
  if(refuser(p, k)) return false;
  const te = tePion(p);
  if(!te) return false;
  const S = decoderTraits(te.etat);
  const avant = S.join('\x00');
  const {cmds, suivis, T} = commandesDe(te.type);
  const nomAvant = nomRapport(p);
  const {agi, effets, tirees} = _passerTouche(p, T, S, cmds, touche, cmdDirecte, lot);
  // Couches qui suivent une propriété : leur niveau en découle.
  _appliquerSuivis(p, T, S, suivis);
  // Propriétés globales changées par la touche (« Set Sides »…).
  const globs = _globAttente.splice(0);
  if(S.join('\x00') !== avant){
    _avantAction();
    const neuf = encoderTraits(S);
    fixerEtat(p, neuf);
    _logCmd(`D/${p.uid}/${neuf}/${te.etat}`);
    majApparence(p);
    // Un véhicule qui tourne paie ses MP de VCA (D2.11).
    if(typeof compterVCA === 'function') compterVCA(p, T, avant.split('\x00'), S);
  }
  if(globs.length) _emettreGlobales(globs, p);
  if(agi) rapportsEtat(p, te.type, tirees, cmds, nomAvant);
  for(const f of effets) f();
  return agi;
}

