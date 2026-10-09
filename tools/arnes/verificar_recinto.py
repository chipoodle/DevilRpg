# -*- coding: utf-8 -*-
"""Verificacion INDEPENDIENTE (sin servidor) de la cuenta de `VillageManager.dentroDelRecinto`.

POR QUE EXISTE (5-oct-2026): la medida del muro quedo con una contradiccion que no cuadraba
--la traza del mod decia `dentroDeLaAldea=true` y la celda se picaba igual--, asi que antes de
seguir tocando logica hay que descartar que el FALLO ESTE EN LA CUENTA. Esto reproduce la cuenta
tal cual esta escrita en `VillageManager.dentroDelRecinto(BlockPos pos, BlockPos center, int cota,
double radio)` (L1319) y comprueba los casos que deciden la regla del jugador ("solo el muro
perimetral es rompible; una vez dentro ya no puede romper nada"), con los numeros MEDIDOS en la
aldea 0 de su partida (centro 470,646 y cota 83).

Uso:  python tools/arnes/verificar_recinto.py
"""
import math
import sys

# --- Los numeros del mod (com.chipoodle.devilrpg.world.VillageManager y VillageGenerator) --------
FENCE_RADIUS = 62          # VillageGenerator.FENCE_RADIUS
RECINTO_DY_ABAJO = 6       # VillageManager.RECINTO_DY_ABAJO
RECINTO_DY_ARRIBA = 16     # VillageManager.RECINTO_DY_ARRIBA
MURALLA_ANCHO = 0          # AggressiveZombieEntity.MURALLA_ANCHO (era 5; la verificacion lo bajo a 0)

# --- Los de la medida (aldea 0 del jugador) ------------------------------------------------------
CENTRO = (470, 83, 646)    # centro del objetivo CON la cota
COTA = 83                  # cota medida en las corridas 9, 13 y 19


def dentro_del_recinto(pos, center, cota, radio):
    """Copia literal de `VillageManager.dentroDelRecinto(BlockPos, BlockPos, int, double)`."""
    dx = pos[0] + 0.5 - (center[0] + 0.5)
    dz = pos[2] + 0.5 - (center[2] + 0.5)
    if dx * dx + dz * dz > radio * radio:
        return False
    dy = pos[1] - cota
    return -RECINTO_DY_ABAJO <= dy <= RECINTO_DY_ARRIBA


def es_la_muralla(pos, center, cota):
    """Copia literal de `AggressiveZombieEntity.esLaMuralla(BlockPos, int)`."""
    dx = pos[0] - center[0]
    dz = pos[2] - center[2]
    d2 = dx * dx + dz * dz
    dentro = FENCE_RADIUS - MURALLA_ANCHO
    fuera = FENCE_RADIUS + MURALLA_ANCHO
    if d2 < dentro * dentro or d2 > fuera * fuera:
        return False
    dy = pos[1] - cota
    return -1 <= dy <= 6  # MURALLA_ALTO = 6


def protegido(pos, center, cota):
    """Copia de `AggressiveZombieEntity.protegidoPorLaAldea(BlockPos)`.

    OJO A LA CORRECCION (5-oct-2026): la version vieja terminaba en `return elAsedioYaSeGano()`, o
    sea que **con el asedio inicial sin resolver devolvia FALSE para todo lo de dentro** -- y eso es
    exactamente lo que dejaba al asaltante abrirse un tunel de 36 bloques dentro del pueblo. La
    regla del jugador ("una vez dentro ya no puede romper nada") NO depende del campo de fuerza: lo
    de dentro esta vetado siempre, y lo unico rompible es el MURO PERIMETRAL y el campo abierto de
    fuera. El campo de fuerza se queda como esta, para lo que SI es suyo (expulsar monstruos, negar
    spawneo), no para decidir si se puede romper dentro.
    """
    if not dentro_del_recinto(pos, center, cota, FENCE_RADIUS):
        return False       # fuera: campo abierto (escaleras, brecha, lo que haga falta)
    if es_la_muralla(pos, center, cota):
        return False       # el muro perimetral: es LA brecha por la que entra el asedio
    return True            # dentro: no se pica NADA, ni con asedio ni sin el


def r_de(pos, center):
    return math.hypot(pos[0] - center[0], pos[2] - center[2])


