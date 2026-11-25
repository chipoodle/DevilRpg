# Asedio — ensanche de brecha para que quepa el zombi (25-nov-2026)

**Rama:** `feature/autonomous-village`
**Código:** `098cf321`
**Origen:** un asaltante se quedó atascado en una brecha de **una columna**: el AABB de 0,6
solapaba con los bloques laterales y `NavObstruction.fitsActualAabb` lo rechazaba aunque el hueco
fuera nominalmente transitable.

## 1 · Cambio

`AggressiveZombieEntity.ensancharBrecha` abre una **columna tangencial contigua** a la brecha,
con las mismas reglas y presupuesto:

- solo si es **anillo real del muro** (`esLaMuralla`);
- nunca interior protegido de la aldea;
- nunca un tablón propio del asalto;
- consume el mismo `tunelRestante` que las demás roturas.

Se llama en los dos caminos:

- **marcha al centro** (`breakBrechaAhead`), tras abrir la primera columna;
- **combate** (`BreakBlockGoal`), tras romper el bloque que estorba.

## 2 · Evidencia

- `compileJava` y gate completo: **BUILD SUCCESSFUL** (1m26s); skills 416, persistencia 553,
  ownership 59.
- No hay corrida runtime nueva: el arreglo es geométrico y de presupuesto. Falta reproducir el
  asedio y comprobar que el asaltante cruza la brecha de dos columnas.

## 3 · Límites

- El comportamiento cambia solo en asedios con muro: se rompe **una columna más** de la misma
  pared, no terreno arbitrario.
- Si el presupuesto `tunelRestante` se agotó en la primera columna, no se ensancha; el asaltante
  seguirá sin pasar. Es intencional: no se regala presupuesto.
- No se tocó el ancho del muro, la colisión ni las reglas de protección.

## 4 · Estado

Commit **`098cf321`**; sin push. Runtime pendiente de la próxima prueba de asedio.
