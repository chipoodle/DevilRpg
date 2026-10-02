# GLOSARIO — cómo se dice cada cosa (30-sep-2026)

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
| **cota** | el nivel de los pies del pueblo (el suelo firme está un bloque por debajo) | **cota** (se deja: es la palabra del propio juego para la altura, y ya está explicada en las invariantes) |
| **POI / estación** | el bloque donde un aldeano trabaja (compostero, ahumador, mesa…) | **puesto de trabajo** |
| **rendición** | el aviso de que un aldeano **no consiguió llegar** a su destino y lo deja por un rato | **«no consigue llegar»** / **se rinde** |
| **testigo** | la marca del pueblo que sirve para saber si una estructura sigue en pie | **señal de la estructura** |
| **aparcar** (un sitio) | dejar de intentar ir a ese sitio durante unos minutos | **dejar de lado por un rato** |

## Cómo se aplica (y qué NO se toca)

El cambio se hizo con un script (`build/glosario.py`) que sustituye **solo en comentarios y en textos entre comillas**
—lo que lee una persona—, **nunca en identificadores**: hay métodos como `bancalSinDueno`, `esCalvaDeBancal` o
`laSalidaDelBancal` que romperían la compilación (y que, además, son nombres internos que no ve nadie).

**Al escribir texto nuevo en este proyecto**: si dudas, mira esta tabla; y si una palabra no la entendería alguien que
no haya leído el código, se cambia.

## Corregido el 30-sep-2026: **lo «hundido» que veía el jugador era el VACÍO de debajo**

El jugador dijo: *«varias construcciones están hundidas un bloque y **alrededor está hueco y da a un pozo** porque
**abajo de la villa está hueco y debería ser sólido**: chequea **alrededor de la choza del minero**»*. Medido en su
guardado (`build/slice_mina.py`), alrededor de la choza del minero:

```
y=77  G G . . B B B B B B B . . G G     <- el suelo de la choza (bien: cota−1)
y=76  . . . . . . . . . . . . . . .     <- AIRE
y=75  . . . . . . . . . . W . . . .     <- AIRE (y la columna del pozo de la mina)
y=74  . . . . . . . . . . . . . . .     <- AIRE
```

La choza **no está hundida** —su suelo está a `cota − 1`, que es lo correcto—: **está sobre el vacío**. Debajo y alrededor
no hay terreno, y solo el **pozo de la mina** (hueco a propósito) tiene algo. Eso es lo que hace que se vea «hundida» y lo
que un día se abre en socavón. **Lo tapa el CIMIENTO de I166** (radio 86 desde el centro del pueblo; la choza está a 44,
así que entra de sobra): rellena de piedra **24 bloques hacia abajo** todo hueco y **respeta el pozo de la mina**.

**Y una corrección mía, dicha sin adornos**: supuse que era **la huerta** la que estaba un bloque hundida (por una
rendición con `destino=farmland` en `y=77` y los aldeanos en `y=78`)… y **la medición dice que no**: como **la cota es el
nivel de los pies y el suelo va en `cota − 1`**, el cultivo en `y=77` con el pueblo andando en `y=78` es **lo correcto**
(el corte del mundo lo confirma en las dos parcelas). Aquella rendición era la clase A de siempre —el destino es un
cultivo, que no se pisa—, que ya arregla I154. **I167** (`asentarLaHuertaALaCota`, trazado 80) se queda como **red de
seguridad** para cuando una migración cambie la cota y el plano se quede con la vieja: **no** era el fallo que se veía.
