# Checkpoint de continuidad — 24-nov-2026

**Este documento no abre campañas.** Fija el estado de referencia y el protocolo para las próximas sesiones.

## 1. Estado de referencia

| Elemento | Valor |
|---|---|
| Rama | `feature/autonomous-village` |
| HEAD final | `18266502` (docs-only sobre el código probado) |
| SHA de código probado | `1016a7cf` |
| Campaña anterior | **COMPLETE para el alcance autorizado de cierre** |
| C4 | `build/c4n/c4n-20261124-0250` → `C4N_PASS` 3/3, cobertura 11/11 |
| POI | `build/av/poi-final-20261124-0305` → `POI_PASS` 4/4 |
| NAV | `build/av/nav-final-20261124-0255` → `NAV_PASS` 21/21 |
| Tests | `gradlew test` 525/32/39/253/15, política, lint y `git diff --check` verdes |

Las tres campañas runtime se ejecutaron con SHA `1016a7cf`. `HEAD 18266502` solo añade documentación.
No se debe mezclar “HEAD documental” con “SHA probado”.

Commits de cierre: `419dcf3b`, `1016a7cf`, `d277ca90`, `18266502`.
Evidencias: `build/c4n/**`, `build/av/**`; no se borran.

## 2. Limitaciones de deuda técnica

No son defectos abiertos ni autorizan una campaña por sí mismas.

| # | Limitación | Evidencia actual | Riesgo | Prueba futura posible |
|---|---|---|---|---|
| L1 | Sin limpieza automática del libro al morir el propietario; la estación puede quedar reservada hasta `forgetJobSite`. **RESUELTO 24-nov (`7db821ea`)**: hooks de muerte y conversión + `releaseJobSitesOwnedBy`; ver `2026-11-24-L1-JOB-SITE-LEDGER-DEATH-CLEANUP.md`. | `cargarDueniosDePuestos` no borra vivos; ahora `VillageAgentRegistry.onDeath`/`onConversion` limpian el libro. C4/POI no matan aldeanos. | Estación libre pero reservada para datos históricos anteriores al fix; el runtime de muerte real no se midió. | Test de muerte + recarga: el libro debe limpiarse y la estación reclamarse (semántica cubierta por `VillagePersistenceIntegrationTest` bloque 11; falta runtime de entidad). |
| L2 | Duplicado con keeper no cargado: se limpian memorias cargadas sin liberar ticket; F1 lo declara `NO_MEDIDO`. | `reconciliarPuestosDuplicados`, `poi-final-20261124-0305` F1. | Posible JOB sin ticket si el borrado era el titular. | Fixture F1 con keeper en chunk no cargado y comprobación de ticket/ghost. |
| L3 | `restaurar` con `yaDeOtro` no restaura al dueño del libro si otro aldeano cargado tiene el JOB. **RESUELTO 24-nov (`e9afbdfc`)**: `planificarRestauracion` limpia la memoria ajena y devuelve el puesto al titular; ver `2026-11-24-L3-JOB-SITE-RESTORE-OWNER.md`. | `restaurarDueniosDelLibro` ya no se rinde ante `yaDeOtro`. | El titular puede quedarse sin estación hasta que otro la suelte (datos históricos/ejecución runtime no medida). | Política pura 54 aserciones; falta fixture runtime de dos aldeanos reales. |
| L4 | `OwnerPos` no se refresca al moverse el propietario. **RESUELTO 24-nov (`e335d867`)**: `cargarDueniosDePuestos` refresca al dueño cargado; ver `2026-11-24-L4-JOB-SITE-OWNER-POSITION-REFRESH.md`. | `claimJobSite(..., ownerPos)` se llamaba solo al reclamar; ahora también en el latido de aldea si el dueño está cargado. | Sin pérdida de dueño; el refresco es cada 10 s, no cada tick. | Persistencia 553 aserciones; falta runtime de alejamiento/descarga. |
| L5 | POI no mide horas de chunks descargados ni jugador conectado. | `POI` dura ~2 min/fase, sin jugador. | Comportamiento a largo plazo no demostrado. | Campaña POI con ciclos largos y jugador conectado. |
| L6 | Navegación runtime con una entidad por escenario. | `nav-final-20261124-0255`, 21 escenarios. | No cubre multientidad, laberintos largos, combate, agua ni puertas. | Matriz multientidad y escenarios compuestos. |
| L7 | fastutil sin A/B runtime. | A/B sintético 750 rutas idénticas; campaña real sin baseline anterior. | Ganancia runtime no cuantificada; no se declara. | A/B de 6+ ejecuciones por brazo, mismo mundo y rutas. |
| L8 | A2 no se reejecutó en el cierre. | Excluido expresamente por el usuario; C4/NAV cubren el camino actual. | Regresión A2 no medida. | Campaña A2 acotada solo si el análisis de dependencias la justifica. |
| **F1** | **Hambre a 16 bocas en 3 días.** El soak oficial 72k dio 5 muertes por hambre; las trazas muestran a los cinco **yendo a la taberna** (4 atascados en `630,63,530`, uno en la puerta `628,66,529`), 4 camas para 16, y `repartirRaciones` no reduce el hambre personal. | `build/soak/soak-final-20261124-1120`; log `ha muerto de hambre`; `2026-11-24-SOAK-MULTIDAY-CAMPAIGN.md` §4. | La aldea no es autosuficiente a 3 días; la cobertura cae con cada baja. | Medir ruta a la taberna, reparto y cadencia de hambre; corregir la causa demostrada y repetir 72k con **0 muertes por hambre**. |

