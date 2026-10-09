# EL CEREBRO DE LA ALDEA — plan de arquitectura (29-sep-2026)

> Lo pidió el jugador, harto de parches: *«si es necesario hay que cambiar toda la arquitectura del sistema de
> aldeanos… reescribir el cerebro de los aldeanos con módulos propios… puedes usar los de vanilla para basarte en el
> funcionamiento y la estructura, pero que sean propios, con un diseño que permita resolver todos nuestros problemas de
> que no cumplen con su tarea porque se atoran»*.

## 1. Por qué los parches no han bastado (lo medido)

Hoy el aldeano de la aldea tiene **tres jefes a la vez**:

1. **Los goals del mod** (`VillagerFarmGoal`, `VillagerAnimalFarmGoal`, `VillagerTavernGoal`…), cada uno con su propia
   idea de dónde hay que estar, su propio contador de atasco y su propia forma de rendirse.
2. **El cerebro del juego** (`Brain`), que con sus paseos y su «anda hacia donde miras» **escribe el mismo
   `WALK_TARGET`** y le roba el rumbo al goal. **Medido**: en **11.039 de 57.663** recados (**19 %**).
3. **Los goals vanilla del aldeano** (cosechar, ir a su puesto, dormir), que también tienen MOVE.

Y las **clases de fallo** que hemos ido encontrando, todas de esa mezcla:

| clase | ejemplo medido | por qué pasa |
|---|---|---|
| **A · destino que no se pisa** | la mesa de la taberna (`dark_oak_fence` + `oak_pressure_plate`), el huevo dentro de la valla | el goal persigue la **celda del objeto/mueble**, no una casilla de pie, y el planificador devuelve `ruta=1 nodos` |
| **B · el cerebro roba el rumbo** | taberna: `cerebro=566,64,566` (la plaza) o `cerebro=-` | el paseo escribe después y gana |
| **C · recado imposible que se reintenta** | ganadero: 2 → 5 → 7 rendiciones del mismo recado | se aparca el punto, pero **se vuelve a elegir** (o su destino es fijo y no se comprueba) |
| **D · encerrado sin ruta** | ganadero en `613,62,574` (dentro del corral) con `ruta=1 nodos` al almacén | el portón solo se abre cuando ya está **pegado** (2,6 bloques); si no hay ruta **hasta el portón**, no llega a pedirlo: círculo cerrado |
| **E · dos goals peleando** | `goals=[VillagerFarmGoal VillagerGateGoal]` con el aldeano parado | los dos tienen MOVE y se pisan |

> ✅ **CERRADO (comprobado el 9-oct-2026)**: la clase **A** la cerró **I154** —`VillageManager.caminarHacia` (L5605-5627) manda al aldeano a la casilla de pie, en un solo sitio para todos los goals—, con **I152** (la casilla del entreno) e **I153** (la del obrero); la **B**, **I171** (si al aldeano se le pierde el rumbo, se le devuelve) e **I206** (el embudo `ponerRumbo` ya no reescribe el destino en cada tick); la **C**, **I97** (`VillagerAnimalFarmGoal` L258-261: un destino fijo aparcado no se vuelve a elegir) sobre el aparcado de **I33**; y la **D**, **I155** rehecha como **I169** (`VillageErrands.abrirLoQueCierreElPaso`, llamado desde `VillageManager.caminarHacia` L5620) más **I197** (el corral pasó a tener **dos** portones). Se deja la tabla porque cuenta el camino, pero no es trabajo pendiente.
> ⚠️ **CORREGIDO el 9-oct-2026**: la clase **E** decía «los dos tienen MOVE y se pisan» → `VillagerGateGoal` corre **sin ningún flag** (`VillagerGateGoal` L175: `EnumSet.noneOf(Goal.Flag.class)`), así que podía correr a la vez que el oficio y **borrarle el camino**; y los `PARAR` eran en su mayoría **llegadas normales**, no peleas (I125 e I205), porque lo que se arregló fue el embudo (I206).

