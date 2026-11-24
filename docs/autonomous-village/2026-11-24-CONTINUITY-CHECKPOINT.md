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
| L1 | Sin limpieza automática del libro al morir el propietario; la estación puede quedar reservada hasta `forgetJobSite`. | `cargarDueniosDePuestos` no borra vivos; no hay hook de muerte; C4/POI no matan aldeanos. | Estación libre pero reservada; cobertura futura puede caer. | Test de muerte + recarga: el libro debe limpiarse y la estación reclamarse. |
| L2 | Duplicado con keeper no cargado: se limpian memorias cargadas sin liberar ticket; F1 lo declara `NO_MEDIDO`. | `reconciliarPuestosDuplicados`, `poi-final-20261124-0305` F1. | Posible JOB sin ticket si el borrado era el titular. | Fixture F1 con keeper en chunk no cargado y comprobación de ticket/ghost. |
| L3 | `restaurar` con `yaDeOtro` no restaura al dueño del libro si otro aldeano cargado tiene el JOB. | `restaurarDueniosDelLibro`. | El titular persistido puede quedarse sin estación hasta que el otro la suelte. | Test de dos aldeanos, uno con JOB no titular y ledger del otro. |
| L4 | `OwnerPos` no se refresca al moverse el propietario. | `claimJobSite(..., ownerPos)` solo al reclamar. | La restauración puede esperar a que el dueño se cargue por otra vía; no hay pérdida por el no-forget. | Guardar posición periódica y test de dueño que se aleja antes de recargar. |
| L5 | POI no mide horas de chunks descargados ni jugador conectado. | `POI` dura ~2 min/fase, sin jugador. | Comportamiento a largo plazo no demostrado. | Campaña POI con ciclos largos y jugador conectado. |
| L6 | Navegación runtime con una entidad por escenario. | `nav-final-20261124-0255`, 21 escenarios. | No cubre multientidad, laberintos largos, combate, agua ni puertas. | Matriz multientidad y escenarios compuestos. |
| L7 | fastutil sin A/B runtime. | A/B sintético 750 rutas idénticas; campaña real sin baseline anterior. | Ganancia runtime no cuantificada; no se declara. | A/B de 6+ ejecuciones por brazo, mismo mundo y rutas. |
| L8 | A2 no se reejecutó en el cierre. | Excluido expresamente por el usuario; C4/NAV cubren el camino actual. | Regresión A2 no medida. | Campaña A2 acotada solo si el análisis de dependencias la justifica. |

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
