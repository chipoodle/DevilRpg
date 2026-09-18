"""Busca UUIDs en TODOS los archivos de entidades (.mca) y dice en qué chunk está cada uno."""
import struct, zlib, gzip, sys, uuid as uuidlib, glob, re, os

def chunks(path):
    with open(path, 'rb') as f:
        raw = f.read()
    if len(raw) < 8192:
        return
    for i in range(1024):
        off = struct.unpack_from('>I', b'\x00' + raw[i*4:i*4+3])[0]
        cnt = raw[i*4+3]
        if off == 0 or cnt == 0:
            continue
        base = off * 4096
        if base + 5 > len(raw):
            continue
        length = struct.unpack_from('>I', raw, base)[0]
        comp = raw[base+4]
        blob = raw[base+5:base+4+length]
        try:
            data = gzip.decompress(blob) if comp == 1 else (zlib.decompress(blob) if comp == 2 else (blob if comp == 3 else None))
        except Exception:
            data = None
        if data:
            yield i, data

targets = {}
for a in sys.argv[1:]:
    if '=' in a:
        u, label = a.split('=', 1)
    else:
        u, label = a, ''
    targets[u.strip().lower()] = label

files = []
for root in sys.argv[0:1]:
    pass
for pattern in ['run/saves/*/entities/*.mca', 'run/saves/*/DIM-1/entities/*.mca', 'run/saves/*/DIM1/entities/*.mca']:
    files += glob.glob(pattern)

found = {}
for path in files:
    for idx, data in chunks(path):
        for u, label in targets.items():
            if uuidlib.UUID(u).bytes in data:
                # sacar el 'id' (tipo) que hay cerca
                pos = data.find(uuidlib.UUID(u).bytes)
                near = [s.decode('ascii', 'replace') for s in re.findall(rb'[ -~]{8,}', data[max(0, pos-600):pos+600])]
                tipo = [s for s in near if s.startswith('devilrpg:') or s.startswith('minecraft:')]
                found.setdefault(u, []).append((path, idx, tipo))

for u, label in targets.items():
    hits = found.get(u, [])
    if hits:
        for path, idx, tipo in hits:
            print('EXISTE  %-40s %-28s %s chunk=%d  %s' % (label, u, os.path.basename(path), idx, tipo[:4]))
    else:
        print('NO EXISTE %-38s %s' % (label, u))
