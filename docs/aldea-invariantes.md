# Aldea: invariantes y lista de consecuencias

Este documento existe por una razón concreta: **casi todos los bugs que reportó el jugador en la aldea fueron
una sola clase** (una altura que no era la cota) y, peor, varios salieron de arreglar el síntoma donde apuntaba el
informe en vez de arreglar la **invariante** y barrer el resto del código. Cada regla de aquí abajo está escrita
después de un bug **medido en juego**, con la evidencia, para que no vuelva.

En cada cambio en `world/Village*` o en los goals de aldeano hay que:

1. repasar la **lista de consecuencias** (§2), y
2. pasar el lint (`python tools/lint_aldea.py --strict`), que vigila las invariantes de §1.

---

## 1. Invariantes

### I1 · Toda altura se mide con `cotaDeLaPlaza`, nunca con la Y del centro
El centro de una aldea viaja por todo el sistema (`ObjectiveTargets.targetOf`, `centroDe`) y **su Y no es la del
pueblo**: llega la del *spawn del jugador* (101 en la partida, con las aldeas a 62-75) o directamente **0** (en el
centro que se saca del plano). Con esa Y:
- el granjero buscaba los cultivos **30 bloques por encima del suelo** y no veía ninguno: daba vueltas y volvía a la
  despensa sin cosechar ("hay betabel y zanahoria y no los cosecha");
- el recolector no recogía nada (155 objetos tirados en una aldea);
- la comprobación "¿es muelle de la herrería?" buscaba en y=101 y no encontraba nada, así que **la herrería se
  reconstruía cada 10 s** (54 reconstrucciones seguidas en el log) y cada una **tiraba el botín de su cofre** al
  suelo (`Containers.dropContentsOnDestroy`): espadas, picos, armaduras, sillas de montar, diamantes.

**Regla:** la Y de cualquier cosa de la aldea (cultivos, cofres, composteros, puestos de trabajo, huecos, medidas de
bloques) sale de `VillageGenerator.cotaDeLaPlaza(level, center)`. Si hace falta un centro "con Y buena", se pide a
`VillageManager.centroDe` (que ya devuelve la **cota**) o se usa `parcelasDe(level, center)`.

### I2 · Las distancias "dentro de la aldea" son HORIZONTALES
La aldea es un **recinto en el plano XZ**. Midiendo en 3D, un aldeano a 30 bloques por encima del suelo cuenta como
"fuera de su pueblo" (el recolector se quedaba bloqueado en la puerta del goal) y un zombie dos bloques por encima
del suelo contaba como "no ha entrado" en el asedio, salvando la aldea de rebote.
**Regla:** `dx*dx + dz*dz` para "¿está dentro del radio?".

