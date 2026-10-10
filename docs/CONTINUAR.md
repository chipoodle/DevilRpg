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
1. **El cruce del aldeano por el porton del muro** (**I222**, acta L6436): cinco corridas del arnes y ninguna aislo el
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
- **A · Camas fuera** — **MEDIDO Y ACOTADO** ✓/✗ (9-oct-2026, **I252**/**I253**/**I254**). Son **dos casos**:
  (i) **dos camas EN EL TEJADO de la barraca** (capa 74 = pueblo+11), restos de un trazado viejo que **la limpieza no
  barre** porque su banda es `pueblo+3 .. pueblo+8` (`VillageGenerator` L1759-1765) → el arreglo es una **limpieza de
  camas sueltas** por encima del tejado (solo camas), y ya tiene su medida (contar antes: 2 ✗ / después: 0 ✓);
  (ii) **dos camas EN LA CALLE, a los lados de una puerta** (el pantallazo): **no son de la barraca** ✗ (en la barraca
  las de dentro están bien, capa 68) → es **otro edificio** y hay que **identificarlo antes de tocarlo** ✗: falta que el
  jugador dé **las coordenadas (F3)** de esa casa.
- **B · El haz del centro** — **MEDIDO Y NO SE REPRODUCE** ✓ (9-oct-2026, **I255**): en su aldea el haz sube por
  **(566.5, 566.5)** y ahí están **el centro de la plataforma del kiosco (9×9)**, la **campana** (566,64,566, `dx=+0
  dz=+0`) y el farol (566,67,566) ✓✓. Su guardado solo tiene **esa** aldea. **Falta saber qué efecto vio**: el haz del
  sello (nº 1), las chispas de la intrusión (nº 2, salen **sobre cada monstruo**, que es lo correcto) o un ritual/guarida
  (nº 3). **Con un pantallazo o diciendo cuál, se cierra en un minuto.**
