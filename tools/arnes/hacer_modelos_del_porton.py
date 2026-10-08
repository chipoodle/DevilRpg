# -*- coding: utf-8 -*-
"""Genera los modelos y el blockstate del porton doble abatible de la aldea.

LA IDEA (y es lo que hace que se vea como UNA SOLA PUERTA, no como tres):
  - El porton ocupa TRES celdas seguidas en la linea del muro, y cada celda tiene un papel (`PARTE`): la 0 es la de la
    BISAGRA, la 1 la del medio y la 2 la del extremo libre.
  - CERRADA: cada celda dibuja su TROZO de la textura (un tercio) y llena su celda -> las tres juntas son una hoja de
    3x3 de una sola pieza ✓.
  - ABIERTA: la hoja ha girado 90 grados sobre la bisagra y queda PERPENDICULAR al muro, ocupando 3 bloques hacia
    dentro. Y aqui esta la clave: **cada celda dibuja el trozo que le toca de ese panel largo**, encadenado con las
    otras dos, asi que las tres forman UNA sola superficie continua ✓.
    (Antes cada celda dibujaba su propio liston de 8 px en su sitio: tres listones separados = «tres puertas
     apiladas», que es exactamente lo que reporto el jugador ✗.)

  Para que el panel quede encadenado, cada celda dibuja FUERA de su propia celda (los modelos pueden salirse):
    parte 0 -> de x -16 a 0        (el trozo pegado al muro)
    parte 1 -> de x -32 a -16
    parte 2 -> de x -48 a -32
  y todas a la altura del plano de la bisagra (z de -16*parte a -16*parte + 2).

ESPACIO LOCAL DEL MODELO (el blockstate lo gira a su sitio):
    local X = la NORMAL del muro (hacia donde mira el porton)
    local Z = la LINEA del muro (por donde se reparten las celdas)
"""
import json
import os

BASE = r'C:\Users\Christian\Documents\DevilRpg\src\main\resources\assets\devilrpg'
RAIZ_MODELOS = os.path.join(BASE, 'models', 'block')
TEX = 'devilrpg:block/porton_doble'
V3 = 16.0 / 3.0
PISOS = {'low': (0.0, V3), 'mid': (V3, 2 * V3), 'high': (2 * V3, 16.0)}
GROSOR = 2.0

# El papel de cada celda: 0 bisagra, 1 medio, 2 extremo. Se identifica con el estado que YA usa el generador:
#   (SIDE, JUNTURA) -> parte       (ver VillageGenerator.sellarLasCeldasDelPorton)
PARTES = {('left', 'false'): 0, ('left', 'true'): 1, ('right', 'false'): 2}
# El trozo de la TEXTURA (tercio) que le toca a cada parte cuando esta cerrada.
TROZOS = {0: (0.0, V3), 1: (V3, 2 * V3), 2: (2 * V3, 16.0)}

# Rotaciones: la hoja izquierda es la del lado NEGATIVO del eje del muro.
#   muro en Z (N/S) -> local +X a +Z -> y=90 (izq) y=270 (der)
#   muro en X (E/O) -> local +X a +X -> y=0  (izq) y y=180 (der)
ROT = {
    'north': {'left': 90, 'right': 270},
    'south': {'left': 90, 'right': 270},
    'east': {'left': 0, 'right': 180},
    'west': {'left': 0, 'right': 180},
}


def caja_cerrada(parte):
    return (0, 16, 0, 16)


def caja_abierta(parte):
    """LA PUERTA ABIERTA: UN SOLO PANEL DE 3 BLOQUES, DIBUJADO POR LA CELDA DE LA BISAGRA.

    Y AQUÍ ESTÁ EL LÍMITE DEL MOTOR QUE COSTÓ UNA TARDE (8-oct-2026): Minecraft **sólo admite geometría entre -16 y
    32 píxeles** en cada eje (`BlockElement`). La versión anterior repartía el panel entre las tres celdas, encadenadas,
    y las partes 1 y 2 llegaban a **-32 y -48** -> el juego **rechazaba el modelo** y lo reportaba como
    «FileNotFoundException» (mensaje engañoso: el fichero estaba ahí) ✗. Medido en la partida del jugador: de los 12
    modelos abiertos cargaba **sólo el de la bisagra**, que es el único cuyo `from.x` es exactamente **-16** ✓.

    Así que ahora **una sola celda dibuja la puerta entera**, de -16 a 32 = 48 píxeles = **3 bloques** ✓, y las otras dos
    **no dibujan nada** al abrirse. Resultado: en el hueco se ve **UNA sola puerta girada de 3 bloques**, que es lo que
    pidió el jugador (*«debería ser UNA sola puerta abierta, aunque se abra ocupando 3 bloques»*) ✓.
    """
    if parte != 0:
        return None  # las celdas 1 y 2 no dibujan nada con el portón abierto: la puerta es una sola pieza
    return (-16.0, 32.0, 0.0, GROSOR)


