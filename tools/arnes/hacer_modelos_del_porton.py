# -*- coding: utf-8 -*-
"""Genera los 12 modelos del porton repartiendo la textura por la superficie de 3x3.

POR QUE ASI: el porton son NUEVE bloques (3 de ancho por 3 de alto) y cada bloque cogia la TEXTURA ENTERA, asi que se
veia repetida tres veces en horizontal y tres en vertical — lo reporto el jugador: *«la textura esta mal puesta, lo
ideal es que la textura cubra la superficie de los 3 x 3 bloques de la puerta»*.

El reparto:
  - EN VERTICAL: cada piso coge su tercio (low/mid/high -> v de 0 a 5.33, de 5.33 a 10.67 y de 10.67 a 16).
  - EN HORIZONTAL: la mitad IZQUIERDA del porton (dos bloques) se reparte el trozo izquierdo de la textura (u 0 a 8.53)
    y la mitad DERECHA el derecho (u 8.53 a 16). Los bloques de la juntura cogen el trozo que les toca de su mitad, asi
    que entre los tres suman la textura entera y el dibujo es CONTINUO: el fleje de hierro cruza los tres bloques de
    lado a lado y la aldaba cae partida en la juntura, que es justo donde van las dos hojas.
  - LAS CARAS DE CANTO (north/south en las hojas cerradas) no se estiran: cogen la tira de su piso. Como la hoja
    cerrada mide 16 de fondo, la escala cuadra; en la abierta (grosor 2) solo se ve una tira, que es lo correcto
    porque esta de canto.
"""
import json
import os

RAIZ = r'C:\Users\Christian\Documents\DevilRpg\src\main\resources\assets\devilrpg\models\block'
TEX = 'devilrpg:block/porton_doble'
V3 = 16.0 / 3.0
PISOS = {'low': (0.0, V3), 'mid': (V3, 2 * V3), 'high': (2 * V3, 16.0)}

# GEOMETRIA, EN EL ESPACIO LOCAL DEL MODELO (el blockstate lo gira a su sitio):
#   local X = la NORMAL del muro (hacia donde mira el portón)
#   local Z = la LÍNEA del muro (por donde se reparten las celdas)
# CERRADA: llena su celda (16×16 en planta) -> el portón se ve macizo y de una pieza.
# ABIERTA: ha girado 90° sobre su bisagra y queda PERPENDICULAR al muro: 8 píxeles hacia dentro por la normal y sólo 2
#          de canto en la línea del muro. ESTE ERA EL FALLO: antes la hoja abierta seguía EN EL PLANO del muro (sólo más
#          fina), así que el jugador veía que «no gira en uno de los lados y se queda en su lugar» aunque ya se pasara ✗.
GEOM = {
    ('left', False): (0, 16, 0, 16),
    ('right', False): (0, 16, 0, 16),
    ('left', True): (0, 8, 0, 2),
    ('right', True): (0, 8, 14, 16),
}
# El trozo de ANCHO (u) de cada bloque, de forma que entre los tres CUBRAN la textura entera sin repetirla ni dejarla a
# medias: la celda de fuera de la hoja izquierda coge el primer tercio, la JUNTURA el trozo con el que las dos hojas se
# encuentran (el borde de su mitad) y la celda de fuera de la hoja derecha el resto. El fleje de hierro y la aldaba
# cruzan así los tres bloques como si fueran una sola pieza ✓.
U = {
    ('left', 0): (0.0, 16.0 / 3.0),           # hoja izquierda en su celda de fuera: el primer tercio
    ('left', 1): (16.0 / 3.0, 32.0 / 3.0),    # la JUNTURA: el tercio del medio
    ('right', 0): (16.0 / 2.0, 16.0 / 2.0),   # (no se usa: la juntura la modela la hoja izquierda)
    ('right', 1): (32.0 / 3.0, 16.0),         # hoja derecha en su celda de fuera: el ultimo tercio
}
# Que celda ocupa cada bloque dentro de su hoja (0 = la de fuera, 1 = la de la juntura).
CELDA = {
    ('left', False): [('left', 0), ('left', 1)],
    ('right', False): [('right', 0), ('right', 1)],
    ('left', True): [('left', 0), ('left', 1)],
    ('right', True): [('right', 0), ('right', 1)],
}


def modelos():
    for (lado, abierto), (x1, x2, z1, z2) in GEOM.items():
        for piso, (v0, v1) in PISOS.items():
            if not abierto:
                # La hoja CERRADA se modela en dos trozos, porque ocupa dos celdas: el de FUERA (su tercio exterior) y
                # el de la JUNTURA (donde las dos hojas se encuentran). Los dos van con el mismo estado salvo el trozo.
                # La hoja DERECHA coge su trozo exterior (indice 0 -> la entrada 1 de la tabla); la juntura la modela
                # la hoja IZQUIERDA, que es la que va en la celda del medio.
                for indice in (0, 1):
                    clave = (lado, 1) if (lado == 'right' and indice == 0) else (lado, indice)
                    u0, u1 = U[clave]
                    sufijo = '' if indice == 0 else '_juntura'
                    yield ('porton_%s_cerrado_%s%s' % (lado, piso, sufijo), (x1, x2, z1, z2), u0, u1, v0, v1, False)
            else:
                u0, u1 = U[(lado, 0)]
                yield ('porton_%s_abierto_%s' % (lado, piso), (x1, x2, z1, z2), u0, u1, v0, v1, True)


def main():
    escritos = []
    for nombre, (x1, x2, z1, z2), u0, u1, v0, v1, abierto in modelos():
        caras = {}
        for cara in ('up', 'down', 'north', 'south', 'east', 'west'):
            if cara in ('east', 'west'):
                # LOS CANTOS de la hoja (el grueso): su tira del piso, sin estirar. La hoja cerrada mide 16 de fondo,
                # asi que la escala cuadra; abierta (grosor 2) solo se ve una tira, que es lo correcto: esta de canto.
                caras[cara] = {'uv': [0, v0, 16, v1], 'texture': '#all'}
            else:
                # LA CARA GRANDE (frontal, trasera y las tapas): el trozo que le toca de la textura.
                caras[cara] = {'uv': [u0, v0, u1, v1], 'texture': '#all'}
        model = {
            'credit': 'DevilRpg - porton doble abatible del muro (roble oscuro con flejes de hierro)',
            'textures': {'all': TEX},
            'elements': [{'from': [x1, 0, z1], 'to': [x2, 16, z2], 'faces': caras}],
        }
        with open(os.path.join(RAIZ, nombre + '.json'), 'w', encoding='utf-8') as f:
            json.dump(model, f, indent=2)
        escritos.append(nombre)
    print('%d modelos escritos' % len(escritos))
    for n in escritos:
        print('   ', n)


if __name__ == '__main__':
    main()
