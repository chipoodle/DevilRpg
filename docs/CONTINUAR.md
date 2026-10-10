# CONTINUAR AQUÍ — acuerdos, convenciones y pendientes (leer lo PRIMERO)

Rama: **`migration-neoforge-1.21.1`** (estable, compilada, con `lint --strict` verde y **subida a GitHub**).
Ultima actualizacion: 9-oct-2026, al cerrar los tres pendientes (I235 el nado, I236 el puente y la escalera, I237 la
milicia) y arreglar la escalera que cavaba en vez de apilar (I238).

---

## 0 · QUE ESCRIBIRLE AL AGENTE EN EL CHAT PARA QUE SIGA

**Frase corta (recomendada):**

> Lee `docs/CONTINUAR.md` y sigue por lo pendiente; mide con el arnes y no te fies de nada que no este medido.

**Variante, si quieres que empiece por algo concreto:**

> Lee `docs/CONTINUAR.md` y sigue por el pendiente 1 (la regla del muro perimetral).

Con eso basta: el documento tiene los pendientes **con clase y numero de linea**, la forma de medir y las reglas de la
casa, asi que no hace falta repetir contexto ni volver a explicar lo ya andado.

---

## 1 · ACUERDOS Y CONVENCIONES (la forma de trabajar de esta casa)

**ESCRIBIR PARA QUE SE ENTIENDA, NO PARA PARECER DEL GREMIO** (8-oct-2026). Lo pidio el jugador, y con razon: yo
escribia *«la traza **canta** los dos numeros»* queriendo decir *«la traza **los escribe en el registro**»*. Jerga mia,
ni inglesa ni tecnica, y si hay que explicarla **esta mal escrita**. Se barrieron **55 apariciones** en 14 ficheros
(docs, herramientas y comentarios del codigo, incluido un metodo que se llamaba `cantarLaMedidaDelDespachador` y ahora
se llama `escribirLaMedidaDelDespachador`). **Regla**: si una palabra necesita traduccion para el jugador, no se usa —
se escribe lo que hace: *escribe*, *dice*, *imprime*, *deja en el registro*, *avisa*. Vale para los documentos, para los
comentarios del codigo y para los mensajes del registro.

**Y LA SEGUNDA PALABRA QUE CAYO FUE «BICHO»** (9-oct-2026, y la cazo el jugador con una pregunta: *«¿que quieres decir
con bichos? ¿te refieres a mobs?»*). Con razon: **no la dice el jugador** (buscado en las frases suyas entre comillas) y
**no estaba en el glosario** — era palabra mia. Se barrieron **361 apariciones en 25 ficheros** y se cambio por
**`monstruo`**, que es lo que el mod ya dice en el texto que ve el jugador (`[Horda] los monstruos marchan contra una
aldea cercana`) y lo que son esas entidades (`net.minecraft.world.entity.monster.Monster`). Incluye los nombres:
`BICHO_DENTRO` → `MONSTRUO_DENTRO`, `BICHO_EN` → `MONSTRUO_EN`, `mantenerBichoDentro` → `mantenerMonstruoDentro`,
`bichosDentro` → `monstruosDentro`, `bichosCerca` → `monstruosCerca`, `cotaBichos` → `cotaMonstruos`. **Aviso para el
futuro**: el termino «mob» se puede usar entre nosotros, pero **en el texto del juego se dice `monstruo`** (o el nombre
del que sea: zombi, esqueleto…).

1. **Medir antes de tocar.** Ningun cambio entra sin su medida. Lo que no mejora la media, **se retira y se dice**.
2. **No suponer nunca.** Cada hipotesis falsa se deja **escrita como falsa** en el acta (hay varias mias, a proposito).
3. **Antes de retirar una idea, comprobar si falla la IDEA o la IMPLEMENTACION** (me paso con I200/I201: la idea era
   buena, la implementacion no).
4. **Nunca encadenar `compilar` y `commitear` sin mirar el resultado**: se commiteo un build roto una vez (se enmendo).
   Compilar y lint van en pasos separados y el commit **solo corre si estan verdes**.
5. **Un goal sin la bandera `MOVE` no puede tocar el camino de otro goal** (es la causa del "baile" de los aldeanos).
6. **NO reescribir toda la logica del aldeano**: se probo y se retiro con medidas (`docs/aldea-cerebro.md` §3), y
   produce **el mismo baile**. Tampoco hay que copiar los goals de vanilla: el conflicto era **entre goals del mod**.
7. **El centro de una aldea es EL DEL OBJETIVO**, nunca el punto medio de lo construido (eso la desplazaba al este y la
   duplicaba). Y **una reparacion NO puede mover una aldea**.
8. **El arnes NUNCA compilado en la partida del jugador**: candado por variable de entorno `DEVILRPG_ARNES=1` + perro
   guardian al arrancar que avisa si `GuardHarness` esta en el classpath.
9. **Nunca dos Gradle ni dos arneses a la vez**; y **no se tocan ficheros Java mientras un arnes mide**.
10. **El juego se puede matar** si tiene el `.jar` bloqueado (permiso permanente del jugador) y se sigue compilando.
11. **Metricas de verdad** (no cuentos de lineas): `PARAR`, `[Rumbo]`, `[Huerta]`, `[Planta]`, `no consigue llegar`,
    `estaba METIDO`, `OLEADA`, `Attributes Scaled`. Ojo: **`PARAR` va limitado a una linea cada 2 s por aldeano**, asi
    que **para el baile fino se lee su numero** (`paradas=N (con faena=M)`, **I246**: en la corrida 182, 91 lineas y
    1790 paradas) ✓;
12. **Documentar SIEMPRE** en `docs/aldea-invariantes.md`: que se ha cambiado, **con clase y numero de linea**, **la
    medida** que lo justifica, y **las hipotesis falsas** que se descartaron. Cada cambio se **compila**, pasa
    `python tools/lint_aldea.py --strict`, se **commitea** con titulo en MAYUSCULAS y **se sube** a GitHub.
13. **Titulos de commit**: prefijo `I###` si es una invariante numerada, o titulo descriptivo; dentro, **las frases
    literales del jugador** entre comillas y **los numeros medidos**.
14. **Idioma y palabras**: el texto que ve el jugador, en **castellano llano**; nada de jerga ("cota" se queda en el
    codigo, en pantalla se dice "nivel del pueblo"); si una palabra no se entiende, se cambia por una clara o en ingles.
15. **Nada de parches que simulen una solucion**: soluciones **de raiz**, y nunca tocar lo que ya funciona (se avisa si
    algo se ha comprobado y **no** se ha tocado, a proposito).
16. **Clase y numero de linea siempre** al explicar un cambio, para poder revisarlo en el IDE.
17. **Entorno**: `$env:GRADLE_USER_HOME="C:\Users\Christian\Documents\DevilRpg\.gradle-home"`; partidas en `run/saves`;
    **registro vivo en `run/logs/latest.log`** (el agente lo lee el solo, no hay que pegarlo); config de servidor en
    `run/config/devilrpg-server.toml` (`logEscaladoDeSpawn` se enciende para medir las velocidades del asedio).
