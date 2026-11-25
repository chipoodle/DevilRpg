# Skills runtime — el humano con 0 de maná se convertía en hombre lobo (24-nov-2026)

**Rama:** `feature/autonomous-village`
**Código:** `161bc4f8`
**Origen:** auditoría de skills runtime (`2026-11-24-PENDING-AUDIT.md`, skills/runtime).

---

## 1 · Defecto

`ClientForgeResourceEventSubscriber.onPlayerTickResource` enviaba
`DirectSkillExecutionPayload(TRANSFORM_WEREWOLF)` **cada 10 ticks** cuando el maná llegaba a 0, **sin
mirar si el jugador estaba transformado**.

`SkillShapeshiftWerewolf.execute` **alterna** (`aux.setWerewolfTransformation(!transformation)`), y el
payload directo **no consume recursos ni comprueba precondiciones**. Resultado:

- un **humano** con 0 de maná podía quedar **convertido en hombre lobo** por la puerta trasera;
- el paquete se repetía cada 10 ticks mientras el maná siguiera a 0 (sonido y alternancia);
- la única intención legítima era **revertir** al lobo sin maná.

## 2 · Corrección

`WerewolfManaPolicy.debePedirReversion(transformado, mana, tickCount)`:

```java
return transformado && mana <= 0.0F && tickCount % 20 == 0;
```

- solo pide la reversión si el jugador **ya está transformado**;
- maná &le; 0;
- como mucho **un aviso por segundo** (tick múltiplo de 20).
- El cliente solo envía el payload y el sonido cuando la política lo permite; el servidor sigue siendo
  la autoridad.

## 3 · Evidencia

- 4 aserciones nuevas en `SKILL_CAPABILITY_INTEGRATION_TEST`:
  - transformado + 0 maná + tick 20 → **pide**;
  - transformado + 0 maná + tick 10 → no (throttle);
  - **humano + 0 maná + tick 20 → NO pide** (el bug);
  - transformado + 1 maná → no.
- `SKILL_CAPABILITY_INTEGRATION_TEST: OK (380 assertions)` (antes 376).
- `gradlew test ...`: **BUILD SUCCESSFUL** (1m19s); persistencia 553, ownership 59.

## 4 · Límites

- **No hay cliente real** en el arnés: la política se prueba headless y el cliente no se ejecuta; la
  verificación runtime con cliente (pantalla, HUD, doble `onKeyInput`, predicción local vs servidor)
  sigue pendiente.
- El doble `onKeyInput` **por diseño** (cliente predice cooldown/efectos visuales; servidor ejecuta y
  da autoridad) está documentado en los ejecutores con `isClientSide`; no se declara verificado en
  cliente real.
- Los 33 skills siguen cubiertos por catálogo/árbol/capability/NBT; el runtime jugable completo no.

## 5 · Veredicto

**Defecto real corregido y probado en política.** Skills runtime **avanzado**, no cerrado: falta el
cliente real. Siguen L5–L8, el resto de skills runtime y la decisión de ramas/A1.