## 3. Protocolo de continuidad

### Regla 1 — Un objetivo principal por campaña

Cada campaña debe declarar: objetivo, alcance, subsistemas/archivos, criterios de aceptación, pruebas, límite de
iteraciones/tiempo, condiciones de parada y entregables. Las tareas auxiliares solo se aceptan si son dependencias
directas. No se añaden objetivos independientes por iniciativa del agente.

### Regla 2 — Autonomía dentro del alcance

El agente puede analizar, diseñar, implementar, corregir, compilar, probar, revisar y repetir sin pedir permiso por
cada cambio reversible que esté dentro del alcance aprobado.

### Regla 3 — Bucles delimitados

`ANALIZAR → IMPLEMENTAR → COMPILAR → PROBAR → DIAGNOSTICAR → CORREGIR → REPETIR`.

Predeterminado: **hasta tres ciclos** de diagnóstico/corrección por defecto. Si no se resuelve, conservar evidencias
y presentar alternativas; no ampliar alcance ni lanzar campañas runtime adicionales fuera de los criterios.
Un bloqueo en una tarea no impide avanzar en otra tarea ya autorizada.

### Regla 4 — Equipos multiagente

Cuando el objetivo lo justifique: arquitectos, programadores, testers y revisores independientes. Usar
`qwen3.5:9b` (y `qwen3.8:27b` si está disponible) como apoyo, verificando sus propuestas. No crear agentes
innecesarios para tareas pequeñas. El coordinador revisa la integración.

### Regla 5 — Revisión independiente

Los cambios importantes los revisa un agente distinto del implementador, buscando regresiones, persistencia,
duplicación de responsabilidades, invariantes, pruebas insuficientes, resultados artificiales y cambios innecesarios.

### Regla 6 — Evidencia por nivel y SHA

Distinguir PASS de compilación, tests, políticas, integración, runtime y aceptación. Cada resultado se asocia al
SHA exacto. No se declara un comportamiento validado si solo compila.

### Regla 7 — Parada

Al completar: revisar criterios, ejecutar pruebas, revisar cambios, commit local si está autorizado, actualizar
checkpoints, entregar informe y detenerse. No iniciar otra campaña automáticamente.

## 4. Protección del proyecto

- No push, merge externo, force push ni cambio de rama sin autorización.
- No `reset --hard`, no `clean`, no borrados masivos.
- No eliminar mundos, logs, respaldos ni evidencias.
- No sobrescribir trabajo ajeno.
- Preservar `.a2-backups/`, `src/foodTest/`, `tools/a2n/`, `tools/a5-closure/` y artefactos diagnósticos.
- A2 y C4 siguen **ACCEPTED**; no reabrirlos por hipótesis.

