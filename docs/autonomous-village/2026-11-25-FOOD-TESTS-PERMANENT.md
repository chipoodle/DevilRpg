# Tests Food — de no persistidos a gate permanente (25-nov-2026)

**Rama:** `feature/autonomous-village`
**Origen:** `src/foodTest` existía **fuera de git**, aunque `build.gradle` ya lo usaba para
`foodNbtTest` y `foodIntegrationTest`. Además `FoodIntegrationTest.pantryPhysical()` **nunca se
llamaba**, así que el test “pasaba” sin probar la retirada física.

## 1 · Qué se encontró al hacerlo permanente

### 1.1 `DeferredHolder` sin enlazar en headless

`VillagePantry.esHuevoEstrellado()` usaba `ModItems.HUEVO_ESTRELLADO.get()`; en un arranque
`Bootstrap` de test el `DeferredHolder` no está enlazado y lanzaba:

```text
NullPointerException: Trying to access unbound value:
ResourceKey[minecraft:item / devilrpg:huevo_estrellado]
```

Se cambió a `BuiltInRegistries.ITEM.get(...)`, igual que la corrección de iconos de P1. En producción
devuelve el mismo item; en headless devuelve `AIR` y el test no explota.

### 1.2 La despensa física destruía puntos

Al llamar por fin a `pantryPhysical()`:

```text
FALLO: conservacion puntos retirados
```

`sacarComidaFisica` podía quitar **3 panes (12 puntos)** para una petición de **10**: se comían 12 y
solo se acreditaban 10. Ahora **no se parte una pieza que exceda el valor pedido**; se devuelve 8
antes que destruir 12 y acreditar 10. La conservación `puntos0 - puntos1 == got` pasa.

## 2 · Qué se hizo permanente

- `src/foodTest/java/.../FoodIntegrationTest.java`
- `src/foodTest/java/.../FoodStateNbtTest.java`
- `build.gradle`: `test` depende de `foodTestSuite` (policy + food NBT + food integración).
- `pantryPhysical()` vuelve a ejecutarse en `main`.

## 3 · Evidencia

- `foodTestSuite`: **BUILD SUCCESSFUL**; `FOOD_INTEGRATION_TEST: OK`, `FOOD_STATE_NBT_TEST: OK`.
- Gate completo: **BUILD SUCCESSFUL** (1m31s); skills 416, persistencia 553, ownership 59.
- `compileJava` y `lint_aldea --strict` verdes.

## 4 · ¿Qué otros no persistidos?

- `tools/a5-closure/run-a5.ps1`: script de arnés, no test.
- `tools/a2n/*.ps1`: scripts de diagnóstico, no tests.
- `src/foodTest`: **sí valía la pena**; ya está.
- `tools/a2n/A2NavHarness.java.partial`: temporal, eliminado.

## 5 · Límites

- No se ejecutó un servidor real para Food; es integración NBT/contenedor con `Bootstrap`.
- El cambio de retirada afecta a la economía de comida: ante una petición que no encaja exactamente en
  piezas, se entrega menos en vez de destruir comida. Documentado como corrección de conservación.
