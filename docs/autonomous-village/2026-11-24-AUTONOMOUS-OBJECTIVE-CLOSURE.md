# Cierre del objetivo autónomo — 24-nov-2026

**Rama:** `feature/autonomous-village`
**SHA final:** `43afe1e6`
**Soak final:** `soak-final-head-20261124-2200` → **`SOAK_PASS`**
**Estado:** objetivo de auditoría + resolución priorizada **CERRADO**

---

## 1 · Resultado final

| Métrica del soak final (HEAD `43afe1e6`) | Valor |
|---|---|
| Ticks | 72.000 |
| Resultado | **`SOAK_PASS`** |
| Muertes | **0** |
| Población final | 30 |
| Profesionales con puesto | **11/11** |
| Ausentes / duplicados / huérfanos | 0 / 0 / 0 |
| Sin puesto | máximo 1 **transitorio** (1 muestra; umbral 30) |
| Inactivos permanentes | **0** |
| Comida | 66 (mín. 66) |
| Guardado | **OK**, sin excepciones no controladas |

El soak también pasó en dos semillas con `bcf98f70` (`soak-f4-confirm` y `soak-f4-stability`). El run
final confirma que los cambios posteriores (L2, autoridad de red, skills) no rompen el pueblo.

## 2 · Qué se auditó y resolvió

### Auditoría
- `2026-11-24-PENDING-AUDIT.md`: 33/33 skills implementadas, A2/NAV aceptados, L1–L8, soak ausente,
  documentación obsoleta.
- Evidencia y clasificación por feature; sin inventar backlog.

### Defectos corregidos
| ID | Defecto | Corrección |
|---|---|---|
| L1 | El libro no liberaba puestos al morir/convertirse | `onDeath`/`onConversion` + `releaseJobSitesOwnedBy` |
| L2 | Duplicado con titular no cargado | `limpiarDuplicados` (59 aserciones; no libera ticket) |
| L3 | Restauración del titular frente a `yaDeOtro` | `planificarRestauracion` |
| L4 | `OwnerPos` no se refrescaba | refresco al cargar dueños |
| F1 | Hambre: la ración no alimentaba | `reducirHambre`, ración al rendirse y a hambre crítica |
| F2 | Caída al volver al pueblo | `VillageFallPolicy` (sin caída fuera de asedio) |
| F3 | `JOB_SITE` fuera del muro | inválido y liberado; 11/11 desde el inicio |
| F4 | Profesional parado sin goal | `despertarSiEstaParado` |
| Skills | humano→lobo a 0 maná | `WerewolfManaPolicy` |
| Skills | payload directo ejecutaba cualquier skill | `DirectSkillExecutionPolicy` |
| Recursos | maná/stamina/auxiliar/minions autoridad del cliente | S→C + regeneración de servidor |
| L9 | árbol/experiencia sin validar | `SkillTreeSpendPolicy` (máx. 1 punto, servidor consume) |

### Soak
- Arnés `tools/soak` (72k, sprint, chunks forzados, métricas de población/cobertura/comida/bajas).
- Aceptado y estable en dos semillas; final PASS en el HEAD.

## 3 · Evidencia de gates

| Gate | Resultado |
|---|---|
| `gradlew test ...` | **BUILD SUCCESSFUL** (1m22s) |
| Persistencia | **553** |
| Ownership | **59** |
| Skills | **392** |
| NAV contrato | 253 |
| POI autotest | 15 |
| `lint_aldea.py --strict` | OK |
| `git diff --check` / `git show --check` | 0 errores |
| Soak final 72k | **`SOAK_PASS`** |

## 4 · Límites declarados (no bloqueantes del objetivo)

- **L5/L6:** el arnés NAV sigue sin escenarios `multientidad_*` y el POI sin horas/jugador; el soak
  aporta 30 aldeanos/3 días y POI sin duplicados como evidencia parcial.
- **L7:** fastutil sin A/B runtime; no se declara ganancia.
- **L8:** A2 no reejecutado; **excluido expresamente por el usuario**.
- **Cliente real de skills:** no hay arnés de cliente; UI/HUD/doble `onKeyInput` no se ejecutan. Los
  handlers y políticas están probados headless.
- **A1/ramas:** decisión del dueño; no se tomó ninguna decisión de rama ni push.

## 5 · Veredicto

La auditoría está completa y **todos los defectos autónomos priorizados están corregidos o
declarados**; el **soak multi-día pasa en el HEAD final** (`43afe1e6`) y en dos semillas con
`bcf98f70`. Los puntos abiertos son **limitaciones de evidencia, el cliente real y decisiones del
dueño**, no defectos del código auditado. **Objetivo cerrado.**
