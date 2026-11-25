# F3 — Puesto del fletcher sin POI: intermitente, 333 → 148–241 muestras (24-nov-2026)

**RUN_ID:** `av-f2-20261124-1825` / `av-f3-20261124-1835` / `av-f3-20261124-1915`
**Rama:** `feature/autonomous-village`
**Código del fallo:** `7e2b4293`
**Reparaciones:** `743b81db` (re-registro al generar) y `68741d29` (reemplazo + latido periódico)
**Origen:** `2026-11-24-F2-FALL-PROTECTION.md` §5.

---

## 1 · Síntoma

Los 72k de `7e2b4293` tienen **0 muertes** (0 hambre, 0 caída), comida 64–72, población 30, pero
fallan la cobertura:

| Run | `sin_puesto` | `sin_puesto_bajo_max` | `huerfanos_max` | Final |
|---|---:|---:|---:|---|
| `soak-f2-final` | 1 | **333** | 1 | 10/11 |
| `soak-f3-poi` (`743b81db`) | 1 | **148** | 1 | **11/11** |
| `soak-f3-periodic` (`68741d29`) | 1 | **241** | 1 | **11/11** |

El POI de la **mesa de flechas** (`610,63,508`, fletcher `e49a2bbc…`) falta/está mal durante las
primeras 148–241 muestras y luego se corrige solo; el 12k de la misma semilla pasa limpio. Es
**intermitente** y el final es 11/11 en los tres runs de la reparación.

## 2 · Reparación aplicada

- `VillageGenerator.asegurarElTallerDelLenador`: si la mesa ya es `FLETCHING_TABLE`, re-registra el POI
  (y lo reemplaza si el bloque y el POI no coinciden).
- `68741d29`: `VillageManager.manageNearby` llama a `asegurarPoiDelTallerDelLenador` **cada latido
  (200 ticks)**; el POI se añade/reemplaza si falta o es de otro tipo.

**Resultado:** el final pasa de 10/11 a **11/11** y el hueco baja de 333 a **148–241 muestras**, pero
**no se cierra**: el hueco es **inicial** y el 72k exige 11/11 en todas las muestras tras la gracia.
El reemplazo periódico no lo eliminó (241 > 148, dentro de la varianza).

## 3 · Lo que queda (F3)

El siguiente paso es **identificar qué profesional** es el `sin_puesto` y por qué su `JOB_SITE` no es
válido hasta ~tick 32k. Hay que capturarlo con un 72k `-Diag` y el log `SOAK_POI_INVALID` (UUID,
profesión, `JOB_SITE`, POI y bloque). Dos hipótesis:

- un **adulto nuevo** con oficio del pueblo y sin estación, que el censo debería pasar a NIL/guardia;
- el fletcher, cuyo `JOB_SITE` apunta a una celda sin el POI correcto hasta que una migración/reclamo
  lo arregla.

No se declara el soak aceptado hasta un 72k con 11/11 desde la primera muestra válida.

## 4 · Evidencia

- `soak-f2-final`: **0 muertes**, 30 de población, comida 72, `sin_puesto` 1/333, final 10/11.
- `soak-f3-poi`: **0 muertes**, 30 de población, comida 64, `sin_puesto` 1/148, **final 11/11**.
- `soak-f3-periodic`: **0 muertes**, 30 de población, comida 100, `sin_puesto` 1/241, **final 11/11**,
  0 duplicados, 0 inactivos permanentes, guardado OK.
- `soak-poi-f3` (12k, misma semilla): `SOAK_PASS`, 11/11 — el fallo no es determinista en 12k.
- Gates: `test` 3m11s / 4m20s; persistencia 553, ownership 54, skills 376; lint/diff verdes.

## 5 · Veredicto

**F1 y F2 cerrados. F3 abierta.** La cobertura final es 11/11 y no hay muertes; el único criterio
pendiente es la **cobertura inicial**. No se declara el soak aceptado.