18. **No hay rama nueva por ahora**: se trabaja en `migration-neoforge-1.21.1` hasta cerrar los pendientes.
19. **LO CERRADO SE MARCA, Y LAS NOTAS VIEJAS NO SE BORRAN** (9-oct-2026, y lo pidio el jugador: *«corrige toda la
    documentacion y anota como cerrado lo que ya esta»*). La documentacion acumula anos y **miente sin querer**: una nota
    que decia «queda abierto: el giro interpolado del porton» hizo que el agente propusiera **hacer lo que ya estaba
    hecho** (`PortonDobleBlockEntity` + `PortonDobleRenderer`): lo cazo el jugador. **Regla**: cuando una nota vieja dice
    que algo esta pendiente, **se comprueba** (en el acta, en el codigo o en los registros) y:
    - si **ya esta hecho/medido**, se le pone DEBAJO la marca
      `> ✅ **CERRADO (comprobado el <fecha>)**: <que lo cerro, con I### y/o `Clase` L###>`;
    - si **sigue abierto**, se le pone `> ⚠️ **SIGUE ABIERTO (comprobado el <fecha>)**: <por que> — esta en
      `docs/CONTINUAR.md` §2.0`;
    - y **el texto viejo NO se borra** (cuenta el camino y las hipotesis falsas, que son parte de la medida).
    **El unico traspaso vivo es este fichero** (`docs/CONTINUAR.md`, su §2.0); `docs/PENDIENTE.md` es el viejo y esta
    marcado como tal; y las entradas del acta son **actas**, no listas de tareas.

---

## 2 · PENDIENTES, EN ORDEN (con clase y numero de linea)

### 2.0 · LO QUE ESTA ABIERTO **HOY** (puesto al dia el 9-oct-2026)

**LO ABIERTO HOY, entero y comprobado contra el acta** (9-oct-2026; lo de mas abajo es el historial, con lo que se fue
cerrando y **como**). Va separado por **quien puede cerrarlo**:

**(a) SOLO SE PUEDEN CERRAR JUGANDO** (el arnes no tiene ventana ni jugador de verdad):
1. **El cruce del aldeano por el porton del muro** (**I222**, acta L6436) — **ACOTADO CON SUS DATOS** ✓/✗ (9-oct-2026,
   **I273**): en su partida el `VillageGateGoal` **corre y mucho** (sale en las lineas `PARAR` 40, 18, 14, 10… veces ✓)
   pero **`[Gate]` sale 0 veces** ✗ → el caso del **porton del MURO** no dispara (los que usa son los del anexo/parcelas ✓);
   y en la MISMA sesion sale **20 veces** `el muro NO esta a la cota` ✗ → **sospecha con fundamento**: con el muro
   desnivelado, el porton del muro puede estar en otra celda que la que tiene apuntada la lista `todosLosPortones` (I4) y
   `VillageGateGoal.esPortonDelMuro` (L514/L523) **no lo reconoce** ✗. **Siguiente medida (sin tocar codigo)**: comparar
   la celda del porton del muro **en la lista** con la que hay **en el mundo** de su aldea. Lo de abajo es el historial:
   cruce; el pueblo abre **0** portones en una corrida normal (**I223**). En el registro tiene que salir
   `[Gate] … aldeano DENTRO · destino FUERA` y despues el aldeano fuera.
2. **El asedio EN VIVO** — **MEDIDO A MEDIAS** ✓/✗ (9-oct-2026, **I251**): el jugador jugó y el registro trae
   **`OLEADA de 8 asediadores intentados, 8 colocados`** ✓ y **`Aldea 0 salvada`** (+1 nivel, y se anuncia el nombre:
   **Valleverde**) ✓✓. **Lo que falta es LA PAUSA** ✗ (alejarse más de 128 bloques y volver): no hay traza de eso en el
   registro ni en el mod, así que necesita instrumento propio.
3. **La barra dibujada en el cliente y el clic en la piedra** (**I87**, acta L2138): la tabla de nombres si esta (30
   nombres), pero lo medido es el **texto** de `VillageBarText`, no el **pixel** en pantalla ni el clic en vivo.
4. ~~**Los 14 faroles de la aldea 0**~~ — **CERRADO POR MEDIDA** ✓ (9-oct-2026, **I250**): la auditoria versionada
   (`tools/audita_aldea.py`) da **0 faroles sin apoyo** en su aldea (y 0 en las otras cinco listas: vallas, cofres
   tapados, puertas incompletas, camas sueltas y portones tapados) -> el retrofit ya corrio. *(Y la herramienta ya no
   lleva el nombre del guardado escrito a mano: busca el mas nuevo de `run/saves`, asi no vuelve a caducar.)*

**(b) CODIGO PENDIENTE, PEQUEÑO Y MEDIBLE YA** (lo abierto, numerado; lo cerrado hoy va al final del apartado):

