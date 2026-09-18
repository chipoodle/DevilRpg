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
  I10 Nivelar la huella de una PARCELA (`nivelarHuella(...PLOT_...)`) recorta el terreno que
      sobresale de la cota y se lleva por delante los cultivos de las celdas altas: salen como
      OBJETOS tirados por toda la parcela (el fallo que el jugador vio DOS veces). El nivelado
      tiene que estar guardado antes con `bancalHecho()` o `hayCultivos()`.
  I11 Contar bichos "dentro de la aldea" solo con la distancia HORIZONTAL: un esqueleto en una
      cueva bajo la plaza congelaba el latido del pueblo y un asediador en una cueva hacia CAER
      la aldea sin que el jugador pudiera verlo. Todo recuento pasa por `dentroDelRecinto`
      (recinto en XZ + banda de altura sobre la cota).
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
    # Goals de las etapas B-E: entraron despues de que el lint ya existiera y hasta ahora no estaban
    # vigilados (el lenador, el ganadero, el lenador de la madera del herrero, el cocinero y la milicia).
    # Son justo los que mas se mueven por el pueblo, asi que son los que mas pisan I3 e I6.
    os.path.join(GOALS, 'VillagerAnimalFarmGoal.java'),
    os.path.join(GOALS, 'VillagerCookGoal.java'),
    os.path.join(GOALS, 'VillagerGuardGoal.java'),
    os.path.join(GOALS, 'VillagerLumberjackGoal.java'),
    os.path.join(GOALS, 'VillagerSmithGoal.java'),
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
    # I10 nace de un fallo que volvio DOS veces: el nivelado de la huella de un bancal RECORTA el terreno que
    # sobresale de la cota y, en una parcela en cuesta (una aldea de montana), se llevaba por delante los cultivos
    # de las celdas altas: salian como OBJETOS tirados por toda la parcela. Cualquier nivelado de una PARCELA
    # (el que cita PLOT_WIDTH/PLOT_DEPTH) tiene que estar guardado antes con bancalHecho() o hayCultivos().
    ('I10', [os.path.join(PAQUETE, 'world', 'VillageGenerator.java')],
     r'nivelarHuella\([^;]*PLOT_',
     'Nivelar la huella de una PARCELA recorta el terreno y se lleva los cultivos (salen como items por la '
     'parcela): pregunta antes con bancalHecho() o hayCultivos().', 8),
    # I11 nace de la CAIDA DE UNA ALDEA EN JUEGO (aldea 1, cota 95): un bicho "dentro de la aldea" se contaba
    # solo con la distancia horizontal, asi que un esqueleto en una cueva bajo la plaza congelaba el latido del
    # pueblo (ni cultivos, ni comida, ni reparaciones, ni repoblacion) y un asediador que se metia en una cueva
    # hacia CAER la aldea sin que el jugador pudiera verlo. Medido en su guardado: 24 monstruos "dentro" con la
    # regla vieja, 18 de ellos en cuevas (y=5..89); con la altura, 6. Todo recuento de bichos "dentro de la
    # aldea" pasa por VillageManager.dentroDelRecinto (recinto en XZ + banda de altura sobre la cota).
    ('I11', [os.path.join(PAQUETE, 'world', 'VillageManager.java')],
     r'getEntitiesOfClass\((\w+\.)*Monster\.class|MobCategory\.MONSTER',
     'Recuento de bichos "dentro de la aldea" sin la ALTURA: usa dentroDelRecinto(...) (un bicho en una cueva '
     'bajo la plaza no es un invasor, y hacia caer la aldea).', 12),
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
                # I10: se admite si el nivelado de la parcela esta guardado (bancalHecho / hayCultivos) justo antes.
                if clave == 'I10' and contexto:
                    ventana = ''.join(contenido[max(0, i - contexto):i])
                    if 'bancalHecho' in ventana or 'hayCultivos' in ventana:
                        continue
                # I11: se admite si el recuento usa el ayudante (recinto + altura), delante o detras.
                if clave == 'I11' and contexto:
                    ventana = ''.join(contenido[max(0, i - contexto):i + contexto + 1])
                    if 'dentroDelRecinto' in ventana or 'cotaDeLaPlaza' in ventana:
                        continue
                fallos.append((clave, os.path.basename(ruta), i + 1, linea.strip()[:110], aviso))
    return fallos


def _git(*args):
    """Salida de `git` SIEMPRE en UTF-8.

    Antes se leia con la codificacion del sistema (cp1252 en Windows) y el lint petaba con
    UnicodeDecodeError en cuanto el diff traia un comentario en espanol con acentos: el hilo lector
    de subprocess reventaba, stdout quedaba en None y el aviso de migracion (I9) daba TypeError.
    """
    return subprocess.run(['git', *args], cwd=RAIZ, capture_output=True,
                          encoding='utf-8', errors='replace').stdout or ''


def aviso_migracion():
    """I9: si el diff CAMBIA LO QUE SE CONSTRUYE, tiene que subir CURRENT_LAYOUT.

    Ojo: no basta con tocar `VillageGenerator` (se le añaden ayudantes de SOLO LECTURA a menudo, como
    `puestoDeHerreria`), asi que se miran solo las lineas AÑADIDAS que de verdad construyen algo.
    """
    try:
        tocados = _git('diff', '--name-only', 'HEAD')
    except Exception:
        return None
    if 'VillageGenerator.java' not in tocados:
        return None
    diff_gen = _git('diff', 'HEAD', '--',
                    'src/main/java/com/chipoodle/devilrpg/world/VillageGenerator.java')
    construye = re.compile(r'^\+.*(colocar\(|placeInWorld\(|placeVanillaHouse\(|return center\.offset\(|'
                           r'static final.*(FARM_PLOTS|PLOT_WIDTH|PLOT_DEPTH|KIOSCO_RADIO|KIOSCO_POSTE|'
                           r'HERRERIAS|IGLESIAS|VANILLA_HOUSES|CASAS_GRANDES))')
    if not any(construye.match(l) for l in diff_gen.splitlines()):
        return None
    # Excepcion justificada a mano en la propia linea: `// lint:ok I9 porque ...`. Se usa cuando NO es una
    # construccion nueva que haya que rehacer, sino un RETROFIT en el sitio que arregla lo ya construido con una
    # pasada idempotente (asi el mundo guardado se corrige sin subir la version del trazado).
    if any('lint:ok I9' in l for l in diff_gen.splitlines() if l.startswith('+')):
        return None
    diff_manager = _git('diff', 'HEAD', '--',
                        'src/main/java/com/chipoodle/devilrpg/world/VillageManager.java')
    if 'CURRENT_LAYOUT = ' in diff_manager:
        return None
    return ('I9', 'VillageGenerator.java', 0,
            'se añade CONSTRUCCIÓN y el diff no sube CURRENT_LAYOUT',
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
