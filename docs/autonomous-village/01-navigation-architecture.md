# 01 · Arquitectura de navegación autónoma, modificación del terreno y construcción

> **ESTADO DE ESTE DOCUMENTO.** Registra la **arquitectura objetivo acordada**. **NO** afirma que esté
> implementada ni aceptada. La implementación actual es la de `com.chipoodle.devilrpg.entity.nav`
> (`NavState`/`NavRoute`/`NavObstruction`/`NavPlanner`/`NavExecutor`) más `AggressiveZombiePathPlanner`, y
> **A2 sigue ABIERTO**: el objetivo de aceptación es **8/8 asaltantes entrando en la aldea en dos corridas
> controladas idénticas**, seguido de la regresión de caza. Este documento no cambia ese objetivo; lo enmarca.
>
> **Se escribe mientras el dueño termina de medir con su Arnés.** No se ha empezado ninguna implementación nueva a
> partir de él.

---

## A · Principio rector

El objetivo es un **sistema de navegación de agente autónomo fiable**, no conservar el planificador propio actual a
toda costa.

**La IA de vanilla no es sagrada.** Si los Goals de vanilla, la selección de objetivos, la navegación u otros
componentes de IA chocan con el comportamiento buscado, hay que **identificar el conflicto concreto** y **retirar o
sustituir los componentes que lo causan**, siempre que lo justifiquen la inspección del código y la evidencia de
aceptación.

**No se retira vanilla a lo tonto.** Lo que sea compatible se conserva.

---

## B · Un único controlador autoritativo de movimiento

**Tiene que haber UNA sola autoridad** controlando movimiento y navegación por entidad y por momento.

- Los goals **envían o actualizan objetivos**; **no** reinician rutas continuamente ni compiten por la navegación.
- Persecución, asedio, patrulla, recolección y vuelta a casa **pueden elegir objetivos distintos** compartiendo la
  **misma infraestructura** de navegación.
- Las actualizaciones de objetivo deben ser **idempotentes** cuando el objetivo efectivo no ha cambiado.
- El progreso de la ruta **sobrevive a los ticks normales de IA** y **no se reinicia** porque se vuelva a enviar el
  mismo objetivo.

> **Evidencia ya medida que sostiene esta regla.** En `a2n-20261015-212315` el goal enviaba la cadena **cada tick**:
> el ejecutor se quedaba replanificando sin caminar (`plans=88-89`, `moves=0-3`). Con envío idempotente por firma,
> la misma corrida pasó a `objSubs=1`, `plans=3-11` y `moves` de 1 a 269.

---

## C · Servicio de navegación compartido

Diseñar la navegación para que la usen **zombis agresivos, guardias, aldeanos** y otros entes autónomos.
Responsabilidades separadas:

1. **Gestión de objetivos.**
2. **Planificación de ruta consciente del movimiento.**
3. **Planificación de acciones de terreno.**
4. **Ejecución de movimiento y acciones.**
5. **Verificación del estado del mundo y recuperación.**

Las **políticas, permisos, prioridades y selección de objetivos propias de cada ente** se mantienen **separadas** de
la mecánica de navegación reutilizable.

---

## D · Algoritmos a evaluar

**Candidatos a evaluar, NO decisiones a implementar a ciegas:**

| Candidato | Papel propuesto |
|---|---|
| **A\* 3D consciente del movimiento** | Candidato inicial para planificación local de ruta. Nodos = posiciones válidas del ente; transiciones = acciones físicamente ejecutables. |
| **D\* Lite o búsqueda incremental equivalente** | Candidato para **replanificar barato** tras cambios del mundo. |
| **HPA\*** | Candidato para **escalar** a terreno grande y viajes largos, **cuando el perfilado lo justifique**. |
| **Theta\* u otros de ángulo libre** | Optimización **opcional** de calidad de ruta, si es compatible con un movimiento seguro frente a colisiones. |

