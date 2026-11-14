// ═══════════════════════════════════════════════════════════════
// DÉS EN 3D — un jet roule du bas vers le haut et s'arrête en haut à
// droite de la carte
// Le jet est tiré d'abord (lancerDR, lancerdr, ou reçu d'un autre
// joueur) ; les dés roulent ensuite et s'arrêtent sur ses faces.
// L'animation ne change rien au jet, au chat ni au log : un simple
// décor, que chacun peut couper (menu Dés, ou la feuille « Plus »).
// Des cubes en CSS 3D, sans bibliothèque : chaque face prend l'image du
// dé du module (DC<n>_R.png, DC<n>_W.png), sur des points dessinés en
// attendant qu'elle arrive.
// Ni pendant la relecture d'un .vlog ni à l'ouverture d'un fichier :
// seuls les jets lancés ici ou reçus en direct roulent.
// ═══════════════════════════════════════════════════════════════
let _des3d = _lirePref('aslweb.des3d') !== '0';
let _jetLocal = false;                // vrai pendant l'affichage d'un jet lancé ici

function basculerDes3d(){
  _des3d = !_des3d;
  try { localStorage.setItem('aslweb.des3d', _des3d ? '1' : '0'); } catch(e){}
  if(!_des3d) _retirerDes3d();
}

// Rotation du cube qui amène la face n devant (face 1 devant, 6 derrière,
// 2 à droite, 5 à gauche, 3 en haut, 4 en bas : les faces opposées font 7).
const _FACE_DEVANT = {1: [0, 0], 2: [0, -90], 3: [-90, 0], 4: [90, 0], 5: [0, 90], 6: [0, 180]};
const _FACE_POSE = {1: '', 2: 'rotateY(90deg)', 3: 'rotateX(90deg)', 4: 'rotateX(-90deg)', 5: 'rotateY(-90deg)', 6: 'rotateY(180deg)'};
// Points de chaque face, dans une grille de 3 × 3 (cases 0 à 8).
const _POINTS = {1: [4], 2: [2, 6], 3: [2, 4, 6], 4: [0, 2, 6, 8], 5: [0, 2, 4, 6, 8], 6: [0, 2, 3, 5, 6, 8]};

let _des3dFin = null;

function _retirerDes3d(){
  clearTimeout(_des3dFin);
  const s = document.getElementById('des3d');
  if(s) s.remove();
}

function _cube(couleur, valeur, taille){
  const de = document.createElement('div');
  de.className = 'de3d';
  const cube = document.createElement('div');
  cube.className = 'cube ' + (couleur === DE_COLORE ? 'colore' : 'blanc');
  for(let n = 1; n <= 6; n++){
    const f = document.createElement('div');
    f.className = 'face';
    f.style.transform = `${_FACE_POSE[n]} translateZ(${taille / 2}px)`;
    for(let c = 0; c < 9; c++){
      const p = document.createElement('i');
      if(_POINTS[n].includes(c)) p.className = 'pt';
      f.appendChild(p);
    }
    chargerImage(`DC${n}_${couleur}.png`).then(u => {
      if(!u) return;
      const im = document.createElement('img');
      im.src = u; im.alt = '';
      f.appendChild(im);
    });
    cube.appendChild(f);
  }
  de.appendChild(cube);
  return {de, cube, valeur};
}

// type : 'DR' (dé coloré puis dé blanc) ou 'dr' (un dé blanc).
function animerDes(type, valeurs, legende){
  if(!_des3d) return;
  if(matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  _retirerDes3d();
  const zone = document.getElementById('mapWrap');
  const r = zone && zone.offsetWidth ? zone.getBoundingClientRect()
          : {left: 0, top: 0, width: innerWidth, height: innerHeight};
  const taille = Math.round(Math.max(44, Math.min(84, Math.min(r.width, r.height) / 7)));

  const scene = document.createElement('div');
  scene.id = 'des3d';
  scene.setAttribute('aria-hidden', 'true');
  Object.assign(scene.style, {left: r.left + 'px', top: r.top + 'px', width: r.width + 'px', height: r.height + 'px'});
  scene.style.setProperty('--de', taille + 'px');
  const piste = document.createElement('div');
  piste.className = 'piste';
  scene.appendChild(piste);
  const leg = document.createElement('div');
  leg.className = 'legende';
  leg.textContent = legende;
  scene.appendChild(leg);
  document.body.appendChild(scene);

  const des = valeurs.map((v, i) => _cube(i === 0 && type === 'DR' ? DE_COLORE : DE_BLANC, v, taille));
  const duree = 1300;
  des.forEach((d, i) => {
    piste.appendChild(d.de);
    // Arrivée du bas de l'écran, un peu de biais, jusqu'en haut à droite :
    // les dés montent, dépassent à peine leur place, puis y retombent.
    const dx = -(.3 + Math.random() * .9) * taille;
    const depart = r.height + taille * 2;
    d.de.animate([
      {transform: `translate(${dx}px, ${depart}px)`, opacity: 0},
      {transform: `translate(${dx * .5}px, ${depart * .45}px)`, opacity: 1, offset: .35},
      {transform: `translate(${dx * .15}px, ${-taille * .3}px)`, offset: .65},
      {transform: `translate(${dx * .04}px, ${taille * .08}px)`, offset: .82},
      {transform: 'translate(0px, 0px)'}
    ], {duration: duree + i * 120, easing: 'cubic-bezier(.2,.7,.3,1)', fill: 'both'});
    // Tours entiers, puis la face du jet devant, un peu de travers.
    const [fx, fy] = _FACE_DEVANT[d.valeur];
    const tx = 360 * (2 + Math.floor(Math.random() * 2)), ty = 360 * (1 + Math.floor(Math.random() * 2));
    const z = Math.round((Math.random() - .5) * 30);
    const de0 = `rotateZ(${z - 200}deg) rotateX(${fx - tx}deg) rotateY(${fy - ty}deg)`;
    d.cube.animate([
      {transform: de0},
      {transform: `rotateZ(${z}deg) rotateX(${fx}deg) rotateY(${fy}deg)`}
    ], {duration: duree + i * 120, easing: 'cubic-bezier(.15,.6,.25,1)', fill: 'both'});
  });
  setTimeout(() => leg.classList.add('vue'), duree);
  _des3dFin = setTimeout(() => {
    scene.classList.add('part');
    _des3dFin = setTimeout(() => scene.remove(), 450);
  }, duree + 1700);
}
