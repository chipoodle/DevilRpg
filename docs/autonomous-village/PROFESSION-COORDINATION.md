# Coordinación compartida de profesiones y planificadores individuales

**Estado:** contrato arquitectónico + primer slice de agricultura, 28-oct-2026.
**No** hay aceptación runtime en este documento.

## Nivel 1 · Coordinador compartido por aldea y profesión

Para agricultura hay **un** coordinador lógico por aldea, no uno por granjero.

- Descubre parcelas con `VillageGenerator.parcelasDeGranja()` y la cota de la plaza.
- Reparte parcelas entre los granjeros disponibles.
- Guarda la asignación en el aldeano (`DevilRpgFarmPlot`) usando el índice estable
  de parcela, no una coordenada suelta.
- Antes de confirmar una parcela comprueba que ningún otro granjero vivo de la
  misma aldea tenga esa parcela.
- Prioriza la parcela del puesto de trabajo (`JOB_SITE`/compostero) si no tiene
  dueño; si no, la primera libre; si no hay libres, reparto estable por UUID.
- No ejecuta trabajo, no mueve aldeanos, no concede XP.
- El estado se deriva de aldeanos/parcelas de **esa** aldea; no hay singleton
  global que mezcle aldeas.

### Identidades separadas

1. Aldea: `center`/`objectiveIndex` existentes.
2. Coordinador: función por aldea+profesión; no se usa como identidad de tarea.
3. Parcela: índice estable de `VillageGenerator`.
4. Asignación: parcela + granjero, guardada en datos del aldeano.
5. Granjero: UUID.
6. Recado: `DevilRpgRecadoId`.
7. Destino de ruta: `BlockPos` del recado.

Las coordenadas solas no identifican una asignación.

## Nivel 2 · Planificador individual del granjero

`VillagerFarmGoal` conserva su estado y decide la acción local:

- trabaja en su parcela asignada;
- cosecha, replanta, abona, recoge y entrega según el estado real;
- espera/descansa si no hay trabajo inmediato, sin recados equivalentes en bucle;
- cede ante sueño, amenaza, combate y supervivencia;
- si la parcela deja de ser válida, puede volver a pedir asignación al
  coordinador.

No reclama una parcela ocupada ni sobrescribe la asignación de otro granjero.

## Ejemplo de tres granjeros

Tres granjeros y tres parcelas válidas producen tres asignaciones no conflictivas.
Cada granjero decide después qué acción local ejecutar. Si uno duerme o responde
a una amenaza, su asignación sigue válida; si muere, desaparece, cambia de oficio
o la parcela deja de ser usable, el coordinador puede reasignar sin crear dos
dueños. No hay un límite fijo de tres.

## Primer slice implementado

- `VillageFarmCoordinator`: asignación/consulta/liberación de parcela.
- `VillagerFarmGoal.miParcela`: consulta primero al coordinador y conserva el
  fallback histórico si no hay parcelas.
- Sin cambios de trabajo, XP, navegación ni otras profesiones.

## Pendiente / límites

- Aceptación runtime pendiente: tres granjeros, tres parcelas, sin duplicados,
  trabajo sostenido, reasignación por muerte/ausencia.
- Persistencia del coordinador: la asignación vive en el aldeano; no se ha
  probado guardado/re carga.
- Invalidez por parcela destruida: el slice comprueba índice/conflicto, no una
  validación completa de todos los bloques de la parcela.
- No se ha generalizado a otras profesiones.

## Coordinación y navegación (28-oct-2026)

La asignación de parcela sigue en `VillageFarmCoordinator`; la navegación propia
NO se usa para `farm:*`. Esa tarea vuelve siempre al fallback del granjero. El
controlador opt-in queda para tareas no agrícolas. Contrato futuro de handoff:
TASK_SELECTED → NAVIGATION_OWNED → WORK_POSITION_REACHED → ACTION_EXECUTABLE →
TASK_COMPLETED; llegada no es finalización.
