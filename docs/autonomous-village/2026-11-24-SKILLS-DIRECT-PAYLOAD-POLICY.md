# Skills runtime — el payload directo ejecutaba cualquier skill (24-nov-2026)

**Rama:** `feature/autonomous-village`
**Código:** `a06c906d`
**Origen:** auditoría de skills runtime; continuación de I416.

---

## 1 · Defecto

`DirectSkillExecutionPayload.handle` ejecutaba en el servidor **cualquier** `SkillEnum` que mandara el
cliente:

```java
AbstractSkillExecutor loaded = cap.getLoadedSkillExecutor(msg.skill());
loaded.execute(sender.level(), sender, null);
```

Sin nivel, sin coste, sin cooldown ni precondiciones: el único uso legítimo del payload directo es la
**reversión del hombre lobo** a 0 maná. Un cliente modificado podía invocar lobos, cargar, etc. **gratis**
por la puerta trasera.

## 2 · Corrección

`DirectSkillExecutionPolicy.puedeEjecutar(skill, nivel)`:

```java
return skill == SkillEnum.TRANSFORM_WEREWOLF && nivel > 0;
```

`DirectSkillExecutionPayload.handle` lee el nivel real de la capability y, si no pasa, **rechaza** con
log `[Skill] payload directo rechazado`. También comprueba `getLoadedSkillExecutor != null`.

## 3 · Evidencia

- 4 aserciones nuevas en `SKILL_CAPABILITY_INTEGRATION_TEST`:
  - transformar con nivel 1 → permitido;
  - transformar sin nivel → rechazado;
  - invocar lobo por payload directo → rechazado;
  - skill nula → rechazada.
- `SKILL_CAPABILITY_INTEGRATION_TEST: OK (384 assertions)` (antes 380).
- `gradlew test ...`: **BUILD SUCCESSFUL** (1m18s); persistencia 553, ownership 59.

## 4 · Límites

- No hay cliente real: la política y el handler se prueban headless; el flujo cliente→servidor de la
  reversión del lobo no se ejecuta con un cliente real.
- El resto del runtime de skills (UI/HUD, doble `onKeyInput`) sigue pendiente de cliente.

## 5 · Veredicto

**Cierre de la puerta trasera del payload directo**: solo la reversión del lobo, con nivel, puede
ejecutarse sin las comprobaciones de `triggerAction`. Skills **384** aserciones. Siguen L7, cliente
real y la decisión de ramas/A1.