## 5. Documentos de continuación

- `NEXT-SESSION.md` — handoff y estado inmediato.
- `ESTADO-ACTUAL.md` — estado resumido.
- `AUTONOMOUS-VILLAGE-MASTER-PLAN.md` — hoja de ruta.
- `TEST-MATRIX.md` — qué prueba cada gate.
- `aldea-invariantes.md` — invariantes I1–I399.
- `2026-11-24-TECHNICAL-CLOSURE-CAMPAIGN.md` — informe de cierre y evidencias.
- Este checkpoint — protocolo y deuda técnica.

**Fin del checkpoint. No iniciar desarrollo desde este documento.**

## 6. Actualización 24-nov-2026 (posterior al cierre)

La auditoría `2026-11-24-PENDING-AUDIT.md` (`2b32ba08`) clasificó los pendientes y eligió **L1** como
primer objetivo: el libro de puestos no se limpiaba al morir o convertirse el titular. Se corrigió en
`7db821ea` con hooks en `VillageAgentRegistry` y pruebas headless (persistencia **544**, ownership
**39**); `test`, lint y `git diff --check` verdes. El límite está declarado: **no hay corrida runtime de
un aldeano muriendo**. L2, L3, L4, L5–L8 y el soak multi-día siguen abiertos. Informe:
`2026-11-24-L1-JOB-SITE-LEDGER-DEATH-CLEANUP.md`.

**P1 (skills) cerrado en su parte ejecutable (`0a9f8670`).** El árbol de skills ya no usa `Minecraft`
(lectura por classpath) y los iconos se resuelven contra `BuiltInRegistries.ITEM`; nuevo
`skillCapabilityTest` dentro de `test` (**376** aserciones: 33/33 skills, 36 nodos, NBT, compatibilidad
de partidas viejas). Límites: **sin servidor dedicado con jugador ni UI de cliente reejecutada**; el
resource pack ya no sobrescribe el árbol; la doble ejecución cliente/servidor sigue sin revisar. Informe:
`2026-11-24-P1-SKILLS-SERVER-SAFE-TREE-AND-TESTS.md`.

**L3 corregido a nivel de decisión y cableado (`e9afbdfc`).** `restaurarDueniosDelLibro` ya no se rinde
ante `yaDeOtro`: la política pura `planificarRestauracion` devuelve el puesto al titular del libro y
ordena limpiar las memorias de los aldeanos cargados que no lo son, sin soltar el ticket. Ownership
**54** aserciones (+15). **Límite:** sin fixture runtime de dos aldeanos reales; C4/POI no reejecutados.
Informe: `2026-11-24-L3-JOB-SITE-RESTORE-OWNER.md`.

**L4 corregido (`e335d867`).** `cargarDueniosDePuestos` refresca `OwnerPos` para el dueño cargado, vivo
y no removido (mismo titular, sin tocar tickets); si no está cargado, se conserva la carga por la última
posición. Persistencia **553** aserciones (+9). **Límite:** sin runtime de alejamiento/descarga.
Informe: `2026-11-24-L4-JOB-SITE-OWNER-POSITION-REFRESH.md`.

**Soak multi-día (24-nov, `552e9b57`).** Nuevo arnés `tools/soak` (72k ticks, sprint, mundo desechable,
sin jugador). Encontró una **asfixia real del ganadero** mientras `Recogiendo el corral`; corregida con
`desatascarSiEstaEncajado` en cada tick del `VillageAnimalFarmGoal`. El run oficial 72k dio **0
duplicados, 0 huérfanos, 0 inactivos permanentes**, pero **5 muertes por hambre** (población 16→11,
cobertura 8/11, comida 6–77, inicial 20). **No se acepta el soak**: la sostenibilidad alimentaria a 16
bocas es el objetivo abierto **F1**; el arnés sin jugador no ejecuta `manageNearby` (reposición).
Informes: `2026-11-24-SOAK-MULTIDAY-CAMPAIGN.md` y `2026-11-24-ANIMAL-FARM-SUFFOCATION-FIX.md`.
