# Armes de soutien de chaque nation : palette VASL (SW) + valeurs lues dans les SVG
import sys, json, re, os, html, xml.etree.ElementTree as ET
VASL = sys.argv[1]; root = ET.parse(VASL + '/dist/buildFile').getroot()
svgs = {os.path.splitext(f)[0]: os.path.join(r, f) for r, _, fs in os.walk(VASL + '/dist/images') for f in fs if f.endswith('.svg')}
def textes(nom):
    t = open(svgs[nom], encoding='utf-8', errors='ignore').read()
    out = []
    for x in re.findall(r'<text[^>]*>(.*?)</text>', t, re.S):
        x = html.unescape(re.sub(r'<[^>]+>', '', x)).strip()
        if x: out.append(x)
    return out
res = {}
def walk(e, path):
    if e.tag.endswith('PieceSlot'):
        if len(path) > 5 and path[5] == 'SW' and path[3] in ('Allied O/B', 'Axis O/B'):
            t = e.text or ''
            ims = list(dict.fromkeys(i for i in re.findall(r'([A-Za-z0-9_]+)\.svg', t) if i in svgs))
            vis = [i for i in ims if not re.search(r'(b|brk|BR|dmb|dmbr)$', i)] or ims
            if not vis: return
            tx = textes(vis[0])
            fp = next((x for x in tx if re.fullmatch(r'\d{1,2}-\d{1,2}', x)), '')
            if not fp and tx and tx[0] == 'MTR':         # mortier : calibre + portée [min-max]
                cal = next((re.sub(r'\D', '', x) for x in tx[1:] if re.fullmatch(r'\*?\d{2,3}\D?', x)), '')
                po = next((x.strip('[]') for x in tx if re.fullmatch(r'\[\d+-\d+\]', x)), '')
                fp = f'{cal} [{po}]' if cal and po else ''
            res.setdefault(path[4], []).append(dict(nom=e.get('entryName'), gpid=e.get('gpid'), image=vis[0],
                type=tx[0] if tx else '', valeurs=fp, textes=tx,
                pp=next((x for x in tx if re.fullmatch(r'\d+PP', x)), '')))
            # versions démontées (« dm ») : couche de la même case de palette, avec leurs propres valeurs et PP
            for dm in [i for i in ims if i.endswith('dm') and i != vis[0]]:
                td = textes(dm)
                fd = next((x for x in td if re.fullmatch(r'\d{1,2}-\d{1,2}', x)), '')
                if fd: res[path[4]].append(dict(nom=e.get('entryName') + ' (dm)', gpid=e.get('gpid'), image=dm,
                    type=td[0], valeurs=fd, textes=td, pp=next((x for x in td if re.fullmatch(r'\d+PP', x)), '')))
        return
    n = e.get('entryName') or e.get('name') or '?'
    for c in e: walk(c, path + [n])
walk(root, [])
json.dump(res, open(sys.argv[2], 'w'), ensure_ascii=False, indent=1)
print(len(res), 'nations ;', sum(len(v) for v in res.values()), 'armes')
for n in ('Russian', 'Finnish'):
    print(n, ' | '.join(f"{s['type']} {s['valeurs']}".strip() for s in res.get(n, [])))
