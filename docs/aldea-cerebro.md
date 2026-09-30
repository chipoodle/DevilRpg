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