### I3 · Atascado = NO ACERCARSE (un contador de paciencia no cuenta los ticks de camino)
Los goals llevan `stuckTicks` y `canContinueToUse` se corta a `STUCK_LIMIT`. Contando **cada tick** mientras el
aldeano camina, el goal se rendía a los 120 ticks (6 s) aunque fuera avanzando: un viaje a la despensa (20-30
bloques) no lo terminaba **nunca** y el granjero se quedaba ciclado con la cosecha encima ("no sube al kiosco a
poner la cosecha").
**Regla:** se guarda la distancia más corta del viaje (`mejorDistancia`) y el contador solo sube cuando **no** se
acerca.

### I4 · Los tamaños y medidas compartidas son constantes, no números a mano
`PLOT_WIDTH`/`PLOT_DEPTH` los usan el generador **y** los goals (los goals tenían `dx < 9`, `dz < 5` escritos a
mano, así que al agrandar la parcela a 9x9 el granjero seguía mirando solo un trozo). El radio del kiosco se pide
con `kioscoRadio()`.
**Regla:** un número que usan dos sitios vive en un solo sitio y se expone.

### I5 · El aldeano camina POR EL CEREBRO
Los aldeanos son mobs de cerebro: en cada tick el cerebro escribe su propio destino (su puesto, la plaza, la cama,
pasear) y **pisa** la navegación manual. Con `getNavigation().moveTo(...)` el aldeano se iba a otro lado a mitad de
camino ("primero da vueltas y se va a otro lado antes de recogerlos").
**Regla:** `VillageManager.caminarHacia(...)` al ir y `VillageManager.parar(...)` al llegar.

### I6 · Lo que corre en el latido tiene que ser IDEMPOTENTE y no destruir contenedores
El latido de la aldea corre cada 10 s para siempre. Cualquier "asegurar" que no detecte que la obra ya está hecha
se convierte en una máquina de reconstruir; y reconstruir un cofre **tira su contenido** al suelo (mecánica de
vanilla) y vacía la despensa de la aldea.
**Regla:** comprobar con un bloque testigo buscado **a la cota** (no por la Y del centro) y no tocar lo que ya está.

### I7 · Cambiar lo que se construye obliga a subir la migración
Las aldeas ya construidas no se rehacen solas: la migración solo corre si `layout < CURRENT_LAYOUT` (o
`casasVersion < CURRENT_HOUSES`). Un arreglo solo de código **no llega** a las aldeas existentes: hay que subir la
versión, y si cambian las casas, también `CURRENT_HOUSES`.
**Regla:** si cambia el trazado, la parcela, un edificio o el aspecto del mundo ya guardado → subir la versión (el
lint avisa con I9 si se toca `VillageGenerator` y no se sube en el mismo diff).

### I8 · Lo que construye el generador tiene que entrar en el PLANO
El obrero repone lo que dice el plano. Lo que se construye **después** de capturarlo (o con `level.setBlock` en vez
de `colocar`) no está en el plano y no se repone nunca; y lo que es "terreno natural" tampoco entra. Esto ya dio
dos bugs: el muro de troncos (no se reponía) y los minerales (flotando **y** en el plano).

---

## 2. Lista de consecuencias (obligatoria en cada cambio)

Antes de escribir el commit, para CADA valor, bloque, contador o comportamiento que toco:

1. **¿Quién más LEE lo que cambio?** Buscar todos los usos (`grep`) y revisarlos uno a uno. *(Fallo real: cambié el
   sentido de `stuckTicks` y no miré los tres `canContinueToUse` que lo leen → granjero, recolector y obrero
   ciclados.)*
2. **¿Depende de una ALTURA?** ¿Es la cota (I1)? ¿Y si el centro/base trae otra Y?
3. **¿Cambia el estado del mundo ya guardado?** → migración (I7) y cómo se repara una aldea que ya existe.
4. **¿Corre en el latido o en un goal repetido?** → ¿es idempotente? (I6) ¿puede destruir un contenedor? ¿puede
   dejar objetos tirados?
5. **¿Rompe el plano o la reparación?** (I8)
6. **¿Cliente o servidor?** ¿Hay que renderizar o sincronizar algo? ¿Es código solo-cliente? (la etiqueta de dos
   líneas necesitó un `RenderNameTagEvent` en el cliente porque `Font.drawInBatch` no parte líneas).
7. **¿Rendimiento?** ¿Escaneos por tick? ¿Cuántas columnas/bloques por pasada? ¿Se puede cachear?
8. **Casos raros** (mirar los que ya nos han mordido): aldea **sobre agua** (islita), bioma **frío** (agua que se
   congela), **montaña** (recorte y minerales), **cueva/barranca** debajo (agujeros), aldea **vieja** (layout
   antiguo), aldea **caída** (ruinas), **chunk descargado**, **jugador ausente**, aldeas a **200 bloques** entre sí.
9. **¿Cómo lo COMPRUEBO?** Guardado (bloques y entidades reales), log del juego, o lint. Si no puedo comprobarlo,
   **decirlo claramente** en vez de dar por hecho que funciona.
10. **¿Afecta a lo que el jugador YA tiene?** Aldeas existentes, inventarios, cofres, granjas sembradas.

---

## 3. Herramientas

| Herramienta | Para qué |
|---|---|
| `python tools/lint_aldea.py --strict` | Vigila I1-I9 en el código. Puerta antes de commitear. |
| `build/inventario.py` | Inventario de estructuras de una aldea en el guardado (qué edificios hay y dónde). |
| `build/granjaestado.py`, `build/farmdiag.py`, `build/columnas.py`, `build/perfilcol.py` | Estado de la granja (cultivos, edades, cotas) y columnas crudas. |
| `build/items.py`, `build/contenedores.py` | Objetos en el suelo por tipo y contenido de cofres/despensa/almacén. |
| `build/aldeanos.py`, `build/aldeanos.py` | Aldeanos: profesión, inventario, posición (carpeta `entities/`). |
| `build/herreria.py`, `build/huecos_ore.py`, `build/solares*.py` | Herrería, huecos y minerales flotantes, solares libres. |
| `build/plantillas*.py`, `build/paleta.py` | Plantillas del juego: tamaños, puertas y qué bloques traen. |

Los scripts de `build/` no se versionan (está en `.gitignore`): son de lectura del guardado del jugador.
