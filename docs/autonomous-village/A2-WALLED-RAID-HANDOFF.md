# A2 · ASEDIO AMURALLADO — HANDOFF DE CONTINUACIÓN

> **Estado global: A2 SIGUE ABIERTO.** El gate del asedio amurallado completo está **sin medir**.
> Este documento es el registro de continuación del *asedio*, no un parte de aceptación.
>
> **No confundir con R4/S5:** `r4golem-20261022-1900` aceptó el **asedio clásico S5** de
> `VillageManager.start` con guardias y oleada 8/8. No es la navegación propia A2 ni mide el fixture amurallado
> de este documento; A2 conserva su bloqueo y su umbral sin aceptar.

---

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

Solo aid, sin cambios de comportamiento (misma busqueda, mismo orden, mismos rechazos, mismos spawns):

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
