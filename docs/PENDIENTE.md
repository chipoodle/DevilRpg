# POR DÓNDE SEGUIR — traspaso de sesión (aldea / DevilRpg)

> **Para la sesión nueva**: lee este fichero entero y sigue por **«Lo que está PENDIENTE»**. Cada apartado trae **lo
> que está medido**, **por qué se hizo así** y **el paso exacto que toca**. Los datos crudos de cada medida están en
> `tools/arnes/medidas-mina-sellada.txt` (§1–§28) y el porqué de cada regla, en `docs/aldea-invariantes.md`
> (**I119–I135**).
>
> **Para el jugador**: pégale a la sesión nueva: *«Lee `docs/PENDIENTE.md` y sigue por lo pendiente; mide con el arnés
> y no te fíes de nada que no esté medido»*.

## CÓMO SE TRABAJA AQUÍ (leer antes de tocar nada)

**La regla de oro**: nada entra sin **`gradlew compileJava`** + **`python tools/lint_aldea.py --strict`** + **una medida
del arnés**. Un intento que no arregla **se retira** y **se dice**. (En la última sesión se retiraron tres intentos,
con su número: 940 falsos positivos, 0 disparos, y una corrida a cero por instrumento ciego.)

**El arnés (una corrida ≈ 9-12 minutos de reloj)**:
1. Copiar `tools/arnes/GuardHarness.java` a `src/main/java/com/chipoodle/devilrpg/debug/GuardHarness.java`.
2. **Encender UN modo** en la copia (`MEDIR_MINERO`, `MEDIR_PEPITAS`, `MEDIR_MILICIA`, …) y **comprobar que está
   encendido**: en la última sesión se perdió una corrida por lanzarla con el modo apagado (`al borde: 0`).
3. `run/world` es **copia** del guardado del jugador: `run/saves/New World (2)` → `run/world` **antes** de cada
   corrida (el guardado del jugador **no se toca**). Borrar antes `run/logs/latest.log`.
4. `gradlew runServer` en segundo plano y leer `run/logs/latest.log`.

**Trampas ya pagadas (no repetirlas)**:
- **El log ROTA por tamaño**: si `latest.log` es pequeño, hay que leer también `<fecha>-N.log.gz` (gunzip y concatenar).
- **Matar `gradlew` NO mata el servidor**: el JVM sigue con el puerto y la corrida siguiente falla en silencio
  (`serverlevel2 is null`). Antes de lanzar:
  `Get-CimInstance Win32_Process -Filter "Name like 'java%'" | Where-Object { $_.CommandLine -match 'fml.modFolders' }`.
- **Al terminar**: apagar el modo, **borrar `debug/`** (y `build/classes/.../debug/`), restaurar `run/world` y
  comprobar que el `.jar` queda libre.

**La medida del pueblo (I135)**: el **total** de rendiciones (`no consigue llegar`) **es ruido** (5, 9, 10, 12, 18 en
corridas comparables). Se mide **por 1.000 ticks y por etiqueta**:
```
python tools/arnes/rendiciones.py --etiquetas build/medida-*.log
```
La prueba de un arreglo **no es el total**, es **el criterio concreto que baja a cero** (p. ej. `Yendo a la taberna`
16 → 0). Para hablar del total, **media de 3-4 corridas**. Tanda de referencia de la última sesión:
**media 1,29 · rango 0,42-2,78 · desviación 0,94** por 1.000 ticks.

## Estado (27-sep-2026 — sesión de los oficios: los 5 atascos, la mina, el hierro y la boca de la galería)

Todo **compila**, pasa `lint_aldea --strict`, el árbol está **limpio** (sin `debug/`, sin JVMs, `run/world`
restaurado) y **todo commiteado**.

**Y el titular de la sesión, medido**: la **tasa de rendiciones del pueblo** (I135, ventana fija 2.000-12.000) baja
de la **media 1,29** de la tanda anterior a **0,30** con el código final (**media de 3 corridas: 0,10 · 0,30 · 0,50**),
y el pueblo queda en **7 avisos, TODOS SUELTOS y ningún bucle** (contra 18 avisos con bucles de **16** y **19** de la
referencia de la mañana): los gordos (`Yendo a la taberna` 16, `Volviendo a la plaza` 19, `Cuidando el ganado` 3,
`no consigue entrar al parcela` 5) están **a cero**.

**Cerrado y MEDIDO en esta sesión** (con el criterio que lo prueba):

| qué | medida |
|---|---|
| **el granjero** (I129) | el caminante daba por **llegado** lo que está a 1 bloque → `caminarHaciaExacto` (tolerancia 0 + ruta pedida a mano): `no consigue entrar al parcela` **5 → 0**, avisos de huerta **6 → 0**, el granjero **`Cosechando`** |
| **la recolectora encerrada** (I130) | no podía **salir del parcela** (una puerta de valla cerrada **no es navegable**): `abrirLaCompuertaDeAlLado` + salir con paso exacto → `Volviendo a la plaza` **19 → 0** (total **27 → 9**) |
| **el ganadero** (I131) | perseguía la **celda cruda del animal** (que no se pisa) → `casillaDePieCercaDe` (regla I114): `Cuidando el ganado` **3 → 0** (total **9 → 5**) |
| **el aldeano METIDO en un bloque** | el criterio bueno es **la forma de colisión contra la altura de los pies** (con `esCeldaDePie`: 940 falsos positivos; con la caja: 0 disparos) → **2 desatascos reales**, 0 falsos positivos |
| **la mina ATRAVIESA el agua** (I132) | **aísla** la cáscara 3×3×3, **seca** la celda y **sigue** → `hechas 0 → 3/24`, **`TOPE=NO`**; y **la mina BAJA** (`pasos 16 → 32`, `y 54 → 46`) con **16 piezas de caracol** |
| **el pico y el hierro de los raids** (I133/I134) | el zombi suelta **pepitas** y **el que mata las lootea** (antes **desaparecían a los 5 min**); el herrero forja el pico **con 27 pepitas** → medido: **`Forjo un pico de HIERRO`** |
| **la taberna** | `VillagerTavernGoal` caminaba a la **celda de la mesa** (no pisable) → `casillaDePieCercaDe`: `Yendo a la taberna` **16 → 0** y la corrida **2,73 → 0,47** por 1.000 ticks |
| **la medida** (I135) | `tools/arnes/rendiciones.py`: rendiciones **por 1.000 ticks en ventana fija**, por etiqueta, con media y rango |
| **la boca de la galería** (I136) | el aldeano va **de pie sobre la losa** (nodo `y+1`) y con **dos** celdas de hueco el vecino sale **BLOCKED**: no entra ni sale → **tres** celdas. `hechas` **3/24 congelado 3.600 ticks → 24/24** y `pasos` **32 → 44** (la cara de `y=46` a `y=40`); y **las paredes contra el agua** al abrir cada celda |
| **la cadena del hierro de los raids** (I137) | el **guardia** mata, **lootea** (2 pepitas), **deja el hierro en el almacén** (13 depósitos, de 0 a **20** pepitas) y **el herrero forja el pico de HIERRO**; el eslabón que faltaba era que el guardia se lo quedaba en el inventario |
| **la balsa, al herrero de herramientas** (I138) | era la faena que le comía el tiempo al minero (**33-46 coladas** con el pedernal en su inventario y el almacén clavado en 6) → ahora cuela el **herrero**: coladas del minero **0**, del herrero **10**, el pedernal **6 → 16**, y la misma galería en **t≈17.200** en vez de **t≈37.800** |
| **los atascos sueltos** (I139) | (1) el guardia: el atasco se medía **solo por la recta** y la ronda es un círculo → cuenta el **avance por la ruta** (`Patrullando` **9 → 2**); (2) a por un objeto caído se iba a la **celda cruda** (la mesa, una valla) → `casillaDePieCercaDe` (`Recogiendo el corral` **4 → 0**, `Guardando lo suyo` **5 → 0**); (3) «pisable» no incluía **los cultivos** → el parcela no tenía ni una casilla de pie (el granjero hundido en bucle **7 → 1**) |

**El aviso de método que salió de aquí**: al normalizar la medida, el "ruido" escondía **16 rendiciones de un solo
aldeano en un solo sitio** (la taberna). La medida no era un trámite: **destapó el fallo**.
## Lo que está PENDIENTE (en este orden, como pidió el jugador)

### 0. Sesión del 27-sep-2026: la **boca de la galería** (el choque caracol ↔ galería) — **CERRADO y MEDIDO**

Lo ordenado era el **choque caracol ↔ galería** (el 68 % del tiempo del minero). **Medido y arreglado** — lo cuenta
**I136** y los datos crudos están en `tools/arnes/medidas-boca-galeria.txt` (corridas
`build/medida-galeria-3alto.log`, `medida-galeria-agua-1.log`, `medida-galeria-muro.log`):

| qué | medida |
|---|---|
| la **boca** (el diagnóstico viejo: «pica su propia losa») | **era un fantasma del instrumento**: `cara` es la celda del caracol del paso, y ésa lleva su pieza por definición |
| la **boca** (causa real) | el aldeano va **de pie sobre la losa** (nodo `y+1`, caja `y+0,5…y+2,45`) y con **dos** celdas de hueco el vecino sale **BLOCKED** → no entra ni sale. Arreglo: **tres** celdas (I136) |
| **antes → después** | `hechas` de la galería del paso 32: **3/24 congelado 3.600 ticks** → **3 → … → 24/24**; y la galería del **paso 16** (que cavaba el arnés) ahora **la cava el minero: 2/24 → 24/24** |
| el **caracol** | `pasos` **32 congelado** → la galería se cierra y **sigue bajando: 34 → … → 44** (la cara de `y=46` a **`y=40`**), con `TOPE=SI`/`se PARA` = **0** |
| el **agua** | las tres celdas abren el techo en el acuífero y el túnel se inundaba (`hechas` 6 → **0**, 223 muestras). Con el censo nuevo `AGUA`: el agua entra por la **celda de delante** → `aislarDelAgua` al **abrir** cualquier celda, saltando solo el paso del caracol. Al final: **31 celdas con fluido, ninguna dentro del túnel** |
| el **muro** | con 21/24 el minero se iba al **portón norte** (243 muestras; el final de la galería cae «fuera del muro» a 17 bloques bajo el suelo): **bajo tierra no hay muro que cruzar** → la ruta a la celda 21 pasa a `22 nodos … alcanza=SI` |

**Lo que toca ahora**: **(1)** los **atascos sueltos** del §7 (**hechos y medidos** en esta misma sesión: I139);
**(2)** volver a medir la **tasa de rendiciones** (`rendiciones.py --etiquetas`, media de 3-4 corridas) con la mina ya
desbloqueada — la primera corrida buena da **0,30** por 1.000 ticks (contra 1,29 de la tanda anterior).

**Y también CERRADO en esta sesión**: la **cadena del hierro de los raids** (§6b.2/§6b.3) — **I137**: el guardia mata,
lootea, **deja el hierro en el almacén** (13 depósitos; el almacén de 0 a 20 pepitas) y **el herrero forja el pico de
hierro**, que se lleva el minero. El eslabón que faltaba era el guardia (se quedaba el botín en el inventario) y el
instrumento tenía tres trampas (contaba pepitas en el suelo, la barredora descartaba el zombi y el escaneo era de 140
bloques): está todo en `tools/arnes/medidas-pepitas.txt`.

**Y la opción C** (la balsa y su acarreo, al herrero de herramientas) — **I138**: coladas del minero **33-46 → 0**, las
hace el herrero (**10**, hasta el objetivo de 16 pedernales) y la misma galería se completa en **t≈17.200** en vez de
**t≈37.800** (`tools/arnes/medidas-balsa.txt`).

**Y los atascos sueltos del §7** — **I139**, los dos que quedaban:

* **el guardia no se rendía por falta de ruta**: sus avisos decían `ruta=16-32 nodos … alcanza=SI` y `nav=[… alcanza]`
  —un destino a 20 bloques con una ruta de **30 nodos**—. El contador medía **solo la distancia en línea recta** y la
  ronda es un **círculo**: en un rodeo la recta sube. Ahora cuenta también el **avance por la ruta viva**. MEDIDO:
  `Patrullando` **9 → 2**.
* **al objeto caído se iba a la celda CRUDA** (que puede ser la **mesa de la taberna**, una **valla** o el propio
  parcela): `casillaDePieCercaDe` en los cuatro goals que recogen **+** `desatascarSiEstaEncajado` antes de contar
  atasco. Y de raíz: **«pisable» no incluía los cultivos** (`esCeldaDePie` pedía aire y un parcela no tiene aire) →
  el granjero hundido en la farmland **no tenía a dónde salir**. MEDIDO: `Recogiendo el corral` **4 → 0**,
  `Guardando lo suyo` **5 → 0**, `Yendo a la taberna` **1-2 → 0**, el bucle del parcela **7 → 1**, y la **tasa del
  pueblo 0,50-0,60 → 0,30** por 1.000 ticks (`tools/arnes/medidas-atascos-sueltos.txt`).

### 8.bis.0. SESIÓN DEL 30-sep-2026: **los cuatro reportes del jugador, y el pueblo a 1 aviso** — ACTA

El jugador se quejó, con razón, de que llevaba rondas midiendo etiquetas mientras él veía cosas a simple vista. Sus
cuatro reportes, cada uno con su causa **medida** en su propio registro:

| lo que reportó | la causa (medida) | el arreglo |
|---|---|---|
| **«los zombis del raid inicial son muy lentos y no atacan a los aldeanos»** | el perfil tenía `baseSpeed = 0.071` y **el zombi del juego anda a 0.23** (un **31 %**), y el aldeano estaba en el `targetSelector` en **prioridad 4**, detrás del jugador | base **0.23**, velocidad con tope **×1.6**; el aldeano pasa a **prioridad 0** (I165) |
| **«los aldeanos no huyen ante los zombis agresivos»** | el pánico **existe** en su cerebro, pero **los goals del oficio cogen `MOVE`** y lo excluyen: no podía moverse (su registro: `Dionisio … slain by Zombie`, **sin dar un paso**) | `VillagerFleeGoal` a **prioridad 0 y con `MOVE`**: suelta el trabajo y corre a su casa (I165) |
| **«varias construcciones hundidas y alrededor hueco, da a un pozo»** | medido con cortes del mundo: la choza del minero tiene el suelo **bien** (`cota−1`) pero **debajo hay 5 bloques de aire con agua al fondo** (el «pozo»); la aldea está **sobre el vacío** en esa zona | **cimiento**: rellena de piedra todo hueco **24 bloques** abajo en un disco de 86, respetando **el anillo del caracol** de la mina (I166) |
| **«semillas y vegetales flotando en la superficie de la parcela»** | **era el mod**: dos `addFreshEntity(new ItemEntity(...))` soltaban el sobrante **en la celda de la mata** | **no se tira nada**: el sobrante va al almacén y, si no cabe, **a la mano**; y la cosecha **no se frena por capacidad** (I166) |

**Y el cuadro por etiqueta de las corridas recientes** (105, 106 y 111: **4 · 3 · 1** avisos, con la mejor de la sesión
en **1**), contra las etiquetas que el objetivo pedía cerrar:

| etiqueta | referencia (I154) | ahora |
|---|---|---|
| `Yendo a la cocina` | 26 | **0** |
| `Labro la huerta` / `Labrando la huerta` | 14 | **0** |
| `Sembrando` | 11 | **0** |
| `Trajo … del almacén a la despensa` | 3 | **0** |
| `Buscando recambios` | 3 | **0** |
| `Yendo a la arboleda` | — | **0** |
| `Bajando lo del corral` | 35 | **0** |
| `Recogiendo lo suyo` | 40 | **0** |
| `Yendo a la taberna` | 29 | **0** |
| `Sacrificando un animal` | 15 | **5** (arreglado en I168, **pendiente de medir**) |
| `Guardando lo suyo` | — | **2** (es el caso de I169, **pendiente de medir**) |

**Lo que queda**: (a) medir 4 corridas con I168 + I169 + el cimiento en su sitio; (b) la **clase B** —la ruta **alcanza**
y el goal se rinde igual—, que es la única forma que sobrevive en el mejor registro (`ruta=23 nodos … alcanza=SI` desde
dentro del corral, con el minero yendo al almacén: el caso de I169); (c) el acta del cuadro de arriba con su media.

#### 8.bis.0.bis · **EL BANCO RÁPIDO Y LO QUE ENSEÑÓ** (30-sep-2026, continuación)

El jugador cortó por lo sano: *«¿por qué tardas tanto en hacer mediciones?»*. **Tenía razón y el cuello de botella era el
instrumento**: cada pregunta costaba **80 minutos** (4 corridas). Se construyó `tools/arnes/tanda-rapida.ps1` (**3 minutos
por corrida**, con el recuento **por etiqueta** y las trazas de la reparación), y con él:

| arreglo | corridas rápidas | media | veredicto |
|---|---|---|---|
| **cimiento: anillo sin holgura** | verificado **en el terreno** | — | **el hueco se cierra y el caracol sigue abierto** ✓ |
| I170 (la ruta manda) | 1 · 1 · 0 | **0,67** | se queda ✓ |
| I172 (atasco «por tiempo») | 1 · 2 · 4 | 2,33 | **RETIRADO** ✗ |
| I174 (mirada en el recado) | 2 · 1 · 2 | 1,67 | **RETIRADO** ✗ |
| I171 (devolver el rumbo) | 1 · 1 · 0 | **0,67** | se queda ✓ |

**Y las tres verdades que salieron del instrumento** (más valiosas que los arreglos, porque evitan perder más tiempo):

1. **El aviso de rendición era una autopsia** ✗: imprimía el estado **después** de rendirse (rumbo borrado, navegación
   parada). Perseguí un `cerebro=-` dos rondas creyendo que era la causa. Ya dice el **tramo** (`recorridos N bloques en
   M ticks`, I173).
2. **La navegación nunca falla** ✓: la traza de «sin camino» **no saltó ni una vez** en dos corridas. Los aldeanos **no**
   están bloqueados: **tienen ruta y casi no avanzan** (medido: 4,6 bloques en 13 s; **0,5 en 9 s**) → **no les empujan**.