- Usar una **heurística adecuada al grafo de movimiento**. Si se afirma optimalidad, **documentar las condiciones**
  que hacen admisible la heurística y correcta la implementación.
- **No adoptar un algoritmo por ser más nuevo o sofisticado.** Se evalúa contra los requisitos reales y contra el
  coste de integrarlo en el modelo de mundo y movimiento de Minecraft.

---

## E · Grafo 3D consciente del movimiento

El modelo debe tener en cuenta **dimensiones reales del ente, colisión, soporte, holgura de cabeza, capacidades de
movimiento, zonas protegidas, límites del mundo y terreno cambiante**.

Transiciones ejecutables a modelar:

`WALK` · `JUMP` · `DROP` · `OPEN` · `BREAK` · `BUILD`

**Añadir otros tipos de acción solo cuando hagan falta y estén implementados de punta a punta.**

- **No** tratar un bloque vacío como prueba de que se puede estar o pasar por ahí.
- **No** tratar una secuencia de waypoints como prueba de que la ruta es transitable.

> **Evidencia ya medida.** `a2n-20261015-234056`: la Y de la heightmap daba celdas de **borde de acantilado sin
> soporte debajo** (`e1=574,113,533(sup=113)` con `soporte=false`). Y en `a2n-20261015-215325` los waypoints se
> emitieron a **alturas interpoladas sobre la superficie** (`e0=597,90,597` con `sup=71`), es decir **flotando**:
> ningún asaltante avanzó de la etapa 0.

---

## F · La ingeniería del terreno es parte de la planificación

Cuando el movimiento normal no alcanza el destino, el sistema **debe considerar alternativas viables** en vez de
declarar el terreno imposible porque la búsqueda local haya fallado.

Estrategias posibles:

- buscar **otra ruta** alrededor del obstáculo;
- **abrir una puerta** o **romper** un obstáculo permitido;
- **construir escaleras**;
- **construir un puente**;
- **construir una subida diagonal o en espiral** cuando proceda;
- **replanificar** cuando el terreno cambia o una acción falla.

**Comportamiento exigido, explícito:** cuando un zombi **no puede subir la elevación requerida** con movimiento normal
o salto, **tiene que ser capaz de construir una ruta físicamente transitable** cuando exista una estrategia de
construcción viable.

La construcción debe ser un **proceso ejecutable real**: validar posición de colocación, soporte, holgura de colisión,
reglas de protección, disponibilidad de material cuando aplique, y **conexión con terreno pisable existente**.
Después: **colocar y verificar** bloques, **confirmar la travesía**, y **retomar el objetivo original**.

> **Reglas de honestidad de la evidencia (ya violadas una vez cada una).**
> - **Un bloque colocado NO es una ruta construida.**
> - **Una posición de partida o un bloque de terreno preexistente NUNCA debe reportarse como escalón construido.**
>   Medido en `a2n-20261016-022229`: se registró como «escalón travesado» el **ancla** `594,94,600` (el propio suelo
>   que el asaltante ya pisaba) **antes de construir nada**. Corregido en Stage 4O separando ancla y escalón colocado.

---

## G · Semántica de recuperación y fallo

Distinguir **al menos**:

1. Obstrucción **temporal**.
2. **Mundo cambiado** que exige replanificación.
3. **Acción fallida** (movimiento o colocación).
4. **Presupuesto de búsqueda agotado**.
5. Ruta que **exige construcción**.
6. Ruta **genuinamente infactible** con las capacidades y restricciones actuales.

- El **agotamiento de presupuesto NO debe convertirse en silencio en fallo terminal permanente.**
- El **reintento debe ser acotado y controlado**.
- Una **acción fallida no debe borrar el objetivo original** ni provocar intentos repetidos sin fin **sin un cambio de
  decisión**.

---

## H · La aceptación tiene que probar el comportamiento de punta a punta

Compilar, planificar con éxito, avanzar un waypoint y colocar un bloque suelto son **comprobaciones necesarias**, pero
**ninguna de ellas por sí sola prueba que la navegación funcione**.