# --- LOS CASOS QUE DECIDEN LA REGLA --------------------------------------------------------------
# (descripcion, celda, protegido_esperado, por_que). El asedio (ganado o no) YA NO CAMBIA la
# respuesta a proposito: la regla del jugador es de GEOMETRIA (dentro / fuera / muro), no de estado.
CASOS = [
    # El ANILLO del muro (r=62 exacto): es LA brecha. Aqui SI se pica.
    ("celda del anillo del muro (r=62 exacto)",
     (532, 83, 646), False,
     "es la banda del muro: la excepcion que deja entrar al asedio"),
    ("celda del anillo, un bloque mas arriba",
     (532, 84, 646), False,
     "muralla, y dentro de la banda de altura (+6)"),
    # La cara de FUERA (r=62,5): ya es terreno de fuera del anillo... ojo, r=62,5 > 62 por lo que
    # el redondeo del radio: el caso real es la celda 533 (r=62,5), que queda FUERA del recinto.
    ("cara de FUERA del muro (r=62,5)",
     (533, 83, 646), False,
     "fuera del recinto (r=62,5 > 62): campo abierto, se pica"),
    # La cara de DENTRO: ya es la aldea. NO se pica.
    ("cara de DENTRO del muro (r=61,5)",
     (531, 83, 646), True,
     "r=61,5 esta DENTRO: la banda del muro es solo el anillo, asi que esto es el pueblo"),
    # La obra del pueblo, a la altura de la cota: NO se pica jamas.
    ("suelo de la plaza (r=1)",
     (470, 83, 646), True,
     "dentro, a la altura del pueblo"),
    ("suelo de la plaza, un bloque por encima",
     (470, 84, 646), True,
     "dentro (dy=1)"),
    # EL CASO MEDIDO DEL TUNEL: bloques de tierra/piedra a r=44 y r=26, a y=84/85.
    ("bloque del TUNEL a r=44 (y=84) -- el caso que se pico",
     (426, 84, 646), True,
     "dentro (dy=1): el veto TIENE que pararlo"),
    ("bloque del TUNEL a r=44 (y=85)",
     (426, 85, 646), True,
     "dentro (dy=2): el veto TIENE que pararlo"),
    ("bloque del TUNEL a r=26 (y=84)",
     (444, 84, 646), True,
     "dentro (dy=1): el veto TIENE que pararlo"),
    # El kiosco (r=3..6): obra del pueblo dentro.
    ("escalera del kiosco (r=4)",
     (474, 83, 646), True,
     "dentro: obra del pueblo"),
    # UN MONSTRUO EN UNA CUEVA no ha pasado los muros: NO esta dentro (esto es I11).
    ("cueva bajo la plaza (r=30, y=40)",
     (500, 40, 646), False,
     "dy=-43 esta por debajo de la banda: una cueva no es 'dentro'"),
    # Fuera del recinto: se pica (campo abierto).
    ("campo abierto a r=70",
     (540, 83, 646), False,
     "fuera del recinto: campo abierto"),
    # Y el caso que costo la ronda: un bloque de DENTRO con el asedio SIN resolver.
    ("bloque de dentro con el asedio SIN resolver (el caso del pendiente)",
     (426, 84, 646), True,
     "la regla del jugador no depende del campo de fuerza: dentro no se pica"),
]


def main():
    fallos = 0
    print("Verificacion de dentroDelRecinto/esLaMuralla con los numeros medidos")
    print("aldea 0: centro {} cota {} radio {} (dy: -{}..+{}) MURALLA_ANCHO={}".format(
        CENTRO, COTA, FENCE_RADIUS, RECINTO_DY_ABAJO, RECINTO_DY_ARRIBA, MURALLA_ANCHO))
    print("-" * 104)
    for desc, celda, esperado, por_que in CASOS:
        dentro = dentro_del_recinto(celda, CENTRO, COTA, FENCE_RADIUS)
        muralla = es_la_muralla(celda, CENTRO, COTA)
        obtenido = protegido(celda, CENTRO, COTA)
        ok = "OK " if obtenido == esperado else "MAL"
        if obtenido != esperado:
            fallos += 1
        print("{:3} {:<52} r={:5.1f} dy={:3d} dentro={:<5} muralla={:<5} protegido={:<5} (esperado {})".format(
            ok, desc[:52], r_de(celda, CENTRO), celda[1] - COTA, str(dentro), str(muralla), str(obtenido),
            str(esperado)))
        print("      por que: {}".format(por_que))
    print("-" * 104)
    if fallos == 0:
        print("TODO OK: la CUENTA del recinto clasifica bien los {} casos que deciden la regla.".format(len(CASOS)))
        print("=> Si el juego pica una celda que aqui sale 'protegido=True', el fallo NO esta en la cuenta:")
        print("   esta en QUIEN la llama y CON QUE COTA/Y. Eso es lo que mide el arnes.")
        return 0
    print("FALLOS: {}".format(fallos))
    return 1


if __name__ == "__main__":
    sys.exit(main())
