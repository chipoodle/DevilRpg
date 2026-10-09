# GLOSARIO — cómo se dice cada cosa (30-sep-2026)

> **ESTADO DE ESTE DOCUMENTO (puesto al día el 9-oct-2026).** Esto es un **glosario de estilo** (cómo se dice cada
> cosa), no una lista de tareas: sus definiciones se comprobaron contra el código el 9-oct-2026 y **siguen siendo
> ciertas** (p. ej. «parcela» son las parcelas de 9×9 de `VillageGenerator.FARM_PLOTS` / `PLOT_WIDTH`-`PLOT_DEPTH`,
> L138-153; los «8 huecos» del inventario del aldeano están en `entity/goal/VillagerFarmGoal.java` L714; y el
> cimiento del final del documento es el de radio 86 = `VillageGenerator.FENCE_RADIUS + 24`, L398). **El único
> traspaso vivo es `docs/CONTINUAR.md` (§2.0 = lo abierto HOY).** Dos notas de proceso quedaron viejas y **no** se
> tocan aquí, porque no son definiciones (van al informe de la ronda): el script `build/glosario.py` ya no está en
> el disco (la carpeta `build/` está ignorada por git y se limpia) y el recuento de «**983 apariciones**» de «cota»
> no se ha podido reproducir (hoy salen **698** en `src/**/*.java` y **928** en todos los `.java` fuera de `build/`).

Lo pidió el jugador al leer los avisos: *«¿qué es bancal? ¿zurrón? ¿cómo que el almacén tampoco traga? ¿qué palabras
son esas? O buscas un sinónimo más entendible o lo pones en inglés»*.

**Se queda en español llano** (todo el proyecto está en español: los documentos, los comentarios y los avisos que ve el
jugador; meter inglés rompería la mitad del texto). Y la regla es: **si una palabra no la usa un jugador cualquiera, no
vale**.

| lo que se decía | lo que es | **lo que se dice ahora** |
|---|---|---|
| **bancal** | la parcela de cultivo vallada de 9×9 de cada granjero (con su acequia, sus compuertas y su compostero) | **parcela** (o **huerta** cuando se habla del conjunto) |
| **zurrón** | el inventario del aldeano: sus 8 huecos, lo que lleva encima | **inventario** |
| **«la despensa no traga»** | que el cofre de la despensa está lleno y no admite más | **«la despensa está llena»** |
| **«si el almacén tampoco tragara»** | que el almacén tampoco tiene sitio | **«si en el almacén tampoco cabe»** |
| **cota** (lo que decía antes) | **jerga de topografía**, no del juego: es la **altura** a la que está el pueblo | **nivel del pueblo** — y se explica con un dibujo, abajo |
| **POI / estación** | el bloque donde un aldeano trabaja (compostero, ahumador, mesa…) | **puesto de trabajo** |
| **rendición** | el aviso de que un aldeano **no consiguió llegar** a su destino y lo deja por un rato | **«no consigue llegar»** / **se rinde** |
| **testigo** | la marca del pueblo que sirve para saber si una estructura sigue en pie. **OJO**: *«testigo» es solo
para las ESTRUCTURAS*; el dato que deja el arnés para poder creerse una medida se llama **prueba** (o «dato de
control»), nunca testigo | **señal de la estructura** |
| **aparcar** (un sitio) | dejar de intentar ir a ese sitio durante unos minutos | **dejar de lado por un rato** |

## Cómo se aplica (y qué NO se toca)

El cambio se hizo con un script (`build/glosario.py`) que sustituye **solo en comentarios y en textos entre comillas**
—lo que lee una persona—, **nunca en identificadores**: hay métodos como `bancalSinDueno`, `esCalvaDeBancal` o
`laSalidaDelBancal` que romperían la compilación (y que, además, son nombres internos que no ve nadie).

**Al escribir texto nuevo en este proyecto**: si dudas, mira esta tabla; y si una palabra no la entendería alguien que
no haya leído el código, se cambia.