La aceptación debe **progresar por etapas**:

1. **Movimiento normal.**
2. **Manejo de obstáculos y recuperación.**
3. **Construcción verificada** de una subida o puente transitable.
4. **Travesía real** de la ruta construida.
5. **Reanudación y finalización** del objetivo original.
6. **Escenario completo de asedio amurallado.**
7. **Comprobaciones de regresión** del comportamiento de caza existente.

**El objetivo de aceptación A2 sigue siendo 8/8 asaltantes llegando a la aldea en dos corridas controladas
consecutivas**, seguidas de las comprobaciones de regresión de caza. Conservar las condiciones de prueba controladas
existentes y **reportar las limitaciones con honestidad**.

**Pruebas por etapas:** no repetir asedios completos cuando el criterio funcional inmediato **no ha pasado**.

---

## I · Base reutilizable de agente autónomo

La **misma** infraestructura de navegación y acciones de terreno debe poder sostener con el tiempo a **aldeanos
inteligentes** que: **recolectan recursos**, se mueven **entre puestos de trabajo**, **reparan**, **construyen**,
**retiran obstáculos** y se mueven **en emergencia**.

Las políticas propias del aldeano y la **logística de recursos NO deben exigir una copia aparte del motor de
pathfinding**.

---

## J · Disciplina de ingeniería

- **Inspeccionar el código existente** y **reutilizar** los componentes que satisfagan de verdad la arquitectura.
- **Sustituir** los componentes cuyas responsabilidades o contratos impidan un comportamiento fiable.
- **Evitar ciclos interminables de solo diagnóstico** y parches aislados **sin criterio de aceptación definido**.
- Toda investigación debe terminar en **una decisión de implementación concreta**, en el **rechazo justificado de una
  hipótesis**, o en un **resultado de aceptación específico**.
- Mantener instrumentación **solo donde sostenga una decisión real o verifique un resultado**.
- **Preservar el trabajo, la evidencia y el comportamiento bueno conocido** hasta que las sustituciones pasen la
  aceptación.

---

## K · Conflictos de documentación pendientes de decisión del dueño

Se dejan **anotados, sin resolver**, porque afectan a decisiones de implementación y no los debe zanjar un agente:

1. **`docs/CONTINUAR.md` L2** declara rama de desarrollo **`migration-neoforge-1.21.1`** y pide mantener
   `feature/autonomous-village` **sincronizada** con ella. Todo el trabajo de A2 vive en
   `feature/autonomous-village` y **no se ha sincronizado**. ¿Sigue vigente esa política?
2. **`docs/CONTINUAR.md` §5.6** («NO reescribir toda la lógica del aldeano… produce el mismo baile») se refiere al
   **aldeano**, no al zombi agresivo. La autorización del dueño de **reescribir la capa de decisión de navegación**
   del agresivo **no contradice** aquel acuerdo, pero conviene que quede explícito que son ámbitos distintos.
3. **`docs/aldea-cerebro.md`** describe una fase de arquitectura del aldeano **retirada** por medida. La sección §I de
   este documento propone reutilizar la infraestructura para aldeanos: **no** es un regreso a aquella fase, pero el
   dueño debería confirmarlo antes de que alguien lo lea como tal.
4. **No existía** `docs/autonomous-village/` ni su `README.md` ni ningún documento numerado. Este documento
   **inaugura** la numeración con `01-`. Si el dueño prefiere otro esquema (por ejemplo `README.md` como índice),
   hay que decidirlo antes de crear el `02-`.

## L · Identidad canonica del sucesor (25-oct-2026)

Flujo real: `NavObstruction.classify` -> `NavPlanner.validarArista` -> `NavSuccessorPolicy` /
`NavPlanner.sucesorCanonico` -> estructuras A* locales/globales -> reconstruccion -> `NavExecutor`.

