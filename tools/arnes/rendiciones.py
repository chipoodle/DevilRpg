#!/usr/bin/env python3
"""RENDICIONES POR 1.000 TICKS (la forma de que el total NO sea ruido).

**El problema que resuelve** (26-sep-2026): yo comparaba corridas por su **total** de rendiciones
(`no consigue llegar`) y salían 5, 9, 10, 12, 18... Comparar totales de corridas distintas es **ruido** por dos
razones, y las dos se arreglan aquí:

1. **Las corridas duran distinto**: una de 4.000 ticks con 5 rendiciones va PEOR que una de 12.000 con 9. La cuenta
   buena es **por 1.000 ticks**, no el total.
2. **La muestra es pequeña (~10 avisos)**: con esos números, una corrida sola no distingue una mejora del azar. Hace
   falta la **media de 3-4 corridas** y el **rango** (mín-máx), no un número suelto.

**Cómo lo mide**: saca el reloj de la propia línea del arnés (`[Arnes] ... t=<ticks>`) —es el tick del servidor, la
única marca de tiempo fiable del log— y cuenta las rendiciones **dentro de una ventana fija** (`--desde`/`--hasta`,
por defecto 2.000-12.000) para que todas las corridas se midan en **el mismo tramo**.

Uso:
    python tools/arnes/rendiciones.py build/medida-*.log            # tabla de cada log
    python tools/arnes/rendiciones.py --tabla build/medida-*.log    # media y rango de la tanda
    python tools/arnes/rendiciones.py --desde 2000 --hasta 12000 build/x.log

Sin dependencias. Acepta también `.log.gz` (el log ROTA por tamaño: ver LEEME).
"""
from __future__ import annotations

import argparse
import glob
import gzip
import io
import re
import sys
from dataclasses import dataclass

# La rendición del pueblo: "no consigue llegar a X desde Y (...)".
RENDICION = re.compile(r"no consigue llegar a ")
# La etiqueta de quien se rinde: etiqueta="Fulano (Oficio) / Faena"
ETIQUETA = re.compile(r'etiqueta="([^"]*)"')
# El reloj del arnés: "[Arnes] MINA t=9440 ..." (el tick del servidor).
RELOJ = re.compile(r"\[Arnes\][^\n]*?\bt=(\d+)")


@dataclass
class Medida:
    log: str
    desde: int
    hasta: int
    rendiciones: int
    por_mil: float
    etiquetas: dict[str, int]

    def linea(self) -> str:
        return (f"{self.log:<44} t={self.desde}-{self.hasta}  "
                f"rendiciones={self.rendiciones:<4} por 1.000 ticks={self.por_mil:6.2f}")


def leer(ruta: str) -> str:
    """El log, tal cual; y si está comprimido (rota por tamaño), se descomprime."""
    if ruta.endswith(".gz"):
        with gzip.open(ruta, "rt", encoding="utf-8", errors="replace") as f:
            return f.read()
    with io.open(ruta, "r", encoding="utf-8", errors="replace") as f:
        return f.read()


def medir(ruta: str, desde: int, hasta: int) -> Medida | None:
    texto = leer(ruta)
    # El último t= de una línea del arnés dentro de la ventana manda: así la corrida puede pasarse de `hasta` y se
    # corta donde toca (o quedarse corta, y entonces la ventana es la que hay).
    ticks = [int(m.group(1)) for m in RELOJ.finditer(texto)]
    if not ticks:
        return None
    fin = min(hasta, max(ticks))
    inicio = min(desde, fin)
    rendiciones = 0
    etiquetas: dict[str, int] = {}
    for linea in texto.splitlines():
        if not RENDICION.search(linea):
            continue
        # El tick de la rendición es el del ÚLTIMO reloj visto en el log antes de esa línea.
        pos = texto.find(linea)
        t = 0
        for m in RELOJ.finditer(texto, 0, pos if pos > 0 else len(texto)):
            t = int(m.group(1))
        if not (inicio <= t <= fin):
            continue
        rendiciones += 1
        et = ETIQUETA.search(linea)
        clave = et.group(1) if et else "(sin etiqueta)"
        etiquetas[clave] = etiquetas.get(clave, 0) + 1
    tramo = max(1, fin - inicio)
    return Medida(ruta.split("/")[-1].split("\\")[-1], inicio, fin, rendiciones,
                  rendiciones * 1000.0 / tramo, etiquetas)


def main() -> int:
    ap = argparse.ArgumentParser(description="Rendiciones por 1.000 ticks (ventana fija).")
    ap.add_argument("logs", nargs="+", help="logs del arnés (admite .log y .log.gz)")
    ap.add_argument("--desde", type=int, default=2000)
    ap.add_argument("--hasta", type=int, default=12000)
    ap.add_argument("--tabla", action="store_true", help="media y rango de la tanda")
    ap.add_argument("--etiquetas", action="store_true", help="desglose por etiqueta (quién se rinde)")
    args = ap.parse_args()

    rutas: list[str] = []
    for patron in args.logs:
        encontrados = glob.glob(patron)
        rutas.extend(encontrados if encontrados else [patron])

    medidas = [m for m in (medir(r, args.desde, args.hasta) for r in rutas) if m is not None]
    if not medidas:
        print("No hay ninguna corrida con reloj del arnés en los logs dados.", file=sys.stderr)
        return 1
    for m in medidas:
        print(m.linea())
    if args.etiquetas:
        for m in medidas:
            print(f"\n-- {m.log}")
            for etiqueta, veces in sorted(m.etiquetas.items(), key=lambda kv: -kv[1]):
                print(f"   {veces:>3}x {etiqueta}")

    if args.tabla or len(medidas) > 1:
        tasas = [m.por_mil for m in medidas]
        media = sum(tasas) / len(tasas)
        # La desviación típica es MUESTRAL (n-1): es una muestra de corridas, no la población.
        var = sum((x - media) ** 2 for x in tasas) / max(1, len(tasas) - 1)
        desv = var ** 0.5
        print(f"\nTANDA de {len(tasas)} corrida(s), ventana t={args.desde}-{args.hasta}:")
        print(f"  media = {media:6.2f} rendiciones por 1.000 ticks")
        print(f"  rango = {min(tasas):6.2f} .. {max(tasas):6.2f}   (desviación típica {desv:.2f})")
        print("  OJO: con 3-4 corridas esto es una orientación, no un valor exacto. Lo que NO se puede hacer es")
        print("       comparar TOTALES de corridas de distinta duración (eso era el ruido).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