**Y el fallo de método que lo ha alargado todo**: se ha juzgado cada intento con **una** corrida, cuando la ventana varía
**7 · 15 · 21 · 27** entre corridas del mismo código. **Toda medida de aquí en adelante es la media de 4 corridas.**

## 2. La arquitectura: quedarse con el mando

La regla es una: **para cada aldeano, UN solo módulo decide qué hace ahora, dónde se pone y por dónde va; los demás
proponen.** Cuatro piezas, todas nuestras:

### M1 · El DESPACHADOR (un solo jefe) — `VillageDispatcher`
- Un goal propio, **primero en la lista y con MOVE** (`goalSelector` prioridad 0), que en cada tick:
  1. pregunta a las **tareas** registradas cuál quiere correr (la de más prioridad que tenga algo que hacer);
  2. le pide a M2 una **casilla válida y alcanzable**;
  3. escribe **él** el `WALK_TARGET` (nadie más lo escribe mientras hay tarea) y **lo defiende**: si el cerebro lo
     pisa, lo vuelve a poner.
- Efecto: las clases **B** y **E** mueren por construcción (no hay dos escritores ni dos goals con MOVE).

> ⚠️ **CORREGIDO el 9-oct-2026**: este bloque decía «**con MOVE**» y daba por hecho ese efecto → el goal que se escribió corre **sin flags** (`VillageDispatcherGoal` L57: `// SIN setFlags`, porque con `MOVE` habría bloqueado a los oficios, `VillageDispatcherGoal` L31-34) y **hoy no está cableado**: nadie lo instancia (`VillageDispatcherGoal` L14-19) y `VillageManager.asegurarElDespachador` (L5932-5939) no lo llama desde ningún sitio.
> ⚠️ **CORREGIDO el 9-oct-2026**: «las clases **B** y **E** mueren por construcción» **no llegó a pasar**: M1 se midió y se **retiró** (**172,5** contra **36** de referencia; `VillageManager` L2990-2998 y §5.2 de este documento), y la pelea del rumbo se atajó en el embudo `VillageManager.ponerRumbo` (I206) más el relleno del rumbo perdido (I171).
- **Lo vanilla se queda**: el movimiento en sí (el `MoveToTargetSink` del juego sigue caminando), dormir, huir,
  aparearse y las animaciones. Solo se le quita la decisión de **a dónde va a trabajar**.

### M2 · La AUTORIDAD DE RECADOS — `VillageErrands`
- `casillaPosible(faena)` → la celda de pie más cercana, **o `null`** (nunca «la faena» cuando no se pisa: ese
  `return faena` es el que hacía empujar el mueble).
- `elRecadoEsPosible(recado)` → casilla posible **y** ruta que **alcance** (`createPath` + `canReach`).
- **Contrato**: una tarea **no empieza** un recado que no pase el contrato; si no hay ninguno posible, la tarea se
  declara vacía y el aldeano hace otra cosa (nunca persigue, nunca ronda).
- Efecto: la clase **A** muere (no se persiguen celdas que no se pisan) y la **C** también (no se reintenta lo imposible:
  el aparcado se respeta **en el único sitio donde se eligen recados**).

> ⚠️ **CORREGIDO el 9-oct-2026**: este efecto **no lo dio M2** (su cableado se probó y se **retiró**, ver §3): la clase **A** la cerró **I154** en el punto único `VillageManager.caminarHacia` (L5605-5627) y la **C**, **I97**; y hoy `VillageErrands.elRecadoEsPosible` (`VillageErrands` L76) **no lo llama nadie** (solo se usa `VillageErrands.casillaPosible`, `VillagerAnimalFarmGoal` L200).

### M3 · La AUTORIDAD DE PUERTAS — `VillageDoors`
- Antes de pedir una ruta que **cruza una puerta nuestra** (los portones de la aldea, los del corral y el gallinero, las
  compuertas de la huerta), **la aldea abre la suya**: se abre el portón, se espera, se pide la ruta y se cierra cuando
  pasa (ya existe el `VillagerGateGoal`, con su lado y su cierre; aquí pasa a ser **el que abre ANTES**, no el que abre
  cuando ya está pegado).