La celda candidata cruda no es necesariamente la celda de pie validada. Para escaladas, `piesHasta` es la
posicion efectiva cuya passability y soporte se comprobaron. El grafo debe usar esa posicion en `bestG`,
`piesDe`, `cameFrom`, `transitionTo`, cola, meta y reconstruccion. El caso cero-delta JUMP_UP->WALK fue el
defecto medido: validaba `(301,120)` y reconstruia `(301,118)`. La correccion no cambia la validacion; solo
alinea la identidad del estado. Los limites de DROP/BREAK/BUILD y las combinaciones no probadas siguen abiertos;
un PASS aislado no es aceptacion A2.

---

## Z · Decisión 28-oct-2026: controlador de navegación propio (primer slice)

**El `VillageDispatcherGoal` opt-in queda RECHAZADO como arquitectura de navegación.** Sigue apagado por defecto y
se conserva solo como registro histórico. La comparación nueva medida dio **71 vs 52** y **68 vs 58** cosechas
(control vs dispatcher); ambas con el dispatcher activo hicieron menos trabajo. El 69-vs-76 histórico es una
observación anterior aislada y **no** se presenta como mejora consistente. El escenario de objetivo competidor
mantuvo el recado pero no demostró una acción de trabajo.

**Nueva dirección:** DevilRpg es dueño de la decisión de ruta de los recados autónomos.

- Un solo dueño autoritativo del recado y su destino (`VillageManager` persistent data + `VillageNavigationGoal`).
- Planificación con la primitiva de navegación existente; no se reescribe la física, colisiones, gravedad ni salto.
- Ejecución acotada: progreso posicional real, máximo 3 replanificaciones, presupuesto `RECADO_PRESUPUESTO`.
- El controlador coge `MOVE` **solo mientras hay recado vivo**; al llegar libera y el oficio recupera el control.
- Objetivos vanilla de movimiento quedan coordinados/suprimidos solo durante esa posesión; supervivencia, sueño y
  respuesta a amenazas no se sustituyen.
- Integración opt-in `-Ddevilrpg.navigation=true`; por defecto manda el camino probado (latido + goals actuales).

**Contrato del primer slice (recado de granjero):** recado real asignado -> controlador toma destino -> avance
medible -> llega -> el goal de oficio trabaja -> recado se libera/completa -> sin controlador duplicado ni ruta
obsoleta.

**Migración por etapas:** (1) validar este slice de granjero; (2) medir sin regresión frente al camino actual;
(3) retirar el cableado experimental del dispatcher; (4) generalizar a otros oficios solo con aceptación propia.
El latido se mantiene para aldeanos no gestionados hasta la etapa 3.

**Regla de parada:** un fallo runtime concreto se documenta y se detiene la investigación; no se encadenan variaciones
equivalentes del mismo test para fabricar un PASS.

---

## AA · Contrato de integración del controlador propio (28-oct-2026)

**Invariante:** como máximo un dueño autoritativo de navegación por aldeano y recado. Ese dueño posee destino,
planificación/ejecución de ruta, progreso, recuperación acotada y limpieza. **No** posee trabajo de oficio, XP, comida,
sueño, combate, física, colisiones, gravedad ni salto.

**Componentes:**
- `VillageManager`: asigna/valida/completa/abandona recados; expone identidad y destino; rechaza recados inválidos;
  conserva presupuesto y política de abandono. No inventa recados.
- `VillageNavigationGoal`: adquiere solo un recado vivo, usa `Navigation` como primitiva, mide progreso real,
  replanifica como máximo 3 veces, respeta `RECADO_PRESUPUESTO` y libera en llegada/cierre.
- Goal de oficio: sigue haciendo el trabajo y dando XP; el controlador no los toca.
- Latido: se conserva para aldeanos no gestionados.

**Identidad:** `DevilRpgRecadoId` estable; no se usa solo la coordenada. Cambio de id => liberar ruta anterior,
limpiar contadores y adquirir la nueva.

