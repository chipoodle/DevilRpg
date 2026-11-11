# ESTADO ACTUAL — Handoff de sesión (navegación autónoma A2)

> **Documento de traspaso.** Escrito al cerrar **Stage 6B**. Describe el estado **real y verificado** del
> repositorio en ese momento, no el estado deseado. Léelo antes de tocar nada.
>
> Fecha de captura: **16-oct-2026**.
> Rama: `feature/autonomous-village`.
> HEAD: **`97220be5453b57387002b9bc91935807cc665274`**.
>
> **A2 sigue ABIERTO.** No hay aceptación funcional: **ningún bloque colocado, ninguna travesía verificada y
> ningún destino alcanzado** en el fixture del acantilado.
>
> **Reconciliación 22-oct-2026:** R4 S5, L40 y el spawn del golem quedaron aceptados en sus fixtures, pero **no
> cierran A2**. El S5 es el asedio clásico de `VillageManager.start`, no la navegación propia de este documento.
> La próxima tarea viva está en la sección final de `docs/CONTINUAR.md` (**A1-CLIENT**), no aquí.

---

## A · Misión y dirección arquitectónica

El proyecto desarrolla un sistema de navegación autónoma para los zombis agresivos de DevilRpg. El objetivo
**no** es que pase un fixture concreto:

- Pathfinding 3D consciente del movimiento.
- Restricciones reales de colisión, soporte, holgura, salto y caída segura.
- Rutas conscientes de la construcción, con **colocación real de bloques**.
- **Planes de construcción conectados y multi-paso**.
- **Travesía verificada antes de autorizar** pasos de construcción dependientes.
- **Un único controlador de movimiento autoritativo** mientras la navegación propia está activa.
- Recuperación y replanificación **acotadas** que **preservan la misión original**.

**A\* es el planificador inicial.** *D\* Lite* es un candidato para replanificación dinámica **si las medidas lo
justifican**. No introducir algoritmos sin necesidad demostrada.

**Referencia arquitectónica permanente:** `docs/autonomous-village/01-navigation-architecture.md`
(es un documento **de destino**, no un informe de lo implementado; sus conflictos abiertos están en su §K).
**Léelo antes de proponer cambios arquitectónicos.**

---

## B · Estado real de la implementación

Paquete `src/main/java/com/chipoodle/devilrpg/entity/nav/`:

| Fichero | Líneas | Estado |
|---|---|---|
| `NavState.java` | 30 | **Integrado.** `IDLE, PLANNING, TRAVERSING, OBSTRUCTED, RECOVERING, ARRIVED, FAILED`. |
| `NavRoute.java` | 94 | **Integrado.** `Transition = WALK, STEP_UP, JUMP_UP, DROP, DOOR, BREAK, BRIDGE, BUILD` (con `BUILD` añadido en Stage 4Q). Nodo = celda de bloque; la posición pisable de `BUILD` es su `above()`. |
| `NavObstruction.java` | 176 | **Integrado.** `Kind = PASSABLE, STEP_UP, JUMP_UP, DROP, BREAKABLE, PROTECTED, BLOCKED`; interfaz `Policies` (`maxSafeDrop()=3`, `waterDepthForSafeDrop()`, `isProtected`, `canPlace`); `classify(...)`, `hasSupport(...)`, `fits(...)`, `caidaReal(...)`. |
| `NavPlanner.java` | 268 | **Integrado.** A\* con presupuesto (`devilrpg.nav.budget`, por defecto 900, clamp 50..20000), `COST_BUILD=16.0`, `esConstruible(...)` (privado), invariante de caída segura (Stage 4V) y `rechazadasPorCaida`. |
| `NavExecutor.java` | 772 | **Integrado, pero la construcción NO funciona todavía** (ver §C). Contiene la máquina de construcción (Stage 6A/6B). |
| `NavFeature.java` | 31 | **Integrado.** `-Ddevilrpg.nav.custom=true` o `DEVILRPG_NAV_CUSTOM=1`. **Por defecto APAGADO** (`ENABLED` se resuelve una vez, estático). |

**`AggressiveZombieEntity.java`** (modificado, no commiteado): registra los goals, expone `navExecutor()`,
`navPolicies()` y `navegarConEjecutorPropio(...)`; `aiStep()` da el tick al ejecutor propio **antes** de los goals
cuando el flag está encendido; `setObjective(...)` fija también la misión del plan de construcción.

**Primitiva de colocación:** `setPlacement(...)` enlaza `ponerEscalon(...)` de la entidad con el plan; verifica
aire, dos huecos encima, soporte con colisión y `!protegidoPorLaAldea`.

