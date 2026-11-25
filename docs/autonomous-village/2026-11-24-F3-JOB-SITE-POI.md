# F3 — Puesto del fletcher sin POI: intermitente, 333 → 148 muestras (24-nov-2026)

**RUN_ID:** `av-f2-20261124-1825` / `av-f3-20261124-1835`
**Rama:** `feature/autonomous-village`
**Código del fallo:** `7e2b4293`
**Código de la reparación:** `743b81db`
**Origen:** `2026-11-24-F2-FALL-PROTECTION.md` §5.

---

## 1 · Síntoma

Los 72k de `7e2b4293` tienen **0 muertes** (0 hambre, 0 caída), comida 64–72, población 30, pero
fallan la cobertura:

| Run | `sin_puesto` | `sin_puesto_bajo_max` | `huerfanos_max` | Final |
|---|---:|---:|---:|---|
| `soak-f2-final` | 1 | **333** | 1 | 10/11 |
| `soak-f3-poi` (`743b81db`) | 1 | **148** | 1 | **11/11** |

El profesional afectado está en el **taller del leñador**: el fletcher (`e49a2bbc…`) tiene su
`JOB_SITE` en `610,63,508`, la **mesa de flechas** existe (el generador la coloca), y el libro/ticket
del puesto también; pero `PoiManager` **no tenía el POI** durante las primeras 148 muestras. El 12k de
la misma semilla pasó limpio: es **intermitente**.

## 2 · Reparación aplicada

`VillageGenerator.asegurarElTallerDelLenador` ahora, si la mesa de flechas ya es
`Blocks.FLETCHING_TABLE`, llama a `asegurarPoiDeTrabajo(level, mesa)`:

```java
if (poi.getType(pos).isEmpty()) {
    PoiTypes.forState(level.getBlockState(pos)).ifPresent(t -> poi.add(pos.immutable(), t));
}
```

Es idempotente y también se llama justo después de colocar la mesa. El POI ausente se re-registra; la
cobertura final pasa de 10/11 a **11/11** y el hueco baja de **333 a 148 muestras**.

## 3 · Lo que queda (F3)

El hueco de 148 muestras es **inicial**: la primera muestra (tick 200) ya tiene varios profesionales
sin POI de la generación, y el fletcher no queda válido hasta el tick ~32.800. El 72k debe tener
**11/11 en todas las muestras tras la gracia**, no solo al final. El siguiente paso es:

- re-registrar el POI en el **latido periódico** (cada 200 ticks), no solo al generar/migrar, para que
  el POI aparezca en la primera muestra válida; o
- asegurar el POI **antes** de asignar `JOB_SITE` al fletcher, y
- repetir el 72k hasta `huerfanos_max=0` y `sin_puesto_bajo_max<=30`.

## 4 · Evidencia

- `soak-f2-final`: **0 muertes**, 30 de población, comida 72, `sin_puesto` 1/333, final 10/11.
- `soak-f3-poi`: **0 muertes**, 30 de población, comida 64, `sin_puesto` 1/148, **final 11/11**,
  0 duplicados, 0 inactivos permanentes, guardado OK.
- `soak-poi-f3` (12k, misma semilla): `SOAK_PASS`, 11/11 — el fallo no es determinista en 12k.
- Gates: `test` 3m11s; persistencia 553, ownership 54, skills 376; lint/diff verdes.

## 5 · Veredicto

**F1 y F2 cerrados. F3 abierta:** el último criterio del soak es la **cobertura desde el principio**;
la reparación de POI ya la lleva al 100 % al final y reduce el hueco a la mitad. No se declara el soak
aceptado hasta un 72k con 11/11 en todas las muestras.
