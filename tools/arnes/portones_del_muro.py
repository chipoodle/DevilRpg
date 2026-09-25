"""LOS CUATRO PORTONES DEL MURO: ¿se puede pisar la casilla de dentro y la de fuera?

Uso: python build\\portones_del_muro.py [mundo]
Aplica la MISMA prueba que `VillageManager.esCeldaDePie` (aire a los pies y a la cabeza, suelo firme debajo) a la
casilla de paso de cada portón cardinal, por dentro y por fuera, a la cota del pueblo.
"""
import glob
import os
import struct
import sys
import zlib

RAIZ = r'C:\Users\Christian\Documents\DevilRpg'
MUNDO = sys.argv[1] if len(sys.argv) > 1 else 'New World (2)'
SAVE = os.path.join(RAIZ, 'run', 'saves', MUNDO)
CENTRO = (470, 646)
R = 62
COTA = 63
sys.path.insert(0, os.path.join(RAIZ, 'tools'))
from nbtdump import R as Reader, payload  # noqa: E402

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
                    r = Reader(data)
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


AIRE = ('air', 'cave_air', 'void_air')


def pisable(x, y, z):
    pies = b(x, y, z) in AIRE
    cabeza = b(x, y + 1, z) in AIRE
    suelo = b(x, y - 1, z) not in AIRE
    return pies and cabeza and suelo, 'pies=%s cabeza=%s debajo=%s' % (b(x, y, z), b(x, y + 1, z), b(x, y - 1, z))


print('mundo=%s  centro=%s  radio=%d  cota=%d' % (MUNDO, CENTRO, R, COTA))
for nombre, (dx, dz) in (('ESTE', (R, 0)), ('OESTE', (-R, 0)), ('SUR', (0, R)), ('NORTE', (0, -R))):
    ux, uz = (0 if dx == 0 else (1 if dx > 0 else -1)), (0 if dz == 0 else (1 if dz > 0 else -1))
    print()
    print('%s: porton en (%d,%d)' % (nombre, CENTRO[0] + dx, CENTRO[1] + dz))
    for lado, signo in (('DENTRO', -1), ('FUERA', 1)):
        x = CENTRO[0] + dx + ux * signo
        z = CENTRO[1] + dz + uz * signo
        ok, detalle = pisable(x, COTA, z)
        print('   %-6s casilla (%d,%d,%d)  pisable=%s  %s' % (lado, x, COTA, z, 'SI' if ok else 'NO', detalle))