**Legado que sigue presente:** `AggressiveZombiePathPlanner.java` (planificador antiguo, *untracked*) continúa en
el árbol como **segundo planificador**; su retirada o permanencia sigue **pendiente de decisión** (Stage 4P §B
propone retirarlo cuando quede superseded). La navegación propia **no** está probada en el mundo real: todo lo
medido es en fixtures aislados.

---

## C · Resultado REAL de Stage 6B

**Directorio del run final: `build/a2n/a2n-20261016-130330`** (log en `build/a2n/a2n-20261016-130330/srv.log`).
Comando: modo `stairs`, `-NavCustom true`, presupuesto 900, chunks forzados, fixture Stage 5C.

| Hito | Resultado |
|---|---|
| Geometría del acantilado validada | ✅ **PASA** (cara de aire, soportes y holguras correctos, desnivel exacto 6) |
| Plan de construcción creado | ✅ sí — `planMision = 130,81,-8`, hook alcanzado (`encima=true`) |
| **Primera colocación legal de bloque** | ❌ **0 colocaciones** (`construidas=0`, `colocadoVerif=0`) |
| Verificación del bloque colocado | ❌ nunca se llegó |
| **Travesía física en un tick posterior** | ❌ **0** (`ESCALON-TRAVESADO: 0`, `travesados=0`) |
| Autorización de pasos dependientes | ❌ nunca se llegó |
| **Llegada al destino original** | ❌ `alcanzoDestino=false` (`yFinal=87`, `destinoY=81`) |
| Caída insegura | ✅ ninguna |
| Oscilación acotada | ⚠️ terminó en `RECOVERING`, `rec=6`, `osc=true` (con la misión intacta) |

**Primera transición fallida:** `buscarEscalonDeDescenso()` **nunca devolvió una celda**, así que
`pasoDeConstruccion()` no colocó nada y `FaseEscalon` **nunca salió de `NOT_STARTED`**. El ejecutor terminó en
`state=RECOVERING` con `rej=2`, `budget=0`, y el zombi parado en `127,87,-8` (borde de la meseta).

**Por qué todavía no se sabe la causa raíz:** no se ejecutó el diagnóstico acotado que habría registrado, para
los primeros candidatos, **qué predicado exacto** los rechaza (`isProtected` / `canPlace` / `!isAir(celda)` /
`!isAir(celda.above())` / `!isAir(celda.above(2))` / sin soporte). **Es la siguiente acción** (§F). No se debe
adivinar: es exactamente el método (log acotado del primer rechazo) que resolvió los bloqueos de Stage 4U, 5B y 6A.

> ⛔ **NO afirmar una causa sin evidencia.** A día de hoy **no está establecido** por qué
> `buscarEscalonDeDescenso()` devuelve `null`. **No** se puede atribuir a la holgura de los candidatos, a la
> protección de bloques, a la política de colocación (`canPlace`) ni a la posición de la entidad **sin el log que
> lo demuestre**. Cualquier informe que afirme una de esas causas sin el registro del predicado que falla es una
> hipótesis no medida y debe rechazarse.

Runs de Stage 6A, para trazabilidad:
`a2n-20261016-121208` (hook detrás del tope de recuperación), `…122549` (**`planMision=null`**),
`…123326` (hook activo, 0 escalones). Run de Stage 5C: `…120009`.

**Detalle de implementación de Stage 6B (en `NavExecutor.java`, compila):**

- Se añadió **`soporteConstruido(BlockPos debajo)`**, que exige **las cuatro condiciones** para aceptar un bloque
  construido como soporte:
  1. el bloque **existe AHORA** en el mundo (no basta con figurar en `construidas`),
  2. su **forma de colisión** cumple el requisito real de soporte,
  3. pertenece al **conjunto verificado** por este plan (`construidas`), y
  4. satisface la **dependencia de travesía** (la entidad lo ha pisado).
- Se distingue **`escalonesTravesadosSet`** de **`construidas`**: colocación **no** es travesía; el conjunto de
  travesados solo se puebla en la transición `TRAVERSED`.
- La búsqueda pasó a una **escalera hacia fuera** (baja avanzando), apoyándose en los escalones **ya travesados**
  o en **terreno natural**, en lugar de exigir terreno natural en toda la cara vertical.
- **Compila, pero la prueba de aceptación funcional FALLÓ** (ver §C).

---

## D · Historial de hallazgos establecidos

1. **Stage 5C** validó una **cara vertical de aire real de 6 bloques** y demostró que la transición `BUILD`
   aislada **no podía generar una arista de construcción** para esa cara. Evidencia: `noAire=2`,
   `sinSoporte=4`, y la única celda `esConstruible=true` era la **superficie ya pisable** de la llanura
   (`121,81,-8`, `PASSABLE`), no una celda que necesitara construirse.
