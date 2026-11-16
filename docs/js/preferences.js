// ═══════════════════════════════════════════════════════════════
// PRÉFÉRENCES — Preferences de VASSAL (Fichier ▸ Préférences, ⚙)
// Une fenêtre à onglets, comme celle de VASSAL : chaque réglage vaut pour
// le poste (localStorage, clés « aslweb. ») et s'applique aussitôt.
//   LOS : l'onglet « LOS » de VASLThread.addTo — Thread Color, Enable LOS
//         checking, Hindrance Thread Color, Blocked Thread Color, Verbose
//         LOS mode, Unit opacity % during LOS checks (js/los-fil.js).
//   Décompte MF / MP : affichage du décompte des MF de l'infanterie et
//         des MP des véhicules (js/mf.js).
//   Général : « Let opponent unconceal my units », l'option
//         OpponentUnmaskable d'ObscurableOptions que VASL place dans
//         l'onglet General (js/des-utilisateurs.js, permettreDemasquage).
//   Utilisateur : nom et mot de passe gardés sur ce poste ; ils
//         remplacent la boîte d'identification à l'ouverture d'une carte
//         (js/des-utilisateurs.js, identifierJoueur).
// ═══════════════════════════════════════════════════════════════
const ONGLETS_PREF = ['general', 'utilisateur', 'los', 'mvt'];
let _ongletPref = ONGLETS_PREF.includes(_lirePref('aslweb.ongletPreferences')) ? _lirePref('aslweb.ongletPreferences') : 'los';

