#!/usr/bin/env python3
"""LINT de las INVARIANTES de la aldea (DevilRpg).

Vigila los patrones que YA nos han costado bugs EN JUEGO. No pretende ser un analisis
semantico: son reglas concretas, nacidas de fallos reales, para que no vuelvan. La regla
de oro es NO dar falsos positivos: si canta, es que hay algo que mirar.

    python tools/lint_aldea.py            # informe
    python tools/lint_aldea.py --strict   # sale con 1 si hay algo (para usar de puerta antes de commitear)

Invariantes (todas han petado al menos una vez):

  I1  La Y del CENTRO/BASE de la aldea no es fiable (llega la del spawn del jugador: 101 con
      el pueblo a 75). Toda altura se mide con `cotaDeLaPlaza`.
      -> `center.getY()`, `base.getY()`, `centro.getY()` usados para construir posiciones.
  I2  Las distancias "dentro de la aldea" son HORIZONTALES (la aldea es un recinto en XZ).
      -> `distSqr(center)`, `distanceToSqr(..., center, ...)`.
  I3  Atascado = NO ACERCARSE. Un contador de paciencia no puede subir mientras el aldeano
      avanza (rendirse a los 6 s de caminar dejaba al granjero ciclado sin entregar).
      -> `stuckTicks++` sin una comprobacion de progreso (`mejorDistancia`) cerca.
  I4  Los tamanos de parcela son constantes publicas, no numeros a mano en los goals.
      -> `dx < 9` / `dz < 5` en `Villager*.java`.
  I5  Las medidas compartidas (kiosco) se piden con su metodo: el numero vive en un solo sitio.
      -> `KIOSCO_RADIO`/`KIOSCO_POSTE` fuera de `VillageGenerator.java`.
  I6  El movimiento del aldeano va POR EL CEREBRO (`VillageManager.caminarHacia`): navegando a
      mano, el cerebro le da otro destino y se va a otra parte.
      -> `getNavigation().moveTo` en `Villager*.java`.
  I7  La granja se consulta a LA COTA: `parcelasDe(level, center)`, no `parcelasDe(center)`.
  I8  Mutar el mundo en el latido de la aldea (fuera de generar/migrar) es delicado: si aparece
      un `setBlock`/`destroyBlock`/`colocar` en `VillageManager`, hay que justificarlo.
  I9  Cambiar lo que se CONSTRUYE obliga a subir `CURRENT_LAYOUT` (el mundo guardado tiene que
      rehacerse). Comprobacion con git sobre el diff pendiente.
"""
import os
import re
import subprocess
import sys

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PAQUETE = os.path.join(RAIZ, 'src', 'main', 'java', 'com', 'chipoodle', 'devilrpg')
GOALS = os.path.join(PAQUETE, 'entity', 'goal')

ALDEA = [
    os.path.join(PAQUETE, 'world', 'VillageGenerator.java'),
    os.path.join(PAQUETE, 'world', 'VillageManager.java'),
    os.path.join(PAQUETE, 'world', 'VillagePantry.java'),
    os.path.join(PAQUETE, 'world', 'VillageStorage.java'),
    os.path.join(GOALS, 'VillagerFarmGoal.java'),
    os.path.join(GOALS, 'VillagerCollectGoal.java'),
    os.path.join(GOALS, 'VillagerRepairGoal.java'),
]
GOALS_JAVA = [r for r in ALDEA if os.path.basename(r).startswith('Villager')]

