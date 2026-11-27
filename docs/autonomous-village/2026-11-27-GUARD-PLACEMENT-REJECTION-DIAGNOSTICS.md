# Diagnóstico exacto de rechazos de colocación — rescate de guardias (27-nov-2026)

## Estado previo

Harness RUN 012 probó `d643e5d7d5dbfd73218eb31fb81330874e9c2dda`:
- STATIC/COMPILE/TESTS/POLICY_TESTS/STRICT_LINT/DIFF_CHECK: PASS.
- Escenario A: FAIL; Ximeno no salió del agua.
- Cuatro planes rechazados; resumen `aligned=4 invalidLandings=2 unsupportedColumns=0 invalidPlacements=2 overBudget=0`.
- La compuerta de rescate sí abrió (`brainMatches=true`, `naturalRouteReachable=false`).
- No se identificaron las coordenadas ni la condición concreta de los dos rechazos.

## Cambio diagnóstico

Commit productivo: `0024196c0811f568a10b01af70bb8f275a90236d`.

Archivo: `src/main/java/com/chipoodle/devilrpg/entity/goal/GuardTerrainRecovery.java`.

Se conserva la decisión y el comportamiento de aceptación/rechazo. No se cambian:
- reglas de `celdaColocable`;
- comprobaciones de protección, colisión, fluidos, bloque-entity ni espacio corporal;
- dirección/rise/step, búsqueda de soporte, rellano, presupuesto, cooldowns o cadencia;
- navegación, destino estratégico, prioridades, umbrales, A2/C4 ni persistencia.

La validación se descompuso en `motivoCeldaNoColocable`, que mantiene las mismas condiciones y devuelve la primera razón concreta:
`NULL_POSITION`, `CHUNK_NOT_LOADED`, `PROTECTED_POSITION`, `BLOCK_ENTITY`, `PLACEMENT_COLLISION` o `UNSUPPORTED_FLUID`.

Cuando un candidato se rechaza, ahora se registra:
- dirección, rise, step y coordenada exacta `place`;
- razón concreta y estado/protección/fluido de la celda;
- coordenadas, estados, fluidos y colisión de `head` y `upperBody`.

El contador `invalidPlacements` y las reglas de rechazo permanecen intactos. La nueva traza permite diferenciar si el rechazo viene de la celda a colocar, de protección o de falta de espacio corporal. No se debe inferir todavía cuál será la causa hasta recibir el siguiente reporte.

## Validación pendiente

ChatGPT no ejecutó Gradle, tests, lint ni Minecraft runtime. Harness debe:
1. Sincronizar de forma segura el HEAD remoto exacto; confirmar que contiene `0024196c0811f568a10b01af70bb8f275a90236d`.
2. Ejecutar compile/test/policyTest, lint estricto y diff-check.
3. Si todo pasa, repetir únicamente escenario A en un mundo nuevo y desechable, con el mismo setup reproducible de Ximeno.
4. Capturar todas las líneas `[GuardTerrain] placement rejected` junto con `plan rejected`, coordenadas y estados.
5. Confirmar que el mundo original mantiene su hash; preservar runs/copies previos. No ejecutar B–F, Save/Reload ni negativos en esta ronda.

Harness no modifica producción/tests/documentación, no crea commits ni hace push. No usar teletransporte, edición manual de posición/Brain/tags ni debilitar reglas de seguridad. A2/C4 permanecen intactos.
