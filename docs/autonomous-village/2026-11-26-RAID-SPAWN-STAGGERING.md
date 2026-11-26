# Raid spawn: anillo exterior y oleada escalonada — 26-nov-2026

## Base y publicación

- Base: `7e147343828d9367bb2dca0fc4b19e2cc1786b3c`
- Código publicado: `aeb00a5a00c1a201c65d0b7311cafb4998631722`
- Archivo de producción modificado: `src/main/java/com/chipoodle/devilrpg/world/VillageManager.java`
- No se modificaron `VexSpawnProfile.java`, archivos de Harness, mundos ni datos locales.

## 1. Spawn fuera de la coronación

- `WAVE_SPAWN_MIN` pasa de `FENCE_RADIUS + 3` a `FENCE_RADIUS + 6`.
- `WAVE_SPAWN_MAX` permanece en `FENCE_RADIUS + 11`.
- Con FENCE_RADIUS=62, el rango efectivo es 68–73.
- Se mantienen las reglas de exterior, chunk cargado, apoyo firme, caja sin colisión y ausencia de fluido/peligro.

## 2. Spawn escalonado

El anterior `spawnWave` buscaba sitio, creaba e insertaba todos los asediadores en un bucle dentro de un único tick. Se reemplazó por:
- `iniciarOleada`: fija total, cota y contadores.
- `spawnWaveAttacker`: procesa exactamente un índice pendiente por llamada/tick; una búsqueda de sitio y como máximo una inserción.
- `finalizarOleada`: emite el resumen agregado al terminar.

Cada atacante recibe el marcador de brillo al insertarse. El libro mayor UUID y `worldSiegeIndex` solo se escriben después de `addFreshEntity` exitoso.

La oleada mantiene su tamaño calculado (`DEFAULT_WAVE + min(objectiveIndex*2, MAX_WAVE_EXTRA)`). Los intentos fallidos de sitio/creación/inserción se contabilizan y no detienen el resto de la oleada.

## 3. Contrato de reintentos

- `waveSpawned` solo se activa después de completar todos los intentos y si al menos un atacante fue insertado.
- Una oleada totalmente vacía conserva los cinco reintentos, separados por 100 ticks, y la cancelación sin recompensa.
- La lista de UUID solo contiene entidades insertadas realmente.
- El estado escalonado vive en `VillageDefense`, que es el estado transitorio del asedio; no se introdujo un formato nuevo de guardado.

## 4. Rendimiento

El cambio reparte entre ticks las búsquedas de sitio, creación e inserción de los atacantes. No modifica el guardado manual del mundo. El retraso observado inmediatamente después de “Saving and pausing game…” sigue atribuido al guardado/pausa del servidor integrado según el log presentado, no a un ciclo de navegación.

## 5. Validación pendiente

ChatGPT publicó el cambio directamente en la rama canónica, pero no ejecutó Gradle ni Minecraft runtime.

Harness debe validar exactamente `aeb00a5a00c1a201c65d0b7311cafb4998631722`:
1. `compileJava`, `compileTestJava`, `test` y suite de políticas.
2. lint estricto y `git diff --check`.
3. Sonda de política/estructura que compruebe rango 68–73 y un solo intento por tick.
4. Runtime de asedio: confirmar spawn 68–73, ocho atacantes en ocho ticks (si total=8), primer waypoint exterior no trivial, movimiento hasta el muro y entrada real.
5. Comprobar reintento de oleada vacía y contabilidad de UUID, sin usar FakePlayer como sustituto de cliente real.

No declarar aceptación funcional hasta observar el cruce real del muro.
