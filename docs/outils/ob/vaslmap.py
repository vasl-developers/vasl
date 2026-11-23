# Valeur de la chart -> image VASL -> (gpid, couche) dans le buildFile
import sys, json, re, os, xml.etree.ElementTree as ET
chart = json.load(open(sys.argv[1])); VASL = sys.argv[2]
PREF = {'GERMAN':'ge','ITALIAN':'it','Eritrean':'it','FINNISH':'fi','AXIS MINOR':'ax','Hungarian':'ax',
        'JAPANESE':'ja','ALLIED MINOR':'al','Ethiopian':'al','Polish':'al','RUSSIAN':'ru','AMERICAN':'am',
        'Army':'am','Early (P.A.)':'am','U.S.M.C.':'am','BRITISH':'br','FRENCH':'fr','CHINESE':'ch',
        'G.M.D.':'ch','Red':'ch','PARTISAN':'pa'}
imgs = {os.path.splitext(f)[0] for r, _, fs in os.walk(VASL + '/dist/images') for f in fs if f.endswith('.svg')}
# image -> [(gpid, nom de case, rang dans la couche)]
ou = {}
root = ET.parse(VASL + '/dist/buildFile').getroot()
for e in root.iter():
    if not e.tag.endswith('PieceSlot'): continue
    t = e.text or ''
    for trait in t.split('\t'):
        trait = trait.lstrip('\\')
        if not trait.startswith('emb2;'): continue
        lst = re.findall(r'([A-Za-z0-9_]+)\.(?:svg|gif|png)', trait)
        for i, im in enumerate(dict.fromkeys(lst)):
            ou.setdefault(im, []).append((e.get('gpid'), e.get('entryName'), i + 1))
val = lambda s: re.sub(r'[^\d-]', '', re.sub(r'\^[^-]*', '', s))   # 6^2-2-8^8 -> 6-2-8
res = {}; manque = []
for nat, v in chart.items():
    p = PREF[nat]
    for kind, items in (('S', [r['squad'] for r in v['squads']]), ('H', [r['hs'] for r in v['squads']]), ('C', [c['crew'] for c in v['crews']])):
        for s in items:
            x = val(s)
            if not re.fullmatch(r'\d-\d-\d', x): continue
            nom = p + x.replace('-', '') + kind
            cands = sorted(i for i in imgs if re.fullmatch(re.escape(nom) + r'[A-Za-z0-9]*', i))
            if not cands: manque.append(f'{nat} {x} {kind} ({nom})'); continue
            for c in cands:
                res.setdefault(nat, {}).setdefault(f'{x} {kind}', {})[c] = ou.get(c, [])[:3]
json.dump(res, open(sys.argv[3], 'w'), ensure_ascii=False, indent=1)
tot = sum(len(v) for v in res.values())
print(tot, 'valeurs reliées ;', len(manque), 'sans image :', manque)
