"""LA COLUMNA DE LA MINA: que hay, celda a celda, desde la boca hacia abajo.

Uso: python tools\\arnes\\columna_mina.py [mundo] [xEje] [zEje] [cota] [simular]

  (por defecto: 'New World (2)', el eje 503,617 de la aldea 0, la cota 63 y SIN simular)

Es el instrumento que pidio el jugador para el caso *"el minero no esta bajando y esta sellada la
entrada"*: en vez de mirar la boca (que ya se midio ABIERTA), recorre **el caracol entero**, paso a
paso, y dice de cada celda:

  * que hay **en la celda de la pieza** (y si es la pieza que el plano de la mina espera ahi),
  * que hay **encima** (el hueco de paso: pies y cabeza),
  * si el paso esta **PISABLE** con la misma regla que el juego (aire a los pies y a la cabeza,
    suelo firme debajo) o **TAPADO** y con que bloque.

Despues, la prueba de verdad: un **recorrido en anchura** (el modelo de `ruta_atasco.py`) desde el
suelo de al lado de la caseta hasta la casilla de pie del ultimo paso hecho. Si NO hay ruta, dice
**hasta donde llega** (la cota mas honda alcanzada), que es donde se corta el paso.

Y con `simular` hace DOS cosas mas, que son las que deciden la reparacion SIN gastar una corrida:

  1. **EL MARCO QUE SE TAPA A SI MISMO**: de cada paso que lleva marco de madera (cada 16), mira si
     sus postes caen en una celda de paso de la mina (en las esquinas del anillo, una de las
     "paredes" es otra celda del caracol). Lista los que si.
  2. **LA REPARACION**: aplica en memoria lo que hace `VillageGenerator.despejarElPozoDeLaMina`
     (quitar de las celdas de paso el terreno del pueblo y los postes del marco, SOLO en lo que el
     minero ya cavo) y **vuelve a buscar la ruta**. Si aparece ruta, el reparador abre el pozo.
"""
import glob
import os
import struct
import sys
import zlib
from collections import deque

RAIZ = r'C:\Users\Christian\Documents\DevilRpg'
MUNDO = sys.argv[1] if len(sys.argv) > 1 else 'New World (2)'
EJE_X = int(sys.argv[2]) if len(sys.argv) > 2 else 503
EJE_Z = int(sys.argv[3]) if len(sys.argv) > 3 else 617
COTA = int(sys.argv[4]) if len(sys.argv) > 4 else 63
SIMULAR = len(sys.argv) > 5 and sys.argv[5].lower().startswith('sim')
SAVE = os.path.join(RAIZ, 'run', 'saves', MUNDO)
if not os.path.isdir(SAVE):
    SAVE = os.path.join(RAIZ, 'run', MUNDO)
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


CAMBIOS = {}


def b(x, y, z):
    if (x, y, z) in CAMBIOS:
        return CAMBIOS[(x, y, z)]
    return chunk(x >> 4, z >> 4).get((x, y, z), ('air', {}))[0]


# --- el caracol, con las MISMAS cuentas que VillageGenerator -------------------------------------
RADIO = 4


def anillo():
    celdas = []
    for dz in range(-RADIO, RADIO):
        celdas.append((RADIO, dz))
    for dx in range(RADIO, -RADIO, -1):
        celdas.append((dx, RADIO))
    for dz in range(RADIO, -RADIO, -1):
        celdas.append((-RADIO, dz))
    for dx in range(-RADIO, RADIO):
        celdas.append((dx, -RADIO))
    return celdas


ANILLO = anillo()
SOPORTE_CADA = 16
GALERIA_CADA = 8
GALERIA_LARGO = 24
FONDO = -58


def y_del_caracol(paso):
    return (COTA - 1) - (paso + 1) // 2


def celda(paso):
    d = ANILLO[paso % len(ANILLO)]
    return (EJE_X + d[0], y_del_caracol(paso), EJE_Z + d[1])


def pieza(paso):
    return 'cobblestone_slab' if paso % 2 == 0 else 'cobblestone'


def lleva_soporte(paso):
    return paso > 0 and paso % SOPORTE_CADA == 0


def abre_galeria(paso):
    return paso > 0 and paso % (GALERIA_CADA * 2) == 0


def direccion_galeria(paso):
    d = ANILLO[paso % len(ANILLO)]
    if abs(d[1]) >= abs(d[0]):
        return (0, 1) if d[1] > 0 else (0, -1)
    return (1, 0) if d[0] > 0 else (-1, 0)


def celda_galeria(paso, indice):
    c = celda(paso)
    d = direccion_galeria(paso)
    return (c[0] + d[0] * indice, c[1], c[2] + d[1] * indice)


def lados(paso):
    """Las dos celdas del radio (las 'paredes') de una celda del caracol: la misma cuenta que
    `VillageGenerator.ladosDeLaCelda`."""
    d = ANILLO[paso % len(ANILLO)]
    if abs(d[0]) >= abs(d[1]):
        sx, sz = (1 if d[0] > 0 else -1), 0
    else:
        sx, sz = 0, (1 if d[1] > 0 else -1)
    c = celda(paso)
    return [(c[0] + sx, c[1], c[2] + sz), (c[0] - sx, c[1], c[2] - sz)]


