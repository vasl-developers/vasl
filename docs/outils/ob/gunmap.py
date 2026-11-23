# Canons de chaque nation : palette VASL (catégorie Gun) + texte imprimé lu dans les SVG VASL
# + nom et données de guns.json (VASL Tools) rattachés par gpid.
# Usage : python3 -I gunmap.py vasl guns.json ob/guns.json
import sys, json, re, os, html, xml.etree.ElementTree as ET
VASL = sys.argv[1]; GJ = json.load(open(sys.argv[2]))
root = ET.parse(VASL + '/dist/buildFile').getroot()
svgs = {os.path.splitext(f)[0]: os.path.join(r, f) for r, _, fs in os.walk(VASL + '/dist/images') for f in fs if f.endswith('.svg')}
rast = {os.path.splitext(f)[0]: os.path.join(r, f) for r, _, fs in os.walk(VASL + '/dist/images') for f in fs if f.endswith(('.gif', '.png'))}
TYPES = {'AT', 'INF', 'ART', 'AA', 'MTR', 'RCL', 'ATR', 'IG'}

def textes(nom):
    t = open(svgs[nom], encoding='utf-8', errors='ignore').read()
    return [x for x in (html.unescape(re.sub(r'<[^>]+>', '', m)).strip() for m in re.findall(r'<text[^>]*>(.*?)</text>', t, re.S)) if x]

def champs(tx):
    """Texte imprimé d'un pion de canon -> champs. Rien n'est déduit : un champ absent reste vide."""
    d = dict(calibre='', type='', M='', ROF='', B='', portee='', IFE='', autres=[])
    vu_m = 'M' in tx
    for i, x in enumerate(tx):
        y = x.replace('✽', '*')
        if not d['calibre'] and re.fullmatch(r'\*?\d{1,3}(?:\.\d+)?(?:LL|L)?\*?†?(?:\(\d+\))?', y) and re.search(r'\d\d|\.', y):
            m = re.match(r'(.*?)(\(\d+\))?$', y); d['calibre'] = m.group(1); d['IFE'] = (m.group(2) or '').strip('()')
        elif y in TYPES and not d['type']: d['type'] = y
        elif re.fullmatch(r'B\d\d', y): d['B'] = y[1:]
        elif re.fullmatch(r'\[\d+(?:-\d+)?\]', y): d['portee'] = y.strip('[]')
        elif re.fullmatch(r'\(\d+\)', y): d['IFE'] = y.strip('()')
        elif re.fullmatch(r'[1-3]', y) and not d['ROF'] and not (vu_m and i + 1 < len(tx) and tx[i + 1] == 'M'): d['ROF'] = y
        elif re.fullmatch(r'-?\d{1,2}†?', y) and vu_m and i + 1 < len(tx) and tx[i + 1] == 'M': d['M'] = y
        elif re.fullmatch(r'M-?\d{1,2}', y): d['M'] = y[1:]
        elif y not in ('M', '*'): d['autres'].append(y)
    return d

# guns.json : gpid -> entrée (nom, dates, notes…)
par_gpid = {}
nat = None
for r in GJ:
    if 'Nationality' in r and len(r) == 1: nat = r['Nationality']; continue
    g = r.get('gpid'); g = g if isinstance(g, list) else [g]
    for x in g:
        par_gpid.setdefault(str(x), dict(r, nat_vt=nat))

res = {}
def walk(e, path):
    if e.tag.endswith('PieceSlot'):
        if len(path) > 5 and path[5] == 'Gun' and path[3] in ('Allied O/B', 'Axis O/B'):
            t = e.text or ''
            ims = list(dict.fromkeys(re.findall(r'([A-Za-z0-9_\-]+)\.(?:svg|gif|png)', t)))
            ok = [i for i in ims if i in svgs or i in rast]
            # face avant : ni dos (nom finissant par « b »), ni attelé (« -l »), ni démonté (« dm »)
            face = next((i for i in ok if not i.endswith('b') and not re.search(r'-l$|dm|brk|BR', i)),
                        next((i for i in ok if not i.endswith('b')), ok[0] if ok else None))
            if not face: return
            tx = textes(face) if face in svgs else []
            d = champs(tx) if tx else {}
            vt = par_gpid.get(e.get('gpid'), {})
            if not d.get('calibre') and vt:          # pas de SVG : valeurs de guns.json (VASL Tools), marquées comme telles
                rof = str(vt.get('ROF') or vt.get('ROF (IFE)') or '')
                d = dict(calibre=(vt.get('CSize') or '').replace('†', ''), type=vt.get('Type', ''),
                         M=str(vt.get('M#') or '').replace('–', ''), ROF=rof[:1] if rof[:1] in '123' else '',
                         B=str(vt.get('B#') or ''), portee=str(vt.get('Range') or '') if vt.get('Type') == 'MTR' else '',
                         IFE=(re.search(r'\((\d+)\)', rof) or [None, ''])[1], autres=[], source='guns.json')
            res.setdefault(path[4], []).append(dict(gpid=e.get('gpid'), nom=e.get('entryName'), image=face,
                cat='/'.join(path[5:7]), textes=tx, **d,
                nom_vt=vt.get('Name', ''), csize_vt=vt.get('CSize', ''), type_vt=vt.get('Type', '')))
        return
    n = e.get('entryName') or e.get('name') or '?'
    for c in e: walk(c, path + [n])
walk(root, [])
json.dump(res, open(sys.argv[3], 'w'), ensure_ascii=False, indent=1)
tous = [g for v in res.values() for g in v]
print(len(res), 'nations ;', len(tous), 'canons ;', sum(1 for g in tous if g.get('calibre')), 'avec calibre lu dans le SVG ;',
      sum(1 for g in tous if g['nom_vt']), 'reliés à guns.json')
# désaccords entre SVG et guns.json (calibre ou type)
n = lambda s: (s or '').replace('✽', '*').replace('†', '')
dif = [g for g in tous if g.get('calibre') and g['csize_vt'] and (n(g['calibre']).lstrip('*') != n(g['csize_vt']).lstrip('*') or g['type'] != g['type_vt'])]
print(len(dif), 'désaccords SVG / guns.json, ex. :')
for g in dif[:8]: print('  ', g['gpid'], g['image'], 'SVG', g['type'], g['calibre'], '| guns.json', g['type_vt'], g['csize_vt'], g['nom_vt'])
for k in ('German', 'Russian'):
    print(k, ' | '.join(f"{g['type']} {g['calibre']} M{g['M']} R{g['ROF']}" for g in res[k]))