**REPORTES NUEVOS DEL JUGADOR (9-oct-2026, con pantallazo — se miden antes de tocar)**:
- **A · Camas fuera** — **(i) LAS DE ENCIMA DE LOS TEJADOS: ARREGLADAS Y MEDIDAS** ✓✓ (9-oct-2026, **I271**: regla de
  **altura y pueblo entero** —toda cama por encima de `nivel + 9`—, en el latido como retrofit idempotente con
  `// lint:ok I9` y **sin subir `CURRENT_LAYOUT`**; medido en la corrida 186: la traza sale ✓, la capa 77 pasa de **4
  mitades a 0** ✓ y las **20 de dentro siguen intactas** ✓; **en su partida se quitan al cargar el mundo** ✓).
  **(ii) LAS DE LA CASA 1 (oeste), en el plano de su pared: PENDIENTES** ✗ (4 mitades rojas en `x=531`, la columna de la
  puerta; dentro hay dos blancas ✓) — es **otra regla** (cama en la pared, no por altura). Lo de abajo es el historial
  de cómo se acotó (I252-I270), **con tres intentos retirados y dos correcciones mías** ✓:
  (i) **dos camas EN EL TEJADO de la barraca** (capa 74 = pueblo+11), restos de un trazado viejo que **la limpieza no
  barre** porque su banda es `pueblo+3 .. pueblo+8` (`VillageGenerator` L1759-1765) → el arreglo es una **limpieza de
  camas sueltas** por encima del tejado (solo camas), y ya tiene su medida (contar antes: 2 ✗ / después: 0 ✓);
  (ii) **dos camas EN LA CALLE, a los lados de una puerta** — **IDENTIFICADO Y MEDIDO** ✓✓ (9-oct-2026, **I256**, con
  las coordenadas del F3 del jugador): la casa está junto a **(531, 65, 563)** y las camas están en **(531, 560)**,
  **(531, 561)**, **(531, 565)** y **(531, 566)** de la **capa 63** — o sea **en el plano de la pared**, la MISMA `x=531`
  que la **puerta (531,563)** ✗, y por eso quedan en la calle (y **Ximena, Leñador, duerme ahí** ✗). La casa **sí tiene
  segunda planta** (capas 66-71, con escaleras), así que **no es** que no quepan: **se colocan una celda hacia fuera** ✗.
  **Acotado** ✓ (9-oct-2026, **I257**/**I259**): la casa es **la «casa 1 (oeste)»** del trazado
  (`VillageGenerator` L121-133, índice 0) y la levanta **`placeVanillaHouse`** (L263-318), que **vacía** la plantilla
  vanilla y la **vuelve a amueblar** — y ahí las dos camas caen **una celda hacia fuera** ✗.   ⚠️ **TRES INTENTOS, TRES RETIRADAS — Y EL PORQUÉ, YA MEDIDO** ✗ (I265/I266/I268/I269): mi barrido **sí corría** (el
  bloque del latido está dentro de `if (% VILLAGE_POLL_TICKS == 0)` ✓), pero **miraba a la barraca**, que está en el
  centro **+(-45,+22)** (`TRAZADO[6]`, `VillageGenerator` L120-129) ✗ — y **las camas están en +(+28,+17)**, el **mismo**
  desplazamiento en su aldea y en la del arnés. O sea: **corrección de fondo** — ese edificio **NO es la barraca** ✗ (lo
  dije mal en I253/I267). **Siguiente paso (sin escribir código): identificar el edificio de (+33,+21)** con un grep de
  `VillageStorage.OFFSET`, `ANEXO_DX`, `PESQUERA`… y luego apuntar ahí el barrido y medir 4 → 0.

  ⚠️ **TRES INTENTOS DE ARREGLO, LOS TRES RETIRADOS CON SU MEDIDA** ✗ (I265/I266/I268): el último, ya con **la excepción
  que el propio lint prevé** para retrofits (`// lint:ok I9`, `tools/lint_aldea.py` L213-216), compilaba y pasaba el
  lint pero **tampoco quitó ninguna cama** (traza ausente y las 4 de la capa 77 intactas). **Antes del cuarto intento
  hay que medir dos cosas**: (1) dónde cae `baseDeBarraca(center)` = `trazado(center, 6)` (¿cubre mi caja esa huella?)
  y (2) si el bloque del latido donde va la llamada (`VillageManager` L2981) **corre de verdad** o está dentro de un
  `if` que casi nunca se cumple.

  ⚠️ **EL PRIMER INTENTO DE ARREGLO SE RETIRÓ** ✗ (9-oct-2026, **I265**/**I266**): se escribió un **barrido de camas sin
  techo** (regla buena, comprobada contra los cuatro casos de abajo) y, al medirlo en el mundo del arnés, **no quitó
  nada** (su traza no sale) y el **salto de `CURRENT_LAYOUT`** que pide el guardián disparó una **reconstrucción gorda**
  (56 → 36 mitades de cama ✗), así que **se retiró todo** ✓. **Siguiente intento, con la lección**: barrer **todos** los
  edificios (no solo las casas y la barraca) y **primero medir de quién son** las camas sueltas.

  **RESUELTO SIN PREGUNTAR** ✓✓ (9-oct-2026, **I264**, *corrigiendo* mi I263): dibujada la capa 63 de la casa, **dentro
  SÍ hay camas** — dos **BLANCAS** en (535,561) y (536,561) ✓ (mi conteo anterior solo miraba las **rojas** ✗) — y las
  **cuatro mitades rojas de la calle están en las celdas de la PARED** (`x=531`) ✗ → son **CAMAS DE MÁS** (duplicados de
  una migración vieja, **la misma familia** que las **2 camas del tejado** de la barraca y que los composteros «de más»
  de I164). **El arreglo, uno solo para los dos medios del reporte A**: **limpieza de camas sueltas** — quitar las que
  estén **fuera del interior** (en la pared o encima del tejado), **solo camas**, sin tocar las de dentro ✓. Medida:
  **antes** 4 mitades fuera ✗ / 2 blancas dentro ✓ → **después** 0 fuera ✓ y las de dentro intactas ✓.
  **dentro** en ese pegado/amueblado (sin tocar la plantilla) y medir: **2 camas fuera → 0** ✓. *(Descartado el
  camino equivocado: la tabla de camas de `VillageGenerator` L9875-9882 es de la **casa grande**, no de la casa 1 —
  ver **I259**.)*
