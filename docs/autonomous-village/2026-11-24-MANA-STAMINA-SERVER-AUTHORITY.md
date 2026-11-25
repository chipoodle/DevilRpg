# Recursos — maná/stamina eran autoridad del cliente (24-nov-2026)

**Rama:** `feature/autonomous-village`
**Código:** `f231923a` (+ normalización `40c1fec5`)
**Origen:** auditoría de skills runtime; continuación de I416/I417.

---

## 1 · Defecto sistémico

Seis payloads eran **bidireccionales** y, en la rama `SERVERBOUND`, deserializaban el NBT del cliente
sobre la capability del servidor:

`PlayerManaPayload`, `PlayerStaminaPayload`, `PlayerSkillTreePayload`, `PlayerExperiencePayload`,
`PlayerAuxiliarPayload`, `PlayerMinionPayload`.

En maná/stamina no hay ninguna escritura legítima del cliente: el cliente **enviaba** su maná al
servidor en cada `setMana`/regeneración. Consecuencias:

- un cliente modificado podía **ponerse maná/stamina infinitos**;
- el uso normal podía **consumir dos veces** (cliente + servidor) y competir con la regeneración;
- el servidor no tenía regeneración propia: dependía del NBT del cliente.

## 2 · Corrección (maná + stamina)

- `PlayerManaCapabilityImplementation` / `PlayerStaminaCapabilityImplementation`: el cliente ya **no
  envía** su estado; solo el servidor manda S→C.
- `PlayerManaPayload` / `PlayerStaminaPayload`: la rama `SERVERBOUND` se **ignora** con log
  (“el servidor es la autoridad”).
- `ModNetwork`: maná/stamina pasan de `playBidirectional` a **`playToClient`**.
- **Regeneración en servidor**: `PlayerCapabilityForgeEventSubscriber.onPlayerTick` regenera maná y
  stamina cada 10 ticks con `PlayerResourceRegenPolicy` (mismos números que el cliente; 0,7 base y
  −0,01 por punto de transformación). Sin esto el maná del servidor se habría quedado congelado.
- El cliente conserva la predicción local para el HUD, pero el servidor manda la verdad.

## 3 · Evidencia

- `PlayerResourceRegenPolicy` con 3 aserciones nuevas (lobo 0→0,7; lobo 20→0,5; humano→0):
  `SKILL_CAPABILITY_INTEGRATION_TEST: OK (387 assertions)` (antes 384).
- `gradlew test ...`: **BUILD SUCCESSFUL** (1m21s); persistencia 553, ownership 59.
- `git diff --check` limpio (ModNetwork normalizado a LF en `40c1fec5`).

## 4 · Lo que queda: L9

Siguen **bidireccionales** y aceptando NBT del cliente:

También se cerraron **auxiliar** (transformación) y **minions**, que son estado puro: el cliente ya no
los envía (`playToClient`, SERVERBOUND ignorado). Los minions los crea el servidor y la transformación
la disparan los payloads de skill; el cliente solo recibe el estado.

Quedan **dos** payloads con acciones legítimas del cliente que aún aceptan NBT completo:

- `PlayerSkillTreePayload` (asignar puntos al árbol),
- `PlayerExperiencePayload` (`consumePoint` al gastar un punto).

Ahí el cliente **sí** tiene una acción legítima, pero debe ser un **payload de acción** validado por el
servidor (¿hay punto disponible?, ¿el nodo es válido?, ¿el nivel/prerrequisito se cumple?) en vez de
un `deserializeNBT` del estado completo. **L9 abierta, reducida de 4 a 2.**

## 5 · Límites

- No hay cliente real: la autoridad y la regeneración se verifican por código/prueba pura; el flujo
  cliente→servidor con HUD no se ejecuta.
- La predicción local del cliente puede ir un instante por delante del servidor; el sync la corrige.
- L9 (2 payloads de acción: experiencia/árbol) sigue abierta.

## 6 · Veredicto

**Maná, stamina, estado auxiliar y minions son autoridad del servidor**, con regeneración propia y
payloads C2S rechazados. **Skills 387**. Queda **L9 con 2 payloads de acción** (experiencia/árbol),
L7, L8 (excluido) y cliente real.
