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
2. **Escalera de bloques, tunel y puente**: **NO EXISTEN** (comprobado en todo `src`: solo hay `EscapeWaterGoal` L851 y
   `BreakBlockGoal` L1021). Son **tres atravesadores nuevos**, con la forma de los que hay (clase interna, decide en el
   mismo tick, sin tanteos) y **a velocidad normal**: apilar bloques para subir, cavar si el objetivo esta por debajo,
   poner puente sobre hueco o agua.
3. **El nado del asaltante**: que avance **a velocidad normal** mientras esta en el agua (sin depender de la navegacion
   de vanilla) y revisar **`MAX_ESCAPE_TICKS = 200`** de `EscapeWaterGoal` (L851), porque hoy se rinde y se queda parado.
4. **Un asaltante salio a `MOVEMENT_SPEED: 0.552`** (= 0.23 x 2.4) con el mismo `scaleFactor` que los demas: ese **no paso
   por el tope** del 1.6 y hay que averiguar por que (¿otro perfil?, ¿otro camino de creacion?).
5. **Traza de la milicia** (pequeno): que el reparto escriba `[Milicia] aldea N: X espadachines y Y arqueros equipados`,
   para poder **contarlos** en el registro en vez de deducirlo. El reparto vive en `VillageManager.repartirGuardia`
   (**L3182**, llamado desde **L3079**) y **ya alterna** los tipos (`i % 2 == 0 ? ESPADACHIN : ARQUERO`, L3222-3227), asi
   que el **2 espadachines + 2 arqueros** ya sale solo con cuatro milicianos.

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
