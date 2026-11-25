# F4 — Aldeano parado sin faena: el empujón no cubría a los que no tienen goal (24-nov-2026)

**RUN_ID:** `av-stability-20261124-2100` / `av-f4-20261124-2115`
**Rama:** `feature/autonomous-village`
**Código del fallo:** `db875a7e` (= `bfd5113b`, soak aceptado en la semilla 3188)
**Corrección:** `bcf98f70`
**Origen:** `2026-11-24-SOAK-ACCEPTANCE.md` §5 (estabilidad multi-mundo).

---

## 1 · La estabilidad falló en el segundo mundo

El soak aceptado en `bfd5113b` pasó en la semilla `3188…`. Al repetirlo en la **otra** semilla
(`7454239611058706439`, `soak-stability`):

- **0 muertes**, población 16, **11/11**, 0 huérfanos/sin puesto, comida 172, guardado OK…
- pero **`inactivos_permanentes=1`**: un **granjero** (`3bce318d…`) estuvo **12.000 ticks** (10 min)
  parado en `629,63,528`, con su `JOB_SITE` en `630,63,567` y **ningún goal del mod** activo.
  Por eso el soak daba `SOAK_FAIL`.

El empujón de I198 (`empujarSiEstaPlantado`) **no lo cubre**: empieza con

```java
if (!tieneUnGoalDelModActivo(villager)) return; // sin faena del mod, su paseo es suyo
```

y el granjero no tenía faena. El `desatascar` tampoco: no estaba dentro de un bloque.

## 2 · Corrección

`VillageManager.despertarSiEstaParado` (`bcf98f70`), llamado en el latido para todos los aldeanos:

- solo adultos, despiertos, sin descanso, sin target y **sin goal del mod**;
- solo **oficios del pueblo** (no NONE/NITWIT/guardias);
- si no se mueve desde hace **30 s** (`DevilRpgUltimoMovimiento`; si no hay dato, se le da 30 s de
  gracia) y no se le ha despertado en otros 30 s;
- le pide camino a su `JOB_SITE` (o al centro si el puesto es su propia celda) con `caminarHacia`.
- **No teletransporta**: solo vuelve a pedir navegación; el aldeano anda.

## 3 · Evidencia

| Run | Semilla | Código | Resultado | Muertes | 11/11 | Inactivos permanentes |
|---|---|---|---:|---:|---:|---:|
| `soak-f3-outside` | 3188… | `bfd5113b` | **`SOAK_PASS`** | 0 | sí | 0 |
| `soak-stability` | 7454… | `bfd5113b` | `SOAK_FAIL` | 0 | sí | **1** (granjero 12.000 ticks) |
| `soak-f4-stability` | 7454… | **`bcf98f70`** | **`SOAK_PASS`** | **0** | **sí** | **0** |
| `soak-f4-confirm` | 3188… | `bcf98f70` | **`SOAK_PASS`** | 0 | sí | 0 |

- `soak-f4-stability`: 72k, 0 muertes, 16 de población, 11/11, 0 duplicados/huérfanos/sin puesto,
  **0 inactivos permanentes**, comida 164, guardado OK.
- `soak-f4-confirm`: 72k, 0 muertes, 30 de población, 11/11, **0 inactivos permanentes**, comida 108,
  guardado OK. **Mismo SHA en las dos semillas.**
- Gates: `test` 1m18s; persistencia 553, ownership 54, skills 376; lint/diff verdes.

## 4 · Criterios

| # | Criterio | Evidencia | Resultado |
|---|---|---|---|
| 1 | El segundo mundo deja de fallar por inactivo | `soak-f4-stability`: 0 permanentes | **PASS** |
| 2 | Sin muertes | 0 | **PASS** |
| 3 | 11/11 | sí | **PASS** |
| 4 | Mismo SHA en la otra semilla | `soak-f4-confirm`: **`SOAK_PASS`** | **PASS** |

## 5 · Veredicto

**F4 cerrada** con `bcf98f70`: el granjero parado sin goal recibe un “despierta” y vuelve a su puesto.
El soak queda **estable en dos semillas con el mismo SHA** (`soak-f4-stability` y `soak-f4-confirm`,
ambos `SOAK_PASS`). Siguen abiertos L2, L5–L8, skills runtime y la decisión de ramas/A1.