- **B · El haz de luz del centro** — **MEDIDO: ESTÁ BIEN PUESTO** ✓✓ (9-oct-2026, **I255**/**I258**). El jugador
  confirmó que era el haz. Comprobado en su aldea: **X/Z** = (566.5, 566.5), que es el medio de la **plataforma** del
  kiosco (9×9), de la **campana** (566,64,566) y del **farol** (566,67,566) ✓; **Y**: el haz arranca en `nivel+6` = **69**
  y el **tejado del kiosco está en 68** → nace **justo encima**, no dentro ✓. **No hay desfase en los datos.**
  Lo único que queda ✗: si lo sigue viendo corrido, es **el dibujo en el cliente** (el margen aleatorio de 0,08 de
  L7684-7685, visto de lejos o con la cámara girada) o que fuera **otra aldea** (la suya es la única del guardado) →
  **un pantallazo del haz** lo cierra; el arnés no tiene ventana. **No se toca nada.**

5. ~~**El compostero que algo vuelve a poner en alto**~~ — **CERRADO: NO OCURRE HOY** ✓ (9-oct-2026, **I250**). Medido
   por dos lados: en el guardado del jugador los **3 composteros estan en su celda oficial** (capa 63; la pelea
   necesitaba justo lo contrario, uno una capa por encima de su objetivo) y en una corrida de 3 minutos salieron **0
   lineas** de la traza del asentado. **La incoherencia del codigo se deja escrita como latente** (el que coloca usa
   `max(nivel, suelo)` y el que asienta usa `nivel`): **no se toca** porque no hay medida que lo pida (el intento se
   midio y se retiro, **I245**), y queda dicho **cuando** importaria: una aldea con el suelo de esa columna por encima
   del nivel del pueblo.
6. ~~**LA FASE 5 DEL PLAN DE LA ALDEA: LOS POLLOS**~~ — **CERRADO: NO ERA UN PENDIENTE** ✓ (9-oct-2026, **I249**): el
    gallinero se resolvio **de otra manera** —tiene un **hueco de un bloque a proposito**, *«los pollos pasan, los
    aldeanos no»* (acta L1275, y la auditoria **salta** ese porton)—, asi que el «pollos fuera del recinto: 0» del plan
    **no puede ser** el criterio. La frase del plan queda corregida, y **si el jugador quiere** el porton que se abre al
    paso y se cierra detras, es una **funcion nueva** (se pide, no se cuela en una pasada de cierre).

**Cerrado hoy en este apartado** (se deja escrito, con su numero de invariante):
- ✅ **El dato de control del arnes que no marca** — **CERRADO Y MEDIDO** (**I244**): ahora cuenta la **columna entera**
  (`columnas del anillo abiertas`) y en la corrida 181 marca **8/4320** mientras taladra, donde antes marcaba **0** ✗.
  Y de paso se arreglaron **dos fallos de la escena** (el asaltante se colaba por una **cueva** por debajo del anillo,
  corridas 179 y 180): el anillo es ahora **macizo hasta 30 bloques por debajo del nivel del pueblo**.
- ✅ **El aviso cosmetico del registro** — **ARREGLADO** (**I244**): se compone segun el trazado de verdad
  (`VillageGenerator.java` L1397-1403) y con `BARRACA_PISO2 = 0` dice «un piso: sala de armas y 8 camas». **Lo que falta
  es verlo**: ese aviso solo se escribe **al construir la barraca**, o sea en una **aldea nueva** (`-MundoNuevo`).
- ➡️ **Las losas del tejado a +11 NO eran un pendiente**: es un limite a proposito y esta en **(c)**, abajo.

**(c) ACEPTADO A PROPOSITO (no se toca, y se dice)**:
7. **Una cama sin acceso no se le da a nadie** (**I43**, acta L994): la celda de espera exige estar a **≤2,0 bloques**,
    asi que una cama **encerrada** (muro o mobiliario delante) se queda sin dueno — y el acta dice por que: *«es
    construccion/mobiliario del pueblo, no del reparto»*. **La medida lo respalda** (misma entrada): en la partida,
    `CAMAS RESUMEN: adultos=11 conCama=11 COMPARTIDAS=0 SIN CAMA=0 DURMIENDO=11` ✓ — **nadie se quedo sin cama**. Decision
    robusta: **no se toca el reparto** (dar una cama inalcanzable o mover al aldeano seria peor: el «baile» costo
    I205-I209). Ver **I248**.
8. **Los tejados quedan fuera del obrero** (**I60**, acta L1470): su banda es **+5/−6** sobre el nivel del pueblo
    (`REPAIR_MAX_UP`/`REPAIR_MAX_DOWN`, `VillageManager.java` L852-853, filtro en L6974), asi que **si un tejado se
    rompe, nadie lo sube a arreglar**. El acta lo llama *«limite conocido (dicho a proposito)»* y da la razon: *«subir a
    un tejado es otra obra, no una reparacion de planta»*. **MEDIDO el 9-oct-2026** con `build/obras_pendientes.py`
    (recorre **todas** las celdas del plano, en su altura): **ALDEA 0 (566,566): OBRAS PENDIENTES: 0** ✓ — las **27
    losas a +11** de septiembre **ya no faltan**, el tejado esta entero. O sea: **no hay nada roto hoy**; lo que hay es
    el limite. *(Estaba en el apartado (b) por error mio: lo puse como «codigo pendiente» sin leer que el acta ya lo
    habia decidido asi. Si el jugador QUIERE que los tejados se reparen, eso es una funcion nueva —que el obrero trabaje
    en alto—, no un arreglo.)*
9. **El piso de arriba desconectado por el apiñamiento de camas** (**I104**, acta L3009): es de la plantilla y de la
    migracion de casas; **no se toco**, y lo que hay es la **red de seguridad** (`bajarDeLasCamas` + el rescate de I103).
10. **El minero coge picos de madera teniendo hierro** (**I141**, acta L4642): la entrada dice que **no se toca ahora**
    («no hay medida que lo pida») y nada posterior lo ha cambiado.

Lo de abajo es el historial de los cinco pendientes que se cerraron en esta ronda; debajo del todo esta el historial
antiguo. Y las tres cosas de **instrumento** con las que empezo la ronda: **la primera ya esta cerrada con numeros**:

1. **La escena del nado** (`MEDIR_AGUA`) — **CERRADA Y MEDIDA** ✓ (9-oct-2026, **I235** en `docs/aldea-invariantes.md`).
   Y salieron **tres** cosas, no una: (a) el **barrido de monstruos del arnes** borraba al asaltante en el primer barrido
   (por eso los 73 volcados de `rapida-49.log` eran la misma celda y la misma velocidad: se midio **un monstruo
   congelado**, no un pozo que lo encerrara ✗); (b) la charca se **juntaba con el mar**, porque en la copia del guardado
   el pueblo esta a la **cota 46 y todo alrededor es agua** (`470,45..62 = water`, comprobado celda a celda): ahora la
   escena se levanta **sobre** el agua y no depende del mundo ✓; y (c) lo gordo: **el goal del nado no arrancaba
   NUNCA** —`ZombieAttackGoal` de vanilla esta en la prioridad **2** con el flag MOVE, igual que `EscapeWaterGoal`, y el
   motor solo deja entrar a un goal si tiene prioridad **estrictamente menor** (`WrappedGoal.canBeReplacedBy`)—, asi que
   el arreglo de I215 era **codigo muerto** mientras el asaltante tuviera objetivo. Arreglado a la **prioridad 1**
   (`AggressiveZombieEntity` **L854**) y medido **antes y despues**: **0 ticks** corriendo en 60 s de atasco → **arranca
   3 veces y saca al monstruo del agua en 2-4 ticks**. La vuelta de orilla a ras ya salia sola: **sale del agua a los 6,0 s**
   (3,4 bloques a 0,57 bloques/s dentro del agua) y anda en tierra a **2,2 bloques/s** ✓.
2. **El puente y la escalera en una ola** — **CERRADA Y MEDIDA** ✓: el **puente** se tiende 8 de 8 y cruzan, y la
   **escalera** primero se midio que **no colocaba nada** (0 escalones en cinco corridas) y **se arreglo** (I238), y
   despues se ha vuelto a medir: **103** lineas `ESCALON` en la corrida 173 (I242) — o sea que las dos herramientas se
   ven en una ola ✓
   (9-oct-2026, **I236** en `docs/aldea-invariantes.md`). Escena nueva (`MEDIR_OLA_CON_FOSO`, en el arnes): **muro entero
   y sin huecos** (anillo r=7, 3 de alto, 68 troncos) con un **foso de 4 de ancho y 7 de hondo delante** y **ocho
   asaltantes fuera**, en **dos vueltas** (objetivo al mismo nivel y 4 bloques arriba). Medido en dos corridas de mundo
   nuevo (153 y 154, `-MundoNuevo`): **el puente lo tienden 8 de 8** ✓ y en la 154 **7 de 8 estan DENTRO del muro a los
   21 s** ✓; el puente **sube un bloque por tablon** (altura maxima 5), o sea que un foso de 4 de ancho deja al monstruo
   **mas alto que el muro: lo pasa por encima sin picarlo** ✓. **La escalera no disparaba NUNCA** (0 `escalon` en cinco
   corridas), **ni dandole su caso** (vuelta 3 de la escena, corrida 158: foso **relleno**, muro **quitado** y el
   objetivo **en el aire**: **0 escalones otra vez**, los ocho asaltantes **debajo del objetivo** y varios **cavando
   hacia abajo**, `266 pica` casi todo `stone` de la plancha). **La causa quedo medida en la linea exacta**:
   `apilarBloqueParaSubir` (**L692**) llamaba **antes** a `breakStepAheadHacia(hacia, true)` y **se salia con `true` en
   cuanto picaba algo**; con `conEscalon = true` esa llamada **pica tambien el suelo de delante**, asi que en cualquier
   suelo rompible (siempre) **cavaba el suelo y no apilaba**: la rama que **coloca** el bloque era **inalcanzable**.
   **ARREGLADO EN I238** (el orden: **colocar primero, picar como ultimo recurso**) y **medido despues** (corrida 159):
   **65 `escalones`** donde antes habia **0**, el puente sigue tendiendose (**13**) y en la vuelta 3 **5 de 8 DENTRO**
   con la escalera en pie ✓. Y DOS HALLAZGOS
   que salieron de la foto: los que entran lo hacen **por debajo** (tunel bajo el muro: 125 piedras
   picadas justo en r=6..7 y los 68 troncos de base **en pie**), y el **romper y el puente se peleaban** (26-36 de los
   bloques picados eran `cobblestone` en r=8..11: **los tablones que ellos mismos acababan de tender**) — **ARREGLADO EN
   I239** (la obra del asedio no se pica: regla del aire debajo, lista **compartida** de tablones y el veto en la unica
   puerta que rompe, `breakBlockAt`) y **medido**: los tablones rotos bajan de **26-36 a 4** y el asedio construye mas
   (puente **16** lineas y escalera **76**, las dos mas que antes) ✓.
3. **Ver la milicia con gente dentro** — **CERRADA Y MEDIDA** ✓ (9-oct-2026, **I237** en `docs/aldea-invariantes.md`).
   Escena nueva (`MEDIR_MILICIA_SOBRANTES`): pone ella misma **12 adultos SIN OFICIO** (el caso «mas adultos que
   puestos») y **3 crias** alrededor de la plaza y deja correr el latido. Medido en un mundo conservado
   (`tanda-rapida.ps1 156 157 -Conservar`): `[Milicia] aldea 0: 4 espadachin(es) y 3 arquero(s) de 12 sobrante(s) de 27
   aldeano(s), milicia hasta 7 | alistados DE VERDAD: 7 (4 espadachin(es), 3 arquero(s))` ✓, las **6 crias nunca se
   alistan** ✓, y los guardias **entrenan** (la marca `entrenado` sube de 0 a **713-777 ticks** y las etiquetas pasan de
   `Yendo a entrenar` a `Patrullando…`) y **se arman** (`arma=minecraft:air` -> `minecraft:bow`, `Cogio 16 flechas del
   almacen`) ✓. **Y LO GORDO, que es una regla del mod y hay que saberla**: el reparto de la milicia vive en
   `tickVillageLife`, que **solo corre con la aldea EN PAZ** (`manageNearby` L1040: `!isUnderAttack`), y una aldea nueva
   **nace con su asedio inicial** — que con un jugador de pega no se resuelve nunca: la corrida 155 dio `guardias=0` los
   tres minutos y ni una linea `[Milicia]`. El instrumento lo arregla dando el asedio **por resuelto** cuando la aldea ya
   esta construida (ver la receta, abajo).
4. **LA MONTAÑA Y EL TUNEL DE FRENTE** — **MEDIDO Y SE CUMPLE** ✓ (9-oct-2026, **I240** en `docs/aldea-invariantes.md`).
   Lo pregunto el jugador: *«lo del tunel se penso para cuando hay una montaña entre el asediador y la villa… en vez de
   rodear (por si es demasiado grande la circunferencia) pueda mejor cavar de frente… si la aldea esta por encima, puede
   hacer una escalera y atravesar abismos o brechas grandes»*. Escena nueva (`MEDIR_MONTANA`): un **anillo de piedra
   CERRADO** a r=70 (imposible de rodear) y el asaltante fuera, a r=80, **sin objetivo** (marchando al centro). Medido:
   **anda hasta la montaña** (r=80 -> r=73 en 9 s), **taladra** (el anillo pasa de 0 a 6 celdas abiertas a los 48 s y a
   **15** a los 72 s) y **la atraviesa** (r=63 a los 72 s, ya en el muro del pueblo; despues muere peleando con la
   milicia). El **puente** (8 de 8, I236) y la **escalera** (65-76 escalones, I238) ya estaban medidos ✓. **Un cambio mio
   que sobraba** (un «paseo a pasos cortos» para acercarse) se midio con su **corrida de control** y **se retiro**: sin el
   el asaltante llega **antes** (72 s y 15 celdas contra 96 s y 12) ✓. Y **la montaña GRUESA aguanta** ✓ (corrida 170:
   anillo de **26 de fondo**: en 156 s avanza de r=92 a r=86 y abre 29 de 37 440 celdas, **sin atravesarla**), que es lo
   que el jugador queria. **Y EL TOPE YA ESTA MEDIDO** ✓ (9-oct-2026, **I241**): las cinco lineas del asedio llevan ahora
   **`asaltante #<id>`** (y el arnes dice cual es el suyo), asi que se pueden contar los bloques de UNO; con el tope de
   verdad el ritmo es **~1 bloque cada 4 s** (38 en 150 s, corrida 171) y **agotar 40 pide 8-10 minutos**, mas de lo que
   dura el banco ✗; con una **corrida de control con el tope en 5** se vio lo que pasa al agotarse: el contador baja
   **5 -> 0** en **20 bloques** y **el asaltante se queda** (anillo clavado en 5 celdas y el monstruo parado en r=91 el
   resto de la corrida) ✓. Y **se recarga con cada centro que se le asigna** (`setGoToCenterActive(true)` **L199** llama a
   `recargarTunel()`): **cada ola vuelve con 40 bloques** — **Y YA ESTA MEDIDO** ✓ (corrida 177, **I243**): el contador de
   la traza (`le quedan N de tunel`) baja `5→1`, y al **volver a asignarle centro** (t=1680) **vuelve a 5** y sigue
   taladrando ✓. Y al medirlo salió **un fallo de la escena**: el anillo se levantaba desde la cota y en un mundo con el
   terreno 13 bloques más alto quedaba **enterrado** (el asaltante lo pasaba andando, r=95→66 en 40 s ✗); **arreglado**
   (cada columna se apoya en su terreno) y **la montaña gruesa vuelta a medir** (corrida 178: r=92→88 en 150 s, 11
   taladros, **sin atravesarla**) ✓.
5. **CAVAR POR DEBAJO OBLIGA A SALIR** — **ARREGLADO Y MEDIDO** ✓ (9-oct-2026, **I242** en `docs/aldea-invariantes.md`).
   La regla del jugador: *«si cavan por debajo pero despues hacen algo para salir a la superficie, obligatorio, esta
   bien; pero si no, mejor que se queden solo cavando cuando sea montaña o algo que los bloquee»*. **No se cumplia
   siempre**: en la corrida 154 el asaltante #0 acabo **8 bloques bajo el suelo y clavado 8 volcados** en un pozo que se
   habia cavado (la escalera no podia colocar porque la celda de delante, a la altura de los pies, era la pared del pozo,
   y el pico le gastaba el presupuesto de escalones). **ARREGLADO**: el escalon se intenta a la altura de los pies y, si
   esa celda esta ocupada, **un bloque mas arriba** (desde el fondo del pozo: un bloque al que saltar). **Medido
   despues** (corridas 173/174/175, 24 casos): fondo maximo **6 bloques** (dentro del foso, que cava 7) y **ninguno bajo
   tierra** ✓, con la escalera subiendo (**103** lineas `ESCALON`). Y queda una **herramienta versionada** para mirarlo
   (`tools/arnes/vuelven_a_la_superficie.py`), porque el dato de control del arnes mezclaba «estar en el foso» con «estar bajo
   tierra».

**Y EL MUNDO DE LA TANDA: LA RECETA YA ESTA MEDIDA (9-oct-2026)** ✓. Lo que salia antes eran **contadores de trabajo a
cero** porque la copia del guardado no tiene aldea donde el arnes la busca (en `470,63,646` habia **mar**: lo dice
`tools\arnes\una_columna.py`, agua de la 45 a la 62), asi que el mod **funda una aldea nueva a la cota 46, bajo el agua**.
**Lo que hay que hacer, y funciona** (medido en I236/I237): **(1)** una corrida con `tanda-rapida.ps1 N -MundoNuevo` (se
genera el mundo y el mod construye la aldea: el cimiento son cientos de miles de bloques y se pasa la corrida
construyendo), y **(2)** las corridas siguientes con **`tanda-rapida.ps1 N+1 N+2 -Conservar`**, que **conservan** ese
mundo ya construido: el pueblo sale **vivo** (medido: **granja 2, ganado 3, pescador 4, herreria 60, cocina 11,
minero 87, guardia 105**) y con **asedio inicial que hay que dar por resuelto** para que corra el latido de la paz
(`tickVillageLife`; lo hace el arnes solo cuando la aldea ya esta generada: ver I237). **Regla de oro intacta:
`run\saves` no se toca** (todo esto va sobre `run\world`).

**Y EL PORTON DOBLE ABATIBLE: CERRADO** ✓ (8-oct-2026, I224–I234 en `docs/aldea-invariantes.md`). Abre y cierra
animado con **entidad de bloque + renderizador propio**, la hoja abate sobre el **canto del marco** sin atravesar el
pilar, es de **un bloque de espesor** (el mismo cerrada y abierta, asi que no hay salto), se pulsa en **toda** la
superficie y el estado abierto deja pasar. Va con **24 modelos** y **192 variantes**, y `HOJA_FUERA` separa la **vista**
de la logica para que el cierre tambien se anime. Tres reglas que costaron caro y quedaron escritas en
`tools/arnes/LEEME.md`: **`run\saves` no se toca nunca**, **con el cliente abierto se mata el proceso y se sigue**, y
**la geometria de un modelo de bloque solo va de −16 a 32** (si se sale, el juego dibuja el damero y miente diciendo
«FileNotFoundException»).
**MEJORAS DEL PORTON, PARA EL FUTURO** (las dejo apuntadas y **fuera del alcance** por decision del jugador, 8-oct-2026):
todavia **no estan definidas** — hay que sentarse a decidir en que merece la pena mejorarlo antes de tocar nada.

---
1. **La regla del muro perimetral** — **CERRADA** (I210 + I211 en `docs/aldea-invariantes.md`), y el fallo de raiz era
   una linea: `AggressiveZombieEntity.protegidoPorLaAldea` (**L539**) terminaba en `return elAsedioYaSeGano()`, o sea que
   **con el asedio inicial sin resolver devolvia FALSE para toda la aldea**. Lo canto la verificacion independiente de la
   cuenta (`tools/arnes/verificar_recinto.py`, 13 casos, sin servidor), no otra corrida. Arreglado: dentro se devuelve
   **`true`** siempre (la regla es de GEOMETRIA: muro perimetral si, dentro no; el campo de fuerza se queda para expulsar
   monstruos y negar spawneo), el veto se aplica **en la puerta** (`breakBlockAt`, **L303**, el unico sitio que destruye un
   bloque), las dos preguntas del recinto usan **la cota de ahora** (`cotaParaElRecinto`, **L470**) y `MURALLA_ANCHO` baja
   **5 → 1 → 0** (**L142**; la verificacion cazo que con 1 la cara de dentro del muro seguia rompible). Medido: la cuenta
   clasifica bien los 13 casos; el asaltante de la aldea **ganada** no toca nada de dentro (**2** bloques, los dos
   **fuera**); y con la regla puesta o puenteada (interruptor `REGLA_DEL_MURO_ACTIVA`, **L287**) el asaltante de fuera
   pica **los mismos 16 bloques, todos r=62..65** (solo el anillo), y el de dentro **0** — porque dentro del pueblo el
   asaltante **anda** hacia su objetivo y no necesita romper nada (medido de r=49 a r=1 sin un solo bloque).
2. **Escalera de bloques, tunel y puente** — **HECHOS Y CORREGIDOS** (I212 + I213). Ya no son «los que no existen»:
   `apilarBloqueParaSubir` (**L688**, el escalon), `cavarHaciaAbajo` (**L777**, el tunel) y `puentearHacia` (**L583**, el
   puente, que ya estaba escrito pero **solo lo llamaba la marcha al centro**, que se apaga en cuanto el asaltante
   tiene objetivo: en un asedio no se usaba nunca). Los usa `TraverseGoal` (**L1495**), pasivo y sin flags como el
   romper, sirviendo a los DOS caminos y **eligiendo por situacion**. **CORREGIDO en I213** (lo que salio mal): el
   puente **solo miraba de frente** (ahora los **cuatro lados**, ordenados por el que mas apunta al objetivo) y el
   escalon **se ponia debajo de sus propios pies** (ahora en la **columna de delante, a la altura de los pies**; el
   fallo lo cazo la ola real, no las escenas). **Medido con `MEDIR_OLA_REAL`** (8 asaltantes a r=60, modo nuevo):
   **6 dentro a los 11 s y 8 a los 37 s**, y de las tres herramientas **solo se usa el tunel (19 cavadas de terreno)**
   — el muro **no lo pica nadie** (`TALADRAR` = 0) porque el anillo **tiene huecos y la ola entra andando**. ABIERTO:
   (a) esa mitad de la regla («el muro perimetral es rompible») **no la ejerce nadie** hoy: o los huecos del anillo se
   cierran, o el asedio no tiene por que abrir brecha; (b) falta ver el **puente y la escalera** en una ola — **CERRADO el 9-oct-2026**: los dos se ven (el **puente** 8 de 8 y la **escalera** 103 lineas `ESCALON`), ver **I236/I238/I242** ✓.
   **CORREGIDO Y MEDIDO en I214**: con el muro de prueba **en r=62** (el radio del muro de verdad), el asaltante
   **ABRE LA BRECHA** (el muro paso de **15 bloques a 10**: pico 5) y **entro** — es la primera medida de esa mitad de
   la regla, y sale **si**. Y entro **andando por el hueco**, sin necesitar la escalera. Ademas, tres fallos de flujo
   mas de los atravesadores, corregidos: `breakStepAheadHacia` **devolvia al picar el hueco y el escalon no se ponia
   nunca** (por eso 0 ESCALON en las corridas 42-46); y dos trampas del instrumento (el muro de prueba puesto **dentro**
   del recinto lo protege la regla -> 15/15 intacto, que es la prueba de que la regla funciona; y el borde de la zanja
   del puente a 3-4 bloques, donde no hay hueco que cubrir).
3. **LOS HUECOS DEL ANILLO DEL MURO** — **RESUELTO** (I218), y **no eran huecos: EL MURO NO EXISTIA**. Medido desde
   dentro del juego (el lector de guardados fallo tres veces; el que funciona es `columna_mina.py`): **ni un tronco ni un
   adoquin en r=62 de la cota-30 a la cota+30**, con la aldea construida. La causa: el muro se levanta en `fence()` con
   la **cota del centro (63)** y el pueblo crecio a **83**, asi que quedo **enterrado veinte bloques**; y `rehacerMuro`
   (la unica ruta que lo reconstruye a la cota buena, L6979) **solo se llama desde la migracion** (VillageManager
   L2364), que **corre una sola vez por aldea**. Arreglado con `VillageGenerator.asegurarMuro` (**L6885**, idempotente),
   llamado desde **`manageNearby` (L1034)** y **NO** desde `tickVillageLife` (que solo corre en aldea EN PAZ: ahi el
   muro no se repararia justo con el asedio dentro, y una aldea vacia no lo levantaria nunca — ver I183). Medido:
   **0 -> 912 de 921 celdas**, los 9 agujeros son **los cuatro portones** (ESTE=3 SUR=2 OESTE=2 NORTE=2), el centro
   **no se movio** (470,63,646) y el asedio **ya pica `cobblestone` y `oak_log`** en vez de solo nieve.
   **DECISION DEL JUGADOR: PORTONES DE VERDAD (hecho en I219)**. Eligio que el asedio tenga que ROMPERLOS: se pone
   **puerta de valla** (`OAK_FENCE_GATE`, 2 de alto) en las 4 entradas cardinales, porque el juego NO deja que un zombi
   abra ni rompa una puerta de valla, y el pueblo SI las cruza con su goal (`VillagerGateGoal`). Las cuatro celdas van
   en el sitio unico de los portones (`VillageGenerator.portonesDelMuro` + `todosLosPortones`, I4). Medido:
   `con muro 921 | PORTONES 9 | AGUJEROS 0 | racha 0` (los 9 = 4 celdas x 2 puertas + 1 dintel), el guardado dice
   `PORTONES 4 celdas: [(408,646),(470,584),(470,708),(532,646)]` y **0 agujeros**, y la ola **ya solo entra rompiendo
   el muro** (`oak_log` y `cobblestone`), **0 puertas rotas**, y **la milicia los mata a los 8** (t=1680).
   **HECHO: PORTON DOBLE ABATIBLE PROPIO, DE 3 DE ANCHO x 3 DE ALTO (I224)**. Nuevo bloque
   `devilrpg:porton_doble` (`DoubleGateBlock`): hojas de 1x3 con `SIDE`/`LAYER`, **12 modelos** y **48 variantes**, que
   **abaten** (cerrada ocupa su celda; abierta de canto en la bisagra) y **no se rompen** (el juego solo deja romper
   `DoorBlock` en difícil), asi que **el asedio tiene que abrir brecha en el MURO**. El hueco son **TRES celdas del
   anillo** cubiertas por **tres bloques** (hoja izquierda, derecha y juntura del medio), que es lo que da el **3 de
   ancho** de verdad. Lo abre el latido (`abrirLosPortonesSegunElPueblo`): **aldeano a menos de 4 bloques -> abren; si
   no, cierran**. Medido: las 4 entradas con el porton (`83/84/85=porton_doble 86=cobblestone`), anillo
   **`921/921 | PORTONES 19 | TAPADOS 0 | AGUJEROS 0`** y **1 reconstruccion**.
   **DOS FALLOS PROPIOS medidos**: con hueco de 3 y solo DOS hojas el muro se reconstruia **3200 veces** (el anillo es
   de UNA celda de grosor: un hueco de 3 deja 3 celdas) -> **una pieza por celda**; y `asegurarMuro` contaba con **su
   propia lista** en vez de mirar el bloque -> mira el bloque. **TEXTURA PROPIA DE ROBLE OSCURO (hecha)**:
   `devilrpg:textures/block/porton_doble.png` (16x16 RGBA: tablones de roble oscuro con flejes de hierro remachados y
   aldaba), generada con `tools/arnes/hacer_textura_del_porton.py` (el PNG se escribe con la libreria estandar: en
   esta maquina no hay PIL). Los 12 modelos apuntan a ella y los tres pisos cogen su tercio, asi que el porton se ve de
   una pieza. Medido: **ni un aviso de modelo ni de textura** en el registro, la textura viaja al recurso del mod, y el
   anillo sigue `921/921 | PORTONES 19 | AGUJEROS 0` con 1 reconstruccion.
3. **El nado del asaltante** — **HECHO en I215** y el **instrumento YA ESTA** (**I235**, 9-oct-2026: el goal arranca 3 veces y saca al monstruo del agua en **2-4 ticks**) ✓. El defecto estaba en la cuenta del propio
   codigo: `MAX_ESCAPE_TICKS = 200` (10 s intentando salir) **+ `retryCooldown = MAX_ESCAPE_TICKS`** en `stop()` (otros
   10 s **sin poder tocar el agua**) = **20 segundos** en los que el asaltante ni avanza ni intenta nada, que es
   exactamente el *"se quedan ahi y avanzan muy lento"* del jugador. Arreglado en `EscapeWaterGoal` (**L1162**): no se
   rinde (`canContinueToUse`, L1233), `stop()` (L1254) ya no pone castigo, el tope es un **recalculo** de orilla
   (`RECALCULAR_CADA_TICKS`, L1185) y el empujon va cada **6** ticks en vez de 15 (L1298). **ABIERTO**: la escena del
   pozo del arnes (`MEDIR_AGUA`) **no mide** (el asaltante queda flotando en el borde, velocidad 0,012, y no sale ni
   con el arreglo ni sin el): hay que quitarle las paredes al pozo antes de dar el nado por medido.
4. **El asaltante de `MOVEMENT_SPEED: 0.552`** — **RESUELTO** (I216). Era un **crio**: el juego le pone al zombi crio un
   modificador de velocidad de **x1,5** (medido con el arnes: adulto `0,23` -> crio `0,345`) y ese multiplicador se
   aplica **despues** de la base, asi que el tope del mod (base `0,23 x 1,6 = 0,368`) se quedaba corto:
   **`0,368 x 1,5 = 0,552`**, exactamente el numero del registro. Arreglado en
   `AggressiveZombieEntity.adjustAttributesBasedOnSpawnDistance` (**L1125-1140**): el tope se aplica al **VALOR FINAL**
   (la base se deriva dividiendo por los modificadores que ya tenga el monstruo). Medido: el crio de la aldea 0 sale a
   **0,552 SIN el arreglo y a 0,230 CON el** (base 0,1533 x 1,5), y la progresion no se toca.
5. **Traza de la milicia** — **HECHA** (I217). `VillageManager.repartirGuardia` (**L3216**) escribe
   `[Milicia] aldea N: X espadachin(es) y Y arquero(s) de S sobrante(s) de A aldeano(s), milicia hasta 7 | alistados DE
   VERDAD: G (…)` **solo cuando el reparto cambia**, y escribe **los dos numeros** (lo que el bucle cree alistar y lo que
   de verdad lleva la marca) para que una discrepancia se vea en vez de esconderse. Medido en la partida de 3 minutos:
   `0 espadachin(es) y 0 arquero(s) de 0 sobrante(s) de 12 aldeano(s) | alistados DE VERDAD: 0` — **y el cero no es un
   fallo**: con los puestos por oficio cubiertos no hay sobrantes y sin sobrantes no hay milicia (es la regla del
   reparto). **Falta ver** una milicia con gente dentro (aldea con crias o mas adultos que puestos) — **CERRADO el 9-oct-2026**: visto y medido en **I237** (4 espadachines y 3 arqueros de 12 sobrantes, y las crias **nunca** se alistan) ✓.

---

## 3 · LO QUE YA ESTA CERRADO Y MEDIDO (no hay que volver a tocarlo)

- **La aldea no se desplaza ni se duplica**: el centro es el del objetivo (`VillageManager.centroDe`). Medido: 0 lineas
  de reconstruccion en las partidas.
- **El asedio funciona de principio a fin**: `OLEADA de 8 asediadores intentados, 8 colocados` y luego `Aldea 0 salvada`
  (arreglado: una oleada vacia no escribe victoria, reintentos, segunda pasada de spawn).
  > ⚠️ **ESTO NO TIENE PRUEBA CITADA, y el acta dice lo contrario** (comprobado el 9-oct-2026): I86/I89 (acta L2262 y
  > L6220-6222) dicen que **el asedio en vivo NO se ha visto** — «0 lineas de `ASEDIO`/`OLEADA`» —, y **ningun registro
  > guardado** de `build/rapida-*.log` tiene la traza `OLEADA` ✗. Se deja la afirmacion porque cuenta la intencion, pero
  > **lo que falta es verlo en la partida**: esta en **§2.0 (a), punto 2**.
- **Los aldeanos no bailan**: I205 (traza + un goal sin MOVE no pisa camino ajeno + el empujon ya no para a quien anda),
  I206 (el embudo `ponerRumbo` no reescribe el destino cada tick: `[Rumbo]` = 94-96 % saltadas), I207 (puertas: se abren
  en vez de sacarlos a empujones), I208 (`parar` idempotente por tick), I209 (una celda de paso no es un encajamiento).
- **Parcelas limpias**: `[Huerta] 0 sin sembrar, 0 pisoteadas` en las partidas nuevas.
- **Velocidad del asaltante anclada**: en la aldea 0 sale a **0.230** (zombi normal de vanilla); la progresion intacta.
- **Milicia completa**: dos roles (`ESPADACHIN`/`ARQUERO`), se **equipa del almacen** (y de la armadura que el pueblo
  fabrique, pieza a pieza), se le **ve el equipo**, escala vida y dano, y la remesa inicial del almacen trae su equipo de
  arranque (cuero, espadas de madera, escudos, arcos, flechas).
- **El arnes fuera de la partida** (`[Arnes]` = 0) y el aviso de compilacion puesto.

---

## 4 · MAPA DE DOCUMENTOS Y HERRAMIENTAS

- `docs/aldea-invariantes.md` — **el acta**: cada cambio con su clase+linea, su medida, y las hipotesis falsas.
- `docs/aldea-cerebro.md` — el plan de arquitectura del aldeano y **por que se retiro** la fase que se probo (§3).
- `docs/PENDIENTE.md` — el traspaso **VIEJO** (27-sep a 3-oct): se conserva como **acta** (el porque medido de la sesion de los oficios), **no** como lista de pendientes, y lleva su aviso arriba. **Lo vivo es este fichero.**
- `tools/arnes/tanda-rapida.ps1` (~3 min) y `tanda-larga.ps1` (~20 min) — los bancos de medida (copian el arnes a un
  directorio ignorado y levantan el servidor **sin el jugador**).
- `tools/lint_aldea.py --strict` — el guardian (I8: mutar el mundo en el latido, etc.; las excepciones se justifican con
  `// lint:ok <clave> porque ...`).
- `run/logs/latest.log` — el registro vivo de la partida del jugador (el agente lo lee el solo).
- `run/config/devilrpg-server.toml` — `[logs] logEscaladoDeSpawn` (encendido para medir el asedio).

---

## 5 · ERRORES YA COMETIDOS (para no repetirlos)

1. Se compilo el **arnes de pruebas dentro de la partida del jugador** (le vacio el almacen): candado por entorno + aviso.
2. Se **commiteo un build roto** por encadenar compilar y commitear sin mirar el resultado.
3. Dos hipotesis falsas publicadas como ciertas y luego tumbadas: que a los goals les faltaba la bandera `MOVE` (la
   tienen), y que los `PARAR` eran peleas (eran **llegadas normales**).
4. Se **retiro una idea buena** (no reescribir el camino cada tick) por medirla mal: ahora es I206.
5. Se **leyo mal la leyenda** del cortador de mapas (`C/P/B/W` eran cultivos, no cobble/planks/bricks/water).
6. Se atribuyo a un bug el "794 bloques" (era la distancia al ancla de invocacion, por diseno).
7. El centro de la aldea tomado de la caja de lo construido **desplazo y duplico** la aldea del jugador (mundo perdido).
