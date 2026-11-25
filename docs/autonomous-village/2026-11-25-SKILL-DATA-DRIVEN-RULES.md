# Skill rules data-driven y validación server-authoritative — 25-nov-2026

## Estado

Implementación publicada en `feature/autonomous-village`.

- Commit: `361aab948117ed62a5b24dbc6f09d6af4e21ec39`
- HEAD verificado al cierre: `361aab948117ed62a5b24dbc6f09d6af4e21ec39`
- Pruebas: NO ejecutadas por ChatGPT. DeepSeek/Harness debe validar exactamente este SHA.

## Cambios

### 1. Progreso separado de reglas

`PlayerSkillCapabilityImplementation.getMaxSkillsPoints()`, `getResourceCostPoints()` y `getResourceType()` ahora construyen sus mapas desde `clientBuilder.getAll()`, cuyo contenido se carga desde `assets/devilrpg/skills/root_complete.json`.

- El NBT del jugador sigue almacenando los niveles que ha comprado.
- Los máximos, costes y tipos de recurso ya no se leen como reglas desde el NBT persistido.
- Cambiar `skillmanacost.maxskilllevel` se refleja para jugadores nuevos y existentes cuando el servidor/capability carga la versión actual del mod; no se borra ni reajusta el progreso comprado.
- Cambiar `manacost` o `resourcetype` también cambia la regla efectiva al cargar la versión nueva.
- Los setters de máximo/coste/tipo se mantienen por compatibilidad de interfaz, pero no pueden sobrescribir el JSON. Emiten warning.
- Los campos NBT históricos se conservan por compatibilidad de guardados, pero los getters ya no los consideran autoritativos.

No hay hot reload del JSON dentro de una sesión: al cambiar el archivo dentro del mod, se requiere reiniciar el servidor/juego para cargar el nuevo recurso.

### 2. Política de acción directa aislada del cliente

Se creó `skillsystem.server.SkillServerPolicy`. Esta clase no importa GUI, widgets ni clases de cliente. Lee directamente el recurso del mod:

`assets/devilrpg/skills/root_complete.json`

La habilidad Werewolf declara:

```json
"directexecution": {
  "trigger": "resource_depleted",
  "resource": "mana",
  "threshold": 0.0001,
  "requires_transformation": true
}
```

El cliente solo envía el enum de la skill. En el servidor, `DirectSkillExecutionPayload` consulta las capabilities del jugador y `SkillServerPolicy` comprueba:

1. La skill existe y no es EMPTY.
2. El nivel real del jugador es mayor que cero.
3. Existe una regla `directexecution` para esa skill en el JSON.
4. El trigger es soportado (`resource_depleted`).
5. El recurso es soportado (`mana`).
6. Si la regla lo exige, el jugador está transformado.
7. El maná del servidor es finito y menor o igual al umbral del JSON.

Cualquier regla ausente, trigger/recurso desconocido, nivel inválido o valor no finito falla cerrado. El umbral ya no está codificado en `DirectSkillExecutionPolicy` ni se recibe del cliente. La clase anterior queda como fachada de compatibilidad para tests/callers antiguos.

El decoder del payload convierte enums inválidos a `SkillEnum.EMPTY`, que el handler rechaza.

### 3. Alcance de la separación

Se separó la política de autorización de acciones directas de la GUI y del payload. La construcción visual del árbol y sus modelos existentes se mantienen en el builder común que ya utiliza la capability; esta entrega no pretende migrar todas las clases `SkillElement`/GUI a otro paquete.

## Validación requerida por DeepSeek

Ejecutar sin modificar código de producción y reportar SHA exacto:

1. `.gradlew.bat compileJava compileTestJava --console=plain`
2. `.gradlew.bat test --console=plain`
3. `.gradlew.bat skillCapabilityTest --console=plain`
4. `python tools\lint_aldea.py --strict`
5. `git diff --check`

Pruebas funcionales específicas:

- Crear mundo/partida con máximo JSON 20: validar que el getter y UI muestren 20.
- Cambiar `maxskilllevel` a 30, reiniciar/cargar con esa versión y probar partida nueva: debe mostrar/aceptar hasta 30.
- Cargar una partida ya existente con progreso, por ejemplo 7/20, bajo JSON máximo 30: debe mostrar 7/30, preservar nivel 7 y permitir comprar hasta 30.
- Reducir máximo por debajo del nivel actual: no borrar ni truncar progreso; bloquear compras nuevas hasta que el nivel sea menor que el máximo.
- Cambiar `manacost` y `resourcetype`: comprobar que getters/runtime usan valores JSON nuevos, no valores antiguos del NBT.
- Confirmar que llamadas a setters legacy no alteran reglas.
- Matriz de directexecution: skill con regla y sin regla; skill equivocada; nivel 0; transformado/no transformado; maná mayor, igual y menor que threshold; NaN/infinito; payload enum inválido.
- Confirmar que el cliente no puede indicar estado de transformación, maná ni threshold y que todo se lee en servidor.
- Cliente real, servidor dedicado, conexión, desconexión y guardar/reload.
- Verificar que no se rompen tests existentes de `DirectSkillExecutionPolicy`.

No modificar producción desde Harness. Registrar logs, resultados y SHA exactos. No declarar PASS si el runtime dedicado o las pruebas funcionales no se ejecutan.

