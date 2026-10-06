# -*- coding: utf-8 -*-
"""EL ANILLO DEL MURO: ¿donde hay muro, donde porton y donde AGUJERO? (medido, sin adivinar)

Uso:  python tools\\arnes\\anillo_del_muro.py [mundo] [cota]

POR QUE EXISTE: medido con el arnes (MEDIR_OLA_REAL, 8 asaltantes a r=60), los OCHO entran y llegan al centro SIN
ROMPER EL MURO NI UNA VEZ. El jugador ha decidido CERRAR EL ANILLO para que el asedio tenga que abrir brecha, asi que
antes hay que saber EXACTAMENTE donde esta el muro, donde los cuatro portones (que son a proposito) y donde los
agujeros (que no).

DOS LECCIONES QUE ESTA HERRAMIENTA YA PASA (costaron tres intentos fallidos):
- El lector de guardados es el de `columna_mina.py` (que funciona). El guardado de `run/saves` es el mundo EN CRUDO
  (cota 63, sin aldea); el migrado es `run/world`.
- El muro son TRONCOS y columnas de ADOQUIN, no piedra: contar "lo que no es aire" mide el TERRENO.
"""
import glob
import math
import os
import struct
import sys
import zlib

RAIZ = r'C:\Users\Christian\Documents\DevilRpg'
MUNDO = sys.argv[1] if len(sys.argv) > 1 else 'world'
COTA = int(sys.argv[2]) if len(sys.argv) > 2 else 83
R = 62
SAVE = os.path.join(RAIZ, 'run', 'saves', MUNDO)
if not os.path.isdir(SAVE):
    SAVE = os.path.join(RAIZ, 'run', MUNDO)
CENTRO = (470, 646)
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


def anillo(centro, r):
    """El MISMO anillo que construye el mod (VillageGenerator.anilloDelMuro)."""
    pts = []
    samples = 720
    for a in range(samples + 1):
        ang = (a / float(samples)) * math.pi * 2.0
        x = int(round(centro[0] + math.cos(ang) * r))
        z = int(round(centro[1] + math.sin(ang) * r))
        if not pts or pts[-1] != (x, z):
            pts.append((x, z))
    ring = []
    for i in range(len(pts)):
        x, z = pts[i]
        nx, nz = pts[(i + 1) % len(pts)]
        ring.append((x, z))
        while x != nx or z != nz:
            if x != nx:
                x += 1 if nx > x else -1
            elif z != nz:
                z += 1 if nz > z else -1
            ring.append((x, z))
    return ring


def main():
    print('mundo=%s  carpeta=%s  centro=%s  radio=%d  cota=%d' % (MUNDO, os.path.basename(SAVE), CENTRO, R, COTA))
    # PRIMERO, ¿ESTE MUNDO TIENE LA ALDEA? Se busca la firma del muro en TODA la columna del anillo.
    ring = anillo(CENTRO, R)
    firma = {}
    for (x, z) in ring:
        for y in range(40, 120):
            n = b(x, y, z)
            if n in ('oak_log', 'cobblestone', 'mossy_cobblestone'):
                firma.setdefault(y, 0)
                firma[y] += 1
    if not firma:
        print()
        print('NO HAY FIRMA DE MURO (troncos/adoquin) en el anillo, en NINGUNA altura de 40 a 120.')
        print('=> este mundo NO tiene la aldea construida. El mundo migrado es `run/world`; el de `run/saves`')
        print('   esta EN CRUDO (cota 63) y ahi no hay aldea.')
        return 0
    print()
    print('alturas con firma de muro (las 6 con mas celdas):')
    for y, n in sorted(firma.items(), key=lambda kv: -kv[1])[:6]:
        print('   y=%-4d %4d celdas de %d' % (y, n, len(ring)))
    y0 = max(firma, key=lambda k: firma[k])
    print()
    print('=> LA BASE DEL MURO ES y=%d (%d de %d celdas)' % (y0, firma[y0], len(ring)))
    # Y AHORA, CELDA A CELDA: muro (tronco/adoquin a la base o encima), porton (entrada declarada) o AGUJERO.
    muro = 0
    agujeros = []
    for (x, z) in ring:
        capas = [b(x, y0 + dy, z) for dy in (0, 1, 2)]
        if any(c in ('oak_log', 'cobblestone', 'mossy_cobblestone') for c in capas):
            muro += 1
        else:
            agujeros.append((x, z, capas))
    # Las entradas cardinales que el generador abre a proposito: (centro.x +- r, centro.z) y (centro.x, centro.z +- r).
    portones = {(CENTRO[0] + R, CENTRO[1]), (CENTRO[0] - R, CENTRO[1]),
                (CENTRO[0], CENTRO[1] + R), (CENTRO[0], CENTRO[1] - R)}
    print('anillo: con muro %d | SIN muro %d (%.1f%%)' % (muro, len(agujeros), 100.0 * len(agujeros) / len(ring)))
    print()
    print('%-8s %8s  %s' % ('RUMBO', 'agujeros', 'ejemplo (celda: capa base / +1 / +2)'))
    nombres = ['ESTE', 'SURESTE', 'SUR', 'SUROESTE', 'OESTE', 'NOROESTE', 'NORTE', 'NORESTE']
    por_rumbo = {}
    for (x, z, capas) in agujeros:
        ang = math.degrees(math.atan2(z - CENTRO[1], x - CENTRO[0])) % 360.0
        por_rumbo.setdefault(int(round(ang / 45.0)) % 8, []).append((x, z, capas))
    for rumbo in sorted(por_rumbo, key=lambda k: -len(por_rumbo[k])):
        v = por_rumbo[rumbo]
        x, z, capas = v[0]
        print('%-8s %8d  (%d,%d): %s / %s / %s' % (nombres[rumbo], len(v), x, z, capas[0], capas[1], capas[2]))
    print()
    # ¿ALGUN AGUJERO ES UN PORTON DECLARADO? (el generador abre cuatro a proposito)
    enPorton = [(x, z) for (x, z, _) in agujeros if (x, z) in portones]
    print('de los agujeros, los que caen EXACTAMENTE en una entrada cardinal declarada: %d %s'
          % (len(enPorton), enPorton if enPorton else '(ninguno)'))
    # Y las rachas de agujeros seguidos: una racha larga es un boquete, no una rendija.
    rachas = []
    actual = []
    for (x, z, _) in agujeros:
        if actual and abs(x - actual[-1][0]) + abs(z - actual[-1][1]) == 1:
            actual.append((x, z))
        else:
            if actual:
                rachas.append(actual)
            actual = [(x, z)]
    if actual:
        rachas.append(actual)
    rachas.sort(key=len, reverse=True)
    print('rachas de agujeros seguidos (las 6 mas largas): %s' % [len(r) for r in rachas[:6]])
    print()
    print('LECTURA: los agujeros que NO estan en las cuatro entradas cardinales son los que hay que CERRAR para que el')
    print('asedio tenga que abrir brecha (decision del jugador, 6-oct-2026).')
    return 0


if __name__ == '__main__':
    sys.exit(main())