3. **Contar rendiciones en 3 minutos es ruido** (0 a 4) ✗: no distingue un arreglo de otro. El banco rápido imprime ahora
   también **el trabajo del pueblo** (sucesos por oficio), que tiene más señal; pero a 3 minutos también es pequeño. **El
   banco rápido sirve para ENCONTRAR el fallo; para confirmar la mejora hace falta la tanda larga de 4.**

#### 8.bis.0.quater · **ACTA DE CIERRE DE LAS ETIQUETAS** (30-sep-2026)

**Lo que pedía el objetivo, etiqueta a etiqueta, con la evidencia de las tandas de 4 corridas de 20 minutos** (cada
tanda es un build distinto, y eso es lo que hace comparable la tabla):

| etiqueta del objetivo | referencia | **estado medido** | quién lo arregló |
|---|---|---|---|
| `Yendo a la cocina` | 26 | **0** ✓ | I156/I161 + el patrón de casilla de pie |
| `Labro/Labrando la huerta` | 14 | **0-1** ✓ | I154 (a una celda que no se pisa, no se camina) + I167 |
| `Sembrando` | 11 | **0-1** ✓ | I154 + la cosecha entera (I166) |
| `Guardo lo suyo` / `Trajo … del almacén a la despensa` | 3 | **0** ✓ | I166 (el sobrante al almacén, nunca al suelo) |
| `Buscando recambios` | 3 | **0-2** ✓ | I154 + I169/I178/I179 (las puertas) |
| `Yendo a la arboleda` | — | **0** ✓ | el leñador por tramos (I112) |
| `Bajando lo del corral` | 35 | **0** ✓ | I154 + el ganadero a una casilla de pie al lado (I168) |
| `Recogiendo lo suyo` | 40 | **0** ✓ | I154 + la ruta manda (I170) |
| `Yendo a la taberna` | 29 | **0** ✓ | I158 (el atasco se mide contra el escalón) |
| `Sacrificando un animal` | 15 | **5 → 0** ✓ | I168 (casilla de pie al lado del animal) |
| `Guardando lo suyo` | 5 | **1-3** ✗ | I179/I180 (en medición) |

**Totales por tanda** (4 corridas de 20 minutos cada una, misma aldea y mismo arnés):

| tanda | qué llevaba | total | media/corrida |
|---|---|---|---|
| 111-114 | I168 | 1 · 2 · 4 · 1 | **2,0** |
| 115-118 | + I169 + I170 + I171 | 5 · 4 · 1 · 8 | 4,5 ✗ (la regresión de mi relleno, I177) |
| 123 | todo menos I180 | **1** | — |

**Las cinco verdades que dejó esta sesión** (valen más que los arreglos, porque evitan repetir el camino):

1. **El aviso de rendición era una autopsia** ✗: imprimía el estado **después** de rendirse. Se persiguió un
   `cerebro=-` durante dos rondas creyendo que era la causa. Ya dice el **tramo** (neto, **ANDADO** y **destinos**).
2. **La navegación nunca falla** ✓: la traza de «sin camino» **no saltó ni una vez** en dos corridas.
3. **«Anda mucho y no llega»** ✓: 15 de 18 rendiciones ocurrían a **3-8 bloques** del destino y **todas con `dy=0`** —
   se quedan **a las puertas**, y la compuerta que estorba suele estar en el **punto medio del lado**, no en la recta.
4. **La clase E estaba pasando dentro del mismo tick** ✗: un aldeano con **36 destinos en 8 ticks** y **0 bloques
   andados** — varios goals y el «tirón» escribiéndole el destino a la vez. **I180**: el primero que escribe manda.
5. **El cuello de botella era el instrumento** ✓: **80 minutos por pregunta** convertían cada intento en una tarde. Con
   `tools/arnes/tanda-rapida.ps1` (3 minutos, con etiquetas y trazas) y el corte del mundo (`slice_mina.py`) se
   encontraron en minutos cosas que llevaban cuatro intentos: **la holgura de ±1 del anillo** que dejaba el borde de la
   choza del minero hueco, y que **el aviso no fotografiaba el fallo sino el después**.

**Y lo que NO queda cerrado, dicho sin adornos**: la etiqueta `Guardando lo suyo` (1-3 por corrida), cuya causa ya está
medida (clase E dentro del tick) y arreglada (I180), **pendiente de la medición de 127-130**.

#### 8.bis.0.quinquies · **CIERRE DE LA SESIÓN** (30-sep-2026, ronda 24)

**Lo medido, y lo que se retiró por medirse mal** (las tres retiradas están escritas, con sus números, en
`docs/aldea-invariantes.md`):

| intento | corridas | media | veredicto |
|---|---|---|---|
| I168 (ganadero a una casilla de pie al lado) | 1 · 2 · 4 · 1 | **2,0** | se queda ✓ |
| I169+I170+I171 (puertas, ruta, rumbo) | 5 · 4 · 1 · 8 | 4,5 | I171 se estrechó con I177 ✓ |
| I172 (atasco «por tiempo») | 1 · 2 · 4 | 2,33 | **RETIRADA** ✗ |
| I174 (mirada en el recado) | 2 · 1 · 2 | 1,67 | **RETIRADA** ✗ |
| I180 («un destino por tick») | 2 · 6 | ~4 | **RETIRADA** ✗ |
| I177+I178+I179 (rumbo solo si está parado, puertas conocidas) | 123 | **1** | se queda ✓ |
| **I181** (el rescate cuenta como avance) | — | — | **medido con el banco rápido** ✓ |

**La medición del BUILD FINAL** (con I181 dentro y I180 fuera), banco rápido, cuatro corridas de 3 minutos:
**0 · 1 · 1 · 1** → **media 0,75** ✓ (la mejor de la sesión), con las etiquetas **rotando** (`Guardando lo suyo` ×2,
`Recogiendo el corral` ×1) y el pueblo trabajando (granja 2-6, ganado 3-5, pescador 2, **herrería 22-33**, cocina 4,
minero 9-16, leñador 0-5 por 3 minutos).

**Lo que queda abierto, con nombre y sin adornos**:

1. **`Guardando lo suyo`** (1-3 por corrida de 20 min): la causa está medida —el aldeano se queda **dentro de una casa a
   `y=79`**, el mod lo **rescata** a la plaza y su goal **se rendía igual**— y el arreglo (**I181**) está dentro y
   medido con el banco rápido. **No hay 4 corridas largas de I181**: el tiempo de esta sesión llegó hasta aquí.
2. **El residuo general**: 1-2 avisos por corrida de 20 minutos sobre **cientos de recados** (≈1 %) y con las etiquetas
   **rotando** de una corrida a otra — no familias sistemáticas, sino tropiezos sueltos. **El pueblo trabaja**: 44-57
   labores de granja, 47-50 de ganado, 174-265 de herrería y 618-666 de guardia por corrida.
3. **Lo que el jugador reportó, todo cerrado**: los ítems de la parcela (el mod los tiraba ✗, I166), el hueco de la choza
   del minero (**verificado en el terreno** ✓, I166), los zombis lentos y que no atacaban aldeanos (0,071 → 0,23 y
   prioridad 0 ✓, I165) y los aldeanos que no huían (el pánico no podía moverlos ✗, `VillagerFleeGoal` ✓).

### 8.bis.0.sexies · **SESIÓN DEL 30-sep (noche) y 3-oct-2026**: los dos reportes nuevos, y el instrumento que mentía

**Los dos reportes del jugador, cerrados — y uno de ellos destapó un fallo de MÉTODO mío.**

| reporte del jugador | causa medida | arreglo | medido |
|---|---|---|---|
| *«la parte de abajo de la aldea está hueco, hay un boquete cerca de una parcela»* | `afianzarElSuelo` **solo** se llamaba desde la **reparación**, así que **una aldea nueva nacía sin cimiento** ✗ | **I182**: cimiento al final del terreno **y** al final de `generate` (la mina cava después ✗) + **trazado 81** para las ya generadas | dos líneas `CIMIENTO` (11200 y 11188 bloques) en cada corrida ✓ |
| *«¿por qué no aparece arriba el nombre y profesión de los aldeanos?»* | el nombrado vive en `tickVillageLife`, que **solo corre con la aldea EN PAZ** (puerta del **12-sep**, `b4d04af`) y **una aldea nueva nace bajo asedio** ✗ | **I183**: la marca y el reparto de nombres corren **también con asedio** (junto a las camas, cada 10 s, idempotente) | `etiqueta=Bartolo (Herrero de armas)` ✓ 242 líneas con nombre y oficio en 3 min |

**Y la parte que era mía, dicha sin adornos**: al hacer a los asaltantes **rápidos** y **que vayan a por los aldeanos**
(I165), el **asedio puede durar mucho más** ✗ — y con él la aldea se queda «sin vida» (sin nombres, sin etiquetas). **No
miré qué dependía de que la aldea estuviera en paz antes de tocar a los zombis** ✗.

**El fallo de método del cimiento, que es el que más vale**: durante la sesión di el hueco por arreglado porque **el
arnés decía 0 avisos** ✓ — pero **el arnés mide sobre el guardado viejo del jugador, que SÍ migra** y por tanto
**siempre** pasaba por el cimiento ✓, mientras su **partida nueva** no ✗. *Lo que solo se prueba por el camino de la
migración no está probado para una partida nueva.*

**El instrumento mentía, y también está arreglado (I184)**: los bancos **mataban el servidor de golpe** ✗, el mundo **no
se guardaba** y, como el trazado **se guarda en el mundo**, **cada corrida volvía a migrar** ✗: el pueblo se rehacía
entero y la medida se llenaba de **5-7 avisos** ✗ que **no eran del arnés sino de la mudanza**. Ahora el arnés para a
un tick pedido con `server.halt(false)` (lo mismo que `/stop`: **guarda y sale**) y `-Conservar` sirve de verdad:

```
corrida 45 (CON migración)     → cierre limpio ✓ · 7 avisos ✗  (la mudanza)
corrida 46 (mundo CONSERVADO)  → cierre limpio ✓ · SIN línea REPARADA ✓ · 1 aviso ✓
```

**Conclusión medida**: los 5-7 avisos **eran la mudanza**, el pueblo asentado vuelve al **mejor registro de la sesión**
y **no había regresión** alguna de I182/I183 ✓. El coste real de la mudanza es **+3 a +6 avisos UNA VEZ** por partida
(al pasar al trazado 81), y eso hay que decírselo al jugador antes de que lo vea ✗.

**Lo que sigue abierto**:

1. **`Guardando lo suyo` con I181**: **4 corridas largas** del pueblo **asentado** (ya medibles ✓, con
   `tools/arnes/tanda-larga.ps1 N -Conservar`), que es lo que cierra el único pendiente de etiqueta que queda.
2. **El residuo**: con el mundo asentado, **1 aviso por 3 minutos** sobre cientos de recados (≈1 %), etiqueta rotando
   (`Sacrificando un animal` en la 46) → tropiezos sueltos, no familias.
3. **Documentar** en el `LEEME` que el banco **exige** el cierre limpio (hecho: es el paso 0 del protocolo).

### 8.bis.0.septies · **LAS 4 CORRIDAS LARGAS DEL PUEBLO ASENTADO, y la ráfaga del minero (I185)**

Hechas con `tools/arnes/tanda-larga.ps1 47 48 49 50 -Conservar`, cuatro corridas de **20 minutos** sobre el pueblo
**ya asentado** (sin migración ✓, con cierre limpio ✓ confirmado en cada registro):

| corrida | 47 | 48 | 49 | 50 | media |
|---|---|---|---|---|---|
| avisos | **1** ✓ | **13** ✗ | **9** ✗ | **12** ✗ | **8,75** ✗ |

**Y el residuo NO son tropiezos sueltos** ✗ — **es UN aldeano atrapado soltando ráfaga** ✓, que es lo que había que
descubrir aquí:

```
corrida 48:  9x  Ximeno (Minero) / Volviendo a la caseta   desde 603, 76, 537  (y=76, calle a 78: DOS por debajo)
             2x  Zacarias (Ganadero) / Cuidando el ganado
             2x  guardias / Yendo a entrenar
```

**El patrón es el de SIEMPRE**, y se ve comparando con **todos** los lotes guardados en `build/medida-tanda*.log`:
en cada lote hay **un dominante** (`Eufemia (Ganadero)` en docenas de corridas viejas, con **30 · 50 · 80 · hasta 238**
avisos ✗; hoy `Ximeno (Minero)`) y domina **`alcanza=NO`** ✗ (destino inalcanzable). Los lotes viejos daban **62 · 64 ·
72 · 80 · 110 · 238**; los buenos de la sesión, **1 · 2 · 4 · 1 · 5 · 4 · 1 · 8**. O sea: **el trabajo de estas semanas
se ve**, y lo que queda es **un atrapado por tanda**, no una familia de fallos por todas partes.

**La causa raíz eran UNA línea** ✗ (en `rescatarAldeanosAtrapados`): la exención decía
`getY() <= cota + 0.6` = **«por debajo de la calle = está en la calle»**, así que al minero metido en la **zanja de la
boca de su mina** (`603, 76, 537`, calle 78 = **dos bloques por debajo**) **ninguna red lo rescataba** ✗ y su errand se
reintentaba 9-13 veces ✗. **Verificado en el terreno**: la zanja es **idéntica en el guardado original del jugador** ✓
(`run/saves/New World`, misma ranura 1×1 en `x=603`, mismo escalón), así que **no la causó el cimiento** ✓.

**ARREGLO (I185)**: la exención es ahora una **banda** de un bloque por debajo de la calle (más el medio escalón de las
losas por arriba), y quien esté más abajo cuenta como atrapado; el minero que **trabaja** abajo sigue igual, porque se
mueve y además se exige que **no sepa volver** a la plaza.

| mismo mundo asentado | antes | después (I185) |
|---|---|---|
| avisos por corrida de 3 min | **1 · 13 · 9 · 12** ✗ (largas) | **1 · 2 · 1** ✓ |
| ráfaga del minero | **9-13** ✗ | **1** ✓ |
| rescates | 0 | **2 · 0 · 1** ✓ (`estaba atascado (sin poder salir) en 520, 79, 594`) |

**Lo que sigue abierto ahora**:

1. **El único aviso que queda** es el minero **bajando a su mina** (`603, 75, 537` → `603, 61, 526`, ruta de 33 nodos):
   se rinde **una vez** y ya no hay ráfaga ✓. Para quitarlo haría falta que el rescate dispare **antes** que el
   presupuesto del errand (~16 s), o una red propia para «no consigo bajar»; **no medido todavía**.
2. **`Guardando lo suyo` (I181)** sigue sin sus 4 corridas largas propias: en estas cuatro **no apareció ni una vez** ✓
   (la etiqueta no salió en 47-50), así que o está cerrada de hecho o le toca otra tanda.
3. **La zanja de la mina** (`x=603`, boca de la mina de la aldea, **1×1 y 2 de fondo**): es un problema de **mundo** ✗
   (el aldeano no puede subir un escalón de 2). El rescate lo tapa ✓, pero lo suyo es **dejar un escalón** al construir
   la mina. Anotado, sin medir.

### 8.bis.0.octies · **I186: la rendición no siempre es culpa del sitio** — y **0 · 0 · 0** ✓

**El dato que lo decidió** (sacado de las 4 corridas largas, gratis): el aviso dice «no consigue llegar a X» y se lee
como «X es inalcanzable», pero **el 100 % de los avisos traía `ANDADO` ≈ 0** ✗ (0,0-0,8 bloques), **incluso con
`alcanza=SI`** y `neto` de 30 bloques ✗. El aldeano **no andaba nada**. Y los relojes estaban al revés ✗: el recado se
rinde a los **10 s**, el goal del oficio a los **16 s**, y el rescate necesitaba **30 s** → llegaba **siempre tarde**.

**ARREGLO (I186)**: `marcarPuntoFallido` **no marca nada** si el aldeano está **atrapado** (quieto ≥ 4 s en una celda
fuera de la banda de la calle: de eso se encarga el rescate ✓), y `ATRAPADO_TICKS` baja de **30 s a 12 s** ✓.

| mismo mundo asentado, banco rápido | 1 · 2 · 1 (I185) | **0 · 0 · 0** ✓ (I186) |
|---|---|---|

**El mejor registro de toda la sesión** ✓ (el anterior mejor era **0 · 1 · 1 · 1**, media 0,75), y el pueblo trabaja:
herrería 46-49, minero 7-9, guardia 397-480 por 3 minutos.

**Y LO QUE DESTAPÓ, que ahora es el pendiente nº 1**: en las 4 corridas **largas** la **granja** rinde **16 · 15 · 15 ·
5** frente a la referencia **40-57** ✗ (los demás oficios en rango: ganado 56-90 ✓, herrería 199-231 ✓, pescador 7-9 ✓,
cocina 4-24 ✓). La causa está **en el propio registro**:

```
[Village] La aldea 0 pasa hambre: 2 boca(s) sin su racion y 0 punto(s) en la despensa      ✗ (x4)
[Arnes] GRANJERO ... puesto=SIN PUESTO ...                                                ✗ (un granjero sin puesto)
[Village] El granjero: Trajo 8 del almacen a la despensa                                  ✗ (x39, al almacen en vez de cosechar)
```

O sea: **la despensa está vacía** ✗ y hay un **granjero sin puesto de trabajo** ✗, así que el granjero se pasa el día
**trayendo del almacén** en vez de cosechar. **Pendiente con nombre**: (a) por qué un granjero se queda **sin puesto**
(¿el compostero no está, o `reclamarEstacionesDelPueblo` no lo ve?), y (b) por qué la despensa queda a **0 puntos**
(¿se cosecha y no se guarda, o no se cosecha?).

### 8.bis.0.nonies · **I186 e I187: la rendición no es siempre del sitio, y «un puesto, un dueño»**

**(a) I186 — «antes de culpar al sitio, mira si el que no se mueve es el aldeano».** El aviso dice «no consigue llegar
a X» y se lee como «X es inalcanzable», pero **el 100 % de los avisos traía `ANDADO` ≈ 0** ✗ (0,0-0,8 bloques),
**incluso con `alcanza=SI`** y `neto` de 30 bloques. Y los relojes estaban al revés ✗: el recado se rinde a los **10 s**,
el goal del oficio a los **16 s** y el rescate necesitaba **30 s** → llegaba **siempre tarde**. Arreglado: el embudo
`marcarPuntoFallido` **no marca nada** si el aldeano está atrapado (quieto ≥ 4 s fuera de la banda de la calle, de eso
se encarga el rescate) y `ATRAPADO_TICKS` baja de **30 s a 12 s**.

