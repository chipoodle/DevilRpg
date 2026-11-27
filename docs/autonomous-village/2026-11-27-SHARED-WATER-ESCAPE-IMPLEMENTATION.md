# Rescate de guardias mediante recuperación compartida de agua — 27-nov-2026

## Estado

**IMPLEMENTADO EN PRODUCCIÓN; VALIDACIÓN PENDIENTE.**

- Rama: \`feature/autonomous-village\`
- Base inspeccionada: \`8b335d024b257bfbbe6bdcf87b67e84ce2e85e8e\`
- Último commit de la serie de código: \`965e60712b177ddccc01d1ad6e15efc4e556b251\`
- RUN 013 anterior: no se ejecutó. Este handoff lo sustituye; no iniciar aquel prompt.

## Decisión e implementación

Se reutilizó el comportamiento ya existente en \`EscapeWaterGoal\` sin copiarlo a un segundo goal de guardia ni entregar la navegación del aldeano a \`NavExecutor\`.

### Núcleo compartido

Nuevo archivo: \`src/main/java/com/chipoodle/devilrpg/entity/WaterEscapeController.java\`.

Comparte:
- Detección de atasco horizontal: 8 ticks.
- Permanencia en agua sin acercarse al objetivo: 60 ticks.
- Reselección tras 80 ticks sin progreso y recálculo a los 200 ticks.
- Búsqueda en espiral hasta radio 16, evitando repetir orillas dentro del episodio. Para guardias se explora el suelo entre -2 y +1 bloques respecto a los pies (permite una orilla con los pies hasta +2); el adaptador zombie conserva exactamente su rango histórico -1..0.
- Presupuesto finito de 4 intentos mediante \`TacticalEscapeBudget\`.
- Impulso físico cada 6 ticks: 0.4 horizontal hacia la orilla y 0.5 vertical.
- Búsqueda y validación desacopladas mediante predicados, para que cada entidad aporte sus propias reglas.

El controlador no es un \`Goal\`, no llama a \`Navigation.moveTo\`, no teletransporta y no coloca ni rompe bloques. La navegación se delega a un callback del propietario.

### Zombies agresivos

\`AggressiveZombieEntity.EscapeWaterGoal\` delega ahora al núcleo compartido:
- búsqueda en espiral;
- impulso físico;
- umbrales compartidos y presupuesto de intentos.

Se conserva el predicado original \`isLand\`, el radio 16, el orden de la espiral y los valores del impulso. El goal zombie conserva su lógica específica de misión, \`NavExecutor\`, \`romperRecuperacionHacia\` y máximo de tres roturas por intento. La intención es no cambiar el comportamiento A2; la regresión de agua de zombies debe comprobarse sobre el nuevo SHA.

### Guardias

\`VillagerGuardGoal\` contiene una instancia del controlador dentro del mismo goal existente:
- El callback de navegación usa exclusivamente \`VillageManager.caminarHacia(villager, shore, VELOCIDAD)\`, que escribe el \`WALK_TARGET\` del Brain.
- No se registra un segundo goal MOVE y el guardia no usa \`NavExecutor\`.
- La orilla debe tener chunks ya cargados, pies/cabeza/volumen superior secos y libres, soporte no fluido y AABB completa sin colisión.
- No se editan bloques durante esta fase de agua.
- Al salir del agua, el controlador termina el episodio y el movimiento normal vuelve al destino estratégico.
- Combate, equipamiento y marcha cancelan la recuperación de agua.
- Si no existe orilla segura o se agotan los intentos, la recuperación de terreno existente queda como respaldo. Si esa recuperación ya está activa, el controlador de agua se cancela para que no compitan ni se intercalen acciones.

La identidad persistente de milicia, \`GUARD_RETURNING_TAG\`, el retorno a calle y la reintegración no se modificaron.

## Archivos

- Nuevo: \`src/main/java/com/chipoodle/devilrpg/entity/WaterEscapeController.java\`
- Modificado: \`src/main/java/com/chipoodle/devilrpg/entity/AggressiveZombieEntity.java\`
- Modificado: \`src/main/java/com/chipoodle/devilrpg/entity/goal/VillagerGuardGoal.java\`
- Nuevo: \`src/test/java/com/chipoodle/devilrpg/entity/WaterEscapeControllerTest.java\`
- Modificado: \`build.gradle\` para registrar el test en \`policyTest\`.

La prueba nueva cubre selección del siguiente rellano válido, exclusión de orillas intentadas, límite de radio, agotamiento de candidatos y el rango vertical elevado de guardias frente al rango histórico de zombies.

## Revisión de invariantes

- No se crea otro goal ni otra autoridad de movimiento para guardias.
- No hay \`Navigation.moveTo\`, teletransporte, cambios de posición ni manipulación de Brain fuera de \`VillageManager.caminarHacia\`.
- No se debilitan soporte, colisión, protección, presupuestos ni validaciones de \`GuardTerrainRecovery\`.
- No se alteran el rescate persistente, las etiquetas de milicia ni la prioridad de combate/equipamiento/marcha.
- No se cambian las reglas de A2/C4. La ruta zombie comparte helpers y constantes con los mismos valores; se requiere prueba de regresión.

## Validación

ChatGPT no ejecutó Gradle, compilación, tests, lint ni Minecraft runtime. El test fue añadido, no ejecutado. No declarar PASS.

## Handoff obligatorio a Harness

1. Sincronizar de forma segura la rama y verificar que el HEAD contiene \`965e60712b177ddccc01d1ad6e15efc4e556b251\` y este informe. No usar reset/clean/stash ni sobrescribir trabajo local.
2. Ejecutar \`compileJava\`, \`compileTestJava\`, \`test\`, \`policyTest\`, lint estricto y diff-check; registrar comandos, exit codes y SHA probado.
3. Solo si el gate estático pasa, reproducir escenario A de Ximeno en una copia NUEVA y desechable del mundo.
4. Medir posición inicial/final, delta físico, ticks hasta salir del agua, destino estratégico, \`WALK_TARGET\`, estado de navegación y trazas \`[GuardWaterEscape]\`. Confirmar que el guardia alcanza terreno seco, vuelve a su destino de calle y posteriormente puede reintegrarse sin perder identidad.
5. Confirmar que el controlador de agua no coloca bloques. En el caso de Ximeno, comprobar que una orilla natural hasta dos bloques por encima puede ser elegida si su AABB/soporte son válidos. Si no encuentra orilla segura, verificar que falla cerrado y que el fallback \`GuardTerrainRecovery\` conserva sus reglas y puede operar sin competencia.
6. Verificar controles de prioridad: combate, equipamiento y marcha cancelan/pausan el rescate; el guardia sano sigue patrullando.
7. Ejecutar una prueba focalizada de salida del agua de zombie para comprobar que el refactor conserva el comportamiento previo. No lanzar una campaña completa A2 ni cambiar criterios; C4/A2 se mantienen como contratos protegidos.
8. Probar negativos de orilla: chunks no cargados, lava/fluido no acuoso, pies/cabeza ocupados, falta de soporte y AABB bloqueada. Registrar cada rechazo y que no se edite terreno.
9. Preservar RUN 012, su mundo original, copias y logs. No reutilizar ni sobrescribir el mundo original.

Harness solo ejecuta y documenta; no modifica producción/tests/docs ni crea commits/push. Sin teletransporte ni edición artificial de posición/Brain/tags.

**Resultado esperado de esta fase:** evidencia runtime reproducible del escape de Ximeno, además de regresión focalizada del mecanismo zombie. Un PASS estático no sustituye el escenario runtime.