def escribe_modelo(nombre, caja, u0, u1, v0, v1):
    # caja = None -> el modelo NO dibuja nada (la celda está abierta pero la puerta entera la dibuja la bisagra).
    elementos = []
    if caja is not None:
        x1, x2, z1, z2 = caja
        caras = {}
        for cara in ('up', 'down', 'north', 'south', 'east', 'west'):
            if cara in ('east', 'west'):
                caras[cara] = {'uv': [0, v0, 16, v1], 'texture': '#all'}
            else:
                caras[cara] = {'uv': [u0, v0, u1, v1], 'texture': '#all'}
        elementos.append({'from': [x1, 0, z1], 'to': [x2, 16, z2], 'faces': caras})
    modelo = {
        'credit': 'DevilRpg - porton doble abatible del muro (roble oscuro con flejes de hierro)',
        'textures': {'all': TEX},
        'elements': elementos,
    }
    with open(os.path.join(RAIZ_MODELOS, nombre + '.json'), 'w', encoding='utf-8') as f:
        json.dump(modelo, f, indent=2)


def main():
    escritos = 0
    # 1) LOS MODELOS
    for (lado, juntura), parte in PARTES.items():
        u0, u1 = TROZOS[parte]
        for piso, (v0, v1) in PISOS.items():
            sufijo = '_juntura' if juntura == 'true' else ''
            escribe_modelo('porton_%s_cerrado_%s%s' % (lado, piso, sufijo), caja_cerrada(parte), u0, u1, v0, v1)
            # ABIERTA: la puerta entera la dibuja la bisagra, así que ese panel de 3 bloques tiene que enseñar **la
            # textura ENTERA** (u 0..16), no el tercio de su celda. Con el tercio salía **una sola puerta estirada a lo
            # largo de los tres bloques** (lo reportó el jugador: *«su imagen es de una sola puerta vanilla extendida en
            # 3 bloques»* ✗). Con la textura entera, cada bloque del panel enseña su panel de puerta y los tres se ven
            # igual que con el portón cerrado ✓. El tercio de abajo/arriba (v) sí es el del piso que le toca.
            escribe_modelo('porton_%s_abierto_%s%s' % (lado, piso, sufijo), caja_abierta(parte), 0.0, 16.0, v0, v1)
            escritos += 2
    # El cuarto estado (derecha + juntura) no lo usa el generador, pero el blockstate tiene que tener modelo para
    # TODOS los estados posibles o el cliente avisa: se le da el del extremo.
    for piso, (v0, v1) in PISOS.items():
        u0, u1 = TROZOS[2]
        escribe_modelo('porton_right_cerrado_%s_juntura' % piso, caja_cerrada(2), u0, u1, v0, v1)
        escribe_modelo('porton_right_abierto_%s_juntura' % piso, caja_abierta(2), 0.0, 16.0, v0, v1)
        escritos += 2
    print('%d modelos escritos' % escritos)

    # 2) EL BLOCKSTATE (96 variantes, con la juntura tambien en el estado ABIERTO)
    variantes = {}
    for facing, lados in ROT.items():
        for lado, y in lados.items():
            for abierto in ('true', 'false'):
                for juntura in ('true', 'false'):
                    for piso in ('low', 'mid', 'high'):
                        clave = ('facing=%s,open=%s,side=%s,layer=%s,juntura=%s'
                                 % (facing, abierto, lado, piso, juntura))
                        sufijo = '_juntura' if juntura == 'true' else ''
                        estado = 'abierto' if abierto == 'true' else 'cerrado'
                        variantes[clave] = {
                            'model': 'devilrpg:block/porton_%s_%s_%s%s' % (lado, estado, piso, sufijo),
                            'y': y,
                        }
    with open(os.path.join(BASE, 'blockstates', 'porton_doble.json'), 'w', encoding='utf-8') as f:
        json.dump({'variants': variantes}, f, indent=2)
    print('%d variantes' % len(variantes))
    print('CERRADA: cada celda llena la suya con su tercio de textura')
    print('ABIERTA: UNA sola puerta de 3 bloques (x -16..32) dibujada por la BISAGRA; las celdas 1 y 2 no dibujan')


if __name__ == '__main__':
    main()
