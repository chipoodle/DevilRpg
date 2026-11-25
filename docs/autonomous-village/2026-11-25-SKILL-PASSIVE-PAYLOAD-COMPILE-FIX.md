# Fix de compilación: reaplicación de pasivos sin payload eliminado — 25-nov-2026

## Origen

DeepSeek validó el commit `611d3d340b0ea708e0cd3145ce7f370e7c9a3403` y encontró dos referencias a `PlayerPassiveSkillPayload` que impedían `compileJava`:

- Import obsoleto en `PlayerCapabilityForgeEventSubscriber.java`.
- Uso obsoleto en `onPlayerChangedDimension`.

La clase había sido eliminada intencionalmente en el hardening anterior porque la activación pasiva no debe disponer de un payload C2S independiente.

## Corrección aplicada

Commit: `eea975ca152d1068e95ba2e61c2122eed5bdee21`

Archivo:
`src/main/java/com/chipoodle/devilrpg/eventsubscriber/common/PlayerCapabilityForgeEventSubscriber.java`

Cambios:

1. Eliminado el import de `PlayerPassiveSkillPayload`.
2. Eliminado el empaquetado de skill a `CompoundTag` para enviarlo al propio servidor.
3. El evento ignora el lado cliente.
4. En servidor, obtiene la capability y reaplica cada pasivo no-minion mediante `triggerPassive(player, skill)`.
5. Se conserva la lógica existente que mueve minions persistentes al jugador en la dimensión destino.

La reaplicación utiliza la API de capability existente, que verifica que la skill sea pasiva, no sea de minion, no sea hija de una skill activa y tenga nivel desbloqueado. No se reintroduce un mensaje de red ni una vía de autorización adicional.

## Validación

ChatGPT inspeccionó el archivo actualizado y confirmó que ya no contiene referencias a `PlayerPassiveSkillPayload`.

No se ejecutaron compilación, tests, lint ni pruebas runtime. No declarar PASS.

## Instrucciones para DeepSeek

Validar el SHA nuevo exacto:

`eea975ca152d1068e95ba2e61c2122eed5bdee21`

Primero ejecutar compileJava/compileTestJava. Si compila, continuar con test, skillCapabilityTest, lint strict y diff check. Después repetir las pruebas funcionales del informe `2026-11-25-SKILL-DATA-DRIVEN-RULES.md`, incluyendo cambio de dimensión y reaplicación de pasivos.

Confirmar especialmente:

- No existe import ni uso de `PlayerPassiveSkillPayload` en código de producción.
- La activación pasiva tras cambio de dimensión ocurre una sola vez por evento y únicamente en servidor.
- Pasivos bloqueados, minion y descendientes de activos no se activan indebidamente.
- Los efectos pasivos se mantienen correctos tras cambio de dimensión.
- Los minions siguen acompañando al jugador.
- No se reintrodujo ningún payload C2S para activar pasivos.

Harness no debe modificar producción. Registrar SHA, comandos, logs y resultados.