# (clave, ficheros, patron, aviso, lineas_de_contexto_atras)
REGLAS = [
    ('I1', ALDEA, r'\b(center|centro|base)\.getY\(\)',
     'La Y del centro/base no es la de la aldea (llega la del spawn): usa cotaDeLaPlaza.', 0),
    ('I2', ALDEA, r'distSqr\(\s*center\s*\)|distanceToSqr\([^;]*\bcenter\b',
     'Distancia al centro en 3D: usa la HORIZONTAL (la aldea es un recinto en XZ).', 0),
    ('I3', ALDEA, r'stuckTicks\+\+',
     'Contador de paciencia sin comprobacion de progreso: tiene que subir solo si NO se acerca.', 25),
    ('I4', GOALS_JAVA, r'[<>]=?\s*(9|5)\s*[;)&|]',
     'Tamano de parcela a mano: usa VillageGenerator.PLOT_WIDTH / PLOT_DEPTH.', 3),
    ('I5', [r for r in ALDEA if not r.endswith('VillageGenerator.java')], r'KIOSCO_(RADIO|POSTE)',
     'Medida del kiosco: pidela con VillageGenerator.kioscoRadio().', 0),
    ('I6', GOALS_JAVA, r'getNavigation\(\)\.moveTo',
     'Movimiento a mano: usa VillageManager.caminarHacia (el cerebro pisa la navegacion).', 0),
    ('I7', ALDEA, r'parcelasDe\(\s*center\s*\)',
     'Consulta la granja a la cota: parcelasDe(level, center).', 0),
    ('I8', [os.path.join(PAQUETE, 'world', 'VillageManager.java')],
     r'\.setBlock\(|destroyBlock\(|\bcolocar\(',
     'Mutar el mundo en el latido de la aldea: mira si es idempotente y si mide desde la cota.', 0),
]

# Formas legitimas: si la linea las cita, no se avisa.
PERMITIDO = [
    r'cotaDeLaPlaza',
    r'//',
    r'/\*',
    r'^\s*\*',
    r'@Nullable',
    # Excepcion justificada a mano en la propia linea: `// lint:ok I1 porque ...`
    r'lint:ok',
]


def lineas(ruta):
    with open(ruta, encoding='utf-8', errors='replace') as f:
        return f.readlines()


def revisar():
    fallos = []
    for clave, ficheros, patron, aviso, contexto in REGLAS:
        for ruta in ficheros:
            if not os.path.exists(ruta):
                continue
            contenido = lineas(ruta)
            for i, linea in enumerate(contenido):
                if not re.search(patron, linea):
                    continue
                if any(re.search(ok, linea) for ok in PERMITIDO):
                    continue
                # La excepcion se puede marcar en la propia linea o en las DOS anteriores:
                #     // lint:ok I1 porque aqui la Y SI es la cota
                #     ... codigo ...
                if any('lint:ok' in l for l in contenido[max(0, i - 2):i + 1]):
                    continue
                # I3: se admite si en las lineas anteriores hay una comprobacion de progreso.
                if clave == 'I3' and contexto:
                    ventana = ''.join(contenido[max(0, i - contexto):i])
                    if 'mejorDistancia' in ventana:
                        continue
                fallos.append((clave, os.path.basename(ruta), i + 1, linea.strip()[:110], aviso))
    return fallos


def aviso_migracion():
    """I9: si el diff pendiente toca lo que se construye, tiene que subir CURRENT_LAYOUT."""
    try:
        tocados = subprocess.run(['git', 'diff', '--name-only', 'HEAD'], cwd=RAIZ,
                                 capture_output=True, text=True, check=True).stdout
    except Exception:
        return None
    if 'VillageGenerator.java' not in tocados:
        return None
    diff = subprocess.run(['git', 'diff', 'HEAD', '--',
                           'src/main/java/com/chipoodle/devilrpg/world/VillageManager.java'],
                          cwd=RAIZ, capture_output=True, text=True).stdout
    if 'CURRENT_LAYOUT = ' in diff:
        return None
    return ('I9', 'VillageGenerator.java', 0,
            'se toca lo que se construye y el diff no sube CURRENT_LAYOUT',
            'Si el mundo ya construido tiene que rehacerse, sube CURRENT_LAYOUT '
            '(y CURRENT_HOUSES si cambian las casas).')


def main():
    estricto = '--strict' in sys.argv
    fallos = revisar()
    migracion = aviso_migracion()
    if migracion:
        fallos.append(migracion)
    if not fallos:
        print('lint aldea: OK (sin patrones peligrosos)')
        return 0
    for clave, nombre, linea, texto, aviso in fallos:
        print('%s  %s:%d  %s' % (clave, nombre, linea, texto))
        print('     -> %s' % aviso)
    print('\n%d aviso(s)' % len(fallos))
    return 1 if estricto else 0


if __name__ == '__main__':
    sys.exit(main())
