#!/usr/bin/env python3
"""Volcado INDEPENDIENTE de los POI guardados en disco (world/poi/*.mca).

No usa Minecraft ni el arnes: lee los ficheros de region tal cual los escribe el
servidor (`SectionStorage` -> `PoiSection` -> `PoiRecord`) y saca una linea por
punto de interes, con su `free_tickets` persistido. Sirve como evidencia de
"lo que quedo en el disco" frente a lo que el arnes observo en memoria.

Uso:
    python tools/poi/poi-dump.py build/av/poi-<run>/game/world
    python tools/poi/poi-dump.py build/av/poi-<run>/game/world --tipo job
    python tools/poi/poi-dump.py build/av/poi-<run>/game/world --json

Formato de salida (texto):
    # world=... ficheros=N registros=N
    # pos|tipo|free_tickets|max_tickets|seccion
    661, 63, 533|minecraft:farm|0|1|...

`max_tickets` no se guarda por registro (solo `free_tickets`); se informa el
conocido para los tipos de puesto de trabajo de la aldea (1) y se deja 0 cuando
no se puede saber sin el registro de tipos.
"""

import argparse
import json
import os
import struct
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import nbtdump  # noqa: E402  (parser NBT minimo ya existente en tools/)


def region_chunks(path):
    """Itera (indice, nbt_bytes) de un fichero .mca."""
    with open(path, 'rb') as f:
        header = f.read(8192)
    if len(header) < 8192:
        return
    for i in range(1024):
        entry = header[i * 4:(i + 1) * 4]
        offset = (entry[0] << 16) | (entry[1] << 8) | entry[2]
        sectors = entry[3]
        if offset == 0 or sectors == 0:
            continue
        with open(path, 'rb') as f:
            f.seek(offset * 4096)
            length_raw = f.read(4)
            if len(length_raw) < 4:
                continue
            length = struct.unpack('>i', length_raw)[0]
            if length <= 0 or length > 64 * 1024 * 1024:
                continue
            comp = f.read(1)
            data = f.read(length - 1)
        if not data:
            continue
        if comp == b'\x02':
            try:
                data = zlib.decompress(data)
            except zlib.error:
                continue
        elif comp == b'\x01':
            try:
                data = zlib.decompress(data, 16 + zlib.MAX_WBITS)
            except zlib.error:
                continue
        elif comp != b'\x03':
            continue
        yield i, data


def parse_root(data):
    r = nbtdump.R(data)
    tag = r.u1()
    if tag != 10:
        return None
    r.s()  # nombre de la raiz
    return nbtdump.payload(r, 10)


def poi_records(nbt):
    """Saca (pos, tipo, free_tickets, seccion) de un NBT de columna de POI."""
    out = []
    secciones = nbt.get('Sections', {}) or {}
    # `Sections` es un Compound indexado por la Y de la seccion como texto ("3"), no una lista.
    iterable = secciones.items() if isinstance(secciones, dict) else [(None, s) for s in secciones]
    for y, section in iterable:
        for rec in section.get('Records', []) or []:
            pos = rec.get('pos')
            if not isinstance(pos, list) or len(pos) != 3:
                continue
            out.append((tuple(int(v) for v in pos), str(rec.get('type', '?')),
                        int(rec.get('free_tickets', 0)), y))
    return out


def es_de_trabajo(tipo):
    """Tipos de puesto de trabajo que usa la aldea (los 11 reales: 3 farmer + 8 oficios)."""
    return tipo in {
        'minecraft:farmer', 'minecraft:weaponsmith', 'minecraft:toolsmith',
        'minecraft:cleric', 'minecraft:shepherd', 'minecraft:butcher',
        'minecraft:fisherman', 'minecraft:fletcher', 'minecraft:mason',
        'minecraft:armorer', 'minecraft:cartographer', 'minecraft:librarian',
        'minecraft:leatherworker',
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('world', help='directorio del mundo (el que contiene poi/)')
    ap.add_argument('--tipo', choices=['todo', 'job'], default='todo')
    ap.add_argument('--centro', help='x,z del centro de la aldea para limitar el radio (p.ej. 661,533)')
    ap.add_argument('--radio', type=int, default=80, help='radio en bloques si se pasa --centro (default 80)')
    ap.add_argument('--json', action='store_true')
    args = ap.parse_args()
    centro = None
    if args.centro:
        try:
            cx, cz = [int(v.strip()) for v in args.centro.split(',')]
            centro = (cx, cz)
        except ValueError:
            print('FALLO: --centro debe ser "x,z"', file=sys.stderr)
            return 2

    poi_dir = os.path.join(args.world, 'poi')
    if not os.path.isdir(poi_dir):
        print('FALLO: no existe %s' % poi_dir, file=sys.stderr)
        return 2
    filas = []
    ficheros = 0
    for nombre in sorted(os.listdir(poi_dir)):
        if not nombre.endswith('.mca'):
            continue
        ficheros += 1
        for _i, data in region_chunks(os.path.join(poi_dir, nombre)):
            nbt = parse_root(data)
            if not nbt:
                continue
            for pos, tipo, free, seccion in poi_records(nbt):
                if centro and (abs(pos[0] - centro[0]) > args.radio or abs(pos[2] - centro[1]) > args.radio):
                    continue
                if args.tipo == 'job' and not es_de_trabajo(tipo):
                    continue
                filas.append({
                    'x': pos[0], 'y': pos[1], 'z': pos[2], 'pos': '%d, %d, %d' % pos,
                    'tipo': tipo, 'free_tickets': free, 'seccion': seccion,
                })
    filas.sort(key=lambda f: (f['tipo'], f['y'], f['z'], f['x']))
    if args.json:
        print(json.dumps({'world': args.world, 'ficheros': ficheros,
                          'registros': len(filas), 'pois': filas}, indent=2, sort_keys=True))
        return 0
    print('# world=%s ficheros=%d registros=%d' % (args.world, ficheros, len(filas)))
    print('# pos|tipo|free_tickets|seccion')
    for f in filas:
        print('%s|%s|%d|%s' % (f['pos'], f['tipo'], f['free_tickets'], f['seccion']))
    return 0


if __name__ == '__main__':
    sys.exit(main())