MAXIMO = 2 * max(0, (COTA - 1) - FONDO)

# Las celdas de PASO de toda la mina (las que se andan): pies y cabeza de cada paso del caracol y la
# celda de cada galeria con la de encima.
PASO = set()
for p in range(MAXIMO):
    c = celda(p)
    PASO.add((c[0], c[1] + 1, c[2]))
    PASO.add((c[0], c[1] + 2, c[2]))
    if abre_galeria(p):
        for i in range(1, GALERIA_LARGO + 1):
            g = celda_galeria(p, i)
            PASO.add(g)
            PASO.add((g[0], g[1] + 1, g[2]))

POSTES = set()
for p in range(SOPORTE_CADA, MAXIMO, SOPORTE_CADA):
    for (x, y, z) in lados(p):
        POSTES.add((x, y + 1, z))
        POSTES.add((x, y + 2, z))

TERRENO = {'grass_block', 'dirt', 'coarse_dirt', 'rooted_dirt', 'podzol', 'mycelium', 'stone',
           'cobblestone', 'gravel', 'andesite', 'granite', 'diorite', 'tuff', 'sand', 'red_sand',
           'sandstone', 'clay', 'snow_block', 'dirt_path', 'cobbled_deepslate'}

# --- la regla del juego para "se puede estar de pie aqui" ---------------------------------------
SIN_CHOQUE = ('air', 'cave_air', 'void_air', 'water', 'short_grass', 'grass', 'fern', 'tall_grass',
              'poppy', 'dandelion', 'torch', 'wall_torch', 'redstone_torch', 'ladder', 'rail',
              'sunflower', 'lilac', 'rose_bush', 'peony', 'sugar_cane', 'wheat', 'carrots',
              'potatoes', 'beetroots', 'oak_sapling', 'birch_sapling', 'spruce_sapling',
              'dirt_path', 'wheat_seeds')


def libre(x, y, z):
    return b(x, y, z) in SIN_CHOQUE


def pisable(x, y, z):
    return libre(x, y, z) and libre(x, y + 1, z) and not libre(x, y - 1, z)


def que_tapa(x, y, z):
    if not libre(x, y, z):
        return 'pies'
    if not libre(x, y + 1, z):
        return 'cabeza'
    if libre(x, y - 1, z):
        return 'sin suelo'
    return ''


print('COLUMNA DE LA MINA · mundo=%s · eje=%d,%d · cota=%d · boca=%s'
      % (MUNDO, EJE_X, EJE_Z, COTA, celda(0)))
print()
print('%-5s %-13s %-16s %-16s %-16s %-16s %s'
      % ('paso', 'celda pieza', 'pieza', 'pies', 'cabeza', '+1', 'veredicto'))

primerTapon = None
ultimoHecho = None
for paso in range(0, min(MAXIMO, 80)):
    c = celda(paso)
    real = b(*c)
    esperada = pieza(paso)
    encima = [b(c[0], c[1] + dy, c[2]) for dy in (1, 2, 3)]
    veredicto = 'PISABLE'
    tapa = que_tapa(c[0], c[1] + 1, c[2])
    if tapa:
        bloque = b(*c) if tapa == 'sin suelo' else b(c[0], c[1] + (1 if tapa == 'pies' else 2), c[2])
        veredicto = 'TAPADO por %s (%s)' % (tapa, bloque)
        if primerTapon is None and real == esperada:
            primerTapon = (paso, c, veredicto)
    marca = '=pieza' if real == esperada else ('<-- FALTA (hay %s)' % real)
    print('%-5d %-13s %-16s %-16s %-16s %-16s %s%s'
          % (paso, '%d,%d,%d' % c, '%s %s' % (esperada, marca), encima[0], encima[1], encima[2],
             veredicto, ' · ABRE GALERIA' if abre_galeria(paso) else ''))
    if real == esperada:
        ultimoHecho = paso
    if real != esperada:
        break

# --- LA GALERIA del primer paso que la abre -----------------------------------------------------
pasoGaleria = next((p for p in range(1, min(MAXIMO, 80)) if abre_galeria(p)), None)
if pasoGaleria is not None:
    print()
    print('GALERIA del paso %d (direccion %s, %d celdas):'
          % (pasoGaleria, direccion_galeria(pasoGaleria), GALERIA_LARGO))
    hechas = 0
    for i in range(1, GALERIA_LARGO + 1):
        g = celda_galeria(pasoGaleria, i)
        estado = b(*g)
        arriba = (b(g[0], g[1] + 1, g[2]), b(g[0], g[1] + 2, g[2]))
        if estado == 'air':
            hechas += 1
        print('  celda %-3d %-13s suelo=%-16s pies=%-14s cabeza=%-14s %s'
              % (i, '%d,%d,%d' % g, estado, arriba[0], arriba[1],
                 'ABIERTA' if estado == 'air' else 'PENDIENTE'))
    print('  galeria hecha: %d de %d' % (hechas, GALERIA_LARGO))