> ⚠️ **CORREGIDO el 9-oct-2026**: decía que esta pieza es el módulo `VillageDoors` → **esa clase no existe** en `src`: quien hace el trabajo es `VillageErrands.abrirLoQueCierreElPaso` (`VillageErrands` L130), llamado en el punto único por el que caminan todos los goals (`VillageManager.caminarHacia` L5620, **I169**), y `VillageErrands.abrirLaPuertaSiHaceFalta` (L94) **no lo llama nadie** (así cierra el acta la nota de **I155**).

- Efecto: la clase **D** muere: el que está dentro de un recinto **siempre** tiene ruta hacia fuera, porque la puerta se
  abre para él, no al revés.

> ✅ **CERRADO (comprobado el 9-oct-2026)**: la clase **D** del **corral y de las parcelas** sí murió: **I155** rehecha como **I169** (la aldea abre la puerta que de verdad separa al aldeano de su recado, `VillageManager.caminarHacia` L5620) e **I197** (el corral pasó a tener **dos** portones). Se deja la nota porque cuenta el camino, pero no es trabajo pendiente.
> ⚠️ **SIGUE ABIERTO (comprobado el 9-oct-2026)**: el **cruce de los portones del muro** (los cuatro cardinales) sigue **sin medir**: el arreglo de **I222** está puesto, pero **cinco corridas no aislaron el cruce** — está en `docs/CONTINUAR.md` §2.0.

- Y es también la pieza que resuelve **los pollos**: el portón se abre **solo** para el aldeano que pasa y se cierra
  detrás; los pollos no saben abrirlo, así que se quedan dentro **por construcción** (sin necesidad de tareas raras).

### M4 · El SUPERVISOR (nada de rondar)
- Por cada recado: presupuesto de **tiempo** y **avance por la ruta** (`avanzaPorLaRuta`, I139). Si no avanza:
  1. **reintento** (una vez): se recalcula el punto de ahora;
  2. **cambio de recado**: se pide a la tarea otro candidato;
  3. **cambio de tarea**: el despachador pasa a la siguiente;
  4. **aparcado**: el punto se marca (I33) y **no se vuelve a elegir** en unos minutos.
- En ningún caso «rendirse y quedarse»: **siempre hay un paso siguiente**. Y el aviso de rendición deja de ser una
  métrica del aldeano (será un aviso del **sistema**, no de un aldeano concreto).

### M5 · Las TAREAS (lo que ya está escrito, con otro contrato)
- Cada oficio es una **tarea** que responde a tres preguntas: *¿quiero correr?*, *¿cuál es el siguiente recado
  (candidatos, no una celda)?*, *¿ya está hecho?*.
- **Se reaprovecha entero** el trabajo de estos días: las rutas por tramos (I140), la casilla de pie (I114/I131), el
  avance por la ruta (I139), el aparcado (I33), el portón (I44/I96), la huerta (I130), la mina (I141-I144).

## 3. Fases (cada una, medida con **medias de 4 corridas**)

> **FASE 1 · PROBADA Y RETIRADA (29-sep-2026).** M2 (autoridad de recados) se cableó en el ganadero, la taberna y el
> recojo, y M3 (abrir el portón antes de pedir la ruta) en el ganadero. **4 corridas contra 4 de referencia**, contando
> **rendiciones por etiqueta en toda la corrida** (más muestra que la ventana de 2.000-12.000):
>
> | etiqueta | referencia | con M2+M3 |
> |---|---|---|
> | `Recogiendo el corral` | 3 | **0** |
> | `Recogiendo lo suyo` | 28 | 27 |
> | `Bajando lo del corral` | 24 | **28** |
> | `Yendo a la taberna` | 14 | **32** |
> | `Sembrando` | 21 | 13 |
> | **total por corrida** | 13 · 72 · 19 · 40 (**36**) | 24 · 42 · 49 · 42 (**39,25**) |
>
> **NO PAGA**: el ganadero no mejora (`Bajando lo del corral` incluso sube) y la taberna **empeora** —`casillaPosible`
> es más estricto que el `casillaDePieCercaDe` viejo (que caía a la propia faena) y **aparca mesas que sí se podían
> atender**, con lo que el aldeano salta de mesa en mesa rindiéndose en cada una. **Cableado RETIRADO**; el módulo
> (`VillageErrands`) y este plan se quedan, porque son la base de las fases siguientes.
>
> **Y DOS LECCIONES DE MEDIDA, que valen para todo lo que venga**:
> 1. **El total no distingue**: 36 contra 39,25 con dispersión 13-72 **no es una diferencia concluyente** (harían falta
>    ~15 corridas por configuración). Lo que sí informa es **la etiqueta concreta** que ataca el arreglo (I135).
> 2. **Y hay que mirar la corrida entera**, no una ventana corta: la mayoría de las rendiciones de estas corridas caen
>    **después** del tick 12.000 (la ventana de 2.000-12.000 veía 3 de las 14 de la taberna).
>
> Próximo intento, por tanto: **etiqueta concreta + corrida entera + más de 4 corridas**, y **M1 (el despachador)**
> antes de volver a tocar M2, porque el problema de la taberna no es «no hay casilla» sino «dos jefes».

