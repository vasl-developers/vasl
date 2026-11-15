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