**Estados:** `UNOWNED -> OWNED -> MOVING -> ARRIVED/RELEASED`; `MOVING <-> RECOVERING`; llegada no es finalización
del recado. `RELEASED` limpia navegación, destino, id y contadores; repetir limpieza es seguro.

**Arbitraje:** `MOVE` solo mientras hay ruta viva; no se suprime todo vanilla. Objetivos de supervivencia/amenaza
tienen prioridad explícita; al terminar, se restaura el comportamiento normal. Sin monopolio permanente a prioridad 0.

**Progreso:** nodo de ruta o reducción real de distancia; ticks, reescrituras idénticas y replans sin avance no cuentan.
Recuperación finita y luego abandono/parqueo existente.

**Aceptación del primer recado de granjero:** recado real -> un solo dueño -> progreso -> llegada -> trabajo real del
goal de granjero -> cierre del recado -> sin controlador duplicado ni ruta obsoleta.

**Política de fallo:** se registra el primer fallo concreto y se detiene la investigación; no se repiten variaciones
equivalentes ni se reactiva el dispatcher rechazado.

## AB · Coordinación compartida + planificadores individuales (28-oct-2026)

La arquitectura oficial pasa a dos niveles. Para agricultura hay un **coordinador
lógico por aldea**, no uno por granjero: reparte parcelas, evita dueños duplicados
y reasigna cuando el granjero deja de estar disponible. El planificador individual
(`VillagerFarmGoal`) conserva el estado y decide la acción local; no ejecuta
trabajo desde el coordinador ni concede XP. Ver
`docs/autonomous-village/PROFESSION-COORDINATION.md` para identidades, ejemplo de
tres granjeros y límites. Primer slice: `VillageFarmCoordinator` +
`VillagerFarmGoal.miParcela`. Aceptación runtime pendiente.

## AC · Contención: agricultura fuera de la navegación propia (28-oct-2026)

La integración del controlador propio con recados de agricultura queda **DESACTIVADA**.
El controlador sigue disponible y opt-in para tareas no agrícolas, pero una tarea
`farm:<plot-id>` es siempre del camino de respaldo del granjero.

Regla semántica única (`VillageTaskNavigationPolicy`):

- `farm:*` => `PROFESSION_FALLBACK`; nunca se adquiere.
- `recado:*`, `delivery:*` y otras tareas no vacías => `CUSTOM_NAVIGATION`.
- vacío/null => fallback.

La exclusión ocurre antes de `canUse` y también en `tick` si la tarea cambia a
agrícola. No usa distancia, coordenadas ni radio de llegada. El recado y la
asignación de parcela no se borran al rechazar la navegación; el fallback del
granjero sigue disponible.

**Contrato futuro de handoff** (no implementado para agricultura):

`TASK_SELECTED → NAVIGATION_OWNED → WORK_POSITION_REACHED → ACTION_EXECUTABLE → TASK_COMPLETED`

La llegada de navegación es `WORK_POSITION_REACHED`, **no** `TASK_COMPLETED`.
El controlador solo puede declarar handoff si el ejecutor de trabajo acepta la
posición resultante. Deben definirse transiciones para posición inválida, ruta
incompleta, reassign/cancel, preempción por supervivencia/sueño y rechazo del
ejecutor. Hasta entonces, agricultura usa el fallback existente.

## AD · Contención de tareas de posición de trabajo (29-oct-2026)

El A/B aceptado de minería demostró que la navegación propia impide al minero
llegar a la posición exacta de trabajo (control +46 XP; enabled 0). Se extiende la
política semántica:

- `farm:*` → `PROFESSION_FALLBACK`.
- `mine:*` → `PROFESSION_FALLBACK` (misma razón: posición de trabajo exacta).
- `recado:*`/`delivery:*` → `CUSTOM_NAVIGATION` mientras sigan siendo elegibles.

No se usan coordenadas, distancias ni radios. El fallback sigue siendo el camino
del oficio. La aceptación runtime de minería queda pendiente; no se re-habilitó
navegación propia para minería.
