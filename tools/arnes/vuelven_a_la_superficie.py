# -*- coding: utf-8 -*-
"""¿CAVAN POR DEBAJO Y VUELVEN A LA SUPERFICIE? (con PROFUNDIDAD, sobre una corrida ya grabada)

Uso: python .migration-tools\\vuelven_a_la_superficie.py build\\rapida-163.log

El testigo del arnes dice "EN EL FOSO" para cualquier celda por debajo del suelo, y eso mezcla dos cosas muy distintas:
estar DENTRO del foso (1..7 de hondo) y estar BAJO TIERRA (>7, o sea por debajo del suelo del foso, que es el tunel).
Aqui se separan por la Y, y se dice de cada asaltante:
  - el fondo maximo al que llego (en bloques por debajo del suelo de la escena),
  - si estuvo BAJO TIERRA (mas de lo que cava el foso),
  - y si DESPUES se le vio en la superficie (que es lo que el jugador exige: "si cavan por debajo, despues salir, obligatorio").
"""
import io
import re
import sys

RUTA = sys.argv[1] if len(sys.argv) > 1 else r'build\rapida-163.log'
HONDO_DEL_FOSO = 7  # lo que cava la escena (ver MEDIR_OLA_CON_FOSO)

patron = re.compile(r'OLA CON FOSO vuelta=(\d+) ASALTANTE (\d+) pos=BlockPos\{x=(-?\d+), y=(-?\d+), z=(-?\d+)\}'
                    r' r=(\d+) (EN EL FOSO|en la plancha)')
suelo = None
for linea in io.open(RUTA, encoding='utf-8', errors='replace'):
    m = re.search(r'OLA CON FOSO: vuelta 1 montada en BlockPos\{x=-?\d+, y=(-?\d+), z=-?\d+\}', linea)
    if m:
        suelo = int(m.group(1))
        break
if suelo is None:
    raise SystemExit('no encuentro la altura del suelo de la escena en ' + RUTA)

porAsaltante = {}
for linea in io.open(RUTA, encoding='utf-8', errors='replace'):
    m = patron.search(linea)
    if not m:
        continue
    vuelta, i, y = int(m.group(1)), int(m.group(2)), int(m.group(4))
    porAsaltante.setdefault(i, []).append((vuelta, suelo - y))

print('corrida: %s   (suelo de la escena: y=%d)' % (RUTA, suelo))
print('asaltante | fondo maximo | bajo tierra (>%d) | volvio a la superficie despues' % HONDO_DEL_FOSO)
for i in sorted(porAsaltante):
    muestras = porAsaltante[i]
    fondo = max(p for _, p in muestras)
    bajoTierra = False
    volvio = False
    for _, p in muestras:
        if p > HONDO_DEL_FOSO:
            bajoTierra = True
        elif bajoTierra and p <= 0:
            volvio = True
    print('   %2d     |   %2d bloques |        %-8s          | %s' % (
        i, fondo, 'SI' if bajoTierra else 'no', 'SI' if volvio else ('no (nunca bajo tierra)' if not bajoTierra else 'NO')))