| mundo asentado, banco rápido | avisos |
|---|---|
| antes (lote largo) | **1 · 13 · 9 · 12** ✗ |
| I185 (el rescate, banda de la calle) | 1 · 2 · 1 ✓ |
| **I186 (el embudo + los relojes)** | **0 · 0 · 0** ✓✓ |

**(b) I187 — «un puesto, un dueño».** En las corridas 47-49 había **exactamente 3 granjeros** ✓, cada uno con su
compostero ✓ y todo sano. En la **50** (aldea de 20 aldeanos) aparecieron **cuatro** ✗ y dos se **turnaban el mismo
compostero** (`535, 78, 600`: 37 muestras uno, 63 el otro) con uno en `SIN PUESTO` **48 muestras** ✗. Causa: el reclamo
tenía un tercer paso —«no hay otra: la más cercana»— que cogía una estación **ocupada**, y el filtro (`deOtro`) solo
mira a los aldeanos **de esa pasada** → si el dueño no estaba en la lista, **los dos se creían dueños**. Fuera el paso
3: quien no encuentra estación **libre** ni una ocupada **sin dueño** se queda sin puesto ese latido y lo reintenta.

**VERIFICADO en la corrida larga 59** ✓: **tres granjeros, cada uno con SU compostero** (`991470fd→533,78,580`,
`6b437808→535,78,600`, `05821ff2→573,78,570`), **cero `SIN PUESTO`** ✓ y **cero reclamos** ✓ → el turno ha desaparecido.

**(c) Y UNA CORRECCIÓN DE MI PROPIA MEDIDA** ✗: la cuenta de labranza que usé buscaba `Guardo` (mayúscula) y la línea
real es **`el granjero guardo`** (minúscula), así que **estaba midiendo de menos** ✗. Con la cuenta bien hecha:
**16 · 21 · 23 · 9** en las cuatro corridas del lote (no 16·15·15·5) y **3** en la 59 ✗. La referencia sigue siendo
**40-57** ✓.

**(d) Lo que la 59 sí dice, y hay que leerlo con cuidado** ✗: sus avisos son **6**, y **4 de ellos son el minero**
(`Eufemia (Minero) / Volviendo a la caseta`) **desde la plaza a 50 bloques** ✗ — **efecto secundario de mi rescate**
✓: al atrapado se le baja a la **plaza** y su recado (la mina) queda a 50 bloques ✗. Y la aldea bajó de **20 a 13**
aldeanos ✗: **el mundo de la 59 llevaba 80 minutos pasando hambre** ✗, así que la granja no se puede juzgar ahí ✗.
**En marcha**: corrida 60 (fresca + migración, 3 min, guarda el mundo) y **61 (fresca y asentada, 20 min)** para medir
la granja en un **pueblo sano** y compararla de verdad con la referencia.

**(e) Y EL VEREDICTO DEL PUEBLO SANO (corrida 61, fresca y asentada, 20 minutos)** ✓:

| | corrida 61 (fresca) | lote anterior (mundo degradado) |
|---|---|---|
| **avisos** | **3** ✓ — y de **tres aldeanos distintos** (`Teodoro/Recolector → Yendo al almacen`, `Zacarias/Ganadero → Sacrificando`, `Anselmo/Granjero → Sembrando`), **sin ráfagas** ✓ | **1 · 13 · 9 · 12** ✗ (media 8,75, con 20 de 35 en un solo minero) |
| **despensa** | **34 · 48 · 28 · 16 puntos** ✓ con **13-16 aldeanos** ✓ | **0 puntos** y `pasa hambre` ✗ |
| **granjeros** | **3, cada uno con SU compostero** ✓ (`991470fd`, `4d096c1c`, `05821ff2`) y **cero `SIN PUESTO`** ✓ | 4 turnándose el mismo ✗ |
| otros oficios | ganado 45 ✓, pescador 9 ✓, herrería 161 ✓, cocina 22 ✓ | en rango |

**Conclusiones que se sostienen con estos números** ✓: (1) **I186** deja el residuo en **3 avisos sueltos** ✗ (frente a
la media de 8,75 ✓ y a los 62-238 de los lotes viejos ✓); (2) **I187** está verificado **dos veces** ✓ (tres granjeros
con su compostero y nadie `SIN PUESTO` ✓); (3) **la granja no estaba rota** ✗ — el pueblo sano come (despensa 34-48 ✓)
y el desplome era del mundo degradado ✓; y (4) **el efecto secundario del rescate queda anotado** ✗: al atrapado se le
baja a la **plaza**, y desde ahí un recado lejano (la mina, 50 bloques) puede fallar ✓ — es el siguiente candidato,
junto con **`Yendo a la taberna`** ✗ (que reapareció: 4 veces en el lote) y **`Yendo a entrenar`** ✗ (dos guardias al
mismo punto inalcanzable, `520, 78, 595`).

### 8.bis.0.decies · **Ronda 26: I188 e I189, y tres cosas que quedan localizadas con coordenadas**

**(a) I188 — el rescate deja al aldeano JUNTO A SU PUESTO, no en la plaza.** Medido en la corrida 64: **13 avisos y 10
del minero** ✗ (`Ximeno / Volviendo a la caseta`, `neto 36-41`, `alcanza=NO`) porque el rescate lo dejaba en la plaza y
su recado quedaba a **54 bloques**. El primer intento (buscar la casilla libre junto al **recado**, a la altura del
recado) **no bastó** ✗ —corrida 67: **7 de 7 rescates seguían en la plaza**— porque **al rescatarlo su goal ya se ha
rendido y no hay recado** ✗. El ancla que **siempre** existe es su **`JOB_SITE`**. Con eso, verificado en las corridas
68-69: el minero cae en **`597, 78, 536`** ✓, a **dos bloques** de su caseta ✓ (antes a 54 ✗).

| mundo asentado | avisos |
|---|---|
| corrida 64 (rescate a la plaza) | **13** ✗ (10 del minero) |
| corrida 67 (rescate junto al recado) | **14** ✗ (7 rescates, todos a la plaza ✗) |
| **68-69 (rescate junto al PUESTO)** | **0 · 0** ✓✓ |

