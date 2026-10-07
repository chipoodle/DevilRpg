# -*- coding: utf-8 -*-
"""Genera la textura propia del porton doble (roble oscuro con flejes de hierro).

Se escribe el PNG con la libreria estandar (zlib + struct): en esta maquina no hay PIL. 16x16, RGBA.
La paleta es la del ROBLE OSCURO del juego, para que pegue con el muro y las casas:
  tablon claro  #6B4A2A / tablon        #5C3F22 / tablon oscuro #4A331B / junta #33230F
  fleje de hierro claro #6E6E6E / hierro #4A4A4A / sombra del fleje #2E2E2E
"""
import os
import struct
import zlib

RAIZ = r'C:\Users\Christian\Documents\DevilRpg\src\main\resources\assets\devilrpg\textures\block'
NOMBRE = 'porton_doble.png'

CLARO = (0x6B, 0x4A, 0x2A)
TABLON = (0x5C, 0x3F, 0x22)
OSCURO = (0x4A, 0x33, 0x1B)
JUNTA = (0x33, 0x23, 0x0F)
HIERRO = (0x4A, 0x4A, 0x4A)
HIERRO_CLARO = (0x6E, 0x6E, 0x6E)
HIERRO_OSCURO = (0x2E, 0x2E, 0x2E)


def pixel(tablero, x, y, color):
    """Pinta un pixel (y=0 arriba, como en Minecraft)."""
    tablero[(x, y)] = color


def main():
    t = {}
    # (1) LOS TABLONES: cinco tablones horizontales, cada uno con su veta y su junta.
    alturas = [0, 3, 6, 9, 12, 16]
    for i in range(5):
        y0, y1 = alturas[i], alturas[i + 1]
        for y in range(y0, y1):
            for x in range(16):
                # Veta: lineas verticales irregulares, para que no se vea plano.
                c = TABLON
                if (x + y * 3) % 7 == 0:
                    c = CLARO
                elif (x * 2 + y) % 11 == 0:
                    c = OSCURO
                pixel(t, x, y, c)
        # La junta entre tablones: una linea oscura y otra clara debajo (relieve).
        if y1 < 16:
            for x in range(16):
                pixel(t, x, y1 - 1, OSCURO)
                pixel(t, x, y1, JUNTA)
    # (2) LOS FLEJES DE HIERRO: dos travesanos (arriba y abajo) y los remaches.
    for y in (1, 2, 13, 14):
        for x in range(16):
            base = HIERRO_CLARO if y in (1, 13) else HIERRO
            pixel(t, x, y, base)
    for y in (3, 12):
        for x in range(16):
            pixel(t, x, y, HIERRO_OSCURO)
    # Los remaches: cuatro por fleje.
    for (rx, ry) in ((2, 1), (7, 1), (12, 1), (2, 13), (7, 13), (12, 13)):
        pixel(t, rx, ry, HIERRO_CLARO)
        pixel(t, rx + 1, ry, HIERRO_OSCURO)
    # (3) EL ARO DE LA ALDABA, en el centro: es lo que dice "esto es un porton".
    for (x, y) in ((7, 7), (8, 7), (6, 8), (9, 8), (6, 9), (9, 9), (7, 10), (8, 10)):
        pixel(t, x, y, HIERRO_CLARO)
    for (x, y) in ((6, 7), (9, 7), (5, 8), (10, 8), (5, 9), (10, 9), (6, 10), (9, 10)):
        pixel(t, x, y, HIERRO_OSCURO)
    # Y EL BORDE DE LA HOJA: un canto oscuro a la derecha, para que se vea el canto de la hoja.
    for y in range(16):
        pixel(t, 15, y, JUNTA)
    # (4) Escribir el PNG (RGBA, 16x16) con la libreria estandar.
    filas = b''
    for y in range(16):
        filas += b'\x00'  # filtro "none"
        for x in range(16):
            r, g, b = t.get((x, y), TABLON)
            filas += bytes((r, g, b, 255))

    def trozo(tipo, datos):
        return (struct.pack('>I', len(datos)) + tipo + datos
                + struct.pack('>I', zlib.crc32(tipo + datos) & 0xFFFFFFFF))

    cabecera = struct.pack('>IIBBBBB', 16, 16, 8, 6, 0, 0, 0)
    png = (b'\x89PNG\r\n\x1a\n'
           + trozo(b'IHDR', cabecera)
           + trozo(b'IDAT', zlib.compress(filas, 9))
           + trozo(b'IEND', b''))
    os.makedirs(RAIZ, exist_ok=True)
    destino = os.path.join(RAIZ, NOMBRE)
    with open(destino, 'wb') as f:
        f.write(png)
    print('escrito %s (%d bytes)' % (destino, len(png)))


if __name__ == '__main__':
    main()
