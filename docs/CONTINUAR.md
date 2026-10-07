# CONTINUAR AQUÍ — acuerdos, convenciones y pendientes (leer lo PRIMERO)

Rama: **`migration-neoforge-1.21.1`** (estable, compilada, con `lint --strict` verde y **subida a GitHub**).
Ultima actualizacion: 5-oct-2026, al cerrar la ronda de la milicia y de los asaltantes.

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
    que **no sirve** para medir el baile fino; para el caminar valen `[Rumbo]` y `no consigue llegar`.
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

---

## 2 · PENDIENTES, EN ORDEN (con clase y numero de linea)

1. **La regla del muro perimetral** — **CERRADA** (I210 + I211 en `docs/aldea-invariantes.md`), y el fallo de raiz era
   una linea: `AggressiveZombieEntity.protegidoPorLaAldea` (**L539**) terminaba en `return elAsedioYaSeGano()`, o sea que
   **con el asedio inicial sin resolver devolvia FALSE para toda la aldea**. Lo canto la verificacion independiente de la
   cuenta (`tools/arnes/verificar_recinto.py`, 13 casos, sin servidor), no otra corrida. Arreglado: dentro se devuelve
   **`true`** siempre (la regla es de GEOMETRIA: muro perimetral si, dentro no; el campo de fuerza se queda para expulsar
   bichos y negar spawneo), el veto se aplica **en la puerta** (`breakBlockAt`, **L303**, el unico sitio que destruye un
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
   cierran, o el asedio no tiene por que abrir brecha; (b) falta ver el **puente y la escalera** en una ola.
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
   **CORRECCION MEDIDA (I223)**: el diagnostico de I222 era **FALSO** — el porton cerrado **SI deja trazar camino**
   (medido con dos aldeanos de verdad: `SABE, 4 nodos` con el porton cerrado y `SABE, 2 nodos` con el abierto: lo
   encarece, no lo impide). Y en una partida normal **el pueblo abre 0 portones en 3 minutos**, porque **no tiene nada
   que hacer fuera del muro** (sus oficios estan dentro). O sea: **el muro cumple su funcion sin depender de que el
   pueblo salga** (el asedio rompe: `oak_log`/`cobblestone`, 0 puertas rotas) y **no hay ninguna circular**. El arreglo
   de `vaACruzar` se queda porque **abre antes** (el aldeano que quiera salir no se queda pegado a la puerta), no
   porque hiciera falta. La traza `[Gate]` con el **lado** (`aldeano DENTRO · destino FUERA`) es la que dice, en la
   partida, si el pueblo cruza.
3. **El nado del asaltante** — **HECHO en I215** (falta el instrumento). El defecto estaba en la cuenta del propio
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
   (la base se deriva dividiendo por los modificadores que ya tenga el bicho). Medido: el crio de la aldea 0 sale a
   **0,552 SIN el arreglo y a 0,230 CON el** (base 0,1533 x 1,5), y la progresion no se toca.
5. **Traza de la milicia** — **HECHA** (I217). `VillageManager.repartirGuardia` (**L3216**) escribe
   `[Milicia] aldea N: X espadachin(es) y Y arquero(s) de S sobrante(s) de A aldeano(s), milicia hasta 7 | alistados DE
   VERDAD: G (…)` **solo cuando el reparto cambia**, y canta **los dos numeros** (lo que el bucle cree alistar y lo que
   de verdad lleva la marca) para que una discrepancia se vea en vez de esconderse. Medido en la partida de 3 minutos:
   `0 espadachin(es) y 0 arquero(s) de 0 sobrante(s) de 12 aldeano(s) | alistados DE VERDAD: 0` — **y el cero no es un
   fallo**: con los puestos por oficio cubiertos no hay sobrantes y sin sobrantes no hay milicia (es la regla del
   reparto). **Falta ver** una milicia con gente dentro (aldea con crias o mas adultos que puestos).

---

## 3 · LO QUE YA ESTA CERRADO Y MEDIDO (no hay que volver a tocarlo)

- **La aldea no se desplaza ni se duplica**: el centro es el del objetivo (`VillageManager.centroDe`). Medido: 0 lineas
  de reconstruccion en las partidas.
- **El asedio funciona de principio a fin**: `OLEADA de 8 asediadores intentados, 8 colocados` y luego `Aldea 0 salvada`
  (arreglado: una oleada vacia no canta victoria, reintentos, segunda pasada de spawn).
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
- `docs/PENDIENTE.md` — la lista historica de pendientes y el acta de cierre.
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
