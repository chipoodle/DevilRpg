"""¿HAY RUTA? BFS de casillas de pie entre dos puntos, con los bloques del guardado.

Uso: python build\\ruta_atasco.py x0 z0 x1 z1 [y] [mundo]
Modelo: una casilla es pisable si a la altura de los pies y de la cabeza NO hay choque y debajo SÍ lo hay.
Movimientos: 4 vecinos a la misma altura, y +/-1 de altura (escalones). Se prohíbe el agua y las hojas.
Sirve para saber si el planificador del juego *debería* encontrar ruta o si el problema es otro.
"""
import glob
import os
import struct
import sys
import zlib
from collections import deque

RAIZ = r'C:\Users\Christian\Documents\DevilRpg'
X0, Z0, X1, Z1 = (int(v) for v in sys.argv[1:5])
Y = int(sys.argv[5]) if len(sys.argv) > 5 else 63
MUNDO = sys.argv[6] if len(sys.argv) > 6 else 'New World (2)'
SAVE = os.path.join(RAIZ, 'run', 'saves', MUNDO)
sys.path.insert(0, os.path.join(RAIZ, 'tools'))
from nbtdump import R, payload  # noqa: E402

REG = {}
for path in sorted(glob.glob(os.path.join(SAVE, 'region', '*.mca'))):
    n = os.path.basename(path).split('.')
    REG[(int(n[1]), int(n[2]))] = path
CACHE = {}


def chunk(rx, rz):
    if (rx, rz) in CACHE:
        return CACHE[(rx, rz)]
    out = {}
    path = REG.get((rx >> 5, rz >> 5))
    if path:
        raw = open(path, 'rb').read()
        if len(raw) >= 8192:
            i = (rx & 31) + (rz & 31) * 32
            off = struct.unpack_from('>I', b'\x00' + raw[i * 4:i * 4 + 3])[0]
            if off and raw[i * 4 + 3]:
                p0 = off * 4096
                ln = struct.unpack_from('>I', raw, p0)[0]
                comp = raw[p0 + 4]
                blob = raw[p0 + 5:p0 + 4 + ln]
                try:
                    data = zlib.decompress(blob) if comp == 2 else None
                except Exception:
                    data = None
                if data:
                    r = R(data)
                    t = r.u1()
                    r.s()
                    root = payload(r, t)
                    for sec in (root.get('sections') or []):
                        bs = sec.get('block_states')
                        if not bs:
                            continue
                        y0 = sec.get('Y')
                        pal = bs.get('palette') or []
                        ds = bs.get('data')
                        if not pal:
                            continue
                        bits = max(4, (len(pal) - 1).bit_length())
                        vpl = 64 // bits
                        mask = (1 << bits) - 1
                        for j in range(4096):
                            ip = 0 if ds is None else ((ds[j // vpl] >> ((j % vpl) * bits)) & mask
                                                       if j // vpl < len(ds) else 0)
                            if not (0 <= ip < len(pal)):
                                continue
                            pp = pal[ip]
                            if not isinstance(pp, dict):
                                continue
                            out[(rx * 16 + (j & 15), y0 * 16 + ((j >> 8) & 15),
                                 rz * 16 + ((j >> 4) & 15))] = (
                                (pp.get('Name') or '').replace('minecraft:', ''), pp.get('Properties') or {})
    CACHE[(rx, rz)] = out
    return out


def b(x, y, z):
    return chunk(x >> 4, z >> 4).get((x, y, z), ('air', {}))[0]


SIN_CHOQUE = ('air', 'cave_air', 'void_air', 'water', 'short_grass', 'grass', 'fern', 'tall_grass',
              'poppy', 'dandelion', 'torch', 'wall_torch', 'redstone_torch', 'ladder', 'rail',
              'sunflower', 'lilac', 'rose_bush', 'peony', 'sugar_cane', 'wheat', 'carrots', 'potatoes',
              'beetroots', 'oak_sapling', 'birch_sapling', 'spruce_sapling', 'dirt_path', 'wheat_seeds')
AGUA = ('water',)


def libre(x, y, z):
    return b(x, y, z) in SIN_CHOQUE


def suelo(x, y, z):
    n = b(x, y, z)
    return n not in SIN_CHOQUE


def pisable(x, y, z):
    """De pie con los pies en (x,y,z)."""
    if b(x, y, z) in AGUA or b(x, y + 1, z) in AGUA:
        return False  # no se modela nadar
    if not (libre(x, y, z) and libre(x, y + 1, z)):
        return False
    return suelo(x, y - 1, z)


inicio = (X0, Y, Z0)
meta = (X1, Y, Z1)
if not pisable(*inicio):
    print('OJO: el inicio %s NO es pisable (pies=%s cabeza=%s debajo=%s)'
          % (inicio, b(*inicio), b(X0, Y + 1, Z0), b(X0, Y - 1, Z0)))
if not pisable(*meta):
    print('OJO: la meta %s NO es pisable (pies=%s cabeza=%s debajo=%s)'
          % (meta, b(*meta), b(X1, Y + 1, Z1), b(X1, Y - 1, Z1)))

RADIO = 200
prev = {inicio: None}
cola = deque([inicio])
while cola:
    x, y, z = cola.popleft()
    if (x, y, z) == meta:
        break
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        nx, nz = x + dx, z + dz
        if abs(nx - X0) > RADIO or abs(nz - Z0) > RADIO:
            continue
        for dy in (0, 1, -1):
            ny = y + dy
            if not pisable(nx, ny, nz):
                continue
            if (nx, ny, nz) in prev:
                continue
            prev[(nx, ny, nz)] = (x, y, z)
            cola.append((nx, ny, nz))

if meta in prev:
    camino = []
    p = meta
    while p:
        camino.append(p)
        p = prev[p]
    camino.reverse()
    print('HAY RUTA: %d pasos' % (len(camino) - 1))
    for p in camino:
        print('   %s' % (p,))
else:
    print('NO HAY RUTA en el modelo (casillas visitadas: %d)' % len(prev))
    ys = {}
    for (x, y, z) in prev:
        ys[y] = ys.get(y, 0) + 1
    print('alturas alcanzadas: %s' % sorted(ys.items()))
    xs = [p[0] for p in prev]
    zs = [p[2] for p in prev]
    print('caja alcanzada: x %d..%d  z %d..%d' % (min(xs), max(xs), min(zs), max(zs)))
