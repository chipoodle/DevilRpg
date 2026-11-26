# Asedio — ensanche desde la acción BREAK activa (26-nov-2026)

**HEAD de partida:** `59ddb53892e538164d491de60bc242d00490132e`  
**Commit de código:** `76cbc148e3680ae0e8da6e35da72c9324e9c8fa1`

## Defecto confirmado

La implementación anterior de `ensancharBrecha` existía, pero sus llamadas estaban en `BreakBlockGoal`:
- `BreakBlockGoal` y `TraverseGoal` ya no están registrados en `GoalSelector`.
- La navegación y las acciones de terreno reales pertenecen a `NavExecutor`.
- Su callback BREAK invoca `romperNodoDeRuta`.
- La rama especial de muro en `romperNodoDeRuta` destruía únicamente el nodo solicitado y llamaba `terrainChanged()`; no ensanchaba el hueco.

Por tanto, el arreglo geométrico estaba en una ruta de código inactiva. Eso explica que el código pareciera contener el ensanche y, aun así, los asediadores siguieran sin poder cruzar.

## Corrección

En la rama de rotura de muro de `romperNodoDeRuta`, después de destruir la primera columna:
1. Se calcula la dirección hacia la misión interior.
2. Se invoca `ensancharBrecha` con la normal de entrada; el método deriva la tangente.
3. La columna adyacente solo se rompe si sigue siendo parte del anillo real, no está protegida, no es obra del propio asedio y cumple las reglas existentes.
4. El ensanche consume el presupuesto `tunelRestante` existente; no se amplía el presupuesto ni se debilita la muralla.
5. Se invalida la ruta mediante `navExecutor.terrainChanged()`.

No se reactivaron goals legados ni se añadió un segundo propietario de movimiento.

## Rendimiento — evidencia y límite

El informe `2026-11-25-PERFORMANCE-SPIKES.md` documenta tres picos de aproximadamente 2 s:
- login/generación/carga;
- creación de ocho asediadores y sus primeras búsquedas de navegación en el mismo tick;
- guardado de tres niveles.

La campaña anterior eliminó trabajo por tick del leñador sin faena, pero dejó explícitamente pendiente repartir el nacimiento de la oleada. Este commit **no modifica todavía** el calendario de spawn ni el guardado: no se debe presentar el problema de rendimiento como corregido. Se requiere medir el siguiente pico y separar generación, oleada y guardado antes de tocar esas rutas.

## Validación

ChatGPT publicó el cambio, pero no ejecutó Gradle ni Minecraft runtime. Pendiente validar el SHA exacto `76cbc148e3680ae0e8da6e35da72c9324e9c8fa1`:
- compile, test/policyTest, lint strict y diff-check;
- inspección estática de que el ensanche se invoca desde la rama activa de `romperNodoDeRuta`;
- runtime con un asediador frente al mismo tipo de muro: abre dos columnas tangenciales, atraviesa físicamente y entra al recinto;
- comprobar que no rompe bloques interiores ni consume más del presupuesto existente.

No declarar aceptación runtime hasta observar el cruce real.