## «cota» → **«nivel del pueblo»** (preguntado el 30-sep-2026)

El jugador preguntó *«¿qué es cota?»*. Es **jerga de topografía** (la altura de un terreno) y yo la usaba como si fuera
algo obvio. **Lo que significa en este proyecto**, con un dibujo:

```
      nivel del pueblo = 78   ← aquí están los PIES del aldeano: aquí se anda
      suelo firme      = 77   ← el bloque que se pisa (SIEMPRE uno por debajo)
      relleno          = 76   ← tierra/piedra debajo
```

Así que «el suelo va en *cota − 1*» es lo mismo que decir **«el suelo está un bloque por debajo de donde andan»**. Se
usa para todo lo que tiene que estar a la altura del pueblo: el kiosco, el muro, los caminos, los composteros, las
parcelas… y sirve para detectar lo que está **torcido** (una cosa un bloque más alta o más baja de lo que toca).

**Dónde se ha cambiado**: en los **avisos que lee el jugador** (los del registro), que ahora dicen *«kiosco de la plaza
colocado **al nivel del pueblo** 78»*, *«muro reconstruido **al nivel del pueblo** 78»*, *«REPARADA (quitados 6452 bloques
de restos **por encima del nivel del pueblo** (78) y terreno nivelado)»*.

**Dónde NO se ha cambiado, y por qué**: dentro del **código** la palabra sigue en **nombres de funciones y variables**
(`cotaDeLaPlaza`, `fijarLaCotaDeLaAldea`, `int cota = …`): son **983 apariciones** mezcladas con el código y renombrarlas
a ciegas **rompe la compilación** (lo intenté y rompió 164 ficheros; se revirtió). Los nombres internos no los lee nadie
más que quien programa, y están explicados aquí.

## Corregido el 30-sep-2026: **lo «hundido» que veía el jugador era el VACÍO de debajo**

El jugador dijo: *«varias construcciones están hundidas un bloque y **alrededor está hueco y da a un pozo** porque
**abajo de la villa está hueco y debería ser sólido**: chequea **alrededor de la choza del minero**»*. Medido en su
guardado (`build/slice_mina.py`), alrededor de la choza del minero:

```
y=77  G G . . B B B B B B B . . G G     <- el suelo de la choza (bien: nivel del pueblo−1)
y=76  . . . . . . . . . . . . . . .     <- AIRE
y=75  . . . . . . . . . . W . . . .     <- AIRE (y la columna del pozo de la mina)
y=74  . . . . . . . . . . . . . . .     <- AIRE
```

La choza **no está hundida** —su suelo está a `nivel del pueblo − 1`, que es lo correcto—: **está sobre el vacío**. Debajo y alrededor
no hay terreno, y solo el **pozo de la mina** (hueco a propósito) tiene algo. Eso es lo que hace que se vea «hundida» y lo
que un día se abre en socavón. **Lo tapa el CIMIENTO de I166** (radio 86 desde el centro del pueblo; la choza está a 44,
así que entra de sobra): rellena de piedra **24 bloques hacia abajo** todo hueco y **respeta el pozo de la mina**.

**Y una corrección mía, dicha sin adornos**: supuse que era **la huerta** la que estaba un bloque hundida (por una
rendición con `destino=farmland` en `y=77` y los aldeanos en `y=78`)… y **la medición dice que no**: como **el nivel del pueblo es el
nivel de los pies y el suelo va en `nivel del pueblo − 1`**, el cultivo en `y=77` con el pueblo andando en `y=78` es **lo correcto**
(el corte del mundo lo confirma en las dos parcelas). Aquella rendición era la clase A de siempre —el destino es un
cultivo, que no se pisa—, que ya arregla I154. **I167** (`asentarLaHuertaALaCota`, trazado 80) se queda como **red de
seguridad** para cuando una migración cambie el nivel del pueblo y el plano se quede con la vieja: **no** era el fallo que se veía.
