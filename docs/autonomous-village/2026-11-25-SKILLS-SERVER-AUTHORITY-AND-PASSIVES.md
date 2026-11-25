# Skills — servidor dedicado, autoridad del árbol y pasivos (25-nov-2026)

**Rama:** `feature/autonomous-village`
**HEAD inicial:** `adf87d27`
**Código skills:** `56454c63`
**Estado:** servidor dedicado y autoridad del árbol **validados**; brecha del zombie **candidata sin commit**.

---

## 1 · Fallo real de servidor dedicado (S1)

El cliente funcionaba, pero un servidor dedicado **no podía construir la capability de Skills**:

```text
java.lang.NoSuchMethodError:
'net.minecraft.resources.ResourceLocation
 com.chipoodle.devilrpg.client.gui.scrollableskillscreen.SkillDisplayInfo.getImage()'
  at ScrollableSkillList.getImageOfSkill
  at PlayerSkillCapabilityImplementation.initNbt
  at PlayerSkillCapabilityImplementation.<init>
```

Causa: `SkillDisplayInfo.getImage()`/`getIcon()` estaban anotados `@OnlyIn(Dist.CLIENT)`.
`RuntimeDistCleaner` **borra** esos métodos en el servidor dedicado, pero el código **común**
(`PlayerSkillCapabilityImplementation` y el constructor de `AbstractSkillExecutor`) los llama. El
servidor no arrancaba Skills: fallaba al pedir `player.getData(PlayerSkillCapability.INSTANCE)`.

**Corrección:** los getters de `SkillDisplayInfo` son comunes (los campos ya lo eran). No cambia el
cliente; el servidor dedicado ya construye la capability y los ejecutores.

## 2 · Autoridad del árbol (S2)

`PlayerSkillTreePayload` aplicaba el NBT completo del cliente, incluidas claves de servidor
`MINIONS_KEY`/`ATTRIBUTE_MODIFIER_KEY`: un cliente podía inyectar minions y modificadores de
atributos. `applyClientTree` copia **solo** las claves que el cliente posee (puntos, máximos, costes,
tipos, iconos, poderes y loadouts) y conserva los minions/atributos del servidor. La validación de
gasto (`SkillTreeSpendPolicy`) sigue igual: mismo total o +1 con punto, y el servidor consume.

## 3 · Skills de minion en la barra del jugador (S3/S6)

`WOLF_FROSTBITE`, `WOLF_ICE_ARMOR`, `WAR_BEAR` y `MOUNT_BEAR` son **activas de minion**
(`ofMinion=true`), no poderes del jugador. `SkillScreen` solo excluía pasivos, así que podían
arrastrarse a una tecla; al pulsarlas, `SingletonSkillExecutorFactory` no tiene ejecutor de jugador y
el servidor reventaba. `SkillCastPolicy.esAsignableAPoder` exige
`!isPassive() && !isForMinion()`; `SkillScreen` lo usa al arrastrar y al soltar. **Se siguen pudiendo
comprar/nivelar**; sus efectos en `SoulWolf`/`SoulBear` siguen en `MinionPassiveAttributes`.

## 4 · Pasivos: por qué no van a la barra (verificado)

- `SkillCastPolicy` y `SkillScreen` exigen `!isPassive()`; un pasivo **no** se puede arrastrar.
- Al comprar un pasivo no-minion, `SkillScreen` manda `PlayerPassiveSkillPayload`; el servidor
  `triggerPassive` solo ejecuta pasivos **raíz** (no hijos de un activo) y con nivel > 0.
- Los pasivos hijos (`SKIN_ARMOR`, `WEREWOLF_VITALITY`, `WEREWOLF_HIT`, `KNOCKBACK_RESISTANCE`) los
  ejecuta su padre activo con `executePassiveChildren`.
- `SOULSHIELDVINE_RADIUS`, `FLORAS_VIGOR` y `VINEFLESHBALL` se leen directamente desde la skill activa
  o la entidad; sus ejecutores no son un poder de barra.
- Los pasivos de minion (`WISP_*`, `MINION_VITALITY`, `WISP_ARMY`) no se envían; los aplica
  `MinionPassiveAttributes` al cargar el minion.

No se modificó la lógica de pasivos ni de minions. El comportamiento que viste es el correcto.

## 5 · Evidencia

- `SKILL_CAPABILITY_INTEGRATION_TEST: OK (416 assertions)`:
  - 5 de `SkillTreeSpendPolicy`;
  - 6 de `SkillCastPolicy`;
  - cobertura de ejecutor para las 14 skills activas de jugador;
  - 3 de sanitización del árbol (Minions/AttributeModifier no inyectables).
- Gate completo: **BUILD SUCCESSFUL** (1m45s); persistencia 553, ownership 59.
- Servidor dedicado (fake player, NeoForge real):
  `SKILL_SERVER_HARNESS OK checks=25`, con 33 skills, 36 nodos, transformación/reversión, maná,
  skill de minion sin crash y NBT recargado. Log: `build/skill-harness/server-run-2.log`.

## 6 · Skills nuevas documentadas

No existe backlog canónico de poderes nuevos: `design-roadmap.md` dice expresamente *«Sin backlog
canónico de poderes pendientes; no inventar nombres»*. Los 33 `SkillEnum` reales están en el árbol;
las 4 activas de minion y las 6 pasivas de minion ya están implementadas para minions. Si hay un
documento con nombres concretos, hay que aportarlo antes de inventar nodos o ejecutores.

## 7 · Brecha del zombie (candidata, sin commit)

El informe de partida dice que un asedio quedó atascado en una brecha de **una columna**. El código
`breakBrechaAhead`/`breakBlock` abría una sola columna; el AABB de 0,6 del zombi puede solaparse con
los bloques laterales y `NavObstruction.fitsActualAabb` lo rechaza. Se añadió
`ensancharBrecha` (columna tangencial contigua, mismo presupuesto y reglas) en marcha y combate.
**No está commiteado ni validado en runtime**: falta reproducir el asedio y comprobar que el zombi
cruza. El gate estático pasa; no se declara cerrado.

## 8 · Veredicto

Skills queda **validado en servidor dedicado y autoridad del árbol**, con pasivos y skills de minion
sin cambios de comportamiento. La brecha del zombie es un candidato de navegación pendiente de
prueba runtime.
