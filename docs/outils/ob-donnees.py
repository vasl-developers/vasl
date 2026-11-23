#!/usr/bin/env python3
# ═══════════════════════════════════════════════════════════════
# DONNÉES DE RÉFÉRENCE DE L'OB — js/donnees/ob-reference.js
# À relancer à la main quand VASL change. Reprend les scripts du
# prototype (outils/ob/*.py, versés tels quels) et ne garde que les
# champs utiles à la lecture d'une fiche :
#   chart   : National Capabilities Chart (outils/ob/chart.json)
#   vasl    : valeur de squad / HS / crew -> images VASL -> (gpid, case, rang)
#   sw      : armes de soutien de chaque nation (texte des SVG VASL)
#   guns    : canons (texte des SVG VASL ; nom VASL Tools par gpid)
#   veh     : véhicules (texte des SVG VASL ; à défaut vehicles.json, marqué)
# Le texte des SVG VASL fait foi ; de guns.json / vehicles.json (extension
# VASL Tools, non versée) on ne garde que les noms, sauf pour les pions
# sans SVG (champ « source »).
#
# Usage :
#   GIT_LFS_SKIP_SMUDGE=1 git clone --depth 1 --filter=blob:limit=3m \
#       -b develop https://github.com/vasl-developers/vasl.git vasl
#   unzip VASLTools.vmdx -d vt
#   python3 -I outils/ob-donnees.py vasl vt/build/guns.json vt/build/vehicles.json
# ═══════════════════════════════════════════════════════════════
import sys, os, json, subprocess, tempfile, datetime

ICI = os.path.dirname(os.path.abspath(__file__))
DEPOT = os.path.dirname(ICI)
SORTIE = os.path.join(DEPOT, 'js', 'donnees', 'ob-reference.js')

def lancer(script, *args):
    r = subprocess.run([sys.executable, '-I', os.path.join(ICI, 'ob', script), *args], capture_output=True, text=True)
    if r.returncode:
        sys.exit(f'{script} : échec\n{r.stderr}')
    print(f'{script} :', r.stdout.strip().splitlines()[0] if r.stdout.strip() else '')

# Leaders d'infanterie : couches de la case « xxLDR » de la nation, images
# préfixe + « L » + valeur (ruL92, ruL61 pour 6+1), relevées dans les traits
# emb2 du buildFile comme vaslmap.py le fait pour les squads.
LEADERS = ['10-3', '10-2', '9-2', '9-1', '8-1', '8-0', '7-0', '6+1']
def leaders(vasl, chart):
    import re, xml.etree.ElementTree as ET
    sys.path.insert(0, os.path.join(ICI, 'ob'))
    PREF = {'GERMAN':'ge','ITALIAN':'it','Eritrean':'it','FINNISH':'fi','AXIS MINOR':'ax','Hungarian':'ax',
            'JAPANESE':'ja','ALLIED MINOR':'al','Ethiopian':'al','Polish':'al','RUSSIAN':'ru','AMERICAN':'am',
            'Army':'am','Early (P.A.)':'am','U.S.M.C.':'am','BRITISH':'br','FRENCH':'fr','CHINESE':'ch',
            'G.M.D.':'ch','Red':'ch','PARTISAN':'pa'}      # table PREF de vaslmap.py
    ou = {}
    for e in ET.parse(os.path.join(vasl, 'dist', 'buildFile')).getroot().iter():
        if not e.tag.endswith('PieceSlot'): continue
        for trait in (e.text or '').split('\t'):
            trait = trait.lstrip('\\')
            if not trait.startswith('emb2;'): continue
            lst = re.findall(r'([A-Za-z0-9_]+)\.(?:svg|gif|png)', trait)
            for i, im in enumerate(dict.fromkeys(lst)):
                ou.setdefault(im, []).append((e.get('gpid'), e.get('entryName'), i + 1))
    res = {}
    for nat in chart:
        p = PREF.get(nat)
        if not p: continue
        for v in LEADERS:
            im = p + 'L' + re.sub(r'\D', '', v)
            if ou.get(im): res.setdefault(nat, {})[v] = {im: ou[im][:3]}
    print('leaders :', sum(len(v) for v in res.values()), 'valeurs reliées')
    return res

