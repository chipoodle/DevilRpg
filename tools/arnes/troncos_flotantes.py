"""TRONCOS FLOTANTES: los que no llegan al suelo por troncos (los restos del hachazo).

Un arbol de verdad SIEMPRE llega al suelo por sus troncos (la base esta en la tierra). Un trozo que quedo
colgando (porque el arbol torcia, porque lo dejo a medias el hachazo, o porque le quitaron las hojas) NO.
Es la misma prueba que usa el lenador (`VillagerLumberjackGoal.tieneApoyo`).

Uso: python build\\troncos_flotantes.py [mundo] [radio]
"""
import glob
import os
import struct
import sys
import zlib
from collections import deque

RAIZ = r'C:\Users\Christian\Documents\DevilRpg'
MUNDO = sys.argv[1] if len(sys.argv) > 1 else 'New World (2)'
RADIO = int(sys.argv[2]) if len(sys.argv) > 2 else 110
SAVE = (os.path.join(RAIZ, MUNDO) if ('/' in MUNDO or '\\' in MUNDO)
        else os.path.join(RAIZ, 'run', 'saves', MUNDO))
CENTRO = (470, 646)
COTA = 63
ARBOLEDA_X0, ARBOLEDA_X1 = -28, -8   # relativo al centro (aprox: el hueco de cesped de la arboleda)
ARBOLEDA_Z0, ARBOLEDA_Z1 = -31, -11
MURO = 62
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
    return chunk(x >> 4, z >> 4).get((x, y, z), ('air', {}))


def es_tronco(n):
    return n.endswith('_log') or n.endswith('_wood') or n in ('log', 'wood')


def es_tierra(n):
    """Como el mod (`VillagerLumberjackGoal.esTierra`): tierra de verdad (dirt/grass/podzol/... y arena)."""
    return n in ('dirt', 'grass_block', 'coarse_dirt', 'podzol', 'mycelium', 'rooted_dirt',
                 'moss_block', 'mud', 'sand', 'red_sand')


def es_terreno_natural(n):
    """Como `VillageGenerator.esTerrenoNatural`: aire/tierra/piedra/arena/agua/hojas/OTROS TRONCOS... no es construccion."""
    if n in ('air', 'cave_air', 'void_air', 'water', 'lava', 'short_grass', 'tall_grass', 'fern', 'dead_bush',
             'dandelion', 'poppy', 'sugar_cane', 'sweet_berry_bush', 'brown_mushroom', 'red_mushroom',
             'oak_sapling', 'spruce_sapling', 'birch_sapling', 'jungle_sapling', 'acacia_sapling',
             'dark_oak_sapling', 'mangrove_propagule', 'cherry_sapling', 'snow', 'vine', 'glow_lichen'):
        return True
    if es_tronco(n) or 'leaves' in n:
        return True
    if n.endswith('_ore'):
        return True
    if n in ('stone', 'deepslate', 'granite', 'diorite', 'andesite', 'tuff', 'calcite', 'basalt',
             'blackstone', 'smooth_basalt', 'sandstone', 'red_sandstone', 'terracotta', 'clay',
             'ice', 'packed_ice', 'blue_ice', 'snow_block'):
        return True
    if n.endswith('_terracotta'):
        return True
    if n in ('dirt', 'grass_block', 'coarse_dirt', 'podzol', 'mycelium', 'rooted_dirt', 'moss_block',
             'dirt_path', 'farmland', 'gravel', 'clay', 'nylium', 'soul_sand', 'soul_soil'):
        return True
    if n.endswith('_log') or n.endswith('_wood') or n.endswith('_stem') or n.endswith('_hyphae'):
        return True
    if n.startswith('cave_vines') or n.startswith('mangrove_') or n.startswith('azalea') \
            or n.startswith('flowering_azalea') or n.startswith('big_dripleaf') or n.startswith('small_dripleaf'):
        return True
    return False


