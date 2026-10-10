"""AUDITORIA de las aldeas del guardado: busca INCONSISTENCIAS reales en lo que hay construido.

VERSIONADO en `tools/`. Antes vivia en `build/audita_aldea2.py` (que estaba clavado a la aldea 2 y se
perdia con `gradlew clean`). Ahora saca las aldeas DEL PROPIO GUARDADO (`data/devilrpg_villages.dat`):
indice de objetivo, centro, cota y si esta caida.

Comprueba, bloque a bloque y con sus PROPIEDADES:
  A) faroles SIN APOYO (ni colgados de algo solido encima, ni sobre una cadena de apoyo que llegue al suelo)
  B) vallas y puertas de valla FLOTANDO (con aire debajo)
  C) cofres TAPADOS (un bloque solido encima: no se pueden abrir)
  D) puertas INCOMPLETAS (sin su mitad de arriba o de abajo)
  E) camas INCOMPLETAS (sin cabecera o sin pie)
  F) PORTONES con el hueco TAPADO en la capa de la cabeza (I54: con algo solido ahi, el que cruza no pasa y se
     queda encerrado; el porton del gallinero no se mira: es un hueco de un bloque a proposito)

Uso (desde la raiz del proyecto; tambien vale desde cualquier sitio):
  python tools\\audita_aldea.py                        -> TODAS las aldeas vivas del guardado por defecto
  python tools\\audita_aldea.py --aldea 2 --aldea 0    -> solo esas (el numero es el indice de objetivo)
  python tools\\audita_aldea.py --caidas               -> incluye tambien las aldeas caidas
  python tools\\audita_aldea.py --save "run/saves/Otro"
  python tools\\audita_aldea.py --centro 1414 1414 --cota 119 [--radio 76]   -> una a mano, sin leer el guardado
  python tools\\audita_aldea.py --resumen              -> solo la tabla final

Sale con codigo 1 si encuentra algo (para poder encadenarlo a un commit o a una tarea).
"""
import argparse
import glob
import os
import struct
import sys
import zlib
from collections import Counter, defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from nbtdump import R, payload, load   # noqa: E402

PROYECTO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
def _guardado_por_defecto():
    """El guardado MAS NUEVO de `run/saves` (I250, 9-oct-2026).

    Antes esto era el nombre escrito a mano `New World (1)` y **caduco**: el jugador renombro su partida y la
    herramienta dejo de funcionar (`no encuentro el guardado ...New World (1)` ✗). Buscarlo es lo robusto: si hay una
    sola partida, esa; si hay varias, la que se toco por ultima vez. Se puede seguir eligiendo a mano con `--save`.
    """
    base = os.path.join('run', 'saves')
    if os.path.isdir(base):
        candidatos = [os.path.join(base, d) for d in os.listdir(base) if os.path.isdir(os.path.join(base, d))]
        if candidatos:
            return max(candidatos, key=os.path.getmtime)
    return os.path.join(base, 'New World')


SAVE_POR_DEFECTO = _guardado_por_defecto()
RADIO_POR_DEFECTO = 76

VACIO = {None, 'air', 'cave_air', 'void_air', 'water', 'short_grass', 'tall_grass', 'fern', 'poppy',
         'dandelion', 'snow', 'oak_leaves', 'vine', 'seagrass', 'torch', 'wall_torch', 'sunflower',
         'cornflower', 'azure_bluet', 'oxeye_daisy', 'red_tulip', 'white_tulip', 'lilac', 'rose_bush',
         'peony', 'large_fern', 'sugar_cane', 'dead_bush'}
NO_SOLIDO = VACIO | {'oak_fence', 'oak_fence_gate', 'spruce_fence', 'spruce_fence_gate', 'lantern',
                     'glass_pane', 'oak_pressure_plate', 'stone_pressure_plate', 'ladder', 'rail',
                     'oak_slab', 'dark_oak_slab', 'stone_slab', 'dirt_path', 'farmland', 'wheat',
                     'carrots', 'potatoes', 'beetroots', 'oak_door', 'dark_oak_door', 'dark_oak_stairs',
                     'oak_stairs', 'cobweb', 'tripwire', 'tripwire_hook', 'soul_lantern', 'campfire',
                     'bell', 'flower_pot', 'potted_fern', 'potted_dandelion', 'composter', 'cauldron'}
