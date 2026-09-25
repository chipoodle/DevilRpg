"""RESUMEN DE RENDICIONES del log del arnés ("no consigue llegar a ... desde ... (ruta=... alcanza=...)").

Uso: python tools\\arnes\\resumen_rendiciones.py [log] [salida.txt]
    (por defecto: run\\logs\\latest.log)

Saca, por aldeano, TODAS sus rendiciones con el destino, desde dónde, la ruta, si ALCANZA y los bloques del destino; y
al final el total, el reparto por `alcanza` y los DESTINOS REPETIDOS (que es lo que delata un sitio del pueblo que se
les atraganta a varios, como pasó con `423,63,671`).
"""
import re
import sys
from collections import Counter, defaultdict

LINEA = re.compile(
    r"\[Village\] (?P<uuid>[0-9a-fA-F-]{36}) no consigue llegar a (?P<dest>-?\d+, -?\d+, -?\d+)"
    r" desde (?P<desde>-?\d+, -?\d+, -?\d+)"
    r" \(ruta=(?P<ruta>.*?) alcanza=(?P<alcanza>SI|NO);"
    r" pies=(?P<pies>[^ ]+) cabeza=(?P<cabeza>[^ ]+) suelo=(?P<suelo>[^ ]+)"
    r" \| destino=(?P<bloque>[^ ]+) encima=(?P<encima>[^)]+)\)")
HORA = re.compile(r"^\[([^\]]+)\]")


def leer(ruta):
    salida = []
    with open(ruta, "r", encoding="utf-8", errors="replace") as f:
        for linea in f:
            m = LINEA.search(linea)
            if not m:
                continue
            hora = HORA.match(linea)
            salida.append({
                "hora": (hora.group(1).split()[-1] if hora else "?"),
                "uuid": m.group("uuid")[:8],
                "dest": m.group("dest"),
                "desde": m.group("desde"),
                "ruta": m.group("ruta").strip(),
                "alcanza": m.group("alcanza"),
                "bloque": m.group("bloque"),
                "encima": m.group("encima").strip(),
            })
    return salida


def main():
    log = sys.argv[1] if len(sys.argv) > 1 else r"run\logs\latest.log"
    destinoFichero = sys.argv[2] if len(sys.argv) > 2 else None
    rendiciones = leer(log)
    lineas = []
    if not rendiciones:
        lineas.append("sin rendiciones en %s" % log)
    else:
        porAldeano = defaultdict(list)
        for r in rendiciones:
            porAldeano[r["uuid"]].append(r)
        lineas.append("%-8s %-11s %-17s %-17s %-6s %-4s %s" %
                      ("aldeano", "hora", "destino", "desde", "alcance", "nodos", "destino(bloque)"))
        for uuid in sorted(porAldeano, key=lambda u: -len(porAldeano[u])):
            for r in porAldeano[uuid]:
                nodos = r["ruta"].split()[0] if r["ruta"] else "?"
                lineas.append("%-8s %-11s %-17s %-17s %-6s %-6s %s" %
                              (uuid, r["hora"], r["dest"], r["desde"], r["alcanza"], nodos, r["bloque"]))
        sinAlcanzar = sum(1 for r in rendiciones if r["alcanza"] == "NO")
        conRuta = len(rendiciones) - sinAlcanzar
        lineas.append("")
        lineas.append("TOTAL: %d rendiciones, %d aldeano(s) | SIN ALCANZAR: %d | CON RUTA QUE ALCANZA: %d"
                      % (len(rendiciones), len(porAldeano), sinAlcanzar, conRuta))
        repetidos = Counter(r["dest"] for r in rendiciones)
        lineas.append("DESTINOS REPETIDOS: " + (", ".join("%s x%d" % (d, n) for d, n in repetidos.most_common()
                                                          if n > 1) or "ninguno"))
    texto = "\n".join(lineas)
    print(texto)
    if destinoFichero:
        with open(destinoFichero, "w", encoding="utf-8") as f:
            f.write(texto + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
