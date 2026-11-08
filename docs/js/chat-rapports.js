// ═══ RAPPORTS AU CHAT — comme VASSAL ═══
// La carte de VASL rapporte mouvements et créations, les traits Report
// rapportent les commandes. Chaque rapport est un message « * … » ajouté
// à la commande de l'action : il fait partie du même coup dans le .vlog,
// et VASSAL l'affiche en le relisant.
const FORMAT_DEPLACEMENT = '$pieceName$ moves $previousLocation$ -> $location$ *';
const FORMAT_CREATION = '$pieceName$ created in $location$ *';

// Nom de lieu, comme ASLMap.locationName : l'hexagone (ou son côté), à
// défaut « offboard ».
function lieu(x, y){
  const a = ancragePlusProche(x, y);
  return a && Math.hypot(a.x - x, a.y - y) < 4 ? a.label : 'offboard';
}

// Nom d'un pion dans un rapport : tous les pions sont alors traités comme
// cachés au joueur (PieceAccess.hideAll) — un pion dissimulé s'appelle « ? ».
function nomRapport(p){
  return lirePiece(p).masque ? '?' : p.nom;
}

function _remplir(format, v){
  return format.replace(/\$(\w+)\$/g, (m, k) => (k in v ? v[k] : ''));
}

function rapport(texte){
  if(!texte || !texte.trim()) return;
  const msg = '* ' + texte;
  _logCmd('CHAT' + msg.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'));
  afficherChat(msg);
}

// MovementReporter : un rapport par groupe de pions déplacés ensemble,
// noms séparés par des virgules ; un pion invisible (HIP) n'y figure pas.
// suite : le décompte des MF / MP, au bout de la ligne.
function rapportDeplacement(liste, ax, ay, suite){
  const vus = liste.filter(p => !p.cache);
  if(!vus.length) return;
  rapport(_remplir(FORMAT_DEPLACEMENT, {pieceName: vus.map(nomRapport).join(', '),
    pieceCount: vus.length, previousLocation: lieu(ax, ay), location: lieu(vus[0].x, vus[0].y),
    mapName: _carteJeu, previousMap: _carteJeu}) + (suite ? ' ' + suite : ''));
}

// ReportState : chaque trait Report dont une touche est passée pendant la
// commande — directement ou par une macro — envoie son message, avec le
// nom du pion avant et après, le lieu, et le nom de la commande.
function rapportsEtat(p, type, tirees, cmds, nomAvant){
  if(p.cache) return;
  const T = decoderTraits(type);
  let pr = null;
  for(const t of T){
    if(!/^report;/.test(t)) continue;
    // Le format peut contenir des « ; » échappés.
    const ch = _seq(t, ';');
    const touches = lireTouches(ch[1]);
    const k = tirees.find(x => touches.includes(x));
    if(!k) continue;
    const cmd = cmds.find(c => c.touche === k && c.nom);
    const format = (ch[2] || '$location$: $newPieceName$ *').replace(/\\(.)/g, '$1');
    // FormattedString : toute propriété du pion peut figurer dans le texte.
    if(!pr){ const te = tePion(p); pr = te ? proprietesPion(p, T, decoderTraits(te.etat)) : {}; }
    rapport(_remplir(format, {...pr, location: lieu(p.x, p.y), oldLocation: p.ancienLieu || lieu(p.x, p.y),
      newPieceName: p.deleted ? nomAvant : nomRapport(p), oldPieceName: nomAvant,
      menuCommand: cmd ? cmd.nom : '', playerName: joueur(), PlayerName: joueur(), playerId: joueur(),
      mapName: _carteJeu}));
  }
}

// Chat des joueurs : « <joueur> - texte », consigné comme dans VASSAL.
function envoyerChat(texte){
  texte = (texte || '').trim();
  if(!texte) return;
  // Un message ne change pas la position : la relecture continue.
  const msg = `<${joueur()}> - ${texte}`;
  _logCmd('CHAT' + msg.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'));
  afficherChat(msg);
}

// Pose le pion d'un PieceSlot (désigné par gpid, à défaut par le nom en
// fin de chemin) à l'emplacement de `p`.
// PieceSlot désigné par un chemin de composants (ComponentPathBuilder),
// tel qu'il figure dans un trait : « Classe:nom/Classe:nom/… », avec le
// « / » encore échappé une fois par la commande +/ qui porte le trait.
function _slotDuChemin(chemin){
  if(!_bdd || !chemin) return null;
  const noms = _splitUnescaped(chemin.replace(/\\\//g, '/'), '/').map(x => {
    const c = x.replace(/\\(.)/g, '$1');
    const k = c.indexOf(':');
    return k < 0 ? '' : c.substring(k + 1);
  });
  return _bdd.parChemin.get(noms.join('\u0001')) || null;
}

// Pose le marqueur d'un trait PlaceMarker / Replace. `spec` est son
// markerSpec : un chemin vers un PieceSlot, ou une définition « +/… »
// autonome (isMarkerStandalone). Le gpId du trait est celui du marqueur
// créé, pas celui d'un PieceSlot : il ne sert pas à le retrouver.
function placerMarqueur(p, spec, placement){
  if(!_bdd){ chatLog('⚠️ Base VASL pas encore chargée'); return null; }
  let def = null, nom = null;
  if(/^\+\\?\//.test(spec)){
    // Définition dans le trait : un niveau d'échappement de « / » (la
    // commande +/) et de tabulation (le trait) en trop.
    def = developperDefinition(spec.replace(/\\\//g, '/').replace(/\\(\t)/g, '$1'));
    if(def) nom = lirePiece('+/0/' + encoderTraits(def.T) + '/' + encoderTraits(def.S)).nom;
  } else {
    let x = _slotDuChemin(spec);
    if(!x){
      // Chemin introuvable (fenêtre renommée…) : le PieceSlot du même nom.
      const n = (spec.replace(/\\(.)/g, '$1').match(/PieceSlot:([^/]*)$/) || [])[1];
      if(n) x = [..._bdd.parGpid.values()].find(y => y.n === n);
    }
    def = x && developperDefinition(x.cmd);
    nom = x && x.n;
  }
  if(!def){ chatLog(`⚠️ ${p.nom} : pion à poser introuvable`); return null; }
  const q = poserTraits(def.T, def.S, p.x, p.y, p, placement);
  chatLog(`➕ ${nom} posé sur ${p.nom}`);
  return q;
}

// La pièce part sur une autre fenêtre de VASL (Casualties…) : elle quitte
// l'affichage mais reste dans la sauvegarde, sur cette carte.
function envoyer(p, carte, x, y){
  if(p.deleted) return;
  if(carte === _carteJeu) return deplacer([p], x, y);
  _avantAction();
  const k = cle(p), sous = _dessous(p), ax = p.x, ay = p.y;
  const te = tePion(p);
  if(te) fixerEtat(p, _majPosEtat(te.etat, carte, x, y));
  p.deleted = true; p.ailleurs = carte; p.el.remove(); _sel.delete(p);
  p.retour = {x: ax, y: ay};
  _logCmd(`M/${p.uid}/${carte}/${x}/${y}/null/${_carteJeu}/${ax}/${ay}/${sous}/${joueur()}`);
  rendrePile(k); afficherPile();
  chatLog(`📤 ${p.nom} → ${carte}`);
}