2. **Stage 6A** introdujo la máquina de construcción, la separación misión/sub-objetivo, la verificación de
   colocación y el **gating por travesía**. Resultado: **0 colocaciones**, destino no alcanzado.
3. La búsqueda de construcción de Stage 6A **dependía de soporte natural** y no podía iniciar el descenso
   conectado. (Medido: ninguna celda de una cara vertical tiene terreno justo debajo.)
4. **Stage 6B** implementó que un bloque **construido previamente** sirva como soporte **solo si** el bloque
   existe ahora en el mundo, su colisión cumple el requisito, lo colocó y verificó **este plan**, y la entidad
   lo ha **PISADO** (`escalonesTravesadosSet`, distinto de `construidas`). También reorientó la búsqueda a una
   **escalera que baja avanzando**. **La implementación está en el árbol y compila, pero sigue produciendo 0
   colocaciones** — es decir, **el contrato de cadena de soporte NO está demostrado en ejecución**.
5. **Criterio de aceptación vigente:** construcción física, **travesía física por la misma entidad** y
   **llegada al destino original**. **No** basta con generar una ruta, ni con colocar un bloque, ni con un
   waypoint.

**Hallazgos anteriores que siguen siendo ciertos:**

- Se **descartó** la arbitración del selector de goals como causa de los atascos (`puedeEntrar=true` siempre).
- **Budgets** no son la restricción: a 900 y a 4000 expansiones el centro protegido sigue `metaEncolada=false`.
- El centro de la aldea es **`PROTECTED`** e inocupable; el objetivo se movió a un **punto de aproximación**
  derivado de la superficie a `RADIO_DE_APROXIMACION = 64` (fuera de `FENCE_RADIUS=62`).
- **Se consiguió una colocación verificada** una vez (`a2n-20261016-023618`) pero **con 0 travesías**, y era un
  artefacto local (`dy=1`) **fuera de cualquier ruta**: no cuenta como aceptación.
- Los fixtures deben **forzar los chunks**; a 256 bloques del spawn las escrituras `setBlock` **no se aplican**
  (medido: la columna leía el terreno natural). Ese fue el bloqueo real de Stage 4X/4Y.
- Referencia de raid con muro: **5/8** estable (`…131506`, hash `4081CAF70F16F813`); sin muro **8/8**
  (`…113448`); los rezagados son siempre **z0/z4/z7**.

---

## E · Invariantes de seguridad y flujo de trabajo (PROMINENTE)

- **`D:\Documents\DevilRpg` (worktree principal) NO debe tocarse.** Verificado: 0 cambios.
- **Nunca** acceder ni modificar `run/saves/New World`.
- **Hash protegido del save:** `D9A58A246C23FDA92BFE2E6CA6770F7BDB1A1FD8C886DB819E485B4E0BD27140`.
- **Nunca** reset, clean, stash ni sobrescribir trabajo sin commitear.
- **Nunca** commit ni push sin autorización explícita.
- **No** ejecutar Gradle y el harness del juego a la vez.
- **Confirmar que no queda ningún JVM del juego activo antes de Gradle.**
- **No** ejecutar los fixtures de caza y de raid amurallado a la vez que el desarrollo de navegación.
- Los artefactos temporales del harness van en **`build/a2n/` (ignorado)** o en la infra de test existente.
- **No dejar marcadores de diagnóstico temporales en código de producción.**
- La navegación propia se mantiene **por defecto APAGADA**.
- **No** revivir la fase retirada de arquitectura de aldeanos ni cambiar IA de aldeanos no relacionada.
- El script de ejecución rápida (`build/a2n/lanzar-a2n.ps1`) crea un `gameDirectory` propio por run; **no usarlo
   sin comprobar que no apunta al worktree principal**.

---

## F · Próximos pasos exactos (ordenados)

**La aceptación de construcción NO ha pasado, así que la ruta es la de fallo:**

1. **Preservar el run que falla** (`build/a2n/a2n-20261016-130330`) e identificar la primera transición fallida
   (ya acotada: `buscarEscalonDeDescenso()` devuelve `null`).
2. **Diagnóstico acotado, SOLO en el harness ignorado de `build/a2n/`, y SIN cambiar comportamiento de
   producción.** Registrar los primeros ~8 candidatos de `buscarEscalonDeDescenso()` con sus coordenadas y **el
   predicado exacto que falla**, entre:
   - `isProtected`
   - `canPlace`
   - celda destino es aire
   - primera y segunda celda de holgura son aire (`celda.above()`, `celda.above(2)`)
   - validez del soporte, **distinguiendo terreno natural** de **soporte construido verificado y travesado**
   - alcanzabilidad u otras restricciones de candidato que **realmente existan** en la implementación

   Informar **cuántos candidatos se examinaron** y **por qué se rechazó cada uno**. **Sin spam por tick.**
   **No duplicar un predicado privado de producción y presentarlo como autoritativo** sin etiquetarlo
   claramente como **replicado**. Esto es lo que faltó al cerrar Stage 6B.
