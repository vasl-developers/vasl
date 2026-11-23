# VASLweb

Carte VASL dans le navigateur : ouvrir, jouer et relire des parties
`.vsav` / `.vlog`. Application installable (PWA), servie par GitHub Pages :
ouvrir `VASLweb.html` (ou la racine du site), puis « Installer » /
« Ajouter à l'écran d'accueil ».

| Fichier | Rôle |
|---|---|
| `VASLweb.html` | L'application (une seule page) |
| `css/vaslweb.css` | Sa feuille de style |
| `pwa.js` | Manifeste, service worker, ouverture des `.vsav` / `.vlog` (inactif en `file://`) |
| `sw.js` | Service worker : application réseau d'abord, boards VASL gardés pour le hors-ligne |
| `manifest.webmanifest`, `icons/` | Installation |
| `index.html` | Renvoie vers `VASLweb.html` |

## Préparer une partie depuis le PDF d'un scénario

Dans le dialogue des boards (« Pick new boards… », ou « Nouvelle partie… »),
le bouton **« Depuis un PDF… »** lit la fiche scannée d'un scénario : la
grille des boards est pré-remplie (numéros, sens, overlays propres au
scénario), puis, après Ok, le panneau **« OB du scénario »** montre, camp par
camp, chaque pion reconnu et sa quantité, à glisser sur la carte comme depuis
la palette. Rien n'est posé tout seul ; un pion douteux n'est jamais nommé :
il garde ses candidats, ou reste illisible avec sa découpe.

| Fichier | Rôle |
|---|---|
| `js/ob-image.js` | Traitements d'image du prototype Python (OpenCV), sans dépendance, au pixel près |
| `js/ob-pdf.js` | Image de la fiche (JPEG pris tel quel dans le PDF, sinon rendu pdf.js) ; bibliothèques chargées à la demande |
| `js/ob-ocr.js` | Pool de workers tesseract.js (données eng tessdata_fast 4.1.0, comme le prototype) |
| `js/ob-lecture.js` | Lecture de l'OB : cases, cadres, nations, squads, leaders, SW, canons, véhicules, quantités |
| `js/ob-carte.js` | Schéma des boards : cases, numéros, sens, grille |
| `js/ob-panneau.js` | Le bouton du dialogue des boards et le panneau « OB du scénario » |
| `js/donnees/ob-reference.js` | Données de référence (produites par `outils/ob-donnees.py`) |
| `outils/ob-donnees.py`, `outils/ob/` | Générateur des données (scripts du prototype, National Capabilities Chart) |
| `outils/test-ob.html` | Page d'essai locale : ouvre des PDF, montre carte et verdicts, exporte en JSON |

Ces fichiers ne sont chargés qu'au premier clic sur « Depuis un PDF… ».
Les PDF de scénario et les images de fiches sont sous droits (MMP) :
jamais dans ce dépôt, les essais se font en local.