function ouvrirPreferences(onglet){
  fermerFenetres();
  if(onglet) _ongletPref = onglet;
  const w = document.createElement('div');
  w.id = 'fenUtil'; w.className = 'fenetre fenPrefs';
  w.setAttribute('role', 'dialog');
  w.setAttribute('aria-label', T('Préférences'));
  // Entre parenthèses, le nom du réglage dans VASL (en anglais, il l'est déjà).
  const nomVASL = v => v && LANGUE === 'fr' ? ` <small>(${_esc(v)})</small>` : '';
  const couleur = (quoi, lib, vasl) => `<label class="coulPref"><input type="color" data-los="${quoi}">`
    + `<span>${_esc(T(lib))}${nomVASL(vasl)}</span></label>`;
  const coche = (attr, lib, vasl) => `<label class="coche"><input type="checkbox" ${attr}>`
    + `<span>${_esc(T(lib))}${nomVASL(vasl)}</span></label>`;
  w.innerHTML = `<div class="ft">⚙ ${_esc(T('Préférences'))}</div><div class="fc">
    <div class="ongletsPref" role="tablist">
      <button type="button" role="tab" data-onglet="general">${_esc(T('Général'))}</button>
      <button type="button" role="tab" data-onglet="utilisateur">${_esc(T('Utilisateur'))}</button>
      <button type="button" role="tab" data-onglet="los">LOS</button>
      <button type="button" role="tab" data-onglet="mvt">${_esc(T('Décompte MF / MP'))}</button>
    </div>
    <div class="pagePref" data-page="general" role="tabpanel">
      ${coche('id="prefDevoile"', 'Permettre à mes adversaires de démasquer mes unités', 'Let opponent unconceal my units')}
      <p class="fnote">${_esc(T('Vos adversaires pourront retirer le « ? » de vos unités dissimulées (commande Conceal), sans les voir avant. Les unités HIP restent cachées. Le choix part aussitôt aux joueurs de la salle et la partie le garde.'))}</p>
      <p class="fnote" id="prefDevoileObs" hidden>${_esc(T('Vous êtes observateur : la case s\'appliquera à vos unités dès que vous vous identifierez (Utilisateur).'))}</p>
    </div>
    <div class="pagePref" data-page="utilisateur" role="tabpanel">
      <p class="fnote" id="prefConnecte"></p>
      <label>${_esc(T('Nom d\'utilisateur'))}<input id="prefNom" maxlength="24" autocomplete="username" autocapitalize="off" spellcheck="false"></label>
      <label>${_esc(T('Mot de passe'))}<input id="prefMdp" type="password" autocomplete="current-password"></label>
      <label class="coche"><input type="checkbox" id="prefVoirMdp"><span>${_esc(T('Afficher le mot de passe'))}</span></label>
      <div class="ferr" id="prefErr"></div>
      <div class="fb">
        <button type="button" id="prefConnecter" class="prim">${_esc(T('Enregistrer et se connecter'))}</button>
        <button type="button" id="prefOublier">${_esc(T('Effacer'))}</button>
        <button type="button" id="prefGerer">${_esc(T('Utilisateurs…'))}</button>
      </div>
      <p class="fnote">${_esc(T('Retenus sur ce poste, ils identifient le joueur à chaque carte ouverte, sans la boîte d\'identification ; effacés, elle revient. Le mot de passe est celui de VASL (il ouvre vos pions dissimulés et HIP) ; il est gardé en clair dans ce navigateur : ne pas l\'enregistrer sur un poste partagé.'))}</p>
    </div>
    <div class="pagePref" data-page="los" role="tabpanel">
      ${couleur('fil', 'Couleur du fil', 'Thread Color')}
      ${coche('data-los="verif"', 'Vérifier la ligne de vue', 'Enable LOS checking')}
      ${couleur('gene', 'Couleur du fil après une gêne', 'Hindrance Thread Color')}
      ${couleur('bloc', 'Couleur du fil après le blocage', 'Blocked Thread Color')}
      ${coche('data-los="verbeux"', 'LOS détaillée', 'Verbose LOS mode')}
      <label>${_esc(T('Opacité des pions pendant la visée'))}${nomVASL('Unit opacity % during LOS checks')}
        <select data-los="opacite">${LOS_OPACITES.map(v => `<option value="${v}">${v} %</option>`).join('')}</select></label>
      <div class="apercuLOS" aria-hidden="true"><span data-seg="fil"></span><span data-seg="gene"></span><span data-seg="bloc"></span></div>
      <p class="fnote">${_esc(T('Aperçu : dégagé, puis après la première gêne, puis après le blocage.'))}</p>
      <div class="fb"><button type="button" data-los="defaut">${_esc(T('Couleurs de VASL'))}</button></div>
    </div>
    <div class="pagePref" data-page="mvt" role="tabpanel">
      ${coche('data-mvt="MF"', 'Afficher le décompte des MF (infanterie)')} <p class="fnote exMvt">* 4-6-7 … moves … * 2MF/ 3MF</p>
      ${coche('data-mvt="MP"', 'Afficher le décompte des MP (véhicules)')} <p class="fnote exMvt">* T-34 M40 … moves … * 1MP/ 3MP</p>
      <p class="fnote">${_esc(T('Masqué, le décompte continue d\'être tenu, mais n\'est plus écrit au chat ni au log.'))}</p>
    </div>
    <div class="fb"><button type="button" id="prefFermer" class="prim">${_esc(T('Fermer'))}</button></div>
  </div>`;
  document.body.appendChild(w);
  const q = s => w.querySelector(s);
  // Valeurs courantes, et l'aperçu du fil.
  const lire = () => {
    q('[data-los="fil"]').value = LOS_COUL_FIL;
    q('[data-los="gene"]').value = LOS_COUL_GENE;
    q('[data-los="bloc"]').value = LOS_COUL_BLOC;
    q('[data-los="verif"]').checked = _losVerif;
    q('[data-los="verbeux"]').checked = _losVerbeux;
    q('[data-los="opacite"]').value = _losOpacite;
    q('[data-mvt="MF"]').checked = decompteAffiche('MF');
    q('[data-mvt="MP"]').checked = decompteAffiche('MP');
    // La préférence elle-même, comme le BooleanConfigurer de VASSAL.
    q('#prefDevoile').checked = _lireLocal(CLE_DEMASQUABLE, '0') === '1';
    q('#prefDevoileObs').hidden = !!_utilisateur;
    q('[data-seg="fil"]').style.background = LOS_COUL_FIL;
    q('[data-seg="gene"]').style.background = LOS_COUL_GENE;
    q('[data-seg="bloc"]').style.background = LOS_COUL_BLOC;
  };
  const lireIdent = () => {
    const m = identifiantsMemorises();
    q('#prefNom').value = m ? m.nom : '';
    q('#prefMdp').value = m ? m.mdp : '';
    q('#prefConnecte').textContent = T('Connecté :') + ' ' + _nomUtilisateur()
      + (m ? '' : ' — ' + T('aucun identifiant enregistré'));
  };
  const montrer = n => {
    _ongletPref = n;
    try { localStorage.setItem('aslweb.ongletPreferences', n); } catch(e){}
    for(const b of w.querySelectorAll('.ongletsPref button')){
      b.classList.toggle('actif', b.dataset.onglet === n);
      b.setAttribute('aria-selected', String(b.dataset.onglet === n));
    }
    for(const p of w.querySelectorAll('.pagePref')) p.hidden = p.dataset.page !== n;
  };
  for(const b of w.querySelectorAll('.ongletsPref button')) b.addEventListener('click', () => montrer(b.dataset.onglet));
  // « input » : la couleur suit le sélecteur pendant qu'on le promène.
  for(const k of ['fil', 'gene', 'bloc']) q(`[data-los="${k}"]`).addEventListener('input', e => { reglerLOS(k, e.target.value); lire(); });
  q('[data-los="verif"]').addEventListener('change', e => reglerLOS('verif', e.target.checked));
  q('[data-los="verbeux"]').addEventListener('change', e => {
    if(e.target.checked !== _losVerbeux){ basculerVerbeux(); rafraichirActions(['verbeux']); }
  });
  q('[data-los="opacite"]').addEventListener('change', e => reglerLOS('opacite', e.target.value));
  q('[data-los="defaut"]').addEventListener('click', () => {
    for(const k of ['fil', 'gene', 'bloc']) reglerLOS(k, LOS_DEFAUTS[k]);
    lire();
  });
  q('#prefDevoile').addEventListener('change', e => permettreDemasquage(e.target.checked));
  for(const u of ['MF', 'MP']) q(`[data-mvt="${u}"]`).addEventListener('change', e => reglerDecompte(u, e.target.checked));
  // Utilisateur : contrôlé comme dans la boîte d'identification.
  const err = q('#prefErr');
  q('#prefVoirMdp').addEventListener('change', e => { q('#prefMdp').type = e.target.checked ? 'text' : 'password'; });
  q('#prefConnecter').addEventListener('click', () => {
    const r = connecterIdentifiants(q('#prefNom').value, q('#prefMdp').value);
    err.textContent = r.erreur || '';
    if(!r.erreur){ lireIdent(); err.textContent = ''; q('#prefConnecte').textContent += ' ✓'; }
  });
  for(const c of [q('#prefNom'), q('#prefMdp')])
    c.addEventListener('keydown', e => { if(e.key === 'Enter'){ e.preventDefault(); q('#prefConnecter').click(); } });
  q('#prefOublier').addEventListener('click', () => {
    oublierIdentifiants();
    err.textContent = '';
    lireIdent();
    chatLog('🗑 ' + T('Identifiants effacés de ce poste : la prochaine carte ouverte demandera l\'identification'));
  });
  q('#prefGerer').addEventListener('click', () => { w.remove(); gererUtilisateurs(); });
  const fermer = () => w.remove();
  q('#prefFermer').addEventListener('click', fermer);
  w.addEventListener('keydown', e => { if(e.key === 'Escape'){ e.stopPropagation(); fermer(); } });
  lire();
  lireIdent();
  montrer(_ongletPref);
  if(_profil === 'pc') setTimeout(() => { const b = w.querySelector('.ongletsPref .actif'); if(b) b.focus(); }, 0);
  return w;
}