# --- EL MARCO QUE SE TAPA A SI MISMO ------------------------------------------------------------
print()
print('MARCOS DE MADERA (cada %d pasos): ¿cae algun poste en una celda de PASO de la mina?' % SOPORTE_CADA)
bloqueados = 0
for p in range(SOPORTE_CADA, MAXIMO, SOPORTE_CADA):
    caen = [(x, y, z) for (x, y, z) in
            [(l[0], l[1] + dy, l[2]) for l in lados(p) for dy in (1, 2)] if (x, y, z) in PASO]
    if caen:
        bloqueados += 1
        print('  paso %-3d celda %-13s -> postes EN EL PASO: %s  · paso vecino tapado: %s'
              % (p, '%d,%d,%d' % celda(p), caen,
                 [q for q in PASO if q[0] == caen[0][0] and q[2] == caen[0][2]
                  and abs(q[1] - caen[0][1]) <= 2]))
print('  paso(s) con el marco dentro del paso: %d de %d' % (bloqueados, MAXIMO // SOPORTE_CADA))


def buscar_ruta(inicio, meta, radio=60):
    prev = {inicio: None}
    cola = deque([inicio])
    while cola:
        x, y, z = cola.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if abs(nx - EJE_X) > radio or abs(nz - EJE_Z) > radio:
                continue
            for dy in (0, 1, -1, -2):
                ny = y + dy
                if not pisable(nx, ny, nz) or (nx, ny, nz) in prev:
                    continue
                prev[(nx, ny, nz)] = (x, y, z)
                cola.append((nx, ny, nz))
    return prev


def informar(prev, meta, inicio):
    if meta not in prev:
        ys = sorted({y for (_, y, _) in prev})
        print('  NO HAY RUTA (casillas visitadas: %d) · cotas alcanzadas: %d..%d (la meta esta en y=%d)'
              % (len(prev), min(ys), max(ys), meta[1]))
        return False
    camino = []
    p = meta
    while p:
        camino.append(p)
        p = prev[p]
    camino.reverse()
    print('  HAY RUTA: %d pasos · por: %s%s' % (len(camino) - 1, camino[:10],
                                                ' ...' if len(camino) > 10 else ''))
    return True


print()
if ultimoHecho is None:
    print('RUTA: no hay ni una celda del caracol hecha.')
    sys.exit(0)
meta = (celda(ultimoHecho)[0], celda(ultimoHecho)[1] + 1, celda(ultimoHecho)[2])
inicio = (EJE_X - 4, COTA, EJE_Z + 4)  # el suelo de al lado de la caseta (donde se queda el minero)
print('RUTA: de %s a la casilla de pie del paso %d %s' % (inicio, ultimoHecho, meta))
if not pisable(*inicio):
    print('  OJO: el inicio NO es pisable (%s / %s / %s)'
          % (b(inicio[0], inicio[1] - 1, inicio[2]), b(*inicio), b(inicio[0], inicio[1] + 1, inicio[2])))
if not pisable(*meta):
    print('  OJO: la meta NO es pisable (%s / %s / %s)'
          % (b(meta[0], meta[1] - 1, meta[2]), b(*meta), b(meta[0], meta[1] + 1, meta[2])))
print(' ANTES (el mundo tal cual):')
informar(buscar_ruta(inicio, meta), meta, inicio)

if SIMULAR:
    print()
    print('REPARACION SIMULADA (`despejarElPozoDeLaMina`), celda a celda:')
    cambios = []
    for p in range(MAXIMO):
        c = celda(p)
        if b(*c) != pieza(p):
            break  # aqui ya no hay mina: lo que venga no lo ha cavado el minero
        for dy in (1, 2, 3):
            q = (c[0], c[1] + dy, c[2])
            if b(*q) in TERRENO:
                CAMBIOS[q] = 'air'
                cambios.append(('terreno', p, q, b(*q)))
            elif b(*q) in ('oak_log', 'oak_planks') and q in POSTES:
                CAMBIOS[q] = 'air'
                cambios.append(('marco', p, q, b(*q)))
        if abre_galeria(p):
            hechas = 0
            for i in range(1, GALERIA_LARGO + 1):
                if b(*celda_galeria(p, i)) == 'air':
                    hechas += 1
                else:
                    break
            for i in range(1, hechas + 1):
                g = celda_galeria(p, i)
                for dy in (0, 1):
                    q = (g[0], g[1] + dy, g[2])
                    if b(*q) in TERRENO:
                        CAMBIOS[q] = 'air'
                        cambios.append(('terreno', p, q, b(*q)))
    if not cambios:
        print('  no hay nada que despejar (el pozo ya esta abierto)')
    for tipo, p, q, bloque in cambios:
        print('  paso %-3d %-9s %-15s quitado %s' % (p, tipo, '%d,%d,%d' % q, bloque))
    print('  total: %d celda(s)' % len(cambios))
    print(' DESPUES (con el reparador):')
    informar(buscar_ruta(inicio, meta), meta, inicio)