# Image sans couche (vaslmap.py ne relève que les traits emb2) : la case
# dont c'est l'image de base (crew allemande ge228C…), rang 0 = rien à régler.
def completerBases(vasl, V):
    import re, xml.etree.ElementTree as ET
    base = {}
    for e in ET.parse(os.path.join(vasl, 'dist', 'buildFile')).getroot().iter():
        if not e.tag.endswith('PieceSlot'): continue
        for trait in (e.text or '').split('\t'):
            trait = trait.lstrip('\\')
            if not trait.startswith('piece;'): continue
            for im in re.findall(r'([A-Za-z0-9_]+)\.(?:svg|gif|png)', trait):
                base.setdefault(im, []).append((e.get('gpid'), e.get('entryName'), 0))
    n = 0
    for nat in V.values():
        for imgs in nat.values():
            for im, l in imgs.items():
                if not l and base.get(im): imgs[im] = base[im][:3]; n += 1
    print('images de base reliées :', n)

def main():
    if len(sys.argv) != 4:
        sys.exit(__doc__ or 'usage : ob-donnees.py vasl guns.json vehicles.json')
    vasl, guns_vt, veh_vt = sys.argv[1:4]
    chart_f = os.path.join(ICI, 'ob', 'chart.json')
    with tempfile.TemporaryDirectory() as t:
        lancer('vaslmap.py', chart_f, vasl, os.path.join(t, 'vasl.json'))
        lancer('swmap.py', vasl, os.path.join(t, 'sw.json'))
        lancer('gunmap.py', vasl, guns_vt, os.path.join(t, 'guns.json'))
        lancer('vehmap.py', vasl, veh_vt, os.path.join(t, 'veh.json'))
        lire = lambda n: json.load(open(os.path.join(t, n), encoding='utf-8'))
        V, SW, G, VE = lire('vasl.json'), lire('sw.json'), lire('guns.json'), lire('veh.json')
    chart = json.load(open(chart_f, encoding='utf-8'))
    garder = lambda d, ks: {k: d[k] for k in ks if k in d and d[k] not in ('', None, [])}
    ref = {
        'chart': {n: {'squads': [{'squad': r['squad'], 'hs': r['hs']} for r in v['squads']],
                      'crews': [{'crew': c['crew']} for c in v['crews']]} for n, v in chart.items()},
        'vasl': V,
        'sw': {n: [garder(s, ('nom', 'gpid', 'image', 'type', 'valeurs', 'pp')) for s in l] for n, l in SW.items()},
        'guns': {n: [garder(g, ('gpid', 'nom', 'image', 'calibre', 'type', 'M', 'ROF', 'B', 'portee', 'IFE', 'nom_vt', 'source'))
                     for g in l] for n, l in G.items()},
        'veh': {n: [garder(g, ('gpid', 'nom', 'image', 'calibre', 'MP', 'MG', 'nums', 'autres', 'nom_vt', 'source'))
                    for g in l] for n, l in VE.items()},
    }
    ref['leaders'] = leaders(vasl, chart)
    completerBases(vasl, ref['vasl'])
    commit = subprocess.run(['git', '-C', vasl, 'rev-parse', '--short', 'HEAD'], capture_output=True, text=True).stdout.strip()
    ref['version'] = {'vasl': commit, 'date': datetime.date.today().isoformat()}
    texte = ('// Données de référence de l\'OB (fiches de scénario) — fichier produit par\n'
             '// outils/ob-donnees.py ; ne pas modifier à la main.\n'
             f'// VASL develop {commit} ; National Capabilities Chart (eASLRB) ; noms VASL Tools.\n'
             'const OB_REF = ' + json.dumps(ref, ensure_ascii=False, separators=(',', ':')) + ';\n'
             "if(typeof module === 'object' && module.exports) module.exports = OB_REF;\n")
    open(SORTIE, 'w', encoding='utf-8').write(texte)
    print(SORTIE, f'{len(texte.encode()) / 1024:.0f} Ko ;',
          sum(len(l) for l in ref['sw'].values()), 'SW,', sum(len(l) for l in ref['guns'].values()), 'canons,',
          sum(len(l) for l in ref['veh'].values()), 'véhicules')

main()
