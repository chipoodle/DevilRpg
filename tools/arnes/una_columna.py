# -*- coding: utf-8 -*-
"""UNA COLUMNA DEL GUARDADO, celda a celda: que hay en (x, z) de yMin a yMax.

Uso:  python tools\\arnes\\una_columna.py [mundo] [x] [z] [yMin] [yMax]

  (por defecto: 'world' —la copia migrada que deja el banco—, el centro de la aldea 470,646 y de 30 a 95)

POR QUE EXISTE (9-oct-2026): el arnes mide la aldea en el centro `470,63,646` y esa manana TODAS las corridas salian
con `aldeanos=0`, cero avisos de rendicion y los contadores de trabajo a cero. La columna de ese centro lo explico en
un segundo: **`470,45..62 = water`, con `470,44 = sand`** — o sea que en la copia del guardado **no hay aldea ahi, hay
MAR**, y el mod fundaba una aldea nueva **bajo el agua** (cota 46, su propio aviso `muro reconstruido al nivel del
pueblo 46`). Sin esta herramienta eso se adivina; con ella se ve.

Reutiliza el lector de `tools/nbtdump.py`, el mismo de `columna_mina.py` y `anillo_del_muro.py` (los que funcionan:
el primer intento de leer guardados fallo tres veces). El mundo puede ser el nombre de una carpeta de `run/saves`
(o un mundo suelto de `run`): **solo lee**, no escribe nada.
"""
import glob
import os
import struct
import sys
import zlib

RAIZ = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
MUNDO = sys.argv[1] if len(sys.argv) > 1 else 'world'
X = int(sys.argv[2]) if len(sys.argv) > 2 else 470
Z = int(sys.argv[3]) if len(sys.argv) > 3 else 646
Y0 = int(sys.argv[4]) if len(sys.argv) > 4 else 30
Y1 = int(sys.argv[5]) if len(sys.argv) > 5 else 95
SAVE = os.path.join(RAIZ, 'run', 'saves', MUNDO)
if not os.path.isdir(SAVE):
    SAVE = os.path.join(RAIZ, 'run', MUNDO)
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


def bloque(x, y, z):
    return chunk(x >> 4, z >> 4).get((x, y, z), ('air', {}))[0]


if not os.path.isdir(SAVE):
    print('ABORTADO: no existe el mundo %s (mira run/saves y run)' % MUNDO)
    raise SystemExit(1)

print('mundo=%s  carpeta=%s  columna x=%d z=%d  y=%d..%d' % (MUNDO, os.path.basename(SAVE), X, Z, Y0, Y1))
anterior, desde = None, Y0
for y in range(Y0, Y1 + 1):
    b = bloque(X, y, Z)
    if anterior is not None and b != anterior:
        print('  y=%3d..%3d  %s' % (desde, y - 1, anterior))
        desde = y
    anterior = b
if anterior is not None:
    print('  y=%3d..%3d  %s' % (desde, Y1, anterior))