> ✅ **CERRADO (comprobado el 9-oct-2026)**: el «próximo intento» se hizo: **M1** se implementó, se midió (**172,5** contra **36** de referencia) y se **retiró** —está contado en §5.2 de este documento y en `VillageManager` L2990-2998—, y su cableado quedó fuera (`VillageManager.asegurarElDespachador` L5932-5939 no lo llama nadie). Se deja la nota porque cuenta el camino, pero no es trabajo pendiente.

| fase | qué entra | criterio de la medida |
|---|---|---|
| **1** | **M2** (autoridad de recados) y su uso en el ganadero y la taberna | `Bajando lo del corral`, `Recogiendo lo suyo` y `Yendo a la taberna` a **0**, sin subir ninguna otra etiqueta |
| **2** | **M1** (despachador) con dos tareas dentro (ganadero y taberna); el resto sigue como hoy | media de rendiciones **por debajo** de la de hoy (medida ya: **10,25**) |
| **3** | **M3** (puertas): abrir antes de pedir la ruta | `Entrando a la huerta` y los recados del corral a **0** |
| **4** | migrar las tareas restantes (granjero, recolector, leñador, herrero, minero, guardia) al despachador | **0 avisos de aldeano** en la ventana; el aviso solo puede venir del sistema |
| **5** | los pollos y el ganadero con el portón (tu idea): apertura al paso, cierre detrás | **pollos fuera del recinto: 0** al final de la corrida |

