# Skills — endurecimiento de seguridad cliente-servidor (25-nov-2026)

**Rama:** `feature/autonomous-village`  
**HEAD de implementación:** `2224c77a359434b23a8929a58e66e4fe60a34930`  
**Estado:** código publicado; validación de Harness/DeepSeek pendiente.  
**Pruebas ejecutadas por ChatGPT:** ninguna. No declarar PASS hasta que DeepSeek valide este SHA.

---

## 1. Correcciones implementadas

### Árbol y experiencia

- `PlayerSkillTreePayload` es ahora exclusivamente servidor → cliente (`playToClient`). El handler rechaza cualquier flujo distinto de `CLIENTBOUND`.
- El servidor ya no deserializa ni aplica el NBT completo enviado por el cliente.
- Las compras usan `SkillPointSpendPayload`, que solo transmite el identificador de una habilidad.
- El servidor valida que la habilidad exista en el árbol, tenga nivel actual válido, no haya alcanzado el máximo, tenga un punto de experiencia disponible y cumpla el requisito del ancestro visible inmediato (misma regla de desbloqueo que `SkillWidget.isDisabled`).
- El servidor consume el punto de experiencia, incrementa exactamente un nivel y sincroniza el árbol autoritativo.
- Los metadatos de la capability (máximos, costes, tipos de recurso, modificadores e iconos) ya no aceptan escrituras desde el cliente.

### Pasivos

- Se eliminó el payload C2S separado `PlayerPassiveSkillPayload`, que podía repetirse después de desbloquear una habilidad.
- El efecto pasivo se ejecuta dentro de la misma operación de compra validada por el servidor.
- `triggerPassive` rechaza habilidades activas, habilidades de minion, nodos no desbloqueados y ejecutores inexistentes.

### Loadouts

- Se añadieron acciones tipadas para asignar/desasignar una ranura (`SkillLoadoutPayload`) y rotar el loadout (`SkillSetRotatePayload`).
- El servidor solo permite asignar habilidades activas de jugador que ya tengan nivel; `EMPTY` permite retirar una asignación.
- La rotación acepta exclusivamente `-1` o `+1`.
- La UI mantiene una predicción local para responder inmediatamente; la sincronización S2C del servidor reemplaza esa copia.

### Transformación de hombre lobo

- El payload directo solo autoriza `TRANSFORM_WEREWOLF` con nivel desbloqueado, jugador ya transformado y maná agotado.
- El servidor vuelve a comprobar las precondiciones del ejecutor antes de ejecutarlo.
- La comprobación de dos argumentos que ya usaban pruebas antiguas se conserva como elegibilidad, pero el handler usa la política reforzada de cuatro argumentos.

## 2. Archivos principales

- `PlayerSkillTreePayload.java`
- `SkillPointSpendPayload.java` (nuevo)
- `SkillLoadoutPayload.java` (nuevo)
- `SkillSetRotatePayload.java` (nuevo)
- `DirectSkillExecutionPayload.java`
- `DirectSkillExecutionPolicy.java`
- `PlayerSkillCapabilityImplementation.java`
- `PlayerSkillCapabilityInterface.java`
- `SkillScreen.java`
- `ModNetwork.java`

## 3. Validación pendiente — DeepSeek/Harness

Ejecutar sobre el HEAD exacto `2224c77a359434b23a8929a58e66e4fe60a34930`:

1. `.\gradlew.bat compileJava compileTestJava --console=plain`
2. `.\gradlew.bat test --console=plain`
3. `.\gradlew.bat skillCapabilityTest --console=plain`
4. `python tools\lint_aldea.py --strict`
5. `git diff --check`

Pruebas específicas requeridas:

- Compra válida de un nodo raíz (las raíces visibles sin capability deben dejar pasar sus primeros nodos).
- Compra de hijo bloqueado; desbloqueo tras subir el padre; niveles máximos; cero puntos; mensajes repetidos y compra simultánea de nodos distintos.
- Verificar que el servidor rechaza nodos que no existen en el árbol o cuyos prerrequisitos no están cumplidos.
- Verificar que el pasivo se aplica una sola vez por compra validada, que un payload pasivo antiguo ya no está registrado y que activos/minions no se ejecutan como pasivos.
- Asignación de habilidades activas desbloqueadas, rechazo de pasivas/minions/bloqueadas, retirada con `EMPTY`, y rotación `-1/+1`; rechazar otros deltas.
- Matriz del payload directo: humano con maná cero, hombre lobo con maná positivo, hombre lobo con maná cero, nivel cero, skill distinta y precondición de montura.
- UI real: compra, actualización visual, loadouts, rotación, rechazo y resincronización S2C.
- Servidor dedicado y reconexión/guardado-recarga.

No reutilizar el PASS histórico de 392 aserciones como evidencia de estos nuevos handlers: esas pruebas anteriores cubrían políticas headless, no estos nuevos flujos de red.

## 4. Límites

- No se ejecutaron Gradle, cliente real ni servidor dedicado durante esta implementación.
- `BytesUtil` conserva serialización Java para datos de capability persistidos. La ruta de árbol C2S ya no acepta esos bytes ni deserializa NBT del cliente; el payload pasivo separado fue eliminado.
- La validación de requisitos se diseñó para reproducir la regla actual de `SkillWidget.isDisabled`; DeepSeek debe verificar con la matriz completa del JSON que no haya diferencias de jerarquía.
- Este documento supersede las afirmaciones previas de cierre de L9 en cuanto a la seguridad del protocolo C2S. El cierre queda pendiente de las pruebas anteriores.

## 5. Historial de commits

- `5e39bddbdebb0847c5e1a95c8ed37ccc03379373` — acciones tipadas y autoridad del servidor.
- `51006bddeabff1e19b7ae96e56d3c6074cfc8c22` — pasivos dentro de la compra validada; se retira el registro del payload independiente.
- `5e0548a1dd4e2015dec3089785616f4617bb507b` — requisitos de árbol y setters de metadatos solo servidor.
- `43eeac0fe8e5347be79725f472e52e7d3afc5a23` — compatibilidad de nodos raíz.
- `1b531ff6b9d6997b26c80f31214afb7809a125f5` — predicción UI de loadouts/rotación.
- `2224c77a359434b23a8929a58e66e4fe60a34930` — eliminación del payload pasivo obsoleto.
