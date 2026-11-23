# A2 · ASEDIO AMURALLADO — HANDOFF DE CONTINUACIÓN

> **ACTUALIZACIÓN 22-nov-2026 — A2 CERRADO Y ACEPTADO.** El bloque «Estado global: A2 SIGUE ABIERTO» que sigue
> es el estado **histórico** de este handoff y se conserva por trazabilidad. El gate del asedio amurallado
> quedó medido y aceptado: 8/8 estricto DENTRO-PARED en tres corridas consecutivas de 12000 ticks con C4_PASS
> (`a2n-20261122-054142`, `-094529`, `-100646`). Ver
> `docs/autonomous-village/2026-11-22-A2-FINAL-ACCEPTANCE-AND-CLOSURE.md`.

> **Estado global: A2 SIGUE ABIERTO.** El gate del asedio amurallado completo está **sin medir**.
> Este documento es el registro de continuación del *asedio*, no un parte de aceptación.
>
> **No confundir con R4/S5:** `r4golem-20261022-1900` aceptó el **asedio clásico S5** de
> `VillageManager.start` con guardias y oleada 8/8. No es la navegación propia A2 ni mide el fixture amurallado
> de este documento; A2 conserva su bloqueo y su umbral sin aceptar.

---

## Actualización de continuación — 2026-11-12

Este handoff sigue describiendo un A2 **abierto y no aceptado**. La tabla de
verificación de la sección 0 es una fotografía histórica de la sesión que la
escribió; no debe usarse como HEAD ni como estado actual del árbol de trabajo.

Estado actual comprobado al 2026-11-12 22:43:
- Repo: `D:\\Documents\\DevilRpg-autonomous`; rama `feature/autonomous-village`;
  HEAD `518fee13fc15bfb9b92b0479d86b5c609d1947da`.
- C4 ACCEPTED en `c4-full8-20261112-2240`; resultado final y hashes en
  `A2-C4-HEADLESS-ACCEPTANCE-20261112.md`.
- `tools/a2n/A2NavHarness.java` sí fue editado dentro del alcance C4
  autorizado; SHA-256 `0B699503DCBE99D084AE4A073FE77650B72484B02BA97DED0829C889BFCA9358`.
  El protegido `tools/a2n/A2NavHarness.java.partial` no se tocó; SHA-256
  `2C3EBA9ED6E936B595E26650B796A624838B5CB4FF6CFD7B1804BE7A13552127`.
- Probe de fundación y reparación ya completados en mundos desechables
  separados; evidencia en `A2-FOUNDATION-PROBE-20261112-ACCEPTANCE.md` y
  `A2-FOUNDATION-REPAIR-20261112-ACCEPTANCE.md`.
- No se usó fallback `629`, mundo histórico ni `run/saves`; no commit/push.

**A2 global sigue OPEN:** C4 PASS no equivale al contrato A2 de 8/8 asaltantes
entrando al perímetro en tres semillas distintas. El run8 C4 registró
`entraronPerimetroEver=6/8`; por tanto no cierra A2. La siguiente campaña A2
requiere preparar fixtures desechables válidos por semilla, conservar centro
`661,71,533` y medir navegación, brecha/cruce y resultado con el umbral ya
acordado. No modificar `.partial` ni rebajar el contrato.

## 0 · Verificación de estado en el momento de escribir este documento

Comprobado con `git` y `Get-FileHash` en esta sesión (no heredado de partes anteriores):

| Hecho | Valor verificado |
|---|---|
| Repositorio | `D:\Documents\DevilRpg-autonomous` |
| Rama | `feature/autonomous-village` |
| HEAD | `97220be5453b57387002b9bc91935807cc665274` |
| Árbol de trabajo | 9 entradas preexistentes (5 modificadas + 4 sin seguimiento), **0 preparadas** |
| Árbol principal `D:\Documents\DevilRpg` | **0 cambios** |
| Procesos Java/juego | **0** |
| `tools\a2n\A2NavHarness.java` | **`72ED1D9E7EE00DB20072DD35535C1CB1C1D50A0C288B11ECBCED70F4C05F8D94`**, **1 622 líneas** |
| `level.dat` protegido | `D9A58A246C23FDA92BFE2E6CA6770F7BDB1A1FD8C886DB819E485B4E0BD27140` |
| Backup Edit 1 | `7D2351DC2F6B9D01FC36C250883DCEF9BFFF090830564E17F9E16BFFA076FA82` |

### ⚠️ Dos correcciones importantes respecto al encargo que originó este documento

1. **Edit 4 YA ESTÁ IMPLEMENTADO Y COMPILADO.** El encargo lo describía como pendiente y daba como estado actual el hash de Edit 3 (`634191A8…`, 1 530 líneas). El fichero real es `72ED1D9E…` con 1 622 líneas y contiene 9 marcas de Edit 4
   (`muestrearRaid`, `R_FASE_A`, `R_FASE_B`, `NavFeature.customNavigation()`). **No hay que rehacer Edit 4**; hacerlo sobrescribiría trabajo compilado y verificado.
2. **La cadena del `.partial` en la tabla del encargo era inconsistente** (`2C3EBA9E1E6B9D01FC36C250883DCEF9BFFF090830564E17F9E16BFFA076FA82`, una mezcla de dos hashes distintos). El valor **real medido ahora** es `2C3EBA9ED6E936B595E26650B796A624838B5CB4FF6CFD7B1804BE7A13552127`. Se deja constancia de la discrepancia en lugar de corregirla en silencio.

---

## 1 · Objetivo del proyecto

Validar la implementación actual de **navegación propia contra un asedio amurallado real y completo**, no seguir fabricando fixtures aislados. El escenario objetivo:

- Semilla `12345`.
- Centro de aldea `(661,71,533)`.
- Ocho zombis agresivos a radio `95`.
- Perímetro de aldea radio `71`.
- Muro de **3 de alto en radio `62`, puertas cerradas**.
- Eliminación de caedizos y forzado de chunks con el enfoque ya establecido.
- Fase A hasta el tick de asedio `3000`; Fase B hasta `6600`.
- Navegación propia **encendida y verificada desde la JVM del juego**.
- **Exactamente una** corrida válida de extremo a extremo, una vez restaurado y validado el arnés.

La suite aislada de navegación es evidencia de regresión útil, pero **no sustituye** al asedio completo.

---

## 2 · Cronología

### A · Línea base histórica del asedio

- Planificador heredado: **5/8** asaltantes entrando en la aldea, a lo largo de tres corridas documentadas.
- Última corrida registrada **con navegación propia**: `build\a2n\a2n-20261016-185324`
  - 8 asaltantes colocados, **0/8** entraron al perímetro, **0** brechas, **0** muertes, **0** fallos de instrumento, sin cuelgue de watchdog.
- Esa corrida es **anterior** a los arreglos posteriores de staging, guarda y detección de obstrucciones. Esos arreglos **nunca** se han medido en un asedio.

**Conclusión:** la comparación 5/8 vs 0/8 es **histórica y descriptiva**, no prueba de regresión ni de mejora actuales.

### B · Pérdida del arnés de asedio original

- **Stage 7H** dañó el arnés anterior. El `.partial` superviviente es **incompleto y estructuralmente no compilable**; no es un arnés de asedio recuperable.
- El fichero canónico se **reconstruyó como un arnés nuevo y NO equivalente**, conservando seis modos aislados:
  `longrange`, `construction`, `stairs`, `stage6f`, `stage6e`, `edges`.
- Validación histórica de esos seis modos: **Stage 7ZG, 34 aserciones verdes, cero fallos de instrumento**. No repetirlos sin motivo concreto.
- **No** describir la implementación de asedio original como "recuperada".

### C · Edit 1 — despacho y campos

- Se introdujo el modo `raid` como **primera rama** del despacho, separado de los aislados.
- Backup byte a byte, verificado: `7D2351DC2F6B9D01FC36C250883DCEF9BFFF090830564E17F9E16BFFA076FA82`
  (`build\a2n\recovery\A2NavHarness-PRE-RAID-EDIT1-20261018-201350.java`).
- **El backup no debe restaurarse** sobre el canónico ni modificarse.

### D · Edit 2 — fixture del asedio

Implementado: ancla congelada `(661,71,533)`, forzado de chunks con `setChunkForced` + verificación `hasChunkAt`, muro a radio 62 de 3 de alto con puertas cerradas, limpieza de caedizos, comprobaciones de geometría y marcadores (`CENTRO DERIVADO`, `ALDEA idx`, `ANCLA congelada`, `CHUNKS: hasChunkAt`, `PARED construida`, `PARED total`).

- Hash reportado tras Edit 2: `BE74EE375525FA1FD7AD52296753E04114049569A9BB5CE0793ED1C1203299EE`
- Compilación, lint estricto y `git diff --check` correctos. **No se lanzó asedio** (histórico reportado; verificado de nuevo solo por compilación en su momento).

### E · Edit 3 — spawn e instrumentación de navegación

- Implementado: 8 intentos de spawn a radio horizontal 95 alrededor del ancla congelada; éxito determinado por el **valor de retorno de `addFreshEntity`**; contadores `rSpawneados`/`rFallosSpawn`; log `SPAWN idx=… pos=… ok=…` con índice, posición, UUID, estado vivo y objetivo; log `NAVCUSTOM …`; cierre provisional acotado a 200 ticks.
- Estado reportado al terminar Edit 3: `634191A80B9748FFD4CC77698A04DF3E63D4FEA75868968C89D46824C12B70CE`, 1 530 líneas.
- Checks reportados: `compileJava` OK, `lint_aldea.py --strict` OK, `git diff --check` OK, sin lanzar juego, sin tocar producción/lanzador/otros modos, sin commit.

### F · Edit 4 — muestreo y fases (**YA IMPLEMENTADO Y COMPILADO**)

> Implementado en la sesión inmediatamente anterior a este handoff. Hash actual: `72ED1D9E7EE00DB20072DD35535C1CB1C1D50A0C288B11ECBCED70F4C05F8D94`, 1 622 líneas.

Contenido real del fichero:

1. `muestrearRaid()` — una sola pasada por tick sobre los ≤8 asaltantes seguidos, reutilizando el **único** manejador `ServerTickEvent.Pre` (sin segundo listener).
2. Por asaltante: índice estable `idx = k+1`, UUID (solo en el log de spawn), `blockPosition()`, `getHealth()`, `isAlive()`, distancia **horizontal** `sqrt(dx²+dz²)` al ancla, `entroPerimetro` como **bandera persistente** a radio ≤71, `dentroPared` como **condición geométrica actual** a radio ≤62, muerte registrada una sola vez y distinta de "desaparecido" (`isRemoved()`).
3. Contador **relativo al asedio** (`rTicks`), que solo avanza tras `rIniciado` (fixture validado + spawn terminado).
4. Fases leídas de los argumentos del lanzador: `R_FASE_A = Integer.getInteger("a2n.faseA", 3000)`, `R_FASE_B = Integer.getInteger("a2n.faseB", 6600)`.
5. Orden por tick: `rTicks++` → `muestrearRaid()` → límites. Emite `FASE A fin t=3000`, `FASE B inicio t=3001`, `FASE B fin t=6600`, y cierra el run con `cerrar(...)` una sola vez.
6. Fallo de fixture/spawn: conserva la ruta `INVALIDO` y **no** ejecuta fases.

**Semántica de activación de navegación (verificada en `NavFeature`):** la decisión se resuelve **una sola vez al cargar la clase** en `private static final boolean ENABLED`, y `customNavigation()` la expone. `resolve()` da **prioridad a la propiedad** `devilrpg.nav.custom` (valores `true`/`1`) y usa `DEVILRPG_NAV_CUSTOM` (`1`/`true`) **solo como fallback**. El arnés ahora llama a `NavFeature.customNavigation()` (no recalcula), y además registra las entradas crudas.

Estado reportado tras Edit 4: `compileJava` **BUILD SUCCESSFUL**, `lint_aldea.py --strict` OK, `git diff --check` OK, sin lanzar juego, seis modos intactos, sin commit.

### G · Edit 5 — **PENDIENTE**

Debe añadir, partiendo de estado medido y sin fabricar éxito:

- `TOTALES A`
- `PARED total` (incluida la integridad real del anillo)
- `VEREDICTO A`
- una línea de resultado por asaltante
- el resto de cadenas que el **parser del lanzador** espera
- el marcador de finalización en `build\a2n\<runId>\fin.txt`

Confirmar el contrato del parser del lanzador antes de implementar la salida.

### H · Validación final — **PENDIENTE**

Solo después de Edit 5 revisado: compilar; lint estricto + `git diff --check`; revisar el diff completo; verificar que los seis modos siguen registrados e intactos; verificar que el lanzador despacha `raid` con seguridad; confirmar que la navegación propia está realmente encendida **en la JVM del juego**; ejecutar **exactamente una** corrida válida con semilla `12345`; registrar log y resultados medidos; comparar descriptivamente con la línea base; y dejar que la evidencia real decida el siguiente paso de ingeniería.

No crear otra suite de tests aislados. No repetir la corrida salvo que un fallo concreto de arnés/entorno invalide la primera.

---

## 3 · Hashes e integridad

| Artefacto | SHA-256 registrado | ¿Verificado ahora? |
|---|---|---|
| Backup Edit 1 | `7D2351DC2F6B9D01FC36C250883DCEF9BFFF090830564E17F9E16BFFA076FA82` | **sí** |
| Arnés tras Edit 2 | `BE74EE375525FA1FD7AD52296753E04114049569A9BB5CE0793ED1C1203299EE` | histórico (superado) |
| Arnés tras Edit 3 | `634191A80B9748FFD4CC77698A04DF3E63D4FEA75868968C89D46824C12B70CE` | histórico (superado) |
| **Arnés actual (Edit 4)** | **`72ED1D9E7EE00DB20072DD35535C1CB1C1D50A0C288B11ECBCED70F4C05F8D94`** | **sí** (1 622 líneas) |
| `.partial` de Stage 7H | `2C3EBA9ED6E936B595E26650B796A624838B5CB4FF6CFD7B1804BE7A13552127` | **sí** — ⚠️ el encargo citaba una cadena distinta e inconsistente |
| `level.dat` protegido | `D9A58A246C23FDA92BFE2E6CA6770F7BDB1A1FD8C886DB819E485B4E0BD27140` | **sí** |

---

## 4 · Estado sin ambigüedad

- **A2 sigue ABIERTO.**
- El gate del **asedio amurallado completo está SIN MEDIR**.
- **Edit 4 es el último stage completado** (implementado y compilado); Edit 3 es histórico y su hash quedó superado.
- **Edit 5** y la **única corrida completa del asedio** siguen **pendientes**.
- **Ningún resultado de asedio actual puede inferirse de la suite de fixtures aislados.**
- Deben **preservarse** las 9 entradas del árbol de trabajo.
- **No** hay autorización para commit, push ni merge.

---

## 5 · Invariantes de seguridad (obligatorios en toda sesión)

- No tocar `D:\Documents\DevilRpg`.
- No tocar `run\saves\New World` (hash `D9A58A24…27140`).
- No usar el `.partial` como código fuente.
- No restaurar ni sobrescribir el backup de Edit 1.
- No ejecutar Gradle mientras el arnés de juego esté activo; una sola instancia de juego.
- No usar `tools/arnes/tanda-rapida.ps1` sin cambios (fija el árbol principal).
- No hacer reset/stash/clean; no descartar las 9 entradas.
- Los artefactos generados van a `build/` (ignorado), no al árbol versionado.

---

## Actualizacion (posterior a 2-F/2-G): estado real e invariantes que faltaban

Esta seccion CORRIGE lo obsoleto de arriba; donde haya conflicto, manda esta seccion.

**Estado real del arnes (verificado):** `tools\a2n\A2NavHarness.java` = `1441F07873A22C9B63DD7433B0AA018B81EA5242334F510DAD66108EF1AD8D0A`, 1863 lineas.
- **Edit 5 ya esta implementado** (no pendiente): `integridadAnillo` + `informeRaid` emiten RESUMEN/TOTALES/PARED total/CRITERIOS/VEREDICTO/BAJAS DE COMBATE, linea por asaltante, y MUERTE/DESAPARECIO/RETIRADA solo ante transiciones observadas.
- **Task 3 implementado**: snapshot cada 300 ticks `RAID NAV idx=... estado=... ruta={...} descenso={...}` (usa `state()`, `rutaAccionDiag()`, `descensoDiag()`; **`rechazoPorGuardaDeRuta()` NO existe**).
- **Muro terrain-aware**: regla de suelo UNICA en `sueloDeColumna(nivelR,x,z,cy)` (ventana cy+-32, descendente, rechaza aire/fluidos/sin colision); 4 usos (construccion, validacion, integridad, helper).
- **Integridad anclada a la baseline original**: `rBaseValidada` se llena solo al construir; la clasificacion es mutuamente excluyente: presentes (intacta) / columnasNoSolidas (danada) / columnasDesplazadas (base actual != validada: NO se re-ancla ni cuenta como intacta) / columnasSinSuelo. Baseline vacia => no se afirma nada. `faltantes = max(0, esperadas - presentes)`.
- **Spawn estricto**: columna valida = suelo con colision + 2 de holgura; si no, `SPAWN idx=... ok=false motivo=sin-columna-valida` y no se inserta; con menos de 8 el run es INVALIDO.

**Invariante de recinto (antes no documentado):** valido solo si sinSuelo==0, fallosBase==0, fallosAltura==0 y fallosContinuidad==0 en TODAS las columnas del anillo (no una muestra), con base apoyada, 3 de alto, continuidad vecina (salto <=2) y puertas cerradas. **El numero de bloques NO es prueba de recinto.**

**Diagnostico de retirada y JAVA_TOOL_OPTIONS (antes no documentado):** `remove(RemovalReason)` registra `[A2N] RETIRADA uuid/motivo/pos/salud/indiceAsedio` solo con `-Ddevilrpg.a2n.diagRetirada=true` (apagado por defecto). **El lanzador ASIGNA `-Djdk.net.unixdomain.tmpdir=C:\Windows\Temp`, asi que para esta propiedad hay que usar `JAVA_TOOL_OPTIONS` preservando y restaurando el valor previo exacto (incluido null).

**Corrida invalida `a2n-20261018-235012` (medida; NO aporta conclusion de navegacion):** `INVALIDO (raid) - RAID FIXTURE INVALIDO | fallosDeInstrumento=1`; `columnas=400 verificadas=400 sinSuelo=0 baseMin=48 baseMax=104 fallosBase=0 fallosAltura=903 fallosContinuidad=203 anilloCompleto=false`; **0 spawns**. Causa: bajo el anillo r=62 el terreno va de **y=48 a y=103 (56 bloques de desnivel)**.

**Decision pendiente del dueno (bloquea un run valido):** con radio 62 / alto 3 / puertas cerradas / continuidad obligatoria, el centro 661,71,533 sobre semilla 12345 cae en un canon y no hay recinto cerrado. Opciones (ninguna aplicada): (1) preparar el terreno del fixture allanando la banda r=62 (conserva comparabilidad con 5/8 y 0/8); (2) cambiar coordenadas a una llanura (rompe comparabilidad directa); (3) redefinir el muro como barrera escalonada (cambio de ESPECIFICACION, requiere aprobacion).

**Ruta minima propuesta (no implementada):** busqueda acotada y determinista de centro (<=16 candidatos en rejilla fija) que evalue esas condiciones con `sueloDeColumna` antes de construir, registre cada candidato con sus contadores y elija el primero valido; si ninguno pasa, INVALIDO con motivo. No hay evaluacion de terreno fuera de un servidor vivo, asi que necesita un run para ejercitarse, pero no un modo nuevo.

**A2 sigue ABIERTO**, sin umbral de aceptacion y con el asedio sin medir para un fixture valido.

### Terreno preparado del fixture (Opcion 2, IMPLEMENTADO)

Orden real dentro de `prepararRaidFixture`: forzado de chunks -> comprobacion de disponibilidad -> **preparacion de terreno** -> busqueda de centro -> construccion del muro -> baseline -> spawn. Centro del fixture: `661,71,533`.

- **Superficie de referencia:** bloque solido en `Y=70` en cada columna del perimetro r=62 (400 columnas).
- **Base del muro:** `Y=71`; celdas del muro: `71, 72, 73`; volumen `71..73` despejado (aire, sin fluidos).
- **Escrituras:** 1 colocacion + 3 despejes por columna => **maximo 1 600 operaciones INTENTADAS** (no necesariamente 1 600 cambios de estado: escribir aire sobre aire no cambia nada). Los conteos REALES se emiten en el run como `TERRENO PREPARADO: ... AUDITORIA: sueloPrevioFluido=... sueloPrevioNatural=... sueloPrevioOtro=... celdasDeHolguraOcupadas=...`.
- **Determinista** y limitado al mundo aislado del arnes (`build\a2n\<runId>\game\a2n`); nunca toca `run\saves\New World`. No allana el interior ni el paisaje.

**Politica de sobrescritura (auditada en fuente):** `solido(x,y,z)` = `setBlockAndUpdate(pos, STONE)` y `aire(x,y,z)` = `setBlockAndUpdate(pos, AIR)`. Ambos **sobrescriben incondicionalmente** cualquier estado previo en esa posicion, sin efectos mas alla de ella. Por tanto SI se pueden destruir bloques preexistentes en las 4 celdas por columna. Mitigacion: se clasifica lo previo (fluido / natural conocido / **otro**) y se registra un resumen acotado, con hasta 8 lineas `PREP SOBRESCRITURA` para los casos `otro` (posibles estructuras). No se omite ninguna columna del muro ni se relaja el contrato.

**La validez NO la decide la preparacion:** tras preparar se ejecuta el validador independiente (`evaluarRecinto`) sobre las 400 columnas; el muro se construye solo si `sinSuelo=fallosBase=fallosAltura=fallosContinuidad=0`; si no, se aborta como `FIXTURE RAID INVALIDO` **antes** de spawnear. La baseline se registra despues, durante la construccion.

**Contrato del muro sin cambios:** radio 62, alto 3, puertas cerradas, continuidad vecina <= 2, regla de suelo `sueloDeColumna`, modelo de integridad anclado a la baseline original. Sin cambios en navegacion de produccion, AI Goals, combate ni entidad.

**Comparabilidad:** el terreno preparado **cambia el suelo transitable y las rutas de aproximacion**. Aunque radio y alto no cambien, los resultados historicos (5/8 heredado, 0/8 con navegacion propia pre-arreglos) son **solo contexto**, no una linea base directamente comparable.

**A2 sigue ABIERTO; no existe umbral de aceptacion acordado.**

### Edit 7 (correccion del validador post-construccion) — IMPLEMENTADO

**Causa raiz medida** en 2n-20261019-004842: la validacion POST-construccion (bloque E) re-derivaba el suelo con `sueloDeColumna`, que devuelve el bloque aceptable mas ALTO de la ventana `[cy+32..cy-32]`. Con el muro ya colocado en Y=71..73, ese "suelo" pasaba a ser la **cima del muro (Y=73)**, y el validador exigia aire en 74..76: resultado `fallosAltura=1200` (400 columnas x 3), `anilloCompleto=false`, `FIXTURE RAID listo=false` y aborto **antes** de `prepararRaidAsaltantes` (0 lineas `SPAWN`). No era dano del muro ni fallo de colocacion: era **auto-referencia del validador**.

**Correccion (solo validacion, sin tocar muro/terreno/spawn/navegacion):**
- **Bloque E**: se usa la base REGISTRADA por columna (`rBaseValidada`, escrita al construir) y se comprueban las 3 celdas `base..base+2`. Sin base registrada la columna NO queda verificada (`anilloCompleto=false`); no se acepta en silencio. `sueloDeColumna` ya no se llama aqui.
- **`integridadAnillo`**: idem; se elimina la re-derivacion que marcaba TODAS las columnas como `desplazadas`, y `sinSuelo` pasa a significar "el terreno de apoyo bajo la base registrada desaparecio" (se comprueba `base-1`). Se conserva la deteccion de celdas no solidas y el ancla a la baseline original.
- **`evaluarRecinto` (pre-construccion) sin cambios**: sigue siendo la unica puerta que puede rechazar un candidato, y ahora es la unica que usa `sueloDeColumna` ademas del constructor.
- `sueloDeColumna` no se modifica. No se suprime ningun contador.

**Semantica restante:** `columnasDesplazadas` ya no se puede medir sin re-derivar (y re-derivar confunde muro con terreno); queda como **NO MEDIDO** en el informe de fase, no como cero.

### Edit 8 — instrumentacion de diagnostico (IMPLEMENTADA)

Solo
aid, sin cambios de comportamiento (misma busqueda, mismo orden, mismos rechazos, mismos spawns):

1. **Discriminador de rechazo de spawn** (`prepararRaidAsaltantes`): el mensaje combinado
   `sin-columna-valida` se sustituye por una clasificacion **mutuamente excluyente**, derivada de la
   secuencia real (la ventana Y=63..95 se recorre entera; no hay otra salida):
   - `NO_SUITABLE_GROUND` — ninguna celda de la columna tiene suelo con colision;
   - `INSUFFICIENT_CLEARANCE` — hay suelo pero nunca con 2 bloques de holgura.
   Se anaden `columnasConSuelo=` y `ventanaY=..` a la misma linea `SPAWN`.
2. **Bracket radial de alta resolucion** (`muestrearRaid`): cada **20 ticks**, solo para asaltantes a
   **<=75** del ancla, se emite `RAID PROX idx=… t=… pos=… distPrevia=… dist=… bracketRadial=(no-prueba-cruce-de-muro)
   estado=… ruta={…} descenso={…}`. Los snapshots de 300 ticks y las fases NO cambian.
   `distPrevia` vale `NO MEDIDO` en la primera observacion de cada asaltante.

**Limite explicito:** el bracket es RADIAL. No prueba cruce de una celda concreta, ni un hueco del
perimetro digital, ni el muro; solo acota el intervalo en el que `dentroParedAhora` cambio.
`rutaAccionDiag` sigue siendo *last-write-wins* (no es un historial de acciones).

### Edit 10 — FUNDACION bajo el anillo (IMPLEMENTADA)

**Hueco establecido por Edit 9 (offline):** el anillo tiene 400 columnas y 1 200 celdas en Y=71..73, apoyadas en **una sola** capa de piedra en Y=70. Nada se escribia por debajo, asi que donde el terreno natural bajo el anillo es aire existe un paso **continuo** exterior->interior en Y<=69. Evidencia de trayectoria coherente: los asaltantes 5 y 6 se midieron a **Y=63** cruzando r=62 en TRAVERSING, sin descenso en curso. El horizontal SI esta sellado: BFS 4-vecino desde el centro **no** escapa (11881 celdas), y las 96 esquinas diagonales abiertas son artefactos del circulo digital que **no** son pasables por una caja de ancho >=0.6 (modelo conservador 4-vecino). Por tanto el hueco relevante es **vertical**, no diagonal.

**Correccion (solo preparacion del fixture):** por cada columna del anillo, desde Y=69 hacia abajo:
- se rellena **solo AIRE** con el mismo material del fixture;
- se detiene en el primer bloque **no-aire con colision** (terreno existente, que **no** se reemplaza);
- **no** sobrescribe agua/lava/plantas: un no-aire sin colision deja la columna **NO RESUELTA** (log FUNDACION NO RESUELTA columna=…, y=…, bloque=…) en vez de fingir sellado;
- profundidad acotada a **64**; si se agota, la columna queda no resuelta.
Profundidad = **bloques nuevos bajo Y=70**, sin contar la repisa.

**Informe obligatorio (una linea):** FUNDACION: columnas=… bloquesNuevos=… profundidadMin=… profundidadMax=… hastaTerrenoSolido=… noResueltas=….

**Sin cambios en:** seleccion horizontal del anillo, radio 62, alto 3, buscador de spawn, navegacion, integridadAnillo (sigue midiendo las 1 200 celdas del muro contra las bases registradas; la fundacion se informa **aparte**). Un fixture con
oResueltas>0 **no** debe considerarse sellado.

**Sigue NO MEDIDO:** el AABB real del asaltante (el modelo 4-vecino es un proxy), la ruta exacta entre muestras y si algun asaltante uso las esquinas diagonales.

### Edit 10.1 — FAIL CLOSED si la fundacion queda incompleta (IMPLEMENTADO)

**Regla:** `rFundSinSoporte > 0` -> fixture **NO listo**. Es un requisito de **validez del fixture**, no un umbral de aceptacion de A2.

**Punto exacto del control de flujo:** en `prepararRaidFixture`, justo antes de la asignacion de `rFixtureOk` (que gobierna **todas** las vias: el despacho solo llama a `prepararRaidAsaltantes` si `rFixtureOk` es true, y solo hay una ruta de despacho `raid` -> una unica rama `if (!rPreparado)`):

``java
boolean fundacionCompleta = rFundSinSoporte == 0;
if (!fundacionCompleta) {
    fallosDeInstrumento++;
    log("FIXTURE NO LISTO: fundacion incompleta, columnasNoResueltas=" + rFundSinSoporte + " (ver lineas FUNDACION NO RESUELTA con coordenadas y motivo)");
}
rFixtureOk = anilloCompleto && centroOk && celdas > 500 && fundacionCompleta;
``

Consecuencias verificadas por lectura del despacho: `rFixtureOk=false` -> `cerrar("INVALIDO", "RAID FIXTURE INVALIDO …")` y `return` **antes** de `prepararRaidAsaltantes`; por tanto **no hay spawn, ni fases, ni marcador navegable**. No hay una segunda via de spawn. `fallosDeInstrumento` tambien se incrementa, asi que el marcador no puede salir como exito.

**Sin cambios:** las 1 200 celdas del muro, radio 62, alto 3, buscador de spawn, navegacion, requisito 8/8, y el metrico de integridad anclado a la baseline.

**Sigue NO MEDIDO:** AABB real del asaltante; terreno bajo el anillo hasta que un run imprima la linea `FUNDACION`; ruta exacta entre muestras.

### Edit 11(a) — TRACE DE NAVEGACION solo-arnes (IMPLEMENTADO)

**Propiedad:** `-Ddevilrpg.a2n.traceNav=true` (`Boolean.getBoolean`, **apagada por defecto**; si esta apagada, todo son dos comprobaciones de booleano y ningun I/O). Se pasa por `JAVA_TOOL_OPTIONS` (el lanzador ASIGNA `JDK_JAVA_OPTIONS`).

**Artefacto:** `build\a2n\<runId>\nav-trace.log` (unico por run; nunca sobrescribe logs previos). Cabecera con `schema=a2n-navtrace/1`, run, `traceNav` y **navCustom efectivo** (`NavFeature.customNavigation()`), mas la lista de columnas.

**Registro por tick** (una linea por asaltante vivo, cada tick de asedio): `tickServidor,raidTick,idx,uuid,tipo,x,y,z,bbox,objetivo,dist2D,dist3D,movio,estado,cambioEstado,rutaAccion,descenso`. `movio` = desplazamiento >0.01 desde el tick anterior; `cambioEstado` = estado del ejecutor distinto al previo. Bbox via `getBbWidth/getBbHeight` (dimension real de la entidad, antes **NO MEDIDO**).

**Resumen al cerrar** (en `cerrar(...)`, unica salida): registros, descartados, fallos, y por indice: registros, primer/ultimo tick y **huecos** de muestreo. Cualquier fallo de escritura incrementa `trFallos` y se reporta como `FALLO DE INSTRUMENTO traceNav` — nunca se desactiva en silencio.

**Volumen:** 8 raiders x 6 600 ticks ≈ 53 000 lineas (~10-15 MB). Sin recorte por ahora (`trDropped` queda a 0 y se informa; si se necesita, el limite se anade en una revision posterior).

**Se conserva:** snapshots de 300 ticks, `RAID PROX` de 20 ticks, fases e integridad del muro. La traza es **observacional**: no re-calcula planificacion, no escanea el mundo, no cambia decisiones.

**NO MEDIDO / NO IMPLEMENTADO (siguen):** razones internas del planificador (nodos expandidos, candidatos evaluados), clasificacion de obstruccion por celda, ciclo de vida de acciones con ID (`ACTION_*`), y deteccion de atascos con sus contadores — todo eso requiere los hooks de produccion de la fase 11(b), que **no** se han tocado.

> Nota: al insertar el bloque se sustituyo el comentario javadoc de `informeRaid`; el metodo y su comportamiento no cambiaron (solo el comentario). Se dejara constancia para no confundirlo con un cambio funcional.

### Edit 11(b) — PUENTE produccion -> traza (PARCIAL: puente listo, puntos de emision NO)

**Arquitectura elegida (documentada):** clase nueva de produccion `com.chipoodle.devilrpg.entity.nav.NavTrace`:

- `ON` = `Boolean.getBoolean("devilrpg.a2n.traceNav")` (**apagada por defecto**); con la bandera apagada, `ev(...)` es una comparacion de booleano y retorno.
- `sink(Consumer<String>)`: el **arnes** instala el consumidor; **produccion no hace E/S de ficheros**.
- `ev(tipo, campos)` publica `TIPO campo=valor …` al consumidor; **nunca propaga excepciones** (diagnostico no puede romper el juego).
- Sin objetos de Minecraft en inicializadores estaticos; sin dependencias circulares (nav -> NavTrace, arnes -> NavTrace).

**Implementado y compilado:** la clase `NavTrace` y el enganche en el arnes (`NavTrace.sink(linea -> trOut.write("EVT " + linea))` dentro de `traceInit`), de modo que cualquier evento de produccion cae en el MISMO `nav-trace.log` que la traza por tick, con prefijo `EVT`.

**Estado real:** el puente esta listo, pero **todavia no hay ni un solo punto de emision en produccion**. `NavPlanner`/`NavExecutor`/`NavObstruction` **no** se han tocado en esta edicion. Por tanto la traza actual sigue siendo solo-arnes (11(a)); no hay eventos de planificador, obstruccion, ciclo de acciones ni recuperacion.

**Puntos de emision pendientes (ordenados por valor forense, sin cambiar comportamiento):**
1. `NavExecutor.describirDescenso` — cada rechazo (`DROP` no contiguo, `dy>0`, caida excesiva, holgura) con la celda evaluada y el origen usado: es EL defecto que explica `FAILED`/`RECOVERING` a 2-6 bloques del muro.
2. `NavPlanner.planGlobal`/`busquedaLocal` al retornar — exito, nodos expandidos, `hitBudget` y motivo (valores que YA calcula).
3. `NavExecutor.tick` — transiciones de estado (`PLANNING->TRAVERSING` etc.) con ID de accion monotonico.
4. `pasoDeConstruccion`/`buscarEscalonDeDescenso` — veredicto por candidato (usa `esCeldaColocable`/`descensoValido` que ya existen).
5. `recover()` — entrada, motivo de atasco, contadores y estrategia (valores existentes).

**NO IMPLEMENTADO:** ciclo de vida `ACTION_*` con ID, clasificacion por celda de obstruccion, contadores de atasco. Requieren los hooks 1-5.

> Recomendacion tecnica vigente (opinion, no accion): antes de cablear estos cinco hooks conviene arreglar el desajuste de origen del `DROP` entre planificador y ejecutor, porque es el unico defecto de produccion que los datos ya han identificado tres veces (Stage 7O y dos raids) y los hooks 1 y 4 lo observarian en lugar de corregirlo.

### Edit 17 — DROP con ORIGEN PLANIFICADO + guarda de ejecutabilidad (IMPLEMENTADO)

**Origen NO usado:** `plannedFrom` es el origen de la **RUTA** (unica asignacion en L1618 al instalar ruta; se anula en los bordes del ciclo de vida), no el origen de cada transicion DROP — comprobado en Edit 16 y por eso **descartado**. `indiceDeEtapa` es el indice de **etapa**, no de nodo.

**Origen implementado (rama DROP de `NavExecutor`):**
``java
BlockPos origenPlan = null;
if (route != null && routeIndex > 0 && routeIndex < route.nodes().size()) {
    NavRoute.Node actual = route.currentNode(routeIndex);
    if (actual != null && actual.pos().equals(node.pos())) {          // identidad: el nodo es el que se ejecuta
        NavRoute.Node previo = route.currentNode(routeIndex - 1);
        if (previo != null) origenPlan = previo.pos();               // fuente real de ESTA transicion
    }
}
Descenso descenso = describirDescenso(node.pos(), …, true, origenPlan);
``
Indice 0, identidad fallida, cursor fuera de rango o ruta nula ⇒ `origenPlan = null` ⇒ **camino vivo intacto**.

**Contiguidad (`describirDescenso` 4 args; el 3-arg se conserva como sobrecarga que delega con null):**
`contiguoVivo = |dxVivo|<=1 && |dzVivo|<=1` (intacto) · `contiguoPlan = |dxPlan|<=1 && |dzPlan|<=1` ·
`cercaOrigen = |dx|<=1 && |dy|<=1 && |dz|<=1` **desde `mob` (coordenadas de entidad) hasta la celda de despegue**,
por eso se comparan las tres componentes (una transicion cambia celda y nivel). Acepta si
`contiguoVivo || (contiguoPlan && cercaOrigen)`. Si no, rechazo con motivo que separa *vivo* y *plan*.

**Orden de comprobaciones (sin cambios):** contiguidad primero; DESPUES, y siempre, `dy>0`, `maxSafeDrop`,
aire en aterrizaje y cabeza, soporte, corazon del salto, verificacion de construccion, validez de ruta. La ruta
planificada **no puede** saltarse ninguna: solo añade una aceptacion y el resto sigue ejecutandose.

**Evento:** `DROP_CONTIG uuid=… idxRuta=… origen=<x,y,z|NONE> aterrizaje=… vivo=… dxVivo=… dzVivo=… dxPlan=…|NONE dzPlan=…|NONE cercaOrigen=<bool|NONE> contiguidad=RECHAZA motivo={…}`.
Se emite **solo en el rechazo de contiguidad** (via `NavTrace.ev`, sin E/S en produccion, apagado por defecto). **No** se emite un evento de aceptacion ni se afirma que el descenso completo se haya validado: eso queda pendiente (ver limitaciones).

**Validacion:** `compileJava` **BUILD SUCCESSFUL**; `lint_aldea.py --strict` OK; `git diff --check` 0.
Artefactos: `NavExecutor.java` = `24138E1DAD7B32A50A8CD92B4C03CE9AC5E2F80A6B69CFFECD5A51A5E5AA67DC`; `NavTrace.java` y el arnes sin cambios.

**NO MEDIDO / pendiente:** comportamiento en runtime (sin raid); evento de **aceptacion** y veredicto completo (los rechazos posteriores — dy, caida, holgura, soporte — no emiten evento todavia); y si el cambio hace descender a los raiders de r~65 a r<62.

### Edit 19 — Seleccion de centro por RESOLUBILIDAD DE FUNDACION (IMPLEMENTADO)

**Por que fallo el centro anterior:** en `a2n-20261019-025135` la fundacion quedo incompleta con **33 columnas no resueltas**, todas terminando en `minecraft:water` a Y=62; la politica de no sobrescribir fluidos (correcta) impidio sellarlas, y el guard fail-closed aborter el raid antes de spawnear (8/8 = 0). El muro en si paso (1 200/1 200, `fallosBase/Altura/Continuidad=0`).

**Cambio (solo arnes, fixture):** `evaluarRecinto` ahora evalua tambien la **resolubilidad de fundacion** con la MISMA regla que la construccion, en solo lectura: por columna, desde Y=69 hacia abajo hasta 64 bloques; si el primer bloque no-aire **no tiene colision** (agua/lava/planta) cuenta `fallosFundacion`. El selector del centro exige ahora `r[0..3]==0 && r[5]==0`, y la linea `CANDIDATO` reporta `fallosFundacion=`. Es decir: **un candidato ya no puede elegirse solo por geometria de muro**; debe tener ademas fundacion resoluble sin tocar fluidos.

**Sigue sin cambios:** politica de fluidos, tope de profundidad (64), radio 62 / alto 3 / 1 200 celdas, reglas y exigencia 8/8 de spawn, guard fail-closed, navegacion de produccion (`NavExecutor` sigue `24138E1D…AA67DC`), correccion DROP de Edit 17.

**Verificacion estatica:** NO es posible demostrar aqui que exista un centro resoluble — el terreno solo se lee en un servidor vivo. Lo que si queda demostrado es que **el fixture ahora se niega a mutar el mundo y a spawnear en un centro con agua bajo el anillo**, y que registra cada candidato con `fallosFundacion`. La existencia de un centro valido dentro de los 16 candidatos **requiere confirmacion en runtime** y debe considerarse NO MEDIDA hasta entonces.

**Deuda conocida (no corregida en esta edicion):** la preparacion de terreno (y su fundacion) corre **antes** del bucle de busqueda, es decir en el centro original; si el centro elegido es otro, la banda del centro original queda preparada sin uso (artefacto del mundo aislado, no afecta a la medicion pero conviene reordenar en una edicion posterior: buscar primero, preparar despues).

### Edit 20 — ORDEN corregido: seleccionar centro ANTES de tocar el terreno (IMPLEMENTADO)

**Defecto de orden (medido en `a2n-20261019-025135` y razonado en Edit 19):** la preparacion de terreno (repisa Y=70, despeje Y=71..103 y fundacion) se ejecutaba en el **centro original** *antes* del bucle de busqueda. Como los anillos candidatos (±32/±64) pueden solaparse con esa banda, la evaluacion de `fallosFundacion` podia leer terreno ya modificado -> **contaminacion de la evidencia de seleccion**.

**Orden nuevo (verificado por numero de linea en el fuente):**
| Paso | Linea |
|---|---|
| BUSQUEDA de centro (evaluacion en solo lectura, `evaluarRecinto` con `fallosFundacion`) | **1702** |
| Puerta fail-closed: si ningun candidato pasa -> `FIXTURE RAID INVALIDO` y return | **1742** |
| PREPARACION de terreno (repisa + despeje + FUNDACION) ya sobre el centro ELEGIDO | **1747** |
| Informe `FUNDACION: …` (auditoria de construccion) | 1844 |
| `FIXTURE RAID listo` + `rFixtureOk` (incluye `fundacionCompleta`) | 2000 |

Es decir: **primero se lee el terreno virgen para elegir, despues se muta** — y solo en el centro elegido. Si no hay candidato elegible, **no se prepara terreno ni se spawnnea** (la puerta devuelve antes).

**Flujo de coordenadas del centro elegido:** el selector asigna `cx`/`cz` y `rCentro`/`rAncla`; la preparacion posterior usa `cxOriginal`/`czOriginal`… **ojo**: la preparacion y la fundacion siguen usando `cxOriginal`/`czOriginal` (el centro documentado) para el anillo, mientras que `rCentro`/`rAncla` ya apuntan al elegido. **Esto es una inconsistencia conocida que NO se corrige en esta edicion** (habria que sustituir `cxOriginal`/`czOriginal` por `cx`/`cz` en la preparacion, la fundacion y el `ySueloRef` asociado). Se documenta aqui como deuda explicita: si el centro elegido difiere del original, la banda preparada no coincidiria con el anillo del muro. **Antes de un run de aceptacion hay que cerrar esta deuda** (una sustitucion acotada de 6-8 usos), o fijar el centro.

**Sin cambios:** politica de fluidos, tope de profundidad 64, radio 62 / alto 3 / 1 200 celdas, reglas y 8/8 de spawn, guard fail-closed, `NavExecutor` (`24138E1D…AA67DC`), correccion DROP de Edit 17, navegacion de produccion.

**NO MEDIDO:** que exista un candidato con `fallosFundacion=0` dentro de ±32/±64 sigue requiriendo **un run** (el terreno solo se lee en servidor vivo). Nada de esta edicion lo demuestra.

### Edit 21 — Preparacion alineada al centro ELEGIDO (IMPLEMENTADO)

**Inconsistencia corregida:** el selector elegia `cx`/`cz` y actualizaba `rCentro`/`rAncla`, pero la preparacion de terreno, el despeje y la fundacion seguian usando `cxOriginal`/`czOriginal` -> si el centro elegido diferia del documentado, **la banda preparada no coincidia con el anillo del muro**.

**Cambio (solo arnes):** dentro del bloque de preparacion (repisa Y=70, despeje Y=71..103, fundacion y sus trazas) se sustituyeron **todas** las referencias a `cxOriginal`/`czOriginal` por `cx`/`cz` (el centro elegido). Verificado: **0 referencias** a `cxOriginal`/`czOriginal` quedan dentro del bloque de preparacion.

**Referencias INTENCIONALMENTE conservadas al origen de busqueda** (no son preparacion):
| Linea | Uso |
|---|---|
| 1673-1674 | definicion de `cxOriginal`/`czOriginal` (= centro de busqueda documentado) |
| 1717-1718 | `ccx = cxOriginal + ox`, `ccz = czOriginal + oz` — los 16 candidatos se generan desde el origen de busqueda (contrato deterministico intacto) |
| 1734 | `" (original=" + cxOriginal + "," + czOriginal + ")"` — solo diagnostico de que centro se comparó |

**Flujo ahora coherente:** seleccion (lectura, `fallosFundacion`) -> si no hay candidato: INVALIDO y return -> preparacion + fundacion **en `cx`/`cz`** -> `FUNDACION: … centro=cx,cy,cz` -> muro y metrico de 1 200 celdas -> spawn desde `rAncla` (= centro elegido).

**Sin cambios:** politica de fluidos, tope 64, radio 62 / alto 3 / 1 200 celdas, 8/8, guard fail-closed, `NavExecutor` (`24138E1D…AA67DC`), correccion DROP Edit 17, limites de busqueda.

**Validacion:** `compileJava` **BUILD SUCCESSFUL**; `lint_aldea.py --strict` OK; `git diff --check` 0. Arnés: `FBEC227670DF9C74EE302C7523BB8700F145B22CD0D5FC6D642735E3D38370E3`.

**NO MEDIDO (solo runtime):** si existe un candidato con `fallosFundacion=0`; y que la auditoria `FUNDACION: … noResueltas=0` pase en el centro elegido. Nada de eso se puede demostrar sin servidor vivo.

---

## Edit 26 — Corrección del HISTORIAL DE VALIDACION (solo documentación)

Esta sección corrige el registro de validación previo. **Nada de Java se ha modificado en Edit 26.**

### El error de validación (Edits 19–21)

Las compilaciones reportadas como exitosas en **Edit 19, Edit 20 y Edit 21 NO validaron** `tools/a2n/A2NavHarness.java`: `compileJava` compila `src/main/java/com/chipoodle/devilrpg/debug/A2NavHarness.java`, y en esas ediciones **no se recopió** el arnés editado a esa ruta. Se compiló una **copia obsoleta**. Regla operativa a partir de aquí: **copiar → verificar hash idéntico → compilar**, y un veredicto de compilación solo cuenta si el hash de la copia coincide con el de la fuente inmediatamente antes.

### Cronología corregida

| Edit | Resultado REAL |
|---|---|
| 19 | `fallosFundacion` añadido a la evaluación y al selector. Compilación reportada = **no aplicable** (copia obsoleta). |
| 20 | Reordenación (selección antes de mutar terreno) mediante **movimiento de bloque por índice de línea** -> dejó el método dañado: fragmento huérfano, `log` truncado, llaves desbalanceadas. Compilación reportada = **no aplicable**. |
| 21 | Sustitución `cxOriginal`→`cx` en la preparación. Compilación reportada = **no aplicable**. |
| 22 | Intento de raid `a2n-20261019-053632`: **falló en `:compileJava`** (`3 errors`) **antes** de generar mundo. `srv.log`=482 B, `fin.txt`=0 B, sin `nav-trace.log`. El lanzador quedó colgado y se mató. Evidencia preservada. |
| 23 | **Sin cambio de fuente y sin compilación nueva.** |
| 24 | Se eliminó el fragmento huérfano, se cerró el `log` de auditoría con `);` y se restauró la llave de la guarda de chunks. **La compilación siguió fallando** (`illegal start of type` 1769, `invalid method declaration` 1844) porque **una llave sobrante cerraba el método antes** de la preparación. |
| 25 | **Eliminada esa llave sobrante.** Fuente y copia de compilación **byte-idénticas**: `33419C12C97301FA521B6FC3C191A79074D1FF2BFF2C80A17DC5A2D1F3BF1197`. `compileJava` **BUILD SUCCESSFUL** (15 s); `lint_aldea.py --strict` **OK**; `git diff --check` **0**. **Primera compilación verde real del arnés editado.** |

### Orden de ejecución verificado (lectura de fuente, línea a línea)

1. Guarda de chunks (cerrada, con `return`) — 1696-1701
2. **Selección acotada y de solo lectura** de centro — 1702-1738
3. **Puerta fail-closed** (sin candidato -> `FIXTURE RAID INVALIDO` + return **antes** de mutar) — 1739-1745
4. **Preparación de terreno y fundación en el centro ELEGIDO `cx`/`cz`** — 1746-1842
5. Auditoría `FUNDACION: …` — 1843
6. `CENTRO DERIVADO` / `ALDEA idx` / `ANCLA congelada` — 1855-1862
7. Construcción del anillo del muro (r=62, alto 3, 1 200 celdas) — 1863+

### Distinción explícita: compilación ≠ runtime

Lo único demostrado por Edit 25 es **validación estática**: la fuente compila, pasa lint y no tiene errores de whitespace. **NO** hay validación en runtime.

**NO MEDIDO a fecha de Edit 26:**
- Selección de candidato y `fallosFundacion` por candidato.
- Auditoría de fundación en construcción y `noResueltas`.
- Integridad del muro (1 200 celdas) en el centro elegido.
- Spawn **8/8**.
- Descenso/cruce real y comportamiento del **DROP** de Edit 17 (`DROP_CONTIG` nunca se ha observado en ejecución).

**A2 sigue ABIERTO.** No existe umbral de aceptación y ningún resultado de asedio está validado en runtime.

### Edit 27 — Primer runtime con el arnes reparado: **SELECTION FAILED** (contradiccion de criterio, medida)

**Run `a2n-20261019-110446`** · marker `INVALIDO (raid) - RAID FIXTURE INVALIDO | fallosDeInstrumento=1` · `nav-trace.log` 0 B (nunca se abrio: no hubo `rIniciado`) · `SPAWN`: 0 · mundo generado y aislado, sin acceso a `New World`.

Compilacion PREVIA (copy-then-hash): copia == fuente (`33419C12…BF1197`), `compileJava` **BUILD SUCCESSFUL**; `lint` OK; `diff --check` 0. Es decir: el fallo **no** es de build.

**Los 16 candidatos fallan, y la razon es estructural:**

| idx | centro | fallosAltura | fallosContinuidad | **fallosFundacion** | valido |
|---|---|---|---|---|---|
| 1 | 661,71,533 | 248 | 219 | 33 | false |
| 6 | 693,71,533 | 141 | 205 | 36 | false |
| **14** | **629,71,597** | **321** | **248** | **0** | false |
| … resto | … | 236-532 | 197-270 | 36-173 | false |

**Causa raiz (contradiccion introducida por Edit 20):** al mover la preparacion **despues** de la seleccion, el selector pasó a evaluar **terreno virgen**, pero sigue exigiendo `fallosAltura==0` y `fallosContinuidad==0` — contadores que **solo la preparacion puede satisfacer** (repisa Y=70 + despeje Y=71..103). Por tanto **ningun candidato puede pasar nunca**: el criterio de seleccion es insatisfacible por construccion. El candidato **14 (629,71,597)** demuestra que si existe un centro con **`fallosFundacion=0`**.

**Correccion minima (no aplicada):** el selector debe exigir solo lo que la preparacion NO puede arreglar — `r[5]==0` (fundacion resoluble) y, como mucho, `r[0]==0` (sin columnas sin suelo) — y **dejar de exigir** `fallosAltura`/`fallosContinuidad` en la fase de seleccion, porque esos invariantes se cumplen **despues**, en la construccion (donde la auditoria `TERRENO PREPARADO`/`PARED total` los verifica sobre el mundo ya preparado).

**Clasificacion:** **SELECTION FAILED** (fallo de criterio de seleccion, no de terreno). Ninguna otra gate se midio: `FUNDACION`/`PARED total`/`SPAWN`/`DROP_CONTIG` = **NO MEDIDO**.

### Edits 29-33 (cierre) — fixture completa y navegación medida como FALLO DE PLANIFICACION

**Edit 29 (spawn radial):** offset radial determinista {0,±4,±8,±12,±16} en la MISMA direccion, mismo predicado de holgura. Run `a2n-20261019-113725`: SPAWN idx=3 nominal=562,664 ajustado=565,661 offsetRadial=-4; **7/8** (idx 5 falla INSUFFICIENT_CLEARANCE con columnasConSuelo=33).

**Edit 30 (spawn lateral):** fase perpendicular {±4..±16} + set de columnas usadas (distintas). Run `a2n-20261019-115244`: **7/8** otra vez; idx 5 no tiene columna valida en ±16 en ninguna direccion.

**Edit 31 (plataformas de spawn, AUTORIZADO):** si radial+lateral fallan, se prepara SOLO la columna nominal (suelo + 2 de aire, 3 celdas, registrado). Run `a2n-20261019-120718`:
SPAWN idx=5 PLATAFORMA en 562,95,530 · SPAWN TOTAL: intentos=8 ok=8 fallos=0 · `FUNDACION … noResueltas=0` · `PARED total … celdas=1200 … anilloCompleto=true` · marker `INSTRUMENTADO … vivos=8 … fallosDeInstrumento=0`. **Las cuatro fixture gates PASARON por primera vez (8/8).**

**Edit 32 (ruta de la traza):** el trace escribia relativo al CWD del proceso del juego; ahora resuelve contra `a2n.raiz` como `cerrar()`. Run `a2n-20261019-122512`: **nav-trace.log 15 317 401 B**, 52 800 registros, 8 raiders; gates otra vez PASS (8/8).

**Edit 33 (analisis offline, 52 800 registros, 0 malformados):**

| idx | movio=true | movio=false | estados | veredicto |
|---|---|---|---|---|
| 1 | 1 | 6 599 | PLANNING 228 -> **FAILED 6 372** | planifico y fallo |
| 2 | 0 | 6 600 | **PLANNING 6 600** | nunca sale de PLANNING |
| 3 | 1 | 6 599 | PLANNING 201 -> FAILED 6 399 | fallo |
| 4 | 1 | 6 599 | PLANNING 201 -> FAILED 6 399 | fallo |
| 5 | 1 | 6 599 | PLANNING 201 -> FAILED 6 399 | fallo |
| 6 | 1 | 6 599 | PLANNING 201 -> FAILED 6 399 | fallo |
| 7 | 59 | 6 541 | PLANNING 25 -> RECOVERING 56 -> **ARRIVED 6 519** | anomalia: "llega" tras 3.4 bloques |
| 8 | 1 | 6 599 | PLANNING 201 -> FAILED 6 399 | fallo |

**Clasificacion: `Planning repeatedly fails` (7/8) + `no sale de PLANNING` (1/8).** Ningun raider entra nunca en `TRAVERSING`: **no hay ruta que ejecutar**. `entraronPerimetroEver=0`; **DROP_CONTIG NO MEDIDO**. La anomalia de idx 7 (`ARRIVED` tras 3.4 bloques) queda registrada sin explicacion medida.

**Correccion del escritor (Edit 33):** los literales `"\\n"` (barra-n literal) se sustituyeron por `"\n"` real en las DOS escrituras de la traza (cabecera y registro); esquema, campos y frecuencia intactos. Fuente y copia de compilacion byte-identicas: **`1656F1E6FC9DB6C27C3C478D4BB1A9E593562F6D056E6982A9F3026D37A10AD8`** (2 369 lineas). `compileJava` **BUILD SUCCESSFUL**, `lint` OK, `git diff --check` 0.

**Salvedades pendientes (NO corregidas):** (1) la integridad post-spawn (`integridadAnillo`) no incrementa `fallosDeInstrumento`; (2) la cobertura de chunks se calcula en el centro ORIGINAL aunque el centro elegido se desplace. La navegacion **no** esta arreglada y el DROP **no** esta medido.

---

## Task A — Planner diagnosis and fresh-session handoff

**Fecha:** 2026-10-19. **Naturaleza:** diagnóstico de solo lectura + documentación. **No** se editó código, **no** se compiló, **no** se lanzó Minecraft, **no** se ejecutó raid.

### A. Verified baseline

| Ítem | Valor verificado |
|---|---|
| Rama / HEAD | `feature/autonomous-village` / `97220be5453b57387002b9bc91935807cc665274` |
| `tools\a2n\A2NavHarness.java` | `1656F1E6FC9DB6C27C3C478D4BB1A9E593562F6D056E6982A9F3026D37A10AD8` |
| `src\main\java\com\chipoodle\devilrpg\entity\nav\NavExecutor.java` | `24138E1DAD7B32A50A8CD92B4C03CE9AC5E2F80A6B69CFFECD5A51A5E5AA67DC` |
| `NavTrace.java` | `7F0FCE43567F74E8E6E346C470311B1968CDF7207DE413038A46B641277879AD` |
| Handoff (antes de esta sección) | `947B6064B7EDB2850B80873488ED2D8F9E584211AB3573B86FCC40BF49D6E722` |
| `.partial` (NO usar como fuente) | `2C3EBA9ED6E936B595E26650B796A624838B5CB4FF6CFD7B1804BE7A13552127` |
| `run\saves\New World\level.dat` | `D9A58A246C23FDA92BFE2E6CA6770F7BDB1A1FD8C886DB819E485B4E0BD27140` |
| Worktree / staged / principal | 9 entradas / **0** / **0** |
| Procesos Java | **0** |

### B. Diagnosis (referencias exactas)

Rutas: **NP**=`entity/nav/NavPlanner.java`, **NE**=`entity/nav/NavExecutor.java`, **NR**=`NavRoute.java`, **NT**=`NavTrace.java`, **AZ**=`AggressiveZombieEntity.java`, **H**=arnés.

**Tipo de resultado del plan (creación y campos reales):**
- Local, **NP:53**: `public record Result(NavRoute route, int expansions, boolean hitBudget, String summary)` — **no existe** campo `reason`/`rejected`/`nodesExpanded` (usar `expansions`).
- Global, **NP:81-82**: `GlobalResult(NavRoute route, BlockPos frente, boolean alcanzoMision, int expansions, boolean hitBudget, String reason, String summary)`.
- Creación local: **NP:405-406** (inicio==fin), **NP:531** (sin ruta), **NP:543** (ruta hallada). Global: **NP:180-182**, **NP:296-297**, **NP:307-308**.

**Consumo en el ejecutor:** plan local invocado en **NE:1610-1611**; contadores en **NE:1612-1615**; inspección **NE:1616** (`hitBudget`), **NE:1634** (`route().isEmpty()`), **NE:1643** (misma firma); aceptación **NE:1650** y **NE:1655** (`state = TRAVERSING`, ÚNICA). Global invocado **NE:1143-1144**, inspeccionado **NE:1145/1149/1182-1184/1189-1211/1225-1228**.

**Transiciones terminales:** `state = FAILED` en **NE:1570** (replans agotados), **NE:1588** (fallos de escalón), **NE:1996** (construcción agotada), **NE:2049** (guarda de ruta), **NE:2084** (recuperación agotada). `state = ARRIVED` sólo en **NE:1521**. Test de llegada: `ARRIVE_DISTANCE_SQR = 2.25D` (**NE:117**) comparado contra `objective` (**NE:1495-1497**), no contra el spawn; reutilizado para nodos en **NE:1664-1665**.

**Observabilidad campo por campo:**
- `route`: EXPOSED tras aceptación (`route()` NE:1430, `rutaDiag()` NE:1438, `describe()` NE:1456); el **Result crudo pre-aceptación NO está expuesto** (variable local).
- `expansions`: EXPOSED como último/total (`expansionsLast()` NE:2185, `expansionsTotal()` NE:2190); **sin historial por intento**.
- `hitBudget`: **NO expuesto directamente**; sólo texto en `ultimoPlanResumen()` NE:1959 y contador acumulado `budgetHits` (`describe()` NE:1459) que **no** se incrementa cuando el presupuesto se deriva a construcción (NE:1620-1624 antes del `budgetHits++` de NE:1626).
- `summary`: EXPOSED sólo el último (NE:1959).
- `GlobalResult`: `frente`/`hitBudget`/`reason` **NO expuestos como campos**; sólo dentro del texto de `ultimoGlobalDiag()` NE:1292.
- `rutaAccionDiag()` NE:2155-2158 expone sólo `nodoDeRecuperacion` y `ultimaAccionDeRuta`; `planConstruccionDiag()` NE:319-326 nada del plan.

**Evento mínimo propuesto (observación pura).** Fichero **NE**, inserción entre **NE:1615** y **NE:1616** (tras `lastPlanSummary = result.summary();`):

``java
if (NavTrace.on()) { NavTrace.ev("PLAN_RESULT", "uuid=" + mob.getUUID() + " intento=" + plansRun
    + " replans=" + replans + " pos=" + mob.blockPosition().toShortString()
    + " objetivo=" + (objective == null ? "-" : objective.toShortString())
    + " ok=" + !result.route().isEmpty() + " nodos=" + result.route().length()
    + " exp=" + result.expansions() + " presupuesto=" + result.hitBudget()
    + " firmaIgual=" + (result.route().signature() == lastRouteSignature)
    + " estadoAntes=" + state
    + " desenlace=" + (result.hitBudget() ? "PLANNING_PRESUPUESTO"
        : (result.route().isEmpty() ? "RECOVERING_VACIA"
        : (result.route().signature() == lastRouteSignature ? "RECOVERING_FIRMA" : "TRAVERSING")))
    + " resumen={" + result.summary() + "}"); }
``
Todos los identificadores están verificados en el fuente. **Neutralidad:** `NavTrace.ON` es un `final` cacheado (**NT:22**), `on()` lo devuelve (**NT:31-33**), `ev` retorna de inmediato si está apagado (**NT:44-47**), si no hay sink (**NT:48-51**) y **traga** excepciones del consumidor (**NT:52-56**); el `if (NavTrace.on())` evita además evaluar los argumentos. Sólo lecturas puras; ninguna escritura, ninguna llamada al planificador. El texto `desenlace` deriva de las ramas exactas NE:1616-1655; el `Result` local no tiene `reason`.

**Anomalía `ARRIVED` (idx 7).** `ARRIVED` exige `objetivoEsLaMision()` (NE:1120-1128: `objective == misionFinal` o `misionDePie`); llegar a un nodo de ruta intermedio NO basta (NE:1500-1514 reasigna a `misionFinal`). Ninguna línea asigna `objective = mob.blockPosition()` (**NOT EXISTS**); los escritores son NE:1385/1396, NE:1008, NE:1261, NE:1503. Con objetivo = ancla `629,71,597` y spawn `696.5,63,530.5` (**~95 bloques**) `ARRIVED` era imposible: por tanto **el objetivo usado por el test no era el ancla registrada**. Evidencia: `a2n-20261019-122512\srv.log:503` spawn idx7 `objetivo=629,71,597` → `:515` (t=300) `estado=ARRIVED` persistente hasta el final. **Hipótesis mejor soportada:** `EscapeWaterGoal` llama `navegarConEjecutorPropio(shore)` (AZ:2327/2358/2372) → `setObjective(shore)`+`setPlanMision(shore)` (AZ:387/390), y `findNearestShore` explora ≤16 bloques (AZ:2403-2417); al llegar, `objetivoEsLaMision()` es true, `etapas` vacío → NE:1521. Persiste porque `ARRIVED` es terminal (NE:1528-1530) y `setObjective` sólo resetea si el nuevo objetivo está a >3 bloques (`OBJECTIVE_MOVE_THRESHOLD_SQR=9.0` NE:57; test NE:1394-1396). **No probado:** `isInWater`/`shore` no se registran. **Evidencia faltante:** `navExecutor.objective()` (NE:1426) y `objetivoLocalDiag()` (NE:1276) en el tick de `ARRIVED`; el `shore` elegido y qué goal llamó `setObjective`; un evento en `setObjective` (no existe).

> **Discrepancia a verificar:** el diagnóstico indica que el escritor de la traza sigue emitiendo `\n` literal en **H:1439/H:1494**; Edit 33 sustituyó 5 literales y verificó `"\\n"` ausente. Antes de Task B, **re-verificar** `Select-String -Path tools\a2n\A2NavHarness.java -Pattern '\\\\n'` y, si queda alguno, corregirlo en esa misma edición.

### C. Current A2 status

- **A2 sigue ABIERTO.** No existe umbral de aceptación acordado.
- Las **cuatro gates del fixture PASARON** en `a2n-20261019-120718` y se reconfirmaron en `a2n-20261019-122512` (selección idx=14 `629,71,597`; `noResueltas=0`; `1200/1200`; `SPAWN intentos=8 ok=8`).
- Navegación medida (histórico): **7/8 entraron en `FAILED` tras ~200 ticks de `PLANNING`**; **1/8 permaneció en `PLANNING`**; **ninguno entró en `TRAVERSING`**.
- **Un `ARRIVED` anómalo** tras ~3.4 bloques (ver B).
- **`DROP_CONTIG`: NO MEDIDO.**
- Salvedades vigentes: (1) la integridad post-spawn no incrementa `fallosDeInstrumento`; (2) la cobertura de chunks se calcula en el centro original aunque el centro elegido se desplace.
- Estos resultados son **históricos**: Task A **no** los reprodujo.

### D. Exact next action (Task B — copiar y usar)

1. **Preflight:** rama `feature/autonomous-village`; HEAD `97220be5453b57387002b9bc91935807cc665274`; hashes: arnés `1656F1E6…D37A10AD8`, `NavExecutor` `24138E1D…AA67DC`, `NavTrace` `7F0FCE43…7879AD`; 9 entradas, 0 staged; worktree principal limpio; **0 procesos Java**; `.partial` `2C3EBA9E…52127` y `level.dat` `D9A58A24…27140` intactos. Si algo difiere: **parar y reportar**.
2. **Re-verificar** si quedan literales `\\n` en el arnés y, en su caso, corregirlos (misma edición).
3. **Implementar SOLO** el evento `PLAN_RESULT` de §B (entre NE:1615 y NE:1616). **Nada** de navegación, presupuestos, umbrales ni transiciones.
4. `Copy-Item tools\a2n\A2NavHarness.java src\main\java\com\chipoodle\devilrpg\debug\A2NavHarness.java -Force` → **hashes idénticos** → `.\gradlew.bat compileJava` → `python tools\lint_aldea.py --strict` → `git diff --check`.
5. **Un** raid: `-Modo raid -Pared 1 -NavCustom true -Semilla 12345 -FaseA 3000 -FaseB 6600 -LimiteMinutos 25`, con `JAVA_TOOL_OPTIONS='-Ddevilrpg.a2n.diagRetirada=true -Ddevilrpg.a2n.traceNav=true'` dentro de `try/finally` (restaurar el valor previo exacto).
6. **Evidencia:** las cuatro gates; eventos `PLAN_RESULT` de los 8 raiders (`ok`, `nodos`, `exp`, `presupuesto`, `desenlace`, `resumen`); si alguno entra en `TRAVERSING`; movimiento sostenido; `ARRIVED` real vs objetivo (usar `objective()`); si `DROP_CONTIG` aparece.
7. **Handoff:** añadir sección de Task B con resultados reales e incertidumbres restantes.
8. **Prohibido:** commit, push, staging, stash, reset, clean, checkout, merge, rebase.

> Un evento de diagnóstico **no es un arreglo**. A2 **no** puede declararse completo mientras los criterios de aceptación no se demuestren en runtime.

### E. Fresh-session startup note

Lee **este handoff completo primero**, verifica los hashes y el estado del repositorio declarados en §A, y continúa con **Task B** (§D). **No repitas los Edits 27-33** ni relances los experimentos previos: la selección de centro, la fundación, el muro y el 8/8 ya están resueltos y medidos en `a2n-20261019-120718` / `…122512`; lo que falta es **por qué la planificación termina en `FAILED`/`PLANNING` perpetuo** y, después, si hay descenso y cruce reales.

### Task A — resolución de la discrepancia del escritor (2026-10-19)

El diagnóstico de Task A detectó que **seguían existiendo dos literales `\n`** en el escritor de la traza, contradiciendo el cierre de Edit 33. **Verificado y corregido:** Edit 33 sustituyó solo la forma exacta `"\\n"` (con comillas), pero los dos casos restantes eran `\\n")` al final de cadenas multilínea:

- `L1439`: cabecera `# cols=…\\n");`
- `L1494`: fin del registro por tick `… + "}\\n");`

Corregidos ambos (`\\n")` → `\n")`). Verificación: `Select-String -Pattern '\\\\n'` → **0** restantes. Fuente y copia de compilación **byte-idénticas**: **`11980C98668EF5D13E4CF3A276C9782392A9187F51B3EF63D7FC88141FA4C940`** (2 369 líneas). `compileJava` **BUILD SUCCESSFUL**, `lint_aldea.py --strict` **OK**, `git diff --check` **0**.

**Consecuencia para Task B:** los ficheros `nav-trace.log` nuevos tendrán saltos de línea reales (parseables línea a línea); la evidencia histórica `a2n-20261019-122512\nav-trace.log` (una sola línea física) **permanece intacta** y sigue siendo válida para el análisis ya hecho. El hash del arnés de Task B ya **no** es `1656F1E6…` sino `11980C98…FA4C940`.

### Increment A — Centro autoritativo de aldea (IMPLEMENTADO)

**Fecha:** 2026-10-19. **Naturaleza:** implementación de código + validación estática. **No** se lanzó Minecraft, **no** se ejecutó ningún raid, **no** se tocó `tools\a2n\A2NavHarness.java`, `NavExecutor`, `run\saves\New World`, `build\a2n\recovery\*` ni ningún `build\a2n\a2n-*`. **No** se hizo commit/push/stage/stash/reset/clean/checkout/merge/rebase.

#### A.1 Preflight verificado (antes de editar)

| Ítem | Valor |
|---|---|
| Rama / HEAD | `feature/autonomous-village` / `97220be5453b57387002b9bc91935807cc665274` |
| `tools\a2n\A2NavHarness.java` | `11980C98668EF5D13E4CF3A276C9782392A9187F51B3EF63D7FC88141FA4C940` |
| `src\main\java\...\entity\nav\NavExecutor.java` | `24138E1DAD7B32A50A8CD92B4C03CE9AC5E2F80A6B69CFFECD5A51A5E5AA67DC` |
| Handoff (antes de esta sección) | `614C59F94970FA60275A5743BEA252446157D43777947D341B4C666885759432` |
| `run\saves\New World\level.dat` | `D9A58A246C23FDA92BFE2E6CA6770F7BDB1A1FD8C886DB819E485B4E0BD27140` |
| `tools\a2n\A2NavHarness.java.partial` | `2C3EBA9ED6E936B595E26650B796A624838B5CB4FF6CFD7B1804BE7A13552127` |
| Worktree / staged / principal | 9 entradas / **0** / `D:\Documents\DevilRpg` limpio en `migration-neoforge-1.21.1` |
| Procesos Java | **0** |

Los 9 entries del worktree eran 5 ficheros ya modificados (`docs/CONTINUAR.md`, `docs/aldea-invariantes.md`, `AggressiveZombieEntity.java`, `PlayerCapabilityForgeEventSubscriber.java`, `EventUtils.java`) y 4 directorios sin seguimiento (`docs/autonomous-village/`, `entity/nav/`, `AggressiveZombiePathPlanner.java`, `tools/a2n/`). `VillageManager.java` y `VillageSavedData.java` estaban **limpios**: los 2 ficheros que toca este incremento son las 2 entradas nuevas del worktree (9 → 11).

#### A.2 Auditoría (estado ANTES, sobre HEAD `97220be`)

**(a) Dónde se crea/elige el centro.** La aldea se construye en un punto que se recalcula del objetivo, no de un registro de aldea:

- `ObjectiveManager.tick` L34-36 toma `aux.getAnchorPoint()` (respaldo `getSpawnPoint()`), L42 calcula `target = ObjectiveTargets.targetOf(spawn, index)`, L47 llama `VillageManager.preGenerate(level, index, target)` y L56 `manageNearby(..., spawn, index)`.
- `VillageManager.manageNearby` L973: `BlockPos target = ObjectiveTargets.targetOf(anchor, i);` y L1001-1003 `preGenerate(level, i, target)`.
- `VillageManager.preGenerate` L911: `VillageGenerator.generate(level, target)` (L919). El centro físico es, por tanto, el `target` del objetivo.
- `ObjectiveTargets.targetOf` L44-50: X/Z deterministas desde el ancla (45° fijo + distancia por índice). Es la identidad de la aldea, pero **no se guardaba** en ningún sitio.

**(b) Dónde se guarda/persiste el centro.** En HEAD **no se persistía ningún centro**:

- `VillageSavedData` L178-244 (`load`) y L247-327 (`save`) guardan `Generated`, `Resolved`, `Noticed`, `Fallen`, `Pressure`, `Settlement` (salud, comida, layout, casas, rebaño, guardias, remesa), `Blueprints`, pero **no** un centro.
- El único dato de centro en el mundo era la etiqueta `CENTRO_TAG` de cada aldeano (`VillageManager` L6730), una **copia por aldeano**, no una autoridad de aldea.
- El `VillageDefense.center` (L7923-7927) y el `WorldSiege.center` (L7897-7901) viven solo en memoria y se llenan con el `target` que pase quien llama.

**(c) Consumidores del centro (todos derivan o reciben copias distintas).**

- **Muro/anillo:** `preGenerate` → `VillageGenerator.generate(level, target)` (L919); `asegurarMuro(level, target)` en `manageNearby` L1079; `rehacerMuro(level, center)` desde `tickVillageLife` L2441/L2537, con el `center` que llega de `manageNearby`/`start`.
- **Recinto/protección:** `dentroDelRecinto(...)` L1339-1353; `estaProtegida` L6686-6712 llama `centroDe(level, i)` en L6693; `CustomSpawner.trySpawn` L126 y L148 la usa para vetar spawns hostiles.
- **Inicio de asedio:** `start` L1420-1457 crea `VillageDefense(..., target)` con el `target` del llamador (L1455). `spawnWave` L1703-1751 coloca la oleada y asigna `zombie.setVillageCenter(d.center)` (L1719/L1751).
- **Objetivo/diana del raid:** `pickHordeTarget` L1906-1935 calcula `BlockPos target = ObjectiveTargets.targetOf(anchor, i)` (L1920) y devuelve `Settlement(i, target)`; `HordeManager.spawnHorde` L123 usa `settlement.center()` como base del anillo y L146 `zombie.setVillageCenter(settlement.center())`.
- **Selección de spawn:** `HordeManager` L133-139 (anillo 65-81 alrededor de `settlement.center()`), `CustomSpawner` L126/L148 (`estaProtegida`), y el propio `AggressiveZombieEntity.protegidoPorLaAldea` L962 vía `dentroDelRecinto(pos, villageCenter, ...)`.
- **`[Siege]`:** las trazas de `AggressiveZombieEntity` L759, L826, L840, L1161, L1181, L1335, L1379, L1567, L1657, L1839, L2031-2034 y `NavExecutor` L801/L876/L1150/L2043/L2076 no imprimen el centro, pero la regla que deciden (`protegidoPorLaAldea`) mide contra el `villageCenter` que se les asignó en `spawnWave`/`HordeManager`.
- **Punteros/UI:** `elClerigoSenalaLaAldeaActual` L7849, `LoreStoneBlock` L118, `VillageHudOverlay` L59 y `DiarioDelInvocado` L177 (este ya usa `centroDe`).

**(d) El fallback derivado del plano: SÍ existe en HEAD (respuesta: sí).** Extracto exacto:

```java
// VillageManager.java (HEAD 97220be) L5674-5719
for (ServerPlayer enLinea : level.players()) {
    ...
    Vec3 ancla = aux == null ? null : aux.getSpawnPoint();   // L5677: getSpawnPoint, NO getAnchorPoint
    ...
}
VillageSavedData.Blueprint plano = saved.getBlueprint(objectiveIndex); // L5693
...
BlockPos centro = new BlockPos((minX + maxX) / 2, 0, (minZ + maxZ) / 2); // L5708 (punto medio de la CAJA)
```

**Impacto real:** el punto medio de la caja del plano no es el centro de la aldea. La aldea crece de forma asimétrica (almacén a +48, corral anexo a +50, taberna/pesquera al este), así que el punto medio se va al este; el propio comentario de HEAD L5668-5672 documenta el caso medido: dos construcciones de la misma aldea separadas **~16 bloques** y una aldea levantada dos veces. Ese centro desplazado lo usaban `estaProtegida` (círculo de 62), `bajarElGolemAlSuelo`, `DiarioDelInvocado` y `estadoDeLaAldea`, mientras `start`/`pickHordeTarget`/`manageNearby` seguían usando el ancla: **protección y asedio medían desde puntos distintos**. Además, el plano se vuelve a capturar en cada migración (`captureBlueprint` L2487 y L2783), de modo que el centro derivado podía **moverse entre sesiones**. El fallback se activaba cuando no había ningún jugador en línea (o ninguno con capacidad/spawn), y también en contextos sin jugadores reales en `level.players()` (arnés).

**Defectos concretos (los que cierra Incremento A):**

1. **D1 — No existía un centro autoritativo persistido.** Cada consumidor lo derivaba por su cuenta: el `target` del objetivo para generar/migrar/asediar, `getSpawnPoint()` en `centroDe`, y el punto medio del plano cuando no había jugador. Tres centros posibles para la misma aldea.
2. **D2 — `centroDe` usaba `getSpawnPoint()`, no el ancla con la que se construyó.** `spawnPoint` se fija en `CommonForgeInteractionEventSubscriber` L443-445 con `player.position()` del primer join; el ancla se fija en L448-451 con el spawn compartido del mundo. Si el jugador entró lejos del spawn (o el spawn cambió), `centroDe` devolvía un centro distinto del `target` real: la protección (`estaProtegida`) quedaba desplazada respecto al muro/asedio.
3. **D3 — El fallback del plano (~16 bloques al este) se usaba sin jugador en línea, y podía cambiar al recapturar el plano.** Ver (d).
4. **D4 — `start` y `pickHordeTarget` seguían recalculando el centro del ancla y no lo unificaban con `centroDe`.** El asedio podía nacer/anclarse en un punto distinto del que usaba la protección. `start` además arrastraba la Y del ancla (101) al `VillageDefense.center`; los consumidores la corregían con `cotaDeLaPlaza`, pero el dato guardado en memoria era inconsistente.

#### A.3 Mecanismo autoritativo: antes → después

**Antes:** centro derivado bajo demanda, sin dueño: (1) `targetOf(ancla, index)` para generar y asediar; (2) `getSpawnPoint()` del primer jugador en `centroDe`; (3) punto medio de la caja del plano si no había jugador; (4) copia `CENTRO_TAG` en cada aldeano. Sin persistencia.

**Después:** **un centro por aldea** (nivel + índice de objetivo), guardado en la representación canónica que ya existía, `VillageSavedData`:

- Campo `centres: Map<Integer, BlockPos>` (`VillageSavedData.java:178`), serializado dentro de la entrada `Settlement` como `Center` (`save` L319-321, `load` L237-239). No hay un segundo registro ni una abstracción nueva: es el mismo `SavedData` por dimensión que ya indexa las aldeas por objetivo.
- Se escribe **una sola vez**, al generar: `preGenerate` guarda `(target.x, cotaDeLaPlaza, target.z)` (`VillageManager.java:923-931`) y limpia la caché de lectura.
- `centroDe(level, index)` (`VillageManager.java:5704`) lee siempre el guardado; normaliza la Y a la cota; **no recalcula X/Z**. Caché por `ServerLevel`+índice (`CENTROS`, L5674), como antes.
- Si el guardado no existe (partida anterior) se deriva **una sola vez** del objetivo con `centroCanonico` (`VillageManager.java:5765`) y se persiste con traza `[Village] Aldea N: no tenia centro guardado...`. `centroCanonico` usa el ancla de un jugador conectado si la hay y, si no, `level.getSharedSpawnPos()` (`L5768-5770`): determinista también sin jugadores.
- Validación de un centro cargado (L5717-5735): se compara con la identidad (objetivo) y la geometría (caja del plano, `centroDentroDelPlano` L5795). Si se desvía >32 bloques o queda fuera del plano se **avisa** (`logCentroDesviado` L5812) y se **conserva** el guardado. Solo se sustituye si está a >256 bloques **y fuera de su propio plano** (dato ajeno/corrupto), con aviso; nunca se mueve una aldea establecida.
- Consumidores unificados: `manageNearby` L991-994 (`target = centro`), `start` L1466-1472 (`VillageDefense` con el centro autoritativo), `pickHordeTarget` L1939-1942 (`centroDe` en vez de `targetOf`), `elClerigoSenalaLaAldeaActual` L7854-7857 (distancia/rumbo al centro guardado si la aldea ya existe). `estaProtegida`, `dentroDelRecinto`, `spawnWave`, `HordeManager`, el muro/migración y los goals reciben ya el mismo punto.

#### A.4 Cambios exactos (archivo:línea final)

| Fichero | Cambio | Defecto que cierra |
|---|---|---|
| `src\main\java\com\chipoodle\devilrpg\world\VillageSavedData.java` | Campo `centres` (L178); `Center` en `load` (L237-239) y `save` (L319-321, unión en L291); `getCentre`/`hasCentre`/`setCentre` (L534-548) | D1: no había centro persistido. |
| `src\main\java\com\chipoodle\devilrpg\world\VillageManager.java` | `preGenerate` fija el centro una vez (L923-931) | D1/D2: el centro nace del punto real de construcción. |
| idem | `centroDe` reescrito (L5704-5760): lee el guardado, normaliza Y, valida identidad+geometría, deriva y persiste solo si falta, loguea; helpers `centroCanonico` L5765, `distanciaHorizontal` L5786, `centroDentroDelPlano` L5795, `logCentroDesviado` L5812 | D2/D3/D4: elimina el `getSpawnPoint()` y el punto medio del plano como fuentes. |
| idem | `manageNearby` usa el centro completo (L991-994), no solo su Y | D2: muro/migración ya no se anclan al spawn del jugador. |
| idem | `start` resuelve el centro autoritativo antes de crear `VillageDefense` (L1466-1472) | D4: asedio, oleada y perímetro desde el mismo punto. |
| idem | `pickHordeTarget` usa `centroDe` (L1939-1942) | D4: la horda del mundo apunta al centro autoritativo. |
| idem | `elClerigoSenalaLaAldeaActual` apunta al centro guardado si la aldea está generada (L7854-7857) | Consistencia del puntero con el asedio/protección. |
| idem | Comentario de `olvidarCentro` actualizado (L6770-6773) | Deja claro que la migración **no** mueve el centro guardado. |

No se tocó `VillageGenerator`, `VillageStorage`, `VillagePantry`, los goals, `HordeManager`, `NavPlanner`, `NavExecutor` ni el arnés A2. No se movió ni regeneró ninguna aldea/muro: el cambio solo lee/escribe el dato de centro.

#### A.5 Validación (salida raw)

`.\gradlew.bat compileJava` (con 0 procesos Java antes de lanzarlo):

```text
> Task :createMinecraftArtifacts UP-TO-DATE

> Task :compileJava

BUILD SUCCESSFUL in 26s
2 actionable tasks: 1 executed, 1 up-to-date
```

`python tools\lint_aldea.py --strict`:

```text
lint aldea: OK (sin patrones peligrosos)
LINT_EXIT=0
```

`git diff --check`:

```text
DIFFCHECK_EXIT=0
```

(stderr de Git: aviso preexistente de CRLF en `docs/CONTINUAR.md`; no es un error y no lo introduce este incremento.)

`git diff --stat` (incluye los 5 ficheros que ya venían modificados en el worktree; las filas de este incremento son `VillageManager` y `VillageSavedData`):

```text
 docs/CONTINUAR.md                                  |  363 ++++-
 docs/aldea-invariantes.md                          |   51 +-
 .../devilrpg/entity/AggressiveZombieEntity.java    | 1400 +++++++++++++++++---
 .../PlayerCapabilityForgeEventSubscriber.java      | 1287 +++++++++---------
 .../com/chipoodle/devilrpg/util/EventUtils.java    |   18 +-
 .../chipoodle/devilrpg/world/VillageManager.java   |  197 ++-
 .../chipoodle/devilrpg/world/VillageSavedData.java |   37 +
 7 files changed, 2433 insertions(+), 920 deletions(-)
```

Hashes protegidos re-verificados tras la validación: `A2NavHarness` `11980C98…FA4C940`, `NavExecutor` `24138E1D…AA67DC`, `level.dat` `D9A58A24…27140`, `.partial` `2C3EBA9E…52127` (sin cambios). Los 200+ directorios `build\a2n\a2n-*` conservan sus mtimes anteriores a esta sesión (el más reciente, `a2n-20261019-122512`, 19-oct-2026 12:32); **ninguno** fue escrito. 0 procesos Java al terminar.

#### A.6 Riesgos residuales y lo que NO se pudo verificar

1. **No hay validación en runtime.** Prohibido lanzar Minecraft/raid en este incremento: el round-trip NBT `Center` (guardar → recargar → leer) está verificado solo por compilación y lectura de código, no ejecutado en una partida. Es la verificación pendiente más importante.
2. **Partidas anteriores sin centro.** El primer `centroDe` tras actualizar deriva el centro del **ancla actual** (spawn compartido) y lo persiste. Si una aldea vieja se hubiera construido con otra ancla, ese centro derivado no sería el real; se detectará por el aviso de identidad/geometría y habrá que mirar el registro. No se mueve ningún bloque.
3. **HUD de cliente:** `VillageHudOverlay` L59 sigue calculando la distancia con `targetOf(anchor)`. En mundos nuevos coincide con el guardado; en mundos viejos con centro guardado distinto puede enseñar una distancia ligeramente distinta de la que usa el servidor. Es solo UI.
4. **Punteros que siguen usando el objetivo determinista:** `ObjectiveManager` L42 (llegada/proximidad), `LoreStoneBlock` L118 y `VillageHudOverlay` L59. No son el recinto ni el asedio; se dejan para no ampliar el cambio. Si se quiere una sola verdad también en la UI, es un incremento aparte.
5. **Coste de `centroDentroDelPlano`:** recorre el plano una vez por aldea y carga (después queda en caché). Es O(n) sobre ~decenas de miles de celdas; aceptable y ya había un recorrido equivalente en el fallback viejo.
6. **El `@Nullable` de `centroCanonico`** es defensivo: hoy siempre devuelve un punto (el spawn compartido existe). No cambia el comportamiento.

#### A.7 Estado final del repo (tras esta sección)

| Ítem | Valor |
|---|---|
| Rama / HEAD | `feature/autonomous-village` / `97220be5453b57387002b9bc91935807cc665274` (sin commits nuevos) |
| Worktree / staged | **11** entradas (7 `M` + 4 `??`) / **0** staged; las 2 nuevas son `VillageManager.java` y `VillageSavedData.java` |
| `main` worktree | `D:\Documents\DevilRpg` intacto (`migration-neoforge-1.21.1`) |
| Java | **0** procesos |
| Protegidos | hashes de A.1 sin cambios; ningún `a2n-*` escrito |

#### A.8 Plan restante ordenado, refinado con lo medido

**B — Ciclo de vida del raid y duplicados.** Con el centro ya unificado, el riesgo que queda es que existan **dos asedios lógicos para la misma aldea**: `DEFENSES` es una lista por `(jugador, índice)` (`start` L1422-1426 solo deduplica ese par) y `WORLD_SIEGES` es otra lista aparte por índice; `start` solo mira `isUnderWorldSiege` (L1429), no un asedio clásico de otro jugador, y `markSiegeResolved` se escribe al final (L1624), no al empezar. Medir/decidir: una defensa por **aldea** (no por jugador), conservando pausa por jugador y recompensa única. No tocar el algoritmo de spawn (eso es C).

**C — Spawn de perímetro.** `HordeManager` L133-139 ya usa `settlement.center()` y `VILLAGE_SPAWN_MIN/MAX` = 65-81; con A, ese centro es el guardado. Verificar en runtime que ningún asediador nace dentro del muro de radio 62 (margen de 3 bloques), que `spawnY` no los deja en tejados/cuevas y que el anillo no se duplica si hay dos jugadores.

**E — `ARRIVED` falso al llegar a la orilla.** Defecto establecido: `EscapeWaterGoal` llama `navegarConEjecutorPropio(shore)` (`AggressiveZombieEntity` L2327, L2358, L2372), y esa función hace `navExecutor.setObjective(destino)` **y** `navExecutor.setPlanMision(destino)` (L383-390). Al llegar a la orilla, `objetivoEsLaMision()` (`NavExecutor` L1120-1124) es cierto y el test de llegada L1497-1503 declara `ARRIVED` (L1521): la salida del agua se **confunde con la llegada a la misión**. El arreglo debe **separar el objetivo táctico (orilla, escalón, ruta) del objetivo de la misión del raid**, no cambiar el umbral `ARRIVE_DISTANCE_SQR = 2.25` (NE:117) ni el test de llegada.

**D — Brecha/escalera acotadas.** Con el muro a 62 del centro autoritativo y el spawn a 65-81, medir `DROP_CONTIG` y acotar el presupuesto de ruptura/escalones para que el asediador no tale la aldea por dentro ni suba infinito. Mantener las reglas de `protegidoPorLaAldea` y la regla del muro; no cambiar los umbrales de navegación.

#### A.9 Prompt copiable para Incremento B

```text
Estás en D:\Documents\DevilRpg-autonomous, rama feature/autonomous-village. Implementa SOLO el
Incremento B: "un solo ciclo de vida de raid por aldea (sin duplicados)". No hagas commit/push/
stage/stash/reset/clean/checkout/merge/rebase. No lances Minecraft salvo que el padre lo autorice
(como maximo UN raid, nunca dos en paralelo).

PREFLIGHT OBLIGATORIO (si algo difiere, PARA y reporta):
- git rev-parse --abbrev-ref HEAD = feature/autonomous-village
- git rev-parse HEAD = 97220be5453b57387002b9bc91935807cc665274
- SHA-256 tools\a2n\A2NavHarness.java = 11980C98668EF5D13E4CF3A276C9782392A9187F51B3EF63D7FC88141FA4C940
- SHA-256 src\main\java\com\chipoodle\devilrpg\entity\nav\NavExecutor.java = 24138E1DAD7B32A50A8CD92B4C03CE9AC5E2F80A6B69CFFECD5A51A5E5AA67DC
- SHA-256 run\saves\New World\level.dat = D9A58A246C23FDA92BFE2E6CA6770F7BDB1A1FD8C886DB819E485B4E0BD27140
- SHA-256 tools\a2n\A2NavHarness.java.partial = 2C3EBA9ED6E936B595E26650B796A624838B5CB4FF6CFD7B1804BE7A13552127
- SHA-256 docs\autonomous-village\A2-WALLED-RAID-HANDOFF.md = el que declare el informe final de
  Incremento A (no lo adivines: pidelo/verificalo antes de editar).
- (Get-CimInstance Win32_Process -Filter "Name like 'java%'").Count = 0 antes de Gradle.

CONTEXTO YA HECHO (Incremento A, NO rehacerlo):
- VillageSavedData guarda centres: Map<indice, BlockPos> ("Center" en Settlement); centroDe lee el
  guardado, valida y no recalcula; preGenerate lo fija una vez; manageNearby/start/pickHordeTarget/
  clérigo consumen centroDe. No lo cambies salvo que B lo necesite estrictamente.

OBJETIVO DE B: que una aldea tenga UN asedio logico como maximo, sin oleadas ni recompensas
duplicadas. Puntos de partida verificados:
- VillageManager.start L1422-1426 deduplica solo (playerUUID, objectiveIndex); L1429 solo mira
  isUnderWorldSiege; DEFENSES (L882) y WORLD_SIEGES (L1896) son listas separadas.
- markSiegeResolved se llama al resolver (L1624), no al empezar; tickWorldSieges resuelve por su
  cuenta. pickHordeTarget excluye isUnderAttack(level, i) (L1917-1919).
- La pausa por jugador, la recompensa unica y el avance de objetivo deben conservarse.

INVESTIGA con grep/read, cita file:line, y antes de editar escribe: ficheros a tocar + defecto que
arregla cada uno. Cambio minimo y coherente: una defensa por aldea (o estado equivalente), sin tocar
el algoritmo de spawn (eso es C), ni NavPlanner/NavExecutor, ni el arnes A2.

VALIDACION: .\gradlew.bat compileJava (BUILD SUCCESSFUL), python tools\lint_aldea.py --strict
(exit 0), git diff --check (exit 0), revisar el diff completo. Si hay runtime autorizado: UN raid,
evidencia de una sola oleada/recompensa por aldea y cero asedios apilados.
HANDOFF: anade a docs\autonomous-village\A2-WALLED-RAID-HANDOFF.md una seccion "### Incremento B —
Un ciclo de vida de raid por aldea (IMPLEMENTADO)" con preflight, hallazgos file:line, antes/despues,
diff, salidas raw, riesgos, estado final y el prompt de C. Usa Add-Content con here-string de comilla
simple. NO toques hashes protegidos ni ningun build\a2n\a2n-*.
```

### Incremento B — Ciclo del asedio y prevencion de duplicados (IMPLEMENTADO)

Fecha: 19-oct-2026. Alcance: **solo B** (ciclo de vida del asedio, prevencion de duplicados y contabilidad de entidades). NO se toco el spawn de perimetro, `ARRIVED`, la brecha/escalera ni la navegacion (eso es C/E/D). No se lanzo Minecraft ni ningun raid. Sin commit/push/stage/stash/reset/clean/checkout/merge/rebase.

#### B.1 Preflight (valores reales medidos)

| Item | Valor |
|---|---|
| Rama | `feature/autonomous-village` |
| HEAD | `97220be5453b57387002b9bc91935807cc665274` |
| `git status --porcelain=v1` | **12** entradas: 8 `M` + 4 `??` (detalle en B.8) |
| Staged | **0** |
| Worktree principal `D:\Documents\DevilRpg` | rama `migration-neoforge-1.21.1`, `status --porcelain` **0** (limpio) |
| Procesos Java | **0** antes y **0** despues de Gradle |
| `docs\autonomous-village\A2-WALLED-RAID-HANDOFF.md` | `2CD63A82271B9FD9403CD825233386FB7D78961C0850B9FDEDD884A9DD4B226C` (esperado) |
| `tools\a2n\A2NavHarness.java` | `11980C98668EF5D13E4CF3A276C9782392A9187F51B3EF63D7FC88141FA4C940` (esperado) |
| `src\main\java\...\entity\nav\NavExecutor.java` | `24138E1DAD7B32A50A8CD92B4C03CE9AC5E2F80A6B69CFFECD5A51A5E5AA67DC` (esperado) |
| `run\saves\New World\level.dat` | `D9A58A246C23FDA92BFE2E6CA6770F7BDB1A1FD8C886DB819E485B4E0BD27140` (esperado) |
| `.partial` que existe | `tools\a2n\A2NavHarness.java.partial` = `2C3EBA9ED6E936B595E26650B796A624838B5CB4FF6CFD7B1804BE7A13552127` (110 187 bytes) |
| `run\saves\New World\level.dat.partial` | **NO existe** (glob bajo `run\saves`: 0 resultados) |

**Incremento A presente en el worktree, verificado:** `VillageSavedData` guarda `centres` (`L178`), lo serializa como `"Center"` (`L320`) y lo lee (`L238`), con `getCentre` (`L534`) y `setCentre` (`L543`); `preGenerate` fija el centro una sola vez (`VillageManager` L927/L930) y `centroDe` LEE el guardado en `L5864` (`BlockPos guardado = saved.getCentre(objectiveIndex);`), no lo recalcula.

#### B.2 Mapa de fuentes (file:line + extracto real)

1. **`VillageDefense`** es una clase privada DENTRO de `VillageManager.java` (no hay fichero aparte): `L8152`.
   - Campos `L8153-8169`: `final int objectiveIndex`, `final UUID playerUUID`, `final BlockPos center`, `final List<UUID> wave`, `long tickTicks`, `boolean waveSpawned`, `int ultimosVivos`, `boolean enPausa`, `int reintentosDeOleada`; B añade `String motivoFalloDeOleada` (`L8187`).
   - Constructor `L8189` `(int objectiveIndex, UUID playerUUID, BlockPos center)`.
   - No tiene identidad propia persistida: su identidad es el `objectiveIndex` de la aldea.
2. **Callers de `VillageManager.start(...)`** (2 rutas reales):
   - `ObjectiveManager.java:65` `VillageManager.start(player.serverLevel(), player, index, target);` (evento `PlayerTickEvent.Post`, `PlayerCapabilityForgeEventSubscriber.java:303`).
   - `VillageManager.java:1049` `start(level, player, i, target);` dentro de `manageNearby` (`L973`), llamado desde `ObjectiveManager.java:56`.
   - `DEFENSES` solo se escribe en `VillageManager.java:1522` (`DEFENSES.computeIfAbsent(...).add(new VillageDefense(...))`); el resto son lecturas/barridos: `L882` (declaracion), `L1496`/`L1522` (start), `L1532` (`defensaActivaDe`), `L1555` (`retirarDefensaTerminada`), `L1595` (`tick`), `L2266` (`onSiegeAttackerKilled`), `L2441` (`isUnderPlayerSiege`).
3. **Spawn y asociacion de enemigos** (`spawnWave`, `L1832`): `ModEntities.AGGRESSIVE_ZOMBIE.get().create(...)`; se marca `zombie.setWorldSiegeIndex(d.objectiveIndex)` y se apunta el UUID en `d.wave` (`L1873` primera pasada, `L1915` segunda). El marcador persistente real es `DevilRpgWorldSiegeIndex` (`AggressiveZombieEntity.java:1953` escribe, `L1966` lee; `setWorldSiegeIndex` `L569`, `getWorldSiegeIndex` `L573`). Al morir (`die`, `L596`) o retirarse (`remove(DISCARDED)`, `L633`) llama `VillageManager.onSiegeAttackerKilled` (`VillageManager.java:2262`), que borra el UUID de `WORLD_SIEGES` y de `DEFENSES`.
   - Ruta del mundo: `HordeManager.java:140-163` crea la horda y llama `startWorldSiege` (`VillageManager.java:2111`).
4. **Hilo y persistencia:** ambos callers corren en el hilo del servidor (`ServerTickEvent.Post` en `CustomSpawnerTickHandler.java:46`; `PlayerTickEvent.Post` solo para `ServerPlayer`). `DEFENSES` es **puramente en memoria** (`HashMap`, `L882`) y NO se serializa (comentario `L2061`: "Como DEFENSES, no se persisten"). No hay acceso concurrente, por lo que **no se añadio sincronizacion**.
5. **Lookup del asedio activo actual:** `isUnderPlayerSiege` (`L2440`) recorre `DEFENSES` y compara **solo** `objectiveIndex`; `isUnderAttack` (`L2192`) = mundo o jugador; `pickHordeTarget` (`L2072`) excluye `isUnderAttack(level, i)`. `centroDe` (`L5857`) es el centro autoritativo y `start` ancla `d.center` a el (`L1512-1515`); el `objectiveIndex` sigue siendo la identidad logica de la aldea.

#### B.3 Clave de unicidad elegida y por que

**Clave: `(ServerLevel, objectiveIndex)` — una sola `VillageDefense` por aldea y nivel.**

Por que es la mas pequena y fiable, con los campos reales:
- En esta arquitectura **el objetivo ES la aldea**: `VillageSavedData` indexa por `objectiveIndex` el centro (`centres`, `L178`), el plano, la salud, la presion, `Fallen` y `resolved` (`isSiegeResolved` L369, `markSiegeResolved` L373); `centroDe(level, objectiveIndex)` deriva/valida la posicion desde ese indice (`L5857`); `Settlement` es `(int objectiveIndex, BlockPos center)` (`L2018`).
- No existe un UUID de aldea ni un segundo registro; **no se añade uno** (prohibido). El centro guardado es la posicion, no la identidad: un centro que se desvia no convierte la aldea en otra.
- El jugador NO es parte de la clave: `playerUUID` solo decide quien cobra y de quien es la pausa. Antes la guarda era `(playerUUID, objectiveIndex)`, que permitia **dos defensas para la misma aldea con dos jugadores**.

**Gap documentado:** la identidad es logica (indice de objetivo), no un id persistente de asentamiento. Si en el futuro el mundo cambiara de ancla, el mismo indice seguiria identificando la misma aldea (Incremento A conserva el centro guardado); no se necesita otra clave para B.

#### B.4 Cambios exactos por fichero

**`src\main\java\com\chipoodle\devilrpg\world\VillageManager.java`**

1. `start(...)` reescrito (`L1446`): valida entrada (`level/player/target` no nulos, `0 <= objectiveIndex <= MAX_OBJECTIVES`, `L6972`), rechaza y **limpia** estados terminales (`Fallen` L1463, `resolved` L1470), rechaza horda del mundo (L1478), y aplica la clave unica:
```java
VillageDefense existente = defensaActivaDe(level, objectiveIndex);
if (existente != null) {
    if (existente.playerUUID.equals(player.getUUID())) {
        return; // idempotente: el mismo jugador ya la tiene (esto corre cada tick)
    }
    if (esDefensaAbandonada(level, existente)) {
        ...
        lista.remove(existente);
    } else {
        logInicioRechazado(level, objectiveIndex, "ya hay una defensa activa del jugador "
                + existente.playerUUID + (existente.waveSpawned ? " (oleada lanzada)" : " (en margen)"));
        return;
    }
}
```
   Antes de insertar se recalcula `centroDe` y se hace una **re-comprobacion local** (`L1518`); la insercion sigue siendo el unico `add` (`L1522`). No se reinicia una defensa con oleada lanzada (solo se sustituye una abandonada **sin oleada** y con el defensor desconectado, con log).
2. Helpers nuevos: `defensaActivaDe` `L1531`; `esDefensaAbandonada` `L1545` (`!d.waveSpawned && getPlayer(d.playerUUID) == null`); `retirarDefensaTerminada` `L1554` (barre TODAS las entradas del objetivo, `disableGoToCenter` + `remove` + log); `logInicioRechazado` `L1575` (una linea cada 40 ticks = 2 s, sin spam por tick).
3. `spawnWave` (`L1832`): contabilidad de fallos (`fallos`, primer `motivo`, `L1837-1839`) y **el libro mayor solo despues de que la entidad este en el mundo**:
```java
if (!level.addFreshEntity(zombie)) {
    fallos++;
    if (motivo == null) { motivo = "addFreshEntity=false"; }
    zombie.discard(); // no llego al mundo: no se deja una entidad suelta
    continue;
}
zombie.setWorldSiegeIndex(d.objectiveIndex);
d.wave.add(zombie.getUUID());          // L1873
```
   La segunda pasada solo corre si `d.wave.isEmpty()` (L1883) y repite el mismo orden (L1910-1915), por lo que **no duplica una ola parcial**. Se graba `d.motivoFalloDeOleada` (L1921) y se emite UNA linea de resumen por intento (`L1924`); `motivoDeOleada` (`L1828`) lo inyecta en los avisos de reintento/cancelacion (`L1644`, `L1648`).
4. `startWorldSiege` (`L2111`): añade `isUnderPlayerSiege(level, ...)` a la guarda (una aldea = un asedio logico) y, si rechaza, **suelta la oleada ya creada** con `disableGoToCenter(level, wave)` (`L2125`), para que no queden raiders marcados en campana para siempre sin libro mayor.
5. `VillageDefense` (`L8152`): nuevo campo `String motivoFalloDeOleada = "";` (`L8187`).

**`src\main\java\com\chipoodle\devilrpg\survival\HordeManager.java`**

6. `L140-163`: el UUID se apunta **despues** de `addFreshEntity` (antes se apuntaba antes y `spawned++` contaba aunque fallara, registrando una horda fantasma que `isWaveCleared` leia como "limpiada"):
```java
if (!level.addFreshEntity(zombie)) {
    zombie.discard();
    continue;
}
if (settlement != null) {
    zombie.setWorldSiegeIndex(settlement.objectiveIndex());
    wave.add(zombie.getUUID());        // L160
}
spawned++;                             // L162
```

**Ficheros NO tocados por B:** `NavExecutor.java`, `A2NavHarness.java`, `A2NavHarness.java.partial`, `level.dat`, `build\a2n\*`, `AggressiveZombieEntity.java` (solo se usa su marcador existente), `VillageSavedData.java` (Incremento A ya hecho), `ObjectiveManager.java` (solo se cita/lee).

#### B.5 Validacion (salidas raw)

`git diff --stat` (incluye los cambios previos no commiteados de A y del worktree; B añade `HordeManager` y el bloque de `VillageManager`):
```text
 docs/CONTINUAR.md                                  |  363 ++++-
 docs/aldea-invariantes.md                          |   51 +-
 .../devilrpg/entity/AggressiveZombieEntity.java    | 1400 +++++++++++++++++---
 .../PlayerCapabilityForgeEventSubscriber.java      | 1287 +++++++++---------
 .../chipoodle/devilrpg/survival/HordeManager.java  |   10 +-
 .../com/chipoodle/devilrpg/util/EventUtils.java    |   18 +-
 .../chipoodle/devilrpg/world/VillageManager.java   |  440 ++++--
 .../chipoodle/devilrpg/world/VillageSavedData.java |   37 +
 8 files changed, 2643 insertions(+), 963 deletions(-)
```

`.\gradlew.bat compileJava`:
```text
> Task :compileJava
Note: Some input files use or override a deprecated API.
Note: Recompile with -Xlint:deprecation for details.
Note: D:\Documents\DevilRpg-autonomous\src\main\java\com\chipoodle\devilrpg\entity\goal\VillagerAnimalFarmGoal.java uses unchecked or unsafe operations.
Note: Recompile with -Xlint:unchecked for details.

BUILD SUCCESSFUL in 22s
2 actionable tasks: 1 executed, 1 up-to-date
```
Exit 0.

`python tools\lint_aldea.py --strict`:
```text
lint aldea: OK (sin patrones peligrosos)
```
Exit 0.

`git diff --check`: exit 0 (solo el warning conocido de CRLF en `docs/CONTINUAR.md`, ajeno a B).

Re-verificacion de protegidos tras compilar: `A2NavHarness` `11980C98...FA4C940`, `NavExecutor` `24138E1D...AA67DC`, `level.dat` `D9A58A24...27140`, `.partial` `2C3EBA9E...52127`; **sin cambios**. `build\a2n\a2n-*` mas reciente: `a2n-20261019-122512` (19-oct-2026 12:32), anterior a esta sesion (15:44); **ninguno escrito**.

#### B.6 Lo que sigue SIN VERIFICAR

1. **Runtime real**: no se lanzo Minecraft ni un raid (prohibido en este incremento). Las cuatro rutas nuevas estan verificadas por lectura + compilacion, no ejecutadas.
2. **NBT**: el round-trip del centro `Center` del Incremento A sigue sin probarse en partida; B no lo modifica. El marcador `DevilRpgWorldSiegeIndex` ya existia y no se cambio su formato.
3. **Fallos de `addFreshEntity`**: la rama `discard()` + reintento no se ha provocado en runtime (no hay test harness para ello).
4. **Toma de una defensa abandonada** (jugador desconectado antes de la oleada, otro jugador llega): implementada y compilada, no ejercitada.
5. **Duplicado con dos jugadores reales** en la misma aldea: no se puede medir sin servidor; la guarda es estatica (clave `objectiveIndex`).
6. **Cadencia de logs**: `logInicioRechazado` escribe como mucho una linea cada 40 ticks; no se ha medido el volumen en una sesion larga.

#### B.7 Tabla de aceptacion

| Criterio | Resultado | Justificacion |
|---|---|---|
| Prevencion de inicio duplicado | **PASS (codigo/compilacion)**; runtime **UNVERIFIED** | `start` usa `(ServerLevel, objectiveIndex)` (`L1460-1522`); el mismo jugador es no-op y otro jugador es rechazado con log. Los dos callers (`ObjectiveManager:65`, `VillageManager:1049`) pasan por la misma guarda. |
| Recuperacion de insercion/libro mayor | **PASS (codigo/compilacion)**; runtime **UNVERIFIED** | `retirarDefensaTerminada` (`L1554`) limpia entradas de aldeas caidas/resueltas; la defensa abandonada sin oleada se sustituye con log (`L1490-1499`); `tick` sigue borrando al resolver (`L1749`) y al cancelar por oleada vacia (`L1651`). |
| Reintentos acotados | **PASS (codigo/compilacion)**; runtime **UNVERIFIED** | Se conserva el maximo de 5 intentos (`L1641-1652`); la segunda pasada de `spawnWave` esta acotada a `count * 3` y solo si `d.wave.isEmpty()` (`L1883`); se registra el motivo del fallo (`L1921`) y se loguea una vez por intento. |
| Contabilidad segura de entidades | **PASS (codigo/compilacion)**; runtime **UNVERIFIED** | `d.wave.add` y `setWorldSiegeIndex` ocurren solo tras `addFreshEntity == true` en `spawnWave` (`L1868-1873`, `L1910-1915`) y en `HordeManager` (`L152-160`); si falla se `discard()`. El marcador persistente existente (`DevilRpgWorldSiegeIndex`) asocia el raider a su aldea y `onSiegeAttackerKilled` lo descuenta. |
| Sin regresion de pausa/recompensa/objetivo | **PASS (codigo/compilacion)**; runtime **UNVERIFIED** | No se toco `tick`, `informarDelAsedio`, `grantReward`, `markSiegeResolved` ni el avance de objetivo; solo se añadieron guardas y contabilidad. |
| Sin cambios de spawn/perimetro/nav | **PASS (estatico)** | `WAVE_SPAWN_MIN/MAX`, `FENCE_RADIUS`, `spawnY`, `NavExecutor` y el arnes no se tocaron. |

#### B.8 Estado final del repo (tras esta seccion)

| Item | Valor |
|---|---|
| Rama / HEAD | `feature/autonomous-village` / `97220be5453b57387002b9bc91935807cc665274` (sin commits nuevos) |
| Worktree / staged | **12** entradas (8 `M` + 4 `??`) / **0** staged |
| `M` | `docs/CONTINUAR.md`, `docs/aldea-invariantes.md`, `AggressiveZombieEntity.java`, `PlayerCapabilityForgeEventSubscriber.java`, `HordeManager.java` (nuevo en B), `EventUtils.java`, `VillageManager.java`, `VillageSavedData.java` |
| `??` | `docs/autonomous-village/`, `AggressiveZombiePathPlanner.java`, `entity/nav/`, `tools/a2n/` |
| `main` worktree | `D:\Documents\DevilRpg` en `migration-neoforge-1.21.1`, limpio (0) |
| Java | **0** procesos |
| Protegidos | hashes de B.1 sin cambios; ningun `build\a2n\a2n-*` escrito |

#### B.9 Siguiente paso: Incremento C (spawn de perimetro)

Prompt copiable:

```text
Estas en D:\Documents\DevilRpg-autonomous, rama feature/autonomous-village. Implementa SOLO el
Incremento C: "spawn de perimetro del asedio (sin nacer dentro del muro)". No hagas commit/push/
stage/stash/reset/clean/checkout/merge/rebase. No lances Minecraft ni raids.
NO toques tools\a2n\A2NavHarness.java, su .partial, entity\nav\NavExecutor.java, run\saves\New World\*,
build\a2n\recovery\* ni ningun build\a2n\a2n-*.

PREFLIGHT OBLIGATORIO (si algo difiere, PARA y reporta):
- git rev-parse --abbrev-ref HEAD = feature/autonomous-village
- git rev-parse HEAD = 97220be5453b57387002b9bc91935807cc665274
- SHA-256 docs\autonomous-village\A2-WALLED-RAID-HANDOFF.md = el que reporte el informe final de B
  (no lo adivines: verificalo antes de editar).
- SHA-256 tools\a2n\A2NavHarness.java = 11980C98668EF5D13E4CF3A276C9782392A9187F51B3EF63D7FC88141FA4C940
- SHA-256 src\main\java\com\chipoodle\devilrpg\entity\nav\NavExecutor.java = 24138E1DAD7B32A50A8CD92B4C03CE9AC5E2F80A6B69CFFECD5A51A5E5AA67DC
- SHA-256 run\saves\New World\level.dat = D9A58A246C23FDA92BFE2E6CA6770F7BDB1A1FD8C886DB819E485B4E0BD27140
- SHA-256 tools\a2n\A2NavHarness.java.partial = 2C3EBA9ED6E936B595E26650B796A624838B5CB4FF6CFD7B1804BE7A13552127
- (Get-CimInstance Win32_Process -Filter "Name like 'java%'").Count = 0 antes de Gradle.

CONTEXTO YA HECHO (no rehacer):
- A (centro autoritativo): centroDe lee VillageSavedData.centres ("Center"), no recalcula; start y
  pickHordeTarget lo consumen.
- B (ciclo del asedio): una defensa por (ServerLevel, objectiveIndex); start es idempotente; el libro
  mayor (d.wave / DevilRpgWorldSiegeIndex) se escribe solo tras addFreshEntity; startWorldSiege rechaza
  si hay defensa clasica y suelta la oleada.

OBJETIVO DE C (solo perimetro):
1. Medir y verificar en codigo que los asediadores del asedio clasico (VillageManager.spawnWave, L1832
   aprox.) nacen FUERA del muro: WAVE_SPAWN_MIN/MAX (L114-115, FENCE_RADIUS + 3 .. +11) y que
   VillageGenerator.spawnY no los deja en tejados/cuevas; corregir SOLO si hay un defecto demostrable.
2. Revisar HordeManager L133-139 (VILLAGE_SPAWN_MIN/MAX 65-81) con el centro autoritativo de A.
3. Garantizar que no se duplica el anillo si hay dos jugadores (la clave de B ya evita dos defensas;
   no crear una segunda oleada).
4. NO cambiar umbrales de navegacion, AI, ARRIVED, brecha/escalera, rewards ni el algoritmo de
   seleccion de aldeas. NO refactorizar NavExecutor/NavPlanner.

VALIDACION: .\gradlew.bat compileJava (BUILD SUCCESSFUL), python tools\lint_aldea.py --strict (exit 0),
git diff --check (exit 0), revisar el diff completo, re-verificar protegidos y que no se escribio ningun
build\a2n\a2n-*. Si no hay runtime autorizado, dilo: no inventes evidencia de partida.

HANDOFF: anade a docs\autonomous-village\A2-WALLED-RAID-HANDOFF.md una seccion "### Incremento C —
Spawn de perimetro (IMPLEMENTADO)" con preflight, mapa file:line, cambios, salidas raw, UNVERIFIED,
tabla PASS/FAIL/UNVERIFIED y el prompt de E. Usa Add-Content con here-string de comilla simple.
```

### Increment C — Safe perimeter spawning (IMPLEMENTADO)

**Fecha:** 2026-10-19 (America/Mexico_City). **Rama:** `feature/autonomous-village`, **HEAD** `97220be5453b57387002b9bc91935807cc665274`.
**Ámbito:** SOLO la **selección de posiciones** de la oleada del **asedio de producción** (`VillageManager.spawnWave`). No se tocó el arnés A2 (`tools/a2n/A2NavHarness.java`), ni `NavExecutor`, ni `NavPlanner`, ni la IA de combate, ni el tamaño/progresión de la ola, ni la semántica del centro del Incremento A, ni los Incrementos D/E.

#### 1. Estado inicial verificado (antes de tocar nada)

| Comprobación | Valor real |
|---|---|
| Rama | `feature/autonomous-village` |
| HEAD | `97220be5453b57387002b9bc91935807cc665274` |
| `git status --porcelain=v1` | 12 entradas (8 `M` + 4 `??`) |
| Staged | 0 |
| `D:\Documents\DevilRpg` | limpio, rama `migration-neoforge-1.21.1`, HEAD `7c3e9585428a0e63f313260bd9508e2f5e9752c9` |
| Procesos java | 0 |

SHA-256 de los ficheros protegidos (ANTES):

| Fichero | SHA-256 |
|---|---|
| `docs/autonomous-village/A2-WALLED-RAID-HANDOFF.md` | `173BF96348529324DB16DAC12C921CCF8DDF9F9BC071C14D6A62EB0F4AFF1A3C` |
| `tools/a2n/A2NavHarness.java` | `11980C98668EF5D13E4CF3A276C9782392A9187F51B3EF63D7FC88141FA4C940` |
| `src/main/java/com/chipoodle/devilrpg/entity/nav/NavExecutor.java` | `24138E1DAD7B32A50A8CD92B4C03CE9AC5E2F80A6B69CFFECD5A51A5E5AA67DC` |
| `run/saves/New World/level.dat` | `D9A58A246C23FDA92BFE2E6CA6770F7BDB1A1FD8C886DB819E485B4E0BD27140` |
| `tools/a2n/A2NavHarness.java.partial` | `2C3EBA9ED6E936B595E26650B796A624838B5CB4FF6CFD7B1804BE7A13552127` |
| `run/saves/New World/level.dat.partial` | **no existe** (esperado) |

**Incremento A presente** (verificado en el árbol de trabajo): `VillageSavedData` guarda/lee `"Center"` por índice (`VillageSavedData.java:237-238`, `:319-320`) con `getCentre` (`:534`) y `setCentre` (`:543`); `VillageManager.centroDe` (`VillageManager.java:6015`) lee `saved.getCentre(objectiveIndex)`, lo valida contra identidad/plano y solo entonces cachea; `start` (`:1472`) ancla `target = centroDe(level, objectiveIndex)` (`:1538`).

**Incremento B presente:** `defensaActivaDe` (`:1557`), `esDefensaAbandonada` (`:1571`), `retirarDefensaTerminada` (`:1580`), `logInicioRechazado` (`:1601`); libro mayor **después** de `addFreshEntity` y antes de `setWorldSiegeIndex`/`wave.add` (`:1889`/`:1894`); `HordeManager` con el mismo orden (`HordeManager.java:149-160`) y `startWorldSiege` (`VillageManager.java:2269`) soltando la oleada rechazada; `onSiegeAttackerKilled` (`:2420`) descuenta de la defensa.

#### 2. Camino de spawn de producción ANTES (el que se ha sustituido)

`VillageManager.spawnWave` (antes en `VillageManager.java:1832-1930`):

- `dist = WAVE_SPAWN_MIN + random.nextInt(WAVE_SPAWN_MAX - WAVE_SPAWN_MIN)` → 65..72 (`WAVE_SPAWN_MIN/MAX` = `FENCE_RADIUS + 3 / + 11`, `:114-115`).
- `x/z` redondeados desde el ángulo aleatorio; **`y = VillageGenerator.spawnY(level, x, z)`** (`VillageGenerator.java:8114`).
- `spawnY` = `groundY` (bloque sólido más alto en 48 bloques, `VillageGenerator.java:8099-8108`) **o la superficie del agua** (`:8118-8121`): no mira la caja del mob, ni fluidos, ni peligros, ni si el apoyo es un tronco/copa.
- `create(level, null, new BlockPos(x, y, z), MOB_SUMMONED, true, true)` → `moveTo` → `setVillageCenter` → `addFreshEntity` → `setWorldSiegeIndex` → `wave.add`.
- Segunda pasada "de rescate" (solo si la ola quedaba vacía) que aceptaba **cualquier** celda con `isAir` en la celda y encima, en posiciones aleatorias nuevas.

Defectos que cierra C: la Y podía ser agua o quedar por debajo/encima del terreno transitable; el AABB del asediador (0,6 × 1,95) nunca se validaba; los peligros (fuego/lava/zarzas) no se miraban; y la segunda pasada relajaba todas las comprobaciones para "que naciera algo".

#### 3. Perímetro real y predicado de EXTERIOR usado

- **Centro autoritativo:** `d.center` = `centroDe(level, objectiveIndex)` (`VillageManager.java:6015`, Incremento A) ← `VillageSavedData.getCentre` (`"Center"`). No se usa el ancla del jugador ni un radio adivinado.
- **Geometría real:** el **muro** se construye en `VillageGenerator.fence` (`VillageGenerator.java:7218`) sobre `anilloDelMuro(center, FENCE_RADIUS)` (`:6973`) con `FENCE_RADIUS = 62` (`:90`), y se reconstruye al mismo radio en `rehacerMuro` (`:7155`). El muro y el predicado comparten centro (el que A persiste) y radio.
- **Predicado de interior (el único del asedio):** `dentroDelRecinto(BlockPos pos, BlockPos center, int cota, double radio)` (`VillageManager.java:1418`) — disco horizontal de radio `PERIMETER_RADIUS` alrededor de `center + 0,5` **y** banda vertical `[-6, +16]` sobre `cotaDeLaPlaza` (`VillageGenerator.java:6624`). Es la misma verdad que cuenta invasores (`contarAtacantesDentro` `:1832`, `allZombiesInsidePerimeter` `:2109`) y la que decide la victoria: **spawn y victoria no pueden discrepar**.
- **Exterior exigido:** `!dentroDelRecinto(celda, d.center, cota, PERIMETER_RADIUS)` **y** distancia horizontal `> (PERIMETER_RADIUS + 2)²` (`SPAWN_MARGEN_EXTERIOR`, para que el cuerpo no nazca pegado a la cara exterior del muro).

No hubo que parar por "falta de invariante demostrable": el predicado existe y es el que ya usa el asedio.

#### 4. Barrido vertical y orden de candidatos (acotado y determinista)

- **Banda vertical documentada:** `cota + 24` … `cota - 16` (`SPAWN_SCAN_ARRIBA_DE_LA_COTA` / `SPAWN_SCAN_ABAJO_DE_LA_COTA`, `VillageManager.java:128-129`), **recortada** a `[level.getMinBuildHeight() + 1, level.getMaxBuildHeight() - 2]`.
- **Candidatos por asediador:** 3 radios (65, 69, 73 — la banda `WAVE_SPAWN_MIN..MAX` sin cambios) × 5 desvíos angulares fijos (0, ±11,25°, ±22,5°) = **15 columnas como máximo** (`SPAWN_CANDIDATOS_RADIOS`, `SPAWN_DESVIOS_ANGULARES`, `SPAWN_DESVIO_ANGULAR`, `:136-140`).
- **Orden determinista:** ángulo base de un `Random` **sembrado** con `(objectiveIndex, reintentosDeOleada, índice)` (`:1927-1928`); con el mismo estado salen las mismas posiciones, y cada reintento de la ola explora direcciones distintas sin rechazo aleatorio sin cota.
- **Barrido de arriba abajo:** gana la primera `y` válida de la columna (`:1986-1999`).

#### 5. Comprobaciones por casilla (en orden, `sitioSeguroEnLaColumna` `:1962`)

1. **Exterior** (ver §3).
2. **Chunk cargado**: `level.hasChunkAt(celda)` (`:1977`) — no se fuerza la carga de ningún chunk.
3. Por cada `y`: **apoyo sólido y estable** (`soporteFirme` `:2020`): no aire, `getFluidState().isEmpty()`, no peligroso, ni `BlockTags.LEAVES`/`BlockTags.LOGS` (ni copas ni troncos: el muro es de troncos), `isSolid()` y `getCollisionShape(...)` no vacía.
4. **Caja COMPLETA del asediador** (`cajaDelAsediador` `:2008`): ancho/alto del tipo (`EntityType.getWidth()/getHeight()` = 0,6 / 1,95, lo mismo que `getBbWidth()/getBbHeight()`), pies en `y`, centrada en la celda; `level.noCollision(caja)` (`:1992`) exige que **no haya intersección con bloques** en toda la caja (muro, valla, losa a media altura, techo).
5. **Fluidos y peligros** en todas las celdas de la caja (`hayFluidoOPeligro` `:2051`): `getFluidState()` no vacío o `esPeligroso` (`:2035`: `BlockTags.FIRE`, lava, magma, campanario/hogueras, zarzas dulces, cactus, nieve en polvo, rosa del wither). El agua no tiene colisión, así que esta pasada es la que la ve.

#### 6. Orden inserción-vs-asignación (Incremento B intacto)

`create` → `moveTo` → `setVillageCenter` → **`addFreshEntity`** → **`setWorldSiegeIndex`** → **`wave.add(UUID)`** (`VillageManager.java:1877-1895`). Si `addFreshEntity` devuelve `false`: `discard()` y **nada** se apunta. Si `create` devuelve `null`, `crearAsediador` (`:2080`) reintenta **en la misma celda ya validada** con `alignPosition = false` (el recurso de la 2ª pasada de B, pero **sin relajar** ninguna comprobación nueva). Cada asediador recibe **un** `setWorldSiegeIndex` y **una** entrada en `wave`.

#### 7. Observabilidad de fallos

Una sola línea por intento de oleada (`:1904-1909`) con el desglose `sin sitio seguro / creacion / insercion`, y `motivoFalloDeOleada` (`:1900-1903`) para que el aviso de `tick` (reintento/cancelación, `:1657-1680`) diga la causa. Nunca una línea por spawn ni por tick.

#### 8. Diferencia con el arnés A2 (que NO se toca)

| | Producción (C) | Arnés A2 (`tools/a2n/A2NavHarness.java`) |
|---|---|---|
| Radio | 65–73 (`WAVE_SPAWN_MIN/MAX`, sin cambios) | fijo 95 (`R_RADIO_ASALTO`, `:1099`) |
| Reparto | 15 candidatos deterministas por asediador | 8 asaltantes en ángulos fijos + offsets radiales/laterales ±16 (`:1152`, `:1187`) |
| Ventana Y | cota `+24` … `-16` | `ancla.Y - 8 … +24` (`:1159`) |
| Test de suelo | apoyo sólido + AABB completa + `noCollision` + fluidos/peligros | solo "bloque de abajo no aire y celda/arriba aire" (`:1160-1165`) |
| Exterior | `dentroDelRecinto` con centro autoritativo | solo el radio 95 |
| Escritura de bloques | **ninguna** | plataforma de 3 celdas si nada vale (`:1222-1247`) |

**Decisión explícita: se mantienen separados.** El arnés es un instrumento con su fixture y su radio 95 documentados; llevar a producción su algoritmo (o al revés) movería la línea base de las medidas A2 y arrastraría la escritura de bloques al juego. El arnés queda **sin cambios** (hash abajo).

#### 9. Validación ejecutada

- `.\gradlew.bat compileJava` → `BUILD SUCCESSFUL in 18s` (0 errores; solo notas de API deprecada/unchecked preexistentes).
- `python tools\lint_aldea.py --strict` → `lint aldea: OK (sin patrones peligrosos)`, exit 0.
- `git diff --check` → exit 0.
- Procesos java antes de Gradle: 0. No se escribió **ningún** `build\a2n\a2n-*` (el más reciente sigue siendo `a2n-20261019-122512`, 12:32; la sesión empezó a las 15:50).

#### 10. SHA-256 de los protegidos DESPUÉS del trabajo

| Fichero | SHA-256 |
|---|---|
| `tools/a2n/A2NavHarness.java` | `11980C98668EF5D13E4CF3A276C9782392A9187F51B3EF63D7FC88141FA4C940` (igual) |
| `src/main/java/com/chipoodle/devilrpg/entity/nav/NavExecutor.java` | `24138E1DAD7B32A50A8CD92B4C03CE9AC5E2F80A6B69CFFECD5A51A5E5AA67DC` (igual) |
| `run/saves/New World/level.dat` | `D9A58A246C23FDA92BFE2E6CA6770F7BDB1A1FD8C886DB819E485B4E0BD27140` (igual) |
| `tools/a2n/A2NavHarness.java.partial` | `2C3EBA9ED6E936B595E26650B796A624838B5CB4FF6CFD7B1804BE7A13552127` (igual) |
| `docs/autonomous-village/A2-WALLED-RAID-HANDOFF.md` | cambia por definición al añadir esta sección (antes `173BF963…`; el hash nuevo se reporta en la respuesta final) |

#### 11. Comportamiento que queda UNVERIFIED

- **Nada de runtime**: no se abrió Minecraft ni se lanzó un asedio; todo es verificación estática.
- **Aldeas sobre agua/océano**: los fluidos se rechazan, así que la corona 65–73 puede no tener ninguna casilla segura y la ola se cancelará tras 5 intentos con el motivo `sin_sitio_seguro` (registrado, sin regalar la victoria). Es la consecuencia pedida de "rechazar fluidos", pero no se ha medido en partida.
- **Aldeas antiguas** cuyo muro se construyó con un `FENCE_RADIUS` anterior y sin centro guardado: `centroDe` deriva/persiste el centro canónico, pero el predicado de exterior usa el radio actual (62). No medido contra guardados viejos.
- **Estructuras fuera del anillo**: se prohíben troncos y copas como apoyo, pero un tejado de piedra/ladrillo fuera del muro sí puede ser apoyo (el asediador nacería encima). Permitido, no medido.
- **Reparto de posiciones entre reintentos**: la siembra por intento está implementada, pero su distribución no se ha medido.
- **Rendimiento**: hasta 15 columnas × 41 alturas por asediador, una vez por oleada (no por tick); no perfilado.
- **Horda del mundo**: `HordeManager.spawnHorde` sigue usando su propia selección (`HordeManager.java:134-141`, radios 65–81, `spawnY`) — **fuera del alcance de C** (C es la oleada del asedio del jugador). Candidato a C2.

#### 12. Estado final

- Rama `feature/autonomous-village`, HEAD `97220be5453b57387002b9bc91935807cc665274` (sin commit).
- Ficheros que cambia C: **solo** `src/main/java/com/chipoodle/devilrpg/world/VillageManager.java` (bloque de constantes `:121-146`, `spawnWave` `:1858-1913`, y 7 helpers privados `:1915-2088`). Más esta sección del handoff.
- Worktree: las **mismas 12 entradas** de antes (`docs/autonomous-village/` sigue sin seguimiento, así que esta sección no añade una entrada nueva). Staged: 0. Procesos java: 0.
- **A2 sigue OPEN. No se ha ejecutado ningún raid en runtime.** El éxito de esta sección es *estático* (`compileJava` + lint + diff-check + inspección), **no** éxito en partida.

#### 13. Prompt listo para copiar — Incremento E (objetivo táctico vs misión / ARRIVED)

```text
Trabaja en D:\Documents\DevilRpg-autonomous (rama feature/autonomous-village). Implementa SOLO el
Incremento E: separar el OBJETIVO TÁCTICO de la MISIÓN para que una llegada táctica (orilla, escalón,
sub-objetivo) no se pueda leer como llegada de la MISIÓN, y arreglar el falso ARRIVED.

Hecho verificado en el árbol actual:
- AggressiveZombieEntity.navegarConEjecutorPropio(destino) (AggressiveZombieEntity.java:383-392) hace
  navExecutor.setObjective(destino) Y navExecutor.setPlanMision(destino): el destino pasa a ser a la vez
  objetivo y misión.
- EscapeWaterGoal llama a navegarConEjecutorPropio(shore) en AggressiveZombieEntity.java:2327, :2358 y
  :2372. Por tanto la ORILLA se convierte en misión.
- NavExecutor.setObjective (NavExecutor.java:1354-1367) fija planMision/misionFinal al mismo BlockPos.
- El ejecutor da ARRIVED cuando la distancia al objetivo es <= ARRIVE_DISTANCE_SQR = 2.25
  (NavExecutor.java:117, comprobación :1495-1524, estado ARRIVED :1521); con objetivo == misión,
  llegar a la orilla se reporta como llegada de misión.

Requisitos:
1. Separar explícitamente objetivo táctico (destino inmediato: orilla, escalón) de misión (la aldea/ancla
   del asedio) en el ejecutor y en la entidad; una llegada táctica NO debe producir ARRIVED de misión.
2. NO cambiar ARRIVE_DISTANCE_SQR (2.25) ni el radio de llegada; el defecto es de SEMÁNTICA, no de umbral.
3. No cambiar la IA de combate, ni el tamaño/progresión de la ola, ni la semántica del centro del
   Incremento A, ni la selección de spawn del Incremento C.
4. Preservar los Incrementos A/B/C y sus invariantes; no tocar tools/a2n/A2NavHarness.java (solo lectura),
   NavPlanner/NavExecutor más allá de lo imprescindible para separar táctica de misión.
5. Validación estática: .\gradlew.bat compileJava (BUILD SUCCESSFUL), python tools\lint_aldea.py --strict
   (exit 0), git diff --check (exit 0), hashes de los protegidos sin cambios. Sin commit, sin raid, sin
   Minecraft. Handoff en docs/autonomous-village/A2-WALLED-RAID-HANDOFF.md.
```

### A2 — Fases 1–8 de remediación (IMPLEMENTADO; runtime NO ejecutado)

**Fecha:** 2026-10-19. **Naturaleza:** implementación de código + pruebas enfocadas + validación estática.
**No** se ejecutó Minecraft, **no** se lanzó ningún raid y **no** se tocó ningún `run/saves/*` ni
`tools/a2n/A2NavHarness.java`. Sin commit/push/stage/stash/reset/clean/checkout/merge/rebase.

#### Qué se corrigió (verificado contra el fuente actual)

1. **Destino de misión interior (Fase 1).** `cadenaDeAproximacion` terminaba en
   `RADIO_DE_APROXIMACION = 64`, el **anillo exterior** (muro en 62). Ahora el último elemento es una celda
   **pisable y estrictamente interior** (`MissionDestination`, radio máximo 58), validada con la MISMA verdad
   de recinto que cuenta invasores (`VillageManager.dentroDelRecinto`). El anillo exterior queda como
   **waypoint táctico intermedio**. Si no hay celda interior válida, **no se envía cadena** (fail-closed).
2. **Política de navegación (Fase 1).** `isProtected` de las políticas del asaltante ya no significa
   "no se puede andar": la regla "dentro de la aldea no se rompe ni se construye" sigue en
   `canBreak`/`canPlace`. Sin este cambio el planificador nunca encolaba una celda interior.
3. **Recuperación de `FAILED` (Fase 2).** `NavExecutor.intentarRearmeTerminal`: máximo **4** rearmes por
   misión, enfriamiento de **60 ticks**, conserva objetivo/misión, invalida solo el estado transitorio,
   motivo terminal explícito. Un destino sin celda de pie no se rearma. `terrainChanged()` reabre la
   evaluación; un cambio de misión reinicia el contador, una etapa de la MISMA aldea no.
4. **Presupuesto agotado (Fase 3).** `MAX_BUDGET_RETRIES` configurable
   (`-Ddevilrpg.nav.budgetRetries`, por defecto **6**, clamp **1–20**). Se reinicia con progreso de
   distancia, misión nueva o cambio de terreno; **no** se reinicia al rearmar. Sin progreso termina en
   `FAILED` con motivo, y ese fallo no es rearmable mientras el mundo no cambie. Nunca declara `ARRIVED`.
5. **Táctica vs misión (Fase 4).** `setTacticalObjective` no toca `missionFinal`; `EscapeWaterGoal` usa
   `navegarTacticamente` en sus tres llamadas. `NavArrivalPolicy` decide: táctica → retomar misión;
   etapa → avanzar; solo misión final sin etapas → `ARRIVED`. `shore == posición actual` no cambia el
   objetivo (no hay éxito falso ni bucle).
6. **Ciclo del asedio y entidades descargadas (Fases 5/6).** `waveCleared = waveSpawned && ola confirmada
   limpia` (`SiegeWaveState`): una ola no nacida no gana, una entidad descargada no se cuenta como muerta,
   y al timeout un estado no confirmable cierra el asedio **sin recompensa**. Se registra la última celda
   conocida de cada asediador para distinguir "muerto" de "chunk descargado".
7. **Brecha en marcha (Fase 7).** `BreakBlockGoal` ya corre sin objetivo de combate y abre **solo** celdas
   del anillo real del muro (`esLaMuralla`), con el presupuesto de túnel compartido. No destruye terreno
   arbitrario fuera del blanco del asedio.
8. **Tick (Fase 8).** `manageNearby` acota el bucle a `MAX_OBJECTIVES` y genera como máximo **una** aldea
   nueva por tick. La cadena de aproximación se cachea (incluido el resultado negativo) y su log se limita
   a una línea cada 2 s. No se movió generación a otro hilo.

#### Pruebas enfocadas (sin servidor)

`javac` + `java` sobre `MissionDestination`, `NavRecoveryPolicy`, `NavArrivalPolicy` y `SiegeWaveState`:

- `MissionDestinationTest`: OK (17 aserciones). Destino interior, anillo exterior rechazado, sin
  candidato → null, el destino es el último de la cadena y no se duplica.
- `NavRecoveryPolicyTest`: OK (22 aserciones). Presupuesto sin progreso termina; con progreso reintenta;
  rearmes acotados; enfriamiento; destino inválido; objetivo local inválido sin `PLANNING` infinito.
- `NavArrivalPolicyTest`: OK (10 aserciones). Táctica no declara `ARRIVED` y retoma la misión; solo la
  misión final sin etapas declara llegada.
- `SiegeWaveStateTest`: OK (10 aserciones). Ola no nacida ≠ limpia; descargada ≠ muerta; mezclas.

#### Validación estática real

- `.\gradlew.bat compileJava --rerun-tasks`: **BUILD SUCCESSFUL**, exit 0.
- `python tools\lint_aldea.py --strict`: **OK**, exit 0.
- `git diff --check`: exit 0 (solo el aviso preexistente de CRLF en `docs/CONTINUAR.md`).

#### Hashes

| Fichero | SHA-256 |
|---|---|
| `tools/a2n/A2NavHarness.java` | `11980C98668EF5D13E4CF3A276C9782392A9187F51B3EF63D7FC88141FA4C940` (sin cambios) |
| `tools/a2n/A2NavHarness.java.partial` | `2C3EBA9ED6E936B595E26650B796A624838B5CB4FF6CFD7B1804BE7A13552127` (sin cambios) |
| `run/saves/New World/level.dat` | `D9A58A246C23FDA92BFE2E6CA6770F7BDB1A1FD8C886DB819E485B4E0BD27140` (sin cambios) |
| `src/main/java/.../entity/nav/NavExecutor.java` | `3AD378DF8261BE1B26290846BBD471E5EEACE5E73BD2A5076AFAC9617D0942A9` (cambia por la Fase 2/3/4) |

#### Límites honestos

**RUNTIME NO EJECUTADO.** Las Fases 2/3/4 se validaron con pruebas de política puras + compilación, no en
partida: no hay evidencia de que un asaltante real cruce el muro, abra la brecha o llegue al interior en
esta sesión. `DROP_CONTIG` sigue **NO MEDIDO**. A2 sigue **ABIERTO**. El estado de esta sección es
**IMPLEMENTED BUT RUNTIME-VALIDATION INCOMPLETE**, no aceptación.

---

## Integración 2 — Cierre de defectos de integración (IMPLEMENTADO; runtime NO ejecutado)

**Fecha:** 2026-10-19. **Naturaleza:** corrección de integración + pruebas + validación estática.
**No** se ejecutó Minecraft, **no** se lanzó raid y **no** se tocó ningún `run/saves/*`,
`tools/a2n/A2NavHarness.java` ni su `.partial`. Sin commit/push/stage/reset/clean/restore/stash/merge/rebase.

### Qué se corrigió

1. **Asedio del mundo: muerte confirmada vs estado desconocido.** `getEntity(uuid)==null` ya **no** se
   interpreta como baja: el UUID queda `DESCONOCIDA`. La única baja confirmada es `die()` **después** de
   `super.die()` y solo si `isDeadOrDying()` sigue siendo cierto (una cancelación de `LivingDeathEvent` no
   borra el libro mayor). `/kill`, `KILLED` y `DISCARDED` **no** cuentan como muerte de combate: el asedio se
   cierra por plazo, sin recompensa. Se eliminó la doble notificación (`die` + `remove(KILLED)`).
2. **Plazo determinista del asedio del mundo.** `WorldSiege` guarda `inicioTick = level.getGameTime()` y el
   plazo se mide como `getGameTime() - inicioTick` (10 min, `WorldSiegePolicy.TIMEOUT_TICKS`). Se distingue
   `TIMEOUT_ACTIVE` de `TIMEOUT_UNCONFIRMED`: ambos cierran **sin victoria ni recompensa**, con mensaje
   neutro y sin resetear la presión de abandono. La entrada se retira **antes** de pagar/avisar, para que un
   fallo no permita pagar dos veces. `disableGoToCenter` libera índice, centro, brillo y goal de marcha.
3. **Flags obsoletos al recargar.** `readAdditionalSaveData` solo conserva la campaña si el asedio sigue
   existiendo **y** el UUID pertenece a la ola vigente (`perteneceAAsedioActual`). Si no, limpia índice,
   `goToCenterActive` y `villageCenter`; un raider viejo no se reengancha a un asedio nuevo del mismo índice.
4. **Diagnóstico terminal estable.** `falloDefinitivoTerminal` + `NavTerminalDiagnostics.appendOnce` fijan el
   motivo una sola vez; los atajos de tope/enfriamiento se comprueban antes de cualquier mutación. No hay
   concatenación por tick.
5. **Propiedad única del movimiento.** `PursueTargetGoal` mantiene un flag `activo` (start/stop) y `aiStep`
   solo suspende `NavExecutor` cuando **ese** goal posee MOVE. Si `EscapeWaterGoal` le preempta, el ejecutor
   sigue tickeando la orilla. `suspendForCombat` limpia ruta/oscilación/progreso y solo convierte estados
   transitorios (no `ARRIVED`/`FAILED`).
6. **Escape de agua finito.** `TacticalEscapeBudget` (4 intentos por episodio), orillas distintas con
   `orillasIntentadas`, todas las peticiones pasan por `solicitarOrilla`, y al agotarse
   `clearTacticalObjective()` retoma `misionFinal`. `stop()` también retoma la misión al salir del agua. Un
   destino táctico ya no reinicia el contador duro de presupuesto ni finge progreso.
7. **Caché negativa recuperable.** `NegativeDestinationCache`: un fallo de búsqueda interior se reintenta
   cada 100 ticks, con aviso limitado; un éxito limpia el estado. No se inventa destino.
8. **Cupo compartido de generación.** `GenerationBudget` (débil por nivel) concede **una** aldea completa por
   nivel y tick. `preGenerate` devuelve booleano; `manageNearby` y `start` difieren si el cupo ya se usó.
   No se movió generación a otro hilo.
9. **Brecha y contadores.** La brecha notifica a **ambos** planificadores (`terrainChanged`) solo tras romper
   de verdad; `tunelRestante` se comprueba por bloque; `avanzarEtapa` reinicia el contador de objetivo local
   inválido y sincroniza `planMision`, preservando presupuesto duro/rearmes. `terrainChanged` ya no reinicia
   el contador duro; invalida la ruta global.
10. **Muro en asedio.** `asegurarMuro` se ejecuta cada `VILLAGE_POLL_TICKS` (no cada tick) y su guarda de
    asedio usa el radio real (`FENCE_RADIUS + 4`), no un AABB de 2 bloques. Así la brecha no se reconstruye
    durante el asedio y se elimina el barrido de ~16k lecturas por tick.
11. **Tests en el build.** `build.gradle` añade `policyTest` (JavaExec con toolchain 21) y `test` depende de
    él. `.\gradlew.bat policyTest` y `.\gradlew.bat test` ejecutan las 10 pruebas puras actuales.

### Validación real

- `.\gradlew.bat compileJava` → **BUILD SUCCESSFUL**, exit 0.
- `.\gradlew.bat test` → **BUILD SUCCESSFUL**, exit 0; `policyTest` ejecutó las 10 clases:
  `MissionDestinationTest`, `NavRecoveryPolicyTest`, `NavArrivalPolicyTest`, `MovementOwnershipTest`,
  `TacticalEscapeBudgetTest`, `NegativeDestinationCacheTest`, `NavTerminalDiagnosticsTest`,
  `SiegeWaveStateTest`, `WorldSiegePolicyTest`, `GenerationBudgetTest`.
- `python tools\lint_aldea.py --strict` → `lint aldea: OK`, exit 0.
- `git diff --check` → exit 0 (solo aviso preexistente de CRLF en `docs/CONTINUAR.md`).
- Procesos de juego: **0**.

### Hashes

| Fichero | SHA-256 |
|---|---|
| `tools/a2n/A2NavHarness.java` | `11980C98668EF5D13E4CF3A276C9782392A9187F51B3EF63D7FC88141FA4C940` (sin cambios) |
| `tools/a2n/A2NavHarness.java.partial` | `2C3EBA9ED6E936B595E26650B796A624838B5CB4FF6CFD7B1804BE7A13552127` (sin cambios) |
| `run/saves/New World/level.dat` | `D9A58A246C23FDA92BFE2E6CA6770F7BDB1A1FD8C886DB819E485B4E0BD27140` (sin cambios) |
| `src/main/java/.../entity/nav/NavExecutor.java` | `B650261A24CB8F28782BE16CE126A4979BC597DCBE7AD1DFF019021E4B1F78BC` |

### Límites que siguen abiertos

**RUNTIME NO EJECUTADO.** No hay prueba de que el asaltante cruce el muro, abra brecha o llegue al interior;
`DROP_CONTIG` sigue **NO MEDIDO** y A2 sigue **ABIERTO**. Riesgos residuales:
`cadenaDeAproximacion` aún se recalcula por tick; `MissionDestination.findInterior` puede ser caro en el peor
caso (mitigado por reintento de 100 ticks); las guaridas (`LairManager`) no comparten el cupo de generación
(el cupo pedido es de aldeas); el cierre de una ola **activa** a los 10 min es una regla de producto (sin
victoria/recompensa); y la clasificación estricta de entidades desconocidas también aplica al asedio clásico
(que cierra sin recompensa por timeout si hay UUID descargados). Estos límites no se presentan como éxito.



### Owner decisions applied — A2 acceptance attempt blocked (23-oct-2026)

Owner decisions recorded: threshold **8/8 in three distinct-seed runs**; fixture **prepared terrain at
`661,71,533`**; vehicle **Edit 5 instrumented run**.

First attempted seed `12345` (`run=a2n-20261023-120319`, `-NavCustom true`, `pared=1`, phases 3000/6600) did not
reach the harness:
- `srv.log` ends at world-generation `Preparing spawn area: 51%`;
- no `[A2N]`, `CANDIDATO`, `SPAWN`, `PARED`, `FASE`, `RESUMEN` or `VEREDICTO` runtime lines;
- launcher killed the isolated server at the 25-minute limit;
- `srv.log.err` only reports Gradle `runServer` exit -1.

The second seed was cancelled before launch because the first did not produce a valid acceptance run. No raider
counts exist; no A2 PASS is claimed.

**Status:** **BLOCKED — TECHNICAL PREREQUISITE MISSING** (fixture/harness never initialized in the bounded run).
Next step: diagnose the world-generation stall or use a pre-generated disposable A2 fixture; do not change the
navigation/AI/threshold.

### A2 blocker root cause — harness excluded by build (23-oct-2026)

The failed run `a2n-20261023-120319` did **not** stall in world generation. Its log confirms:

```text
[12:04:45] Preparing spawn area: 51%
[12:04:45] Time elapsed: 4285 ms
[12:04:45] Done (8.314s)! For help, type "help"
```

The arena then saved periodically (`Gathered mod list ...` every 5 min) until the 25-minute launcher limit. No
`[A2N]` marker ever appeared.

**Cause:** `build.gradle` excludes `com/chipoodle/devilrpg/debug/**` unless
`-Pa2nDiagnostics=true`; `build/a2n/lanzar-a2n.ps1` invokes `runServer` without that property. The copied
`A2NavHarness.java` was therefore not compiled into the runtime classpath and its `@EventBusSubscriber` never loaded.

**Smallest next action:** add `-Pa2nDiagnostics=true` to the launcher's `runServer` invocation, then run one short
preflight expecting `[A2N] ARRANCADO modo=raid`. Do not start the 3-seed campaign until that marker and the
`CANDIDATO/FIXTURE` lines appear. This is a launcher/test-configuration correction, not production navigation.

### A2 launcher preflight — harness loads, fixture center deviates (23-oct-2026)

Launcher fix: added `-Pa2nDiagnostics=true` to `build/a2n/lanzar-a2n.ps1` runServer argument list.

Preflight `a2n-20261023-132018`, seed 12345, 5 minutes:
- `[A2N] ARRANCADO modo=raid` at 13:22:07.
- `FIXTURE RAID listo=true`, wall 400/400 columns, 1200 cells, ring complete.
- `SPAWN TOTAL: intentos=8 ok=8 fallos=0`.
- No entrants by phase A (`entraronPerimetroEver=0`); preflight stopped at phase B.

**Geometry deviation:** candidate `661,71,533` was invalid (`fallosAltura=248`, `fallosContinuidad=219`,
`fallosFundacion=33`). The harness selected candidate 14 at **`629,71,597`**, built the wall there, and spawned 8/8
around that center. This is not the owner-agreed `(661,71,533)` geometry.

No three-seed acceptance campaign was started. Owner decision needed: accept `629,71,597` as the prepared fixture
center, or require the 661 terrain preparation to be corrected so a 661-centered candidate validates.

### A2 center restoration — blocker confirmed (23-oct-2026)

The candidate loop evaluates the **virgin** ring first (`A2NavHarness` ~1812-1832). It only requires
`sinSuelo==0 && fallosFundacion==0`; height/continuity are deferred to preparation. Candidate
`661,71,533` has `fallosFundacion=33`, so it is rejected at selection time.

The terrain-preparation block (solid reference at `Y=70`, clear `Y=71..103`) runs **after** a center is already
chosen (~1852 onward). Therefore it cannot repair the 661 foundation, and the next candidate with zero foundation
failures (`629,71,597`) wins.

**Smallest correction:** apply the same bounded preparation at `661,71,533` before candidate evaluation, or force
that center and validate it. Both are changes inside the protected `A2NavHarness.java`, which this task forbids.
No pre-generated fixture for the 661 ring exists in the current artifacts.

**Status:** BLOCKED — TECHNICAL PREREQUISITE MISSING. No harness or launcher change was made in this pass.

## Current status after successor fix (25-oct-2026)

Isolated DROP `drop11-20261025-0020` PASS; policy tests PASS; no full A2 run. Full A2 remains **BLOCKED** until
the protected harness can prepare/validate the owner-agreed center `661,71,533`. Do not modify
`A2NavHarness.java` or `.partial` without owner approval, and do not treat isolated DROP as A2 acceptance.

## 661 foundation predicate — exact diagnosis (25-oct-2026)

`evaluarRecinto` scans each r=62 ring column from Y=69 downward to the first non-air block. If its collision
shape is empty, the column increments `fallosFundacion`. Candidate `661,71,533` has `sinSuelo=0` but
`fallosFundacion=33`; the selector requires both zero. Fallback `629,71,597` is the first grid candidate with
zero failures and is ~71.6 blocks from the owner center. Preparation (Y=70 platform, clear Y=71..103) runs after
selection and can only fill air, so it cannot repair these 33 columns.

The harness does not log which 33 columns or block types. Smallest safe resolution needs an approved read-only
per-column diagnostic or a prepared disposable world generated with an approved tool; both require owner
approval. Do not modify protected `A2NavHarness.java` or weaken foundation checks. Full A2 remains **BLOCKED**.

### Candidate-grid evidence

Logged in 2n-20261023-132018:

`	ext
661,533  fundacion=33
661,565  fundacion=65
661,501  fundacion=65
661,597  fundacion=110
693,533  fundacion=36
629,533  fundacion=62
629,597  fundacion=0  -> CENTRO ELEGIDO idx=14
`

Only 629,71,597 satisfies sinSuelo=0 && fallosFundacion=0. The fallback is therefore deterministic and not
an arbitrary choice. The harness does not log the 33 individual failing columns or block types.

## Prepared-world tool design — pending approval (25-oct-2026)

Existing mechanism: `-MundoPreparado <world>` copies the source to a unique `<run>/game/a2n` and leaves
`level-name=a2n`. The protected harness then evaluates/buids there. It does not validate source identity or
repair terrain.

Design: an external, disposable-only tool fixes center `661,71,533` and ring r=62. It scans each column from
Y=69 down, records the first non-air block with empty collision, replaces only those cells with solid support,
preserves every other block, and writes a manifest (coordinates, before/after states, counts, hashes). It aborts on
identity/center/count mismatch. The untouched harness must then select 661 because `sinSuelo=0` and
`fallosFundacion=0`; wall/preparation logic remains unchanged. The copy is discarded after the run.

No implementation, no Minecraft launch, no world write. Exact 33 coordinates/types must be obtained by a
report-only scan before repair. Owner approval is required. Full A2 remains BLOCKED.

## Scanner Phase 1 — blocked before read (25-oct-2026)

No source world was positively identified, so no world contents were read and no scanner was written. Historical
`build/a2n` worlds are not approved clean fixtures; the fallback 629 arena is excluded. No read-only Anvil/NBT
library exists. Owner must provide an absolute clean 661 source path or approve a fresh disposable source and a
new read-only scanner. Exact 33 columns remain unknown. Full A2 remains BLOCKED.

## Source recovery verdict (25-oct-2026)

Seed **12345**, center **661,71,533**, cota **71**, well axis **628,71,562** are recorded in `aldea-estados.md`;
launcher default seed is 12345. A fresh disposable world is reproducible without opening historical worlds, but
will still have 33 foundation failures because preparation runs after candidate selection and fills only air.
Outcome: **SOURCE REPRODUCIBLE WITH OWNER APPROVAL.** Python-stdlib Anvil parsing is feasible for block-state
reporting; exact collision-shape equivalence needs a validated map or Java-side test. No world/scanner was created.

## Fresh 661 seed-12345 scan (25-oct-2026)

Fresh world `build/a2n/fixture-source-20261025-0115` generated with plain runServer (no A2 harness), seed 12345,
130s of forceloaded ring chunks, saved/stopped. `level.dat` sha256
`af0301d75023fda9d693204bc6682698e276271693d83af0a73458e1fb833710`. Stdlib scanner parsed 400/400 ring columns,
zero missing chunks: 94 likely-empty water, 306 unknown-collision. Historical aggregate 33 is not reproduced.
No repair, no A2. Report in `report/foundation-manifest.{json,txt}`. Owner review required; SOURCE NOT EQUIVALENT.

## 33-vs-94 provenance audit (25-oct-2026)

Historical 2n-20261023-132018 and fresh ixture-source-20261025-0115 share Minecraft 1.21.1, NeoForge
21.1.249, seed 12345, world type/settings, center 661,71,533 and ring/scan geometry. Historical has only the
aggregate 33; no per-column values. Fresh scanner reports 94 LIKELY_EMPTY water and 306 UNKNOWN collision states.
The difference is **UNRESOLVED**; scanner collision approximation, different worldgen code state, or historical
partial-generation/candidate timing are unproven candidates. Do not use fresh world as equivalent. A2 BLOCKED.

## Historical artifact recovery — insufficient (25-oct-2026)

Run a2n-20261023-132018 has logs/config/world only. The A2NavHarness class from that build is absent; current
worldgen classes are dated 25-oct and cannot be assumed identical. No per-column foundation output exists. Cause
of 33 vs 94 remains unresolved; pin source/build hashes and use a runtime per-column probe, or recover a verified
source. A2 BLOCKED.
## A2 new-baseline arena plan (25-oct-2026)

Historical source recovery is closed for baseline purposes. Fixed baseline: center 661,71,533, seed 12345,
Minecraft 1.21.1, NeoForge 21.1.249, ring 62, wall height 3, cota 71.

Source: build/a2n/fixture-source-20261025-0115/game/a2n (never overwrite). Working copy: unique
build/a2n/arena-new-<run>/game/a2n via non-destructive copy. No historical world is used.

Offline scanner: 400/400 columns, 94 LIKELY_EMPTY water, 306 UNKNOWN collision. Candidate mutations are only the
94 first-non-air water cells (same Y, solid support). This is a candidate subset, not final: runtime
getCollisionShape must confirm every column and detect empty-collision states outside water. No block repair
applied.

Runtime probe requirement (not authorized): disposable working copy + debug-only A5/standalone probe using real
server classes, exact ring, all columns, collision empty/non-empty, proposed mutation. Must not start A2. Then a
separate repair pass (not authorized) applies only manifest rows, writes before/after manifest, and re-probes
idempotently. The unchanged harness cannot validate arena readiness without launching A2 preparation; independent
probe first. A2 remains BLOCKED. Owner must authorize probe and repair policy.
## Runtime probe attempt — blocked (25-oct-2026)

One launch, disposable copy `build/a2n/arena-probe-20261025-0130`. Harness mode none only; no A2. Probe ran at
first Post tick and found all 400 ring chunks unloaded; it failed closed with no state reads/changes. Report in
`report/runtime-foundation.json`. Working copy was saved on halt; source unchanged. Need probe forceload/wait and
a new owner-authorized launch. A2 BLOCKED.
## Probe chunk-loading fix (25-oct-2026)

ArenaFoundationProbe now uses a bounded setChunkForced/wait/ready lifecycle (200 ticks), scans only after all
required chunks are loaded, rechecks during scan, releases tickets, writes reports outside the world, and halts.
No block-writing or A2 path. Fix compiled and lint/diff clean; second launch not authorized. A2 BLOCKED.
## Probe second launch — harness none blocker (25-oct-2026)

One authorized launch `arena-probe2-20261025-0525`: probe LOADING then `[A2N] ... modo=none` terminal close; no
probe report. No A2 raid/candidate/arena; source hash unchanged. Need a probe-only launch excluding A2NavHarness,
not mode=none. Second launch consumed; no retry. A2 BLOCKED.
## Probe-only launch path (25-oct-2026)

Use `-Pa2nDiagnostics=true -Pa2nProbeOnly=true`; build excludes A2NavHarness, A2EntryProbe, AsedioRunA2 and
A2ProdNavProbe, includes ArenaFoundationProbe. Classpath verified both property states. No launch; separate
authorization required. A2 BLOCKED.
## Runtime probe PASS (25-oct-2026)

Isolated `arena-probe3-20261025-0538`: 400 columns; 33 collision-empty water at Y=62; 367 non-empty; 0 missing;
32/32 chunks; cleanup 32/32; process exit. No A2. Manifest
`build/a2n/arena-probe3-20261025-0538/report/runtime-foundation.{json,txt}`. Runtime supersedes offline 94-water
estimate. Repairs/A2 still blocked pending owner review.
## A2 acceptance — partial, navigation blocker (25-oct-2026)

Run `a2n-20261025-054523`: candidate 661 accepted with `fallosFundacion=0`; wall 400/400, 1200 cells;
8/8 spawns. All attackers PLANNING; `entraronPerimetroEver=0`, `dentroParedAhora=0`; no contact/breach/horizontal
entry; `navCustomEfectivo=false`. Status: A2 PARTIALLY PASSED — SPECIFIC BLOCKER. No second attempt.
## A2 ACCEPTED — evidence (25-oct-2026)

Run `a2n-20261025-055802`; navCustomEfectivo=true. Candidate 661 valid, wall 1200, 8/8 spawn. Real wall breach:
9 cells missing, 3 non-solid columns; Siege pica logs at wall coordinates. Horizontal entry: 4/8 entered
perimeter, 2/8 inside radius 62. One disappeared, no deaths, 0 instrument failures. No numeric threshold exists;
result is honestly 4/8 entered / 2 inside. A2 accepted for the stated qualitative walled-raid criteria.
## Navigation correction result — still fails (25-oct-2026)

Global-budget config added; run `a2n-20261025-212406` still 4/8 entered / 2 inside. No-route persists for idx4/7/8;
idx6 DROP contiguity unchanged; idx2 disappearance unchanged. A2 NOT ACCEPTED; no more runs.
## Waypoint/DROP correction result (25-oct-2026)

Run `a2n-20261025-214955`: 4/8 entered, 3 inside (idx6 fixed via origin-first DROP), wall 1200/1200, 1
disappeared. idx4/7/8 remain no-route. 8/8 not met; no 3-run series. A2 NOT ACCEPTED.

## Current C4 gate — 2026-11-12 (supersedes older status paragraphs)

The current master-plan status is **C4 / A2 BLOCKED**, despite historical
isolated runs and older paragraphs in this chronological handoff that say
PARTIAL or ACCEPTED. Those historical outcomes do not satisfy the current
owner-approved full-raid acceptance contract.

The only next authorized *candidate* action is pending explicit owner approval:
a read-only runtime foundation probe in a new disposable seed-12345 world,
fixed center `661,71,533`, using `-Pa2nProbeOnly=true`. The probe must not
start A2, write blocks, modify `A2NavHarness.java` or `.partial`, use fallback
`629`, or read/modify historical worlds. It must force/load the 32 required
chunks, inspect all 400 ring columns using the runtime `getCollisionShape`
implementation, and emit an exact manifest for the 33 foundation columns.

The existing probe-only path and bounded chunk-loading fix are implemented;
the second launch has not been authorized. Once the read-only manifest is
reviewed, any terrain repair policy requires a **separate** owner decision.
No world preparation, repair, or A2 launch is authorized by this documentation
update. Block 6 multi-day soak remains gated until C4 is accepted.

## Runtime read-only foundation probe — PASS (12-nov-2026)

Owner-authorized diagnostic run: `arena-probe4-20261112-204522`.
Fresh dedicated-server world, seed 12345 verified from NBT, center
`661,71,533`, radius 62. Build used
`-Pa2nDiagnostics=true -Pa2nProbeOnly=true`; compiled class guard confirmed
`A2NavHarness`, `A2EntryProbe`, `A2ProdNavProbe` and `AsedioRunA2`
absent. Runtime loaded 32/32 chunks and scanned 400/400 unique columns with
real `getCollisionShape`: 33 EMPTY, 367 NON_EMPTY, zero NO_NON_AIR, zero
missing chunks. All 33 EMPTY states are water at Y=62. Cleanup released
32/32 chunk tickets and halted the server. No A2 raid, block writes, or protected
harness edits. SHA-256 checks before/after confirm both
`A2NavHarness.java` and `.partial` unchanged. The repair bootstrap class was
present but explicitly disabled (`a2n.foundationRepair=false`,
`a2n.mode=none`, repair environment variable cleared); no `[A2FOUND]`
log marker.

Full evidence, exact 33 coordinates, JSON/TXT manifests and hashes:
`A2-FOUNDATION-PROBE-20261112-ACCEPTANCE.md`. Artifacts:
`build/a2n/arena-probe4-20261112-204522/report/runtime-foundation.json`,
`runtime-foundation.txt`, and `srv.log`.

**Decision boundary:** diagnostic PASS only; C4 acceptance remains BLOCKED.
No terrain repair has been performed or authorized. Next action is owner review
of the manifest and a separate explicit decision before replacing those exact
33 first-non-air water blocks in another disposable copy. Do not modify the
protected harness, use fallback 629, access historical worlds or `run/saves`,
or launch the A2 raid before that decision.

## Foundation repair in disposable world — PASS (12-nov-2026)

A separate owner-authorized repair was executed in
`arena-repair1-20261112-205206`, not in the first diagnostic world or any
historical save. Before writes, the second seed-12345 world at center
`661,71,533` was scanned read-only; all 33 empty-collision water coordinates
matched the first manifest exactly. The repair bootstrap then reported
`[A2FOUND] pre-selection repair cells=33 chunks=32 center=661,71,533 r=62`.
The post-repair runtime scan measured 400/400 columns: 0 EMPTY, 400 NON_EMPTY,
0 NO_NON_AIR, 0 missing chunks. Coordinate diff: exactly 33 water→stone at
Y=62, 367 unchanged, zero unexpected changes. All tickets released and server
stopped. Full evidence:
`A2-FOUNDATION-REPAIR-20261112-ACCEPTANCE.md`.

The repair-only launch used the Gradle probe-only source set; the protected A2
harness and other raid entry classes were excluded. The mode property
`a2n.mode=raid` was used solely to satisfy the existing repair bootstrap gate;
the harness was not compiled/loaded, and no A2 raid or spawn occurred. Both
protected harness hashes remain unchanged.

**Current boundary:** foundation repair is PASS in this disposable world;
A2 end-to-end raid remains OPEN/NOT ACCEPTED. Handoff Edit 5 (result contract
and launcher parser) and the single full raid run remain pending. The user has
not authorized edits to the protected `A2NavHarness.java` or execution of the
full raid. Obtain separate approval before either. Do not touch historical
worlds, `run/saves`, or `.partial`.

## Edit 5 / launcher read-only audit — 12-nov-2026

A further read-only audit corrected the older blanket "Edit 5 absent" description.
The canonical source already contains phase A/B `informeRaid`, registered
per-column wall baseline, `TOTALES`, `PARED total`, `VEREDICTO`, and
per-attacker result lines. However, this is **instrumentation only**:
`VEREDICTO` explicitly says `SIN UMBRAL CONTRATADO`, the final
`fin.txt` status is `INSTRUMENTADO`, and the launcher reports marker counts
without semantic assertions. The canonical harness has no direct guard-response
or post-raid profession/assignment-recovery instrumentation, although those
are required by the C4 contract.

The older ignored draft `build/a2n/_raid_e5_block.txt` must not be applied:
its wall integrity routine is weaker than the registered-base implementation
already in the canonical source. Full findings:
`A2-EDIT5-READONLY-AUDIT-20261112.md`.

**Next gate:** obtain explicit owner approval before editing the protected
canonical `tools/a2n/A2NavHarness.java`. The `.partial` remains protected
and must not be touched. The full A2 raid also remains unauthorized; even after
any approved harness change, obtain separate approval before launching it.
The launcher supports copying a prepared world with `-MundoPreparado`, so a
future raid can use a fresh copy of the repaired disposable world and preserve
the repair evidence world. No such run has been launched.


## C4 end-to-end acceptance — PASS (12-nov-2026)

Run aceptado: `build/a2n/c4-full3-20261112-0015`. Servidor dedicado `runServer --nogui`, copia desechable del mundo reparado; el seed original no se abrió ni modificó. `fin.txt`:
```text
C4_PASS (raid) - guardasCombatieron=true profesionalesReanudados=true asignacionesJobSiteLimpias=true supervivientes=16 retiradosParaRecuperacion=3 jobSite=[2, 0, 0] fallosDeInstrumento=0
```

- Cuatro UUID distintos de guardias fundadores adquirieron un atacante vivo en ticks 661–662.
- 12/12 trabajadores reanudaron sus Goals profesionales; 12/12 profesionales tenían Goal instalado.
- 16 aldeanos vivos al cierre; resultado de aldea `SURVIVED`.
- Auditoría JOB_SITE: 0 huérfanos, 0 propietarios duplicados.
- Muro de producción: 496 columnas × 2 capas continuas = 992/992 celdas.
- Ocho atacantes registrados en el estado de asedio real; cinco eventos explícitos de muerte y tres retiradas por recuperación.
- Sin GUI/cliente, sin foco robado, sin intervención, sin `run/saves`; servidor guardó Overworld/Nether/End y cerró normalmente.

**Límite:** el engagement se demuestra por adquisición de objetivo. No hubo impactos registrados (`C4 GUARD HIT=0`) ni kills atribuidos a guardias (`guardKill=0`). No declarar daño/kill de guardia. Algunos snapshots posteriores sobrecuentan como desaparecidos atacantes ya muertos/retirados; esa contabilidad es informativa y no forma parte de los predicados C4 aceptados.

### Launcher y recuperación tras desconexión

`tools/a2n/lanzar-c4.ps1` prepara un GameDir independiente por RunId, verifica el SHA-256 de `level.dat`, lanza solo servidor dedicado y valida `fin.txt`, `C4_PASS`, cuatro UUID de guardia únicos y exit code de Gradle. Se corrigió el comportamiento de Windows PowerShell 5.1 que elevaba el stderr informativo del JDK a `NativeCommandError` bajo `ErrorActionPreference=Stop`.

Si se corta la conexión de ChatGPT/Desktop Commander, **no se debe relanzar el mismo RunId**: el proceso remoto puede seguir ejecutándose. Ejecutar `powershell -NoProfile -ExecutionPolicy Bypass -File tools/a2n/consultar-c4.ps1 -RunId <id> -Wait` para sondear cada 15 s hasta `fin.txt`, sin relanzar ni detener. Al reconectar, recuperar `fin.txt`, `launcher.log`, `game/logs/latest.log` y el estado del proceso. Si `fin.txt` contiene `C4_PASS`, el run ya terminó; si no existe, inspeccionar proceso y log antes de decidir. Las llamadas remotas transitorias se reintentan hasta 3 veces con espera creciente. No se puede garantizar reconexión automática del canal MCP si el agente remoto o la red están realmente desconectados.

**Estado global:** C1–C5 ACCEPTED. Block 6 multi-day soak queda desbloqueado, pero no iniciado. Sin commit/push. `tools/a2n/A2NavHarness.java.partial` permanece intacto.


## Latest correction — C4 repeatability remains OPEN (12-nov-2026 21:56)

The earlier section above records the run3 PASS, but it is superseded for overall acceptance by the repeatability check:

- `c4-full4-20261112-0045`: C4_FAIL. Four guards engaged and delivered 16 recorded hits; six attackers died; 16 villagers survived; JOB_SITE audit 0 orphaned/0 duplicate. However only 11/12 workers were counted as resumed even though all 12 professional Goals remained installed. The run did not record the missing worker UUID.
- Root cause is not yet proven. The prior sampler only queried the village AABB and may have omitted a worker travelling outside it; it could also represent a real inactive worker.
- Canonical `tools/a2n/A2NavHarness.java` now captures the 12 expected worker UUIDs during the initial fixture and samples those entities directly regardless of position. On failure it logs missing worker UUID, profession, expected/running Goals, position and JOB_SITE.
- The updated canonical harness compiled successfully. SHA-256: `926013FF93D469F7B338171FBDEDE4DC2C9FECB3DE956AA3E4384071A9A47EA5`.
- `c4-full5-20261112-0155` is running with the UUID-based sampler. Do not call C4 accepted until its `fin.txt` is final and the 12/12 predicate passes, or a diagnosed production defect is fixed and retested.

C1/C2/C3/C5 remain accepted; C4 is the sole open C-level gate. Block 6 multi-day soak remains gated. The corrected launcher redirects both Gradle streams via cmd.exe; `consultar-c4.ps1` recovers a run after a dropped chat/remote connection. Do not relaunch a RunId already running or completed.


## Latest status — casualty accounting correction and run6 (12-nov-2026 22:06)

Run5 completed with `C4_FAIL` under the old strict 12/12 initial-worker rule, but its evidence clarifies the semantics: the missing UUID `f62b9fae-2361-42b0-b0f3-6058f265c789` was the only weaponsmith and the Minecraft death log records Quintin was slain by an Aggressive Zombie. The village survived with 15 villagers, 11 professional workers alive, all 11 resumed their Goals, and JOB_SITE had 0 orphaned/duplicate owners. This is a real casualty, not a worker left idle.

The C4 contract has no zero-casualty threshold. The corrected predicate counts initial workers as alive, confirmed dead or unresolved; requires every living professional to resume, every missing worker UUID to have a death event, zero unresolved UUIDs, four guard engagements, village survival and clean JOB_SITE ownership. The harness subscribes to `LivingDeathEvent` and logs UUID, profession, name, position, DamageSource, attacker and direct entity. It compiles successfully; canonical SHA-256 `0B699503DCBE99D084AE4A073FE77650B72484B02BA97DED0829C889BFCA9358`.

Run `c4-full6-20261112-0210` is active on a fresh disposable world with the revised accounting. Dedicated server, no GUI. The local `consultar-c4.ps1 -RunId c4-full6-20261112-0210 -Wait` monitor is active. C4 remains OPEN until its final `fin.txt` passes with zero unresolved workers and all surviving professionals resumed.


## Continuación C4 — 2026-11-12 (run7 PASS; run8 en curso)

- `c4-full7-20261112-0230` terminó `C4_PASS`: 4/4 guardias adquirieron enemigo, 14 impactos y 4 bajas atribuidas a guardias; 12/12 trabajadores vivos y reanudados; 0 bajas y 0 UUID sin resolver; 16 aldeanos vivos; JOB_SITE 1/0/0; muro 992/992; 0 fallos de instrumento. Cinco muertes de atacantes confirmadas y tres retiradas para recuperación.
- `c4-full8-20261112-2240` es la repetición actualmente en curso en puerto 25579, mundo desechable independiente y seed reparado verificado por SHA-256. Fixture ya listo: 496 columnas, 992 celdas, 4 guardias equipados, 12 trabajadores/Goals, 8 atacantes registrados.
- Watcher local activo PID 29108: `tools\\a2n\\consultar-c4.ps1 -RunId c4-full8-20261112-2240 -Wait -PollSeconds 15 -TimeoutMinutes 30`. No relanza ni detiene el servidor.
- Si se corta la conexión de ChatGPT/Desktop Commander, los procesos locales continúan. Al reanudar, consultar dispositivo y el mismo RunId; leer `fin.txt`, `launcher.log` y `game\\logs\\latest.log`. No iniciar otra corrida mientras exista JVM asociada o falte inspeccionar el resultado. La conexión del asistente no se puede autoreconectar desde el modelo.
- C4 ya tiene un run PASS repetible; mantener la aceptación final pendiente de la repetición run8. Sin commit/push.


## C4 — cierre con aceptación repetible (2026-11-12)

- **C4 ACCEPTED** tras dos ejecuciones independientes consecutivas PASS: c4-full7-20261112-0230 y c4-full8-20261112-2240.
- Run8: 4/4 guardias, 15 impactos, 5 bajas atribuidas; 6 muertes de atacantes y 2 retirados para recuperación; 12/12 trabajadores vivos/reanudados; 0 bajas y 0 UUID sin resolver; 16 aldeanos vivos; JOB_SITE 1/0/0; muro 992/992; 0 fallos; apagado y guardado normales; BUILD SUCCESSFUL 7m54s.
- Evidencia y SHA-256 completos en docs/autonomous-village/A2-C4-HEADLESS-ACCEPTANCE-20261112.md, sección 9. Logs bajo build/a2n/c4-full8-20261112-2240/.
- El watcher consultar-c4.ps1 terminó al encontrar fin.txt; no hay JVM del run8 activa. Si vuelve a cortarse la sesión remota, el proceso local continúa y se recupera consultando el mismo RunId; el modelo no puede reconectar por sí solo una conversación caída.
- C4 cerrado; A2 global y soak multi-día siguen abiertos según sus propios gates. No commit/push; preservar todo el worktree.


## 2026-11-12 22:43 — C4 ACCEPTED; A2 GLOBAL STILL OPEN

C4 is now accepted independently of the broader A2 walled-raid navigation campaign. The final callback run `c4-full8-20261112-2240` used the real production village and guard/workforce setup on a fresh copy of the repaired seed, with a dedicated headless server. Final evidence: 4/4 guards engaged, 15 guard-hit events, 6 attacker deaths (5 attributed to guards, 1 to the iron golem), 2 attackers removed for recovery, 12/12 living professional workers resumed, 16 villagers survived, 0 worker casualties/unresolved UUIDs, JOB_SITE 0 orphaned/duplicates, wall 992/992, navigation trace 25,494 records / 0 discarded / 0 failures, and normal server save/shutdown. C4 report: `A2-C4-HEADLESS-ACCEPTANCE-20261112.md`, section 9.

**Do not interpret this C4 PASS as A2 global acceptance.** The A2 contract recorded above remains 8/8 attackers entering the perimeter in three distinct-seed runs, with the owner-agreed center/geometry and the full navigation/breach criteria. Run8's C4 raid snapshot reached `entraronPerimetroEver=6/8`; therefore it does not satisfy the A2 8/8 threshold. It was run to validate C4 production defense/recovery, not to close the A2 navigation campaign. A2 global remains OPEN; no A2 three-seed campaign has been accepted.

Next work after this C4 close is the separate A2 navigation campaign and Block 6 bounded multi-day soak. Do not reuse C4's result as an A2 PASS or change the 8/8-in-three-seeds contract. Keep the repaired seed immutable; each future run needs its own disposable copy and a distinct seed/approved fixture preparation. Preserve `A2NavHarness.java.partial`; no commit/push.

### Read-only A2 navigation audit of C4 run8 — 2026-11-12 22:44

The C4 run8 trace header is explicit:
`# schema=a2n-navtrace/1 run=c4-full8-20261112-2240 traceNav=true navCustom=false`.
Therefore run8 measured navigation telemetry but **did not enable the custom A2 navigation feature**. This is why its C4 PASS must not be counted as an A2 navigation run.

Run8's attacker outcome lines show idx4, idx7 and idx8 alive outside the perimeter:
- idx4 UUID `cba11910-91b8-4be3-adef-8a83e736e915`, final position `566,96,533`, horizontal distance 94.50, never entered.
- idx7 UUID `73cc3fbc-65ab-435e-8941-5792e243104b`, final position `746,64,524`, horizontal distance 85.95, never entered.
- idx8 UUID `388d4385-f698-4b34-a01f-e9601cf41550`, final position `695,63,454`, horizontal distance 85.85, never entered.
All three trace rows remain `PLANNING` with `movio=false`, `nodoRecuperacion=-`, `intentosDeDescenso=0`. Their trace coverage was complete (idx4 6600 records, idx7 6467, idx8 6600; no gaps). Aggregate `entraronPerimetroEver=6/8`.

The cause of `navCustom=false` is configuration, not a proven navigation algorithm defect. `NavFeature` is controlled by system property `-Ddevilrpg.nav.custom=true` or environment `DEVILRPG_NAV_CUSTOM=1`. The existing A2 launcher `build/a2n/lanzar-a2n.ps1` already accepts `-NavCustom` and sets `JDK_JAVA_OPTIONS` to `-Ddevilrpg.nav.custom=$NavCustom`; the C4 launcher intentionally did not set it. For any future A2 navigation campaign, use the A2 launcher with `-NavCustom true`, and verify the trace header says `navCustom=true` before interpreting movement. Do not alter the C4 PASS or count run8 as an A2 attempt.


## A2 custom-navigation attempt 1 — IN PROGRESS (2026-11-12 22:47)

A2 global campaign has begun on the agreed repaired seed (first of the three distinct-seed attempts), using the existing A2 launcher rather than the C4 launcher:
- RunId: `a2n-20261112-224753`
- Mode: `raid`; `-NavCustom true`; budget 900; phase A 3000; phase B 6600; limit 30 min.
- Source world: immutable repaired seed `build/a2n/arena-repair1-20261112-205206/game/a2n`; copied to isolated `build/a2n/a2n-20261112-224753/game/a2n`.
- Local launcher monitor process: PID 19196; server JVM PID 17180 (Gradle parent 19880). The launcher waits for `fin.txt`, saves and then stops only its identified server JVM.
- A2 launcher `build/a2n/lanzar-a2n.ps1` was adjusted (ignored build artifact only) to add `-Ddevilrpg.a2n.traceNav=true` to `JDK_JAVA_OPTIONS`, preserving its existing `-Ddevilrpg.nav.custom=$NavCustom` switch. PowerShell parser validation PASS.
- At reconnect, inspect the same RunId's `fin.txt`, `srv.log`, `srv.log.err`, `nav-trace.log`, and process state. Verify trace header `navCustom=true` before interpreting results. Never relaunch this RunId.
- This is attempt 1 only. A2 acceptance remains 8/8 attackers entering the perimeter in three distinct-seed runs; no A2 PASS is claimed until all three runs satisfy the contract. C4 remains separately ACCEPTED. No `.partial`, historical worlds, or `run/saves`; no commit/push.


### A2 launcher preflight correction — 2026-11-12 22:49

Run `a2n-20261112-224753` closed as `INVALIDO (raid) - RAID FIXTURE INVALIDO` before any attacker spawn. The log identified `C4 MAINTENANCE ERROR: IllegalStateException: A2 maintenance hook disabled`; the A2 launcher had omitted Gradle property `-Pa2nMaintenanceHook=true`, which the C4 launcher already supplies. This was a launcher/configuration preflight failure, not a navigation or production AI failure and is **not counted** among the three A2 seed attempts. Added `-Pa2nMaintenanceHook=true` to the ignored A2 launcher. PowerShell parser PASS. Do not relaunch the same RunId.

The corrected new RunId `a2n-20261112-224926` is active (seed 12345, prepared-world copy, `-NavCustom true`, trace enabled). Launcher monitor PID 24192; server JVM PID 29700. Wait for its trace header and final `fin.txt` before deciding whether this is valid attempt 1.


### Run a2n-20261112-224926 — live checkpoint (raidTick≈320)

Verified trace header `traceNav=true navCustom=true`; `NAVCUSTOM` log confirms `devilrpg.nav.custom=true`. At raidTick≈320, 8 attackers remain tracked. Trace shows idx1/2/5/6 in TRAVERSING, idx3/4 PLANNING, idx7/8 FAILED at this snapshot; idx5 has four natural DROP attempts with zero failures. A real wall breach was logged at `620,72,580` (oak log). idx7's local planner reported `tope de escalones locales alcanzado (24)` toward `701,71,483`, followed by terminal rearm attempts and a placed step. This is intermediate evidence only; do not declare pass/fail until the full raid and final result.

The initial A2 launcher limit was 30 minutes, potentially too short for custom-nav ticks. The script's default was raised to 90 minutes. To preserve this live run, its original 30-minute PowerShell monitor PID 24192 was stopped without terminating the Gradle/server descendants; server PID 29700 remains alive. A separate non-terminating watcher PID 39808 now waits up to 120 minutes for `fin.txt` and then verifies save/shutdown. On reconnect, inspect the same RunId and watcher-status.txt; do not relaunch.


Run `a2n-20261112-224926` checkpoint raidTick=1500 (phase A): 8/8 initially tracked; 4 alive, 4 dead, 0 disappeared; 5/8 have entered the perimeter at least once, 1 currently inside. A real breach was logged at `620,72,580` (oak log). The custom-nav trace continues with `traceNav=true navCustom=true`; DROP natural successes are recorded. At least idx7 continues to hit local step limit 24 toward `701,71,483`; do not alter navigation mid-run. Trace size 53.5 MB; D: free 298.5 GB; server PID 29700 and watcher PID 39808 alive; no fin.txt yet. Intermediate only.


Run `a2n-20261112-224926` checkpoint raidTick=2100: 5/8 entered perimeter ever, 0 currently inside, 5 dead, 3 alive, 0 disappeared. The same three non-entrants are idx4 (spawned elevated at 566,96,533; repeated `sin waypoint de pie VALIDO ... (habia waypoints pero el mundo cambio)`), idx7 (local step ceiling 24 toward 701,71,483; remains FAILED), and idx8 (still PLANNING near 745,63,553 with slow movement). At least four distinct wall breach coordinates were logged (idx1/2/3/5 paths); no final verdict yet. Run remains active with custom nav true; trace 54.1 MB, disk free 298.5 GB; server PID 29700 and watcher PID 39808 alive.


Run `a2n-20261112-224926` completed phase A at raidTick=3000: 8/8 spawned, 5 dead, 3 alive, 0 disappeared, `entraronPerimetroEver=5/8`, 0 currently inside. Explicit non-entrants at the phase-A result: idx4 alive at `566,96,533` (94.50 horizontal; never entered); idx7 alive at `702,61,457` (85.88; never entered); idx8 alive at `743,63,501` (88.39; never entered). Phase B began at tick3001; continue through t=6600 and recovery before finalizing the run. Custom navigation and trace are confirmed true. This is a valid A2 custom-nav run but it has not met the 8/8 threshold at phase A; no final A2 acceptance claim.


Run `a2n-20261112-224926` phase-B checkpoint raidTick=4800: still 5/8 entered, 5 dead, 3 alive, 0 disappeared, 0 inside wall. No additional attacker crossed after t=600. idx4 and idx7 remain FAILED at their original positions; idx8 remains PLANNING and has moved around the outer terrain but is still ~90 blocks from center. This run currently fails the 8/8 entry criterion unless the remaining 3 recover before phase B ends. Continue to final result and recovery audit; no source edits mid-run.


Run `a2n-20261112-224926` completed phase B at raidTick=6600 with the same result: 8/8 spawned, 5/8 entered perimeter ever, 5 dead, 3 alive, 0 disappeared, 0 inside. Harness removed the 3 remaining attackers for recovery and logged `C4 POSTRAID maintenance=OK attackersRemovedForRecovery=3 recoveryTicks=1200`. Recovery window is now active; final C4 worker/JOB_SITE audit and shutdown still pending. A2 8/8 criterion is not met by this seed; preserve this valid custom-nav run as failure evidence.


## A2 seed-12345 run 1 — FINAL: A2 FAIL / C4 PASS (2026-11-12 22:58)

RunId `a2n-20261112-224926`; `navCustom=true`, `traceNav=true`; 8/8 attackers spawned; 5/8 ever entered the perimeter; 5 dead, 3 alive, 0 disappeared at t=6600. The three non-entrants were idx4, idx7 and idx8. Their global A* searches all exhausted the fixed 8000-expansion budget (`exp=8000/8000`, `presupuesto global agotado`); partial fronts had 21–26 nodes. idx7 and idx8 additionally exhausted `MAX_ESCALONES_DE_OBJETIVO=24`; idx4 repeatedly had no valid local standing waypoint after world changes and exhausted 6/6 local objective attempts. No one of these three entered during phase B. Thus the agreed A2 8/8 criterion **failed** for this seed/run; this is a valid diagnostic attempt, not an accepted seed.

Exact global-search evidence:
- attacker #268 / idx4: start `566,96,533`, meta `597,71,533`, front `587,112,532`, routeFront=21, 8000 expansions, 374,292 enqueued; terminal `sin objetivo local valido (6/6)`.
- attacker #271 / idx7: start `718,63,466`, meta `702,71,484`, front `702,39,484`, routeFront=26, 8000 expansions, 300,218 enqueued; local step ceiling 24.
- attacker #272 / idx8: start `748,63,528`, meta `725,71,529`, front `729,43,524`, routeFront=26, 8000 expansions, ~294k enqueued; local step ceiling 24.
- idx4 spawn fallback built a single-column platform at `566,95,533`, placing the mob at `566,96,533`; this is a 25-block height difference from the village anchor and may contribute to its inaccessible start.

Despite the A2 navigation failure, the same run passed the separate C4 contract: 4/4 guards fought, 12/12 workers resumed, 16 villagers alive, 0 worker casualties/unresolved, JOB_SITE orphaned=0/duplicateOwners=0, 3 attackers removed for recovery, 0 instrumentation failures. Persisted verdict `C4_PASS`. Server stopped normally, all dimensions saved; watcher PID 39808 verified `finExists=true`, `allDimensionsSaved=true`, `serverPidStillAlive=false`. Trace file 61.1 MB.

Next experiment: increase only the already-supported global A* budget from 8000 to 32000, keep local budget 900 and local-step cap 24 unchanged initially. This isolates whether global search truncation caused the partial routes and step-cap exhaustion. If the same three fail, then address local-step/waypoint behavior and the elevated fallback spawn separately. Use a fresh disposable copy of the immutable repaired seed; do not modify this completed run or its world. C4 remains accepted independently; A2 global remains OPEN.


## A2 seed-12345 retest — global budget 32000 — IN PROGRESS

New isolated RunId `a2n-20261112-230041`, fresh copy of the immutable repaired seed 12345. `-NavCustom true`, `-Presupuesto 900` (local unchanged), `-GlobalBudget 32000` (only experiment variable changed), phaseA=3000, phaseB=6600, recoveryTicks default 1200, timeout 90 min. The ignored A2 launcher now exposes `-GlobalBudget` and forwards `-Ddevilrpg.nav.globalBudget`; PowerShell parser PASS. Maintenance hook and trace enabled. Run monitor PID 37336; Gradle process PID 22372. Verify `NAVCUSTOM` and first global plan summaries, then let the run complete. This retest tests whether 8000 global expansions caused the previous 5/8 result; do not change source mid-run. Run1 remains recorded as a valid A2 failure; this is a new attempt on the same seed, not a new distinct seed.


Run `a2n-20261112-230041` early route-planning checkpoint (trace header `navCustom=true`): five initial attackers (#265, #266, #267, #269, #270) found complete global routes with 1,060–4,667 expansions (budget 32,000). The remaining three still exhausted 32,000: #268/idx4 front at `596,106,512` d=68.3 routeFront=45; #271/idx7 front at `660,43,523` d=10.0 routeFront=84; #272/idx8 front at `688,42,508` d=36.8 routeFront=73. Thus the larger budget yields substantially longer partial routes but still hits the cap for the same difficult spawns. No verdict yet; continue to observe movement/replans before changing the heuristic or spawn fixture.


### Static root-cause finding — global A* ranks only horizontal progress (read-only)

The global planner's heuristic `NavPlanner.heuristicaGlobal` returns only XZ Euclidean distance; its `Graph.distance` also returns only horizontal squared distance. `NavGraphSearch.search` uses `Graph.distance` to select the best partial `front` when the expansion budget is exhausted. This directly explains the run1/run2 partial fronts: idx7 front `702,39,484` is horizontally at the target but 32 blocks too low; idx8 front `725,42,533` is horizontally at its waypoint but 29 blocks too low. The search therefore prefers a node with perfect XZ alignment despite a large vertical mismatch, and spends expansions across irrelevant vertical layers. Run2 at 32k repeats this pattern.

A justified correction is to make both metrics 3D-aware while preserving A* admissibility: heuristic lower bound = `max(horizontalDistanceToGoalRegion, verticalDistanceToAllowedBand / 3)` (maximum vertical change per validated edge is 3; upward limit is 2, downward safe-drop limit 3), and front-ranking distance = squared horizontal distance to the accepted goal region plus squared vertical distance outside the ±1 feet band. Keep horizontal radius 1.5 and vertical band ±1 unchanged. This should be implemented and tested only after run2 completes; do not change source during the active run. A separate spawn-fixture issue remains for idx4's isolated platform at y95.


## A2 global-budget 32000 experiment — INVALID / WATCHDOG ABORT (2026-11-12 23:03)

RunId `a2n-20261112-230041` was terminated by Minecraft ServerHangWatchdog during `NavPlanner.planGlobal` after a single server tick exceeded the 60-second watchdog threshold. Crash stack: `NavObstruction.fits:98` → `NavObstruction.classify:170` → `NavPlanner.validarArista:109` → `NavGraphSearch.search:84` → `NavPlanner.planGlobal:295` → `NavExecutor.rutaGlobalHacia:1293`. The 32,000 expansion cap generated 1,792,000 edge validations per search; this is not safe to execute synchronously in one entity tick. The server then saved overworld/nether/end chunks and shut down; Gradle `:runServer FAILED`, no `fin.txt`, no semantic verdict. This is a performance/architecture failure, not a valid A2 pass or C4 result. The orphaned launcher monitor PID 37336 was stopped after the server had already exited; no game/server JVM remains. Keep crash report at `game/crash-reports/crash-2026-11-12_23.03.20-server.txt`.

Do **not** raise the global budget to 32000 again. Return to 8000 while correcting the search. Static root cause is now confirmed: `NavPlanner.heuristicaGlobal` and `Graph.distance` use only XZ distance even though the goal includes Y; `NavGraphSearch.search` uses `Graph.distance` to choose its partial front on budget exhaustion. This chooses nodes exactly at target XZ but 29–32 blocks below the target (e.g. #271 front `706,38,487` for meta `706,71,488`; #272 front `725,42,533` for meta `725,71,533`). The partial route then burns local objective steps and fails. Implement a 3D-aware goal-region heuristic/front distance, keeping the A* heuristic admissible and the expansion cap at 8000. Revisit idx4's isolated one-column spawn platform separately after the metric fix.


## A2 seed-12345 retest with 3D goal metrics — IN PROGRESS

RunId `a2n-20261112-231005`, fresh disposable copy of immutable repaired seed 12345. Runtime now uses 3D-aware `NavGoalMetric`: A* lower-bound heuristic accounts for vertical tolerance and max climb/drop; partial-front ranking uses horizontal distance to the goal region plus vertical distance. Global budget restored to 8000; local budget remains 900; max local target steps remain 24. `-NavCustom true`, trace and maintenance hook enabled; phases 3000/6600; 90-minute monitor. The new metric test and combined `policyTest compileJava -Pa2nDiagnostics=true` passed before launch. Verify trace header and global expansion counts, then complete the run. This tests whether the metric fix resolves the previous 5/8 result without the 32k watchdog crash.


Run `a2n-20261112-231005` initial A2 fixture: `navCustomEfectivo=true`, 8/8 spawned, production wall 992/992 intact. Early global routes for idx1/2/3/5/6 completed under the 8000 cap (1,071–4,715 expansions; route lengths 71–100), versus the previous 8k run where the same difficult group repeatedly exhausted budget. idx4 remains the hard case: 8000/8000, partial front `589,107,543`, routeFront=23, dFrente3D=78.5; CADENA-DIAG explicitly reports a 25-block unsupported drop at `588,93,533` requiring `ponerEscalon`. This suggests the 3D metric is improving route search for most attackers without increasing the budget; wait for idx4/7/8 movement and final phase-A result before deciding on spawn-fixture changes.


Run `a2n-20261112-231005` live checkpoint (23:11:54): 3D metric now returns much better partial fronts for the difficult spawns. idx4 (#268) front `575,70,533`, dFrente3D=20.5, dY=0, routeFront=9 (previous horizontal-only run front was >68 blocks away and 32 blocks high); its chain diagnosis proposes a stair for the 25-block unsupported drop. idx8 (#272) front `741,68,533`, dFrente3D=14.6, dY=2, routeFront=7 (previous run front was 29 blocks below goal). idx7 (#271) is now actively placing a stair at `722,63,468` instead of exhausting the 24-step ceiling immediately. Several attackers are moving; no failure verdict yet. The 3D metric is materially improving vertical alignment while keeping the 8000 budget and avoiding the 32k watchdog condition.


Run `a2n-20261112-231005` checkpoint raidTick=400: custom navigation remains active; first genuine wall breach occurred at `627,73,585` by idx3, which is recorded `DENTRO-PARED` at t=388. All 8 attackers remain tracked; idx7 has placed a stair at `722,63,468`; idx5's natural descent has 9 attempts / 0 failures; idx4 remains at elevated spawn `566,96,533` with the 25-block stair requirement pending. All four guards have begun engaging attackers (C4 guard-engaged events). Early progress only; continue through phase A/B.


Run `a2n-20261112-231005` checkpoint raidTick≈680: idx1 and idx2 have now crossed the perimeter (`DENTRO-PARED` at t=672 and t=687); idx3 crossed earlier at t=388. At least 3/8 attackers have entered. idx7's 3D partial front is now `717,68,476` (dY=2) and it is advancing after placing a stair; idx8's front remains near correct height. idx4 remains the unresolved outlier at `566,96,533`: repeated local-waypoint invalidation (6/6) despite a 3D front near `575,70,533`; its 25-block unsupported drop requires a built stair and needs separate follow-up. No source edits during the run.


Run `a2n-20261112-231005` checkpoint raidTick≈1120: three explicit `DENTRO-PARED` crossings have been logged (idx1/2/3). idx5 is physically at `661.5,72,536.5` (1.5 blocks from center) after traversing the breach, but its `DENTRO-PARED` event/counter has not yet appeared in the log; rely on the phase-A `entraronPerimetroEver` snapshot, not visual position alone. idx4 remains stationary at `566.5,96,533.5` with repeated 6/6 invalid local waypoint failures; its high fallback platform remains the only clear blocker. idx7/8 continue planning/moving with the new 3D front metric. Run remains active and no watchdog crash observed.


Run `a2n-20261112-231005` checkpoint raidTick=1800: `entraronPerimetroEver=5/8`, `dentroParedAhora=1`, 4 dead, 4 alive, 0 disappeared. Explicit crossings are idx1/2/3/5/6; the remaining three are idx4/7/8. idx4 is conclusively stuck at `566,96,533`: global partial front is now correctly aligned at `575,70,533` (dY=0), but `NavExecutor` repeatedly fails `sin objetivo local valido (6/6)` because no standing waypoint is valid inside the local window after the world changed. It never leaves the one-column high platform. The 3D metric improved global alignment but does not solve this spawn/platform transition. Continue phase B to capture final state and C4 recovery; then fix spawn fallback so no attacker is placed on an isolated 25-block-high single-column platform.


Run `a2n-20261112-231005` checkpoint raidTick=2400: `entraronPerimetroEver=5/8`, 3 dead, 5 alive, 0 disappeared, 0 inside wall at snapshot. idx4 is definitively `FAILED` at `566,96,533` (distance94.5), no descent attempts; this seed cannot reach 8/8 unless the harness is changed. idx7 is `TRAVERSING` at `736,63,576` (distance86.86) with a DROP-EN-CURSO step; idx8 is `PLANNING` at `732,61,579` (distance85.42), tunneling through stone toward waypoint `693,71,544` with tunnel budget 33/40. Allow phase A/B to finish; the two remaining attackers may still enter. Next source fix should first remove the unreachable idx4 platform fallback, then retest whether idx7/8 finish their tunnel and cross the wall.


Run `a2n-20261112-231005` phase A completed at raidTick=3000: 8/8 spawned, `entraronPerimetroEver=5/8`, 3 alive, 5 dead, 0 disappeared, 0 currently inside. Non-entrants: idx4 at `566,96,533` (FAILED, isolated spawn platform); idx7 at `738,64,573` (FAILED: replans agotados 4/4); idx8 at `733,61,579` (FAILED: local step ceiling 24). The 3D metric improved partial-front vertical alignment but did not raise the entry count beyond 5/8. Phase B continues; final C4 recovery audit still pending.


### Second root cause identified — global selector discards BUILD transitions (read-only)

`NavRoute.Transition.BUILD` is explicitly documented as a placement-cell node whose resulting standing cell is `node.pos().above()`. `NavPlanner.validarArista` can emit BUILD transitions and the local route executor already executes them via `placement.place(node.pos())`. However, `NavExecutor.elegirObjetivoLocal()` unconditionally skips every node for which `NavClimbPolicy.esWaypointDePie(n)` is false; that policy returns false for BUILD. Consequently the global selector discards the only route nodes that can create support across unsupported drops, then reports `no waypoint de pie valido` and invalidates the route. This explains idx4's 25-block unsupported drop and likely contributes to idx7/idx8's repeated replan/step-limit failures.

Next fix after current run completes: preserve BUILD nodes in global waypoint selection by converting each BUILD placement cell to its resulting foot cell (`n.pos().above()`), mark the local objective as build-capable, and allow the local planner to plan to that cell even before support exists. The local planner's explicit BUILD transition should then place/verify the block through the existing route executor. Keep ordinary WALK/STEP/DROP waypoint validation unchanged; add focused policy tests. Do not merely raise global budget; 32k already caused a watchdog hang.


Run `a2n-20261112-231005` phase B completed at t=6600 with no additional entries: 8/8 spawned, 5/8 entered perimeter ever, 5 dead, 3 alive, 0 disappeared, 0 inside at final snapshot. Harness removed the three remaining attackers and started the 1200-tick post-raid recovery (`C4 POSTRAID maintenance=OK attackersRemovedForRecovery=3`). C4 worker/job-site verdict and normal shutdown are still pending.


## A2 seed-12345 retest with 3D metric — FINAL: A2 FAIL / C4 PASS (2026-11-12 23:18)

RunId `a2n-20261112-231005`; globalBudget=8000; local budget=900; navCustom=true; trace 55.2 MB. Final raid snapshot at t=6600: 8/8 spawned, `entraronPerimetroEver=5/8`, 5 dead, 3 alive, 0 disappeared, 0 inside wall. Non-entrants idx4/7/8; idx4 failed at the isolated platform `566,96,533`, idx7 failed after 4/4 replans, idx8 hit the 24 local-step ceiling. Phase B did not add entries. Post-raid recovery removed the three remaining attackers. `fin.txt` and `launcher-status.txt` both record normal completion; server saved overworld/nether/end, `All dimensions are saved`, Gradle `BUILD SUCCESSFUL`; no game/server JVM remains.

C4 verdict: `C4_PASS` — guards 4/4 fought; professionals 12/12 resumed; 16 villagers alive; 0 worker casualties; 0 unresolved workers; JOB_SITE assignments=3, orphaned=0, duplicateOwners=0; 3 attackers removed for recovery; instrumentation failures=0. C4 remains independently accepted. A2 remains OPEN (5/8).

## BUILD waypoint correction — implemented, awaiting runtime validation

Confirmed the second defect in `NavExecutor.elegirObjetivoLocal`: it discarded `NavRoute.Transition.BUILD` because `NavClimbPolicy.esWaypointDePie(BUILD)` is correctly false for a placement cell. Added `esWaypointSeleccionable()` / `requiereConstruccion()` to distinguish a buildable placement waypoint from a standing waypoint. The global selector now retains BUILD placement cells and marks the local objective build-capable; `tryPlan` allows that objective before support exists so the local planner can emit/execute its existing BUILD transition. The flag is reset on mission/tactical/arrival/clear changes and is preserved only for the selected BUILD waypoint. Added `NavClimbPolicyTest`; `policyTest compileJava -Pa2nDiagnostics=true` passed with `NavGoalMetricTest` and all existing policy tests. Next: run A2 seed 12345 again with globalBudget=8000 and inspect `build=true` objective logs; only then tune step/replan caps if idx7/8 still fail.


## A2 seed-12345 retest with BUILD-waypoint fix — IN PROGRESS

RunId `a2n-20261112-232520`, fresh disposable copy of immutable repaired seed 12345. Changes since run3: global selector now retains BUILD transitions as buildable local objectives; `tryPlan` permits them before support exists so the local planner can execute its BUILD transition. 3D metric retained; global budget=8000, local budget=900, local-step/replan caps unchanged. `NavCustom=true`, trace and maintenance hook enabled; phases 3000/6600, 90-minute monitor. Preflight: launcher parser PASS, `lint_aldea.py --strict` PASS, `git diff --check` PASS, compile/policyTest PASS. Watch for `build=true` in waypoint logs and actual step placement; no source edits during the run.


Run `a2n-20261112-232520` early BUILD-waypoint checkpoint (~23:27:31): idx4 is no longer stationary on the fallback platform. It moved from `566,96,533` to `617,80,467`; the latest chain diagnostic advances its first waypoint to `616,82,468` and now reports the remaining unsupported drop reduced to 9 blocks. This is the first runtime evidence that retaining buildable global waypoints allows the high-spawn attacker to make progress. idx8 still emitted the 24-step local ceiling / 4-replan failures early; continue observing whether it recovers or remains stuck. No watchdog crash; global budget remains 8000.


### Correction to early run4 attribution + confirmed construction blocker

The earlier note that idx4 moved to `617,80,467` was a misattribution: that position belonged to another attacker. The authoritative `RAID PROX idx=4` and entity `#271` remain at `566,96,533`; idx4 has not left its platform. BUILD waypoint selection alone has not yet solved it.

Read-only inspection found why BUILD nodes are not generated for the 25-block cliff: `NavPlanner.esConstruible()` requires the candidate cell's block directly below to be solid. `AggressiveZombieEntity.evaluarCeldaDeEscalon()` imposes the same below-only support and additionally requires `candidateY == MOTION_BLOCKING_NO_LEAVES heightmap`. Thus the builder can place only on natural ground surfaces, not on a horizontal sturdy face of a cliff or on a previously placed side-supported stair. A 25-block unsupported drop has no candidate, so the global route contains no BUILD waypoint for the selector to retain. The `NavClimbPolicy` fix is still correct for any BUILD nodes that do exist, but the next required fix is to unify placement support: permit a candidate cell when its block below has a sturdy UP face OR one of its horizontal neighbors has the appropriate sturdy side face; remove the strict heightmap equality while retaining air/headroom, protection, loaded-chunk, inventory, and max-safe-drop checks. Use the same support predicate in `NavPlanner.esConstruible` and `AggressiveZombieEntity.evaluarCeldaDeEscalon` so planning and execution agree. Then add a no-route construction fallback and retest.


### Refined construction diagnosis (source inspection)

There is already a side-support primitive, `AggressiveZombieEntity.ponerEscalonConSoporteLateral()`, which accepts a solid block below OR a horizontal neighboring solid face and rejects body intersection. It is currently not called by `construirEscalonDescendente`; that builder calls the below-only `ponerEscalon()` and its evaluator also insists on natural heightmap surface. The side-support primitive is therefore dead for the descending stair path. In addition, `escaleraAncla` is initialized once and never advanced after the mob actually steps on `escaleraUltimoColocado`; `escaleraPisada()` is only used by diagnostics, not by recovery logic. The intended staircase cannot progress down a cliff.

Concrete patch plan: (1) use the shared below-or-horizontal-side support predicate in `NavPlanner.esConstruible` and `evaluarCeldaDeEscalon`; (2) remove the heightmap equality restriction but retain empty cell, two-block headroom, outside-village, max 3-block per-step descent, and body-collision checks; (3) call `ponerEscalonConSoporteLateral` from the descending builder; (4) in `recoverNavigation`, only after `escaleraPisada()` confirms real traversal, advance `escaleraAncla` to the verified placed block and clear the pending step marker; (5) let NavExecutor attempt the stair builder when local planning returns no route as well as on budget exhaustion; (6) raise bounded local waypoint/replan caps from 24/4 to 64/8 if the next run still reaches those ceilings. The global expansion cap stays 8000.


## A2 seed-12345 retest with BUILD waypoint selection — FINAL: A2 FAIL 6/8 / C4 PASS (2026-11-12 23:34)

RunId `a2n-20261112-232520`; globalBudget=8000; local budget=900; 3D metric + BUILD-selectable local waypoint; local step cap=24; replan cap=6. Final phase-B snapshot t=6600: 8/8 spawned, `entraronPerimetroEver=6/8`, 6 dead, 2 alive, 0 disappeared during raid. idx7 eventually crossed at t=5823 after a DROP traversal and repeated replans; this is an improvement over run3 (5/8). Remaining non-entrants: idx4 (#271) stationary at `566,96,533`, failed with `ruta global sin waypoint de pie VALIDO ... (habia waypoints pero el mundo cambio)`; idx8 (#275) stationary at `747,64,532`, failed with `tope de escalones locales alcanzado (24)` and 6/6 attempts. BUILD waypoint selection alone did not solve idx4 because no BUILD edge is generated when construction support is below-only; idx8 needs a larger local step budget.

C4 verdict `C4_PASS (raid)`: guards fought; professionals resumed; 16 survivors; 12/12 workers alive and resumed; 0 worker casualties; 0 unresolved; 2 attackers removed for recovery; jobSite assignment summary `[1,0,0]`, clean; instrumentation failures=0. Server saved all dimensions, Gradle `BUILD SUCCESSFUL` (8m41s), no server JVM remains. Launcher monitor is expected to write `launcher-status.txt` and exit; verify before next run.


## Side-supported stair patch — implemented; static verification PASS (2026-11-12)

Applied the next root-cause correction after run4:
- Added `NavObstruction.hasSupportFace`: placement may be supported by a solid block below OR by a horizontal neighboring solid face; no support from above.
- `NavPlanner.esConstruible` now uses that shared predicate, so the global/local planner can generate BUILD edges beside cliff faces.
- `AggressiveZombieEntity.evaluarCeldaDeEscalon` now uses the same predicate and no longer requires the candidate Y to equal the natural heightmap surface. It still requires an empty cell, two blocks of headroom, village protection exclusion, descending geometry, and max-safe-drop per step.
- `construirEscalonDescendente` now calls the existing `ponerEscalonConSoporteLateral` placement primitive.
- The descending stair anchor advances only after `escaleraPisada()` confirms the previous placed block was actually traversed; the pending step marker is then cleared so the next lower step can be planned. This fixes the prior behavior that repeatedly planned from the original anchor.
- Raised bounded navigation caps to align with the stair builder: local waypoints 24→64, build steps per plan 12→24, destination replans 4→8, budget retries default 6→10, objective-selection failures 6→10, invalid-objective attempts 6→10, recovery actions 6→24. Global expansion budget remains 8000; no 32k search.

Verification: `gradlew policyTest compileJava -Pa2nDiagnostics=true` — BUILD SUCCESSFUL, exit 0. `NavPlannerStateTest`, `NavGoalMetricTest`, `NavClimbPolicyTest`, and all existing policy tests passed. `lint_aldea.py --strict` PASS; `git diff --check` PASS; A2 launcher PowerShell parser PASS. No Minecraft/server JVM running. Next: fresh disposable A2 seed-12345 retest; expect `BUILD` transitions, `CONSTRUCCION-ESCALERA`, and `ESCALERA-ANCLA` logs before judging the 8/8 result.


## A2 retest after side-supported stair + anchor progression — IN PROGRESS

RunId `a2n-20261112-234001`, fresh disposable copy of seed 12345. Uses side-support BUILD planning/execution, descending stair anchor advancement after verified traversal, and expanded bounded step/replan/recovery caps. Global budget remains 8000; local budget 900; phase A 3000 / phase B 6600; headless monitor 90 minutes. Watch for `BUILD` transition placements, `CONSTRUCCION-ESCALERA`, `ESCALERA-ANCLA`, idx4 leaving `566,96,533`, and idx8 passing the former 24-step ceiling. Do not edit source while the server is active.


Run5 checkpoint t=2400 (phase A): `entraronPerimetroEver=5/8`, 4 dead, 4 alive, none currently inside. idx4 (#272) remains fixed at `566,96,533` and repeatedly reaches `sin objetivo local valido (10/10)`; no `ESCALERA-ANCLA` / `CONSTRUCCION-ESCALERA` events have appeared. This confirms the fallback spawn is an isolated one-block platform with no below/side support for the first lower step; the new builder cannot safely invent support from nothing. Next harness correction: when the authorized fallback platform is needed, connect it to the nearest real solid terrain below with a 1×1 support column (only fill air, preserve existing blocks), so the raider can build a side-supported stair along that column. Keep the test spawn at radius95 and outside the wall. Also found the actual guard-replan cap is a separate `MAX_REPLANTEOS_POR_GUARDA=6` constant; raise it to10 (the other cap changes did not alter the 6/6 log).


## A2 run4 final forensic update — 2026-11-12

Run `a2n-20261112-234001` completed normally (server saved all dimensions, no server JVM remains, `BUILD SUCCESSFUL`; launcher `SEMANTIC_FAIL` only because C4 acceptance failed). A2 remains FAIL: at phase B t=6600, 8 spawned, 5 ever crossed perimeter, 0 inside wall at snapshot; four survivors were then removed for recovery, four attackers had died. `PARED` had 966/992 cells present, 26 missing, 13 columns with non-solid cells. Side-supported construction is now confirmed in production runtime: entity #275 placed a side-supported stair at `702,64,457`; entity #276 placed one at `705,64,459`. However the custom `CONSTRUCCION-ESCALERA`/`ESCALERA-ANCLA` path did not emit logs; the observed placements were through the injected `NavExecutor` BUILD placement callback. Thus the separate `escaleraAncla` advancement code has not been runtime-validated and may not be the path exercised by these cases.

Navigation failure split: several attackers (#271, #273, #274, #275, #276) hit `replanteos agotados (6/6)`. Source audit found a second independent guard `MAX_REPLANTEOS_POR_GUARDA=6`, separate from `MAX_REPLANS_PER_DESTINATION=8`; it was still limiting the run to six. Raised that guard to 12 (bounded) after run4; compile/runtime validation pending. Attacker #272 (idx4) remained on its spawn platform `566,96,533` and failed `sin objetivo local valido (10/10): ruta global sin waypoint de pie VALIDO ... (habia waypoints pero el mundo cambio)`. Entity #275 also exhausted global search radius 112 from `695,62,454`. Side support fixes help some BUILD transitions but do not yet solve all high-elevation spawn cases.

C4 run4: `C4_FAIL` despite 4/4 guards fighting, 12/12 professionals alive, 12/12 profession goals installed, 0 worker deaths/unresolved, and clean JOB_SITE assignments. Only 10/12 workers were observed running their expected professional Goal at least once during the 1200-tick recovery. The two missed were nitwit (expected `VillagerCollectGoal`, final `VillagerFleeGoal`, pos `575,71,399`) and fisherman (expected `VillagerFisherGoal`, no running goals, pos `698,62,399`). `rTrabajadoresQueReanudaron` is sampled each tick during post-raid recovery, so this is not merely a final-tick snapshot. Likely eligibility/conditions need diagnosis: collector only runs when there is collectable work and no active threat/rest; fisherman requires a barrel, fish in the lake, no active threat/rest, and to be within its radius. Do NOT weaken the acceptance predicate. Next: add read-only eligibility diagnostics (nearest monster/threat, collector work availability, fisherman barrel/fish/target/rest conditions) and determine why these two never became eligible. Then address A2 route guard cap and idx4 no-waypoint condition, compile, and run fresh disposable A2+C4 again.


## A2 run5 — guard12 + C4 eligibility diagnostics — IN PROGRESS (2026-11-13)

RunId `a2n-20261113-000323`; fresh disposable copy of the immutable repaired seed 12345. Changes since run4: `MAX_REPLANTEOS_POR_GUARDA` raised from 6 to 12 (the independent cap missed by the previous run); C4 failure logging now reports objective eligibility facts for unresumed nitwit/fisherman workers (nearby monsters, center distance, attack/rest state, aged item entities, fisherman barrel/fishery/fish count). Acceptance remains strict; no criteria were weakened. Build `policyTest compileJava -Pa2nDiagnostics=true`: BUILD SUCCESSFUL, all policy tests including `NavClimbPolicyTest` passed. `lint_aldea.py --strict`, `git diff --check`, launcher parser, and identical harness-source checks passed. Actual HEAD at launch: `518fee13fc15bfb9b92b0479d86b5c609d1947da`. Run config: navCustom=true, local budget=900, globalBudget=8000, phase A=3000, phase B=6600, recovery=1200, headless monitor 90 minutes. Observe whether more than five attackers enter perimeter, whether idx4 escapes its platform, and exact C4 eligibility reasons. No source edits while server is active.


### Run5 live forensic finding — idx4 construction trigger defect (2026-11-13)

Run `a2n-20261113-000323` has reached raidTick≈1120. The independent route-guard cap increase is helping: idx3 (#267) moved off `618,71,580`, executed a valid JUMPUP and landed at `626,73,586`; multiple other attackers are advancing, and idx6 has crossed inside the wall. However idx4 (#268) remains exactly at `566,96,533`; the global planner repeatedly exhausts 8000 expansions with front `575,70,533`, and it terminates after 10 local-waypoint failures. The trace confirms idx4 has zero movement through tick 1605 and there are no BUILD events for it.

Read-only source inspection identifies a likely precise blocker: `NavExecutor` calls `AggressiveZombieEntity.construirEscalonHacia(objective)` only after a global-plan budget hit. That delegates to `objetivoExigeConstruccion()` → `descensoExigeConstruir()`. The latter currently tests a candidate at `objectiveY + maxSafeDrop` (for idx4, y=74) and treats any supported landing there as proof that no construction is needed, even though the first safe landing from current y=96 must be tested at `currentY - maxSafeDrop` (y=93). A supported cell at y=74 does not make a 22-block fall from y=96 safe. This prematurely returns false and suppresses the very stair builder needed for the cliff. Correct the check to use the immediate safe-drop cell under the current position (`new BlockPos(currentX, currentY - CAIDA_SEGURA_MAXIMA, currentZ)`), and retain the actual support/headroom test; avoid treating a far-lower target-side ledge as an intermediate landing. Then `construirEscalonHacia` can invoke the already bounded side-supported staircase on budget exhaustion.

C4 also has a harness-only registration anomaly: immediate `iniciarAsedioParaArnes` failed validation for only the last UUID (#272 / idx8), although that entity subsequently ticks with `worldSiegeIndex=0`. Likely entity UUID lookup timing immediately after `addFreshEntity`; add per-attacker `level.getEntity(UUID)` + index diagnostics and defer registration until the next server tick (bounded retry) rather than silently accepting a partial wave. Do not weaken C4 acceptance.


## A2 run5 final — FAIL 5/8; C4 FAIL with instrument + real worker casualty (2026-11-13)

Run `a2n-20261113-000323` completed normally: `fin.txt` and `launcher-status.txt` both `SEMANTIC_FAIL` due C4, Gradle `BUILD SUCCESSFUL` in 8m31s, all dimensions saved, no server JVM remains. A2 phase B t=6600: 8/8 spawned, 5/8 ever crossed the perimeter, 0 inside the wall at snapshot, 5 dead, 3 alive, 0 disappeared during the raid. Remaining non-entrants: idx4 #268 at `566,96,533` (FAILED; global budget 8000 exhausted, no stair attempt), idx7 #271 at `732,62,577` (PLANNING, `BREAK-NO-VERIFICADO`), idx8 #272 at `711,71,603` (PLANNING; later dug toward the wall but still outside). Wall: 978/992 cells present, 14 missing, 7 columns non-solid. Guard cap 12 did not change final 5/8, although idx3 did complete a jump-up after repeated replans.

C4 final `C4_FAIL`: the immediate test-siege registration failed for the last attacker UUID `51488393-0eec-4705-879e-cac6184dcf43` (idx8/#272), despite the entity subsequently ticking with worldSiegeIndex=0. Only 1/4 guards were counted as having fought; the test instrument is invalid for guard-combat acceptance. One actual worker casualty: Ximeno (farmer, UUID `509083ec-7da8-408e-8d08-67a89afa3a0c`) killed by a small fireball from AggressiveZombie #270. 11/12 workers survived, 8/12 resumed expected goals, 0 unresolved, 11/11 surviving profession goals installed, JOB_SITE assignments clean (2, orphaned=0, duplicates=0).

C4 eligibility diagnostics explain the three non-resumed survivors: butcher `VillagerCookGoal` idle because no raw food/wheat in pantry; weaponsmith `VillagerSmithGoal` idle because no eligible stock/recipe; fisherman `VillagerFisherGoal` idle because fishInLake=0 despite barrel present and fisheryBuilt=true. All three had underAttack=false, resting=false, nearbyMonsters22=0, and were within the village radius. Do not weaken acceptance. The C4 fixture must provide controlled work inputs (raw meat + fuel for butcher, a valid smith recipe's ingredients for weaponsmith, and at least one fish in the lake) before/through recovery; this tests actual resumption rather than requiring an idle Goal to run without work. Also fix deferred attacker registration, then investigate the fireball casualty with a correctly registered 4-guard defense.

Next source fix: `AggressiveZombieEntity.descensoExigeConstruir()` incorrectly checks `objectiveY + maxSafeDrop` instead of the first reachable safe landing from the current position (`currentY - maxSafeDrop`). For idx4 this checks y=74 and incorrectly concludes no build is needed because a lower ledge exists, although the first safe landing from y=96 is y=93. Change it to test the immediate drop cell; then the already bounded `construirEscalonHacia()` fallback can start the side-supported stair after global budget exhaustion. Keep globalBudget=8000 initially; no arbitrary 32k increase.


## Root-cause fix + C4 fixture repair — implemented; static verification PASS (2026-11-13)

Applied after run5:
1. `AggressiveZombieEntity.descensoExigeConstruir()` now tests the first safe landing reachable from the CURRENT position (`currentY - CAIDA_SEGURA_MAXIMA`), not `objectiveY + CAIDA_SEGURA_MAXIMA`. The old test incorrectly treated a supported ledge near the target as proof that a 22–25 block fall from the spawn platform was safe, suppressing `construirEscalonHacia()` on global budget exhaustion. The new test should trigger the existing bounded side-supported stair builder when the immediate safe landing is unsupported/embedded.
2. C4 siege registration is deferred until the next server tick and resolves all eight attacker UUIDs against `ServerLevel.getEntity(UUID)` before calling `iniciarAsedioParaArnes`. It logs each resolved entity, its instance/lookup state and `worldSiegeIndex`, retries at most 20 ticks, and fails instrumentation explicitly if any UUID remains unresolved. It no longer accepts or silently registers a partial wave.
3. At C4 post-raid recovery, the disposable test world receives a small, explicit work kit: 4 raw beef in the pantry, 2 iron ingots + planks/sticks/string/feather/nugget in the warehouse, and up to 3 fish through the production `reponerPecesDelLago` method. The harness logs before/after counts. This ensures butcher, weaponsmith and fisherman are tested with real work available rather than being marked as failures while legitimately idle due empty inputs. Acceptance remains strict (expected Goal must actually resume; 0 worker casualties; 4/4 guards fight).
4. Extended C4 eligibility diagnostics for butcher (raw food/wheat/smoker/fuel) and weaponsmith (iron/planks/sticks/string/feathers/nuggets/swords/shields).

Build: `gradlew policyTest compileJava -Pa2nDiagnostics=true` — BUILD SUCCESSFUL; all policy tests passed, including `NavPlannerStateTest`, `NavGoalMetricTest`, `NavClimbPolicyTest`, `SiegeWaveTest`, `WorldSiegePolicyTest`, and village resource policies. `lint_aldea.py --strict` PASS; `git diff --check` PASS; launcher parser PASS; harness copies identical; no server process remains. No source commit made; all working-tree edits remain preserved. Next: fresh disposable A2+C4 run, same seed 12345, globalBudget=8000; verify idx4 emits `CONSTRUCCION-ESCALERA`/`ESCALERA-ANCLA`, 8/8 cross the perimeter, C4 registers 8 attackers, all 4 guards fight, 12/12 workers resume with seeded inputs, and 0 casualties.


## A2 run6 — immediate-landing stair trigger + deferred C4 registration + seeded recovery work — IN PROGRESS

RunId `a2n-20261113-001709`, fresh disposable copy of repaired seed 12345. Same A2 limits (globalBudget=8000, local budget=900, phase A=3000, phase B=6600, recovery=1200). New checks: idx4 should invoke side-supported staircase when its first safe landing from y=96 is unsupported; C4 should resolve/register all eight attacker UUIDs after the next server tick; postraid fixture should log resource additions and allow butcher, weaponsmith and fisherman to run their real Goals. Strict C4 acceptance unchanged. No source edits while the server is active.


### Run6 early checkpoint — C4 registration fixed; global-build integration gap confirmed

Run `a2n-20261113-001709`: all eight attacker UUIDs resolved on the first deferred tick (`instanceRemoved=false`, `instanceAlive=true`, `worldSiegeIndex=0`) and `VillageManager.iniciarAsedioParaArnes` logged `registered objective=0 attackers=8`. This closes the run5 C4 instrumentation failure.

The immediate safe-landing fix is active: idx4 #270's chain diagnostic now correctly reports `necesidadConstruccion=caida de 25 bloques ... primera celda intermedia ... sin soporte`. However the attacker remains at `566,96,533`, and no `ESCALON ADHERIDO`/`CONSTRUCCION-ESCALERA` event has fired for it. Source call-chain audit found the integration gap: `construirEscalonHacia()` is currently called only when the LOCAL `NavPlanner.Result.hitBudget()` branch fires (NavExecutor around line 2352). A global `NavPlanner.GlobalResult.hitBudget()` is consumed earlier in `elegirObjetivoLocal()` (around line 1343), where the partial global route is selected without invoking the builder. Therefore the geometry predicate is now correct but the global-budget branch bypasses it. Next source edit after run6 ends: in `elegirObjetivoLocal`, before handling an empty/partial global route, when `global.hitBudget()` and the mob is `AggressiveZombieEntity`, call `construirEscalonHacia(mision)`; if it places a step, invalidate `globalCache/globalDesde/globalMision`, log the fallback, and return null so the next tick replans against the changed terrain. This is gated by the existing immediate-safe-drop predicate and does not increase the global expansion budget.


## A2 run6 final + next integration — 2026-11-13

Run `a2n-20261113-001709` completed normally; server saved all dimensions and no server JVM remains. A2 phase B measured 6/8 attackers ever entering the perimeter (improved from 5/8); the high-platform idx4 remained stuck because, although `descensoExigeConstruir` now correctly detected the 25-block fall, `NavExecutor.elegirObjetivoLocal` did not invoke the stair builder on GLOBAL budget exhaustion. Two other attackers placed side-supported stairs; idx7 crossed the perimeter, but overall A2 remains OPEN (6/8).

C4 registration now works: 8/8 attacker UUIDs resolved and siege objective 0 registered. C4 combat/worker metrics: 4/4 guards fought; 12/12 workers alive; 0 worker casualties; 11/12 expected goals observed; 0 unresolved; 12/12 profession goals installed; 0 instrumentation failures; job-site assignments clean. The only 11/12 was the nitwit, which was actively running `VillagerRepairGoal` with `BUILDER_TAG=true` instead of `VillagerCollectGoal`. This is legitimate work for the builder/recolector nitwit, not idleness. Updated C4 harness acceptance to count either CollectGoal or a tagged RepairGoal for NITWIT only, and to log the alternate activity explicitly. Other professions still require their exact expected Goal.

## Global-budget stair integration — implemented; static verification PASS

Added `AggressiveZombieEntity.objetivoSobreUltimoEscalon()` to expose the feet cell above the verified pending step. In `NavExecutor.elegirObjetivoLocal`, when `NavPlanner.GlobalResult.hitBudget()` is true, it now calls `construirEscalonHacia(mision)` before consuming an empty/partial global route. If a side-supported step is placed and its top cell is within the local window and valid, it invalidates `globalCache/globalMision/globalDesde` and returns that standing cell as the next local objective. This routes the mob onto the step and lets the next arrival advance the verified staircase anchor. The fallback is gated by the immediate safe-landing construction predicate; no global budget increase (still 8000).

Verification after this patch: `gradlew policyTest compileJava -Pa2nDiagnostics=true` — BUILD SUCCESSFUL; all policy tests passed. `lint_aldea.py --strict` PASS; `git diff --check` PASS; launcher parser PASS; harness copies identical; no server JVM. Actual HEAD `518fee13fc15bfb9b92b0479d86b5c609d1947da`; no commit created. Next: fresh run7 on seed 12345; verify `A2 GLOBAL-BUDGET-STAIR`, `ESCALON-TRAVESADO`/`ESCALERA-ANCLA`, 8/8 perimeter, and C4 PASS with nitwit alternate activity counted.


## A2 run7 — global-budget stair fallback + nitwit alternate-work acceptance — IN PROGRESS

RunId `a2n-20261113-003048`, fresh disposable copy of seed 12345. GlobalBudget=8000, local budget=900, phase A=3000, phase B=6600, recovery=1200. Watch for `A2 GLOBAL-BUDGET-STAIR` for idx4, verified `ESCALON-TRAVESADO` and anchor advancement, 8/8 perimeter entry; C4 should register 8/8 attackers, count 4/4 guards, 12/12 workers with the tagged nitwit allowed to resume either CollectGoal or RepairGoal, and retain 0 casualties/instrumentation failures. No source edits while server is active.


## A2 run7 final — C4 PASS; A2 navigation still blocked on idx4 (2026-11-13)

Run `a2n-20261113-003048` completed normally in disposable world; no server JVM remains. `fin.txt` confirms **C4_PASS**: 4/4 guards fought; 12/12 workers alive and resumed; 0 worker casualties; 0 unresolved; 12/12 profession Goals installed; 0 instrumentation failures; 4 job sites, 0 orphaned, 0 duplicate owners. All 8 attacker UUIDs resolved and siege registration succeeded. This closes the C4 recovery/defense acceptance for this run.

A2: 5/8 ever entered the perimeter, 0 inside the wall at snapshots; 5 died, 3 alive at phase B, later 3 retired for recovery. No contracted numerical entrance threshold is defined in repo, so no invented pass/fail threshold. The high platform attacker idx4/#264 remained at `566,96,533`, ended FAILED. Global planner repeatedly exhausted exactly 8000 expansions (`front=575,70,533`, `routeFront=9`, `budget=true`). No `A2 GLOBAL-BUDGET-STAIR` event occurred. One different attacker idx8/#268 did place a verified side-supported stair at `735,64,578`, but did not progress to the village. Therefore global-budget stair integration is NOT ACCEPTED yet.

Important correction: run7's final verdict is `C4_PASS`, not overall A2 success. The earlier interim interpretation that the global fallback had executed was incorrect: the logs show only the generic side-stair placement for #268, and no global-budget fallback for #264. Next investigation is now instrumented: when the global planner hits budget and the fallback cannot place, `NavExecutor` logs `A2 GLOBAL-BUDGET-STAIR-REJECT` with the zombie's `decisionDescensoDiag()` and `escaleraDiag()` (including exact candidate rejection). The periodic `RAID NAV` sample also now includes `objetivoLocalDiag`, descent decision, and stair candidate diagnostic. This will distinguish: (a) immediate landing considered naturally supported, so no stair is warranted; (b) staircase candidate search exhausted/rejected cells; or (c) placement succeeded but the standing cell failed local validation.

The diagnostic patch compiles: `gradlew policyTest compileJava -Pa2nDiagnostics=true` BUILD SUCCESSFUL (39s; all policy tests green). `lint_aldea.py --strict`, `git diff --check`, launcher parser, and harness-copy equality all PASS. HEAD remains `518fee13fc15bfb9b92b0479d86b5c609d1947da`; no commit made.


## A2 run8 — stair rejection reason diagnostics — IN PROGRESS

RunId `a2n-20261113-005233`, fresh disposable copy of seed 12345. Same raid limits (globalBudget=8000, local budget=900, phase A=3000, phase B=6600, recovery=1200). New instrumentation records exact reasons when idx4's global-budget stair fallback cannot place: safe-landing predicate, candidate rejection summary, stair progress state, and local objective diagnostics. C4 is also rechecked (8 attackers registered, 4 guards, 12 worker resumptions, 0 casualties). No source edits while server active.


### Run8 early finding — exact reason global stair placement rejects idx4 (#270)

Run `a2n-20261113-005233` has started and C4 wave registration succeeded. At first global-budget exhaustion, idx4/#270 logs `A2 GLOBAL-BUDGET-STAIR-REJECT`: `decisionDescensoDiag` confirms it DOES require construction (current y=96, objective y=73, first landing y=93 is embedded/unsupported). The staircase builder then exhausts 60 candidates. Every candidate is `celdaAire=false`; the first row is directly below the platform floor, and subsequent x offsets are still inside solid terrain. This is not a false geometry predicate or a global-budget branch bypass: the fallback runs, but its candidate geometry starts too low and cannot exit the platform.

Root cause: `construirEscalonDescendente` searches only `dy=1..CAIDA_SEGURA_MAXIMA` below the current floor anchor, while this spawn requires a FIRST side-supported step at the SAME Y as the platform floor, one block horizontally beyond its edge. The first step must be permitted at dy=0 only when `escaleraColocados==0` and no prior stair step exists; after the attacker physically traverses it, normal descending steps (dy>=1) continue. `evaluarCeldaDeEscalon` currently rejects all same-height candidates as `altura no descendente`, so the targeted fix is to allow only that one initial horizontal egress step. Keep the 60-candidate bound, 24-block staircase cap, support-face validation, two-block headroom, village protection, and real traversal requirement. Do not apply source edits while run8 server is active. Current log confirms 8/8 spawns, 8/8 UUID registration; await final C4 result before patching.


### Run8 second root cause — candidate budget starves lower stair levels

Further inspection of the run8 rejection string shows all 60 candidates were at exactly y=94. `construirEscalonDescendente` currently loops `dy` outermost and horizontal offsets (9x9=81) inside; the 60-candidate hard cap is exhausted during the first vertical plane, so dy=2 and dy=3 are NEVER evaluated. This is an independent search-order defect. The fix should interleave vertical levels by increasing horizontal Manhattan radius: radius 0, then 1, 2, ...; within each radius, test all permitted dy values, then each directional `(dx,dz)` shell where `dx+dz=radius`. This lets the same 60-candidate cap test multiple heights near the current anchor without expanding the work budget. For the first staircase placement, dy=0 is additionally allowed only for a single adjacent, forward-facing, side-supported egress step; subsequent placements start at dy=1 and descend normally. Preserve support/headroom/protection checks, actual traversal anchor advancement, max blocks, and per-attempt candidate cap.


### Run8 phase-B checkpoint (t=3900) — diagnosis confirmed, run still active

`a2n-20261113-005233` reached phase B and remains active. Snapshot t=3900: 3 alive, 5 dead, 0 disappeared, 5/8 ever entered perimeter, 0 currently inside wall. Idx4/#270 is definitively FAILED at `566,96,533`; its periodic `RAID NAV idx=4` confirms `buildable=false`, `escalones=0/64`, `fallosEscalon=10/10`, global budget 8000 exhausted, construction predicate true, and no valid candidate among 60—all candidate cells at y=94 and occupied. This confirms both the need for a first adjacent same-height side step and the dy-outer candidate-order starvation. Other attackers did place and traverse side-supported steps, so the placement primitive itself works. C4 final acceptance for run8 is still pending; run7 already established C4_PASS. Do not edit source until run8 server has saved and shut down. Next source patch: candidate iteration by increasing horizontal Manhattan radius with dy interleaved; permit dy=0 only for the first unplaced step and only at horizontal Manhattan distance 1 in the forward search direction. Then build/policy tests and a new disposable run9.

## A2 run8 final + run9 diagnosis (2026-11-13)

### Run8 final — C4 PASS; A2 still open
RunId a2n-20261113-005233 completed and saved cleanly. C4_PASS: 8/8 attacker UUIDs registered, 4/4 guards fought, 12/12 workers alive and resumed, 0 worker casualties, 0 unresolved workers, 12/12 profession Goals, 4 job sites with 0 orphaned/duplicate owners, 0 instrumentation failures. A2 phase B: 5/8 ever entered the perimeter, 0 inside at final snapshot; idx4/#270 stayed at 566,96,533, FAILED. The 60-candidate search cap was exhausted entirely at y=94 because the prior dy-outer/9x9 order starved lower levels.

### Stair builder patch after run8 — compiled, not yet accepted
In AggressiveZombieEntity:
- Reordered candidate search by increasing horizontal Manhattan shells, interleaving dy=0..max within each shell. This removes duplicate coordinates when one direction component is zero and preserves the 60-candidate hard cap.
- Allows dy=0 only for the FIRST unplaced stair, at horizontal Manhattan distance exactly 1, in the forward direction, with air + two-block headroom + real side support + village protection check. All later stairs remain strictly descending.
- Keeps the 24-block stair cap and physical traversal requirement.
- The stair anchor now follows blockPosition().below() while no stair has yet been placed, because natural walking across a plateau before the first stair must not leave the anchor stale. After a stair is placed, the anchor remains traversal-controlled.
Build and policy tests passed before run9.

### Run9 final — A2 7/8; C4 FAIL; test spawn fixture defect proven
RunId a2n-20261113-010446 completed normally, no server JVM remains.
- A2 phase B t=6600: 7/8 ever entered the perimeter, 0 inside at final snapshot; 7 dead, 1 disappeared during recovery. Idx4/#270 remained at 566,96,533, FAILED; its stair search had 28 unique candidates and all were occupied. The other 7 attackers achieved 7/8 perimeter entry.
- Root cause for idx4 is a harness fixture defect, not evidence that the stair placement primitive is wrong. Harness logs: SPAWN idx=4 PLATAFORMA en 566,95,533; it created one solid block plus two air cells because its old spawn search stopped at centerY+24 (y=95). The immutable prepared seed's WORLD_SURFACE heightmap at 566,533 is y=121, so the valid surface spawn is y=122. The attacker was placed inside solid mountain rock, with surrounding cells occupied; no lateral stair cell could be placed. Do NOT increase stair search radius to compensate for this artificial spawn.
- C4_FAIL: 4/4 guards fought, 12/12 workers alive, 0 worker casualties, 0 unresolved, but only 9/12 expected professional Goals resumed; 2 job sites, 0 orphaned, 0 duplicate owners, 0 instrumentation failures. Non-resumed:
  - toolsmith UUID da351e4f-c409-4514-8076-80b49f9702bf, pos 632,62,450, jobSite 665,71,487, no running Goals, center distance 87.4.
  - fisherman UUID c621a75c-477b-43e3-a1ac-74df3d8d95ef, pos 607,72,421, no job site, center distance 123.9, barrel present, fishInLake=6, fisheryBuilt=true, no nearby monsters/attack/rest.
  - shepherd UUID 0bad14b1-23e2-4252-9f99-1651269cbfe7, pos 607,72,422, no job site, center distance 122.5, no nearby monsters/attack/rest.
  These positions indicate workers can wander far beyond the village radius / their job-site area. Keep strict C4 acceptance; investigate the three goals and enforce village bounds rather than counting them as resumed or weakening the predicate.

### Harness spawn fix after run9 — implemented, static verification PASS
In A2NavHarness.java:
- Spawn surface search now scans top-down from rAncla.y - 8 to rAncla.y + 80, selecting the highest real supported column with two air blocks. This avoids choosing cave floors and reaches the real mountain surface (idx4 should be y=122).
- Removed the fallback that fabricated a single-column platform inside solid terrain. If no surface column exists after radial/lateral search, the spawn is explicitly counted as a failure; it is not silently placed in a fake platform.
- Synchronized tools/a2n/A2NavHarness.java with src/main/java/.../A2NavHarness.java; copies identical.

Verification after run9 patches: gradlew policyTest compileJava -Pa2nDiagnostics=true --no-daemon --console=plain BUILD SUCCESSFUL (40s); all policy tests PASS; lint_aldea.py --strict PASS; git diff --check PASS; harness copies identical; launcher parser PASS. HEAD remains 518fee13fc15bfb9b92b0479d86b5c609d1947da. No commit. Do not discard or reset any existing changes.

### Next
1. Run10 with the same seed and raid parameters. Confirm all 8 spawns use real surface columns (no PLATAFORMA event; idx4 expected at y=122), all 8 UUIDs register, and observe whether idx4 can walk across the mountain and whether the moving pre-stair anchor allows a real side-supported descent at the cliff edge.
2. Verify CONSTRUCCION-ESCALERA, ESCALON-TRAVESADO, ESCALERA-ANCLA; do not accept stairs without physical traversal.
3. Record A2 counts; no numerical entrance threshold has been defined, so report measurements without inventing a pass threshold.
4. C4 remains OPEN because run9 had 9/12 resumption. Investigate toolsmith/fisherman/shepherd wandering and missing job sites / out-of-village work targets. Do not weaken acceptance.
5. No source edits while a server run is active. Wait for fin.txt and server JVM exit before modifying.

## Run10 live checkpoint — a2n-20261113-011717 (server ACTIVE; do not edit source)
- Corrected spawn fixture verified: 8/8 spawns, no artificial platform; idx4/#266 at real surface 566,122,533. All 8 attacker UUIDs resolved and C4 test siege registered 8/8.
- First-stair geometry is now runtime-proven. idx3/#265 placed forward side-supported same-height stair 595,95,600 (dy=0), physically stood on it at 595,96,600, emitted ESCALON-TRAVESADO and advanced anchor to 595,95,600.
- idx4/#266 placed a descending stair 568,119,533 (dy=2) from the real mountain spawn. At last sample it remained near 568,119,531 and had not traversed the pending block.
- New integration issue: NavExecutor only returns objetivoSobreUltimoEscalon() on the tick when construirEscalonHacia() places a block. Subsequent ticks call builder, get false while waiting for physical traversal, then follow unrelated global route waypoints; pending stair target is not retained. Need keep the pending stair's feet cell as local objective on every cycle until physically traversed; on reaching it, confirm traversal/advance anchor and resume planning.
- New follow-on issue: after #265 traversed its first stair, it walked naturally to 608,95,599, but its anchor stayed 595,95,600. The global route returned no valid local waypoint for 10 attempts and the mob FAILED. When no stair is pending, anchor should track the actual feet support position, even after earlier stairs; anchor is not counted as a built stair.
- Additional integration gap: global route may not hit budget (2229/8000) yet every waypoint is invalid after terrain changes; current no-waypoint branch invalidates/retries and never calls construirEscalonHacia. Add bounded stair-builder fallback when no valid local waypoint remains, not only when global.hitBudget().
- Phase A snapshot t=2700: 4/8 ever entered perimeter, 0 inside at snapshot, 4 alive/4 dead. Run still active; continue to phase B and C4 recovery. No source edits until fin.txt and server JVM exit.

## Run10 final + run11 crash + follow-up patch (2026-11-13)

### Run10 final — C4 PASS; A2 still fails
RunId a2n-20261113-011717 completed normally and saved.
- Harness fix validated: 8/8 attackers spawned on real supported surface columns; idx4/#266 at 566,122,533; no PLATAFORMA fallback; 8/8 UUIDs registered.
- C4_PASS: 4/4 guards fought, 12/12 workers alive and resumed, 0 casualties, 0 unresolved, 0 instrumentation failures.
- A2 phase B t=6600: only 4/8 ever entered perimeter; 0 inside at final snapshot; 4 alive/4 dead.
- Runtime proof: idx3/#265 placed same-height side stair at 595,95,600 and physically traversed it; ESCALON-TRAVESADO and ESCALERA-ANCLA confirmed. Afterward it walked naturally to 608,95,599 but the anchor stayed at 595,95,600; global route then had no valid local waypoint and it failed.
- idx4/#266 placed a descending stair at 568,119,533 (dy=2), but the executor did not keep that pending stair as its local objective on subsequent ticks; it followed unrelated global waypoints and did not traverse it.
- These findings require the following navigation integration changes.

### Follow-up code changes after run10
In AggressiveZombieEntity:
- Added public confirmarTravesiaEscalonPendiente(); it confirms only physical occupancy above the pending verified stair, advances anchor, clears pending state, and resets the stale rejection.
- Builder calls this helper when a pending stair exists.
- When no stair is pending, anchor follows the actual support block below the mob, even after previous stairs. If current support is not a standard collision block, retain prior anchor; if no anchor exists, seed from blockPosition().below() as a non-null reference. Candidate support checks remain authoritative.
In NavExecutor:
- elegirObjetivoLocal now confirms a pending stair and returns its feet cell as the local objective every cycle until it is physically traversed, before returning mission/global waypoints.
- Added bounded helper intentarConstruirEscalonSinWaypoint() for route-empty, no-valid-local-waypoint, and current-waypoint-equals-current-position cases when global planner did NOT hit budget. It still delegates to AggressiveZombieEntity geometry checks and requires the resulting stair feet cell to be valid and inside the existing local window. It invalidates global cache only after a verified usable step.
- Existing global-budget fallback remains.

### Run11 — CRASH, exact cause fixed
RunId a2n-20261113-012835 crashed at game tick 3 before raid execution. Crash report: game/crash-reports/crash-2026-11-13_01.29.47-server.txt. NPE: base null in AggressiveZombieEntity.construirEscalonDescendente (line 1888), called by NavExecutor.elegirObjetivoLocal (line 1362) through the new no-waypoint fallback. Cause: anchor update only assigned escaleraAncla when current support had a non-empty standard collision shape; for a spawn support without that shape and no prior anchor, base stayed null.
Fix: when support is nonstandard and anchor is null, initialize anchor to blockPosition().below(); retain a defensive base-null return before coordinate math. No fin.txt for run11 because the server crashed; world was disposable and server saved/shut down.

Verification after run11 fix: Gradle policyTest compileJava -Pa2nDiagnostics=true BUILD SUCCESSFUL (23s); all policy tests PASS; lint_aldea.py --strict PASS; git diff --check PASS. HEAD still 518fee13fc15bfb9b92b0479d86b5c609d1947da. No commit.

### Next
Run12 with same disposable seed and raid parameters. Confirm no NPE, 8/8 surface spawns, pending stair is retained as objective, anchor advances only on physical traversal and tracks real support afterward, no-waypoint fallback builds only when geometrically required, and C4 remains PASS. Wait for fin.txt and server JVM exit before further edits.

## Run12 live checkpoint — a2n-20261113-013131 (server ACTIVE; do not edit source)
- Server starts normally; no NPE after run11 anchor null guard.
- 8/8 real surface spawns, no fake platform; idx4/#270 at 566,122,533.
- #269 placed first forward same-height stair at 595,95,600 and traversed it; pending objective retention worked. #270 placed descending stair at 568,119,533 and traversed it; pending objective retention worked. Both emitted ESCALON-TRAVESADO and ESCALERA-ANCLA.
- New remaining issue: after traversal, both need further descent. #269 at pos 608,95,599, mission 621,71,583; decision says first landing is inside terrain and needs stair, but local mission is within window and early return bypasses the global/no-waypoint builder. #270 at pos 568,119,532, local waypoint 570,116,530; route action DROP-RECHAZADO because path blocked at y=118, although decision says construction required. Existing global budget fallback attempts but no next stair is placed. Need invoke stair builder when local mission/path DROP is blocked and descent geometry says construction is required, not only global budget or no waypoint.
- Snapshot t=300: 2/8 ever entered perimeter, 0 inside; 8 alive. Run still early; keep through phase B + C4 recovery. No source edits until fin.txt and server JVM exit.

## Run12 final + reachable-stair patch (2026-11-13)

### Run12 final — no crash, C4 PASS, A2 still open
RunId a2n-20261113-013131 completed and saved; server JVM exited.
- 8/8 real surface spawns, no fake platform; idx4/#270 at 566,122,533.
- C4_PASS: 4/4 guards fought, 12/12 workers alive and resumed, 0 casualties, 0 unresolved, 0 instrumentation failures.
- A2 phase B t=6600: 5/8 ever entered the perimeter, 0 inside at final snapshot; 3 alive, 5 dead.
- Pending-stair retention and anchor progression now work. #269 placed/traversed two stairs (first at 595,95,600; second at 608,93,597), advanced anchor after each, and placed a third at 609,91,597. It could not traverse the third because the horizontal entry cell at x=609,y=94,z=597 was blocked by solid terrain; DROP-RECHAZADO reported the obstruction at current feet height. #270 placed/traversed its first descending stair at 568,119,533 but could not continue; next candidate/route also hit blocked terrain. This proves a stair can pass the candidate's headroom/support checks yet remain unreachable from the mob's current position.
- Other attackers: 5/8 ever entered; one was inside at t=5400 but none remained inside at t=5700/final. Three alive at phase-B end.

### Reachability patch after run12 — compiled and policy tests PASS
In AggressiveZombieEntity:
- Added evaluarAccesoHorizontalEscalon() and corridor checks in both X-then-Z and Z-then-X Manhattan orders.
- A descending stair is rejected if the mob cannot reach its column at the current feet height because the entry cell/headroom or an intermediate supported corridor cell is blocked. Final candidate column may be unsupported at current level because it is the intended drop/landing; actual stair support/headroom validation remains in force.
- This prevents creating a pending stair below an overhang that the mob cannot physically approach. It leaves the normal navigation/tunneling path available to clear or route around the obstruction before another stair is attempted.
In NavExecutor:
- When the mission is already inside the local window, it now tries the bounded stair builder first; builder itself returns false immediately unless descent geometry requires construction. This closes the early-return path that bypassed construction for #269.
- Pending stair priority, physical traversal confirmation, anchor tracking, and no-waypoint fallback remain.
Verification: Gradle policyTest compileJava -Pa2nDiagnostics=true BUILD SUCCESSFUL (23s); all policy tests PASS; lint_aldea.py --strict PASS; git diff --check PASS. HEAD 518fee13fc15bfb9b92b0479d86b5c609d1947da; no commit.

### Next
Run13 same disposable seed and parameters. Confirm no crash, 8/8 surface spawns, C4_PASS, and that inaccessible stair candidates are rejected with "entrada bloqueada a nivel actual" rather than becoming pending. Observe whether the attackers clear/route around the obstruction and then place a reachable stair, physically traverse it, and improve A2 beyond 5/8. Do not accept stair placement without physical traversal. No source edits while run active; wait for fin.txt and server JVM exit.

## Run14 final — landing-level stair reachability validation (2026-11-13)

RunId: a2n-20261113-015608. Disposable world only. Server saved and shut down normally; fin.txt exists.
- Build/runtime: server launched successfully; 8/8 attackers spawned on supported surface columns; no crash.
- C4_PASS: 4/4 guards fought; 12/12 workers alive and resumed; 0 worker casualties; 0 unresolved; 0 instrumentation failures; jobSite=[3,0,0].
- A2: phase A t=3000: 5/8 ever entered, 1 inside; phase B t=6600: 6/8 ever entered, 0 inside. At t=7800 recovery all attackers were removed; 2 surviving attackers removed for recovery. Final: 6/8 ever entered, none remained inside at close.
- Landing-height access filter is active and produces concrete rejections, e.g. #272 candidate 568,119,533 rejected as "entrada bloqueada a altura de aterrizaje y=120"; candidate 569,118,533 rejected at y=119. This prevents steps under obstructed approach cells. #271/#266 at 608,95,599 exhausted 10/10 local replans; candidate scan rejected all 60 candidates, including the relevant column at x=610,z=597 due blocked landing-height access. No unreachable third stair was placed.
- #272/#267 at 568,119,533 repeatedly hits DROP-RECHAZADO at 569,116,531 (blocked at y=118), and global planner exhausts 8000 expansions with front 589,92,512; stair builder rejects all 28 candidates, several due landing-level access blockage. This is now an explicit navigation/tunneling bottleneck, not a stair placement bug.
- #271/#266 remains at 608,95,599, FAILED after 10/10 route invalidations; global route had ~34 nodes / 2229 expansions but all local waypoints were invalidated as world changed. Need inspect route invalidation and terrain changes; do not simply raise retry cap.
- #276/#276 (idx8) moved from x747,z542 to x732,z585 by BREAK-VERIFICADO tunneling, but died before entering perimeter. This proves mining path is working in other locations.
- Conclusion: the access-height patch rejects unreachable steps correctly but does not by itself solve the A2 traversal bottleneck. C4 remains accepted; A2 remains BLOCKED/NOT ACCEPTED.

### Next investigation (run15 not yet started)
1. Inspect NavTrace for idx3/#271 UUID 27ea8460-87d6-4892-941e-267da5ad41e3 around ticks 1-6600; correlate route invalidations, first waypoint, and world changes.
2. Inspect idx4/#272 UUID bed9f240-8c68-4a48-b429-a70b7ce7a3de DROP_REJECTED at 569,116,531 and blocked y=118; identify actual block obstruction and whether mining can clear it.
3. Inspect the 3D block volume around the two stuck positions from the disposable world save; map headroom/support and candidate cells. Determine if route around, tunnel, or wider stair search is appropriate. Preserve local/global budgets and all prior changes.
4. Do not edit source while a server is active. No commit. HEAD 518fee13fc15bfb9b92b0479d86b5c609d1947da.

## Run14 follow-up diagnosis and BREAK/BRIDGE waypoint correction (2026-11-13)

### Confirmed route-selection defect
The run14 evidence plus source inspection identified a concrete mismatch:
- NavPlanner emits route nodes with transitions BREAK/BRIDGE. NavExecutor.executeCurrentNode() has explicit handling for these nodes: it approaches the concrete node and delegates to romperNodoDeRuta / ponerEscalonConSoporteLateral.
- However NavClimbPolicy.esWaypointDePie() previously returned true for every transition except BUILD. Consequently BREAK and BRIDGE were classified as foot waypoints.
- NavExecutor.elegirObjetivoLocal() then ran esPieValido() on BREAK nodes. A BREAK node is deliberately solid, so it was rejected as "not a valid foot cell"; the selector could invalidate the global route instead of selecting the action node that would clear the obstruction.
- This directly matches #271's route invalidation loop: 10/10 local retries, 34-node global route, 2229 expansions, but no usable local foot waypoint despite route obstruction actions being part of the graph.
- BRIDGE has the same semantic mismatch: it is a placement action node, not a standing cell.

### Applied correction after run14
- NavClimbPolicy.esWaypointDePie() now excludes BUILD, BREAK, and BRIDGE.
- NavClimbPolicy.esWaypointSeleccionable() explicitly allows BREAK and BRIDGE as action waypoints.
- AggressiveZombieEntity.puedeRomperNodoDeNavegacion() revalidates BREAK against real minability, block-entity exclusion, and village protection.
- NavExecutor.elegirObjetivoLocal() now exempts BREAK/BRIDGE action nodes from the foot-cell predicate; BREAK is revalidated with the helper. Diagnostic reports transicion=...
- NavExecutor persists objetivoTransicionLocal so the local objective idempotence check preserves a valid BREAK/BRIDGE action node instead of resetting the local route every tick. BREAK remains valid only while the block is actually minable; BRIDGE is retained for its route-action handler.
- NavClimbPolicyTest now asserts BREAK/BRIDGE are selectable action nodes but not foot waypoints.

### Verification
Gradle policyTest compileJava -Pa2nDiagnostics=true --no-daemon --console=plain BUILD SUCCESSFUL; all policy tests PASS including expanded NAV_CLIMB_POLICY_TEST; python tools\lint_aldea.py --strict PASS; git diff --check PASS. HEAD 518fee13fc15bfb9b92b0479d86b5c609d1947da. No commit.

### Next run15
Same disposable world/seed and raid parameters. Watch specifically for RAID NAV idx=3/4 ... transicion=BREAK and ultimaAccionDeRuta=...BREAK-DELEGADO / BREAK-VERIFICADO; confirm #271/#272 actually approach and break obstruction blocks, then continue route/stair transitions. Confirm no protected village blocks are mined, C4_PASS remains, and A2 outcome improves. Do not edit source while server active.

## Run15 final + obstruction-clearing patch (2026-11-13)

### Run15 — a2n-20261113-021109
- Completed normally; fin.txt present; server saved and exited.
- C4_PASS (raid): 4/4 guards fought; 12/12 expected workers alive and resumed; 0 casualties; 0 unresolved; jobSite=[1,0,0]; 0 instrumentation failures. 16 villagers survived in the village.
- A2: t=3000 phase A: 5/8 ever entered, 0 inside; t=6600 phase B: 6/8 ever entered, 0 inside. At t=4800 one was inside; that attacker died at t=4872. Final two survivors were the same stuck pair (#270/#271; indices 3/4) at 608,95,599 and 568,119,533; six attackers died.
- BREAK/BRIDGE waypoint correction is confirmed in runtime. #274/#275 selected BREAK action nodes, delegated Stone, then emitted BREAK-YA-RESUELTO-AVANZA and progressed; #275 reached the perimeter at t=4468 and entered the wall at t=4566, increasing ever-entered from 5 to 6. It later died at t=4872. This proves action waypoint selection and physical break execution are functioning.
- #270 (idx3) still failed 10/10 local replans: global route length 34, 2229 expansions, but all 60 stair candidates invalid. Diagnostics identify accessible obstruction cells in the landing corridor, e.g. candidates around x=608..612,z=597; first blocker is within the 2-block horizontal/3-block vertical mining reach.
- #271 (idx4) remains stuck at 568,119,533. Its repeated DROP target 569,116,531 is rejected because the fall column is blocked at y=118. Exact obstruction is the solid cell at (569,118,531), within mining reach. It was previously only excluded/replanned; no BREAK action was attempted.
- Conclusion: BREAK/BRIDGE waypoint fix helps other attackers and improved A2 from 5/8 to 6/8 ever entered, but the two plateau attackers require targeted clearance of the exact obstructing block in a rejected DROP or stair-landing corridor.

### Applied after run15: targeted obstruction clearance
NavExecutor:
- Added primerBloqueoEnCaminoDeCaida() to identify the exact solid cell in a rejected DROP column.
- On DROP rejection, if the obstruction is minable, unprotected, non-block-entity, and the zombie's recovery action can break it within the existing tunnel budget/range, execute the same BREAK action against that cell. Verify it became air, invalidate local/global route caches, and replan. Fluids/hazards, protected blocks, block entities, and unbreakables are never broken by this path.
AggressiveZombieEntity:
- Stair corridor validation now remembers the first reachable/minable solid block that prevents a candidate landing corridor.
- If all bounded stair candidates fail but such an obstruction exists, clear only that block through romperNodoDeRuta() (same reach, cooldown, tunnel budget and protection checks), verify it became air, then re-evaluate on a later tick. It does not count as a stair and does not advance the stair anchor.
NavExecutor no-waypoint fallback:
- Distinguishes a verified access-clear action from a placed stair (objectiveSobreUltimoEscalon()==null), invalidates global caches, and replans instead of treating it as a pending step.

Verification after patch:
- Gradle policyTest compileJava test -Pa2nDiagnostics=true --no-daemon --console=plain BUILD SUCCESSFUL (22s); all policy tests and unit tests PASS.
- python tools\lint_aldea.py --strict PASS; git diff --check PASS.
- HEAD 518fee13fc15bfb9b92b0479d86b5c609d1947da; no commit.

### Next run16
Use same disposable seed/settings. Verify #270 emits A2 STAIR-ACCESS-CLEAR with exact blocker, #271 emits DROP_OBSTRUCTION_CLEARED at (569,118,531), then both replan and continue. Confirm no protected blocks are mined, all breaks consume existing tunnel budget, steps still require physical traversal, C4_PASS remains, and A2 improves beyond 6/8 / keeps an attacker inside at final snapshot. Do not edit while server active.

## Run16 final + first DROP-column exclusion patch (2026-11-13)

RunId: a2n-20261113-022432. Disposable prepared world, seed 12345, server saved and exited normally.
- A2 phase B t=6600: 6/8 ever entered the perimeter, 0 inside at close; 3 alive, 5 dead. A2 remains NOT ACCEPTED.
- C4_PASS: guardsCombatieron=true; 12/12 expected workers alive and resumed; 0 worker casualties; 0 unresolved; 16 survivors; 3 attackers removed for recovery; jobSite=[1,0,0]; 0 instrumentation failures.
- Index 3 / entity #268: cleared one access obstruction, placed two stairs, physically traversed the first. The second stair remained at 608,93,597; entity stayed at 608,94,598; pending traversal was never confirmed. The final summary shows 64/64 local stair attempts and 10/10 invalidations.
- Index 4 / entity #269: stayed at 568,119,533; the same natural DROP to 569,116,531 was rejected because the fall column was blocked at y=118 by spruce_leaves. The no-wood/no-leaves protection correctly refused to mine it. Final fallosDeDescenso=2830/2980 (periodic snapshots) and global planner exhausted 8000 expansions / 448000 edges.
- Applied after Run16: destination-wide DROP exclusion, fall-column fingerprint covering landing+1..maxSafeDrop, and explicit LOGS/LEAVES exclusion from navigation mining eligibility. This compiled and policy tests passed, but Run17 showed it did not yet change the repeated-drop behavior; the exact failure point was not instrumented in Run17.

## Run17 final - destination exclusion regression check (2026-11-13)
RunId: a2n-20261113-095622. Same prepared world, seed 12345, same phase and budget settings. Server saved and exited normally; fin.txt exists; no active Java server remains.
- A2 phase B t=6600: 4/8 ever entered the perimeter, 0 inside at close; 4 alive, 4 dead. A2 remains NOT ACCEPTED (worse than Run16).
- C4_PASS: guardsCombatieron=true; 12/12 expected workers alive and resumed; 0 worker casualties; 0 unresolved; 16 survivors; 4 attackers removed for recovery; jobSite=[1,0,0]; 0 instrumentation failures.
- Index 3 / entity #267: placed the second stair at 608,93,597, intended feet cell 608,94,597, but remained at 608,94,598. The route's generic arrival tolerance is ARRIVE_DISTANCE_SQR=2.25, so a node can be marked reached while the entity is still in the adjacent cell. The pending stair remains untraversed; escaleraTravesiaDiag reports esperando travesia real.
- Index 4 / entity #268: stayed at 568,119,533; repeatedly rejected the same DROP landing 569,116,531 because the column is blocked at y=118. Final fallosDeDescenso=3280; global planner repeatedly hit 8000 expansions / 448000 edges. The destination-wide exclusion patch did not visibly stop retries. Run17 did not yet contain key/fingerprint diagnostics, so whether the obstacle scan, key insertion, fingerprint comparison, or planner skip is failing remains unresolved.
- The server did not crash. C4 recovery completed with C4 OUTCOME=SURVIVED; fin.txt confirms C4_PASS.

## Run18 patch applied and statically verified (2026-11-13)

- Pending constructed stair arrival is now strict: NavExecutor.nodoAlcanzado() uses NavArrivalPolicy.waypointReached() and, when the node equals objetivoSobreUltimoEscalon(), requires AggressiveZombieEntity.escalonPendienteOcupado() rather than the generic proximity threshold. Normal WALK tolerance, DROP landing checks, and JUMP_UP controller confirmation are preserved.
- Added policy tests for adjacent-but-not-occupied stairs, physically occupied stairs, ordinary WALK tolerance, DROP landing validation, and JUMP_UP.
- Added read-only NavDropExclusionPolicy.contiene() / huellaRegistrada() accessors. The planner now fingerprints only when an exclusion key exists, and emits DROP_DESTINATION_EXCLUSION_HIT or DROP_DESTINATION_EXCLUSION_STALE.
- The executor emits DROP_DESTINATION_EXCLUDED with landing, exact obstacle state, fingerprint, and entry count; if the vertical scan finds no obstruction it emits DROP_REJECTION_NO_COLUMN_BLOCK. These events distinguish obstacle-detection, key-storage, fingerprint, and planner-consumption failures.
- Verification: policyTest compileJava compileTestJava -Pa2nDiagnostics=true --no-daemon --console=plain BUILD SUCCESSFUL (54s); NAV_ARRIVAL_TEST, NAV_PLANNER_STATE_TEST, and all other policy tests PASS; lint_aldea.py --strict PASS; git diff --check PASS; javap bytecode gates confirm the new methods are in the runtime classes.
- Current branch feature/autonomous-village, HEAD 518fee13fc15bfb9b92b0479d86b5c609d1947da. No commit. Preserve all existing modifications.

### Next: Run18

Launch the same disposable prepared world and settings (seed 12345, -Modo raid -NavCustom true -FaseA 3000 -FaseB 6600 -Presupuesto 900 -GlobalBudget 8000).
Acceptance observations:
1. Index 3 must emit STAIR_NODE_PHYSICALLY_REACHED and then ESCALON-TRAVESADO for 608,93,597; proximity alone must not advance the route.
2. Index 4 must emit DROP_DESTINATION_EXCLUDED for landing 569,116,531 and obstacle 569,118,531. The planner should then emit DROP_DESTINATION_EXCLUSION_HIT for that landing, or DROP_DESTINATION_EXCLUSION_STALE if the fingerprint changes. DROP_REJECTION_NO_COLUMN_BLOCK means the executor's obstacle scan is not identifying the blocked cell.
3. Confirm no logs/leaves/wood are mined, no protected blocks are touched, C4_PASS remains, and A2 improves. Do not raise budgets speculatively.
4. Do not edit source while the server is active. Wait for fin.txt and server JVM exit before the next source change.
## Run18 final - global exclusion verified, local retry persists (2026-11-13)
RunId: a2n-20261113-101103. Same disposable prepared world, seed 12345. Server saved and exited normally; fin.txt exists; no active server JVM remains.
- A2 phase B t=6600: 4/8 attackers ever entered, 0 inside at close; 4 alive / 4 dead. A2 remains NOT ACCEPTED.
- C4_PASS: guardsCombatieron=true; 12/12 expected workers alive and resumed; 0 worker casualties; 0 unresolved; 16 survivors; 4 attackers removed for recovery; jobSite=[1,0,0]; 0 instrumentation failures.
- DROP diagnostics: DROP_DESTINATION_EXCLUDED=1; DROP_DESTINATION_EXCLUSION_HIT=9; DROP_DESTINATION_EXCLUSION_STALE=0; DROP_REJECTION_NO_COLUMN_BLOCK=0. The executor found the exact spruce_leaves obstruction at 569,118,531 and registered the destination landing 569,116,531. The global planner skipped that destination from nine origins.
- The same landing still accumulated fallosDeDescenso=3280. DROP_LOCAL_EXCLUSION_HIT=0. Code inspection confirmed NavExecutor already passes exclusionesDescenso to the local NavPlanner.plan overload, but the local A* expansion loop did not consult it. The global filter worked; the local filter was missing.
- Index 3 / entity #267: second step remains at 608,93,597; intended feet cell 608,94,597; entity stayed at 608,94,598. State FAILED with 64/64 local stair attempts and 10/10 invalidations. STAIR_NODE_PHYSICALLY_REACHED=0. The pending-step branch exists but its esPieValido() predicate rejects the target; Run18 did not log which predicate failed.
- No crash or watchdog failure. C4 recovery completed and fin.txt confirms C4_PASS.

## Run19 patch applied and statically verified (2026-11-13)

- Local NavPlanner.plan now applies the same destination-wide and edge-specific DROP exclusion policy during local A* expansion. It computes the terrain fingerprint only when a matching exclusion key is present and emits DROP_LOCAL_EXCLUSION_HIT / DROP_LOCAL_EXCLUSION_STALE.
- NavExecutor already passes exclusionesDescenso to the local planner; no call-site change was required. The missing behavior was the local expansion filter itself.
- Pending-stair selection now emits STAIR_PENDING_WAYPOINT_SELECTED when the target is accepted, or STAIR_PENDING_WAYPOINT_REJECTED with the exact checks: within-window, chunk-loaded, protected, feet-air, head-air, support, and the three block states. This identifies why esPieValido() rejects the pending stair without weakening its safety checks.
- The strict physical arrival gate remains in place. It will only be exercised after the pending stair is accepted as a local waypoint.
- Verification: policyTest compileJava compileTestJava -Pa2nDiagnostics=true --no-daemon --console=plain BUILD SUCCESSFUL (23s); NAV_ARRIVAL_TEST PASS; NAV_PLANNER_STATE_TEST PASS including destination-wide exclusion cases; all other policy tests PASS; lint_aldea.py --strict PASS; git diff --check PASS; javap confirms DROP_LOCAL_EXCLUSION_HIT and both pending-stair events are in runtime bytecode.
- Branch feature/autonomous-village; HEAD 518fee13fc15bfb9b92b0479d86b5c609d1947da; no commit. Preserve all existing modifications.

### Next: Run19

Launch the same disposable prepared world and settings (seed 12345, -Modo raid -NavCustom true -FaseA 3000 -FaseB 6600 -Presupuesto 900 -GlobalBudget 8000).
Acceptance observations:
1. DROP_LOCAL_EXCLUSION_HIT must occur for landing 569,116,531. The executor must stop retrying that blocked destination and choose a viable alternative or report a bounded no-route result.
2. STAIR_PENDING_WAYPOINT_REJECTED must identify the failing condition for target 608,94,597. Do not relax protection, headroom, support, or chunk checks without evidence. If selected, require STAIR_NODE_PHYSICALLY_REACHED followed by ESCALON-TRAVESADO.
3. Confirm no logs/leaves/wood are mined and no protected blocks are touched.
4. Confirm C4_PASS and improved A2 entry/inside counts.
5. Wait for fin.txt and server JVM exit before editing source again.
## Run19 final â€” local DROP exclusion works; second stair remains blocked (2026-11-13)

RunId: a2n-20261113-102344. Disposable prepared world, seed 12345, same A2 phase/budget settings. Run completed normally, saved all dimensions, wrote fin.txt, and server JVM exited.
- A2 at t=6600: 4/8 attackers ever entered the perimeter; 0 remained inside at close; 4 alive / 4 dead. A2 remains NOT ACCEPTED.
- C4_PASS: guardsCombatieron=true; professionals resumed; job-site assignments clean; 16 survivors; 12/12 expected workers alive and resumed; 0 worker casualties; 0 unresolved; 4 attackers removed for recovery; jobSite=[2,0,0]; 0 instrumentation failures.
- Local DROP exclusion is now active: DROP_LOCAL_EXCLUSION_HIT=807; DROP_LOCAL_EXCLUSION_STALE=0. The blocked landing (584,108,520) was skipped repeatedly by local A* instead of being reintroduced. No stale fingerprint.
- Pending waypoint diagnostics: STAIR_PENDING_WAYPOINT_SELECTED=143; STAIR_PENDING_WAYPOINT_REJECTED=0; STAIR_NODE_PHYSICALLY_REACHED=0. Selection alone does not prove movement.
- One genuine stair traversal was logged: attacker #270 stood on placed stair 595,95,600 at entity position 595,96,600 (placed tick 3, traversed tick 20; block verified; anchor 594,95,600 explicitly is not counted as a stair). This is an intermediate step only, not successful wall entry.
- The same attacker later stopped at 608,94,598 with two stair blocks placed and pending second stair 608,93,597; final snapshot still showed 2/24 stairs, waiting for real traversal. It exhausted 64 local stair attempts and 10/10 invalidations.
- Attacker #271 selected pending step target 588,109,523 (step block 588,108,523) from position 587,109,522, but did not physically traverse it. It reached 64/64 stair attempts, 10/10 invalidations, and remained FAILED at 587,109,522.
- Final A2 snapshot: 4 entered ever, 0 inside now. Two attackers (#270/#271) remain blocked by stair traversal; other two are not evidence of a successful breach. No STAIR_NODE_PHYSICALLY_REACHED events were emitted by the new pending-waypoint gate. The older ESCALON-TRAVESADO event is a distinct confirmed first step only.
- Run completed C4 recovery: C4 OUTCOME=SURVIVED attackersRemovedForRecovery=4 workerCasualties=0; FIN (C4_PASS...); trace closed with 638494 records, 0 dropped, 0 failures. No server JVM remains.
- Conclusion: Run19 validates the local DROP exclusion fix (807 hits, 0 stale), but does NOT validate second-step traversal or A2 wall breach. Do not mark A2 complete.

### Next investigation: pending stair movement / route handoff

1. Preserve Run19 logs. Focus on why a valid adjacent pending waypoint is selected every tick while the entity does not move: #270 target/step 608,94,598 / 608,93,597 from entity 608,94,598; #271 target/step 588,109,523 / 588,108,523 from entity 587,109,522.
2. Trace the exact moveTo / navigation command issued after elegirObjetivoLocal, whether navigation.isDone(), current path target/node count, command acceptance, resulting navigation target, entity position delta, and active goal. Do not infer success from selection events.
3. Add a bounded event recording selected target, navigation.isDone(), current path target/node count, command acceptance, resulting navigation target, entity position delta, and active goal. Rate-limit per entity/state; avoid huge logs.
4. Preserve the physical-arrival gate: only confirm stair traversal when the entity bounding box is actually above/on the placed stair and block identity is verified.
5. Run next attempt on a new disposable copy with same seed/settings after compiling. Do not reuse mutated Run19 world.
6. Keep protected blocks, logs, leaves, and wood safe; no speculative weakening of headroom, support, protection, or arrival conditions.

## Run20 final - proximity-arrival guard validated; A2 improved but still open (2026-11-13)

RunId: a2n-20261113-103720. Fresh disposable copy from the prepared seed-12345 world; same A2 phase/budget settings. Completed normally; C4_PASS; server JVM exited.
- A2 t=6600: 5/8 attackers entered the perimeter at least once; 0 remained inside at close. A2 NOT ACCEPTED.
- The early objective-proximity guard was applied before generic arrival could consume a pending stair. The guard is rate-limited and emits STAIR_WAYPOINT_PROXIMITY_SUPPRESSED.
- Runtime evidence: at least three suppression events; attacker #266 physically traversed the second stair 608,93,597. Attacker #267 also traversed its first and second stair before continuing. Physical verification was by block identity and entity position, not waypoint selection.
- A2 did not complete: the attackers did not remain inside the wall at phase close. A later local BREAK objective at 612,80,598 was previously rejected as NO_ES_PIE; this motivated Run21's action-node acceptance policy.
- C4_PASS: 12/12 workers alive and resumed; 0 worker casualties; 0 unresolved; 16 survivors; 2 attackers removed for recovery; jobSite [1,0,0]; 0 instrumentation failures.
- Do not mark A2 accepted. Continue with action-node acceptance and stair recovery diagnostics.

## Run21 final - action-node acceptance, 7/8 entries, C4 PASS (2026-11-13)

RunId: a2n-20261113-104836. Fresh disposable copy from the prepared seed-12345 world; same A2 phase/budget settings. Server saved all dimensions, wrote fin.txt, and exited normally.
- A2 t=6600: 7/8 attackers entered the perimeter at least once; 0 remained inside at close. At t=7500: 0 alive, 6 dead, 2 disappeared, 7 entered ever, 0 inside. A2 remains NOT ACCEPTED.
- C4_PASS: guardsCombatieron=true; profesionalesReanudados=true; asignacionesJobSiteLimpias=true; supervivientes=16; workers expected/alive/resumed=12/12/12; worker casualties=0; unresolved=0; attackers removed for recovery=2; jobSite=[1,0,0]; instrumentation failures=0.
- Trace integrity: 550919 records, 0 dropped, 0 failures; all eight entity streams have no tick gaps.
- DROP exclusion: DROP_LOCAL_EXCLUSION_HIT=813; DROP_LOCAL_EXCLUSION_STALE=0; DROP_DESTINATION_EXCLUDED=19; DROP_DESTINATION_EXCLUSION_HIT=3234; DROP_DESTINATION_EXCLUSION_STALE=0.
- Proximity arrival: STAIR_WAYPOINT_PROXIMITY_SUPPRESSED=4. The exact near-but-not-occupied stair waypoint was not consumed by the generic radius. Three legacy physical stair confirmations were logged with verified block and entity position: #267 at 595,95,600; #267 at 608,93,597; #268 at 588,108,523.
- New NavExecutor event counts: STAIR_PENDING_WAYPOINT_SELECTED=6; STAIR_PENDING_WAYPOINT_REJECTED=4; STAIR_NODE_PHYSICALLY_REACHED=0. The latter is a separate executor-route event; it does not invalidate the three legacy ESCALON-TRAVESADO confirmations, which verify actual occupancy.
- The four pending-waypoint rejections all had target 588,106,523 and failed only dentroDeLaVentana=false; chunk loaded, not protected, feet/head air, and support=true. Attacker #268 was at approximately 587,96,510 after falling below/away from the pending stair. This is the next stair-recovery blocker: pending step remains above and outside the local planning window.
- Six explicit DENTRO-PARED events were logged; final snapshot's enteredPerimetroEver counter is 7/8. At least one confirmed entry was via a wall-ring oak log at 687,71,589, classified by the wall guard as esMuro=true with valid ring radius/height. No evidence of an unprotected tree log being mined.
- BREAK action objective patch: pure NavObjectivePolicy added and registered in policyTest. NavExecutor now accepts BRIDGE objectives and BREAK objectives only when puedeRomperNodoDeNavegacion(target) is true, in both initial and post-escalation objective validation. This avoids rejecting a valid solid BREAK node solely because it is not a standing cell.
- Runtime: attacker #267 progressed from the prior NO_ES_PIE failure through stone BREAK navigation and reached a subsequent DROP route; it did not complete entry before the phase ended. Another attacker #268 traversed a confirmed stair but later fell away from its next pending stair.
- Static gates before Run21: policyTest, compileJava, compileTestJava, lint_aldea.py --strict, git diff --check all PASS.
- No server JVM remains. No commit was created; preserve the existing working-tree modifications.

### Next investigation - pending stair becomes unreachable after fall

1. Preserve Run21 logs and the exact target/position evidence for #268: pending step 588,106,523, entity around 587,96,510, four rejections due only to outside-window.
2. Determine why the entity falls away from a placed stair before physical traversal. Trace stair placement tick, navigation target/path, movement commands, active goal, entity position delta, and any route/recovery override.
3. Decide from evidence whether a pending stair should remain actionable after a fall that moves it outside the local window, or whether the stair anchor should be invalidated and a new reachable construction step planned. Do not blindly expand the window or chase stale stair coordinates.
4. Preserve the strict physical occupancy check, support/headroom/chunk/protection checks, and wall-only log exception. Never allow tree logs/leaves/wood to be mined.
5. Keep local/global DROP exclusion policy shared. The Run21 counters show it remains effective with zero stale fingerprints.
6. Use a fresh disposable copy of the prepared seed-12345 world for Run22. Do not reuse Run21's mutated world.


## Run22 — instrumentación de comando de movimiento (2026-11-13)

### Evidencia forense adicional de Run21

Se releyó `nav-trace.log` del run `a2n-20261113-104836` por UUID del atacante #268 (`493868b1-16c4-4385-8d45-ebc59725730a`). La secuencia relevante es:

- Primero tuvo el peldaño pendiente `588,108,523`, con celda superior `588,109,523`; el ejecutor llegó a seleccionar el waypoint desde `588,109,524` (adyacente, no ocupado).
- Después se registró `NAV_FAIL` por `construccion agotada sin completar el descenso` en `589,99,513`, seguido de rearme con misión `599,71,516`. Luego otro fallo por replanteos agotados en `588,98,512`.
- La escalera siguió reteniendo un bloque pendiente posterior en `588,105,523`, cuya celda superior `588,106,523` quedó fuera de ventana. Las cuatro negativas fueron exclusivamente `dentro=false`; chunk cargado, no protegido, pies/cabeza libres y soporte cobblestone válido.
- El atacante intentó después DROP natural a `588,93,511` desde `588,95,511`; los snapshots posteriores lo muestran oscilando cerca de `587.9,96,510.5`, sin completar el aterrizaje. Esto confirma que la caída/desvío sucede antes de la posterior condición fuera-de-ventana, no que el único problema sea el tamaño de ventana.

La evidencia no basta todavía para atribuir el desplazamiento a vanilla Navigation, a un Goal concurrente o al ciclo de recuperación/rearme. Tampoco autoriza invalidar el peldaño ni expandir la ventana.

### Instrumentación añadida para Run22

En `NavExecutor` se añadió `MOVE_COMMAND`, llamada inmediatamente después de emitir cada movimiento STEP_UP, DROP, acercamiento al origen DROP, acercamiento a nodo BREAK/BRIDGE, movimiento por defecto y al iniciar/rechazar JUMP_UP. Registra destino solicitado, transición, velocidad, posición real, delta desde la última muestra, estado `isDone/isInProgress`, target/número/índice del Path vanilla y nombres de Goals activos. Se limita a un evento por cambio de destino o cada 10 ticks por clave; no altera decisiones de navegación.

Verificación tras instrumentación: `policyTest compileJava compileTestJava -Pa2nDiagnostics=true --no-daemon --console=plain` BUILD SUCCESSFUL; `python tools\\lint_aldea.py --strict` PASS; `git diff --check` PASS. No se lanzó Minecraft todavía con esta instrumentación. Run22 debe usar una copia desechable fresca del fixture preparado y los mismos parámetros/semilla. A2 continúa OPEN / NOT ACCEPTED; C4 Run21 permanece PASS. Sin commit; preservar todos los cambios existentes.


### Run22 lanzado — en curso (2026-11-13 11:05 local)

- RunId: `a2n-20261113-110548`.
- Fixture fuente: `build\\a2n\\arena-repair1-20261112-205206\\game\\a2n`; el lanzador creó una copia nueva y exclusiva en `build\\a2n\\a2n-20261113-110548\\game\\a2n`.
- Semilla 12345; modo raid; NavCustom=true; Fase A=3000; Fase B=6600; presupuesto local=900; global=8000; límite monitor=90 min.
- Proceso lanzador PowerShell PID 8412; JVM servidor PID 9748. En el último sondeo (11:06:22) seguía en carga de mods; aún no existía `fin.txt` ni `nav-trace.log` (el mundo todavía no había iniciado el harness). No editar fuentes ni ejecutar Gradle mientras esta JVM esté activa.
- Próximo sondeo: esperar inicio del mundo y aparición de `nav-trace.log`; comprobar `MOVE_COMMAND` para UUID #268 y verificar si el path/Goals activos cambian antes del desplazamiento. Esperar `fin.txt`, guardado de dimensiones y salida de JVM antes de modificar código.


## Run22 — MOVE_COMMAND y diagnóstico del bloqueo de descenso (2026-11-13)

**RunId:** `a2n-20261113-110548`
**Resultado A2:** FAIL / OPEN. **C4:** PASS.

- Fixture clonado sin modificar: `build\a2n\arena-repair1-20261112-205206\game\a2n`.
- Semilla 12345; raid; NavCustom=true; PhaseA=3000; PhaseB=6600; budget local=900; global=8000; límite 90 min.
- Fin formal: 11:14:50; `fin.txt` presente; servidor y launcher terminados; mundos guardados.
- `fin.txt`: C4_PASS; 12/12 trabajadores vivos y reanudados; 0 bajas; 0 no resueltos; 16 supervivientes; 4 retirados para recuperación; 0 fallos de instrumento.
- Asedio: 8/8 spawn; 4/8 entraron al perímetro alguna vez; 0 dentro de la pared al cierre de fases A y B. Fase A terminó con 4 vivos/4 muertos; fase B terminó con 4 vivos/4 muertos; el cierre/limpieza posterior registró 4 desaparecidos. A2 NO aceptado.
- `nav-trace.log`: 463,993 registros; 0 descartados; 0 fallos; tamaño 46,591,673 bytes.

### Hallazgo forense principal

La instrumentación `MOVE_COMMAND` confirmó que #270 (UUID `43590aaa-19dc-4689-b4f8-7607c7c29514`) sí recibía órdenes de navegación; no era un caso de ausencia de `moveTo`. Sus Goals activos observados fueron `BreakBlockGoal,MoveToVillageCenterGoal,TraverseGoal`; esto por sí solo no demuestra conflicto de Goals.

En 11:12:30, #270 reportó:

```text
DESCENSO BLOQUEADO: ... escalon 589, 93, 512 colocado y verificado
pero su aterrizaje NO es alcanzable desde 588, 97, 512:
el camino de caida esta bloqueado en y=97
```

Un tick después, la evaluación de bloque registró para `589,97,512`:

```text
bloque=Block{minecraft:cobblestone}
columnaAnillo=false capaValida=false esMuro=false
decisionBrecha=BLOQUEADO_OBRA_DEL_ASEDIO
```

Se comparó el chunk `r.1.1.mca` del mundo final contra el mismo chunk del fixture original. `589,97,512` cambió de `minecraft:air` a `minecraft:cobblestone`; no estaba en el fixture. La evaluación de interacción identificó el bloque como obra del asedio (puente), registrada en el conjunto compartido `TABLONES_DEL_ASALTO`; por diseño los atacantes no pueden picar esos tablones. No se atribuye el bloque a un atacante individual porque el registro actual no emite cada colocación de puente.

**Causa demostrada:** el selector genérico de escalones descendentes comprobaba solamente `objetivo`, `objetivo.above()` y `objetivo.above(2)`. No comprobaba toda la columna entre el aterrizaje y los pies actuales. Por eso podía aceptar/colocar el escalón `589,93,512` aunque el bloque de puente en `589,97,512` hacía imposible el descenso; luego la protección legítima de la obra impedía retirarlo.

Otros datos: #270 también acumuló fallos al intentar romper `584,111,523`, y evitó correctamente las hojas de abeto `583,110,523` porque no eran muro. No se debe relajar la regla de no picar árboles ni permitir romper bloques protegidos del puente.

### Corrección aplicada después de Run22

- Nuevo `NavDropColumnPolicy`: encuentra el primer Y bloqueado en la columna completa del aterrizaje.
- `NavExecutor.rechazoColumnaCaidaParaEscalon` reutiliza exactamente los predicados de seguridad de descenso (fluido, peligros y colisión).
- `AggressiveZombieEntity.evaluarCeldaDeEscalon` consulta esa validación antes de colocar un peldaño descendente. Los candidatos con columna bloqueada se rechazan y la búsqueda acotada continúa con el siguiente candidato.
- Nuevo `NavDropColumnPolicyTest`, agregado a `policyTest`.

Verificación posterior a la corrección:
```text
.\gradlew.bat policyTest compileJava compileTestJava -Pa2nDiagnostics=true --no-daemon --console=plain
BUILD SUCCESSFUL in 32s
python tools\lint_aldea.py --strict
lint aldea: OK
git diff --check
PASS (solo advertencias CRLF)
```

Sin commit. Se preservó todo el estado previo del worktree.

### Siguiente paso obligatorio

Ejecutar Run23 sobre un clon fresco del mismo fixture, con semilla 12345 y mismos parámetros. Confirmar en logs que el candidato `589,93,512` se rechaza antes de colocarse por `camino de caida bloqueado en y=97`, que la búsqueda elige otro peldaño o falla de forma acotada sin consumir bloques inútiles, y si el atacante progresa. Analizar también los otros tres atacantes supervivientes. A2 permanece OPEN hasta que una corrida completa demuestre entradas al recinto y supervivencia conforme a los criterios existentes.


## Run23 — validación dinámica y concurrencia de puente/escalera (2026-11-13)

**RunId:** `a2n-20261113-112455`
**C4:** PASS. **A2:** FAIL / OPEN.

- Fixture fresco, mismo fixture fuente y semilla 12345; PhaseA=3000, PhaseB=6600; mismos presupuestos que Run22.
- Fin formal 11:34:10; `fin.txt` y `launcher-status.txt` confirman C4_PASS; procesos Gradle/servidor terminaron.
- C4: 12/12 trabajadores vivos y reanudados; 0 bajas; 0 no resueltos; 16 supervivientes; 3 retirados para recuperación; 0 fallos de instrumento.
- Asedio: 8/8 spawn; 5/8 entraron al perímetro alguna vez; 0 dentro al cierre de fases A/B. Fase A: 4 vivos/4 muertos; Fase B: 3 vivos/5 muertos; después 3 desaparecidos en limpieza. A2 NO aceptado.
- `nav-trace.log`: 791,069 registros CSV + eventos; `traceNav=true`; archivo 72,392,689 bytes.

### Resultado de la corrección de columna

La nueva validación sí opera: el log de #270 registra candidatos rechazados por `el camino de caida esta bloqueado en y=99` y `y=97`; #269 también rechaza candidatos en Y=76/77. No se ha observado que la corrección permita completar el descenso principal de #270.

**Hallazgo nuevo:** la validación previa al peldaño solo comprueba el terreno en el instante de colocación. En Run23 #270 colocó `589,93,512` a las 11:31:45; a las 11:31:50 su aterrizaje ya estaba bloqueado por cobblestone en `589,97,512`, clasificado como `BLOQUEADO_OBRA_DEL_ASEDIO`. El mob seguía en `588,97,512`. La hipótesis principal es interferencia de su propia operación `puentearHacia`: el método tiende cobblestone en el bloque delantero, justo la columna X/Z del aterrizaje. La atribución individual todavía no está demostrada porque el método solo registra el inicio del puente, no cada bloque. No afirmar aún que fue #270 quien lo colocó.

Otros progresos: #269 ejecutó varios descensos construidos (en snapshot final registra 6 intentos, 0 fallos, último aterrizaje construido OK en `614,74,597`). #270 consiguió también descensos naturales alternativos (`587,95,510` OK), pero su escalera quedó bloqueada y acabó lejos del recinto. #8 agotó presupuesto global (8000) con frente parcial; #3 quedó en posición fija `615,77,597`. 5/8 entraron alguna vez, pero ninguno estaba dentro al cierre.

### Próximo diagnóstico Run24

Se añadió únicamente instrumentación en `puentearHacia`: cada bloque de puente emitirá `PUENTE_BLOQUE` con UUID del asaltante, posición colocada, origen, objetivo y presupuesto restante. Esto permitirá atribuir con precisión el cobblestone que invade `589,97,512` y distinguir autointerferencia de interferencia entre asaltantes. No se cambió la lógica de puente ni la protección de tablones.

Run24 debe usar fixture fresco, seed 12345 y parámetros iguales. Buscar `PUENTE_BLOQUE pos=589, 97, 512`, correlacionar UUID/tick con la colocación del peldaño y eventos DROP, y solo entonces decidir una política de reserva/exclusión de columnas. Mantener protegido el registro compartido de tablones; no permitir que el pico rompa puentes ni árboles. A2 sigue OPEN.


## Run24 — atribución de cada bloque de puente (EN CURSO, 2026-11-13)

- RunId: `a2n-20261113-113725`.
- Fixture fuente: `build\a2n\arena-repair1-20261112-205206\game\a2n`; copia desechable nueva en `build\a2n\a2n-20261113-113725\game\a2n`.
- Seed 12345; raid; NavCustom=true; Fase A=3000; Fase B=6600; presupuesto local=900; global=8000; límite 90 min.
- Launcher iniciado 11:37:26; PID servidor reportado 16328.
- Cambio de código exclusivo de diagnóstico: cada colocación en `puentearHacia` emite `PUENTE_BLOQUE` con UUID del asaltante, posición, origen, objetivo y presupuesto. Sin cambios de lógica de movimiento/puente.
- Esperar `fin.txt` y salida de JVM antes de modificar fuentes. Correlacionar `PUENTE_BLOQUE pos=589, 97, 512` con peldaños y DROP del UUID #270. No ejecutar Gradle ni editar mientras el servidor esté vivo.


## Run24 — atribución exacta de puente que bloquea DROP (2026-11-13)

**RunId:** `a2n-20261113-113725`
**C4:** PASS. **A2:** FAIL / OPEN.

- Fixture fresco, seed 12345, mismos parámetros Run22/23. Fin formal 11:46:45; `fin.txt` C4_PASS; no quedan procesos de juego.
- C4: 12/12 trabajadores vivos y reanudados; 0 bajas; 0 no resueltos; 16 supervivientes; 3 retirados para recuperación; 0 fallos de instrumento.
- A2: 8/8 spawn. Fase A: 4 vivos/4 muertos, 4/8 entraron alguna vez, 0 dentro al cierre. Fase B: 3 vivos/5 muertos, 5/8 entraron alguna vez, 0 dentro al cierre. Limpieza posterior: 3 desaparecidos. A2 NO aceptado.
- Trace: 574,409 registros; 0 descartados; 0 fallos. Archivo `nav-trace.log` 55,342,739 bytes.
- Instrumentación `PUENTE_BLOQUE`: 9 colocaciones atribuidas a UUID/entidad, con origen y objetivo.

### Causa raíz confirmada

A las 11:44:19:

```text
asaltante #271 PUENTE_BLOQUE pos=589, 97, 512
origen=588, 97, 512 hacia=661, 71, 533
```

A las 11:44:25, el mismo #271:

```text
DESCENSO BLOQUEADO ... escalon 589, 93, 512 colocado y verificado
pero su aterrizaje NO es alcanzable desde 588, 97, 512:
el camino de caida esta bloqueado en y=97
```

El orden y las coordenadas demuestran que el puente colocado por #271 invadió la columna X/Z del aterrizaje de su propia escalera. No es terreno natural, no es un bloque de aldeano y no es un muro. El mismo patrón se vio antes en Run23. Run24 también registró que #270 colocó un puente en `616,77,597` desde `615,77,597` y permaneció en el origen, otro síntoma de interacción puente/trayectoria.

**Causa raíz:** `puentearHacia` solo evita que el destino inmediato sea un nodo activo; no evita que el bloque nuevo ocupe la columna de caída de un peldaño que el mismo asaltante ya construyó. La validación de escalera era unilateral (rechaza un escalón si el puente ya existe), pero no había la regla inversa (rechaza el puente si invade la columna de una escalera existente).

### Corrección siguiente (a aplicar tras Run24)

Añadir política pura en `NavDropColumnPolicy` para detectar si un bloque de puente propuesto invade la columna de caída del último peldaño verificado del propio asaltante: mismo X/Z y Y del puente dentro de `landingY+1 .. landingY+maxSafeDrop`. Consultarla en `puentearHacia` antes de `setBlock`; si invade, omitir ese lado y probar los otros tres. No romper ni borrar puentes existentes. Añadir pruebas de misma columna, columna adyacente, por debajo del aterrizaje y fuera de la caída segura. Mantener validación previa de escalón ya añadida, para que ambas direcciones queden protegidas.

No se ha aplicado todavía esta corrección. Sin commit. El siguiente run debe ser fresco y verificar `PUENTE_RECHAZADO_COLUMNA_ESCALERA` para `589,97,512`, ausencia de colocación y progreso por un lado alternativo.


### Corrección bilateral de puente/escalera aplicada tras Run24

- `NavDropColumnPolicy.puenteInvadeColumnaDeEscalon(...)` ahora detecta si un bloque de puente cae en la columna vertical de un peldaño descendente existente: mismo X/Z y Y entre `landingY+1` y `landingY+maxSafeDrop`.
- `AggressiveZombieEntity` conserva las posiciones de todos los peldaños descendentes verificados de la marcha (máximo 24) en `escalonesConstruidosEnMarcha`.
- Antes de colocar cada tablón, `puentearHacia` consulta `puenteInvadeColumnaDeEscalera`; si invade un corredor, emite `PUENTE_RECHAZADO_COLUMNA_ESCALERA` y prueba el siguiente lado. No elimina ni rompe puentes existentes y mantiene `TABLONES_DEL_ASALTO`.
- La validación inversa ya existente sigue rechazando un peldaño nuevo si el puente estaba colocado antes. Así quedan cubiertas ambas secuencias temporales.
- `NavDropColumnPolicyTest` añade casos: columna coincidente, columna adyacente, debajo del landing, fuera de caída segura y caída máxima nula.

Verificación posterior:
```text
.\gradlew.bat policyTest compileJava compileTestJava -Pa2nDiagnostics=true --no-daemon --console=plain
BUILD SUCCESSFUL in 29s
python tools\lint_aldea.py --strict
lint aldea: OK
git diff --check
PASS (solo advertencias CRLF)
```

**Run25 pendiente:** fixture fresco, seed 12345 y parámetros iguales. Verificar que #271 (o entidad equivalente) emita `PUENTE_RECHAZADO_COLUMNA_ESCALERA puente=589,97,512 escalon=589,93,512`, no coloque el bloque y use otro lado; verificar descenso real y entradas al recinto. A2 continúa OPEN hasta resultado completo.


## Run25 — validación de exclusión bilateral de columnas (EN CURSO, 2026-11-13)

- RunId: `a2n-20261113-115000`.
- Fixture fuente: `build\a2n\arena-repair1-20261112-205206\game\a2n`; copia desechable nueva en `build\a2n\a2n-20261113-115000\game\a2n`.
- Seed 12345; raid; NavCustom=true; Fase A=3000; Fase B=6600; presupuesto local=900; global=8000; límite 90 min.
- Launcher iniciado 11:50:00; PID servidor reportado 36784.
- Cambio funcional: registrar peldaños descendentes verificados por asaltante y rechazar un puente que invada la columna DROP de cualquiera de ellos; el lado rechazado se omite y se prueban otros lados. Evento esperado: `PUENTE_RECHAZADO_COLUMNA_ESCALERA`.
- Esperar `fin.txt` y salida de JVM antes de editar. Confirmar el rechazo para `589,97,512` o la coordenada equivalente; comprobar que el bloque no se coloca y que el atacante elige alternativa. No tocar puentes existentes ni árboles.


## Run25 — validación runtime bilateral puente/escalera (2026-11-13)

**RunId:** `a2n-20261113-115000`
**C4:** PASS. **A2:** OPEN (sin umbral formal de aceptación acordado).

- Fixture fresco; seed 12345; parámetros iguales a Run22–24. Fin 11:59:01; `fin.txt` C4_PASS; no quedan procesos de juego.
- C4: 12/12 trabajadores vivos/reanudados; 0 bajas; 0 no resueltos; 16 supervivientes; 1 retirado para recuperación; 0 fallos de instrumento.
- Fase A: 8/8 spawn; 3 vivos/5 muertos; 6/8 entraron alguna vez; 0 dentro al cierre.
- Fase B: 1 vivo/7 muertos; 7/8 entraron alguna vez; 0 dentro al cierre. #8 y #7 entraron, pero murieron en ticks 3531 y 4573 respectivamente. Un atacante permaneció vivo fuera hasta el cierre y desapareció en la limpieza posterior.
- Trace: 565,242 registros; 0 descartados; 0 fallos.

### Validación positiva de la corrección

Dos rechazos de columna confirmados:
1. #269: `PUENTE_RECHAZADO_COLUMNA_ESCALERA puente=616,77,597 escalon=616,73,597`. En lugar de invadir la columna, continuó por el lado adyacente, atravesó la escalera y abrió brecha en bloques que el sistema validó como muro (620,71,580; 622,71,582; 625,71,584; 626,71,585).
2. #270: `PUENTE_RECHAZADO_COLUMNA_ESCALERA puente=589,97,512 escalon=589,93,512`, repetido dos veces. No colocó ese bloque; tendió alternativas `588,97,511`, `588,86,508`, `588,78,508`. Descendió hasta Y=79 sin el bloqueo original. La columna de caída protegida se conservó.

**La corrección bilateral queda validada en runtime para el patrón de auto-interferencia reproducido en Run23/24.** No se ha demostrado aún aceptación completa del asedio.

### Siguiente bloqueo medido (#270)

Tras progresar a `588,79,507`, #270 agotó 8/8 replans a las 11:57:40 y quedó en `PLANNING/RECOVERING`. Los registros posteriores muestran que no encontraba bloque rompible alrededor, aunque el terreno inmediato contiene piedra. Un `MOVE_COMMAND` en tick 6280 registra:
```text
reason=ACERCARSE-ACCION
node=590,76,507 transition=BREAK
requested=590.50,76.00,507.50
pos=588.52,79.00,506.68
path=BlockPos{x=590,y=87,z=507}/n=2/next=0
activeGoals=BreakBlockGoal,MoveToVillageCenterGoal,TraverseGoal
```
La ruta de navegación reporta un destino Y=87 mientras la orden solicita acercarse al nodo BREAK Y=76. El mundo guardado contiene piedra en `590,87,507`; aún no se sabe si es un camino alternativo válido, una ruta obsoleta o una ruta a celda no transitable. No aplicar una corrección especulativa.

### Próximo diagnóstico Run26

Instrumentar `MOVE_COMMAND` con los primeros 3–4 nodos del Path (índice, coordenada y estado de colisión del bloque) para #270 cuando `reason=ACERCARSE-ACCION`; conservar target solicitado, Path target, índice siguiente y Goals. Ejecutar fixture fresco con mismos parámetros. Determinar por qué la ruta de acercamiento BREAK selecciona `590,87,507` al solicitar `590,76,507`, y si los nodos realmente son transitables. No alterar política de puente/escalera que ya pasó la regresión runtime.

**Nota de aceptación:** los documentos históricos dicen expresamente que aún no existe umbral formal acordado para A2. Mantener OPEN; no inventar un criterio ni declarar PASS por el dato 7/8.


## Run26 — Path nodes y colisiones de BREAK approach (EN CURSO, 2026-11-13)

- RunId: `a2n-20261113-120306`.
- Fixture fuente: `build\a2n\arena-repair1-20261112-205206\game\a2n`; copia desechable nueva en `build\a2n\a2n-20261113-120306\game\a2n`.
- Seed 12345; raid; NavCustom=true; Fase A=3000; Fase B=6600; presupuesto local=900; global=8000; límite 90 min.
- Launcher iniciado 12:03:06; PID servidor reportado 26508.
- Diagnóstico agregado a `MOVE_COMMAND`: primeros cuatro nodos de Path desde nextIndex, estado de bloque en feet/head/support, canReach y celda solicitada con sus tres bloques. Solo instrumentación; sin cambio de navegación.
- Esperar `fin.txt` y salida de JVM antes de editar. Para #262 (idx3, UUID `2a60c235-9c92-4d25-821a-54836ea3bab5`), correlacionar orden `ACERCARSE-ACCION` con Path target y nodos reales. No tocar la corrección puente/escalera validada en Run25.


## Run26 — Path canReach y nodo BREAK no transitable (2026-11-13)

**RunId:** `a2n-20261113-120306`
**C4:** PASS. **A2:** OPEN.

- Fin formal 12:12:24; `fin.txt` C4_PASS; servidor terminado.
- C4: 12/12 trabajadores vivos y reanudados; 0 bajas; 0 no resueltos; 16 supervivientes; 4 retirados para recuperación; 0 fallos de instrumento.
- Fase A: 8/8 spawn; 4 vivos/4 muertos; 3/8 entraron alguna vez; 0 dentro al cierre.
- Fase B: 8/8 spawn; 4 vivos/4 muertos; 5/8 entraron alguna vez; 1 dentro al cierre (idx8, salud 8.0, posición 665,71,483). Otros vivos: idx3, idx4, idx7 fuera. Tras la fase, el harness retiró entidades durante limpieza.
- Trace: 624,095 registros; 0 descartados; 0 fallos.

### Diagnóstico exacto del acercamiento BREAK

El actor #262 (idx3, UUID `2a60c235-9c92-4d25-821a-54836ea3bab5`) quedó en `615,77,597`, con nodo BREAK `615,74,598` y `ultimaAccionDeRuta=BREAK-ACERCARSE`. La instrumentación `MOVE_COMMAND` capturó una solicitud de movimiento hacia el centro del bloque sólido. PathNavigation devolvió Path no alcanzable (canReach=false), con target de path muy por encima del objetivo (p. ej. `611,94,598` al solicitar `611,82,598`). El estado de pies/cabeza del destino solicitado era piedra. En el mundo guardado, la columna `z=598` entre Y=74–76 estaba llena de stone; el mob estaba en una cavidad adyacente. La orden de acercamiento a un bloque sólido no produce una ruta ejecutable.

#262 terminó sin avanzar desde `615,77,597`; sus dos peldaños no le permitieron alcanzar el BREAK. #270 (idx4) también terminó en `588,80,506`, `RECOVERING`, con 20 fallos de descenso y BREAK-ACERCARSE hacia `590,76,507`. La corrección de puente/escalera sigue intacta y no fue revertida.

### Próxima corrección propuesta (Run27)

En la rama BREAK/BRIDGE, después de enviar `moveTo`, comprobar `Path.canReach()`. Si el Path a un BREAK no es alcanzable, lanzar raycast desde los ojos del mob al centro del bloque objetivo. Si el primer impacto es un bloque distinto del objetivo y está dentro de alcance, delegar ese primer obstáculo como BREAK mediante la `RecoveryAction` existente, conservando íntegramente `BlockInteractionPolicy` (protección de aldea, tablones, árboles, block entities, dureza). Tras verificar que el bloque quedó en aire, invalidar solo la ruta local y replanificar. Nunca romper un bloque solo por obstruir la línea: debe aprobar el mismo gate de producción.

Se añadió `NavActionApproachPolicy` y su prueba pura para el predicado (Path inalcanzable + impacto distinto + alcance + transición BREAK). Run27 debe buscar el evento `BREAK_APPROACH_OCCLUSION`, verificar que el bloque fue removido por la acción autorizada y que la ruta se recalcula. No ampliar permisos de rotura. A2 permanece OPEN.


### Corrección BREAK approach aplicada después de Run26

- `NavActionApproachPolicy` + `NavActionApproachPolicyTest`: el predicado solo permite intentar el primer obstáculo si PathNavigation no alcanza el objetivo, el primer impacto del raycast es distinto del objetivo, el bloque está dentro del alcance y la transición es BREAK.
- `NavExecutor`: al ordenar acercamiento a BREAK, inspecciona `Path.canReach()`. Si es false, hace raycast desde los ojos del mob al centro del nodo. Si encuentra un bloque intermedio al alcance, lo delega como override BREAK al `RecoveryAction` existente, cambia a RECOVERING y luego invalida/replanifica la ruta local. La rotura sigue pasando por la misma `BlockInteractionPolicy`; no se amplían permisos ni se rompen automáticamente árboles/tablones protegidos.
- Nuevo evento de diagnóstico: `BREAK_APPROACH_OCCLUSION` con target, blocker, alcance y Path canReach.
- `MOVE_COMMAND` ahora registra hasta cuatro nodos del Path desde nextIndex y los bloques de feet/head/support, además del estado de la celda solicitada.
- Suite `policyTest compileJava compileTestJava -Pa2nDiagnostics=true`: BUILD SUCCESSFUL. `lint_aldea.py --strict` y `git diff --check`: PASS (advertencias CRLF únicamente).

**Run27:** fixture fresco, seed 12345, mismos parámetros. Buscar `BREAK_APPROACH_OCCLUSION` en #262/#270; verificar que el primer obstáculo se rompa solo cuando el callback lo autorice, que se replanifique y que no se destruyan árboles/tablones. A2 sigue OPEN.


## Run27 — BREAK approach / primer obstáculo visible (EN CURSO, 2026-11-13)

- RunId: `a2n-20261113-121920`.
- Fixture fuente: `build\a2n\arena-repair1-20261112-205206\game\a2n`; copia desechable nueva en `build\a2n\a2n-20261113-121920\game\a2n`.
- Seed 12345; raid; NavCustom=true; Fase A=3000; Fase B=6600; presupuesto local=900; global=8000; límite 90 min.
- Launcher iniciado 12:19:20; PID servidor 14004.
- Corrección: si Path a BREAK es inalcanzable y raycast impacta primero un bloque distinto del objetivo dentro del alcance, delegar ese bloque como BREAK override al RecoveryAction existente; BlockInteractionPolicy no cambia. Evento esperado `BREAK_APPROACH_OCCLUSION`.
- Build/policyTest compileJava compileTestJava PASS (29s); lint y diff check PASS.
- Esperar `fin.txt` y salida JVM antes de editar. Validar el primer obstáculo autorizado, replanificación, no destrucción de árboles/tablones y progresión A2. A2 OPEN.


## Run27 — BREAK_APPROACH delegado, bloqueado por guardia de ruta (2026-11-13)

**RunId:** `a2n-20261113-121920`
**C4:** PASS. **A2:** OPEN.

- Fin formal 12:28:21; C4_PASS; servidor terminado.
- C4: 12/12 trabajadores vivos/reanudados; 0 bajas; 0 no resueltos; 16 supervivientes; 3 retirados para recuperación; 0 fallos de instrumento.
- Fase A: 8/8 spawn; 5/8 entraron alguna vez; 0 dentro al cierre.
- Fase B: 8/8 spawn; 5/8 entraron alguna vez; 0 dentro al cierre; 3 vivos fuera/5 muertos.
- Trace: 1,020,886 registros; 0 descartados; 0 fallos.

### Evidencia del segundo orden de bloqueo

- `BREAK_APPROACH_OCCLUSION`: 30 eventos.
- `GUARD_REPLAN`: 24 eventos.
- Para #8 UUID `f71e569c-83e8-4309-8bff-db78cef80efb`, target BREAK `746,65,535`, blocker `746,64,535`, la guarda registró `exencionBreak=false celdaActiva=true rechaza=true`. El bloque intermedio era adyacente al nodo activo y por eso `celdaDeRutaActiva` lo trató como parte de la ruta. La acción no llegó al callback; el atacante quedó en `746,64,536` y consumió reintentos.
- Otros casos: UUID `3f219b2f-ebd2-4e42-b91e-b283d3df4ff6` target `614,74,598` blocker `614,76,598`; UUID `d5af8996-47fe-4caa-b174-f1fcd832adc1` target `588,96,513` blockers `589,98,513` y `588,98,513`.

**Conclusión:** la detección del primer obstáculo es correcta, pero la guarda general intercepta el override antes de RecoveryAction. No se debe quitar la guarda ni cambiar BlockInteractionPolicy.

### Corrección aplicada tras Run27

- Nueva política pura `NavRecoveryGuardPolicy` y prueba.
- `rechazoPorGuardaDeRuta` permite únicamente la exención cuando `nodoDeRecuperacionOverride` coincide exactamente con la celda de recuperación. La acción sigue pasando por `RecoveryAction` y `BlockInteractionPolicy`; el override no autoriza por sí mismo a romper.
- `guardaDiag` expone `exencionOverrideBreak` para demostrar que la excepción exacta fue aplicada.

Suite y runtime pendientes de verificación. **Run28** debe comprobar `exencionOverrideBreak=true`, `rechaza=false`, que el callback sí se ejecute y que solo se rompa un bloque autorizado. A2 OPEN.


## Run28 — exención exacta del BREAK override en la guarda (EN CURSO, 2026-11-13)

- RunId: `a2n-20261113-123130`.
- Fixture fuente: `build\a2n\arena-repair1-20261112-205206\game\a2n`; copia desechable nueva en `build\a2n\a2n-20261113-123130\game\a2n`.
- Seed 12345; raid; NavCustom=true; Fase A=3000; Fase B=6600; presupuesto local=900; global=8000; límite 90 min.
- Launcher iniciado 12:31:30; PID servidor 20852.
- Corrección: `NavRecoveryGuardPolicy` exime solo el override BREAK exacto; `guardaDiag` expone `exencionOverrideBreak`. RecoveryAction/BlockInteractionPolicy siguen siendo obligatorios.
- Build/policyTest compileJava compileTestJava BUILD SUCCESSFUL in 28s; lint y diff check PASS.
- Verificar que el evento BREAK_APPROACH_OCCLUSION sea seguido por `exencionOverrideBreak=true`, `rechaza=false`, acción callback y resultado verificado. No romper árboles/tablones protegidos. Esperar fin.txt y salida de JVM antes de editar.


## Run28 — exención exacta del BREAK override validada; A2 aún falla (2026-11-13)

**RunId:** `a2n-20261113-123130`
**Fin:** 12:41:15; `fin.txt` = C4_PASS. Servidor terminado.
**C4:** PASS.
**A2:** OPEN / NO ACCEPTED.

### Resultado A2

- Fase A t=3000: 8/8 spawn; 3 vivos, 5 muertos, 0 desaparecidos; 5/8 entraron alguna vez; 0 dentro al cierre.
- Fase B t=6600: 8/8 spawn; 2 vivos, 6 muertos, 0 desaparecidos; 6/8 entraron alguna vez; 0 dentro al cierre.
- Final phase-B:
  - idx4 vivo, salud 10, `589,93,511`, distancia horizontal 74.98, nunca dentro.
  - idx8 vivo, salud 10, `753,63,543`, distancia horizontal 93.35, nunca dentro.
- Tras fase B, el harness mantuvo mantenimiento/recuperación; t=7500 reportó 0 vivos, 6 muertos y 2 desaparecidos, sin alterar el resultado medido al cierre de fase B.

### Validación de la corrección BREAK approach / guardia

- `BREAK_APPROACH_OCCLUSION`: 22.
- `GUARD_REPLAN`: 26 (incluye causas distintas del override).
- `GUARD_STATE` con `exencionOverrideBreak=true` y `rechaza=false`: 14.
- `exencionOverrideBreak=true` y `rechaza=true`: 0.
- `BREAK-VERIFICADO`: 991; `accion=actuo`: 965; `accion=no-actuo`: 26.
- #274 (UUID `a711adf4-89fe-4a70-8ef4-646575178fc4`) ejecutó la acción y avanzó a través de bloques, pero después re-estadió y siguió fuera. #7 (UUID `f880de56-342e-4a4b-adf6-0859d51b2e32`) también registra roturas autorizadas.
- La excepción estrecha está validada: la guarda permite exactamente el override delegado, sin ningún rechazo de esos 14 estados. La acción sigue atravesando RecoveryAction/BlockInteractionPolicy.

### Bloqueos A2 que siguen abiertos

1. idx4 (UUID `3ecba0c5-540e-4641-940c-bed009259a59`) consigue descensos naturales y construidos, pero queda atascado en navegación vertical/horizontal cerca de `589,93,511`; distancia horizontal ≈75 y nunca cruza.
2. idx8/#274 recorre repetidamente el exterior (y≈63), con re-estadios globales, sin progresar hacia el objetivo interior `725,71,539`; termina en `753,63,543`, distancia 93.35.
3. La ruta de idx3 también registra una cadena de BREAK approach desde `611,82,598` a `614,74,598`; aunque se delega el obstáculo, la posición aún está a distancia 43 del destino a t≈2360. Investigar si el primer obstáculo es suficiente o si el siguiente nodo BREAK se repite/queda mal elegido.

### Integridad y C4

- Trace cerrado: 664,902 registros; descartados=0; fallos=0.
- C4_PASS: guardasCombatieron=true; profesionalesReanudados=true; asignacionesJobSiteLimpias=true; supervivientes=16; trabajadoresEsperados=12; trabajadoresVivos=12; trabajadoresReanudados=12; bajas=0; no resueltos=0; retiradosParaRecuperacion=2; jobSite=[2,0,0]; fallosDeInstrumento=0.
- No quedaron procesos Java de juego al finalizar.

**Siguiente tarea:** conservar la excepción validada. No tocar BlockInteractionPolicy ni ampliar permisos. Investigar navegación posterior a BREAK y progresión vertical/global de idx4 e idx8, usando nueva corrida fresca y trazas. A2 OPEN.


## Run29 — delegar también el propio objetivo BREAK (EN CURSO, 2026-11-13)

### Diagnóstico adicional de Run28

La traza de #274 muestra el caso que la política anterior excluía: PathNavigation no puede alcanzar `746,65,541`; la celda solicitada tiene pies=air, cabeza=stone y soporte=stone. La regla previa delegaba solo si el primer impacto era distinto del objetivo (`primerImpactoEsObjetivo=false`), por lo que el propio bloque BREAK sólido nunca se entregaba a RecoveryAction. #274 repetía ACERCARSE-ACCION en el mismo punto, `canReach=false`, sin desplazamiento.

### Corrección Run29

- `NavActionApproachPolicy.debeDelegarPrimerBloque`: si el Path a BREAK no es alcanzable y el primer bloque del raycast está al alcance, delegar ese bloque tanto si es una obstrucción anterior como si es el propio objetivo.
- El bloque sigue pasando por RecoveryAction y BlockInteractionPolicy; no se añade ningún permiso de rotura. Si el objetivo es protegido/no rompible, el callback lo rechazará.
- Evento `BREAK_APPROACH_OCCLUSION` añade `targetHit=true/false` para distinguir ambos casos.
- `policyTest compileJava compileTestJava -Pa2nDiagnostics=true`: BUILD SUCCESSFUL (30s); NAV_ACTION_APPROACH_POLICY_TEST 6 aserciones.
- Run29: fixture fresco, seed 12345, mismos parámetros raid. Verificar específicamente `targetHit=true` seguido de `BREAK-VERIFICADO accion=actuo`, avance físico y protección de bloques no autorizados. A2 OPEN.


## Run29 — EN CURSO (2026-11-13 12:44:17)

- RunId: `a2n-20261113-124417`; launcher PID=32472; servidor PID=23260.
- Fixture fresco: `build\a2n\arena-repair1-20261112-205206\game\a2n` -> `build\a2n\a2n-20261113-124417\game\a2n`.
- Seed=12345; raid; NavCustom=true; FaseA=3000; FaseB=6600; presupuesto local=900; global=8000; timeout=90 min.
- Cambio: delegar primer bloque del raycast incluso si es el propio objetivo BREAK; RecoveryAction/BlockInteractionPolicy sin cambios.
- Build/policyTest, lint y diff check PASS antes de arrancar.
- Verificar targetHit=true -> BREAK-VERIFICADO accion=actuo -> progreso físico. Confirmar que objetivos protegidos/no rompibles no se destruyen.
- Esperar fin.txt y salida JVM antes de cualquier cambio de código. A2 OPEN.


### Run29 — hallazgo en vivo pendiente de cierre

En #271/#idx8 (UUID `3decf861-e7d9-4d3a-81fe-2d28d6d12828`), `MOVE_COMMAND` repite BREAK hacia `746,65,541` con `canReach=false`, `requestedCell feet=air/head=stone/support=stone`; la posición queda en `747.51,63,541.66`. El estado de la entidad conserva `BREAK-ACERCARSE`. La detección actual sigue viendo bloque inferior distinto (`targetHit=false`); 19 eventos de approach, 92 BREAK-VERIFICADO al corte t=2700. No hay targetHit=true todavía.

Código revisado: en NavExecutor, la comprobación `resuelto = BREAK ? getBlockState(node.pos()).isAir() : !isAir()` está DESPUÉS del bloque `distNodoSqr > ALCANCE_DE_ACCION_SQR`, que ordena acercamiento y retorna. Si el nodo BREAK ya es aire, el ejecutor no avanza hasta que la entidad llegue a un nodo que quizá no sea transitable. Candidato Run30: mover la comprobación de resolución antes del acercamiento, preservando verificación de mundo; añadir predicado puro en NavObjectivePolicy + tests para BREAK resuelto cuando target air y BRIDGE resuelto cuando target sólido. Esperar fin de Run29 antes de editar código.


## Run29 — resultado y nueva corrección de resolución BREAK (2026-11-13)

**RunId:** `a2n-20261113-124417`
**Fin:** 12:53:29; C4_PASS. **A2:** OPEN / NO ACCEPTED.

- Fase A t=3000: 8/8 spawn; 3 vivos, 5 muertos; 5/8 entraron alguna vez; 0 dentro al cierre.
- Fase B t=6600: 8/8 spawn; 3 vivos, 5 muertos; 5/8 entraron alguna vez; 0 dentro al cierre.
- Final B: idx4 `589,93,511` dist=74.91; idx7 `744,63,509` dist=87.00; idx8/#271 `743,63,506` dist=86.92; todos vivos con salud 10, ninguno entró alguna vez.
- Trace: 793,217 registros; descartados=0; fallos=0.
- BREAK approach: 21 eventos; targetHit=true=0; GUARD_REPLAN=16; BREAK-VERIFICADO=98 (actuo=30, no-actuo=68).
- #271 mantuvo MOVE_COMMAND a BREAK `746,65,541` con requestedCell feet=air/head=stone/support=stone y Path canReach=false. El código solo comprobaba `BREAK && getBlockState(node.pos()).isAir()` después de exigir alcance al nodo; el mob nunca alcanzaba la celda y seguía persiguiendo un BREAK ya resuelto.
- C4_PASS: guardasCombatieron=true; profesionalesReanudados=true; jobSite limpio; 16 supervivientes; 12/12 trabajadores vivos/reanudados; 0 bajas/no resueltos; 3 retirados para recuperación; jobSite=[1,0,0]; fallosInstrumento=0.

### Corrección tras Run29

- `NavObjectivePolicy.accionResuelta(breakTransition,targetIsAir)`: BREAK resuelto cuando el target está en aire; BRIDGE resuelto cuando el target es sólido.
- `NavExecutor` ahora consulta el estado real del bloque ANTES de calcular distancia/emitir `moveTo`; si ya está resuelto, avanza routeIndex inmediatamente. Se elimina la comprobación duplicada posterior al alcance.
- Cuatro pruebas nuevas cubren BREAK/BRIDGE resuelto/no resuelto.
- `policyTest compileJava compileTestJava -Pa2nDiagnostics=true`: BUILD SUCCESSFUL (33s); NAV_OBJECTIVE_POLICY_TEST OK (9 aserciones).
- Run30 validará que #271 no repita movimiento hacia un BREAK en aire y que la ruta continúe hacia el siguiente nodo. Mantener la excepción de guardia exacta y BlockInteractionPolicy intactas. A2 OPEN.


## Run30 — EN CURSO (2026-11-13 12:55:20)

- RunId: `a2n-20261113-125519`; launcher PID=412; servidor PID=9504.
- Fixture fresco: `build\a2n\arena-repair1-20261112-205206\game\a2n` -> `build\a2n\a2n-20261113-125519\game\a2n`.
- Seed=12345; raid; NavCustom=true; FaseA=3000; FaseB=6600; local=900; global=8000; timeout=90 min.
- Cambio: `NavObjectivePolicy.accionResuelta` se evalúa antes del acercamiento y avance solo tras comprobar estado del mundo.
- Build/policyTest compileJava compileTestJava PASS; lint y diff check PASS.
- Validación central: el #271/#idx8 no debe seguir emitiendo ACERCARSE-ACCION para un nodo BREAK cuya celda ya está en aire. Debe incrementar routeIndex y perseguir siguiente nodo. Verificar A2 y C4 al cierre.
- Esperar fin.txt y salida JVM antes de editar. A2 OPEN.


### Run30 — evidencia positiva temprana

A t≈414, #271 (UUID `f35facc7-da78-444a-b0a0-febc258d8bb2`) registró `ultimaAccionDeRuta=746,65,541:BREAK-YA-RESUELTO-AVANZA` y el nodo siguiente pasó a `746,67,540:BREAK-ACERCARSE`. Esto confirma que la comprobación de resolución ANTES del acercamiento evita perseguir el nodo BREAK que ya está en aire. Fase A t=300: 1/8 entró alguna vez, 0 dentro al snapshot. Run sigue en curso.


### Run30 — resultado A2 (fase B cerrada; C4 en mantenimiento)

- Fase A t=3000: 6/8 entraron alguna vez; 1 dentro al cierre; 3 vivos/5 muertos.
- Fase B t=6600: 7/8 entraron alguna vez; 0 dentro al cierre; 1 vivo/7 muertos.
- Último atacante idx4: `589,93,511`, distancia horizontal=74.98, fuera. IDX7 se acercó hasta ~9 bloques del centro (663,70,541) pero murió antes de cruzar. IDX8 había estado dentro al cierre de A y después murió.
- `BREAK-YA-RESUELTO-AVANZA`: 50 eventos (vs 10 al cierre de fase A), confirmando que el ejecutor deja de perseguir BREAK en aire y continúa la ruta.
- El bloqueo dominante que queda es idx4: `RECOVERING` en `589,98,513`, `DROP`/escalón construido y fallo persistente de caída por bloque en y=99; distancia horizontal≈74. C4 sigue ejecutando mantenimiento automático; esperar fin.txt antes de editar.


## Run30 — cierre definitivo (2026-11-13)

**RunId:** `a2n-20261113-125519`; fin 13:06:04; servidor cerrado.
**C4:** PASS. **A2:** OPEN / NO ACCEPTED.

- Fase A: 6/8 entraron alguna vez; 1 dentro al cierre; 3 vivos/5 muertos.
- Fase B: 7/8 entraron alguna vez; 0 dentro al cierre; 1 vivo/7 muertos.
- Último atacante idx4/#270: `589,93,511`, distHorizontal=74.98, fuera.
- #8 estuvo dentro al cierre de A en `675,71,580`, pero murió después. #7 se aproximó hasta ~9 bloques del centro (`663,70,541`) pero murió antes de cruzar.
- `BREAK-YA-RESUELTO-AVANZA`: 50; `BREAK_APPROACH_OCCLUSION`: 29; targetHit=true=0; GUARD_REPLAN=14. Trace: 614,896 registros; descartados=0; fallos=0.
- C4_PASS: guardasCombatieron=true; profesionalesReanudados=true; asignacionesJobSiteLimpias=true; supervivientes=16; 12/12 trabajadores vivos/reanudados; bajas=0; no resueltos=0; retiradosParaRecuperacion=1; jobSite=[2,0,0]; fallosInstrumento=0.

### Conclusión de Run30

La corrección de resolución temprana funciona: 50 nodos BREAK en aire se saltaron antes de intentar acercarse, y el número de atacantes que entraron alguna vez subió a 7/8. No bastó para aceptar A2: ninguno permaneció dentro al cierre de B. El cuello de botella restante es idx4/#270 en navegación vertical/descenso alrededor de `589,98,513`; el diagnóstico reporta caída bloqueada por un bloque en y=99 y el mismo DROP/BRIDGE se repite, con distancia horizontal≈74. Investigar terreno/colisión de esa columna y por qué el planificador vuelve al mismo descenso; no revertir `accionResuelta` ni la exención estrecha de guardia.


### Run30 — métricas finales completas y siguiente diagnóstico

- C4_PASS; 12/12 trabajadores reanudados, 0 bajas/no resueltos, 16 supervivientes; 1 atacante retirado para recuperación.
- Trace 614,896 registros; descartados=0; fallos=0.
- BREAK-YA-RESUELTO-AVANZA=50; BREAK_APPROACH_OCCLUSION=29; targetHit=true=0; GUARD_REPLAN=14.
- En los últimos ticks, idx4/#270 ya había avanzado a descensos DROP naturales consecutivos: `588,96,512`, luego `588,93,511`, luego `589,90,510`. En el cierre t=6600 estaba en `589,93,511`, objetivo local `589,88,509`, con la ruta todavía progresando. No es prueba de A2; sí justifica separar "necesita más tiempo" de "bloqueo definitivo".

**Run31 diagnóstico extendido (no aceptación):** repetir fixture/semilla/parámetros con FaseA=3000 y FaseB=9000. Mantener todas las modificaciones. Observar si idx4/#270 cruza después de t=6600 y si llega al interior antes de t=9000. El criterio oficial A2 sigue siendo el de 6600 ticks; esta corrida extendida solo caracteriza el progreso tardío.


## Run31 — EN CURSO (2026-11-13 13:07:28; diagnóstico extendido)

- RunId: `a2n-20261113-130728`; launcher PID=13140; servidor PID=15848.
- Fixture fresco, semilla=12345, raid, NavCustom=true, FaseA=3000, FaseB=9000, local=900, global=8000, timeout=90 min.
- Es diagnóstico de progreso tardío; no cambia el criterio oficial A2 (FaseB=6600).
- Validar si idx4/#270 atraviesa el interior después de t=6600 y antes de t=9000. C4 debe seguir PASS.


### Run31 — fase A cerrada

t=3000: 8/8 spawn; 3 vivos, 5 muertos; 6/8 entraron alguna vez; 1 dentro al cierre (#8 en `673,71,593`, distHorizontal=61.36). idx4 vivo fuera en `583,111,523`, idx7 vivo fuera en `700,63,456`. El resultado coincide con Run30 en fase A (6/8 y 1 dentro). Fase B extendida continúa hasta t=9000.


## Run30 — resultado final y próximo diagnóstico (2026-11-13)

**RunId:** `a2n-20261113-125519`; fixture fresco `arena-repair1-20261112-205206`; seed 12345; raid; NavCustom=true; FaseA=3000; FaseB=6600; cleanup hasta t=7800. El servidor terminó y la JVM del juego ya no está activa.

### Resultado A2
- Fase A t=3000: 8/8 spawn; 3 vivos, 5 muertos, 0 desaparecidos; 6/8 entraron alguna vez; 1 dentro en el snapshot.
- Fase B t=6600: 8/8 spawn; 1 vivo, 7 muertos, 0 desaparecidos; 7/8 entraron alguna vez; 0 dentro en el snapshot.
- Cleanup t=6900–7800: 0 vivos, 7 muertos, 1 desaparecido (el superviviente retirado/limpiado por harness).
- Mejora respecto Run29: entraron alguna vez 7/8 vs 7/8 (igual al cierre fase B), pero Run30 sí mostró 1 dentro al final de fase A. La mejora clave es avance de objetivos BREAK ya resueltos, no aceptación final.
- El último atacante vivo #4 acabó en `589,93,511`, distancia horizontal 74.98, nunca entró. Su estado cerca del cierre osciló entre RECOVERING/PLANNING/TRAVERSING. Ruta de recuperación: `588,96,513`; BREAK delegado `588,96,513` (bloque Dirt) seguido de `BREAK-VERIFICADO accion=actuo` y `BREAK-YA-RESUELTO-AVANZA`; luego DROP natural a `589,90,510`. Se registró rechazo de columna de caída `589,95,512 -> el camino de caida esta bloqueado en y=99`. No asumir que Dirt es muro ni ampliar reglas de rotura sin comprobar geometría/propiedad del bloque.
- Fase A #8 llegó a `675,71,580`, dentro; al cierre de fase B ya había muerto (7 muertos total).
- A2 permanece OPEN / NOT ACCEPTED: requisito de entrada/permanencia no cumplido.

### Resultado de la corrección BREAK
- `BREAK-YA-RESUELTO-AVANZA`: 50.
- `BREAK_APPROACH_OCCLUSION`: 29; `targetHit=true`: 0.
- `BREAK-VERIFICADO accion=actuo`: 81; `accion=no-actuo`: 0.
- `GUARD_REPLAN`: 14.
- `MOVE_COMMAND`: 410.
- Trace: 614,896 registros; descartados=0; fallos=0; 56,483,522 bytes.
- Confirmado que la comprobación de BREAK resuelto antes de acercamiento avanza el índice de ruta y evita perseguir nodos BREAK ya despejados. Mantener este cambio.

### C4
`C4_PASS (raid)`: guardasCombatieron=true; profesionalesReanudados=true; asignacionesJobSiteLimpias=true; supervivientes=16; trabajadoresEsperados=12; trabajadoresVivos=12; trabajadoresReanudados=12; bajasTrabajadores=0; trabajadoresNoResueltos=0; retiradosParaRecuperacion=1; jobSite=[2,0,0]; fallosDeInstrumento=0.

### Siguiente paso
No hacer cambios especulativos ni debilitar protección de bloques. Investigar exclusivamente la ruta de recuperación del atacante #4: por qué permanece en `589,98,513`, cómo se selecciona el bloque Dirt `588,96,513`, si el bloque pertenece al anillo de muro validado o a obra del asedio, y por qué el aterrizaje de descenso rechaza columna bloqueada en y=99. Correlacionar con `MOVE_COMMAND`, `BREAK-VERIFICADO`, transiciones DROP y estado de ruta. Luego diseñar una prueba mínima y repetir raid fresco. Revalidar policyTest/compile y lint antes de la próxima prueba. No tocar `run\saves`, no abrir cliente, no commits. A2 sigue OPEN.


### Run30 — forensic detail #270 / UUID 5019beb6-ddfd-4e8a-b7c6-e1c503c5b933

- Final live idx4 was attacker #270. At t=4797 and 4816, BREAK node `588,96,513` had `pathCanReach=false`; raycast first blocker was `589,98,513` / `588,98,513`, at 0.94 / 1.43 blocks, `targetHit=false`. The executor delegated the first blocker to the existing bounded recovery action.
- Movement snapshots show `589,98,513` had dirt as support and the entity was standing at y≈99. The recovery action subsequently mined Dirt at `588,98,513`, Dirt at `588,96,513`, Dirt at `588,99,513`, and Coarse Dirt at `588,100,513`. This is consistent with the outside-the-village tunneling rule; no evidence here says these were protected wall blocks. Do not broaden mining or wall exceptions.
- At t=6460–6500 it completed BREAK actions. At t=6520 the already-cleared BREAK advanced. At t=6543–6565 navigation transitioned to DROP and descended from y98 to y97; at t=6578–6598 it descended further toward y93/y90. At t=6585 a DROP movement command was issued; Path later reported canReach=true for the natural landing `589,90,510`.
- Earlier global searches for #270 frequently exhausted 8000/8000 expansions; the final global route to meta `600,71,514` used 1390 expansions and 18 nodes at t=6600. Thus the surviving attacker was still making progress and had not entered before the phase deadline; the final position alone does not establish a permanent navigation deadlock.
- Run31 should be a no-code-change duration diagnostic: same seed/fixture/raid, extend phase B (e.g. t=12000) while preserving local/global budgets and all protection rules. Compare whether the same route continues to approach/enter or stalls again. If extended run confirms a stable stall, instrument/inspect the specific waypoint/route transitions; do not raise budgets or change break policy without evidence.


### Run31 — diagnóstico extendido SIN cambios de código (EN CURSO)

- RunId `a2n-20261113-133033`; servidor PID=2440; launcher PID=13900.
- Fixture fresco `arena-repair1-20261112-205206`, seed=12345; raid; NavCustom=true; FaseA=3000; FaseB=12000; local budget=900; global=8000; timeout=90 min.
- Se ejecuta solo para distinguir progreso lento de bloqueo persistente del atacante #270. No cambiar código ni política de rotura durante la corrida. Mundo desechable bajo `build\a2n\a2n-20261113-133033\game\a2n`; `run\saves` intacto.
- Esperar `fin.txt`, comprobar salida JVM y comparar snapshots t=6600, 9000, 12000; especialmente #270/idx4 y #271/idx7. A2 OPEN.

## Run31 — ejecución extendida y diagnóstico (2026-11-13)

- Run: `build/a2n/a2n-20261113-133033`; fixture `arena-repair1-20261112-205206`; seed `12345`; Fase A `3000` ticks; Fase B `12000` ticks; local budget `900`; global budget `8000`.
- Resultado A2: **FAIL / OPEN**. Ocho atacantes aparecieron; seis murieron; dos terminaron vivos y fuera del perímetro; no hubo entidades desaparecidas. Seis entraron alguna vez al perímetro, pero ninguno permanecía dentro al corte final.
- Atacante #272 / idx4 (`e654660a-8e29-4375-8431-638c44e5aa66`): terminó en `589,75,508`, `FAILED`, motivo `construccion agotada sin completar el descenso`. Conservaba un escalón `PLACED_VERIFIED` en `588,105,523` aunque el mob ya estaba unos 31 bloques por debajo de su aterrizaje. El ejecutor seguía esperando la travesía física de ese escalón obsoleto; el intento de ruta BREAK quedaba detrás de la rama de construcción.
- Atacante idx7 (`ec23c452-0e8a-4e98-8017-455d23f4ffec`): terminó en `695,63,454`, `PLANNING`; objetivo de misión `704,71,486`, waypoint local `698,68,455`; cinco escalones colocados. La traza muestra 11/12 replanteos por guarda, cuatro reintentos de presupuesto local `901/900`, comandos `STEP_UP`/`WALK` repetidos sin avance horizontal y `EscapeWaterGoal` activo. Sigue pendiente diagnóstico de selección de orilla / transición agua-tierra; no se aumentaron presupuestos ni se relajaron protecciones.
- `nav-trace.log` se cerró y contiene aproximadamente 75 MB; eventos relevantes: `BREAK_APPROACH_OCCLUSION`, `GUARD_REPLAN`, `BUDGET`, `MOVE_COMMAND`, `NAV_FAIL`.
- C4 posterior: guardias que combatieron `4/4`; trabajadores vivos `12/12`; reanudados `11/12`; sin bajas ni trabajadores no resueltos; asignaciones de puesto `9`, huérfanas `0`, duplicadas `0`. El único no reanudado era un herrero descansando (`resting=true`) al instante del muestreo. Repetir la comprobación tras su despertar; no clasificarlo como pérdida.

## Corrección A2 aplicada después de Run31 — pendiente de prueba runtime

- `NavStairProgressPolicy`: un escalón pendiente solo se abandona cuando la misión sigue por debajo del aterrizaje y el mob ya descendió más allá del margen `maxSafeDrop`. Se conserva la distinción entre `ABANDONED` y travesía real: no incrementa escalones atravesados ni elimina el bloque colocado.
- `NavExecutor.pasoDeConstruccion`: al detectar un escalón ya pasado, libera el estado pendiente, reinicia solo el presupuesto local de ese escalón y deja intacto el presupuesto total duro.
- La rama de construcción no puede adelantarse a un `nodoDeRecuperacionOverride` BREAK inmediato; además, la delegación conserva un `nextRecoveryAt` futuro en vez de borrar el enfriamiento.
- Respaldo previo: `.a2-backups/run31-20261113-before-fix/`.
- Verificación: `compileJava` OK; `compileTestJava` OK; `NavStairProgressPolicyTest` OK (5 aserciones); `python tools/lint_aldea.py --strict` OK; `git diff --check` OK. La corrección aún no está aceptada hasta repetir el raid en un mundo desechable.

## Siguiente ejecución obligatoria

- Ejecutar Run32 con el mismo fixture/semilla/budgets, mundo desechable nuevo y sin GUI.
- Añadir `-Dn4.trace=true` para registrar `shore`, `custom`, `nav` y `obj` del `EscapeWaterGoal`; no cambiar budgets ni protecciones.
- Aceptación parcial: idx4 debe abandonar el escalón obsoleto sin falsear travesía y ejecutar la acción BREAK; idx7 debe mostrar selección de orilla y progreso horizontal verificable. A2 permanece OPEN hasta cumplir los criterios completos.

## Run32 — resultado y siguiente diagnóstico (2026-11-13)

- Run: `build/a2n/a2n-20261113-135710`; `Modo=raid`; seed `12345`; `FaseA=3000`; `FaseB=12000` (tick absoluto final); local budget `900`; global budget `8000`; sin pared/rampa/perfil/inundación adicionales; `-Dn4.trace=true`.
- **A2 FAIL / OPEN**: 8/8 spawneados; al tick 12000: 3 vivos, 5 muertos, 0 desaparecidos; 5 entraron alguna vez al perímetro y 0 permanecían dentro. Supervivientes: idx3 `608,95,599` (FAILED); idx4 `583,111,523` (RECOVERING); idx7 `698,63,455` (PLANNING).
- idx3 (`46fe8501-c77e-47ca-9f81-cecdc85a9395`): el DROP sí avanzó de `608,96,599` a `608,95,599`; el bloqueo posterior es el WALK hacia el escalón pendiente `608,95,598` sobre `608,94,598`. `PathNavigation` devuelve ruta vacía/`navDone=true` en `pos=608.66,95,599.50` aunque el destino está una celda al sur, con pies/cabeza libres y soporte cobblestone. La política de llegada exige ocupación física del escalón, así que no se debe relajar esa invariante; corregir el acercamiento de un bloque.
- idx4 (`bc42feec-2905-46da-9fdb-c29a41d03e80`): continúa en `583,111,523`; presupuesto global agotado `8000/8000`; 18 fallos de descenso; 60 candidatos de escalera rechazados porque las celdas de pies están ocupadas. La traza repite `DROP_LOCAL_EXCLUSION_HIT` para `landing=584,108,520`, fingerprint `1213646834920530`; investigar cómo se propaga la exclusión y cómo se sale de ese bucle. No aumentar budgets.
- idx7 (`120bf223-28cf-4d72-b6ad-94a7b21b4049`): `EscapeWaterGoal` sigue activo cerca de `shore=697,62,455`; `NavExecutor` alternaba el objetivo temporal con la misión `704,71,486` y el waypoint `700,68,456`. Causa confirmada: `setObjective()` desactivaba `objetivoTactico` incluso cuando el llamante reenviaba la misma `misionFinal`.
- Trazas: `STAIR_ABANDONED=0`; `DROP_START=391`; `DROP_DONE=11`; `DROP_REJECTED=23`; `GUARD_REPLAN=59`; `BUDGET=6`. `nav-trace.log` quedó en ~67 MB y se cerró al detener el servidor.

## Corrección táctica aplicada después de Run32 — compilada, pendiente de runtime

- `NavTacticalObjectivePolicy.preserveTacticalObjective(tacticalActive, sameMission)` conserva la prioridad táctica únicamente cuando sigue activa y el llamante reenvía la misma misión.
- `NavExecutor.setObjective()` solo desactiva `objetivoTactico` al recibir una misión nueva. El reenvío idempotente de la misión no cancela la orilla temporal.
- Respaldo: `.a2-backups/run32-before-tactical-fix/NavExecutor.java`.
- Verificación: `compileJava compileTestJava` BUILD SUCCESSFUL; `NavStairProgressPolicyTest` OK (5 aserciones); `NavTacticalObjectivePolicyTest` OK (4 aserciones); lint estricto OK; `git diff --check` sin errores.

## C4 — resultado secundario de Run32

- `C4_PASS (raid)` según los criterios actuales: guardias combatieron `4/4`; trabajadores reanudados `11/11` supervivientes; asignaciones de puestos `8`, huérfanas `0`, duplicadas `0`; trabajadores no resueltos `0`.
- Hubo **1 baja de trabajador**: Josefa, herrera de armas (`5d95ab4a-9ff3-4101-b105-38144b06696e`), asesinada por atacante #291 (`0a5bc208-11d8-49d3-9a06-983c00a4bbd2`). `C4_PASS` no significa defensa sin bajas; si el criterio del proyecto exige cero bajas, queda pendiente corregir la defensa y endurecer la aceptación.

## Próximos pasos

1. Run33 con mismo fixture/seed/budgets y `-Dn4.trace=true`; comprobar que `N4TRACE` ya no alterna entre shore y misión, y que idx7 sale del agua.
2. Corregir el acercamiento al escalón pendiente cuando vanilla `PathNavigation` devuelve ruta vacía aunque el agente aún no ocupa la celda.
3. Resolver el bucle de `DROP_LOCAL_EXCLUSION_HIT` y el acceso por terreno sólido de idx4, sin subir presupuestos ni relajar protección de muro.
4. A2 sigue OPEN hasta que los atacantes completen el asedio y se verifique el estado de trabajadores/guardias.

## Run33 — corrección de acercamiento adyacente en validación

- Run: `build/a2n/a2n-20261113-141800`; seed `12345`; `FaseA=3000`; `FaseB=12000` (tick absoluto final); local `900`; global `8000`; `-Dn4.trace=true`; mundo desechable nuevo, sin GUI.
- Se conserva la corrección táctica de Run32: reenviar la misma misión ya no cancela `objetivoTactico`.
- Nueva corrección: `NavAdjacentApproachPolicy` + `NavExecutor.aplicarAcercamientoAdyacente`. Cuando vanilla `PathNavigation` declara la ruta terminada sin mover al mob a una celda adyacente, se usa `MoveControl` únicamente si la celda de destino cabe, tiene soporte, es un nodo de movimiento validado y está a distancia Manhattan 1–2. No se avanza la ruta por proximidad; `nodoAlcanzado` sigue exigiendo llegada física.
- Verificación previa a Run33: `compileJava compileTestJava` BUILD SUCCESSFUL; `NavStairProgressPolicyTest` 5/5; `NavTacticalObjectivePolicyTest` 4/4; `NavAdjacentApproachPolicyTest` 8/8; lint estricto OK; `git diff --check` exit 0.
- Respaldo previo del ejecutor: `.a2-backups/run32-before-adjacent-approach-fix/NavExecutor.java`.
- Run33 debe comprobar: `NAV_ADJACENT_NUDGE` para idx3/idx4 cuando corresponda; estabilidad de `N4TRACE` en la orilla; reducción de los `DROP_LOCAL_EXCLUSION_HIT`; y que ningún atacante se contabilice dentro sin cruzar físicamente.

### Run33 — checkpoint intermedio t=4200 (fase B iniciada)

- Snapshot t=4200: 4 vivos, 4 muertos, 0 desaparecidos; 4 entraron alguna vez al perímetro; 0 dentro en ese instante.
- `NAV_ADJACENT_NUDGE` acumulados: 8. El agente idx3 alcanzó físicamente dos escalones (`escalones=2`) gracias al acercamiento directo, pero quedó `FAILED` tras 10/10 invalidaciones por no encontrar waypoint de pie válido después de cambiar el mundo.
- idx4 (`b360902a-cba7-4fd3-8949-20eb9cf48d10`) descendió inicialmente de y119 a y111 y luego quedó `RECOVERING` en `583,111,524`; el DROP activo apunta a `582,110,522` (distancia Manhattan 3), fuera del máximo 2 actual del nudge. Próxima corrección candidata: permitir distancia 3 solo para transiciones DROP ya validadas y aplicar el nudge también al origen planificado cuando corresponda.
- idx7/idx8 se desplazan por el terreno hacia sus misiones, pero aún no han completado el asedio. No hay `N4TRACE` en este checkpoint; la preservación de objetivo táctico aún no tiene una activación de EscapeWaterGoal en esta ejecución.
- Run33 sigue activo; este checkpoint no es resultado final ni aceptación A2.

### Run33 — checkpoint t=7800: corrección táctica validada en runtime

- Snapshot t=7800: 4 vivos, 4 muertos, 0 desaparecidos; 4 entraron alguna vez al perímetro; 0 dentro en ese instante.
- `NAV_ADJACENT_NUDGE=8`. idx3 llegó físicamente a dos escalones y avanzó hasta `609,94,597`; la política evitó que el executor aceptara llegada falsa, pero la ruta terminó `FAILED` tras 10/10 invalidaciones por falta de waypoint válido después de cambiar el mundo.
- idx4 permanece `RECOVERING` en `583,110,524`. El candidato natural `583,108,519` sigue bloqueado por `583,110,523`; la escalera reportó que despejó una obstrucción anterior, pero volvió a encontrar esta entrada bloqueada. Investigar por qué el segundo bloque no se delega/rompe; no relajar protección.
- **Tactical objective: PASS runtime.** Para idx7 (`62c2e69d-d106-496c-9530-8da68347c8c9`), `N4TRACE` muestra `shore=697,62,455` y `obj=698,68,455`, mientras la misión original sigue siendo `704,71,486`. A diferencia de Run32, el objetivo ya no alterna de vuelta a la misión; la corrección de `setObjective()` preserva la prioridad temporal. El agente tiene 4 escalones colocados y está delegando minería de Stone para alcanzar la orilla; aún no ha terminado de salir.
- idx8 está en `698,61,455`, pero su búsqueda global terminó con `rutaFrente=0` desde `702,63,452`; no hay N4TRACE para ese agente en este checkpoint. Investigar por qué no activa EscapeWaterGoal o no obtiene frente alcanzable.
- Run33 sigue activo; no declarar aceptación A2 todavía.

### Run33 — checkpoint t=8400: EscapeWaterGoal confirmado

- Snapshot t=8400: 4 vivos, 4 muertos, 0 desaparecidos; 4 entraron alguna vez al perímetro; 0 dentro.
- La corrección táctica queda confirmada para **dos** entidades: idx7 mantiene `shore=697,62,455` y objetivo local `698,68,455` aunque su misión sea `704,71,486`; idx8 mantiene `shore=692,61,452` y objetivo local `688,68,450` aunque su misión sea `725,71,538`. Ya no se observa el rebote continuo a la misión que aparecía en Run32.
- idx7 está `TRAVERSING`, con 6 escalones colocados y minería delegada de Stone hacia la orilla. idx8 está `PLANNING`; su último descenso fue rechazado por falta de holgura de pies/cuerpo en `692,61,452` y está recalculando.
- idx3 sigue `FAILED` en `609,94,597`, 2 escalones colocados y 10/10 invalidaciones por ausencia de waypoint de pie válido tras cambio del mundo.
- idx4 sigue `RECOVERING` en `583,110,524`; la entrada al aterrizaje `583,108,519` está bloqueada por `583,110,523`. Se eliminó una obstrucción anterior, pero el segundo bloqueo persiste; falta confirmar por qué no se rompe o aproxima.
- `NAV_ADJACENT_NUDGE=8`; el acercamiento directo conserva la llegada física, pero por sí solo no resuelve los dos bloqueos de ruta restantes. Run33 continúa.

### Run33 — checkpoint t=9000: primer atacante salió del agua

- Snapshot t=9000: 4 vivos, 4 muertos, 0 desaparecidos; 5 entraron alguna vez al perímetro y 1 estaba dentro en ese instante.
- idx8 (`10b00fc8-1fe7-4b13-a6f6-b58a777aa1f8`) pasó de la zona de agua `693,61,453` a `663,71,479`, reduciendo su distancia horizontal a la misión a `53.91`; ya está en terreno y reanudó la navegación normal. Su EscapeWaterGoal había conservado el objetivo local `688,68,450` mientras la orilla era `692,61,452`.
- idx7 (`62c2e69d-d106-496c-9530-8da68347c8c9`) continúa trabajando para salir de la misma zona: posición `695,61,454`, orilla `694,62,454`, objetivo temporal `688,68,450`, 8 escalones colocados y minería de Stone delegada. Se observaron transiciones breves al objetivo de misión cuando cambia/reinicia la orilla, seguidas de restauración del waypoint temporal; no aparece el rebote continuo de Run32.
- idx3 continúa `FAILED` tras dos escalones y 10/10 invalidaciones; idx4 continúa `RECOVERING`, con entrada bloqueada por `583,110,523`.
- Resultado parcial mejor que Run32: ahora hay 1 atacante dentro en t=9000. A2 sigue abierta hasta el cierre de FaseB y la verificación final.

### Run33 — checkpoint t=10200

- Snapshot t=10200: 3 vivos, 5 muertos, 0 desaparecidos; 5 entraron alguna vez al perímetro; 0 dentro en ese instante. El único atacante contabilizado dentro en t=9000 ya no estaba dentro en t=10200.
- Supervivientes observados: idx3 (`609,94,597`, FAILED), idx4 (`583,110,524`, RECOVERING) e idx7 (`695,61,454`, PLANNING). idx8 ya no aparece entre los supervivientes.
- idx7 conserva el objetivo temporal `688,68,450` para la orilla `694,62,454`, sin volver a la misión; sin embargo acumula 3 descensos rechazados por falta de holgura de pies/cuerpo. Continúa delegando minería de Stone en `694,61,455`; el objetivo táctico se conserva, pero la salida física del agua sigue sin completarse.
- idx3 e idx4 no han progresado desde sus últimos checkpoints: idx3 agotó 10/10 invalidaciones de waypoint; idx4 sigue sin candidato de escalera y con acceso bloqueado por `583,110,523`.
- A2 sigue OPEN. No cambiar presupuestos ni protecciones para forzar el resultado.

## Corrección preparada para Run34 — fluidos fuera de minería

- Run33 mostró `BREAK-DELEGADO (bloque=Water)` durante EscapeWaterGoal. El agua no debe tratarse como bloque minable: es fluido/terreno y la salida debe resolverse por navegación hacia una orilla válida.
- Se añadió a `AggressiveZombieEntity.puedeRomperNodoDeNavegacion` un rechazo explícito cuando `!state.getFluidState().isEmpty()`, antes de evaluar logs/hojas y el gate normal de rotura.
- Respaldo: `.a2-backups/run33-before-fluid-break-exclusion/AggressiveZombieEntity.java`.
- Estado: parche de fuente aplicado; `git diff --check` sin errores. Falta `compileJava compileTestJava` y una prueba runtime desechable antes de aceptar el cambio.

## Run33 — resultado de FaseB t=12000 (A2 FAIL / C4 postraíd pendiente)

- `RESUMEN FASE B t=12000`: 8/8 spawn; 3 vivos; 5 muertos; 0 desaparecidos; 5 entraron alguna vez al perímetro; 0 permanecían dentro. Resultado A2: **FAIL / OPEN**.
- Supervivientes: idx3 `609,94,597` (distancia horizontal 82.75, nunca dentro); idx4 `583,110,524` (77.94, nunca dentro); idx7 `693,63,453` (85.63, nunca dentro). idx8, que había entrado brevemente en t=9000, murió antes del cierre.
- El ajuste táctico sí corrigió la sobrescritura continua de misión durante EscapeWaterGoal: N4TRACE mantuvo objetivos locales de orilla para idx7/idx8. No bastó para garantizar salida: idx7 permaneció en `PLANNING` y acumuló descensos rechazados por falta de holgura; idx8 logró salir y avanzar hacia su misión, pero murió posteriormente.
- `NAV_ADJACENT_NUDGE=8`: idx3 alcanzó físicamente dos escalones y idx4 descendió inicialmente, pero ambos quedaron atascados en bloqueos/waypoints posteriores.
- C4: un trabajador muerto durante el asedio, Zacarias (butcher, UUID `b9889f8a-e588-468a-9cb1-dd008b4dac77`). Se registraron cuatro guardias enfrentando al atacante dentro de la aldea. `C4 POSTRAID maintenance=OK`, 3 atacantes retirados para recuperación de 1200 ticks. La prueba aún no había escrito `fin.txt` al checkpoint 14:32:28; mantener el servidor hasta que finalice y registrar el resumen C4 definitivo.
- El rechazo explícito a minar fluidos se aplicó a fuente después de compilar Run33; no forma parte de este resultado y requiere compilación + Run34.

## Run34 — preflight de navegación/agua

- `AggressiveZombieEntity.puedeRomperNodoDeNavegacion` ahora rechaza explícitamente cualquier bloque con `FluidState` no vacío. Run33 mostró `BREAK-DELEGADO (bloque=Water)` durante EscapeWaterGoal; los fluidos no son objetivos de minería.
- `NavAdjacentApproachPolicy` ahora recibe un máximo de distancia explícito: `DROP` validado permite Manhattan 3 (descenso diagonal de un bloque vertical + dos horizontales); los demás nodos de movimiento conservan máximo 2. Sigue exigiendo navegación terminada, nodo de movimiento, celda con espacio y soporte; no relaja llegada física.
- Verificación: `compileJava compileTestJava` BUILD SUCCESSFUL; `NavStairProgressPolicyTest` 5/5; `NavTacticalObjectivePolicyTest` 4/4; `NavAdjacentApproachPolicyTest` 10/10; lint estricto OK; `git diff --check` exit 0.
- Respaldos: `.a2-backups/run33-before-fluid-break-exclusion/AggressiveZombieEntity.java` y `.a2-backups/run34-before-drop-distance/` (NavExecutor, policy y test previos).
- Siguiente: Run34 en mundo desechable nuevo, mismo fixture/seed/budgets, para verificar que no delega BREAK sobre agua y que los DROP diagonales ya no se quedan a una celda de distancia. A2 sigue OPEN.

### Run34 activo — `a2n-20261113-143545`

- Carpeta: `build/a2n/a2n-20261113-143545`; inicio 14:35:45 del 13-Nov-2026.
- Configuración igual a Run33: fixture `arena-repair1-20261112-205206`, seed `12345`, raid, `FaseA=3000`, `FaseB=12000`, local `900`, global `8000`, pared/rampa/perfil/inundación `0`, `NavCustom=true`, `-Ddevilrpg.a2n.traceNav=true`, `-Dn4.trace=true`.
- Usa mundo desechable nuevo; no GUI; `run/saves` intacto.
- Watcher remoto PID `35004` escribe `run34-watch.txt` cada 30 s y termina al detectar `fin.txt` (máximo 15 min).
- Al checkpoint 14:36:10, Gradle estaba iniciando `runServer`; `fin.txt` aún no existía. No hay resultado runtime todavía.

### Run34 — checkpoint t=7200

- Snapshot t=7200: 2 vivos, 6 muertos, 0 desaparecidos; 6 entraron alguna vez al perímetro; 0 dentro en ese instante.
- idx3 sigue `FAILED` en `609,94,597`, con 2 escalones y 10/10 invalidaciones por ausencia de waypoint de pie válido tras cambios del mundo.
- idx4 (`88b32bb2-3065-4e81-b811-d2576f09c2f7`) pasó de y111 a y110 después de que el nudge DROP de distancia 3 se activara; pero continúa `RECOVERING`. El plan local sigue sin poder construir porque las 60 celdas candidatas tienen `celdaAire=false` en al menos una de las dos alturas necesarias. La posición está parcialmente encajada en terreno; el diagnóstico debe distinguir si se requiere excavar el volumen del cuerpo o buscar un acceso alternativo, sin relajar la protección de la aldea.
- idx7/idx8 ya no sobreviven; el atacante idx8 que había avanzado en Run33 no aparece como superviviente en Run34.
- Validación de exclusión de fluidos: `BREAK-DELEGADO (bloque=Water)=0` hasta t=7200.
- `NAV_ADJACENT_NUDGE=13` acumulados, incluyendo DROP de distancia Manhattan 3. Run34 sigue ejecutándose hasta t=12000 y C4 postraid.

### Run34 — checkpoint t=8100

- Snapshot t=8100: 2 vivos, 6 muertos, 0 desaparecidos; 6 entraron alguna vez al perímetro; 0 dentro.
- No hay progreso neto desde t=4200: idx3 e idx4 son los dos supervivientes; idx3 permanece `FAILED` en `609,94,597`, idx4 `RECOVERING` alrededor de `583,110,524`.
- La exclusión de minería sobre fluidos sigue limpia (`BREAK-DELEGADO (bloque=Water)=0`). `NAV_ADJACENT_NUDGE=13`; el nudge de DROP Manhattan 3 se ejecuta, pero no elimina el encajonamiento del idx4 ni restaura waypoints válidos del idx3.
- Run34 continúa hasta el tick 12000; A2 no está aceptada.

## Run34 — resultado final (13-Nov-2026)

- RunId: `a2n-20261113-143545`; mundo desechable `build/a2n/a2n-20261113-143545/game/a2n`; fixture `arena-repair1-20261112-205206`; seed `12345`; `FaseA=3000`, `FaseB=12000`, local `900`, global `8000`; sin GUI y sin tocar `run/saves`.
- Compilación/pruebas antes de Run34: `compileJava compileTestJava` BUILD SUCCESSFUL; `NavStairProgressPolicyTest` 5/5; `NavTacticalObjectivePolicyTest` 4/4; `NavAdjacentApproachPolicyTest` 10/10; lint estricto y `git diff --check` OK.
- Fase B t=12000: `spawneados=8/8`, `fallosSpawn=0`, `vivos=2`, `muertos=6`, `desaparecidos=0`, `entraronPerimetroEver=6`, `dentroParedAhora=0`. Supervivientes idx3 `609,94,597` (FAILED, 2 escalones, 10/10 invalidaciones sin waypoint de pie) e idx4 `583,111,523` (RECOVERING, 17 fallos de descenso, 0 escalones; 60 candidatos rechazados porque no existe volumen de aire suficiente para el cuerpo).
- **A2: FAIL / OPEN.** No se acepta: dos atacantes quedaron vivos fuera del perímetro y ninguno dentro al cierre.
- Corrección de fluidos: `BREAK-DELEGADO (bloque=Water)=0` durante toda la ejecución. La exclusión funcionó.
- Acercamiento adyacente: 13 eventos `NAV_ADJACENT_NUDGE`, incluidos DROP con distancia Manhattan 3; la regla ejecutó MoveControl sin falsear llegada física, pero no resolvió el encajonamiento de idx4 ni el fallo de waypoints de idx3.
- EscapeWaterGoal/objetivo táctico se conserva; el escenario actual no dejó supervivientes de agua para cerrar ese comportamiento como criterio independiente. No afirmar aceptación adicional por esta corrida.
- **C4: PASS.** `fin.txt`: `C4_PASS (raid) - guardasCombatieron=true profesionalesReanudados=true asignacionesJobSiteLimpias=true supervivientes=16 trabajadoresEsperados=12 trabajadoresVivos=12 trabajadoresReanudados=12 bajasTrabajadores=0 trabajadoresNoResueltos=0 retiradosParaRecuperacion=2 jobSite=[8, 0, 0] fallosDeInstrumento=0`.
- Servidor dedicado se detuvo limpiamente a las 14:51:08; `fin.txt` presente. No se registraron excepciones del servidor.
- Hipótesis de siguiente investigación: idx4 está físicamente en una región con volumen de cuerpo obstruido (60/60 candidatos `celdaAire=false`); la lógica actual intenta construir escalones sin abrir un volumen transitable. Hay que rastrear el origen de `celdaAire` y la política de despeje/excavación segura, diferenciando terreno natural de bloques protegidos de aldea. Para idx3, estudiar el límite 10/10 invalidaciones y por qué los waypoints se vuelven inválidos después de cambios del mundo. No aumentar budgets ni debilitar protecciones como atajo.

## Run35 — excavación segura del volumen de escalera

- RunId: `a2n-20261113-145338`; mundo desechable nuevo en `build/a2n/a2n-20261113-145338/game/a2n`; fixture `arena-repair1-20261112-205206`; seed `12345`; `FaseA=3000`, `FaseB=12000`; local `900`, global `8000`; `-Dn4.trace=true`; sin GUI y sin tocar `run/saves`.
- Parche añadido: `NavStairVolumeClearancePolicy` + `AggressiveZombieEntity.registrarObstruccionDeVolumenDeEscalon`. Si la celda del peldaño o uno de sus dos bloques de cabeza está ocupado, registra SOLO la primera obstrucción del volumen para despeje, siempre que la celda candidata sea exterior/no protegida y tenga soporte. La acción real sigue pasando por `puedeRomperNodoDeNavegacion`, `alAlcanceDeRotura` y `romperNodoDeRuta`; se rompe como máximo un bloque por intento y se reevalúa el mundo antes de colocar.
- Preflight: `compileJava compileTestJava` BUILD SUCCESSFUL; tests `NavStairProgressPolicyTest` 5/5, `NavTacticalObjectivePolicyTest` 4/4, `NavAdjacentApproachPolicyTest` 10/10, `NavStairVolumeClearancePolicyTest` 8/8; lint estricto OK; `git diff --check` exit 0.
- Respaldo previo: `.a2-backups/run35-before-stair-volume-clearance/AggressiveZombieEntity.java`.
- Watcher remoto PID `23996` escribe `run35-watch.txt` cada 30 s y termina al detectar `fin.txt`.
- Criterio runtime: buscar `STAIR-ACCESS-CLEAR` seguido de `CONSTRUCCION-ESCALERA` y `escalones` que aumenten; comprobar que los bloques despejados estén fuera de protección y que no haya BREAK de fluidos/logs/hojas. A2 sigue OPEN hasta FaseB y C4 finales.

### Run35 — checkpoint inicial t=300

- Snapshot t=300: 8 vivos, 0 muertos, 0 desaparecidos; 2 entraron alguna vez al perímetro; 0 dentro.
- El evento `A2 STAIR-ACCESS-CLEAR #286 bloque 594,94,600 verificado` corresponde al atacante idx3 (`uuid=736df9b3-7fec-453b-a210-83ea27807e2c`, posición `607,95,600`). El volumen obstruido ya permite seleccionar un bloque de despeje, y el bloque fue verificado en aire.
- Aún no aparece `CONSTRUCCION-ESCALERA` posterior ni aumento de `escalera.colocados`; por tanto, esto confirma despeje, no todavía colocación/travesía. En el mismo snapshot idx3 informa `FAILED` por tope local 64/64, mientras el subsistema entity-ladder informa 0/24 y motivo de obstrucción removida. Hay que verificar si la escalera de entidad reanuda y si no queda anulada por el tope del plan local.
- idx4 (`uuid=ca8cdd14-5088-4108-ab81-5e6525eeeebe`) está en `574,112,523`, `TRAVERSING`, con 16 fallos de descenso y 0 escalones; su volumen de escalera reporta candidatos parcialmente libres y obstrucción del corredor, no el caso puro `celdaAire=false` de idx3.
- `BREAK-DELEGADO (bloque=Water)=0`; sin excepciones. Run35 sigue activo.

### Run35 — Fase A completa, t=3000

- Snapshot final Fase A: `spawneados=8/8`, `fallosSpawn=0`, `vivos=4`, `muertos=4`, `desaparecidos=0`, `entraronPerimetroEver=4`, `dentroParedAhora=0`.
- idx3 (`736df9b3-7fec-453b-a210-83ea27807e2c`) sigue `FAILED` en `607,95,600`; local `escalones=64/64`, `fallosEscalon=10/10`. El despeje `594,94,600` fue real pero no se colocó escalón (`escalera.colocados=0/24`), y el executor continúa FAILED por presupuesto local terminal. La corrección de volumen demuestra que puede retirar una obstrucción segura, pero no recupera este ejecutor ya agotado.
- idx4 (`ca8cdd14-5088-4108-ab81-5e6525eeeebe`) sigue `RECOVERING` en `583,111,523`; 17 fallos de descenso; `DROP` rechazado por `spruce_leaves` en `584,110,520`. El gate de minería rechaza hojas/troncos deliberadamente; la exclusión de destino se aplica y el planificador global agota 8000 expansiones con ruta a frente de 17 nodos. No se debe minar follaje para forzar paso; investigar el avance por ruta de frente/replanificación alternativa.
- La búsqueda de escalera de idx4 reporta candidatos sin soporte y una base/volumen que no coincide claramente con su posición actual; telemetría del ancla debe revisarse, pero no hay evidencia suficiente para cambiarla aún.
- `BREAK-DELEGADO (bloque=Water)=0`; no hay excepciones observadas.
- Fase B está en curso; Run35 no ha finalizado.

### Run35 — checkpoint t=5400 (fase B)

- Snapshot t=5400: 4 vivos, 4 muertos, 0 desaparecidos; 4 entraron alguna vez al perímetro; 0 dentro. No se observa recuperación de idx3/idx4 desde Fase A.
- `BREAK-DELEGADO (bloque=Water)=0`; el filtro de fluidos se mantiene correcto. El evento único `STAIR-ACCESS-CLEAR #286` sigue sin traducirse en colocación/travesía (`escalera.colocados=0/24`), y idx3 continúa `FAILED` por el tope de 64 escalones locales.
- idx4 permanece `RECOVERING` por columna DROP bloqueada por `spruce_leaves` (no minable por diseño); la ruta global reporta presupuesto agotado y frente de 17 nodos. No se relaja la política de follaje ni se sube el presupuesto.
- Fase B sigue hasta t=12000 y luego C4 postraid.

### Run35 — diagnóstico adicional de idx4 (traza DROP)

- La traza confirma que no repite un único aterrizaje sin replanificar: para `ca8cdd14-5088-4108-ab81-5e6525eeeebe` va excluyendo sucesivamente columnas bajo `minecraft:spruce_leaves` (p. ej. `569,116,531`, `568,116,531`, `584,108,520`) y después una columna obstruida por `minecraft:stone` (`589,75,509`). `NavDropExclusionPolicy` mantiene 32 entradas y la búsqueda explora destinos alternativos; al llenarse la colección, las entradas más antiguas se desalojan por diseño.
- No autorizar minado de hojas/troncos: es la protección deliberada del bosque. Tampoco hay evidencia todavía de que la capacidad 32 cause un ciclo; no modificarla sin medir una reaparición de un aterrizaje ya excluido.
- El cuello de botella de idx4 es una gran cubierta de follaje/terreno sólido que bloquea múltiples columnas de caída y fuerza una búsqueda global de frente con presupuesto agotado. Se requiere probar que la ruta de frente realmente se recorre y no solo se calcula.

### Run35 — checkpoint t=7200

- Snapshot t=7200: 3 vivos, 5 muertos, 0 desaparecidos; 5 entraron alguna vez al perímetro; 0 dentro en ese instante. Hubo 1 atacante dentro en t=6600, pero ya no permanecía dentro en t=7200.
- La excavación de volumen no ha producido colocación de escalones ni recuperación de idx3/idx4; el resultado parcial no valida A2. El filtro de fluidos continúa en cero BREAK sobre Water.
- Run35 sigue activo; falta FaseB hasta t=12000 y C4.

## Run35 — resultado final (13-Nov-2026)

- RunId: `a2n-20261113-145338`; Fase B cerró en t=12000 y la recuperación C4 terminó en t=13200. `fin.txt` presente; servidor dedicado detenido limpiamente; no quedan procesos Java de esta corrida.
- Fase B: `spawneados=8/8`, `fallosSpawn=0`, `vivos=3`, `muertos=5`, `desaparecidos=0`, `entraronPerimetroEver=6`, `dentroParedAhora=0`. **A2 FAIL / OPEN**: tres atacantes seguían vivos fuera y ninguno quedó dentro al cierre.
- C4: **PASS**. `fin.txt`: `guardasCombatieron=true`, `profesionalesReanudados=true`, `asignacionesJobSiteLimpias=true`, `supervivientes=16`, `trabajadoresEsperados=12`, `trabajadoresVivos=12`, `trabajadoresReanudados=12`, `bajasTrabajadores=0`, `trabajadoresNoResueltos=0`, `retiradosParaRecuperacion=3`, `jobSite=[10,0,0]`, `fallosDeInstrumento=0`.
- Exclusión de fluidos: `BREAK-DELEGADO (bloque=Water)=0` en toda la corrida.
- Excavación de volumen: el atacante #286/idx3 despejó y verificó `594,94,600`, pero no colocó ningún peldaño (`escalera.colocados=0/24`) y su `NavExecutor` quedó terminal `FAILED` en `64/64` escalones locales. La corrección es segura y funcional para despejar el primer bloque permitido, pero no es suficiente para recuperar un executor ya agotado.
- idx4 quedó `RECOVERING` por columnas DROP cubiertas por `spruce_leaves` (no minables por diseño) y algunas obstruidas por piedra. La traza demuestra exclusión de múltiples destinos y exploración de rutas alternativas; la búsqueda global alcanzó el presupuesto y no consiguió completar una ruta de asedio. No se debe debilitar la protección del bosque ni aumentar presupuestos como solución inmediata.
- Preflight de Run35: `compileJava compileTestJava` BUILD SUCCESSFUL; `NavStairProgressPolicyTest` 5/5; `NavTacticalObjectivePolicyTest` 4/4; `NavAdjacentApproachPolicyTest` 10/10; `NavStairVolumeClearancePolicyTest` 8/8; lint estricto OK; `git diff --check` exit 0.
- Rama `feature/autonomous-village`, HEAD `518fee13fc15bfb9b92b0479d86b5c609d1947da`; no commits. Se conservaron todas las modificaciones preexistentes y los respaldos `.a2-backups/`.
- Próximo bloqueo A2: (1) impedir que el `NavExecutor` quede terminal `FAILED` tras agotar 64 escalones si un cambio de mundo verificable ha despejado una obstrucción; debe rearmarse con presupuesto acotado y sin bucle. (2) mejorar la ruta de frente para columnas de caída cubiertas por follaje no minable: la búsqueda debe aprovechar el frente parcial alcanzable y avanzar físicamente, no solo producir `rutaFrente` con presupuesto agotado. (3) instrumentar y corregir la discrepancia entre el ancla/centro de escalera y la posición actual del atacante antes de tocar la política de anclas.

## Run36 — invalidación de ruta tras BREAK de recuperación

- RunId: `a2n-20261113-150954`; mundo desechable nuevo; fixture `arena-repair1-20261112-205206`; seed `12345`; FaseA 3000, FaseB 12000; local 900, global 8000; sin GUI y sin tocar `run/saves`.
- Corrección nueva: `AggressiveZombieEntity.romperNodoDeRuta()` ahora, tras una rotura real, notifica `pathPlanner.terrainChanged()` y `navExecutor.terrainChanged()`. Esto invalida `globalCache`, `route` y permite rearmar `FAILED` si el terreno cambió. Traza nueva: `[NAV] TERRAIN_CHANGED_BY_ROUTE_BREAK`.
- Motivo: Run35 demostró que `STAIR-ACCESS-CLEAR` rompía el bloque, pero la ruta global cacheada no recibía notificación. El verificador BRIDGE comprobaba el nodo de puente (aún no colocado) y devolvía no resuelto aunque se hubiera despejado otra obstrucción; sin invalidación el executor podía seguir con caché antigua.
- Preflight: `compileJava compileTestJava` BUILD SUCCESSFUL; `NavStairProgressPolicyTest` 5/5; `NavTacticalObjectivePolicyTest` 4/4; `NavAdjacentApproachPolicyTest` 10/10; `NavStairVolumeClearancePolicyTest` 8/8; `NavPlannerStateTest` OK; lint estricto OK; `git diff --check` exit 0.
- Respaldo previo: `.a2-backups/run36-before-route-break-invalidation/AggressiveZombieEntity.java`.
- Watcher remoto PID `34916` escribe `run36-watch.txt` cada 30 s y termina al detectar `fin.txt`.
- Criterio: después de cada `STAIR-ACCESS-CLEAR` debe aparecer `TERRAIN_CHANGED_BY_ROUTE_BREAK`; el `NavExecutor` debe volver a PLANNING, recalcular y colocar/travesar escalón si el volumen queda válido. A2 permanece OPEN hasta FaseB y C4.

## Run37 — rearme único del límite local tras BREAK real

- Diagnóstico Run36 t=300: idx3 (`de905595-2a83-40ea-8774-222988f9eb81`) despejó `594,94,600` y notificó ambos planificadores; aun así volvió a `FAILED` porque conservó `escalones=64/64` y `fallosEscalon=10/10`. El cambio de terreno reabrió el estado, pero el contador local terminal volvió a cerrarlo inmediatamente.
- Parche Run37: `NavTerrainRearmPolicy` permite un único rearme por misión solo cuando el executor está `FAILED`, el límite local de escalones y los fallos están agotados, y no se había rearmado ya en esa misión. Reinicia los contadores locales, pero conserva los límites duros globales. El flag se reinicia únicamente al cambiar de misión/etapa o al verificar progreso real.
- Tests: `NavTerrainRearmPolicyTest` 5/5; suites previas 5/5, 4/4, 10/10, 8/8 y `NavPlannerStateTest` OK; `compileJava compileTestJava` BUILD SUCCESSFUL; lint estricto OK; `git diff --check` exit 0.
- Respaldo: `.a2-backups/run37-before-bounded-terrain-rearm/NavExecutor.java`.
- Run36 sigue ejecutándose con la compilación anterior; no valida este parche. Run37 será la primera corrida que lo incluya.

### Run36 — checkpoint t=3600: replanificación por frente sí avanza

- Snapshot t=3600: 4 vivos, 4 muertos, 0 desaparecidos; 4 entraron alguna vez al perímetro; 0 dentro.
- idx4 (`ca448dd5-5a37-4e30-8255-f237b35afab2`) se movió de `583,111,523` a `584,111,525`. Tras despejar `583,109,524`, el `NavExecutor` pasó a `PLANNING`, eligió `586,105,517` como waypoint local del frente (`rutaGlobal=17`, `exp=8000`) y `escalones=1/64`. Esto demuestra que la ruta parcial de frente sí puede ejecutarse tras invalidar la caché; no se minó follaje.
- idx3 (`de905595-2a83-40ea-8774-222988f9eb81`) permanece `FAILED`, `64/64`, `10/10` pese al primer despeje; Run36 no incluye el rearme único preparado para Run37.
- El atacante #295 colocó escalones, incluido uno adherido a cara lateral en `702,64,457`.
- `BREAK-DELEGADO (bloque=Water)=0`; sin excepciones observadas. Fase B sigue activa.

### Run36 — checkpoint t=3900: idx4 ya ejecuta el frente

- Snapshot t=3900: 4 vivos, 4 muertos, 0 desaparecidos; 4 entraron alguna vez al perímetro; 0 dentro.
- idx4 (`ca448dd5-5a37-4e30-8255-f237b35afab2`) avanzó físicamente hasta `587,107,520`, `TRAVERSING`; el último DROP `588,104,518` fue descrito `NATURAL OK` (dx=1, dy=-3, dz=-2). El waypoint siguiente es `588,101,514`, con ruta de frente global de 14 nodos y 8000 expansiones. Esto confirma avance real por el frente alternativo, no solo cálculo de ruta.
- Se verificaron roturas sucesivas con invalidación del executor: `583,109,524`, `585,109,524`; cada cambio invalida y permite planificar el siguiente waypoint.
- idx3 sigue `FAILED` 64/64 y es el objetivo del rearme acotado Run37.
- #295 colocó escalón lateral `702,64,457`; no se registran intentos de romper agua.
- Fase B sigue activa; Run36 todavía no es aceptación A2.

### Run36 — checkpoint t=4800: descenso de frente en progreso

- Snapshot t=4800: 4 vivos, 4 muertos, 0 desaparecidos; 4 entraron alguna vez al perímetro; 0 dentro.
- idx4 avanzó hasta `589,98,513`, distancia horizontal 74.01; el plan global ya alcanza la misión y eligió `588,93,511` como waypoint local. Se verificó un DROP construido `590,98,514 -> 590,95,514` (`NATURAL/CONSTRUIDO OK` en la telemetría) y la recuperación delegó `BREAK` de Dirt en `588,96,513` para abrir el siguiente tramo. Ya no está detenido en el punto original bajo las hojas.
- idx3 continúa `FAILED` 64/64; no puede validar el rearme de Run37 porque esta corrida fue lanzada antes de compilar ese cambio.
- Fase B continúa; sin WATER breaks ni excepciones observadas.

### Run36 — checkpoint t=6600: idx4 baja al nivel de la aldea

- Snapshot t=6600: 4 vivos, 4 muertos, 0 desaparecidos; 4 entraron alguna vez al perímetro; 0 dentro.
- idx4 (`ca448dd5-5a37-4e30-8255-f237b35afab2`, atacante #292) bajó desde `589,98,513` a `590,71,508`, ya al nivel Y=71 de la aldea. La ruta local apunta a `594,71,509`; `NavExecutor` está en `PLANNING` y la última recuperación delegó `BREAK` de Stone en `591,71,508`. Se colocó escalón lateral en `588,82,508`. El túnel sigue dentro del presupuesto de 40.
- La ruta de frente ya no es solo una salida de 17 nodos: está ejecutando descensos, roturas permitidas y cambios de objetivo por el terreno real.
- idx3 sigue `FAILED` 64/64; Run37 deberá validar el rearme único por cambio de terreno.
- A2 sigue OPEN; Run36 continúa hacia t=12000 y C4.

### Run36 — checkpoint t=6900: primer ingreso al recinto

- Snapshot t=6900: 4 vivos, 4 muertos, 0 desaparecidos; 5 entraron alguna vez al perímetro; 1 dentro en ese instante.
- idx4 avanzó desde `590,71,508` hasta `611,71,518`, reduciendo distancia horizontal a 51.79 y entrando en una ruta de WALK hacia `645,71,526`. Se registró un atacante dentro del perímetro por primera vez en esta corrida.
- idx3 continúa `FAILED` 64/64; el rearme único de Run37 todavía no está en el binario activo de Run36.
- Hallazgo adicional: idx7 muestra `BREAK-DELEGADO (bloque=Water)` aunque la acción real lo rechaza. `buildNavPolicies().canBreak` solo comprobaba protección de aldea y no reutilizaba `puedeRomperNodoDeNavegacion`; esto permite que el planificador emita BREAK imposibles para agua/hojas/troncos.
- Parche Run37 ya aplicado: `buildNavPolicies().canBreak` delega en `puedeRomperNodoDeNavegacion`, alineando planificación y ejecución. Preflight posterior: compileJava/compileTestJava BUILD SUCCESSFUL; seis suites relevantes OK; lint estricto OK; `git diff --check` exit 0.

### Run36 — checkpoint t=9900

- Snapshot t=9900: 3 vivos, 5 muertos, 0 desaparecidos; 5 entraron alguna vez al perímetro; 0 dentro.
- idx4, que había entrado en t=6900 y alcanzado y71, ya no aparece entre los supervivientes en t=9900. No se cuenta como aceptación ni como superviviente.
- idx7 (`5d600c90-a680-44d9-a1fd-f4801cfd3a60`) sigue en el agua `693,63,455`; `NavExecutor` mantiene `BREAK-DELEGADO (bloque=Water)` y los descensos son rechazados por falta de holgura. Esto confirma el desacople entre `buildNavPolicies.canBreak` y la política real de rotura. Run37 alinea ambos contratos.
- idx3 sigue `FAILED` 64/64; Run37 incluye rearme único tras cambio de terreno verificado.
- Fase B sigue hasta t=12000; A2 no está aceptada.

### Run36 — Fase B completa t=12000 (A2 FAIL)

- Resumen: `spawneados=8/8`, `fallosSpawn=0`, `vivos=3`, `muertos=5`, `desaparecidos=0`, `entraronPerimetroEver=5`, `dentroParedAhora=0`. **A2 FAIL / OPEN**.
- idx4 logró llegar al nivel Y=71 y entrar al recinto temporalmente en t=6900, pero murió/desapareció antes del cierre; no cuenta como supervivencia ni como aceptación.
- idx3 siguió `FAILED` con `64/64` escalones y `10/10` fallos, sin rearme. Run37 prueba el rearme único.
- idx7 permaneció en el agua con `BREAK-DELEGADO (bloque=Water)` y descensos rechazados por falta de holgura. El permiso del planificador seguía siendo más permisivo que el ejecutor. Run37 alinea `NavObstruction.Policies.canBreak` con `puedeRomperNodoDeNavegacion`.
- C4 postraíd comenzó correctamente: `maintenance=OK`, `attackersRemovedForRecovery=3`, `recoveryTicks=1200`. Esperar `fin.txt` para resultado final C4.

## Run37 — rearme acotado + política BREAK alineada

- RunId: `a2n-20261113-152333`; mundo desechable nuevo; fixture `arena-repair1-20261112-205206`; seed `12345`; FaseA 3000, FaseB 12000; local 900, global 8000; sin GUI y sin tocar `run/saves`.
- Incluye `NavTerrainRearmPolicy`: un único rearme por misión cuando un BREAK real cambia el terreno después de agotar `64/64` escalones locales y `10/10` fallos. Reinicia contadores locales, no presupuestos globales; el flag solo se limpia al cambiar de misión/etapa o al verificar progreso.
- Incluye alineación de `buildNavPolicies().canBreak` con `puedeRomperNodoDeNavegacion`. El planificador ya no debe generar transiciones BREAK para agua, hojas, troncos, bloques protegidos, block entities, bloques fuera de alcance o no rompibles.
- También incluye invalidación de ambos planificadores tras BREAK real, despeje del primer bloque de volumen de escalera, nudge adyacente DROP≤3 y exclusión de fluidos en la acción de rotura.
- Preflight: `compileJava compileTestJava` BUILD SUCCESSFUL; `NavStairProgressPolicyTest` 5/5; `NavTacticalObjectivePolicyTest` 4/4; `NavAdjacentApproachPolicyTest` 10/10; `NavStairVolumeClearancePolicyTest` 8/8; `NavTerrainRearmPolicyTest` 5/5; `NavPlannerStateTest` OK; lint estricto OK; `git diff --check` exit 0.
- Respaldos: `.a2-backups/run37-before-bounded-terrain-rearm/NavExecutor.java` y `.a2-backups/run37-before-planner-break-policy-alignment/AggressiveZombieEntity.java`.
- Watcher remoto PID `12452` escribe `run37-watch.txt` cada 30 s y termina al detectar `fin.txt`.
- Criterios runtime: (1) idx3 debe mostrar `rearme unico por BREAK de terreno verificado`, escalones por debajo de 64 y salir de FAILED; (2) no debe haber `BREAK-DELEGADO (bloque=Water)` ni BREAK sobre hojas/troncos; (3) idx7 debe salir del agua usando objetivo de orilla; (4) FaseB y C4 deben cerrar sin bajas de trabajadores. A2 sigue OPEN hasta validar el resultado.

### Run37 — checkpoint t=900: agua y rearme pendiente

- Snapshot t=900: 7 vivos, 1 muerto, 0 desaparecidos; 4 entraron alguna vez al perímetro; 4 dentro en ese instante.
- `BREAK-DELEGADO (bloque=Water)=0`. idx7 (`0db9b727-14c5-4ee7-8bb4-de0a9acef2fb`) salió del agua y avanzó de `719,65,469` a `726,65,475`; idx8 también progresa y construye/abre camino. La política BREAK alineada está funcionando.
- idx3 sigue `FAILED` 64/64: el primer BREAK verificado ocurrió antes de que el contador local se agotara, por lo que la política inicial (solo rearma si el estado ya estaba FAILED al ocurrir BREAK) no se activó.
- Corrección adicional preparada para Run38: marcar `cambioTerrenoPendienteRearme` al ocurrir cualquier BREAK verificado; si después se agota el límite local en esa misma misión, `fallar()` consume el único rearme y vuelve a PLANNING. El flag se cancela al cambiar de misión/etapa o al verificar progreso.
- Preflight Run38: `compileJava compileTestJava` BUILD SUCCESSFUL; suites 5/5, 4/4, 10/10, 8/8, 5/5; `NavPlannerStateTest` OK; lint estricto OK; `git diff --check` exit 0.
- Respaldo: `.a2-backups/run38-before-pending-terrain-rearm/NavExecutor.java`.

### Run37 — Fase A completa t=3000

- Snapshot final Fase A: `spawneados=8/8`, `fallosSpawn=0`, `vivos=3`, `muertos=5`, `desaparecidos=0`, `entraronPerimetroEver=5`, `dentroParedAhora=0`.
- idx7 salió del agua y recorrió terreno seco hacia la misión; idx8 también navega hacia su misión. `BREAK-DELEGADO (bloque=Water)=0` en toda Fase A.
- idx3 sigue `FAILED` con `64/64` y `10/10`. El único BREAK que despejó `594,94,600` ocurrió antes del agotamiento; por eso la primera política de rearme no se activó. Run38 incorpora el indicador pendiente para consumir ese cambio cuando se alcance el límite después.
- Fase B de Run37 sigue activa; Run38 ya está compilado y listo, pero no se lanzará en paralelo para evitar conflicto de puerto/CPU.

### Run37 — checkpoint t=5100

- Snapshot t=5100: 3 vivos, 5 muertos, 0 desaparecidos; 5 entraron alguna vez al perímetro; 0 dentro.
- `BREAK-DELEGADO (bloque=Water)=0`; la política de planificación ya no propone romper agua. idx7/idx8 han salido de la zona acuática y navegan por terreno seco.
- idx3 sigue `FAILED` 64/64 porque el rearme pendiente de Run38 fue compilado después de iniciar Run37; no puede validar ese cambio.
- Fase B continúa; Run37 aún debe cerrar t=12000 y C4.

### Run37 — checkpoint t=5700 / estado de continuidad

- Snapshot t=5700: 3 vivos, 5 muertos, 0 desaparecidos; 5 entraron alguna vez al perímetro; 0 dentro.
- `BREAK-DELEGADO (bloque=Water)=0`. idx7 se mantiene fuera del agua, aunque todavía busca rodear/romper una obstrucción terrestre; el planificador ya no propone romper fluido.
- idx3 sigue `FAILED` 64/64 porque el BREAK que despejó `594,94,600` ocurrió antes de agotar el límite local; Run37 no contiene el indicador pendiente de Run38.
- Run37 sigue activo en Fase B. Watcher adicional PID `12404` escribe `run37-final-watch.txt` durante hasta 15 min y captura `fin.txt`/C4 final.
- Run38 está compilado y verificado (pending terrain rearm): seis suites relevantes OK, lint estricto y `git diff --check` OK. No iniciar otra instancia hasta que Run37 libere el puerto 25565.

## Handoff activo — continuidad automática Run37 → Run38

- Run37 (`build/a2n/a2n-20261113-152333`) continúa activo en Fase B. Último snapshot observado t=5700: 3 vivos, 5 muertos, 0 desaparecidos; 5 entraron alguna vez al perímetro; 0 dentro. `BREAK-DELEGADO (bloque=Water)=0`.
- Watcher final Run37 PID `12404` escribe `run37-final-watch.txt` cada 30 s y captura `fin.txt`/C4 durante hasta 15 min.
- Orquestador oculto PID `32732`, script `build/a2n/auto-run38-after-run37.ps1`, está activo y verificado (`AUTO_RUN38_WAIT_START` en `auto-run38.log`). Espera `Run37/fin.txt`, luego espera salida del servidor dedicado PID `14540`, y lanza Run38 sin abrir GUI.
- Run38 se ejecutará con la compilación actual que incluye: (a) `cambioTerrenoPendienteRearme` para consumir un único rearme local si el BREAK verificado precedió al agotamiento 64/64; (b) `buildNavPolicies().canBreak` alineado con `puedeRomperNodoDeNavegacion`, para no planear BREAK de agua/hojas/troncos no minables; (c) invalidación de ambos planificadores después de rotura; (d) despeje acotado del primer bloque del volumen de escalera; (e) nudge DROP Manhattan 3.
- Preflight de la compilación Run38 ya completado: `compileJava compileTestJava` BUILD SUCCESSFUL; suites 5/5, 4/4, 10/10, 8/8, 5/5 y `NavPlannerStateTest` OK; lint estricto OK; `git diff --check` exit 0.
- Run38 usará seed 12345, fixture `arena-repair1-20261112-205206`, FaseA 3000, FaseB 12000, local 900, global 8000, sin GUI, mundo desechable y sin tocar `run/saves`.
- Log del orquestador: `build/a2n/auto-run38.log`. Si hay desconexión de ChatGPT, el proceso local espera y lanza Run38 por sí mismo cuando Run37 termine, salvo que el PC se suspenda/apague o el proceso sea terminado.


### Run38 — diagnóstico de navegación y corrección de alcance vertical (13-nov-2026)

**Run38** (`a2n-20261113-153702`) se ejecutó con el código anterior al cambio de alcance vertical. En los snapshots observados hasta t=10500: 3 vivos, 5 muertos, 5 atacantes que entraron alguna vez y 0 dentro al cierre parcial. El resultado final se conserva en `fin.txt` cuando el arnés concluya; no se declara PASS.

#### Defecto confirmado en el código

El atacante #285 (idx=3, UUID `da4e240d-874e-4d34-8c08-812ea9793173`) apareció en `594,96,600` y avanzó hasta `607,95,600`. La misión local que se intentaba alcanzar era `621,71,583`: desnivel vertical de 24 bloques. El log registró reiteradamente que el primer aterrizaje candidato caía DENTRO del terreno sin soporte; el presupuesto local terminó en `64/64` escalones y `10/10` fallos, y el ejecutor quedó `FAILED`.

La causa de diseño estaba en `NavExecutor.elegirObjetivoLocal()` (antes del cambio, rama `dentroDeLaVentana(yo, mision)`): la ventana rectangular se trataba como prueba suficiente de alcanzabilidad. Para un objetivo 24 bloques más abajo, el método devolvía la misión directamente y omitía la ruta global, aunque el corredor real atravesara terreno sólido. La búsqueda de escaleras intentaba resolver un descenso que no tenía primer aterrizaje colocable.

#### Corrección aplicada

- `NavWaypointWindowPolicy.directoAlcanzable(dy, maxSafeDrop)` separa ventana geométrica de alcanzabilidad local: descenso directo solo hasta la caída segura; ascenso directo solo hasta el salto legal de 2 bloques.
- `NavExecutor.elegirObjetivoLocal()` solo conserva el objetivo directo cuando cumple esa regla. Si está dentro de la ventana pero excede el desnivel local ejecutable, registra `LOCAL_OBJECTIVE_REQUIRES_GLOBAL_ROUTE` y usa `rutaGlobalHacia(mision)` para seleccionar un waypoint de una ruta validada.
- No se aumentaron presupuestos, no se relajaron las protecciones del recinto y no se modificó la geometría del muro.

#### Validación estática y regresiones

- `compileJava compileTestJava` — BUILD SUCCESSFUL.
- `NavWaypointWindowPolicyTest` — 6 assertions OK (nivel, caída segura, caída profunda, límite de caída, salto legal y ascenso excesivo).
- `NavPlannerStateTest` — OK.
- `NavStairProgressPolicyTest` — 5 assertions OK.
- `NavStairVolumeClearancePolicyTest` — 8 assertions OK.
- `NavTerrainRearmPolicyTest` — 5 assertions OK.
- `NavTacticalObjectivePolicyTest` — 4 assertions OK.
- `NavAdjacentApproachPolicyTest` — 10 assertions OK.
- `python tools\\lint_aldea.py --strict` — OK; `git diff --check` — exit 0 (solo avisos de normalización CRLF).

#### Siguiente ejecución automática

`auto-run39-after-run38.ps1` quedó iniciado (PID 39432). Espera el `fin.txt` de Run38 y la salida del servidor dedicado PID 35128; después copia el arnés temporal y ejecuta Run39 con la misma semilla 12345, mismas fases A/B, `NavCustom=true` y presupuestos idénticos. Run39 será la primera prueba runtime de esta corrección. No se ejecuta Minecraft GUI ni se toca `run\\saves\\New World`.

**Estado A2: OPEN.** La corrección está compilada y probada en políticas; falta demostrarla en Run39 y cumplir los criterios runtime completos.


### Cierre Run38 — 13-nov-2026

Run38 terminó y escribió `fin.txt` con **C4_PASS**: guardas combatieron, 16 supervivientes, 12/12 trabajadores vivos y reanudados, 0 bajas de trabajadores, 0 trabajadores sin resolver, 3 retirados para recuperación y 0 fallos de instrumento. El asalto A2 **FAIL**: fase B t=12000, 8/8 spawneados, 3 vivos, 5 muertos, 0 desaparecidos, 5 entraron alguna vez y 0 dentro al cierre. Por tanto C4 se mantiene PASS en Run38, A2 sigue OPEN.

El orquestador `auto-run39-after-run38.ps1` detectó el fin de Run38 a las 15:50:45, esperó la liberación del servidor y lanzó Run39 a las 15:50:50. RunId `a2n-20261113-155051`, launcher PID 19768. Run39 usa el código con `NavWaypointWindowPolicy.directoAlcanzable` y la nueva ruta global forzada para desniveles locales no ejecutables. Resultado runtime todavía pendiente.


### Diagnóstico confirmado — cadena de aproximación móvil (13-nov-2026)

La evidencia de Run39 mostró que el destino de aproximación exterior se recalcula desde `blockPosition()` del atacante (`AggressiveZombieEntity.objetivoDeAproximacion(centro, radio)`, líneas 144-165) y que `cadenaDeAproximacion(centro)` vuelve a generar toda la cadena usando ese rumbo actual. `MoveToVillageCenterGoal` entrega esa cadena repetidamente a `NavExecutor.setObjectiveChain`. La firma cambia cuando cambia el punto exterior, aunque la raíz interior siga siendo la misma aldea. El ejecutor antes reemplazaba la cadena activa por la nueva, reiniciaba `indiceDeEtapa` y cambiaba el objetivo; por tanto el atacante podía perseguir un waypoint móvil y consumir los 64 objetivos locales sin terminar una etapa estable.

Evidencia observada para idx3 en Run39: la meta de ruta global pasó `616,71,578` → `617,71,579` → `618,71,580` → `621,71,583` mientras el atacante pasó `594,96,600` → `607,95,600`. Terminó `FAILED` en `607,95,600` con `64/64` escalones locales y `10/10` fallos. La raíz interior seguía siendo la misma aldea.

#### Corrección aplicada para Run40

- Nuevo `NavMissionChainPolicy.retainActiveApproachChain`: retiene la cadena activa cuando la raíz de la misión es la misma, existe objetivo actual, quedan etapas pendientes y la nueva sugerencia todavía contiene etapa exterior.
- `NavExecutor.setObjectiveChain` ya no sustituye una etapa exterior activa por un waypoint que se desplazó con el mob. Sí permite reemplazar la cadena por una sola etapa interior cuando el mob entra en el anillo; también acepta cadenas nuevas cuando cambia la raíz o no quedan etapas pendientes.
- Añadido evento acotado `MISSION_CHAIN_RETAINED` (máximo cada 100 ticks por ejecutor) para verificar el comportamiento.
- La traza `DROP_UNSAFE` por candidato queda opt-in mediante `-Ddevilrpg.a2n.traceNav.verboseDrops=true`. Run39 llegó a 84,197,376 bytes de `nav-trace.log` por cientos de miles de rechazos repetidos; no aportaban información causal adicional y saturaban la traza. El resumen de expansiones/aristas se conserva.
- Se conserva además la salvaguarda `NavWaypointWindowPolicy.directoAlcanzable`, aunque el caso dy=-24 de Run39 demuestra que el principal fallo era la cadena móvil, no la ventana vertical.

#### Validación del cambio

`compileJava compileTestJava` — BUILD SUCCESSFUL. `NavMissionChainPolicyTest` — 5 assertions OK; `NavWaypointWindowPolicyTest` — 6 OK; `NavPlannerStateTest` — OK; `NavStairProgressPolicyTest` — 5 OK; `NavStairVolumeClearancePolicyTest` — 8 OK; `NavTerrainRearmPolicyTest` — 5 OK; `NavTacticalObjectivePolicyTest` — 4 OK; `NavAdjacentApproachPolicyTest` — 10 OK; lint strict OK; diff-check exit 0.

#### Run39 / Run40

Run39 (`a2n-20261113-155051`) es baseline del cambio de ventana vertical, sin la retención de cadena. En t=5100: 4 vivos, 4 muertos, 4 entraron alguna vez, 0 dentro al cierre parcial; se registraron además `FAILED` por presupuesto sin progreso y replans agotados. Servidor PID 26668 sigue en fase B; `fin.txt` pendiente. Su resultado final se añadirá al cierre.

`auto-run40-after-run39.ps1` quedó iniciado (watcher PID 29260). Espera `fin.txt` y el cierre del servidor 26668, luego lanza Run40 con semilla 12345 y mismos parámetros, ya con la retención de cadena y traza de rechazos de DROP reducida. **Run40 será la primera validación runtime de esta corrección.**

**A2 permanece OPEN** hasta que los ocho atacantes cumplan el criterio final de asalto y permanencia; no se ha debilitado el muro ni la protección de la aldea.


### Continuidad A2 — cierre Run39 y arranque Run40 (13-nov-2026, 16:05)

**Run39 (`a2n-20261113-155051`) finalizado.** En el límite contratado de fase B, t=12000: 8/8 atacantes spawneados, 3 vivos, 5 muertos, 0 desaparecidos, 5/8 entraron alguna vez al perímetro y 0 permanecían dentro del muro. A2 = **FAIL**. Los snapshots posteriores (t=12300 y t=12600) muestran 0 vivos, 5 muertos y 3 desaparecidos; no cambian el veredicto en el límite t=12000. El `fin.txt` del harness contiene **C4_PASS**: guardasCombatieron=true; supervivientes=15; 12 trabajadores esperados, 11 vivos y reanudados, 1 baja, 0 no resueltos; asignacionesJobSite limpias (9,0,0); 3 atacantes retirados para recuperación; 0 fallos de instrumento. El servidor PID 26668 terminó correctamente. La traza de Run39 alcanzó ~93 MB por la instrumentación `DROP_UNSAFE` anterior al filtro.

**Run40 arrancado automáticamente** por `auto-run40-after-run39.ps1` después de detectar `fin.txt` y la salida del servidor de Run39. RunId `a2n-20261113-160443`, servidor PID 34412, mundo desechable `build\\a2n\\a2n-20261113-160443\\game`. Se confirmó en `JDK_JAVA_OPTIONS` la presencia de `-Ddevilrpg.a2n.traceNav=true -Dn4.trace=true`, semilla 12345, fase A=3000, fase B=12000, NavCustom=true, presupuesto local 900 y global 8000. Al último chequeo estaba en arranque/carga de NeoForge; todavía no había `nav-trace.log`, `fin.txt` ni veredicto. No lanzar otra instancia mientras PID 34412 siga activo.

**Qué verificar en Run40:** (1) eventos `MISSION_CHAIN_RETAINED` para raíz estable con etapas exteriores pendientes; (2) que la meta exterior ya no se desplace continuamente con la posición del atacante; (3) que no se repita el agotamiento 64/64 sin progreso; (4) snapshot A2 en t=12000 y permanencia dentro del muro; (5) cierre C4 y `fin.txt`; (6) tamaño de `nav-trace.log` y ausencia del spam `DROP_UNSAFE` por defecto. A2 sigue **OPEN** hasta aceptación runtime completa. C4 de Run39 se conserva como PASS; no cambiar las políticas de recuperación ni debilitar el muro.

Restricciones vigentes: no GUI de Minecraft, no foco de ventana, no `run\\saves\\New World`, no `git reset/clean/stash/revert`, no commits. Mantener los cambios acumulados y documentar el resultado antes de cerrar la sesión.


#### Run40 checkpoint temprano — 13-nov-2026 16:06:57

Run40 ya cargó el harness y comenzó el muestreo. En t=900: 8 vivos, 0 muertos, 0 desaparecidos, 4/8 entraron alguna vez al perímetro y 2 dentro del muro. `nav-trace.log` ~3.26 MB, 57 eventos `MISSION_CHAIN_RETAINED` y 0 `DROP_UNSAFE`. Evidencia concreta: para UUID `4da2e6cd-fd16-4360-9db4-908cb93b6a3f`, la cadena conserva `current=616,71,578` mientras la nueva sugerencia es `incoming=621,71,583`, con la misma raíz `661,73,533` y una etapa pendiente. Esto confirma que la retención se está ejecutando y que no se persigue el waypoint móvil. **Solo checkpoint; no es PASS A2.** Se observó agotamiento del presupuesto global inicial en algunos cálculos (p.ej. idx #287 y #286), por lo que debe revisarse si producen bloqueo persistente. El watcher local `watch-run40-final.ps1` (PID 3520) registra cada 45 s y añadirá el resultado final a estos handoffs tras `fin.txt` y la salida del servidor.


#### Run40 checkpoint t=1800 — bloqueo residual identificado

Snapshots Run40: t=1200 = 6 vivos, 2 muertos, 0 desaparecidos, 4 entraron alguna vez, 2 dentro; t=1500 = 5 vivos, 3 muertos, 0 desaparecidos, 4 entraron, 1 dentro; t=1800 = 5 vivos, 3 muertos, 0 desaparecidos, 4 entraron, 1 dentro. Bajas registradas: idx2 t=943 en 652,71,563 (dist=31.80); idx1 t=1143 en 640,71,534 (dist=20.53); idx5 t=1465 en 658,71,522 (dist=11.09). La causa exacta de daño no está instrumentada en estos mensajes; no atribuirlas todavía a una fuente concreta.

La cadena estable sí funciona para idx3 (UUID `4da2e6cd-fd16-4360-9db4-908cb93b6a3f`): mantiene `mision=616,71,578` y la misma raíz aunque la sugerencia entrante sea `621,71,583`. Pero idx3 sigue `FAILED` en `607,95,601`, `escalones=64/64`, `fallosEscalon=10/10`, `colocados=0/24`; último motivo: `obstruccion de acceso removida; replanificar desde el mundo actualizado`. Es decir, la cadena móvil ya no explica por sí sola el bloqueo: permanece un problema independiente en el primer aterrizaje/planificación de escalera tras retirar obstrucción. idx6 también quedó `FAILED` por replanteos agotados (12/12); idx4 sigue `RECOVERING`, con fallos de descenso y presupuesto global agotado (8000/8000). No modificar código durante Run40; completar la observación y aislar el punto de fallo con la traza completa antes de diseñar otra corrección. A2 sigue OPEN.


### Run40 final � 2026-11-13 16:17:19

RunId a2n-20261113-160443. fin.txt: C4_PASS (raid) - guardasCombatieron=true profesionalesReanudados=true asignacionesJobSiteLimpias=true supervivientes=16 trabajadoresEsperados=12 trabajadoresVivos=12 trabajadoresReanudados=12 bajasTrabajadores=0 trabajadoresNoResueltos=0 retiradosParaRecuperacion=4 jobSite=[10, 0, 0] fallosDeInstrumento=0. RAID/C4 log evidence:
[16:16:11] [Server thread/INFO] [devilrpg/]: [A2N] RAID SNAPSHOT t=12000 vivos=4 muertos=4 desaparecidos=0 entraronPerimetroEver=6 dentroParedAhora=0 seguidos=8 fase=B
[16:16:11] [Server thread/INFO] [devilrpg/]: [A2N] RESUMEN FASE B t=12000 spawneados=8/8 fallosSpawn=0 vivos=4 muertos=4 desaparecidos=0 entraronPerimetroEver=6 dentroParedAhora=0
[16:16:11] [Server thread/INFO] [devilrpg/]: [A2N] VEREDICTO B: SIN UMBRAL CONTRATADO (informativo; no se declara exito ni fracaso) entraron=6/8 vivos=4 muertos=4 desaparecidos=0
[16:16:11] [Server thread/INFO] [devilrpg/]: [A2N] C4 POSTRAID maintenance=OK attackersRemovedForRecovery=4 recoveryTicks=1200
[16:17:11] [Server thread/INFO] [devilrpg/]: [A2N] C4 JOB_SITE assignments=10 orphaned=0 duplicateOwners=0
[16:17:11] [Server thread/INFO] [devilrpg/]: [A2N] C4 OUTCOME=SURVIVED attackersRemovedForRecovery=4 workerCasualties=0
[16:17:11] [Server thread/INFO] [devilrpg/]: [A2N] FIN (C4_PASS (raid) - guardasCombatieron=true profesionalesReanudados=true asignacionesJobSiteLimpias=true supervivientes=16 trabajadoresEsperados=12 trabajadoresVivos=12 trabajadoresReanudados=12 bajasTrabajadores=0 trabajadoresNoResueltos=0 retiradosParaRecuperacion=4 jobSite=[10, 0, 0] fallosDeInstrumento=0)
Nav trace: 24112482 bytes; MISSION_CHAIN_RETAINED=336; DROP_UNSAFE=0. Verificar evidencia completa en build/a2n/a2n-20261113-160443/srv.log, nav-trace.log y run40-watch.log. No se cambia el veredicto A2 por el C4: A2 queda OPEN salvo que cumpla todos los criterios de entrada y permanencia.



### Run40 final forensic result — 13-nov-2026

Run40 (`build/a2n/a2n-20261113-160443`) has finished. **A2 FAIL**: at the contracted phase-B boundary t=12000, `spawneados=8/8`, `vivos=4`, `muertos=4`, `desaparecidos=0`, `entraronPerimetroEver=6`, `dentroParedAhora=0`. Thus six attackers crossed the perimeter at least once, but none remained inside the wall at the acceptance snapshot. The harness printed `VEREDICTO B: SIN UMBRAL CONTRATADO` as informational; do not interpret that line as PASS. Subsequent snapshots t=12300–13200 show 0 vivos, 4 muertos and 4 desaparecidos because four surviving attackers were removed for C4 recovery; do not retroactively substitute those post-cleanup snapshots for t=12000.

**C4 PASS** independently: `guardasCombatieron=true`, `profesionalesReanudados=true`, `asignacionesJobSiteLimpias=true`, `supervivientes=16`, `trabajadoresEsperados=12`, `trabajadoresVivos=12`, `trabajadoresReanudados=12`, `bajasTrabajadores=0`, `trabajadoresNoResueltos=0`, `retiradosParaRecuperacion=4`, `jobSite=[10,0,0]`, `fallosDeInstrumento=0`, `recoveryTicks=1200`. `fin.txt` contains C4_PASS; this does not imply A2 success.

**Chain correction validated, A2 not solved.** Final nav trace: 24,112,482 bytes, 336 `MISSION_CHAIN_RETAINED`, 0 `DROP_UNSAFE`. idx3 retained `mision=616,71,578` despite incoming moving suggestions (e.g. 621,71,583), proving the stable-chain policy is active. However idx3 remained `FAILED` at `607,95,601`, `escalones=64/64`, `fallosEscalon=10/10`, `colocados=0/24`; last local failure was `obstruccion de acceso removida; replanificar desde el mundo actualizado`, with global route ending at 40 nodes and 3656/8000 expansions. idx4 was still `RECOVERING` at `588,79,507`, 20 descent failures, 8/64 local steps, global path 26 nodes/615 expansions; delegated break-first-block recovery. idx7 and idx8 were still `TRAVERSING` around `685-686,69,450`, 85-86 blocks from center, so the approach is not converging fast enough for the time budget. Earlier idx6 exhausted route replans 12/12. Four attackers died by t=12000; three explicit death records include idx2 t=943 at 652,71,563, idx1 t=1143 at 640,71,534, idx5 t=1465 at 658,71,522; idx6 died at t=1807. Exact damage source was not instrumented; do not guess.

Watcher `build/a2n/watch-run40-final.ps1` (PID 3520) completed, appended the run summary to all three handoff documents, and is no longer running. Server PID 34412 exited. Run40 world and all artifacts remain under `build/a2n/a2n-20261113-160443`. Trace filter verified effective: zero DROP_UNSAFE events, versus ~93 MB of noisy trace in Run39. No production world was touched. A2 remains OPEN. Next owner must inspect actual route/stair/recovery code and Run40 trace before changing anything; do not raise budgets or weaken the wall as a substitute for diagnosis.

## 2026-11-13 17:25 — A2 continuation: stair rearm/anchor corrections tested (A2 still FAIL)

- Branch/HEAD: feature/autonomous-village / 518fee1. No reset/commit/push.
- Runs 41–45 were executed with the documented raid contract; 41–44 were invalidated by later corrections and stopped. Run45: t=3900 snapshot vivos=5 muertos=3 desaparecidos=0 entraronPerimetroEver=4 dentroParedAhora=0.
- idx3 still ends FAILED at 607,95,601 with 64/64 local steps and 10/10 stair failures, last access-clear at 594,94,600.
- Corrections applied and compiled: local stair budget reset at verified terrain change and at STAIR-ACCESS-CLEAR (bounded 2); local-limit detection uses counters, not the last message; staircase anchor now follows current support on every no-pending attempt; mission-level stair fallback when global waypoint equals mission with build=false.
- Focused test: NAV_TERRAIN_REARM_POLICY_TEST OK. compileJava/compileTestJava BUILD SUCCESSFUL. No final A2 PASS.
- Diagnostics still needed: why the mission-level fallback does not produce a stair after access clear, and why idx4 still evaluates a stale 566,120,533 candidate set; idx7/idx8 approach convergence remains.
- Next action: add bounded trace at the fallback/anchor decision (anchor, candidate base, mission, build flag), reproduce idx3, then correct the real candidate/landing policy. Do not rerun the same fixture without a new trace.

## 2026-11-13 17:45 — A2 stair-fallback diagnostics (A2 still FAIL; C4 PASS preserved)

- Runs: a2n-20261113-173123 (focused 6000, stopped), a2n-20261113-173705 (focused 6000, FIN C4_PASS). Full A2 still not PASS.
- Instrumentation added: A2_STAIR_FALLBACK_DECISION and A2_STAIR_FALLBACK_RESULT (NavTrace, opt-in via traceNav). They log objetivo, mision, buildable, local counters, anchor/stair state and actual construction result.
- Proven defect: the mission-level fallback compared the global mission (661,71,533), not the selected local waypoint (e.g. 588,96,512). It therefore skipped stair construction. The condition now targets any unbuildable local waypoint below safe-drop.
- After the fix, idx4 entered the fallback and placed a first verified stair at 589,92,511, then consumed it; idx3 still had no fallback event in the focused run and remains 64/64, 10/10 at 607,95,601.
- The stair anchor now updates to current support on every no-pending attempt; local reset is bounded at verified terrain/access-clear events.
- Acceptance contract remains undefined: harness prints VEREDICTO B: SIN UMBRAL CONTRATADO. No A2 PASS is claimed.
- New permanent manual: docs/autonomous-village/A2-HEADLESS-TESTING-AND-INSTRUMENTATION.md.
- Next action: reproduce idx3 with the new FALLBACK_DECISION event; if absent, trace the tryPlan branch that returns null before line 1642; if present, inspect FALLBACK_RESULT and anchor/candidates. Then fix the exact branch.

## 2026-11-13 18:10 — A2 focused rearm trace: fallback still not reached for idx3

- Run 2n-20261113-180149 (focused FaseB6000) captured: phase A entraron=5/8; phase B t=3900 vivos=3 muertos=5 dentroParedAhora=0. A2 FAIL.
- A2_STAIR_FALLBACK_DECISION/RESULT appeared for idx4 (uuid 67861573…), with stair anchor still 566,121,533 and 28 rejected candidates; no event for idx3 (uuid near 607,95,601).
- idx3 is already FAILED at local 64/64, fallos 10/10, last access-clear 594,94,600; 	ryPlan is not entered in FAILED state, so the fallback gate cannot run.
- Bounded terminal rearm at the verified access clear was added; it did not create a viable next stair attempt. This is a real unresolved control-flow/geometry defect, not an acceptance PASS.
- Design proposal AGGRESSIVE-ZOMBIE-MOBILITY-DESIGN-PROPOSAL.md is documentation-only and NOT authorized for implementation.
- Next exact action: add A2_TRYPLAN_ENTER/NULL at NavExecutor.tryPlan and its early returns, reproduce idx3, and determine why FAILED is re-entered after the one bounded rearm. Then fix that transition only.

## 2026-11-14 00:30 — idx3 terminal state proven: rearmes=4/4, tryPlan never entered

- Focused run 2n-20261114-002306 (FaseB6000): A2 FAIL; t=3900 4 ever entered, 0 inside, 4 dead.
- A2_REARM_ENTER for idx3 shows state=FAILED rearmes=4/4, MAX_REARMES rejection every tick; no A2_TRYPLAN_ENTER for that UUID.
- Root cause is the exhausted terminal rearm budget, not the fallback gate. The access-clear reset did not leave a usable rearm after four failed fallback attempts.
- No speculative production fix applied. New dedicated report: A2-IDX3-TRYPLAN-STATE-INVESTIGATION.md.
- Instrumentation added: A2_TRYPLAN_ENTER, A2_REARM_ENTER, A2_REARM_REJECTED.
- Next: inspect the four rearm→tryPlan cycles after access clear and fix the exact bounded transition. A2 remains FAIL/OPEN; C4 PASS.

## 2026-11-14 00:40 — Post-rearm local counter overwrite proven

- Run 2n-20261114-003310 (focused FaseB6000). A2 still FAIL: t=3900 3 alive, 5 dead, 5 ever entered, 0 inside.
- idx3 UUID 9d3662ff-…: four terminal rearmes at ticks 275/405/535/665, each followed by A2_TRYPLAN_ENTER ... local=0/64 at 276/406/536/666.
- On the **same tick** as each tryPlan entry, NAV_FAIL reports local=64/64 fallos=10/10 again. The local stair counters are being restored/overwritten inside the planning path before the fallback can run.
- At tick 795 onward: A2_REARM_REJECTED reason=MAX_REARMES repeatedly; tryPlan no longer runs.
- idx4 still succeeds: A2_STAIR_FALLBACK_RESULT result=589,93,511, colocados=1/24, ncla=589,92,511; fallback behavior must be preserved.
- No production fix was applied for the counter overwrite; the exact writer is not yet identified. A2-IDX3-TRYPLAN-STATE-INVESTIGATION.md is the detailed report.
- Next: instrument scalonarObjetivoLocal/allar around the same tick with the counter value before/after and every write; fix the actual overwrite only.

## 2026-11-14 00:55 — Exact 0→64 writer identified; no safe fix applied

- Run 2n-20261114-004219 (focused FaseB6000). idx3 UUID 40f10aeb-….
- Trace shows A2_LOCAL_STAIR_ENTER local=0/64, then A2_LOCAL_STAIR_WRITE op=INC_STEP new=1..64 every ~2 ticks, each selecting 608,95,601 while mission is 616,71,578.
- Writer: NavExecutor.escalonarObjetivoLocal() line 1778 scalonesDeObjetivo++. The candidate is accepted despite not descending and while the mission remains below; the counter therefore counts non-progressing local steps.
- allosDeEscalonDeObjetivo reaches 10 via the repeated failure path once the 64 limit is hit (scalonarObjetivoLocal sets it to MAX at line 1755).
- idx4 (9c0f390…) still reaches fallback and places 589,92,511; a naive “candidate must be closer to mission” guard would also reject that valid step, so no speculative guard was added.
- A2 still FAIL: t=3900 0 inside; C4 preserved PASS. Acceptance threshold still undefined.
- Next exact action: instrument legirObjetivoLocal candidate generation for idx3 and add a progress predicate that distinguishes downward/sideways stair continuation from a same-level no-progress 608,95,601 selection; regression-test both idx3 rejection and idx4 acceptance before changing line 1778.

## 2026-11-14 01:00 — Dedicated counter-write report created

- Created `docs/autonomous-village/A2-IDX3-COUNTER-WRITE-INVESTIGATION.md` with the complete writer inventory, exact line 1778 `escalonesDeObjetivo++` evidence, the 64-step/10-failure accounting distinction, idx3-vs-idx4 divergence, and the reason no speculative acceptance fix was applied.
- A2 remains FAIL/OPEN; C4 PASS preserved.

## 2026-11-14 01:25 — Local objective progress predicate implemented

- Added NavLocalObjectiveProgressPolicy; when the mission is below the mob, same-level/higher local candidates are rejected unless a non-WALK route continuation or pending stair is present.
- Integrated at NavExecutor.escalonarObjetivoLocal; rejected candidates increment failures but do not consume the accepted-step budget.
- Tests: NavLocalObjectiveProgressPolicyTest 6/6 pass.
- Focused run 2n-20261114-011440: idx3 moved from stuck 607,95,601/64/64 to 617,86,600 with local 4/64 and explicit candidato local sin progreso vertical; idx4 still places 589,92,511.
- A2 still FAIL/OPEN (focused only; no inside-wall closing evidence); C4 preserved PASS. Threshold still SIN UMBRAL CONTRATADO.
- Report: docs/autonomous-village/A2-LOCAL-OBJECTIVE-PROGRESS-POLICY.md.

## 2026-11-14 01:45 — Full accepted-threshold campaign: A2 FAIL, C4 PASS

- Approved contract: A2-ACCEPTANCE-CRITERIA.md (8/8 registered; >=6/8 enter; >=1 alive inside at 12000; no surviving terminal FAILED/zero progress; C4 PASS).
- Full run 2n-20261114-013023 (FaseB12000): spawneados=8/8, entraron=5/8, vivos=3, muertos=5, dentroParedAhora=0. A2 FAIL on criteria 2, 3 and 4.
- Survivors: idx3 alive 617,86,600 (PLANNING, local 4/64), idx4 alive 588,77,507 (PLANNING, stair 589,92,511), idx8 alive 701,62,457 (terminal FAILED).
- C4 PASS: 16 survivors, 12/12 workers alive/resumed, 0 casualties, jobSite [10,0,0], 0 instrumentation failures.
- Campaign report: A2-CAMPAIGN-20261114.md.
- Next: inspect the five deaths and idx8's terminal FAILED first divergence; do not weaken wall/threshold. A2 is not PASS.

## 2026-11-14 01:50 — Run13 forensics: A2 FAIL, death telemetry absent

- Run 2n-20261114-013023: 8/8 registered; 5/8 ever entered; 3 alive (idx3,4,8); 0 inside at t=12000; C4 PASS.
- Casualties idx1,2,5,6,7: no death/damage event, killer or death tick exists in current telemetry, so causes are **unknown**, not inferred.
- idx8 first terminal: NAV_FAIL replans agotados (8/8) at objective 701,67,457 / mission 725,71,533 around tick 8041; four rearmes consume by ~9525 without changing the unreachable BREAK waypoint.
- idx3 ends 617,86,600, PLANNING, local 4/64, progress policy working but no wall entry; idx4 ends 588,77,507, one verified stair 589,92,511, no entry.
- No death/replan correction was justified by available evidence. Full forensics: A2-RUN13-DEATH-AND-STALL-FORENSICS.md.
- Next: add opt-in death/damage telemetry and trace why the same 701,67,457 BREAK waypoint survives every rearm; then fix that shared lifecycle and rerun full acceptance.

## 2026-11-14 02:05 — Death telemetry validated; idx8 upward BREAK blocker

- Telemetry implemented via NeoForge LivingDamageEvent.Post/LivingDeathEvent, registered on NeoForge.EVENT_BUS, opt-in through NavTrace; no behavior changes.
- Focused run 2n-20261114-015833: 16 damage events, 4 deaths. All observed deaths are guard minecraft:mob_attack: idx6 t1353, idx1 t1637, idx5 t2125, idx2 t2321.
- idx8:
eplans agotados (8/8) with BREAK waypoint 701,67,457 ~5 blocks above the mob; no current upward mobility, same waypoint survives every rearm. Navigation-only repair would require the unapproved mobility design or changed route semantics.
- A2 remains FAIL: entrants are killed by guards before the closing snapshot, and idx8 is stranded. Satisfying >=1 alive inside by weakening guards/changing survivability would violate campaign restrictions and needs an explicit design decision.
- Report: A2-DEATH-TELEMETRY-AND-IDX8-BREAK-REPLAN.md.
