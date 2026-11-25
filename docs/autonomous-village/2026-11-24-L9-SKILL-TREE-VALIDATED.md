# L9 — El árbol de skills y la experiencia: gasto validado en servidor (24-nov-2026)

**Rama:** `feature/autonomous-village`
**Código:** `20d9c517`
**Origen:** `2026-11-24-MANA-STAMINA-SERVER-AUTHORITY.md` §4.

---

## 1 · Defecto

Los dos últimos payloads con acción legítima del cliente aceptaban el **NBT completo**:

- `PlayerSkillTreePayload`: el servidor deserializaba el árbol que mandaba el cliente (niveles,
  puntos, poderes) sin comprobar el gasto;
- `PlayerExperiencePayload`: el cliente mandaba su experiencia/puntos, incluidos puntos **de más**;
- `PlayerPassiveSkillPayload`: ejecutaba un pasivo sin comprobar que el árbol le hubiera dado nivel.

Un cliente modificado podía **regalarse puntos y skills**.

## 2 · Corrección

- **Experiencia S→C** (`playToClient`): el cliente ya no escribe su experiencia; el servidor la
  manda.
- **Árbol validado** con `SkillTreeSpendPolicy`:
  ```java
  permite(antes, despues, sinGastar):
      despues == antes  (recolocar poderes, permitido)
      || (despues == antes + 1 && sinGastar > 0)  // gastar un punto
  ```
  nunca se permite bajar el total ni subir más de un punto por paquete.
- `PlayerSkillTreePayload` (C2S) deserializa el árbol en una **copia temporal**, suma los puntos y:
  - si el total sube 1 y hay punto sin gastar, **el servidor consume el punto**
    (`exp.consumePoint(serverPlayer)`) y aplica el árbol;
  - si no pasa la política, **rechaza** y reenvía al cliente el árbol y la experiencia autoritativos.
- `triggerPassive` solo ejecuta un pasivo si el árbol del servidor le dio **nivel > 0**.

## 3 · Evidencia

- `SkillTreeSpendPolicy`: 5 aserciones nuevas (subir 1 con punto, sin punto, subir 2, mismo total,
  bajar total) → `SKILL_CAPABILITY_INTEGRATION_TEST: OK (392 assertions)`.
- `gradlew test ...`: **BUILD SUCCESSFUL** (1m22s); persistencia 553, ownership 59.

## 4 · Límites

- No hay cliente real: la UI de skills no se ejecuta; la validación y el reenvío se prueban a nivel de
  política/handler.
- El árbol sigue viajando como NBT completo (validado por delta), no como una acción por nodo. Un
  cliente puede **recolocar** poderes (mismo total) pero **no regalarse puntos**; una acción por nodo
  sería más estricta y queda como mejora futura.
- La doble ejecución de `onKeyInput` en cliente real sigue sin verificarse.

## 5 · Veredicto

**L9 cerrada en política/handler:** experiencia S→C, árbol con gasto validado y servidor que consume
el punto, pasivo con nivel. **Skills 392.** Quedan L7, L8 (excluido), cliente real y la decisión de
ramas/A1.
