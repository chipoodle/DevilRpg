# DevilRpg — Diseño y Roadmap (Action-RPG)

Visión: convertir **DevilRpg** en un **action-RPG de fantasía oscura** (estilo *Heretic* de Raven
Software) donde el mundo está vivo y es hostil. No es "un mod más": el jugador debe **sobrevivir,
avanzar y nunca establecerse**, porque el mundo se endurece, los enemigos se organizan y las
comunidades que encuentra pueden vivir o caer.

> El plan está pensado para que las mecánicas funcionen hoy sobre el mapa horizontal y **se reutilicen
> después** en el futuro "abismo vertical" (estilo *Made in Abyss*).

---

## 1) El loop central

> El mundo alrededor del jugador está vivo y es hostil. Sales de tu punto seguro (spawn), el terreno se
> vuelve más duro mientras más te alejas, los enemigos se organizan y se fortalecen, las aldeas que
> encuentras viven (construyen, cultivan, envejecen, se defienden) y **pueden caer** — y vos tenés que
> seguir avanzando, sin poder establecerse en ningún lado.

### Los 3 pilares

1. **Escalación + dirección** — el mundo se endurece (distancia desde el spawn **y** tiempo) y te
   empuja a salir (objetivos cada vez más lejos).
2. **Enemigos vivos** — hordas, guaridas, adaptación, se fortalecen con el tiempo.
3. **Asentamientos vivos** — aldeas que progresan, se defienden y pueden ser arrasadas.

---

## 2) Arquitectura del sistema de supervivencia

Se apoya en dos herramientas que ya se construyeron:

- **`SpawnScaleProfile`** (`spawnprofile`): perfil neutro de **distancia** — curva de probabilidad y
  factor de escalado de atributos según la distancia al spawn. Lo comparten la entidad y su spawnRule
  sin acoplarse.
- **`ThreatLevel`** (`survival`): componente de **tiempo** — amenaza global 0..1 que crece con las horas
  de juego. Server y cliente la calculan igual sin sincronizarla (deriva del reloj del mundo).

```
Nivel de amenaza (tiempo) ──┐
                            ├─▶ factor de dificultad = scaleFactor(distancia) × multiplier(tiempo)
SpawnScaleProfile (distancia)┘
```

### Paquete `com.chipoodle.devilrpg.survival`

| Clase | Responde a | Rol |
|---|---|---|
| `ThreatLevel` | "¿cuánto se endureció el mundo?" | 0..1 por tiempo + `multiplier()`. |
| `ObjectiveTargets` | "¿hacia dónde tengo que ir?" | Posición determinista del objetivo `i` desde el spawn (server y cliente coinciden). |
| `ObjectiveManager` | "¿llegué al objetivo?" | Server: avanza el índice al alcanzar el objetivo. |
| `HordeManager` | "¿me atacan en grupo?" | Server: horda periódica, tamaño/frecuencia según amenaza. |

---

## 3) Iteración 1 — IMPLEMENTADA ✅

### 3.1 Escalación (distancia **+** tiempo)

- La entidad captura **`spawnDistance`** (distancia horizontal al spawn) **y** **`spawnThreat`** (amenaza
  global al momento del spawn).
- Al ajustar atributos (una sola vez en `aiStep`):

```java
double scaleFactor = PROFILE.scaleFactor(spawnDistance) * spawnThreat;   // distancia × tiempo
```

Con esto, un zombie generado lejos del spawn **y/o** tarde en la partida es más fuerte
(vida/velocidad/daño). El `AggressiveZombieEntity` ya lo aplica.

### 3.2 Objetivo direccional (te empuja a salir)

- **`ObjectiveTargets.targetOf(spawn, index)`** → el objetivo `i` está a `800 + i*600` bloques del spawn,
  en **una dirección fija** (el mismo rumbo para todos, para que la flecha no salte de dirección y no
  confunda). Determinista → server y cliente calculan el mismo punto.
- **`ObjectiveManager.tick(player)`** (en el tick del jugador, servidor): si el jugador está a ≤24 bloques
  del objetivo, avanza el índice (y se sincroniza al cliente). Cada objetivo está **más lejos**, lo que
  obliga a seguir avanzando y a no establecerse.
- El índice se guarda en la **`PlayerAuxiliaryCapability`** (se sincroniza automáticamente, sin nueva
  capability/payload).
- **HUD `ObjectiveHudOverlay`**: en la parte superior central muestra "Objetivo N — X m ↑" con la
  distancia restante y una flecha cardinal relativa al giro del jugador.

### 3.3 Horda periódica

- **`HordeManager.tick(level)`** (en el tick del servidor): cada cierto intervalo (que se acorta con la
  amenaza; 20 min → 3 min) intenta spawnear una partida de `AggressiveZombieEntity`.
- Los zombies de la horda están **sometidos al `SpawnScaleProfile`**: cada uno spawnea solo si pasa la
  probabilidad según la **distancia del jugador a su spawn**. Zona protegida (<200) → 0; de 200 a 1500 →
  fracción; ≥1500 → todos.
- La horda ataca al jugador **más lejos de su spawn**; si todos están en la zona protegida, no spawnea
  nada (el jugador está a salvo cerca de su base).
- El **tamaño** crece con la amenaza (`1 + round(amenaza*6)`).

---

## 4) Roadmap (próximas iteraciones)

### Iteración 2 — Enemigos inteligentes (pilar 2)
- Comportamiento de manada (rodea, esquiva, usa el terreno).
- **Guaridas** que **cambian el terreno** alrededor (placas, estructuras) y que el jugador puede asaltar.
- Se fortalecen con el tiempo (ya arrancado con `ThreatLevel`).

### Iteración 3 — Asentamientos vivos (pilar 3)
- Aldeanos que **construyen/reparan/fortifican**, **cultivan**, **necesitan comer**, **envejecen** y se
  defienden cuando llegan las hordas.
- El jugador **ayuda a progresar** pero no son dependientes.
- **Riesgo real**: la aldea puede **caer** y el jugador debe buscar otra.

### Iteración 4 — El abismo vertical (estilo *Made in Abyss*)
- El mundo genera un **abismo descendente infinito** por capas en vez de extenderse en horizontal.
- La escalación por "distancia al spawn" se convierte en **profundidad** (el mismo `SpawnScaleProfile`,
  solo cambia la variable). Por eso se construyó la Iteración 1 pensando en reutilizarla.

---

## 5) Configuración rápida

- **Amenaza**: `ThreatLevel.MAX_EXTRA_DIFFICULTY` (0.8 = +80%) y `FULL_THREAT_TICKS` (3 h).
- **Objetivo**: `ObjectiveTargets.BASE_DISTANCE` (800), `STEP_DISTANCE` (600), `REACH_RADIUS` (24).
- **Horda**: `HordeManager.BASE_INTERVAL_TICKS` (20 min al inicio), `MIN_INTERVAL_TICKS` (3 min con máxima
  amenaza, es decir a más amenaza salen más seguido), `MAX_EXTRA_MEMBERS` (6). El tamaño base es 1 enemigo
  con amenaza 0 (jugador débil) y crece con la amenaza.

> TODO (siguiente): que las hordas apunten al **asentamiento más cercano** en vez de al jugador, para
> conectar con la Iteración 3.
