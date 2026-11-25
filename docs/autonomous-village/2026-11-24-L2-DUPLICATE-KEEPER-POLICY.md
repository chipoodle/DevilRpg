# L2 — Duplicado con el titular no cargado: política y prueba (24-nov-2026)

**Rama:** `feature/autonomous-village`
**Código:** `b426814b`
**Origen:** auditoría `2026-11-24-PENDING-AUDIT.md` L2.

---

## 1 · El caso

Dos aldeanos cargados pueden conservar la misma memoria `JOB_SITE` si el guardado capturó una
reasignación transitoria. El libro (`JobSiteOwners`) dice quién es el **titular real**. El caso
delicado es cuando el titular **no está cargado**:

- no se le puede tocar su `JOB_SITE` (no está);
- las memorias duplicadas de los que sí están deben limpiarse;
- **el ticket del POI NO se libera**: pertenece al titular ausente; soltarlo dejaría que otro aldeano
  se llevara su estación mientras no está.

La implementación (`reconciliarPuestosDuplicados`) ya hacía esto, pero no estaba aislado ni probado:
el `VillageJobSiteOwnershipPolicyTest` cubría 54 aserciones y L2 quedaba como “limitación NO_MEDIDO”.

## 2 · Corrección

- `VillageJobSiteOwnershipPolicy.limpiarDuplicados(titular, titularCargado, cargados)`:
  - sin titular en el libro → no limpia nada (no se adivina);
  - titular cargado → limpia a todos menos al titular;
  - titular no cargado → limpia a todos los cargados, **sin tocar el ticket**;
  - devuelve un `TreeSet` determinista; UUID nulo no rompe.
- `VillageManager.reconciliarPuestosDuplicados` usa esa decisión y deja explícito en el log
  “el titular no está cargado; se limpian N memorias y NO se libera el ticket”.
- Pruebas nuevas en `VillageJobSiteOwnershipPolicyTest`: 5 aserciones (54 → **59**).

## 3 · Evidencia

| Comprobación | Resultado |
|---|---|
| Titular NO cargado: limpia a los dos cargados | PASS |
| Titular cargado: conserva al titular y limpia al resto | PASS |
| Sin titular en el libro: no limpia | PASS |
| Lista vacía: no limpia | PASS |
| UUID nulo: no rompe | PASS |
| `VillageJobSiteOwnershipPolicyTest` | **59 assertions** |
| `gradlew test ...` | **BUILD SUCCESSFUL** (1m26s) |
| Persistencia / skills | 553 / 376 |

## 4 · Límite

No hay un **runtime fixture** con el titular realmente descargado (aldeano fuera de chunk/servidor):
la política pura prueba la decisión, pero el caso “titular no cargado en un servidor real” sigue sin
medirse. L2 queda **CERRADA en política**; la limitación de evidencia runtime se declara.

## 5 · Veredicto

L2 **cerrada en política y pruebas**; el ticket no se libera con el titular ausente. Siguen L5–L8,
skills runtime y la decisión de ramas/A1.
