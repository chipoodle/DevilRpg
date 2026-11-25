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

- `PlayerSkillTreePayload` (puntos/árbol),
- `PlayerExperiencePayload`,
- `PlayerAuxiliarPayload` (transformación),
- `PlayerMinionPayload`.

Ahí sí hay acciones legítimas del cliente (asignar puntos, transformarse, gestionar minions), pero
deben ser **acciones validadas por el servidor**, no un `deserializeNBT` del estado completo. **L9
abierta**: diseñar payloads de acción con validación (puntos disponibles, nivel, propiedad, cupo) en
vez de aceptar el NBT del cliente.

## 5 · Límites

- No hay cliente real: la autoridad y la regeneración se verifican por código/prueba pura; el flujo
  cliente→servidor con HUD no se ejecuta.
- La predicción local del cliente puede ir un instante por delante del servidor; el sync la corrige.
- L9 (4 payloads de acción) sigue abierta.

## 6 · Veredicto

**Maná y stamina pasan a ser autoridad del servidor**, con regeneración propia y payload C2S
rechazado. **Skills 387**. Queda L9 (4 payloads de acción), L7, L8 (excluido) y cliente real.
