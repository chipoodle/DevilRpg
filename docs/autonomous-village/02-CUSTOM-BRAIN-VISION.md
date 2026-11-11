# Visión: cerebro autónomo propio

**Estado:** documentado, NO implementado. Documento de dirección; no autoriza un
rewrite inmediato.

## Objetivo

Sustituir las limitaciones del Brain/Goal de vanilla con una arquitectura propia,
extensible y compartida, sin perder los componentes ya probados.

## Requisitos

1. **Percepción / snapshot acotado** del mundo: entorno, amenazas, recursos,
   obras y estado de la aldea, con límites de coste.
2. **Objetivos estratégicos** con identidad estable y condiciones de éxito
   explícitas; no confundir objetivo con waypoint.
3. **Arbitraje de prioridad** e interrupción/reanudación táctica (sueño, amenaza,
   combate, refugio).
4. **Asignación coordinada** de tareas entre aldeanos (coordinador compartido por
   aldea/profesión).
5. **Propiedad de movimiento** y ejecución de oficio sin Goals en conflicto.
6. **Evaluación de progreso**, memoria de recuperación y alternativas tras fallo.
7. **Identidad persistente** de aldea, necesidades, roles y asignaciones.
8. **Extensión** a nuevas profesiones y conductas sin parches por síntoma.

## Fronteras

- El coordinador comparte asignaciones; no ejecuta trabajo ni mueve entidades.
- El planificador individual elige la acción local.
- El controlador de navegación posee la ruta solo durante una tarea válida.
- El goal de oficio ejecuta la acción y concede XP.
- La llegada a un waypoint **no** es finalización de la tarea.
- La física, colisiones, gravedad y salto de Minecraft se conservan inicialmente.

## Estado actual y deuda

- Existe `VillageTaskNavigationPolicy` y `VillageNavigationGoal` opt-in.
- La navegación propia se excluye de agricultura; el A/B de minería mostró que
  tampoco es segura para tareas de posición de trabajo.
- No existe todavía un cerebro global con snapshot/objetivos/arbitraje propio.
- **Requiere diseño:** modelo de objetivos, percepción, persistencia, migración y
  criterios de aceptación por fases.
