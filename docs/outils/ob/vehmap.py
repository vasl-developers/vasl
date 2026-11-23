# Véhicules de chaque nation : palette VASL (catégorie Veh) + texte imprimé lu dans les SVG VASL
# + nom et données de vehicles.json (VASL Tools) rattachés par gpid.
# Usage : python3 -I vehmap.py vasl vehicles.json ob/veh.json
# Texte d'un pion de véhicule dans le SVG, dans l'ordre : calibre (ou AAMG, 15PP…), B#, ROF, blindage avant,
# blindage côté, chaîne des mitrailleuses « 3/5/2 », MP (dernier). Ex. PzKw IVH : 75L | 1 | 8 | 3 | 3/5/2 | 13
import sys, json, re, os, html, xml.etree.ElementTree as ET
VASL = sys.argv[1]; VJ = json.load(open(sys.argv[2]))
root = ET.parse(VASL + '/dist/buildFile').getroot()
svgs = {os.path.splitext(f)[0]: os.path.join(r, f) for r, _, fs in os.walk(VASL + '/dist/images') for f in fs if f.endswith('.svg')}
rast = {os.path.splitext(f)[0]: os.path.join(r, f) for r, _, fs in os.walk(VASL + '/dist/images') for f in fs if f.endswith(('.gif', '.png'))}
CAL = r'\*?\d{1,3}(?:\.\d+)?(?:LL|L)?[*✽†]?(?:\(\d+\))?'

def textes(nom):
    t = open(svgs[nom], encoding='utf-8', errors='ignore').read()
    return [x for x in (html.unescape(re.sub(r'<[^>]+>', '', m)).strip() for m in re.findall(r'<text[^>]*>(.*?)</text>', t, re.S)) if x]

def champs(tx):
    """Texte imprimé -> champs. Rien n'est déduit : un champ absent reste vide."""
    d = dict(calibre='', MP='', MG='', nums=[], autres=[])
    t = [x for x in tx if x not in ('*', '✽')]
    if t and re.fullmatch(r'[*✽]?\d{1,2}[*✽†]?', t[-1]): d['MP'] = re.sub(r'\D', '', t[-1]); t = t[:-1]   # « ✽15 » compris
    for x in t:
        if not d['calibre'] and re.fullmatch(CAL, x) and re.search(r'\d\d', x): d['calibre'] = x
        elif re.fullmatch(r'[-\d★*✽]+/[-\d★*✽/]*', x): d['MG'] = x
        elif re.fullmatch(r'\d{1,2}', x): d['nums'].append(x)
        else: d['autres'].append(x)
    return d

par_gpid = {}; nat = None
for r in VJ:
    if 'Nationality' in r and len(r) == 1: nat = r['Nationality']; continue
    g = r.get('gpid'); g = g if isinstance(g, list) else [g]
    for x in g: par_gpid.setdefault(str(x), dict(r, nat_vt=nat))

res = {}
def walk(e, path):
    if e.tag.endswith('PieceSlot'):
        if len(path) > 5 and path[5] == 'Veh' and path[3] in ('Allied O/B', 'Axis O/B'):
            ims = list(dict.fromkeys(re.findall(r'([A-Za-z0-9_\-]+)\.(?:svg|gif|png)', e.text or '')))
            # face avant : pas les calques (Sz_, No_…), pas le dos (« …b »), pas l'épave
            ok = [i for i in ims if (i in svgs or i in rast) and not re.match(r'(Sz|No|Wreck|wreck|BU|CE)_', i)]
            face = next((i for i in ok if not i.endswith('b') and not re.search(r'wr$|Wr$|brk', i)), ok[0] if ok else None)
            if not face: return
            tx = textes(face) if face in svgs else []
            d = champs(tx)
            vt = par_gpid.get(e.get('gpid'), {})
            if not d['MP'] and vt:            # pas de SVG : valeurs de vehicles.json (VASL Tools), marquées comme telles
                ma = (vt.get('MA') or '').replace('†', '')
                m = re.search(r'\d{2,3}(?:LL|L)?\*?', ma)
                d = dict(calibre=m.group(0) if m else '', MP=re.sub(r'\D', '', str(vt.get('MP') or '')), MG='',
                         nums=[], autres=[], source='vehicles.json')
            res.setdefault(path[4], []).append(dict(gpid=e.get('gpid'), nom=e.get('entryName'), image=face,
                cat='/'.join(path[5:7]), textes=tx, **d, nom_vt=vt.get('Name', ''), type_vt=vt.get('Type', ''),
                mp_vt=str(vt.get('MP') or ''), ma_vt=vt.get('MA') or ''))
        return
    n = e.get('entryName') or e.get('name') or '?'
    for c in e: walk(c, path + [n])
walk(root, [])
json.dump(res, open(sys.argv[3], 'w'), ensure_ascii=False, indent=1)
tous = [g for v in res.values() for g in v]
print(len(res), 'nations ;', len(tous), 'véhicules ;', sum(1 for g in tous if g['MP'] and g.get('source') != 'vehicles.json'), 'lus dans le SVG ;',
      sum(1 for g in tous if g.get('source')), 'depuis vehicles.json ;', sum(1 for g in tous if not g['MP']), 'sans MP')
dif = [g for g in tous if g['MP'] and g['mp_vt'] and g['MP'] != re.sub(r'\D', '', g['mp_vt']) and not g.get('source')]
print(len(dif), 'désaccords de MP SVG / vehicles.json, ex. :', [(g['gpid'], g['image'], g['MP'], g['mp_vt']) for g in dif[:6]])
for k in ('German',):
    print(k, ' | '.join(f"{g['nom']}:{g['calibre']} MP{g['MP']} {g['MG']}" for g in res[k][:40]))
