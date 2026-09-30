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

### M3 · La AUTORIDAD DE PUERTAS — `VillageDoors`
- Antes de pedir una ruta que **cruza una puerta nuestra** (los portones de la aldea, los del corral y el gallinero, las
  compuertas de la huerta), **la aldea abre la suya**: se abre el portón, se espera, se pide la ruta y se cierra cuando
  pasa (ya existe el `VillagerGateGoal`, con su lado y su cierre; aquí pasa a ser **el que abre ANTES**, no el que abre
  cuando ya está pegado).
- Efecto: la clase **D** muere: el que está dentro de un recinto **siempre** tiene ruta hacia fuera, porque la puerta se
  abre para él, no al revés.
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

| fase | qué entra | criterio de la medida |
|---|---|---|
| **1** | **M2** (autoridad de recados) y su uso en el ganadero y la taberna | `Bajando lo del corral`, `Recogiendo lo suyo` y `Yendo a la taberna` a **0**, sin subir ninguna otra etiqueta |
| **2** | **M1** (despachador) con dos tareas dentro (ganadero y taberna); el resto sigue como hoy | media de rendiciones **por debajo** de la de hoy (medida ya: **10,25**) |
| **3** | **M3** (puertas): abrir antes de pedir la ruta | `Entrando a la huerta` y los recados del corral a **0** |
| **4** | migrar las tareas restantes (granjero, recolector, leñador, herrero, minero, guardia) al despachador | **0 avisos de aldeano** en la ventana; el aviso solo puede venir del sistema |
| **5** | los pollos y el ganadero con el portón (tu idea): apertura al paso, cierre detrás | **pollos fuera del recinto: 0** al final de la corrida |

**Nada de esto entra sin su media.** Y todo lo que no mejore la media, se retira y se apunta (como se ha hecho hoy con
la Opción A: **17,5** con el rumbo sostenido contra **10,25** sin él).

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

