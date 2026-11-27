# Guard recovery — Harness Run 011 (27-nov-2026)

## Resultado

**PARTIAL / BLOCKED**: diagnóstico de código y estado; no se ejecutó una recuperación runtime. El escenario reutilizó el mismo mundo runtime de Run 010. No es una nueva copia independiente y no debe presentarse como una nueva reproducción limpia.

- Branch: `feature/autonomous-village`
- Tested SHA: `0349ddffe0848df8955c3112e0b3ac90a71b7f8c`
- Guardia: Ximeno, UUID `5b42b9cf-8e30-4c9f-ad16-2ad31491a2e5`
- Posición: `637.525, 62.521, 577.897`; block position `637,62,577`
- Centro: `566,64,566`
- Distancia horizontal al centro: `72.508`
- Límite de retorno previo: `MAX_DISTANCE_FROM_CENTER=88`; por tanto no activaba el retorno por distancia.
- Estado: pies en agua, cabeza aire, soporte agua; `MARCHING=false`, `EQUIPPING=false`, `COMBAT=none`.

## Hallazgo causal

- `VillagerGuardGoal.canUse()` calculaba `puntoDeGuardia(level)` y devolvía false cuando era null.
- La recuperación de terreno solo se invocaba desde `tick()`, después de que el goal hubiera arrancado y tuviera destino estratégico.
- Los rellanos norte `637,64,574` y oeste `634,64,577` pasaban las validaciones individuales, pero no se seleccionó un punto de apoyo ni se midió un plan completo.
- Resultado observado: `canUse=false`, destino nulo, Brain `WALK_TARGET` vacío, recuperación NO.
- El centro no era el problema: el guardia estaba dentro del límite de 88 bloques.

## Límites de evidencia

- Run 011 reutilizó el mundo runtime de Run 010; no prueba reproducibilidad desde una copia nueva.
- No hubo plan completo de rampa, colocación, movimiento, salida ni retorno.
- No hubo save/reload.
- No se modificó producción ni mundo: `PRODUCTION_MODIFIED=false`, `DIFF=0`.
- No inferir PASS de recuperación física a partir de candidatos de rellano válidos.

## Resolución de ingeniería posterior

La implementación posterior añade un estado persistente de retorno y activa el mismo `VillagerGuardGoal` cuando no existe puesto de patrulla y el guardia está en terreno inseguro o fuera del perímetro. Reutiliza `GuardTerrainRecovery` hacia `VillageManager.casillaDeLaCalle`, mantiene la identidad y excluye temporalmente al guardia del reparto dinámico. Ver `2026-11-27-GUARD-RETURN-AND-REINTEGRATION.md`.

