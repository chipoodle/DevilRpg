# Herramientas del proyecto (versionadas)

**Todo lo de esta carpeta está en git**, así que `gradlew clean` (o borrar `build/`) **no se lo lleva**.
Los ~130 scripts sueltos de diagnóstico siguen en `build/`, que está en `.gitignore`: esos sí se pierden.
Aquí está lo que merece sobrevivir.

| Herramienta | Qué hace |
|---|---|
| `lint_aldea.py` | Lint de las reglas de aldea (invariantes I*). **Se pasa antes de cada commit de aldea**: `python tools\lint_aldea.py --strict`. Sale con código 1 si algo falla |
| `audita_aldea.py` | **Auditoría de las aldeas del guardado**: faroles y vallas flotando, cofres tapados, puertas incompletas y camas sueltas. Saca las aldeas de `data/devilrpg_villages.dat` (índice, centro y cota), así que **no hay nada clavado a una aldea concreta**. Sale con código 1 si encuentra algo |
| `nbtdump.py` | Lector mínimo de NBT (`load`, `R`+`payload`, `walk`). `build/nbtdump.py` es un **puente** a este, para que los scripts de `build/` sigan funcionando con **una sola copia** |
| `finduuid.py` | Busca UUIDs en los `.mca` de entidades y dice en qué chunk está cada uno (para comprobar que un minion está muerto de verdad antes de olvidarlo) |
| `recover/NbtTool.java` | Diagnóstico y reparación del NBT del jugador con las **clases reales de Minecraft**, sin arrancar el juego (volcar, `--find`, `--snbt`, `--restore-minions-from`, `--forget`, `--inject-into`). Las librerías de `build/recover/libs` **no** se versionan (pesan): ver `docs/design-roadmap.md` §6 |

Los que usan rutas relativas (`finduuid.py`) se corren **desde la raíz del proyecto**; `audita_aldea.py`
funciona desde cualquier sitio porque resuelve las rutas desde su propio archivo.

## Ejemplos

```powershell
# Lint de aldea (antes de commitear)
python tools\lint_aldea.py --strict

# Auditoría: todas las aldeas vivas del guardado por defecto
python tools\audita_aldea.py

# Solo una aldea, o incluir las caídas, o solo la tabla final
python tools\audita_aldea.py --aldea 2
python tools\audita_aldea.py --caidas
python tools\audita_aldea.py --resumen

# Una aldea a mano, sin leer el guardado (centro y cota a mano)
python tools\audita_aldea.py --centro 1414 1414 --cota 119

# NBT del jugador (ojo: en singleplayer manda level.dat, ver docs/design-roadmap.md §6)
java -cp "build\classes\java\main;build\recover\libs\*" tools\recover\NbtTool.java "run\saves\New World (1)\level.dat" --find

# ¿Sigue vivo ese minion?
python tools\finduuid.py a1b2c3d4-....=lobo
```