def es_tronco_de_arbol(x, y, z):
    """Como el mod: tronco DE PIE y sin NADA construido pegado (3x3x3)."""
    n, props = b(x, y, z)
    if not es_tronco(n):
        return False
    if props.get('axis', 'y') != 'y':
        return False
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if dx == 0 and dy == 0 and dz == 0:
                    continue
                v = b(x + dx, y + dy, z + dz)[0]
                if v == 'air' or es_terreno_natural(v):
                    continue
                return False
    return True


def tiene_apoyo(x, y, z):
    cola = deque([(x, y, z)])
    vistos = {(x, y, z)}
    while cola and len(vistos) < 64:
        px, py, pz = cola.popleft()
        if es_tierra(b(px, py - 1, pz)[0]):
            return True
        for dy in (-1, 0):
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    if dy == 0 and dx == 0 and dz == 0:
                        continue
                    q = (px + dx, py + dy, pz + dz)
                    if q in vistos:
                        continue
                    vistos.add(q)
                    if es_tronco(b(*q)[0]):
                        cola.append(q)
    return False


def en_la_arboleda(x, z):
    return (CENTRO[0] + ARBOLEDA_X0 <= x <= CENTRO[0] + ARBOLEDA_X1
            and CENTRO[1] + ARBOLEDA_Z0 <= z <= CENTRO[1] + ARBOLEDA_Z1)


troncos = []
for x in range(CENTRO[0] - RADIO, CENTRO[0] + RADIO + 1):
    for z in range(CENTRO[1] - RADIO, CENTRO[1] + RADIO + 1):
        if (x - CENTRO[0]) ** 2 + (z - CENTRO[1]) ** 2 > RADIO * RADIO:
            continue
        for y in range(COTA - 6, COTA + 30):
            n, props = b(x, y, z)
            if es_tronco(n):
                troncos.append((x, y, z, n, props.get('axis', 'y')))

flotantes = [t for t in troncos if es_tronco_de_arbol(t[0], t[1], t[2]) and not tiene_apoyo(t[0], t[1], t[2])]
print('mundo=%s radio=%d  troncos=%d  FLOTANTES (restos de verdad, como los mira el lenador)=%d'
      % (MUNDO, RADIO, len(troncos), len(flotantes)))

dentro = [t for t in flotantes if (t[0] - CENTRO[0]) ** 2 + (t[2] - CENTRO[1]) ** 2 <= MURO * MURO]
fuera = [t for t in flotantes if t not in dentro]
arboleda = [t for t in flotantes if en_la_arboleda(t[0], t[2])]
print('  dentro de la muralla (r<=%d): %d   |  en la ARBOLEDA: %d  |  fuera: %d'
      % (MURO, len(dentro), len(arboleda), len(fuera)))

# Agrupados por columna (x,z) para ver cuantos "montones" hay
cols = {}
for x, y, z, n, ax in flotantes:
    cols.setdefault((x, z), []).append(y)
print('  columnas con restos: %d' % len(cols))
print()
print('--- DENTRO de la muralla (lo que ya limpia el lenador si esta en la arboleda) ---')
for (x, z), ys in sorted(cols.items(), key=lambda kv: (kv[0][0] - CENTRO[0]) ** 2 + (kv[0][1] - CENTRO[1]) ** 2):
    if (x - CENTRO[0]) ** 2 + (z - CENTRO[1]) ** 2 > MURO * MURO:
        continue
    r = int(((x - CENTRO[0]) ** 2 + (z - CENTRO[1]) ** 2) ** 0.5)
    print('    (%d,%d) ys=%s  r=%d  arboleda=%s' % (x, z, sorted(ys), r, en_la_arboleda(x, z)))
print()
print('--- FUERA de la muralla (el monte donde tala) ---')
for (x, z), ys in sorted(cols.items(), key=lambda kv: (kv[0][0] - CENTRO[0]) ** 2 + (kv[0][1] - CENTRO[1]) ** 2):
    if (x - CENTRO[0]) ** 2 + (z - CENTRO[1]) ** 2 <= MURO * MURO:
        continue
    r = int(((x - CENTRO[0]) ** 2 + (z - CENTRO[1]) ** 2) ** 0.5)
    print('    (%d,%d) ys=%s  r=%d' % (x, z, sorted(ys), r))