**(b) I189 — la mesa de la taberna es donde come, no donde se pone.** Los cuatro avisos de `Yendo a la taberna` del
lote tenían la firma de «destino que no es casilla»: `neto` 0,6-0,8 ✗, `alcanza=SI` ✓ y `ANDADO ≈ 0` ✗. En el terreno,
los tres destinos (`606, 78, 587`, `600, 78, 583`, `600, 78, 590`) son **ladrillo** ✗, y el generador da **«el centro de
cada mesa»** ✗ — la mesa **es** de ladrillo. Arreglado: se elige la **casilla pisable de al lado** de la mesa (el
contrato que ya usa el ganadero) y se descartan las mesas sin casilla. **PERO SIGUE FALLANDO UNA VEZ** ✗ (corrida 67:
`Isidoro / Yendo a la taberna` con destino `605, 78, 586`, que el corte del mundo da como **ladrillo** ✗ y que
`casillaPosible` **aceptó** ✓ hmm — **contradicción sin resolver** ✗, y es el siguiente paso: o `esCeldaDePie` acepta
una celda maciza, o el corte y el destino no son la misma celda. **Anotado con las dos coordenadas.**

**(c) Y `Yendo a entrenar` queda LOCALIZADO** ✗: los dos guardias rescatados en la corrida 69 estaban atrapados en
**`519, 79, 594`** y **`520, 79, 595`** ✓ — o sea, **dentro del patio de tiro** ✗ (valla con una compuerta en
`z=591`), a **y=79** (un bloque por encima de la calle, fuera de la banda que exime). El rescate los saca ✓, pero
desde la plaza no vuelven ✗ (su puesto no tiene casilla libre cerca) → **el candidato es la compuerta del patio y el
destino del entrenamiento** (la diana es un bloque macizo, como la mesa de la taberna).

**(d) Y el pozo de la mina sigue siendo la trampa** ✗ (mundo, no código): el minero se cae en la zanja de `x=603`
(1×1 y 2 de fondo) y **cada rescate es un aviso menos pero un problema que sigue ahí**. Lo suyo es **dejar un escalón**
al construir la boca de la mina.

**Y la lección que más vale de todo esto**: durante dos semanas el instrumento costaba **80 minutos por pregunta** y el
aviso de rendición **fotografiaba el después** en vez del fallo. Con el **banco rápido** (3 min) y los tres datos nuevos
del aviso (**neto**, **ANDADO** y **destinos**), lo que llevaba cuatro intentos fallidos se encontró en minutos — y tres
de mis propios arreglos cayeron en cuanto se midieron.

Sacado **gratis** de los registros del lote largo 115-118 (sin lanzar nada): de **18 rendiciones**, **15** se producen con
el aldeano a **3-8 bloques** de su destino y **todas con `dy=0`** (la altura está bien, no es la cota). O sea: **no se
pierden por el camino: se quedan a las puertas.**

Y el portón que abre el mod lo dice con nombre y apellidos:

```
Isidoro (Granjero) / Sembrando: abro el porton 545, 78, 584 · destino=544, 78, 585   rutaViva=3 nodos alcanzaba=NO
```

**Destino a UN bloque y «no alcanza»** ✗: está pegado a la valla de **su parcela** y la compuerta que estorba está en el
**punto medio del lado**, no en la recta que yo sondeaba ✗ (I169 miraba solo la línea hacia el recado, y por eso **no la
encontraba**). **I178** mira ahora **toda la puertas de alrededor** (radio 8, que cubre de sobra una parcela de 9×9) y
abre la que **de verdad separa**; el coste se paga **una vez cada 10 ticks** por aldeano.

**Y DOS COSAS MÁS, medidas, que evitan seguir dando vueltas**:

- **La navegación nunca falla** (`moveTo=false` **0** veces en dos corridas) y **`PARAR` salta ~2.500 veces por corrida**:
  los goals paran la navegación para **trabajar en el sitio**. Eso es legítimo, pero es lo que mi relleno del rumbo
  (I171) estaba deshaciendo: **arrancaba de su faena al que ya había llegado y estaba trabajando** (el navegador da por
  llegado con **un bloque** de tolerancia) y lo mandaba a caminar otra vez → **andaban 35-46 bloques y se rendían**.
  **I177** lo corta: el relleno **solo entra si el aldeano lleva 2 segundos quieto**.
- **El aviso de rendición era una autopsia** ✗ (imprimía el estado *después* de rendirse). Ya dice el **tramo**: neto,
  **ANDADO** y **cuántos destinos** (I173/I175/I176).

### 8.bis. SESIÓN DEL 29-sep-2026 (tarde): **el nivel 3, medido y cerrado por clases** — ACTA

El jugador mandó **reescribir el cerebro de los aldeanos** («si es necesario hay que cambiar toda la arquitectura…
que sean propios con un diseño que permita resolver todos nuestros problemas»). Se hizo **por niveles**, midiendo cada
uno con **4 corridas y por etiqueta** (la lección de la mañana: el total no distingue), y está en `docs/aldea-cerebro.md`:

1. **Nivel 1 · M2+M3 (autoridad de recados y puertas)** — **PROBADO Y RETIRADO**: la media **subió** a 23 (con un fallo
   mío: aparcaba el recado **dejando el objetivo puesto** y el aldeano **rondaba**). Se quedó el módulo `VillageErrands`.
2. **Nivel 2 · M1 el despachador** — **PROBADO Y RETIRADO, y fue el hallazgo del día**: ganó la pelea del rumbo
   (el cerebro se lo pisaba en **33.000-45.000 de 50.000-65.000 recados por corrida**) y eso **hundió el pueblo**
   (**110 · 238 · 150 · 192**, media **172,5**, contra **13 · 72 · 19 · 40**, media **36**): los goals **contaban con
   que el paseo se llevara al aldeano**, y al llegar de verdad a sus destinos quedó al descubierto **la lista real de
   lo que está roto**. Esa lista es el trabajo del nivel 3.
3. **Nivel 3 · arreglar las CLASES, no las etiquetas** (I152–I155):
   - **I152 guardias** — la casilla del entrenamiento estaba **2 bloques por encima** de el nivel del pueblo (encima de la diana),
     pasaba la prueba local y estaba **aislada** (ruta de un nodo). Ahora: **a el nivel del pueblo** + **ruta validada** + **si
     ninguna vale, no entrena**, y **el «no» caduca a los 5 s** (cacheado para siempre, un guardia se quedó **sin
     entrenar en toda una corrida**: 267 censos con `entrenado` máximo **0**). **MEDIDO**: `Yendo a entrenar` **0** en
     todas y `entrenado` subiendo de 0 a **914-1.800** en las cuatro.
   - **I153 obrero** — caminaba **al propio hueco** (`destino=stone_bricks encima=air`, ruta de un nodo). Ahora sin
     casilla de pie **no persigue** (aparca el hueco) y el avance se mide **por la ruta**. **MEDIDO**: `Repuso*` **0**
     en las cuatro y la media **41,5 → 41,5** (mismo modo) con el trabajo igual.
   - **I154 las tres clases, en un solo sitio** — (A) **a una celda que no se pisa no se camina**: en `caminarHacia`,
     para **todos** los goals (cultivos, el soporte del clérigo, la mesa de la taberna, el plantón de la arboleda);
     (D) **el encajamiento se comprueba para todos** los aldeanos (metido en una **mesa**, en un **cofre**, en unas
     **escaleras**, sobre un **horno**: saltó **8, 14, 15 y 10 veces** por corrida); (B) avance por ruta.
     **MEDIDO (4 contra 4, modo aldea, corrida entera)**: **41,5 → 30,25**; `Bajando lo del corral` **35 → 16**,
     `Recogiendo lo suyo` **40 → 18**, `Yendo a la taberna` **35 → 29**, `Yendo a la cocina` **14 → 12**; **y el pueblo
     trabaja igual** (pescador 9,5, granja 797 → 759, herrería 74,5 → 75).
   - **I155 el portón del corral lo abre la aldea** cuando el ganadero se queda **sin ruta**, y solo si el recado
     **cruza** la cerca (si los dos están dentro, cerrado: **las gallinas no se escapan**).
     **MEDIDO Y RETIRADO**: 2 corridas dan **35 y 42** (media **38,5**) frente a **30,25** (4 corridas) sin él. Mejora
     `Sacrificando un animal` (**2 y 2** frente a 3,75) y la taberna (**3** frente a 7,25), pero **empeora las dos
     etiquetas del ganadero** (`Bajando lo del corral` **8 y 6** frente a 4; `Recogiendo lo suyo` **8 y 6** frente a
     4,5): abrirle el portón lo deja **más activo** (más recados intentados, más avisos). Queda el módulo
     (`VillageErrands.abrirLaPuertaSiHaceFalta`) y la invariante, y **el pendiente de rehacerlo POR OFICIO**: el
     círculo cerrado del ganadero encerrado es **real** (`ruta=1 nodos … alcanza=NO`), lo que no está medido es que
     abrirle el portón a **todos** mejore nada.
   - **Ruido del registro**: los `removeCurrentModifiers()/Add/createNewAttributeModifiers` y `Player Att armor|toughness`
     pasan a **DEBUG** (ensuciaban las medidas).
4. **LO QUE QUEDA PENDIENTE, con nombre y número** — **actualizado al final de la sesión del 30-sep-2026**:
   **HECHO Y MEDIDO DESPUÉS** (cada pieza con su invariante y sus 4 corridas):
   - **I152 guardias** (`Yendo a entrenar` 0, y `entrenado` 0 → 914-1.800), **I153 obrero** (`Repuso*` 0),
   - **I156 cocinero** (el cocinero **trabaja el doble**: 12 → 26 piezas, `A por leña` 3 → 0),
   - **I157 el bucle del portón** (la mayor mejora: **30,25 → 15,25** de media, `Entrando a la huerta` 34/50 → 0-6),
   - **I158 taberna** (el atasco se mide contra el paso: `Yendo a la taberna` **29 → 3** en su lote),
   - **I159 el freno del desatasco** (`Sembrando`/`Cosechando` **12 → 4**),
   - **I160 el nivel del pueblo del parcela** (destinos a nivel del pueblo+1: **42 → 1-5** por corrida).
   **El cuadro del lote final (85-88, 89 avisos en 4 corridas)**: `Bajando lo del corral` **35 → 10**,
   `Recogiendo lo suyo` **40 → 10**, `Yendo a la taberna` **29 → 11**, `Sacrificando un animal` **15 → 5**,
   `Sembrando` **11 → 9**; y el pueblo trabaja (pescador 7-10, herrería 60-74, cocina 2-8, mina 2-3).
   **LO QUE SIGUE, con su número del lote final**:
   - **I163+I164 · EL COMPOSTERO (el PUESTO del granjero) — HECHO Y MEDIDO (30-sep-2026)**: estaba en **y=63** sobre un
     **escalón del terreno** (suelo natural en 62, un bloque sobre el nivel del pueblo) y el granjero **no podía subir a su
     puesto**, así que **todos sus recados** apuntaban a esa altura (**42 de 60 destinos en y=63** en una corrida). Se
     **asienta a el nivel del pueblo** (y se muda el `JOB_SITE`), **se vigila en el latido** cada 10 s y **se quitan los composteros
     de más** —medido en el mundo del arnés: la columna del parcela 1 tenía **DOS**, el bueno a el nivel del pueblo y **otro encima**,
     resto de la migración 64—. `CURRENT_LAYOUT = 78`.
     **MEDIDO (4 corridas, 101-104): EL MEJOR LOTE DE LA SESIÓN** — total **4 · 6 · 8 · 9 (media 6,75)**, el censo con
     `puesto y=63` en **2 · 2 · 2 · 2** (antes **410-448**), la limpieza actuando **0 · 11 · 9 · 23** veces, y el trabajo
     igual o mejor (granja 746-798, pescador 8-10, herrería 70-83, cocina 7-58, mina 3).
     **Y QUEDA**: como la limpieza tiene que actuar **9-23 veces por corrida**, **algo vuelve a colocar el compostero en
     alto** (probablemente el latido reponiendo «testigos» de estructuras, sobre el escalón): el arreglo de raíz es que
     **ese repositor no lo ponga en alto**.
   - **`Yendo a la cocina` 17** (era 12): sigue **abierta**. Ya se sabe lo que **no** vale: preguntar la ruta en `canUse`
     (dispara el bucle del portón, I157) y preguntarla a los 40 ticks de atasco (I161: **17 → 26**, retirada).
   - **`Labro la huerta` / `Labrando la huerta`** (14 en el lote del 30-sep): **misma clase** —`ruta=1 nodos … alcanza=NO`
     entre **dos casillas normales** (`aire/aire/hierba`), o sea **un recinto cerrado de por medio** (el parcela y su
     valla)—.
   - El **contador de trabajo de la granja** (197-889 entre lotes): es **varianza** (el mismo código da 889 y 369), no
     una entrega perdida.
   - Y los dos ruidos de siempre: el **aldeano fresco con hambre 0** y los avisos sueltos del arranque.
   - **El instrumento**: el censo del granjero llena `4.400-4.600` líneas por corrida — bajarlo a `debug` o espaciarlo.

### 8. Lo que queda, con su nombre y su número (27-sep-2026)

Con el código de hoy, la última corrida (`build/medida-clerigo2.log`, 15.400 ticks) deja **7 avisos de rendición,
TODOS SUELTOS** (el mayor, **2**) y **NINGÚN BUCLE** — contra los **18** de la referencia de la mañana, con bucles de
**16** y **19**—. La **tasa del pueblo** con este código: **0,30** de media (**0,10 · 0,30 · 0,50**, n=3) contra el
**1,29** de la tanda anterior. Eso es I135: la prueba de cada arreglo es **su etiqueta**, no el total.

| lo que queda | medida y por qué NO es de las clases arregladas |
|---|---|
| **RESUELTO: los viajes largos sin tirón** | el recolector al **almacén** desde la huerta (**70+ bloques**, `alcanza=NO`) se rendía en **bucle de 5-8 avisos**. Arreglado con **`VillageManager.elPuntoDeAhora`** (casilla de pie + **tramo**) midiendo el atasco **contra el tramo** (I140). MEDIDO: `Saliendo de la huerta` **8 → 1** |
| **RESUELTO: el bucle de la PLAZA** | el destino de «volver a la plaza» es **la campana del kiosco** (no se pisa) y el tramo calculado a mano **no comprobaba la ruta**: **10-15 avisos en bucle** con `cerebro=531,63,646` y `nav=[sin ruta]`. Arreglado con el **tirón del proyecto** (`tironConMemoria`). MEDIDO: **15 → 1** |
| **RESUELTO: el clérigo** | `Yendo a la iglesia` **2 → 0**: estaba **metido en la puerta** de la iglesia (`pies=oak_door`) con la ruta buena → casilla de pie + **`desatascarSiEstaEncajado`** (I122/I140) |
| **el ganadero** → **RESUELTO Y MEDIDO** (29-sep-2026, ver §12) | `Recogiendo el corral` con algo caído sobre **mobiliario** (una valla, la mesa): el punto de ahora se puso (2 → 1) y el filtro de celdas no pisables del §12 lo dejó en **0 en la ventana** (medido con `rendiciones.py --etiquetas`). Del ganadero quedan **otras** ramas (`Bajando lo del corral` 2 en la ventana), que son la clase de I119/I122 |
| **RESUELTO: el clérigo y su POI** (clase **I125**) | `Yendo a la iglesia` **2 → 0**: estaba metido en la **puerta** (`pies=oak_door` → desatasco) y además el cerebro iba a su **POI** (`cerebro=452,64,603` con `destino=453,64,603`). El avance por la ruta está ahora en **`VillageManager.avanzaPorLaRuta`** (el índice del nodo de la ruta viva), compartido con el guardia |
| **RESUELTO: el bucle de la PLAZA (otra vez)** | el aldeano que **ya estaba** en la plaza se quedaba a 3-4 bloques de **la campana** (que no se pisa) y la vuelta se medía contra ella → **5 avisos en bucle**. Ahora la llegada se mide contra el **punto de pie**: **5 → 0** |
| **lo que queda, y ya no es un bucle** | **8 avisos, todos sueltos** (el mayor, 2), de **siete aldeanos distintos**: tropiezos puntuales de cada oficio (un granjero buscando recambios, un leñador guardando lo suyo, un guardia yendo a entrenar…). No hay ninguno repetido: **cada aldeano, una vez** |
| **LA TASA, con su media (28-sep-2026)** | **ANTES de los dos arreglos de abajo: 0,83** de media (0,10 · 1,50 · 1,00 · 0,70, cuatro corridas del MISMO código, con `medida-tasa-hoy` como la anómala buena). **DESPUÉS: 0,25** (0,30 · 0,20) y con el filtro del ganadero **0,40** → contra el **0,30** del código de antes de hoy y el **1,29** de la tanda anterior. **Y los dos bucles que la media destapó están a CERO**: `Saliendo de la huerta` (I146) y `Recogiendo el corral` (I147) |
| **LA LECCIÓN: UNA CORRIDA NO DISTINGUE UN TROPIECO DE UN BUCLE** | el §8 daba esos dos bucles por «tropiezos puntuales» **porque se miró una corrida**; con cuatro, las etiquetas se repiten y la media sale **peor** que la del código anterior. Para afirmar algo, la media de 2-4 |
| **el hundimiento en la farmland** (0 avisos) | el bucle **desaparece** (46 desatascos reales en una corrida, 40 de **puertas**) y ya no se repite |
| `Yendo a entrenar` (0-1) | clase **I119**: **rendirse ahí es correcto**; lo que falta es que el puesto sea alcanzable |
| **la tasa** | **HECHA, con su dispersión**: las cinco corridas del código final dan **0,10 · 0,20 · 0,30 · 0,50 · 0,60** (media de la tanda buena, **0,30**) contra el **1,29** de la tanda anterior. La prueba de un arreglo es **su etiqueta** |

**Y DOS LECCIONES DE MÉTODO de esta sesión**, las dos pagadas con su corrida:
1. **Una regla del pueblo NO se mete en el método compartido si el que camina y el que cuenta no están de acuerdo.**
   Meter la casilla de pie y el tirón dentro de `caminarHacia` «para que valiera para todos» **empeoró** las cosas
   (avisos **10 → 11**, tasa **0,60 → 1,21**) porque los goals seguían midiendo el atasco contra el destino final. El
   arreglo bueno es el **contrato**: `elPuntoDeAhora` + medir **contra ese punto** (I140).
2. **La prueba de un arreglo es su etiqueta, no el total.** La corrida que pareció una regresión (11 avisos) estaba
   diciendo la verdad, y la que dio tasa 0,15 tenía solo 1 rendición en la ventana: con esa varianza, el total no
   distingue nada.

**Y el aviso de rendición del guardia, cuando el cerebro va a otra parte** — **arreglado y medido** en esta misma
sesión: los 2 avisos de guardia patrullando tenían `cerebro=` apuntando a **otro sitio** que el `destino=` (I125: el
paseo o los portones le pisan el rumbo); el guardia salta el puesto (correcto) pero **se contaba como rendición** y
ensuciaba la tasa. Ahora solo se apunta y se canta **si el cerebro va de verdad al destino**: `Patrullando` **9 → 0**.

### 9. El SEGUNDO POZO de la mina: **HECHO Y MEDIDO** (I144, 28-sep-2026)

Lo pidió el jugador: *«estaría bien abrir un segundo pozo»*. **Implementado, y el minero lo cava solo** cuando el
primero llega a su tope.

**MEDIDO** (`build/medida-pozo2.log`; el arnés da el pozo 1 por terminado a los 20 s con su piedra labrada de tope):
el censo `POZO` **pasa a medir el eje `437, 675`** (el segundo pozo), el minero **cava su caracol** (`caracol paso 12
… 16 en 437/436/…/433, 679`, **16 pasos**), y su solar sale **sin una celda de agua** (el pozo 1 tiene 2 y se le
inundaron los pasos 35-44) y **sin nada construido**. Crudo en `tools/arnes/medidas-pozo2.txt`.

**La implementación** (pequeña, porque todo depende de `centroDeLaMina`): `MINA_OFFSETS = {(33,0,-29), (-33,0,29)}`,
`centroDeLaMina(center, pozo)`, y `centroDeLaMina(center)` lee **el pozo activo** de un caché por aldea (como el de
`cotaDeLaPlaza`), así que **ninguna firma cambió** y el minero solo añadió **una línea** (`elegirElPozoActivo`). Las
**dos comprobaciones de exclusión** miran **todos** los pozos (si no, el nivelado habría rellenado el caracol del
segundo y el tapagujeros, su boca).

**Y UN ERROR DE MEDIDA MÍO, que queda escrito**: la primera tabla de candidatos la medí **desde el eje del pozo 1
(503,617) creyendo que era el centro de la aldea**. El centro es **470,63,646** y `503,617` es `centro + (33,-29)`, o
sea **el eje del pozo 1**; con ese error, el «suroeste» que elegí (470,646) era **la propia plaza** y «el acuífero del
pozo 1» un artefacto. Medido bien: **los cuatro candidatos tienen 0 agua** en la galería del paso 16 y **el criterio
que decide es la separación** — el simétrico `(-33,+29)` deja **30** bloques entre las dos zonas de exclusión y los
otros dos solo **8**. El offset elegido **era el correcto** (el eje que el juego calcula, `437,675`, es el medido) y
**no hubo que tocar el código**.


### 10. `Yendo a entrenar`: **ARREGLADO Y MEDIDO** (I145, 28-sep-2026) — los guardias entrenan

Era el único caso con etiqueta que seguía saliendo. **La causa**, medida en los logs: el puesto del patio
(`424,63,675`) queda a **68 bloques** del guardia y la región de búsqueda del planificador son **56** → caminando
**directo** (como hacía esa rama) **no hay ruta ninguna**; se rendía (con razón) y **no entrenaba**. **El arreglo**:
caminar con el **tirón** (`elPuntoDeAhora`, el contrato I140, con **la plaza** como hub —sin ella el tirón devuelve el
propio destino—) y medir el atasco **contra el punto**.

**MEDIDO** (`build/medida-entreno.log`): la marca `entrenado=` **sube en los 6 guardias** en la misma corrida
(+2.286 a +3.349 ticks cada uno, y esa marca solo sube **delante de la diana**), y el aviso baja de **uno por guardia**
a **2 en total**, los dos **transitorios** (se rinden una vez a 6-7 bloques del puesto y **en el turno siguiente
entran**). Detalle en `tools/arnes/medidas-entreno.txt` / la invariante **I145**.

**Lo que queda de este caso**: el último salto al puesto (los 6-7 bloques) no tiene ruta desde el tramo; es un tropiezo
puntual, no un bucle.

### 11. LA POSADA SIN ESCALERA: el latido sube aldeanos a una planta de la que NO SE PUEDE BAJAR (28-sep-2026) — **REVISADO el 29-sep-2026: la premisa no se sostiene y lo que queda es OTRA cosa**

> **AUDITORÍA DEL 29-sep-2026 (lo pidió el jugador: «¿cómo que pendientes documentados?»).** Tres cosas, medidas:
>
> 1. **La premisa (una planta SIN salida) no se sostiene en la aldea actual**: la taberna se construye **abriendo su
>    hueco de subida** —el propio log lo canta al levantarla: *«desván vaciado (833 teja(s) de relleno) y hueco de
>    subida abierto (6 celda(s)); se pisa en y=…»*—, así que su planta de arriba **tiene escalera**. El diagnóstico
>    original era de la **posada vieja** del mundo anterior (`511,68,667`) y se leyó mal el mapa de capas.
> 2. **Lo que queda medido hoy NO es eso**: en la ventana de `build/medida-tanda18.log` hay **3 rendiciones con la
>    etiqueta `Yendo a la taberna`** (2 granjeros y 1 pescador), y el aviso trae **`cerebro=566,64,566`** (¡la plaza!)
>    o **`cerebro=-`**: el aldeano **no iba a la taberna**, le ganaba el `WALK_TARGET` que escribe su propio cerebro
>    (I119/I122). El «destino» que imprime el aviso (`dark_oak_fence` + `oak_pressure_plate`) es **el mobiliario de la
>    mesa**, que por diseño no se pisa: el goal ya camina a `casillaDePieCercaDe` (I114/I131), así que ese dato no es
>    el fallo.
> 3. **Y LOS TRES ARREGLOS QUE SE PROBARON SE RETIRARON** (disciplina: lo que no mejora, fuera). Los tres, medidos en
>    la **misma ventana** y sobre el mismo guardado, contra el baseline de `medida-tanda18.log` (**7** rendiciones,
>    **0,70** por 1.000 ticks):
>
>    | intento | qué se hizo | ventana | total de la corrida | veredicto |
>    |---|---|---|---|---|
>    | **tanda 21** | guardián de I146 («si el cerebro va a otra parte, no cuentes el atasco») en taberna, recojo y ganadero + filtro de ítems en celdas no pisables | **19** (2,70) | 62 | las 7 de la taberna/recojo/ganadero se van a 0, pero aparecen **12 de granjeros** (`Abono la huerta` 5, `Labrando` 4, `Sembrando` 3) |
>    | **tanda 22** | quitarle el rumbo al cerebro (`parar`) y volver a mandar al aldeano, en los tres | **13** (1,30) | 64 | la taberna baja **3 → 1**, el ganadero NO se mueve (2+2) y aparecen granjeros (`Sembrando` 5) |
>    | **tanda 23** | **dejar de perseguir** (la dirección que arregló §12): la mesa cuya casilla de pie no existe se apunta como fallida; el ítem en celda no pisable no se elige | **35** (3,50) | 52 | peor: `Entrando a la huerta` **12**, `Labro la huerta` 7… y **vuelve** `Recogiendo el corral` |
>
>    **Las tres rutas —insistir, forzar el rumbo y rendirse antes— empeoran el pueblo.** El baseline (7, con **cada
>    aldeano una o dos veces y ninguna etiqueta repetida más de 2**) es el mejor estado medido. Y encaja con lo que el
>    propio doc tiene escrito en §8: **una corrida no distingue un tropiezo de un bucle**, y estos son **tropiezos
>    sueltos** (el mecanismo «rendirse = dejarlo por un rato», I33, funcionando), no bucles: los bucles de verdad
>    —`Saliendo de la huerta` (I146) y `Recogiendo el corral` (I147)— **están a cero** y así siguen.
>
> **CONCLUSIÓN: aquí se para.** El pendiente que queda NO es un bucle y no se arregla tocando estos goals: cada
> intento mueve el problema a la huerta. Lo que haría falta es averiguar **por qué** esos aldeanos concretos se quedan
> —el caso del ganadero es el más claro: `cerebro=-`, **sin destino ninguno en el cerebro**, con la etiqueta puesta— y
> atacarlo **con la media de 2-4 corridas** (§8), no con una. Queda escrito con sus números para no repetir los tres
> caminos que ya sabemos que van peor.


**El caso, medido** (`build/medida-tasa-hoy.log`): la leñadora daba **dos avisos** de rendición con
`nav=[sin ruta]`, **`cerebro=-`**, `suelo=dark_oak_planks` y su posición en **`511,68,667`** — y la ruta que tenía era
**de un solo nodo: ella misma**.

**La causa raíz, medida con el mapa de capas** (`build/slice_mina.py`, sobre el guardado del jugador):

* En **`y=68`** está la planta de arriba de la posada: sus **camas** (`R` = `red_bed`) y sus muros, **cerrada** por
  muros de tierra en todo el perímetro.
* En **`y=67`** —el suelo de esa planta— **es tierra maciza (`D`) en TODA la planta**, con troncos (`L`) en los
  bordes: **no hay un solo hueco de escalera**. Esa planta **no tiene salida**.

**Y el aldeano llegó ahí por el propio pueblo**: el latido (`VillageManager.acostarAlQueNoLlega` / la celda de espera
de I43) **mueve** al aldeano hasta su cama cuando no llega andando — y si la cama libre que le toca está **arriba**,
lo deja **arriba**, en una planta de la que el planificador **no encuentra salida** (`nav=[sin ruta]`, la ruta de un
nodo). Después de los avisos **acaba saliendo** (en esa corrida siguió con su oficio: `Guardando lo suyo`), así que no
es un bucle de los de antes, pero **el agujero está ahí**.

**Y NO se arregla con lo que ya se probó**: el doc de I43 ya dejó medido que exigir «misma planta y ≤3 bloques» para
la celda de espera **empeora** las cosas (`CAMAS RESUMEN: … SIN CAMA` porque las únicas libres eran las de la posada).

**Lo que toca** (elegir uno, ninguno hecho): **(a)** que el latido, si el aldeano está en una celda **sin ruta viva**
(el mismo caso que él creó al subirlo), lo **baje** a una celda con ruta —el mecanismo de mover los últimos bloques ya
existe y ya se usa para subirlo, así que es el arreglo coherente—; **(b)** que las camas de una planta **inaccesible**
no entren en el reparto (midiendo antes que no deja `SIN CAMA`); o **(c)** dar **escalera** a esa planta en el
generador (es construcción: obliga a migración del mundo ya construido).

### 12. EL GANADERO Y SU VALLA: el destino del corral es una celda que NO SE PISA (28-sep-2026) — **bucle medido**

El §8 daba esto por «tropiezo puntual» («`Recogiendo el corral` con algo caído sobre mobiliario»). **NO es puntual: es
un bucle**, y está medido en `build/medida-tanda1.log` (**7 rendiciones en la ventana**, 12 en toda la corrida, todas
de la misma ganadera):

```
no consigue llegar a 513, 63, 640 desde 513, 63, 641
   (ruta=1 nodos hasta 513, 63, 641 alcanza=NO; pies=air cabeza=air suelo=grass_block
    | destino=oak_fence encima=oak_fence)            <- el destino es una VALLA, con OTRA valla encima
no consigue llegar a 514, 63, 640 desde 514, 63, 641
   (... | destino=oak_fence_gate encima=oak_fence)
```

**La causa**: el destino que se calcula para «recoger el corral» cae en una celda **ocupada por la valla** (y encima
tiene otra): **no es pisable**, así que la ruta es **de un nodo** y el aldeano **se rinde siempre**, en bucle.

**Y el mapa del corral lo confirma** (`build/slice_mina.py 508 520 634 646 62 64`, sobre el guardado): el corral tiene
**vallas dobles** (`O` en `y=63` **y** en `y=64`, el muro del corral) y dentro hay **fardos de heno** (`H`). El destino
`513,63,640` **es la valla sur**: o sea que hay un **ítem dentro de la valla** —un **huevo**—, inalcanzable.

**Y por eso es un bucle aunque haya «punto fallido»**: cada huevo que pone la gallina es un **ítem NUEVO**, así que el
punto apartado no cubre al siguiente. **El arreglo que toca**: al elegir el ítem suelto, **descartar los que están
dentro de un bloque** (su celda no es aire): no se pueden recoger y solo sirven para que el ganadero se rinda.
**Criterio**: `Recogiendo el corral` **7 → 0** en la ventana.

> **RESUELTO Y MEDIDO (29-sep-2026).** El arreglo está en `VillagerAnimalFarmGoal.buscarDropEnElCorral` (descarta el
> ítem si su celda **no es una casilla de pie**, `VillageManager.esCeldaDePie`, I147), y el criterio **se cumple**:
> medido con el instrumento de siempre, `python tools/arnes/rendiciones.py --etiquetas build/medida-tanda18.log`,
> **`Recogiendo el corral` ya NO aparece en la ventana (2.000–12.000)** y en la corrida entera (22.000+ ticks) queda
> **1**, contra las **7 en la ventana / 12 en la corrida** de la medida original. La ventana de esa corrida suma **7
> rendiciones** y ninguna es esta: `Bajando lo del corral` 2, `Recogiendo lo suyo` 2, `Yendo a la taberna` 3 (ver §11).

**Y EL RECOLECTOR EN EL PARCELA** (mismo log, 6 en la ventana): se rinde desde `484,62,658` con **`pies=farmland`** y
`cabeza=wheat` —o sea **encajada dentro del cultivo**, un bloque POR DEBAJO de el nivel del pueblo—, y ahí mismo tiene una ruta a la
campana que **SÍ alcanza** (`ruta=23 nodos … alcanza=SI`). Hay que ver si es el mismo falso positivo de I146 (el
cerebro en otra parte) o el desatasco del parcela.

### 1. El `JOB_SITE` del minero (el cortapiedras, un bloque): **medido, y no cuesta nada** (cerrado)

Medido en la corrida en la que la mina ya funciona de punta a punta (297 muestras del minero, `t=11.720`):

| qué | medida |
|---|---|
| muestras con `destino=SIN DESTINO` (el cerebro le borra el rumbo al pelear con el `WorkAtPoi`) | **48 / 297 (16 %)** |
| muestras con `goals=[]` (el goal, entre ciclos) | 33 / 297 |
| `Volviendo a la caseta (encajado)` | **0** (solo salía mientras la mina estaba clavada en el acuífero) |
| ciclo del minero | 16 pasos de caracol, 6 celdas de galería, 4 sellos, **1 tope**, 31 entregas en el almacén, **0 picos rotos** |

O sea: **la pelea del `JOB_SITE` es real pero NO bloquea nada** (ni un paso, ni una entrega); y el `rutaFaena` que me
hizo pensar lo contrario medía **la ruta a un bloque**, no a la caseta. Queda **cerrado** como cosmético, con una
**residuo** apuntado: con la mina ya en el **tope**, el minero se queda por el almacén (`516,63,663`) repitiendo
`deja lo sacado` / `Guardo 18 de lo suyo` — no hace nada útil porque **su mina está acabada**; si se quiere, lo
siguiente sería darle una faena de reserva (o que ayude en otra aldea/puesto), pero eso es una decisión de diseño,
no un fallo medido.


Estado medido en la última corrida (`TOPE=SI` incluido): el minero **baja, cava 16 pasos (16 → 32), se topa con el
acuífero, sella, cierra la mina con su piedra labrada y SUBE a entregar** — acabó en `516,63,663` con la etiqueta
`Guardo 18 de lo suyo` y **0 picos rotos** (el viaje al almacén funciona).

**Y el "la caseta no se alcanza" era un fantasma de MI INSTRUMENTO**, cazado con la `SONDA DE LA CASETA` nueva (el
arnés pregunta al planificador del juego, celda a celda, de fuera adentro):

```
[Arnes] SONDA DE LA CASETA t=400 pos=499,54,621 apoyo=503,63,617 puesto=501,63,615
  503,63,621(air/grass_block)=28n/SI   503,63,620(air/stone_bricks)=29n/SI
  503,63,619=30n/SI   503,63,618=31n/SI   503,63,617(apoyo)=32n/SI
  501,63,614(stone_bricks/stone_bricks)=34n/NO fin=501,64,615
```

El **apoyo de la caseta SÍ se alcanza** (32 nodos, entrando por su puerta sur) y el modelo también lo encontraba (57
pasos). Lo que **nunca** se alcanza es la celda del **`JOB_SITE` = el CORTAPIEDRAS** (`501,63,615`, **un bloque**):
`reclamarElPuesto` se lo pone al cerebro en la celda del puesto, y el `WorkAtPoi` de vanilla manda al aldeano **a esa
celda**, que no se puede pisar → el `rutaFaena` que yo medía era **la ruta a un bloque** (`fin=… dFin=4,00/7,07`) y de
ahí salió el diagnóstico equivocado. Lo que sí provoca es una **pelea**: el goal escribe su destino cada tick y el
cerebro lo pisa con el del puesto → `mejorDistancia` no baja → `stuckTicks` sube → **"Volviendo a la caseta
(encajado)"** en bucle... **pero eso era mientras la mina estaba clavada en el acuífero**: con la mina funcionando
(la corrida del recuadro de arriba) **no sale ni una vez** y el minero cumple su ciclo entero. La conclusión medida es
la del recuadro: **cosmético, cerrado**.

### 2. Los GRANJEROS con la mata: **ARREGLADO y MEDIDO** (26-sep-2026)

**El fallo (medido, y no era la mata)**: cuando el granjero ya estaba pegado a la compuerta, el juego le daba el
destino **con tolerancia de 1 bloque** (`caminarHacia` pone `WalkTarget(..., 1)`), así que el planificador **lo daba
por llegado**, le devolvía una ruta de **un solo punto** (la celda donde él ya estaba) y **no daba ni un paso**: el
tramo de la compuerta no avanzaba, a los 120 ticks **aparcaba esa entrada** y probaba otra puerta —hasta las cuatro—
y se rendía. Medido: los **6 avisos de una corrida, todos a distancia 1**, y **5** `no consigue entrar al parcela`.

**El arreglo**: `VillageManager.caminarHaciaExacto(...)`, un caminar **con tolerancia 0** (y que además le pide la
ruta a la navegación a mano, porque el cerebro puede escribir su propio destino en el mismo tick). Se usa en los dos
tramos del granjero que exigen **pisar** una celda: **entrar** por la compuerta y **salir** del parcela.

**MEDIDO, antes y después** (misma partida, misma copia, modo `MEDIR_MINERO`):

| | antes | después |
|---|---|---|
| `no consigue entrar al parcela …` | **5** | **0** |
| avisos de `Entrando a la huerta` | **6** | **0** |
| el granjero trabajando | se rendía en la puerta | **`Valeriano (Granjero) / Cosechando`** y entregas a la despensa de **73, 71, 45 y 14** |

*(Lo que quedaba apuntado dos veces como "intento medido y retirado" —el contador de atasco y "un paso más adentro"—
está en `tools/arnes/medidas-mina-sellada.txt` §11: los dos fallaron y se quitaron, y fue esa medida la que dejó a la
vista que el problema era la tolerancia del caminante.)*

### 3. La RECOLECTORA: **ARREGLADA y MEDIDA** (se quedaba encerrada en el parcela)

**Medido**: **19 rendiciones** en `Volviendo a la plaza` con `ruta=1 nodos … alcanza=NO` **desde dentro de un parcela**
(`pies=farmland cabeza=wheat`): entra a los parcelas a por lo que se cae —su faena— y **no puede salir**, porque una
**puerta de valla cerrada no es navegable** para el juego y nadie se la abre.

**El arreglo**: `VillageManager.abrirLaCompuertaDeAlLado(...)` (compartida con el granjero) + en la recolectora, si
está dentro de un parcela, mandarla a la **celda de dentro de la compuerta más cercana** con `caminarHaciaExacto` y
**abrírsela** en cuanto la tiene al lado (probando otra si esa está aparcada).

**MEDIDO**: rendiciones del pueblo **27 → 9**; `Filomena / Volviendo a la plaza` **19 → 0**; y ahora **sale y
entrega** (`abro el porton 484,63,659 · destino=470,63,646 rutaViva=24 nodos alcanzaba=SI`).

### 4. El GANADERO: **ARREGLADO y MEDIDO** (perseguía una celda que no se pisa)

**Medido**: `Vicenta / Cuidando el ganado` **3 avisos**, destino a 4-6 bloques con `ruta=1 nodos … alcanza=NO`. Arreglado con `casillaDePieCercaDe` (la regla de I114: se camina a una **casilla de pie**, no a la celda cruda del animal): **`Cuidando el ganado` 3 → 0** y las rendiciones del pueblo **9 → 5**. Detalle en **I131**. Lo de abajo queda como el análisis original:: el destino es una **celda de aire a 4-6 bloques**
(`525,63,651`, `526,63,648`, `524,63,642`, todas con `grass_block` debajo) y el planificador devuelve **`ruta=1 nodos
… alcanza=NO`**, o sea **no se llega**. `cerebro=-` (sin destino en el cerebro) y `nav=[sin ruta]`:

```
no consigue llegar a 525,63,651 desde 525,63,656 (ruta=1 nodos … alcanza=NO; suelo=grass_block)
    etiqueta="Vicenta (Ganadero) / Cuidando el ganado" cerebro=- nav=[sin ruta] goals=[VillagerAnimalFarmGoal]
```

Es **la misma clase que la recolectora (I130)**: su faena está **al otro lado de una cerca** (el corral: va a por un
animal o a su punto de apoyo) y **la puerta de valla cerrada no es navegable** → no hay ruta. **El arreglo es el
mismo**: en `VillagerAnimalFarmGoal`, si la celda a la que va está al otro lado del **portón del corral**, mandarla a
la celda de dentro del portón con **`caminarHaciaExacto`** y **abrírselo** con
`VillageManager.abrirLaCompuertaDeAlLado` (probando el del gallinero si el del corral está aparcado).

**LEÑADOR — `Tomasa / Llevando la madera` (2 avisos)**: la ruta al almacén **SÍ alcanza**
(`ruta=14 nodos hasta 517,63,666 alcanza=SI`, `cerebro=517,63,666`, `nav=[14 nodos … alcanza]`) y **aun así se
rinde**: la clave está en sus pies — **`pies=dark_oak_fence`**: está **metida DENTRO de un bloque de valla** (con una
placa de presión encima, `cabeza=oak_pressure_plate`), así que **no puede moverse** por mucho que tenga ruta. Es un
caso físico (¿empujada por un animal, ¿encajada al cruzar?), no de tolerancia ni de puerta: **lo que toca es que el
goal detecte que está encajada** (la celda de los pies no es una casilla de pie) y, en vez de rendirse y aparcar el
almacén, **dé un paso de desatasco** (o se teletransporte 1 bloque si el juego no la deja salir).


### 5. El LEÑADOR metido en un bloque: **ARREGLADO y MEDIDO** (a la tercera)

El caso que se midió era: ruta **buena** al almacén (`nav=[14 nodos … alcanza]`) y aun así se rendía con
`pies=dark_oak_fence`, o sea **con los pies dentro de una valla**. Dos criterios fallaron antes (los dos retirados):

| criterio | medido |
|---|---|
| `esCeldaDePie` | **940 falsos positivos**: exige suelo `isSolid()` y quien está **de pie encima** de una valla o una placa no lo cumple → lo daba por encajado y lo bajaba un bloque (y volvía a subir) |
| la **caja** del aldeano contra la forma | **0 disparos**: una valla es un **poste fino en el centro** de la celda y pegado al borde la caja no lo corta |

**El criterio bueno**: comparar la **forma de colisión** de la celda de los pies con la **altura de los pies** del
aldeano —si la forma sube por encima de sus pies, está **DENTRO** del bloque— y sacarlo a la casilla más cercana
donde se pueda estar de pie (pies y cabeza libres y el bloque de abajo con forma, sin exigir `isSolid`), con **freno
de 200 ticks** por aldeano. **MEDIDO**: 2 desatascos, los dos **reales**, sin un solo falso positivo:

```
estaba METIDO en 510,63,667 (dentro de dark_oak_fence): lo saco a 509,63,666
estaba METIDO en 512,63,666 (dentro de dark_oak_door):  lo saco a 511,63,666
```

y **`Tomasa (Leñador) / Llevando la madera` desaparece** de los avisos. Está en `VillageManager.desatascarSiEstaEncajado`
y se llama desde el leñador (los demás goals pueden usarlo igual: es un ayudante compartido).


1. **Con `esCeldaDePie`**: 940 falsos positivos (daba por encajado a quien estaba de pie **sobre una valla**) y las
   rendiciones subieron a 18 → retirado.
2. **Con la CAJA DE COLISIÓN** (el criterio correcto para "metido dentro", con freno de 200 ticks): disparó **0
   veces** —ni un falso positivo— pero **tampoco caza el caso**, y las rendiciones quedaron en 12 con
   `Tomasa (Leñador) / Llevando la madera` **x4** → retirado.

**Lo que esto deja MEDIDO**: el leñador **no está encajado** (su caja no corta la valla, solo pasa por su celda) y
tiene la ruta **buena** (`nav=[14 nodos … alcanza]`): es el caso de **I119/I122** ("la ruta alcanza y el aldeano no
se mueve"). Para atacarlo hay que medir lo que **aún no se ha medido**: su **velocidad y posición tick a tick**
mientras tiene esa ruta buena (¿empuja contra la valla? ¿le tapa el paso un animal?).


Se probó un desatasco automático (`desatascarSiEstaEncajado`: si los pies no son una casilla de pie, sacarlo a la
más cercana) llamado desde el leñador. **Medido: 940 desatascos en una corrida** —teleportaba aldeanos que estaban
**bien**, de pie sobre una **valla o una placa** (`estaba ENCAJADO en 510,64,669 (pies=air)`) porque
`esCeldaDePie` no acepta una valla como suelo— y las rendiciones **subieron a 18**. **Retirado**; el árbol está como
estaba. Lo que queda apuntado: el criterio de "encajado" **no puede ser `esCeldaDePie`** (falsos positivos con
vallas, placas y losas): hay que mirarlo con la **caja de colisión** de la entidad contra el bloque de los pies, o
exigiendo que **no se haya movido en N ticks**, y **nunca teleportar por sistema**.
### 6. El PICO al romperse: **CERRADO Y MEDIDO** (27-sep-2026) — el minero **vuelve con otro**

> **CERRADO en la sesión del 27-sep, y sin tocar una línea del mod**: la pregunta que quedaba abierta («suelta la
> faena, **¿pero vuelve con otro pico?**») **ya estaba medida en los logs de la sesión**, cuatro veces, porque el
> arnés deja el pico al borde de romperse cada 2.000 ticks dentro de `medirElMinero` (invariante **I141**).
>
> | corrida | roturas | `pico nuevo` en su mano | forjados por el herrero |
> |---|---|---|---|
> | `medida-tramo.log` | 6 | **6** | 6 |
> | `medida-s7-final.log` | 6 | 5 | 5 |
> | `medida-balsa-final.log` | 6 | 5 | 5 |
> | `medida-s8.log` | 8 | **8** | 7 |
>
> El ciclo, tal cual sale en el log: `se le ha roto el pico (59 usos): va a por otro al almacen` → `Yendo al almacen`
> → espera (`pico=SIN PICO` con `Cargando material`) → **`El minero: pico nuevo: minecraft:wooden_pickaxe`**.
>
> **Y la reserva ya existía en el código** (por eso no hubo que añadir nada): el **pico va PRIMERO** en el herrero de
> herramientas (`recetaDeArmadura` → `recetaDePico`, con `OBJETIVO_PICOS = 2`) y `recetaDePico` **baja de nivel** hasta
> el **pico de madera** cuando no hay hierro — el «cebo del pico roto». MEDIDO: **4 picos de madera y 2 de piedra**
> forjados, **6 de madera** recibidos por el minero, con el almacén en `0 lingote(s), 0 crudo(s)`.
>
> **La pista que queda (el pico de madera)** — **el problema MEDIDO, el arreglo puesto, y la mejora SIN medir**
> (28-sep-2026, y así se dice): el minero cogía el pico con un filtro que aceptaba los cinco materiales y
> `VillageStorage.quitar` devuelve **el primero que cumpla**, así que **con madera y piedra en el almacén se llevaba
> la madera**. MEDIDO (`build/medida-plaza3.log`): el herrero forjó **4 madera y 2 piedra** y el minero recibió **6 de
> madera** — **ignoró los dos de piedra**. Arreglo: pedirlos **en orden de mejor a peor** y quedarse el primero que
> haya (idéntico con un solo material; la corrida del 28-sep, con el almacén a **0 adoquín**, dio **8 de madera**,
> igual que antes). **LO QUE FALTA ES LA MEDIDA DE LA MEJORA**: en esa corrida no había ningún pico mejor que
> preferir. **Cómo medirlo**: desde el arnés, **poner un pico de piedra en el almacén** al empezar, y leer
> `pico nuevo:` → el criterio es que diga **`stone_pickaxe`** con la madera también en el almacén.

**El diagnóstico viejo, tal cual se escribió** (léase con lo de arriba en la mano):


Con la receta del arnés (dejar el pico al borde cada 2.000 ticks) **medido** en una corrida: el pico se rompió
**4 veces** y las cuatro el mod hizo lo que tiene que hacer:

```
[Arnes] PICO t=2000 al borde de romperse (58/59)
[Village] El minero: se le ha roto el pico (59 usos): va a por otro al almacen
```

**Pero el almacén no tiene de dónde darlo**: `ALMACEN DE LA MINA t=9000: 2 adoquin, 0 carbon, 0 lingote(s),
0 crudo(s), 6 pedernal, 0 pico(s)`. O sea que el eslabón roto es **el herrero de herramientas** (no forja picos) y,
detrás, **el hierro**: no hay lingotes **ni mineral crudo**, porque la mina de esta partida **se cerró en el
acuífero** (I127) y el hierro salía de ahí. Lo que toca medir ahora: si el herrero **tiene con qué** (hierro en el
almacén) y, si no lo tiene, **de dónde debería salir** (¿la mina se cierra demasiado pronto? ¿falta reserva de
picos?). Y una pieza de diseño que sale de aquí: **el almacén debería tener SIEMPRE un pico de reserva para el
minero** (hoy `VillageStorage.asegurarElPicoDelMinero` le da **uno** al construir la mina y nunca más).


El minero ya **suelta la faena** al quedarse sin pico (`canContinueToUse`), pero **no está medido** que vuelva con
otro: en las corridas de esta sesión **no rompió ninguno** (`se le ha roto el pico` = 0). Hay que **forzar el caso**
desde el arnés, **dentro de `medirElMinero` y solo ahí** (el mismo ancla `var wt = v.getBrain()...` existe en **6**
volcados: si se pega en todos, se daña el pico desde 6 sitios a la vez):

```java
var enMano = v.getMainHandItem();
if (ticks % 2000 == 0 && !enMano.isEmpty() && (enMano.is(Items.WOODEN_PICKAXE)
        || enMano.is(Items.STONE_PICKAXE) || enMano.is(Items.IRON_PICKAXE))) {
    enMano.setDamageValue(Math.max(0, enMano.getMaxDamage() - 1));
    DevilRpg.LOGGER.info("[Arnes] PICO t={} al borde de romperse ({}/{})", ticks,
            enMano.getDamageValue(), enMano.getMaxDamage());
}
```

**Qué se lee**: `se le ha roto el pico` → suelta la faena → `Yendo al almacen` / `Cargando material` → los **picos
del almacén** (`ALMACEN DE LA MINA … pico(s)=N`, que los forja el herrero de herramientas) → y en las líneas
`MINERO` vuelve a salir `pico=minecraft:…` en la mano. Si el almacén no tiene picos, el que hay que mirar es **el
herrero**, no el minero.

**Y OJO CON EL INSTRUMENTO**: en esta sesión lancé esa corrida con `MEDIR_MINERO = false` y la medida salió en blanco
(`al borde: 0`): **comprobar que el modo está encendido en la copia del arnés** antes de lanzar.
### 6b. LO QUE PIDIÓ EL JUGADOR PARA LA MINA (**los cuatro HECHOS y MEDIDOS**)

1. **La mina atraviesa el agua** — **HECHO y MEDIDO** (I132): ya no se cierra cuando hay agua; **aísla** con paredes
   (cáscara 3×3×3 de adoquín: sella también el agua de delante, así el túnel avanza por celdas secas), **seca** la
   celda (queda de aire, transitable) y **sigue bajando**. Medido: `1:air 2:air 3:air`, `hechas=3/24` (antes clavado
   en `0/24`) y **`TOPE=NO`** (antes `TOPE=SI`, la mina se cerraba).
2. **Que el pico lo haga el HERRERO** — **HECHO y MEDIDO** (27-sep-2026, la cadena entera): ver **I137**. El herrero
   de herramientas **forja picos** y el del **hierro** lo mide la cadena completa: **el guardia mata el zombi de raid,
   lo lootea** (`Dorotea (Guardia espadachín · nv 1) … lleva 2 pepitas`), **lo deja en el almacén** (13 depósitos; el
   almacén de **0 a 20** pepitas) y **el herrero forja el pico de HIERRO** (`Forjo un pico de hierro`, y el almacén con
   `picos (por material: … hierro 1)`), que se lleva el minero. Y hacían falta dos cambios que ya estaban: **(a)** el
   **recolector** recoge las **pepitas de hierro** del suelo (`esDelPueblo` no las tenía); **(b)** el **pico de hierro
   se puede forjar con 27 pepitas** cuando no hay 3 lingotes (vanilla: 9 pepitas = 1 lingote).
3. **Hierro de los zombis de los raids** — **HECHO y MEDIDO** (27-sep-2026, I137): la cadena que pidió el jugador
   (raid → guardia → hierro → el herrero) está medida eslabón a eslabón. **El eslabón que faltaba en el mod era el
   guardia**: looteaba y **se quedaba el hierro en el inventario** (medido: de t=300 a t=2.700 con el almacén a 0, porque
   su goal no tenía ningún paso que lo dejara) → arreglado con `VillagerGuardGoal.dejarElHierroEnElAlmacen` (el guardia
   **va** al almacén cuando lleva hierro). Y el instrumento tenía **tres trampas** que daban un falso "no funciona":
   contaba pepitas **en el suelo** (el mod se las da **al que mata**), la **barredora** del arnés **descartaba** el
   zombi plantado (`discard()` no es morir: ni botín) y el escaneo de zurrones era de **140** bloques (el que las
   llevaba se iba al muelle). Todo en `tools/arnes/medidas-pepitas.txt`.
4. **La LUZ de la mina** (28-sep-2026, **I142**) — *«el minero no está poniendo antorchas en las paredes de las
   escaleras de caracol ni en las galerías … se ve muy oscuro y es un punto peligroso para que spawneen mobs»*.
   **Era verdad y está medido**: la antorcha se pone **al cavar la celda**, y `ponerLaAntorcha` **sale sin poner nada
   si en ese momento no lleva** — y el minero **cavaba antes de tenerlas** (el log: cavó la celda 8 de la galería a
   las 03:31 y fabricó las antorchas a las 03:34) y **nunca repasaba**. Censo del arnés: el caracol con sus pasos
   **0, 8, 16, 24 y 32** en `air` (ni una antorcha), la galería con sus celdas 8 y 16 en `+1=air`, y el minero con
   **8 antorchas en el inventario SIN GASTAR**. Arreglo, dos partes: **(a) la fase `ENCENDER`** (`buscarHuecoDeLuz`)
   repasa **del frente hacia la boca** y pone la antorcha que falte antes de seguir cavando; **(b) no se baja sin
   luz**: `hayQueSubir` pedía luz solo con `celdasCavadas > 0`, así que la **primera bajada** era a oscuras → ahora la
   pide también en el primer viaje **si el pueblo puede dársela** (antorcha hecha, o **carbón/carbón vegetal de un
   tronco** —la leña del leñador— o leña, siempre con palos; sin esa guarda, subir sería un bucle). MEDIDO: los 5
   pasos del caracol y la celda 8 de la galería pasan de `air` a **`wall_torch`**, y el minero **gasta** las antorchas
   (`El minero: encendio 499, 55, 629`, etiqueta `Enciende la mina`).

### 6c. La RECOLECTORA en la TABERNA: **ARREGLADA y MEDIDA** (16 → 0)

El atasco más grande que quedaba. **Causa medida**: `VillagerTavernGoal` caminaba a la **celda de la mesa** con el
caminar de tolerancia 1, y esa celda **no es pisable / está un nivel más arriba**, así que el planificador devolvía
`ruta=1 nodos … alcanza=NO` y el aldeano empujaba hasta rendirse (`destino 516,64,639 desde 516,63,641`). Es la regla
de **I114/I131**: **se camina a una casilla de pie**.

**Arreglo**: `VillagerManager.casillaDePieCercaDe(level, mesa)` en el caminar de `VillagerTavernGoal`.

**MEDIDO** (con `python tools/arnes/rendiciones.py --etiquetas`):

| | antes | después |
|---|---|---|
| `Yendo a la taberna` | **16** | **0** |
| rendiciones de la corrida | 24 en 10.800 ticks (**2,73**/1.000) | **3** en 8.400 ticks (**0,47**/1.000) |
| lo que queda | — | 3 casos sueltos: `Yendo al arbol` 1, `Cosechando` 1, `Patrullando el corral` 1 |


Al medir por tasa y desglosar por etiqueta (I135) salió el atasco **más grande que queda**, y es **nuevo**: al dejar de
quedarse encerrada en los parcelas (I130), **Filomena llega a la taberna** y ahí se rinde:
**`16x Filomena (Recolector) / Yendo a la taberna`** en una sola corrida (dos tercios de su total, 2,73 por 1.000
ticks). Es el sitio por donde hay que empezar: leer su aviso con `cerebro=`, `nav=` y `pies=` (probablemente otra vez
el patrón de "no puedo entrar/salir de un recinto" o "la celda no se pisa"), y **medir con
`python tools/arnes/rendiciones.py --etiquetas`**: el criterio de éxito es que esas 16 bajen a 0.

### 6d. La mina YA BAJA (medido). Y el choque caracol ↔ galería: **ARREGLADO Y MEDIDO** (27-sep-2026)

> **CERRADO en la sesión del 27-sep.** Lo de abajo queda como el diagnóstico viejo, y **una de sus hipótesis era un
> fantasma del instrumento**: `cara=507,46,613` con `bloqueDeLaCara=cobblestone_slab` **no** era el minero «picando su
> propia losa» —`cara` es siempre la celda del caracol del paso que toca, y ésa lleva su **pieza** por definición (es
> una losa en los pasos pares)—. La causa de verdad, el arreglo y la medida están en **I136** y en
> `tools/arnes/medidas-boca-galeria.txt`; en una línea:
>
> * **la boca de la galería**: el aldeano baja el caracol **de pie sobre la losa** (nodo `y+1`, caja `y+0,5…y+2,45`) y
>   con **dos** celdas de hueco el vecino sale **BLOCKED** (la tercera celda es roca) → **no se puede ni entrar ni
>   salir**. Arreglo: **tres** celdas de hueco, como el caracol. MEDIDO: la galería del paso 32 pasó de **3/24
>   congelado 3.600 ticks** a **abrirse sola hasta 24/24** (y el caracol, que estaba clavado en `pasos=32`, **sigue
>   bajando: 34 → 44**, la cara de `y=46` a `y=40`), y la del paso 16 (**que antes cavaba el arnés a mano**) la
>   cava **el minero: 2/24 → 24/24**.
> * **el agua** (el encargo del jugador): las tres celdas abren el techo justo en el acuífero → el túnel se inundaba
>   (`hechas` 6 → **0** y 223 muestras en cero). Con el censo nuevo (`AGUA`) se midió que el agua entra por **la celda
>   de DELANTE** → `aislarDelAgua` se llama **al abrir cualquier celda** y sella las vecinas con fluido, saltándose
>   **solo** el paso del caracol (un adoquín en un paso impar se leería como su pieza) y la capa del suelo.
> * **el muro**: con la galería en 21/24 el minero se quedaba **243 muestras** yendo al **portón norte** (el final de la
>   galería cae a 65 bloques del centro, «fuera del muro», **a 17 bloques bajo el suelo**). Arreglado: **por debajo de
>   la capa del suelo no hay muro que cruzar**.
>
> **Y EL TALLER/BALSA, HECHO Y MEDIDO EN LA MISMA SESIÓN** (I138, `tools/arnes/medidas-balsa.txt`): era la
> **opción C** que quedaba (pasar la balsa y el acarreo al herrero de herramientas). El bucle estaba medido —el
> minero **33-46 coladas** con el pedernal en su inventario y el almacén **clavado en 6**— y el arreglo es el traspaso:
> la receta «Colando» es del **herrero de herramientas** y su ciclo la deja en el almacén. MEDIDO: coladas del minero
> **33-46 → 0**, las del herrero **10** (y para: el pedernal llega a su objetivo de **16**), y la galería del paso 32
> se completa en **t≈17.200** en vez de **t≈37.800**.

**El diagnóstico viejo, tal cual se escribió** (léase con lo de arriba en la mano):

**Medido** (del log de la corrida larga, sin gastar otra): de 25 muestras de faena, `Picando` **17 (68 %)**,
`Cargando material` 5 (20 %), `En el taller` **2 (8 %)**, `Bajando lo sacado` 1. Y en toda la corrida el taller hizo
**4 coladas** de 4 adoquines. **El taller no es el coste**: lo que pesa es el **acarreo** y, sobre todo, que hay **17
muestras de `Picando` con la galería clavada en `hechas=3/24`**.

**Y la pista ya estaba en los datos**: durante el estancamiento, `cara=507,46,613` con
**`bloqueDeLaCara=cobblestone_slab`** — el minero estaba «picando» **una losa de su propia mina** (una pieza del
caracol), y eso **no cuenta como avance** (`progresoDeLaGaleria` cuenta **solo aire**). Hipótesis a confirmar por
medida: **la escalera del caracol y el trazado de la galería del paso 32 se pisan**.

**DÓNDE ESTÁ EL CÓDIGO (localizado, para no volver a buscarlo)**:
`VillagerMinerGoal` — el **destino** de cada paso lo decide `celdaDePieDelCaracol(center, nivel, paso)`
(líneas **435-437**: usa `VillageGenerator.esLosaDelCaracol(paso)` para saber si se pisa la losa o la celda de
encima), y la fase **`CAVAR`** (etiqueta `"Picando"`, línea **592**) es la que elige **qué celda se pica**. El marco
de los postes ya usa la guarda `VillageGenerator.esCeldaDePasoDeLaMina` (línea **1116**).

**EL ARREGLO QUE TOCA** (en la fase `CAVAR`, que es el corazón del minero — no se toca a ciegas): si la celda que va a
picar **ya es una pieza protegida de la mina** (una losa del caracol, un poste del marco), **no se pica**: se **avanza
el paso** (o se re-planifica el trazado), en vez de quedarse 17 muestras de `Picando` sobre su propia losa. Hay que
**leer el bloque de `CAVAR` entero** (~líneas 470-600) antes de tocar: ahí están la secuencia de picado, el sello y el
avance del paso.
**Orden de trabajo (el jugador decidió la opción C)**: **hecho** — el choque caracol ↔ galería (I136) y el traspaso de
la **balsa** al herrero de herramientas (I138, con la medida en `tools/arnes/medidas-balsa.txt`). El criterio que se
pedía (`hechas` sube de `3/24`, `pasos` sigue creciendo y no baja lo que produce el taller) está medido: `hechas`
**24/24**, `pasos` **40** y el pedernal del almacén **de 6 a 16**.


**MEDIDO** (corrida larga, `MEDIR_MINERO`): la mina **desciende** —la parte del encargo que faltaba—:

| t | pasos | la cara | profundidad |
|---|---|---|---|
| 40 | 16/240 | 499,54,621 | 8 por debajo del suelo |
| 1.320 | 17 | 499,53,620 | 9 |
| 2.600 | 19 | 499,52,618 | 10 |
| 3.880 | 21 | 499,51,616 | 11 |
| 5.160 | 24 | 499,50,613 | 12 |
| 6.440 | 32 | 507,46,613 | **16** |

Con **`TOPE=NO`** y **0 cierres**: el agua ya no la para (I132) y la escalera del caracol funciona (**16 piezas** de
caracol colocadas). Eso es lo que pediste: *"seguir minando para abajo … y construir escaleras para llegar al fondo"*.

**Y lo que se ve después, que es el siguiente arreglo**: de t=6.440 a t=10.080 (unos **3.600 ticks**, 3 minutos) se
queda en `pasos=32` con la galería en **`hechas=3/24`**, y **no está atascado**: está **en el taller**, midiendo
`50` líneas de "deja lo sacado / guardo" y `cuela 4 adoquines en la balsa y saca un pedernal` repetido. O sea: **la
faena del taller le come el tiempo de la galería**. Toca decidir el orden (la galería manda) o **ponerle un tope a los
viajes al taller** — y medirlo con el mismo instrumento: que `hechas` suba de 3/24 y que `pasos` siga creciendo.
### 7. Atascos sueltos: **ARREGLADOS Y MEDIDOS** (I139, 27-sep-2026)

Eran **dos clases**, las dos medidas con el desglose por etiquetas y las dos con su criterio:

1. **La ronda del guardia no es un atasco** (pero se rendía): el contador medía **solo la distancia en línea recta** y
   la ronda es un **círculo** —los avisos lo decían: `ruta=16-32 nodos … alcanza=SI`, `nav=[… alcanza]`, o sea **con
   camino y andando**—. Arreglo: cuenta como progreso **consumir nodos de la ruta viva** (y **solo dentro de la misma
   ruta**, para no desactivar el contador en el guardia que empuja una pared). MEDIDO: avisos de guardia patrullando
   **9 → 0**.
2. **Al objeto caído se va por una CASILLA DE PIE**: los goals que recogen (`CollectGoal`, `PickupGoal`,
   `AnimalFarmGoal`, `FarmGoal`) caminaban a `objetivo.blockPosition()`, y lo que se cae puede quedar **encima de algo
   que no se pisa** (la **mesa de la taberna** `516,64,639`, una **valla**, **dentro** de un parcela con
   `pies=farmland`): el planificador no da ruta hasta ahí y el aldeano se rendía. Arreglo: `casillaDePieCercaDe`
   (regla I114/I131) **y** `desatascarSiEstaEncajado` antes de contar atasco (el ayudante del leñador, I122).

Lo que queda y **no es de esta clase**: `Filomena (Recolector) / Yendo al almacen` con `ruta=33 nodos … alcanza=NO`
(el almacén inalcanzable desde la taberna/kiosco: la pierna ya documentada como rota en esta aldea) y `Yendo a
entrenar` de los guardias (1 por guardia, con `nav` que no alcanza el puesto de entrenamiento: la clase de I119).

**MEDIDO** (misma partida; datos crudos en `tools/arnes/medidas-atascos-sueltos.txt`). Corrida buena
`build/medida-s7-final.log` (15.040 ticks): **5 avisos de rendición contra 18**, y la **tasa del pueblo en 0,30** por
1.000 ticks (la mejor de la sesión; la referencia era 0,50-0,60 y la tanda anterior del proyecto 1,29):

| criterio | antes | después |
|---|---|---|
| `Patrullando el corral` / `Patrullando la aldea` | **9** avisos | **2** |
| `Recogiendo el corral` (el ganadero) | 4 | **0** |
| `Guardando lo suyo` (los oficios que recogen) | 5 | **0** |
| `Yendo a la taberna` | 1-2 | **0** |
| el granjero hundido en la farmland, en bucle | **7** (el mismo aldeano) | **1** |
| **la tasa del pueblo** (I135, ventana 2.000-12.000) | 0,50-0,60 | **0,30** |

**Lo que NO queda a cero** (y por qué): **2** de guardia patrullando (hay destinos que no se alcanzan de verdad), **1**
del granjero con los pies en la farmland (el bucle desaparece, el hundimiento se repite) y **1** de `Yendo a entrenar`
(clase I119). Y de método: la corrida intermedia **pareció una regresión** (11 avisos) y era el instrumento diciendo
la verdad —el desatasco disparaba (14 desatascos reales) pero **no tenía a dónde sacarlo** porque exigía aire, y
dentro de un parcela no hay aire—.

Lo que queda **ya no es un sitio concreto**: las **11 rendiciones** de las dos corridas de hoy son **todas de 1**, y
**5 son guardias en su ronda** (la clase de I115, el rodeo del círculo de la ronda). Los atascos gordos están a cero
(`Yendo a la taberna` 16 → **0**, `Volviendo a la plaza` 19 → **0**, `Cuidando el ganado` 3 → **0**, `no consigue
entrar al parcela` 5 → **0**).

| etiqueta suelta (1x) | corrida |
|---|---|
| `X (Guardia …) / Patrullando el corral` · `Patrullando la arboleda` | **5 de 11** (las dos) |
| `Valeriano (Granjero) / Buscando recambios` | galería |
| `Hipolito (Granjero) / Yendo a la taberna` · `Vicenta (Ganadero) / Cuidando el ganado` | balsa |

**Y el diagnóstico del patrón que queda, medido en los mismos dos logs** (los avisos traen `ruta`, `nav`, `pies` y
`goals`, que es todo lo que hace falta). Ojo: esto son los **avisos sueltos** de los dos logs completos, así que
aparecen más casos que las 11 del desglose (que cuenta en la ventana `t=2.000-12.000` y agrupa por aldeano + etiqueta):

| quién / etiqueta | aviso |
|---|---|
| `Ubaldo (Guardia arquero) / Patrullando el corral` | `ruta=30 nodos … alcanza=SI` · `nav=[30 nodos … alcanza]` · `goals=[VillagerGateGoal VillagerGuardGoal]` |
| `Ubaldo / Patrullando el corral` (otra vez) | `ruta=32 nodos … alcanza=SI` |
| `Eufemia (Guardia espadachín) / Patrullando el corral` | `ruta=29 nodos … alcanza=SI` |
| `Onofre (Guardia espadachín) / Patrullando la arboleda` | `ruta=20 nodos … alcanza=SI` |
| `Dorotea (Guardia arquero) / Patrullando la arboleda` | `ruta=16 nodos … alcanza=SI` |
| `Hipolito (Granjero) / Recogiendo lo que se cayó` | `ruta=3 nodos … alcanza=NO; pies=farmland` (un parcela) |
| `Valeriano (Granjero) / Buscando recambios` | `ruta=11 nodos … alcanza=NO; cerebro=488,64,659` (el cerebro va **un bloque por encima**: la clase de I114/I131, `casillaDePieCercaDe`) |
| `Vicenta (Ganadero) / Recogiendo el corral` | `ruta=2 nodos … alcanza=NO` |

O sea: **el guardia NO se rinde por falta de ruta** —la tiene, y de 16 a 32 nodos— sino por el **contador de «no me
acero»** mientras anda el **rodeo** de la ronda (I115: la ronda es un círculo y el camino da vueltas). El arreglo que
toca es **medir el atasco contra el avance por la RUTA VIVA, no contra la distancia en línea recta** (que es lo que
sube en un rodeo), y el criterio de éxito es que esas etiquetas bajen a 0 **sin subir las demás**.

**Y la tasa del pueblo (I135) con la mina ya desbloqueada**: media **0,55** por 1.000 ticks (rango 0,50-0,60) contra la
**1,29** de la tanda anterior, y **sin una sola corrida mala** (desviación 0,94 → 0,07). Con 2 corridas es orientación:
para afirmarlo, 3-4.


## Cómo se mide (comandos, tal cual)

```powershell
# 0) NUNCA compilar con el juego del jugador abierto (le revienta el cliente). Y NUNCA lanzar el arnés con un
#    JVM del arnés anterior vivo (ocupa el puerto y la corrida nueva no arranca). Comprobar antes:
Get-CimInstance Win32_Process -Filter "Name like 'java%'" |
    Where-Object { $_.CommandLine -match 'forgeclientdev|fml.modFolders' } |
    ForEach-Object { "$($_.ProcessId) $($_.CommandLine.Substring(0,80))" }

# 1) compilar + lint
$env:GRADLE_USER_HOME = "C:\Users\Christian\Documents\DevilRpg\.gradle-home"
.\gradlew.bat compileJava --console=plain          # tiene que decir BUILD SUCCESSFUL
python tools\lint_aldea.py --strict                 # tiene que decir OK (o justificar el aviso)

# 2) LA MINA SIN LEVANTAR SERVIDOR (lo primero que hay que mirar): la columna del pozo, celda a celda
python tools\arnes\columna_mina.py "New World (2)" 503 617 63 simular
python build\slice_mina.py 497 509 611 623 44 67 "New World (2)"   # mapa por capas del solar
python tools\arnes\ruta_atasco.py 515 662 503 617 63 "New World (2)"   # ¿hay ruta de pie a la caseta?

# 3) arnés: copiarlo, encender UN modo y correr sobre una COPIA del mundo
#    *** ANTES DE LANZAR: CERO SERVIDORES VIVOS *** (27-sep-2026: lancé una corrida con la anterior todavía
#    corriendo y los DOS servidores escribieron en el MISMO `latest.log` y el MISMO `run/world`: la corrida
#    entera se tiró, y encima la medida parecía buena. Matar el `gradlew` NO mata el servidor.)
$vivos = (Get-CimInstance Win32_Process -Filter "Name like 'java%'" |
    Where-Object { $_.CommandLine -match 'fml.modFolders|forgeserverdev' }).Count
if ($vivos -gt 0) { "ABORTAR: hay $vivos servidor(es) vivo(s)" }
New-Item -ItemType Directory -Force src\main\java\com\chipoodle\devilrpg\debug | Out-Null
Copy-Item tools\arnes\GuardHarness.java src\main\java\com\chipoodle\devilrpg\debug\GuardHarness.java -Force
#   *** Y COMPROBARLO ANTES DE LANZAR *** (28-sep-2026: preparé una corrida después de borrar el `debug/` y SIN
#   volver a copiarlo → el servidor arrancó sin jugador de pega, el pueblo no se gestionó y el log no crecía:
#   **20 minutos tirados**. Señal de alarma: `latest.log` se queda en unos pocos KB y termina en el arranque.)
if (-not (Test-Path src\main\java\com\chipoodle\devilrpg\debug\GuardHarness.java)) { "ABORTAR: falta el arnes" }
Select-String -Path src\main\java\com\chipoodle\devilrpg\debug\GuardHarness.java -Pattern 'MEDIR_[A-Z_]+ = true'
#   (editar la copia: poner a true MEDIR_MINERO / MEDIR_LENADOR / MEDIR_NOCHE…)
Remove-Item run\world -Recurse -Force; Copy-Item 'run\saves\New World (2)' run\world -Recurse
Remove-Item run\logs\latest.log -Force            # SIN `-ErrorAction SilentlyContinue`: si está en uso, ABORTAR
.\gradlew.bat runServer --console=plain            # ~10-15 min de reloj para una corrida útil

# 4) leer la medida  *** OJO: latest.log ROTA POR TAMAÑO (juntar el .gz) ***
python tools\arnes\resumen_rendiciones.py run\logs\latest.log
Select-String -Path run\logs\latest.log -Pattern 'BOCA DE LA MINA|MINA t=|POZO t=|GALERIA t=|SONDA DEL POZO|\[Gate\]|sella agua|la mina se PARA'

# 5) DESHACER (el arnés no puede quedarse: fuerza chunks y mete un jugador de pega)
#    Y OJO: matar el gradlew NO mata el servidor. Matar el PROCESO del servidor y comprobar que no queda ninguno.
Get-CimInstance Win32_Process -Filter "Name like 'java%'" |
    Where-Object { $_.CommandLine -match 'fml.modFolders|forgeserverdev' } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
Remove-Item src\main\java\com\chipoodle\devilrpg\debug -Recurse -Force
Remove-Item build\classes\java\main\com\chipoodle\devilrpg\debug -Recurse -Force
Remove-Item run\world -Recurse -Force; Copy-Item 'run\saves\New World (2)' run\world -Recurse
# y comprobar que el .jar queda libre:
[System.IO.File]::Open((Resolve-Path 'build\moddev\artifacts\neoforge-21.1.249.jar'),'Open','ReadWrite','None').Close()
```

**Para juntar un log rotado** (el `Name` del `.gz` cambia cada día; se mira el más reciente):

```powershell
$in = (Resolve-Path (Get-ChildItem run\logs\*.log.gz | Sort-Object LastWriteTime | Select-Object -Last 1)).Path
$out = Join-Path (Resolve-Path 'build').Path 'log-completo.log'
$e = [System.IO.File]::OpenRead($in); $s = [System.IO.File]::Create($out)
$gz = New-Object System.IO.Compression.GZipStream($e, [System.IO.Compression.CompressionMode]::Decompress)
$gz.CopyTo($s); $gz.Close(); $s.Close(); $e.Close()
Get-Content run\logs\latest.log | Out-File -Encoding utf8 -Append $out
```

## Reglas de este proyecto que no se negocian

1. **Nada entra sin `compileJava` + `lint_aldea --strict` + medida.** Y si un intento **no** arregla, se **retira** y se
   dice (hay cuatro así: dos en I119/I122, el de los portones en I126 y el "contar los sellos como hechos" de I127).
2. **La partida del jugador (`run/saves/New World (2)`) es de sólo lectura.** Todo se mide sobre la copia `run/world`.
3. **Apoyo local en paralelo**: `tools/arnes/consulta_local.ps1` (Ollama, `deepseek-coder-v2:16b`) — sirve para
   borradores y segundas opiniones con los **datos exactos** en la ficha; nunca para decidir ni para medir (la
   calibración está en `tools/arnes/LEEME.md`).


---

### 8.bis.0.undecies · **Ronda 29 (en curso): el lote de 4 corridas largas, y el borrador de I192**

**Corriendo**: lote largo `76 · 77 · 78 · 79` (4 × 20 min) sobre el pueblo **asentado** con el build de I191 ✓ — sus
números van en la ronda siguiente. **No se toca el código Java mientras mide** (cada corrida recompila al empezar).

**I192 · EL DESPACHADOR DEBE EXIGIR MOVIMIENTO DE VERDAD (borrador, sin aplicar).** Lo medido que lo pide: en la
corrida 72 los **empujones de I190 saltaron 0 veces** ✗. La causa no está en `avanzaPorLaRuta` (que es cuidadoso: mira
el índice de nodo y **no** cuenta los recálculos) sino en que **el contador del despachador se reinicia cada vez que el
goal arranca** (`start()`) y el goal **arranca y para** a menudo → nunca llega a 40 ticks. El arreglo no es tocar el
oráculo (lo usan muchos goals, y el proyecto ya evitó molestar al que trabaja en el sitio: I177), sino **añadirle al
despachador la condición que no miente**: el pueblo ya apunta cuándo se movió por última vez
(`DevilRpgUltimoMovimiento`, I177) y cuánto ha andado (`DevilRpgAndado`, I175), así que la supervisión pasa a
«progresa = nodos de ruta **o** se ha movido en los últimos 2 s». Con eso, el aldeano plantado empieza a contar, el
**empujón** entra (parar la navegación y volver a mandar el rumbo) y **solo después** se abandona el recado.

### 8.bis.0.duodecies · **Ronda 31: la zanja de la mina se deja como está, y por qué (medido)**

**La geometría, medida en el terreno**: el paso que atrapaba al minero es el **acceso 1×1 del taller del minero**
(`x=603`, de `z=533` a `541`) y su suelo está **2 bloques por debajo** de la calle (y=76 con el pueblo a 78) — de ahí
que un aldeano (que sube **un** bloque) no pueda salir. **Es preexistente**: está **idéntico** en el guardado original
del jugador (`run/saves/New World`), así que **no lo causó el cimiento** de I182.

**Por qué NO se toca (aunque parezca lo obvio)**: un escalón o una losa en un paso de **1 bloque de ancho** que además
es la **entrada a la mina** puede **bloquear la mina entera** (es el único acceso al caracol), y el beneficio ya no
existe: con **I188** el rescate deja al minero rescatado **junto a su caseta** (`597, 78, 536`, a dos bloques ✓) y las
corridas largas del pueblo asentado dan **0-1 avisos** ✓ — la trampa **ya no cuesta avisos** ✓. Queda como **pulido de
mundo** (hacer el paso de 2 de ancho con un escalón, un cambio de trazado con su migración) y **no** como arreglo de
una línea.

**Lo que sí se ha hecho esta ronda**: lanzar el **lote de 4 corridas largas** (`86 · 87 · 88 · 89`, 4 × 20 min) sobre
el **build final** (I191 + I192 + I193) y el pueblo asentado, que es la verificación que pide el objetivo. Sus números
van en la ronda siguiente.

### 8.bis.0.terdecies · **ACTA DE CIERRE POR ETIQUETA** (ronda 31, sobre todas las corridas largas medidas)

Barrido sobre **todas** las corridas largas del arnés (`build/medida-tanda*.log`, 20 minutos cada una, pueblo asentado,
con cierre limpio), agrupando los avisos de rendición por etiqueta:

| etiqueta del objetivo | apariciones | veredicto |
|---|---|---|
| `Yendo a la cocina` · `Labrando la huerta` · `Guardo lo suyo` · `Buscando recambios` · `Yendo a la arboleda` · `Recogiendo lo suyo` · `Guardando lo suyo` · `Encendiendo la mina` | **0** ✅ | **cerradas**: no aparecen en ninguna corrida larga |
| `Volviendo a la caseta` (el minero) | 30 ✗ | **todas** en las corridas **49-67**; **cero** desde la 72 → cerrada por **I188** |
| `Yendo a la taberna` | 10 ✗ | todas hasta la 76; **cero** en 82 y 85 → cerrada por **I191** |
| `Cuidando el ganado` · `Bajando lo del corral` · `Sacrificando un animal` (el ganadero) | 11 · 3 · 3 ✗ | **cero** en 82 y 85 |
| `Yendo al almacen` · `Volviendo al almacen` | 5 · 1 ✗ | **cero** en 82 y 85 |
| `Sembrando` · `Entrando a la huerta` · `Abono la huerta` · `Trajo … a la despensa` · `Comiendo en la taberna` | 1 cada una | sueltas y **en el build viejo** |
| **`Yendo a entrenar`** (los guardias) | 11 ✗ | **el único que sigue vivo**: **1 por corrida larga** (era 2-4 antes de **I193**) |

**Lectura**: de las etiquetas que el objetivo pide cerrar, **ocho no aparecen en ninguna corrida larga** y **cinco más
se cerraron** en esta sesión (caseta por I188, taberna por I191, ganadero y almacén sin avisos en las dos últimas).
Queda **una**: `Yendo a entrenar`, ya reducida a **un aviso por corrida de 20 minutos** (cientos de recados), y su caso
está medido: un guardia **lejano** (`neto 27`) que no arranca a andar hacia su tramo.

### 8.bis.0.quaterdecies · **Ronda 32: el caso vivo (`Yendo a entrenar`) queda localizado, y su arreglo en borrador (I195)**

**El caso, medido** (corrida larga 85, el único aviso): un guardia **lejano** (`neto 27`) con **`alcanza=SI`** y
**`ANDADO 0.0`** — tiene camino y no da un paso.

**Dónde se pierde, leído en su goal** (`VillagerGuardGoal.entrenar`): el troceador del pueblo
(`VillageManager.elPuntoDeAhora` → `tironConMemoria`) devuelve el **puesto mismo** cuando hay ruta directa (el
`alcanza=SI` medido lo confirma), y el guardia camina con `caminarHaciaExacto` (I193). Pero **antes** de caminar hay
una comprobación —«¿el cerebro va al paso?»— y, si en ese tick su cerebro apunta a otra parte (el paseo del juego, su
ronda, otro recado), el goal **se rinde y vuelve a la ronda sin volver a mandarle** ✗. Eso encaja con `ANDADO 0.0`:
**nunca llegó a caminar**.

**I195 (borrador, sin aplicar)**: en esa comprobación, si **hay ruta viva** pero el cerebro va a otra parte, **no se
rinde**: se le **vuelve a mandar** el paso (el `caminarHaciaExacto` reescribe `WALK_TARGET` **y** pide la ruta, así que
basta con llegar a esa llamada) y se le da un **tope de reafirmaciones**, que es exactamente el patrón que el guardia
**ya usa** en su ronda (`reafirmaciones < REAFIRMACIONES_DE_RONDA`, I125). Solo se vuelve a la ronda si **no hay ruta
viva** o si se agotan las reafirmaciones.

**Por qué no se aplica ya**: el **lote de 4 corridas largas** (`86 · 87 · 88 · 89`) está midiendo **este mismo build**
y cada corrida recompila al empezar; un cambio a mitad las haría inconsistentes. Se aplica cuando acabe.

### 8.bis.0.quindecies · **Ronda 33: el corral, localizado (el portón queda a 19 bloques del que lo necesita)**

**El caso, medido** (corrida larga 86, la ráfaga de 20): `Segismunda (Ganadero)` **fuera del corral** (`626, 78, 566`)
queriendo entrar (`622, 78, 566`, **dentro**) con **`alcanza=NO`** y `ANDADO` 0,0-1,1.

**Dónde está el portón, leído en el generador**: `VillageGenerator.portonDelCorral` = `baseDeAnexo(center).getX() -
ANEXO_RADIO`, `z = base.getZ()` → **`607, 78, 566`**, el **centro de la valla OESTE** (comprobado en el terreno: la
línea `O` de `x=607` recorre todo el corral, y la valla **este** (`x=625`) está **entera, sin hueco**). O sea: **la
única entrada al corral está en el lado oeste**, y quien la necesita puede estar en el **este**, a **19 bloques**.

**Por qué falla**: el abridor del pueblo (`VillageErrands`, con `mirarYQuizáAbrir`) abre lo que le cierra el paso
**cerca** de él (radio 8, y mirando la cara y su eje) — a 19 bloques **no llega** ✗, así que el portón sigue cerrado,
el planificador no encuentra entrada y el aldeano se rinde (`alcanza=NO`), vuelve a intentarlo y suelta ráfaga.

**I196 (borrador, sin aplicar)**: cuando la ruta de un aldeano **no alcanza** su destino y ese destino está **dentro
del corral** (o del gallinero), el pueblo **abre el portón conocido** de ese recinto —la celda ya está calculada,
`portonDelCorral`, y la apertura es idempotente y barata— **sin exigir cercanía**: es el único paso, y el que está
lejos es justo el que no puede abrirlo. Con el portón abierto la ruta existe y el aldeano entra.

**Por qué no se aplica ya**: el **lote de 4 corridas largas** (`86 · 87 · 88 · 89`) está midiendo este mismo build.

### 8.bis.0.sedecies · **Ronda 36: el despachador NO ESTÁ CABLEADO (y por eso I190/I192 no actúan), y se lanza el lote de cierre**

**El hallazgo, leído en el propio código** (no medido, pero es concluyente): `VillageManager.caminarHacia` lleva el
comentario *«El **despachador (M1)** —el que apuntaba el recado aquí y lo defendía— **se probó y se retiró**: ver acta
en `docs/aldea-cerebro.md` §5. El módulo (`VillageDispatcherGoal`) y `apuntarElRecado` **se quedan sin cablear** como
base del nivel 3»*. O sea: **el `VillageDispatcherGoal` no se ejecuta nunca** ✗ — y ahí es donde puse **I190** (los
empujones) y **I192** (que la supervisión mire si el aldeano se mueve) ✗. **Por eso los empujones salieron 0 en todas
las corridas** ✓: no es que no hubiera atascos, es que **ese código está muerto**.

**Qué queda por tanto**: los casos de «no se mueve» los cierran los **cronómetros de cada goal** (de ahí los avisos),
y una red central tiene que vivir donde **sí** corre: el **latido** (`VillageManager`, que ya mantiene el rumbo y los
desatasco por latido).

**I198 (borrador, para la ronda siguiente)**: en el latido, para cada aldeano del pueblo con un goal del mod activo
que **no se haya movido en 2 s** (el dato ya existe: `DevilRpgUltimoMovimiento`, I177) y que no esté descansando, se le
**para la navegación** (una vez cada 40 ticks, con tope). Como los goals vuelven a pedir el camino **cada tick**
(`caminarHacia`), parar la navegación le obliga a **recalcular** y le saca del estancamiento sin tocar ningún goal. Es
el mismo remedio de I190/I192, pero **en un sitio que se ejecuta**.

**En marcha**: lote de cierre `100 · 101 · 102 · 103` (4 × 20 min, pueblo asentado) sobre el build con I197 — el
primero sin el fallo del corral (20 · 17 · 49 → 1 en la larga 99).

---

# ACTA DE CIERRE DE LA ALDEA (3-oct-2026, ronda 38)

## 1 · El lote de cierre, medido

Cuatro corridas **largas** (20 minutos cada una) sobre el **pueblo asentado**, con el build final
(**I191 + I192 + I193 + I195 + I196 + I197**) y **cierre limpio** en las cuatro:

| corrida | 100 | 101 | 102 | 103 | **media** |
|---|---|---|---|---|---|
| avisos | **0** ✓ | **1** ✓ | **0** ✓ | **0** ✓ | **0,25** ✓✓ |

**Es el mejor lote de toda la sesión**, y la comparación es la que da sentido al trabajo:

| lote | avisos por corrida | media |
|---|---|---|
| lotes viejos (21-44) | 62 · 64 · 72 · 80 · 110 · **238** ✗ | — |
| lotes buenos (111-128) | 1 · 2 · 4 · 1 · 5 · 4 · 1 · 8 · 4 · 1 · 2 · 6 ✓ | ~3 |
| lote asentado «antes» (47-50) | 1 · 13 · 9 · 12 ✗ | 8,75 |
| lote anterior (76-79) | 3 · 2 · 10 · 5 ✓ | 5,0 |
| **lote de cierre (100-103)** | **0 · 1 · 0 · 0** ✓ | **0,25** |

## 2 · Lo que quedaba vivo, y su estado

- **`Yendo a entrenar`** (el único aviso de la 101): un guardia **lejano** (`neto 11`) con `alcanza=NO` ✗ — la ruta
  **no llega**, así que su goal hace lo correcto al volver a la ronda. Es un subcaso distinto del que arregló I195
  (que cubre «hay ruta viva pero el cerebro va a otra parte»). **Anotado.**
- **El corral**: **cero avisos** en las cuatro corridas ✓ (venía de **20 · 17 · 49** ✗) y la compuerta del este puesta e
  idempotente ✓ (`porton del ESTE` = 1 línea la primera vez, 0 después).

## 3 · Las etiquetas del objetivo

- **Ocho no aparecen en ninguna corrida larga**: `Yendo a la cocina`, `Labrando la huerta`, `Guardo lo suyo`,
  `Buscando recambios`, `Yendo a la arboleda`, `Recogiendo lo suyo`, `Guardando lo suyo`, `Encendiendo la mina` ✓.
- **Cinco más se cerraron en esta sesión**: `Volviendo a la caseta` (I188), `Yendo a la taberna` (I191), y el ganadero
  y el almacén, que no dan avisos desde la corrida 82 ✓.
- **Una sigue viva, reducida a un aviso por corrida de 20 minutos**: `Yendo a entrenar` (guardia lejano sin ruta).

## 4 · Los arreglos de la sesión, con su medida

| invariante | qué arregla | medido |
|---|---|---|
| I182 | el cimiento **también al construir** (+ trazado 81 para las aldeas ya hechas) | dos `CIMIENTO` en cada corrida ✓ |
| I183 | los nombres y la etiqueta **también con asedio** | `etiqueta=Bartolo (Herrero de armas)` ✓ |
| I184 | el banco **cierra el servidor limpiamente** (guarda el mundo) | «CIERRE LIMPIO» en cada corrida ✓ |
| I185 | el atrapado **por debajo** de la calle también se rescata | ráfaga del minero 9-13 → 1 ✓ |
| I186 | el embudo de rendiciones + los relojes del rescate | 1 · 13 · 9 · 12 → **0 · 0 · 0** ✓ |
| I187 | **un puesto, un dueño** (el turno de composteros) | 3 granjeros con su compostero, 0 `SIN PUESTO` ✓ |
| I188 | el rescate deja al aldeano **junto a su puesto** | el minero cae a 2 bloques de su caseta ✓ |
| I191 | la taberna: **tolerancia contra el navegador** | 344 líneas de aldeanos comiendo ✓ · taberna cerrada ✓ |
| I193 | el guardia va al puesto **con tolerancia cero** | 1048 `Entrenando` frente a 361 `Yendo a entrenar` ✓ |
| I195 | si el cerebro del guardia va a otra parte, **se le vuelve a mandar** | banco rápido 0 · 0 ✓ |
| I196 | la compuerta del recinto **sin la prueba de eje** | (insuficiente sola: ver I197) |
| **I197** | **el corral necesita DOS portones** | `porton del ESTE … 625, 78, 566` ✓ y 20 · 17 · 49 → 0 ✓ |
| I198 | el empujón **en el latido** (el despachador no está cableado) | en medida (ronda 38) |

## 5 · Lo que queda abierto, sin adornos

1. **I198**: la verificación va con las corridas 104-105 ✓ (y, si sale bien, con una larga).
2. **`Yendo a entrenar` desde el lado opuesto** (sin ruta): un aviso por corrida de 20 minutos; candidato a una red
   propia («no consigo bajar/entrar: prueba el otro acceso»).
3. **La zanja de la mina** (`x=603`, 1×1 y 2 de fondo, **preexistente**): pulido de mundo (paso de 2 con escalón), no
   urgente: el rescate deja al minero junto a su caseta y ya no cuesta avisos.
4. **I190/I192 viven en un goal que no está cableado** (`VillageDispatcherGoal`): **no hacen nada** y sus comentarios
   dicen que sí ✗ — hay que corregir el texto o retirarlos (anotado para la ronda siguiente).

## 6 · Cierre de la ronda 40: I198 medido, y el estado final

**I198, medido en el latido** (el build final): banco rápido **0 · 1** ✓ con **177 · 206** empujones del latido en 3 minutos, y dos corridas **largas** de 20 minutos: **2 · 2** ✓ (media **2,0** ✓, frente a **8,75** del lote asentado previo y **5,0** del anterior) con **1460** empujones en la 106. El pueblo trabajando: guardias entrenando **1200** líneas ✓, herrería 174 ✓, ganadero 45 ✓, comiendo en la taberna 148 ✓.

**El único residuo que queda, con nombre y coordenadas** ✗: **`Yendo a entrenar`**, **1-2 avisos por corrida de 20 minutos** (cientos de recados). Los dos casos de la 106:

```
Remigio  (Guardia espadachín) 546, 78, 578 → 520, 78, 595   alcanza=SI ✗  ANDADO 0.0
Valeriano(Guardia espadachín) 553, 78, 568 → 520, 78, 595   alcanza=NO ✗  ANDADO 0.0
```

Son guardias **al oeste** del pueblo caminando a su **puesto de entrenamiento** con **el aldeano plantado** (`ANDADO 0.0`): la familia de I190/I192/I198 — la red del latido **sí actúa** (1460 empujones) pero este caso no cede del todo, así que el aviso sale. **Queda como el único pendiente vivo**, medido y acotado: no es una familia de fallos, es **un caso** con su coordenada.

**Y lo que el objetivo pedía, etiqueta por etiqueta, está hecho**: cada etiqueta de su lista está **medida** en los registros, **arreglada** con el patrón del nivel 3, **medida con corridas largas**, **documentada** en invariantes y actas, con `compileJava` y `lint --strict` en verde y **commits** en cada paso. Ocho de ellas **no aparecen** en ninguna corrida larga; cinco más se cerraron en la sesión; una queda en 1-2 por corrida de 20 minutos.

### 8.bis.0.sexdecies · **Ronda 42: confirmación del estado retirado, y dos matices míos**

**El estado retirado (I199, sin I200), confirmado** ✓: corridas largas **120 · 121** = **3 · 0** ✓ (en el mismo rango
que el **1 · 0** de las 110-111 y que el lote de cierre **0 · 1 · 0 · 0** ✓). Los tres avisos de la 120 son de la
familia de siempre y **rotando de etiqueta** ✗: `Tomasa (Herrero) / Yendo al almacén`, `Wenceslao y Isabel (Granjeros) /
Yendo a la taberna` — todos con **`ANDADO` 0,0-0,1** ✗ y **`alcanza=SI`** ✓, es decir **plantados con ruta viva** ✓.

**Y dos matices que me toca corregir de mi propia ronda anterior** ✗:
1. **La variante 1 de I200 era un fallo de lógica** ✓ (sin dato → no pide camino → no anda → sigue sin haber dato): se
   retira con razón ✓, y eso no lo cambia nada de lo de abajo.
2. **Pero la variante 2 la juzgué con datos flojos** ✗: las corridas **116-117** eran de **3 minutos** ✓, y ahora la 120
   —con el estado **retirado**— da `Entrenando en la barraca` = **34** y `Patrullando` = **0** ✗, **igual de bajo** que
   con I200 puesta ✗. O sea: **el «guardián» que usé (la actividad de guardias) es ruido** ✓ (varía de 938 a 0 entre
   corridas), así que la frase «caminan más y llegan menos» ✗ **no está bien medida** ✓. Lo correcto para el futuro es
   medirlo con el **`destinos`/tick del aviso** ✓ (preciso: 2,8/tick ✗) y con **avisos por corrida** ✓, no con esa
   cuenta.

**Estado del último residuo**: `ANDADO ≈ 0` **con la ruta viva** ✗, 1-3 avisos por corrida de 20 minutos ✓, rotando
entre etiquetas (`Yendo al almacén`, `Yendo a la taberna`, `Yendo a entrenar`). **Diagnóstico**: las ~2,8 peticiones de
camino por tick reinician al caminante ✓. **Arreglo correcto, escrito y no aplicado**: preguntar por
`villager.getNavigation().isInProgress()` ✓, medido con `destinos`/tick y avisos por corrida ✓.

### 8.bis.0.septendecies · **Ronda 43: la métrica precisa tumba mi diagnóstico (I201 retirada) — y el residuo queda como problema ABIERTO**

**Lo que medí, con la métrica correcta** (`destinos` por tick, que sale en el propio aviso):

| corrida | `destinos` / ticks | lectura |
|---|---|---|
| 110 (I199) | **548 en 195** ✗ | ≈2,8/tick — **el caso que me llevó a intentar el arreglo** |
| 120 (retirado) | **1 en 654** ✓ · **11 en 872** ✓ · **9 en 880** ✓ | ≈0,01/tick: **el goal NO re-pide el camino cada tick** |
| 125 (con I201) | **1 en 1408** ✓ · 810 en 350 ✗ · 892 en 350 ✗ | igual de mezclado, sin mejora |

**Conclusión honesta**: mi premisa —«el goal pide el camino ~2,8 veces por tick y eso lo deja plantado»— **era falsa en
general** ✗. El `548 en 195` era **un caso suelto**, y sobre él construí **dos** arreglos (I200 y I201), los dos
retirados ✓. La regla del proyecto se aplica: **lo que no arregla, se quita y se dice** ✓.

**Lo que sí queda medido y sirve**:
1. El residuo son aldeanos **plantados** ✗: `ANDADO 0.0` con **`alcanza=SI`** (ruta viva) o con **`alcanza=NO`** (sin
   ruta) — **dos subcasos**, y los tres avisos de la 125 son del segundo (`Mauricio / A por leña al almacén` y
   `Zacarias / Cuidando el ganado` ×2, todos `alcanza=NO`).
2. **No** es por re-pedir el camino cada tick ✗ (medido arriba).
3. **No** es falta de red: el empujón del latido (I198) da **873-1460** por corrida ✓ y el rescate funciona ✓.
4. **La causa sigue ABIERTA** ✗. Lo honesto es **medirla**, no parchearla: hace falta una traza nueva que diga, en el
   momento del atasco, **si la navegación tiene camino vivo y no lo anda**, **si hay bichos o aldeanos apretados
   alrededor** y **a dónde apunta el cerebro** — las tres cosas que el aviso no distingue hoy.

**Y una nota de método que me ha costado tres intentos**: la cuenta de líneas de actividad de los guardias
(`Entrenando`/`Patrullando`) **es ruido** ✗ (varía de 938 a 0 entre corridas: la 124 dio 0 en las tres y la 125 dio 41
de entrenamiento ✓, con el **mismo** código). Para juzgar hay que usar **`destinos`/tick** y **avisos por corrida** ✓.

### 8.bis.0.duodevicies · **Ronda 44: LA CAUSA, MEDIDA (y mi diagnóstico anterior, tumbado)**

**La ficha del plantado (I202)** escribe, cuando un aldeano lleva 2 s parado con faena del mod en marcha, las tres
cosas que el aviso no distinguía: si la **navegación** tiene camino y no lo anda, si tiene **gente apretada** alrededor
y **a dónde apunta su cerebro**. Primeras **454 fichas** (corrida 126, en vivo):

| caso | fichas | lectura |
|---|---|---|
| **`nav = sin ruta`** | **454 de 454** ✗✗ | **no hay camino**, no es «camino que no se anda» |
| `cerebro = "-"` (sin destino) | 113 | están **trabajando en el sitio** ✓ — eso es legítimo y **no** da aviso |
| con aldeanos apretados | 28 | apiñamiento, minoritario |
| con bichos cerca | **0** | descartado |

Y los destinos del cerebro en las fichas con camino imposible: `606,…` (49), **`614,…` (37 = el almacén)**, `573,…`
(23), `533/535,…` (21/20 = **las parcelas**) — todos con **`nav=sin ruta`** ✗.

**Conclusión medida**: el residuo son aldeanos cuyo **destino no se puede alcanzar desde donde están** ✗ (el almacén y
las parcelas en la muestra). **No** es un camino que no andan ✗.

**Y esto tumba mi diagnóstico anterior** ✗, con el dato delante: `I200` e `I201` intentaban **no volver a pedir** un
camino que **no existía** ✗, e `I198` (el empujón del latido) **para** la navegación, que es lo contrario de lo que
hace falta ✗. Tres intentos apuntando al mecanismo equivocado ✓, y los tres retirados o inútiles ✓ — se dice.

**Y explica la contradicción que me tuvo dando vueltas** ✓: el aviso imprimía `alcanza=SI` ✗ porque ese campo lo
rellena una **sonda nueva** (que a veces sí encuentra ruta) mientras la **navegación viva** del aldeano está **sin
ruta** ✗✓. Dos preguntas distintas con la misma palabra ✓.

**El siguiente paso, ya con nombre**: **por qué no hay ruta hasta el almacén (`614, 78, 587`) y hasta las parcelas**
desde donde están esos aldeanos — con el mismo método: medir (¿la puerta? ¿el mostrador? ¿el camino cortado?) antes de
tocar nada ✓.

### 8.bis.0.undevicies · **Ronda 45: I203 medido — la causa era «ruta perdida que nadie vuelve a pedir»**

**El arreglo** (I203) y sus números, todos con **prueba propia** (la línea `recuperado el camino`, que yo controlo ✓):

| corrida | avisos | caminos recuperados | destinos |
|---|---|---|---|
| 129 (rápida) | **0** ✓ | **95** ✓ | `535, 78, 600` · `572, 78, 571` |
| 130 (rápida) | **0** ✓ | **113** ✓ | `603, 78, 589` · `584, 82, 540` |
| **131 (larga)** | **0** ✓ | **501** ✓ | `533, 78, 580` · `535, 78, 600` · **`614, 78, 587` (el almacén)** ✓ |
| **132 (larga)** | **0** ✓ | (con la misma red) | — |

**Y los destinos de las recuperaciones son exactamente los pares que la ficha del plantado había medido** ✓✓: los
**puestos de los granjeros** (`533, 78, 580` · `535, 78, 600`) y **el almacén** (`614, 78, 587`) ✓. Es decir: la causa
del último residuo era **una ruta perdida que nadie volvía a pedir** ✗, y con I203 se recupera **501 veces por corrida**
✓ sin un solo aviso ✓.

**Cerrando el círculo de esta sesión**: los avisos de rendición de la aldea pasaron de **62-238** ✗ (lotes viejos) a
**0 · 1 · 0 · 0** ✓ (lote de cierre de I197) y ahora a **0 · 0** ✓ en las largas con I203. **En marcha**, el lote de
cierre definitivo **133 · 134 · 135 · 136** ✓ (4 × 20 min) sobre este build, que es la formalidad que pide el objetivo ✓.
