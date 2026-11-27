# Guard return and militia reintegration — 27-nov-2026

- **Production implementation commit:** `39229f5dc45c2234bb65559251d33aeb42735c67`.

## Objetivo

Un guardia que perdió el acceso a un puesto de patrulla no debe quedar inactivo porque `puntoDeGuardia()` devuelve null. Debe recuperar terreno seguro y regresar a la aldea; no necesita alcanzar el centro ni recibir un puesto ficticio. Una vez dentro, el reparto ordinario decide si conserva plaza militar o vuelve a cubrir un oficio.

## Decisión de arquitectura

No se crea un segundo goal de movimiento. El rescate vive dentro de `VillagerGuardGoal`, que ya posee las flags MOVE/LOOK y es el único goal de guardia. Así no hay dos goals de rescate/patrulla reclamando MOVE ni dos autoridades de navegación.

El estado de retorno se persiste en `VillageManager.GUARD_RETURNING_TAG` (`DevilRpgGuardiaRegresando`) en PersistentData. Los goals no se serializan; el goal existente vuelve a asegurarse por el mecanismo normal de alistamiento al cargar/reconciliar la aldea, y `canUse()` rehidrata el estado desde la etiqueta.

## Activación

El guardia entra en retorno si no hay un puesto válido y su celda actual es insegura (pies/cabeza con fluido, ocupada o sin soporte) o está fuera del perímetro. Además, si un puesto seleccionado no tiene ruta alcanzable y el guardia acumula 60 ticks sin progreso con el Brain apuntando a ese puesto, abandona esa patrulla y cambia el objetivo a la calle. No construye hacia una patrulla inalcanzable.

Una etiqueta de retorno ya persistida mantiene el estado a través de guardados. El retorno puede arrancar aunque el guardia esté en turno ordinario de descanso; ese horario no puede dejar abandonado a un guardia varado.

No se inicia el retorno si hay marcha activa: la rama de marcha conserva prioridad. El combate sigue siendo lo primero dentro de `tick()`; equipamiento conserva su rama y puede pausar el retorno. La recuperación de terreno activa se cancela si comienza equipamiento o marcha, según el contrato previo.

## Ruta de regreso

- Objetivo: `VillageManager.casillaDeLaCalle(level, center)`, no el centro geométrico ni un puesto de patrulla.
- Todos los desplazamientos siguen pasando por `VillageManager.caminarHacia`; no se usa `Navigation.moveTo` ni teletransporte.
- Si no hay ruta natural y el guardia lleva 60 ticks sin progreso con el Brain apuntando al objetivo, primero cambia el destino al punto de calle. Entonces se reutiliza `GuardTerrainRecovery` para buscar un rellano seguro orientado hacia la calle.
- La recuperación existente conserva sus límites de subida, profundidad, presupuesto de bloques, cadencia, protección, validación de chunks, block entities y soporte.
- Si no hay plan válido, falla cerrado y reintenta con backoff; no rompe bloques ni fuerza una ruta.

## Coordinación con el reparto

Mientras `GUARD_RETURNING_TAG` esté activo:
- el guardia no entra en la lista de candidatos de milicia dinámica;
- el cleanup de guardias que ya no sobran no lo deslista;
- conserva `GUARD_TAG`, tipo, índice, equipo, atributos y marca sincronizada;
- no ocupa una plaza dinámica nueva ni se reasigna a otra plaza durante el retorno.

Al llegar a una casilla segura dentro del muro y cerca de la calle:
- se elimina la etiqueta persistente;
- el goal deja de considerar al aldeano en retorno;
- se vuelve a calcular el puesto de patrulla; si no hay uno, el goal termina sin inventar destino;
- el siguiente reparto periódico aplica la política normal de oficios/plazas. Por tanto, rescatar no garantiza que siga siendo soldado: eso lo decide la población y los cupos existentes.

## Prioridades y arbitraje

| Actividad | Comportamiento |
|---|---|
| Huida vanilla / emergencia | Sigue por delante del goal SECURITY |
| Combate | Se procesa antes del movimiento de retorno en el mismo goal |
| Marcha a guarida | La rama de marcha sale antes de seleccionar retorno |
| Equipamiento | Puede sustituir temporalmente el destino y pausar/cancelar recovery |
| Entrenamiento | Se omite mientras `regresandoAlPueblo` está activo |
| Retorno | Mismo goal SECURITY=3; sin goal competidor |
| Patrulla | Solo vuelve después de confirmar terreno seguro dentro del muro |
| Profesiones | SECURITY sigue delante de PROFESSION; reparto no reasigna al guardia durante retorno |

## Evidencia previa que motiva el cambio

- Run 008 mostró fallback al blockPosition en agua y falsa llegada a menos de REACH.
- Run 009 mostró `canUse=false`, destino null y WALK_TARGET vacío.
- Run 010 identificó celdas de rellano candidatas, pero no probó plan completo.
- Run 011 confirmó que el centro no era el problema: distancia 72.508 frente a límite 88; seguía `canUse=false` por destino null. Run 011 reutilizó el mundo de Run 010.

## Archivos de producción modificados

- `VillageManager.java`: etiqueta persistente; exclusión temporal del reparto dinámico y del cleanup de bajas.
- `VillagerGuardGoal.java`: detección/rehidratación del retorno; prioridad frente al descanso ordinario; destino de calle; confirmación de retorno seguro; reintegración al reparto.
- `GuardTerrainRecovery.java`: no se modificó; se reutiliza su planificador y límites existentes.

## Validación pendiente

ChatGPT no ejecutó Gradle, tests, lint, servidor ni cliente Minecraft. No declarar PASS.

Harness debe validar el SHA final del handoff, en una copia NUEVA del mundo:
1. Caso Ximeno/agua con destino de patrulla null.
2. Retorno por ruta natural cuando exista; solo usar rampa si no hay ruta natural alcanzable.
3. Evidencia de `[GuardReturn]`, `[GuardTerrain]`, WALK_TARGET, coordenadas, bloques y llegada a calle segura.
4. Reparto periódico durante rescate sin cambiar GUARD_TAG/tipo/índice ni desalistar.
5. Al llegar dentro del muro, limpiar tag y dejar que el siguiente reparto determine la asignación.
6. Save/reload durante retorno: PersistentData conserva etiqueta y goal se reasegura.
7. Combate, equipamiento y marcha mantienen prioridad; turnos de descanso no bloquean rescate.
8. Casos negativos: sin calle cargada, chunks no cargados, protección, lava, bloque sólido, block entity, falta de soporte o plan inválido; fallo cerrado, sin teletransporte ni bloques no permitidos.
9. Verificar que guardias sanos dentro del perímetro siguen su patrulla normal y que A2/C4 no cambian.