APOYO = ('oak_fence', 'spruce_fence', 'oak_fence_gate', 'spruce_fence_gate')
VALLA = ('oak_fence', 'spruce_fence', 'oak_fence_gate', 'spruce_fence_gate')
PUERTAS = ('oak_door', 'dark_oak_door', 'spruce_door')
# Lo que NO bloquea la capa de la CABEZA del carril de un portón (I54): sin caja de colisión, o una planta que se
# pisa. Un farol NO está aquí a propósito (tiene caja y es lo que tapaba el portón del corral del jugador).
NO_COLISIONA = VACIO | {'wheat', 'carrots', 'potatoes', 'beetroots', 'rail', 'redstone_wire', 'ladder',
                        'oak_sapling', 'spruce_sapling', 'tripwire', 'string', 'snow'}
# Geometría de los portones, la del código (VillageGenerator): los 12 de los bancales (4 por parcela: FARM_PLOTS,
# PLOT_WIDTH/DEPTH, en el centro de cada lado del anillo) y el del corral anexo (base - ANEXO_RADIO, lado oeste,
# mirando al camino). El del GALLINERO no se audita: es un hueco de UN bloque (los pollos pasan, los aldeanos no)
# y su valla de encima es a propósito.
FARM_PLOTS = [(-30, 14), (10, 4), (-28, 34)]
PLOT_W = PLOT_D = 9
ANEXO_DX, ANEXO_RADIO = 50, 9
# Un pilote metido en el agua NO flota: el muelle de la pesquera es una valla con agua debajo a proposito.
AGUA = {'water', 'seagrass', 'tall_seagrass', 'kelp', 'kelp_plant', 'bubble_column'}


class Aldea:
    """Una aldea a auditar: su indice de objetivo, el centro y la cota (la capa que se pisa)."""

    def __init__(self, indice, cx, cz, cota, caida=False, fuente='guardado'):
        self.indice, self.cx, self.cz, self.cota = indice, cx, cz, cota
        self.caida, self.fuente = caida, fuente

    def __str__(self):
        return 'aldea %-3s centro (%d,%d) cota %d%s' % (self.indice, self.cx, self.cz, self.cota,
                                                        ' [CAIDA]' if self.caida else '')


def portones_de_la_aldea(cx, cz, nivel):
    """(x, y, z, eje) de cada portón de valla a auditar: los 12 de los bancales y el del corral anexo.

    El eje es el de CRUCE (la valla va perpendicular): en los bancales los portones del norte y del sur se cruzan
    en Z y los del este y el oeste en X; el del corral mira al oeste y se cruza en X.
    """
    portones = []
    for plot in FARM_PLOTS:
        x0, x1 = cx + plot[0] - 1, cx + plot[0] + PLOT_W
        z0, z1 = cz + plot[1] - 1, cz + plot[1] + PLOT_D
        mx, mz = (x0 + x1) // 2, (z0 + z1) // 2
        portones.append((mx, nivel, z0, 'Z'))
        portones.append((mx, nivel, z1, 'Z'))
        portones.append((x0, nivel, mz, 'X'))
        portones.append((x1, nivel, mz, 'X'))
    portones.append((cx + ANEXO_DX - ANEXO_RADIO, nivel, cz, 'X'))   # el portón del corral
    return portones


def bloque_de_pos(p):
    """`Blueprints[].Pos` es una lista de BlockPos.asLong() empaquetados (o de listas [x,y,z])."""
    if isinstance(p, (list, tuple)):
        return int(p[0]), int(p[1]), int(p[2])
    x = (p >> 38) & 0x3FFFFFF
    z = (p >> 12) & 0x3FFFFFF
    y = p & 0xFFF
    if x >= 1 << 25:
        x -= 1 << 26
    if z >= 1 << 25:
        z -= 1 << 26
    return x, y, z


def aldeas_del_guardado(save):
    """Lee centro y cota de cada aldea del guardado (de sus planos). Devuelve [] si no puede."""
    ruta = os.path.join(save, 'data', 'devilrpg_villages.dat')
    if not os.path.isfile(ruta):
        return []
    datos = load(ruta)
    datos = datos.get('data', datos)
    caidas = set(datos.get('Fallen', []))
    aldeas = []
    for b in datos.get('Blueprints') or []:
        posiciones = b.get('Pos') or []
        if not posiciones:
            continue
        xs, ys, zs = [], [], []
        for p in posiciones:
            x, y, z = bloque_de_pos(p)
            xs.append(x)
            ys.append(y)
            zs.append(z)
        centro_x = (min(xs) + max(xs)) // 2
        centro_z = (min(zs) + max(zs)) // 2
        # La COTA (la capa que se pisa) es UNA MAS que la Y que mas se repite: el bloque que mas se coloca en una
        # aldea es el SUELO, que va en `cota - 1` (invariante I1). Comprobado en el guardado del jugador: la aldea 2
        # da modo 119 y su taberna esta a cota 120 (forjado en 124, escalones en 122..124).
        cota = Counter(ys).most_common(1)[0][0] + 1
        indice = b.get('Index')
        aldeas.append(Aldea(indice, centro_x, centro_z, cota, indice in caidas))
    return sorted(aldeas, key=lambda a: (a.indice is None, a.indice))