3. **Leer el código y la evidencia antes de cambiar nada.** Corregir **el defecto de implementación más pequeño
   demostrado**, dentro de la arquitectura de construcción ya aprobada (no reabrir el diseño).
4. **Repetir el test de aceptación aislado** del acantilado (modo `stairs`, `-NavCustom true`, presupuesto 900,
   chunks forzados, fixture Stage 5C).
5. **No ejecutar la caza ni el raid amurallado** hasta que pasen los criterios del acantilado.

Si la aceptación llegara a pasar: revisar riesgos de regresión → validar compilación/lint/diff → **caza** →
**raid amurallado (8/8 en dos corridas idénticas)** → evaluar huecos arquitectónicos restantes antes de declarar
A2 cerrado.

**En cualquier caso, A2 permanece ABIERTO.**

---

## G · Decisiones del dueño y preguntas abiertas

Solo lo que **realmente** requiere al dueño. **No** se pide volver a aprobar el diseño de construcción conectada
multi-paso: **ya fue autorizado** (Stage 6A) y está implementado.

1. **Política de ramas (conflicto ya documentado en la arquitectura, §K).** `CONTINUAR.md` dice que
   `feature/autonomous-village` **debe mantenerse sincronizada** con `migration-neoforge-1.21.1`. **No** se debe
   fusionar ni sincronizar hasta que el dueño resuelva ese conflicto.
2. **Retirada de `AggressiveZombiePathPlanner.java`** (segundo planificador aún en el árbol): ¿se retira cuando el
   nuevo quede demostrado, o se conserva? (Stage 4P §B propone retirarlo.)
3. **`PatrolHomeGoal` — rama de deambulación**: la vuelta a casa ya está convertida a sumisión de objetivo, pero
   el `moveTo` de deambulación **sigue sin pasar por el ejecutor propio**. ¿Se convierte también (autoridad de
   movimiento única estricta) o se deja?
4. **Alcance de la reescritura aldeano-vs-zombi** y esquema de numeración/README de `docs/autonomous-village/`
   (conflictos §K del documento de arquitectura): pendientes de decisión.

---

## H · Integridad verificada al escribir este documento

| Comprobación | Resultado |
|---|---|
| JVMs (juego y totales) | **0** — ninguno activo |
| `git rev-parse HEAD` | `97220be5453b57387002b9bc91935807cc665274` |
| Rama | `feature/autonomous-village` |
| Área de stage (`git diff --cached`) | **vacía** — nada preparado para commit |
| Worktree principal `D:\Documents\DevilRpg` | **0 cambios** |
| Hash del save protegido | `D9A58A246C23FDA92BFE2E6CA6770F7BDB1A1FD8C886DB819E485B4E0BD27140` ✅ |
| Run de Stage 6B `build/a2n/a2n-20261016-130330` | **existe** (`srv.log` presente) |
| `compileJava` | ✅ BUILD SUCCESSFUL (Stage 6B) |
| `lint_aldea.py --strict` | ✅ OK, exit 0 (Stage 6B) |
| `git diff --check` | ✅ exit 0 |
| Commit / push | **ninguno** (HEAD sin cambios) |

**Ficheros modificados sin commitear (pre-existentes, intactos):**

```
 M docs/CONTINUAR.md
 M docs/aldea-invariantes.md
 M src/main/java/com/chipoodle/devilrpg/entity/AggressiveZombieEntity.java
 M src/main/java/com/chipoodle/devilrpg/eventsubscriber/common/PlayerCapabilityForgeEventSubscriber.java
 M src/main/java/com/chipoodle/devilrpg/util/EventUtils.java
?? docs/autonomous-village/
?? src/main/java/com/chipoodle/devilrpg/entity/AggressiveZombiePathPlanner.java
?? src/main/java/com/chipoodle/devilrpg/entity/nav/
```

> Nota: `PlayerCapabilityForgeEventSubscriber.java` muestra un diff grande **por normalización de finales de
> línea (CRLF→LF)**, no por cambio semántico (+5 líneas reales: guarda de `FakePlayer`). No revertirlo.

## I · Estado tras el fix de sucesor canonico (25-oct-2026)

Rama `feature/autonomous-village`, HEAD `97220be5453b57387002b9bc91935807cc665274`, staged 0. El fix de identidad A* esta verificado por
`policyTest` y por el DROP aislado `drop11-20261025-0020`. A2 completo no se ejecuto: sigue bloqueado por el
centro del harness protegido. Ver `CONTINUAR.md` para el handoff y los comandos.