> ✅ **CERRADO (comprobado el 9-oct-2026)**: fases **1** y **2** probadas, medidas y **retiradas** (fase 1: la nota de arriba; fase 2: **I150** y `VillageManager.caminarHacia` L5600-5602, con el módulo sin cablear); fase **3** cumplida (`Entrando a la huerta` **0 y 0** y los avisos del corral a **0-1**, **I155**/I157/**I197**); y fase **4** **no procede** (migrar las tareas al despachador dejó de tener sentido al retirarse M1). Se deja la tabla porque cuenta el camino, pero no es trabajo pendiente. **La fase 5 no tiene medida en el acta** (ver la nota del portón y los pollos, arriba).

**Nada de esto entra sin su media.** Y todo lo que no mejore la media, se retira y se apunta (como se ha hecho hoy con
la Opción A: **17,5** con el rumbo sostenido contra **10,25** sin él).

## 3.bis · EL NIVEL 3, HECHO Y MEDIDO (29-sep-2026)

Lo que M1 enseñó (que los goals **no sirven para un aldeano que obedece**) se arregló quitándoles los fallos **de
clase**, no de uno en uno. Tres reglas **centrales** (I152–I155):

| regla | dónde | qué clase de fallo mata |
|---|---|---|
| **A · A UNA CELDA QUE NO SE PISA NO SE CAMINA** | `VillageManager.caminarHacia` (**un solo sitio**, para los ~20 goals) | el destino de la faena no es casilla de pie: **cultivos** (`destino=farmland encima=wheat`), el soporte del clérigo (`brewing_stand`), la mesa de la taberna (`dark_oak_fence encima=oak_pressure_plate`), el plantón (`oak_sapling`) → `ruta=1 nodos alcanza=NO` y el aldeano empujando el obstáculo |
| **D · EL DESATASCO POR ENCAJAMIENTO, PARA TODOS** | el latido del pueblo (una comprobación, todos los aldeanos) | el aldeano **metido dentro** de un bloque: `pies=dark_oak_fence cabeza=oak_pressure_plate` (una mesa), `pies=chest`, `pies=oak_stairs`, `suelo=furnace`, con la ruta viva `alcanza=SI` |
| **C · LA CASILLA DEL ENTRENAMIENTO, CON CONTRATO Y RUTA** | `VillagerGuardGoal` (I152) | la casilla estaba **2 bloques por encima** de el nivel del pueblo, aislada: ruta de un nodo. Ahora: **a el nivel del pueblo** + **ruta validada** + **si ninguna vale, no entrena** (y el «no» **caduca a los 5 s**: cacheado para siempre dejaba al guardia sin entrenar **jamás**, medido) |
| **O · EL PORTÓN DEL CORRAL, ABIERTO POR LA ALDEA** | `VillagerErrands.abrirLaPuertaSiHaceFalta` (I155) | el ganadero **encerrado** con el portón cerrado: el planificador no cruza una valla cerrada y el portón solo se abría con el aldeano ya pegado a él → **círculo cerrado** |

> ⚠️ **CORREGIDO el 9-oct-2026**: la regla O citaba `VillagerErrands.abrirLaPuertaSiHaceFalta` → esa función **ya no la llama nadie**; hoy lo hace `VillageErrands.abrirLoQueCierreElPaso` (`VillageErrands` L130), llamado desde `VillageManager.caminarHacia` L5620 (**I169**), que solo abre la puerta que **de verdad** separa al aldeano de su recado (el cierre de I155 en el acta lo dice así).

**MEDIDO, 4 corridas contra 4 del mismo modo** (modo aldea del arnés, la corrida **entera**):

| | referencia (51-54) | con I154 (59-62) |
|---|---|---|
| rendiciones | 50 · 20 · 48 · 48 → **41,5** | 25 · 29 · 31 · 36 → **30,25** |
| `Bajando lo del corral` | 35 | **16** |
| `Recogiendo lo suyo` | 40 | **18** |
| `Yendo a la taberna` | 35 | 29 |
| `Yendo a la cocina` | 14 | 12 |
| **y el pueblo TRABAJA** | pescador 9,5 · granja 797 · herrería 74,5 | pescador 9,5 · granja 759 · herrería 75 |

Y los jueces propios de cada pieza: **guardias** `Yendo a entrenar` **0** y `entrenado` subiendo de 0 a **914-1.800**
(sin entrenar en una corrida antes del arreglo del rechazo); **obrero** `Repuso*` **0** en las cuatro.

## 4. Lo que NO se toca

- El **movimiento** lo sigue haciendo el juego (`PathNavigation` + `MoveToTargetSink`): no se reescribe el A\*, que no
  es el problema. La clase de fallo nunca ha sido «no encuentra el camino», sino «le mandan a un sitio imposible, o dos
  jefes le mandan a sitios distintos, o nadie abre la puerta».
- Dormir, huir, el pánico, la cría y las animaciones: vanilla.

## 5. Si M1 tampoco paga: la escalada (lo avisó el jugador)

*«Si aun así sigue evaluando pobremente vamos a tener que cambiar de arquitectura y hacer todo el comportamiento
personalizado de los aldeanos para evitar fallos y bloqueos.»* Queda escrito el orden, para no improvisarlo:

1. **Nivel 1 (hecho y retirado)**: M2 (autoridad de recados) y M3 (puertas). Medido: **no paga** —
   `Bajando lo del corral` 24 → 28 y `Yendo a la taberna` 14 → 32—, así que el cableado se quitó.
2. **Nivel 2 (implementado, medido y RETIRADO)**: **M1**, el despachador. `VillageDispatcherGoal` corría **sin flags**
   a prioridad 0 —con `MOVE` habría bloqueado a los oficios, que también lo usan— y en cada tick **escribía y
   defendía** el rumbo del recado, con **supervisión** (abandonar el recado si no consume nodos de su ruta).
   **MEDIDO, 4 corridas contra 4** (rendiciones en la **corrida entera**):
   **110 · 238 · 150 · 192 (media 172,5)** contra **13 · 72 · 19 · 40 (media 36)** → **4,8 veces peor**, y con
   separación limpia (todas las de M1 ≥ 110, todas las de referencia ≤ 72), o sea **concluyente**.
   **El mecanismo SÍ funcionó**: el instrumento dice que el cerebro le había pisado el rumbo en **33.000-45.000 de
   50.000-65.000 recados por corrida** (y hasta 117 abandonos por la supervisión).
   **Y por qué hundió el pueblo**: los goals **contaban con que el paseo se llevara al aldeano**. Al llegar de verdad a
   sus destinos, quedaron **al descubierto todos los recados que no son alcanzables**:
   - **guardias**: `Yendo a entrenar`
   - **obrero**: `Repuso un bloque`, `Repuso losa`, `Repuso piedra labrada`
   - **cocinero**: `A por leña al almacén`, `Yendo a la cocina`
   - **granjeros**: `Sembrando`, `Labro la huerta`, `Guardo lo suyo`, `Trajo del almacén a la despensa`, `Buscando recambios`
   - **leñador**: `Yendo a la arboleda`
   - **recojo**: `Recogiendo lo suyo`
   La supervisión **no bastó** (65-117 abandonos de 50.000+ recados) porque **el goal vuelve a pedir el mismo recado en
   el tick siguiente**: el abandono se deshace solo. Cableado **retirado**; el módulo y la API del recado se quedan
   como base del nivel 3.
   > **Y ESTA ES LA LECCIÓN QUE ABRE EL NIVEL 3**: no se puede arreglar «que llegue» sin arreglar **«que elija bien a
   > dónde»**. La lista de arriba es, por primera vez, la lista **real** de lo que está roto —la tenía escondida el
   > robo del rumbo—, con nombres y oficios.

3. **Nivel 3 — comportamiento 100 % propio** (solo si el nivel 2 no baja las etiquetas):
   - **entidad propia** para los aldeanos de la aldea (`VillageVillager extends Villager`) con **cerebro propio**: sin
     los `Behavior` de paseo (`SetWalkTargetFromLookTarget`) ni los de trabajo vanilla que compiten, y con **una sola**
     actividad: «cumplir mi tarea», gobernada por las tareas del mod;
   - **navegación con evaluador propio** (`PathNavigation` + `NodeEvaluator` propios): nuestros portones siempre
     franqueables, la llegada sin la tolerancia de 1 bloque, y **nunca** una ruta que termine en una celda que no se
     pisa (el contrato de M2, **dentro del buscador** en vez de en cada goal);
   - **sin rondar por diseño**: el estado del aldeano es siempre «voy a X» o «trabajo en X»; **no existe** «no sé qué
     hacer», que es lo que produce el 19 % de robos de rumbo y los avisos sueltos;
   - **coste y riesgo**: altos (hay que conservar lo que vanilla garantiza: ahogarse, caer, dormir, huir, criar y
     comerciar), pero es la única forma de que un fallo sea **imposible por construcción** en vez de improbable.
   - **Condición para entrar**: solo si el nivel 2 no baja las etiquetas, y con el juez de siempre: **etiqueta concreta
     + corrida entera + varias corridas**.

> ⚠️ **CORREGIDO el 9-oct-2026**: esta «entidad propia con cerebro y navegador propios» (`VillageVillager`) **no se escribió** —no hay tal clase en `src/main/java/com/chipoodle/devilrpg`— y la casa **retiró la idea de reescribir la lógica del aldeano** porque, medida, da **el mismo baile**: ver `docs/CONTINUAR.md` §1.6 y **I206** (nota 1), que dice que el conflicto era **entre goals del mod**, no con vanilla. Lo que sí está hecho y medido es el **patrón** de candidatas + contrato + validación de **I152–I155** (ver §3.bis de este documento).