class Mundo:
    """Lector de bloques del guardado (paleta por chunk, con cache)."""

    def __init__(self, save):
        self.region = {}
        for path in sorted(glob.glob(os.path.join(save, 'region', '*.mca'))):
            n = os.path.basename(path).split('.')
            self.region[(int(n[1]), int(n[2]))] = path
        self.cache = {}

    def paleta(self, ccx, ccz):
        if (ccx, ccz) in self.cache:
            return self.cache[(ccx, ccz)]
        out = {}
        path = self.region.get((ccx >> 5, ccz >> 5))
        if path:
            with open(path, 'rb') as f:
                raw = f.read()
            if len(raw) >= 8192:
                i = (ccx & 31) + (ccz & 31) * 32
                off = struct.unpack_from('>I', b'\x00' + raw[i * 4:i * 4 + 3])[0]
                if off and raw[i * 4 + 3]:
                    p0 = off * 4096
                    ln = struct.unpack_from('>I', raw, p0)[0]
                    comp = raw[p0 + 4]
                    try:
                        data = zlib.decompress(raw[p0 + 5:p0 + 4 + ln]) if comp == 2 else None
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
                            bits = max(4, (len(pal) - 1).bit_length())
                            vpl = 64 // bits
                            mask = (1 << bits) - 1
                            for j in range(4096):
                                ip = 0 if ds is None else ((ds[j // vpl] >> ((j % vpl) * bits)) & mask
                                                           if j // vpl < len(ds) else 0)
                                pp = pal[ip] if 0 <= ip < len(pal) else None
                                if isinstance(pp, dict) and pp.get('Name'):
                                    out[(ccx * 16 + (j & 15), y0 * 16 + ((j >> 8) & 15),
                                         ccz * 16 + ((j >> 4) & 15))] = pp
        self.cache[(ccx, ccz)] = out
        return out

    def bloque(self, x, y, z):
        return self.paleta(x >> 4, z >> 4).get((x, y, z))


def auditar(mundo, aldea, radio, callar_detalle=False):
    """Devuelve (celdas, hallazgos) con los cinco apartados ya contados."""
    cx, cz, nivel = aldea.cx, aldea.cz, aldea.cota

    def nombre(x, y, z):
        b = mundo.bloque(x, y, z)
        return None if not b else b['Name'].replace('minecraft:', '')

    def prop(x, y, z, clave):
        b = mundo.bloque(x, y, z)
        return None if not b else (b.get('Properties') or {}).get(clave)

    def solido(x, y, z):
        n = nombre(x, y, z)
        return n is not None and n not in NO_SOLIDO

    def apoya(x, y, z):
        """¿Ese bloque puede sostener un farol encima? (solido o valla)"""
        n = nombre(x, y, z)
        return n in APOYO or (n is not None and n not in NO_SOLIDO)

    celdas = {}
    for x in range(cx - radio, cx + radio + 1):
        for z in range(cz - radio, cz + radio + 1):
            for y in range(nivel - 8, nivel + 24):
                celdas[(x, y, z)] = nombre(x, y, z)

    rel = lambda x, y, z: (x - cx, y - nivel, z - cz)   # noqa: E731
    hallazgos = {}

    # --- A) FAROLES SIN APOYO ---------------------------------------------------------------------
    flotantes = []
    for (x, y, z), n in sorted(celdas.items()):
        if n != 'lantern':
            continue
        if solido(x, y + 1, z) or apoya(x, y - 1, z):
            continue
        hueco = 0
        for dy in range(2, 6):
            if apoya(x, y - dy, z):
                break
            hueco += 1
        flotantes.append((x, y, z, hueco, nombre(x, y - 2 - hueco, z)))
    hallazgos['faroles'] = flotantes
    if not callar_detalle:
        print('=== A) FAROLES SIN APOYO: %d ===' % len(flotantes))
        grupos = defaultdict(list)
        for x, y, z, hueco, apo in flotantes:
            grupos[(apo, hueco)].append(rel(x, y, z))
        for (apo, hueco), lista in sorted(grupos.items(), key=lambda kv: -len(kv[1])):
            print('  --- %d farol(es) a %d bloque(s) de aire sobre un apoyo de %s ---'
                  % (len(lista), hueco + 1, apo))
            for rx, ry, rz in sorted(lista):
                print('      rel plaza (%+4d,%+3d,%+4d)' % (rx, ry, rz))

    # --- B) VALLAS FLOTANDO -----------------------------------------------------------------------
    vallas = []
    for (x, y, z), n in sorted(celdas.items()):
        if n not in VALLA:
            continue
        debajo = nombre(x, y - 1, z)
        if solido(x, y - 1, z) or debajo in VALLA + ('oak_log', 'dark_oak_log') or debajo in AGUA:
            continue
        hueco = 0
        for dy in range(2, 8):
            if solido(x, y - dy, z) or nombre(x, y - dy, z) in ('oak_fence', 'spruce_fence') + tuple(AGUA):
                break
            hueco += 1
        vallas.append((x, y, z, hueco))
    hallazgos['vallas'] = vallas
    if not callar_detalle:
        print('=== B) VALLAS FLOTANDO: %d ===' % len(vallas))
        for x, y, z, hueco in vallas:
            print('    (%d,%d,%d) rel plaza (%+d,%+d,%+d)  aire debajo: %d' % ((x, y, z) + rel(x, y, z) + (hueco,)))

    # --- C) COFRES TAPADOS ------------------------------------------------------------------------
    tapados = []
    for (x, y, z), n in sorted(celdas.items()):
        if 'chest' not in (n or ''):
            continue
        if solido(x, y + 1, z):
            tapados.append((x, y, z, nombre(x, y + 1, z)))
    hallazgos['cofres'] = tapados
    if not callar_detalle:
        print('=== C) COFRES TAPADOS: %d ===' % len(tapados))
        for x, y, z, enc in tapados:
            print('    (%d,%d,%d) rel plaza (%+d,%+d,%+d)  encima: %s' % ((x, y, z) + rel(x, y, z) + (enc,)))

    # --- D) PUERTAS INCOMPLETAS -------------------------------------------------------------------
    puertas = defaultdict(dict)
    for (x, y, z), n in celdas.items():
        if n in PUERTAS:
            puertas[(x, z)][y] = (n, prop(x, y, z, 'half'))
    incompletas = 0
    if not callar_detalle:
        print('=== D) PUERTAS INCOMPLETAS ===')
    for (x, z), ys in sorted(puertas.items()):
        for y, (n, half) in sorted(ys.items()):
            if half == 'lower' and (y + 1) not in ys:
                incompletas += 1
                if not callar_detalle:
                    print('    puerta sin mitad de ARRIBA en (%d,%d,%d) rel plaza (%+d,%+d,%+d)'
                          % ((x, y, z) + rel(x, y, z)))
            if half == 'upper' and (y - 1) not in ys:
                incompletas += 1
                if not callar_detalle:
                    print('    puerta sin mitad de ABAJO en (%d,%d,%d) rel plaza (%+d,%+d,%+d)'
                          % ((x, y, z) + rel(x, y, z)))
    hallazgos['puertas'] = incompletas
    if not callar_detalle:
        print('  puertas incompletas: %d (de %d puertas)' % (incompletas, len(puertas)))

    # --- E) CAMAS INCOMPLETAS ---------------------------------------------------------------------
    camas = {}
    for (x, y, z), n in celdas.items():
        if n and n.endswith('_bed'):
            camas[(x, y, z)] = prop(x, y, z, 'part')
    sueltas = 0
    if not callar_detalle:
        print('=== E) CAMAS SUELTAS ===')
    for (x, y, z), part in sorted(camas.items()):
        if part != 'head':
            continue
        vecinos = [nombre(x + dx, y, z + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))]
        if not any(v and v.endswith('_bed') for v in vecinos):
            sueltas += 1
            if not callar_detalle:
                print('    cabecera de cama SUELTA en (%d,%d,%d) rel plaza (%+d,%+d,%+d)'
                      % ((x, y, z) + rel(x, y, z)))
    hallazgos['camas'] = sueltas
    if not callar_detalle:
        print('  camas sueltas: %d (de %d mitades)' % (sueltas, len(camas)))

    # --- F) PORTONES CON EL HUECO TAPADO (I54) -----------------------------------------------------
    # El hueco de un portón de valla son TRES celdas (la hoja y las dos de al lado) y el aldeano mide 1,95: si en la
    # capa de la CABEZA hay algo con caja de colisión, no se puede cruzar y se queda encerrado (el jugador: "el
    # ganadero quiere ir a la taberna y no puede, la única salida está obstruida por una lámpara"). La geometría de
    # los portones es la del código (VillageGenerator: los 12 de los bancales, el del corral y el del gallinero);
    # el del GALLINERO se salta: es un hueco de UN bloque (los pollos pasan, los aldeanos no) y su valla de encima
    # es a propósito.
    tapados = []
    for (x, y, z, eje) in portones_de_la_aldea(cx, cz, nivel):
        if nombre(x, y, z) != 'oak_fence_gate':
            continue
        for d in (-1, 0, 1):
            px = x + (d if eje == 'X' else 0)
            pz = z + (0 if eje == 'Z' else d)
            encima = nombre(px, y + 1, pz)
            if encima is not None and encima not in NO_COLISIONA:
                tapados.append((x, y, z, px, y + 1, pz, encima))
    hallazgos['portones'] = tapados
    if not callar_detalle:
        print('=== F) PORTONES CON EL HUECO TAPADO: %d ===' % len(tapados))
        for x, y, z, px, py, pz, n in tapados:
            print('    portón en (%d,%d,%d) rel plaza (%+d,%+d,%+d): la cabeza del carril (%d,%d,%d) la tapa %s'
                  % ((x, y, z) + rel(x, y, z) + (px, py, pz, n)))

    return celdas, hallazgos


def main(argv=None):
    ap = argparse.ArgumentParser(description='Auditoria de las aldeas del guardado (faroles y vallas '
                                             'flotando, cofres tapados, puertas incompletas, camas sueltas).')
    ap.add_argument('--save', default=SAVE_POR_DEFECTO, help='carpeta del guardado (por defecto %s)'
                                                             % SAVE_POR_DEFECTO)
    ap.add_argument('--aldea', type=int, action='append', default=None,
                    help='indice de objetivo de la aldea (repetible). Por defecto: todas las vivas')
    ap.add_argument('--caidas', action='store_true', help='auditar tambien las aldeas caidas')
    ap.add_argument('--centro', type=int, nargs=2, metavar=('X', 'Z'),
                    help='auditar una aldea a mano (con --cota), sin leer el guardado')
    ap.add_argument('--cota', type=int, help='cota (capa que se pisa) de la aldea de --centro')
    ap.add_argument('--radio', type=int, default=RADIO_POR_DEFECTO, help='radio a barrer (por defecto %d)'
                                                                        % RADIO_POR_DEFECTO)
    ap.add_argument('--resumen', action='store_true', help='solo la tabla final, sin el detalle')
    args = ap.parse_args(argv)

    save = args.save if os.path.isabs(args.save) else os.path.join(PROYECTO, args.save)
    if not os.path.isdir(save):
        print('ERROR: no encuentro el guardado %s' % save)
        return 2

    if args.centro:
        if args.cota is None:
            print('ERROR: con --centro hace falta --cota')
            return 2
        aldeas = [Aldea('?', args.centro[0], args.centro[1], args.cota, fuente='a mano')]
    else:
        aldeas = aldeas_del_guardado(save)
        if not aldeas:
            print('ERROR: no pude leer las aldeas de %s (mira que exista data/devilrpg_villages.dat)'
                  % os.path.join(save, 'data'))
            return 2
        if args.aldea is not None:
            aldeas = [a for a in aldeas if a.indice in args.aldea]
        if not args.caidas:
            aldeas = [a for a in aldeas if not a.caida]
    if not aldeas:
        print('Nada que auditar (¿las aldeas que pediste estan caidas? prueba --caidas)')
        return 2

    print('guardado: %s' % save)
    print('aldeas a auditar: %d' % len(aldeas))
    mundo = Mundo(save)
    filas = []
    problemas = 0
    for aldea in aldeas:
        print()
        print('#' * 100)
        print('## %s' % aldea)
        print('#' * 100)
        celdas, h = auditar(mundo, aldea, args.radio, callar_detalle=args.resumen)
        no_aire = sum(1 for v in celdas.values() if v)
        if not args.resumen:
            print('  (%d bloques no-aire en el recuadro)' % no_aire)
        fila = (str(aldea), len(h['faroles']), len(h['vallas']), len(h['cofres']), h['puertas'], h['camas'],
                len(h['portones']))
        filas.append(fila)
        problemas += sum(fila[1:])

    print()
    print('=' * 100)
    print('RESUMEN   aldea | faroles | vallas | cofres tapados | puertas incompletas | camas sueltas | portones tapados')
    for f in filas:
        print('  %-46s %5d %7d %8d %8d %8d %8d' % f)
    print('  TOTAL de cosas mal: %d' % problemas)
    return 1 if problemas else 0


if __name__ == '__main__':
    sys.exit(main())
