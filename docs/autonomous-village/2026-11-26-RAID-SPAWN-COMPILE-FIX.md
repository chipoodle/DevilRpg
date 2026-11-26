# Corrección de compilación — spawn escalonado de asedio — 26-nov-2026

## Resultado de la corrección

- SHA con fallo: `f5c5fcad700e794022d34e25f72c636f54947561`
- SHA corregido: `3882b61a67739b72c43528112efbfdd8ff47ba72`
- Archivo: `src/main/java/com/chipoodle/devilrpg/world/VillageManager.java`
- Cambio: se eliminó el fragmento residual `ro, la aldea cae."), false);` y las dos llaves duplicadas que le seguían.
- Se actualizó el comentario del radio para indicar 68/70/73 con la valla en 62.
- El diff correctivo sobre el SHA fallido afecta exclusivamente a ese archivo: 1 inserción y 4 eliminaciones.

## Verificación de contenido remota

Inspección del archivo en GitHub sobre el SHA corregido:
- El fragmento truncado no aparece.
- El bloque de `displayClientMessage` queda completo y con sus llaves balanceadas en el contexto inspeccionado.
- `WAVE_SPAWN_MIN = FENCE_RADIUS + 6` permanece.
- `WAVE_SPAWN_MAX = FENCE_RADIUS + 11` permanece.
- `spawnWaveAttacker(level, d)` permanece.
- Los cinco reintentos de oleada vacía y el decremento de 100 ticks permanecen.
- El método anterior con bucle de spawn no se restauró.

## Validación pendiente

No se ejecutaron `compileJava`, `compileTestJava`, `test`, lint ni runtime. El dispositivo CHRIS-PC aparece online, pero Desktop Commander reporta 0% de llamadas remotas disponibles este mes. No afirmar compilación PASS hasta que Harness ejecute los gates sobre el SHA exacto:

`3882b61a67739b72c43528112efbfdd8ff47ba72`

Runtime/performance permanecen NOT_RUN. No se modificaron los cambios locales preservados ni archivos de Harness.
