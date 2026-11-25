# Leñador — portón para árboles de fuera y harina del compostero (25-nov-2026)

**Rama:** `feature/autonomous-village`
**Origen:** partida `New World`; el leñador sale, reclama un árbol de fuera y vuelve sin talar; los
plantones de la arboleda interior no se abonan.

---

## 1 · Evidencia del log

```text
[TREE-CLAIM] event=ACQUIRE tree=479,63,569 task=a547...
[TREE-CLAIM] event=RELEASE tree=479,63,569 ... (29 s después, sin tala)

[LENADOR-DISCOVER] uuid=8ead... pos=503,69,565 harina=0 faltaArboleda=true
[LENADOR-DISCOVER] planton=522,69,549
[LUMBER-HARINA-SOURCE] despensaHarina=0 almacenHarina=0
[Planta] ... nav=[sin ruta] ... goals=[VillagerLumberjackGoal VillagerGateGoal]
```

Dos causas distintas:

### A · El árbol de fuera no se planificaba a través del muro

`pasoDeCamino` usaba **solo** `TalarStepPlanner` para `Fase.TALAR`. Ese planificador no conoce el
muro de la aldea: al reclamar un árbol fuera, devolvía `NO-STEP`/sin ruta y el leñador
`abandonarObjetivoInvalido` lo soltaba. Reclamaba otro árbol, volvía a fallar, y repetía.

**Fix:** si el objetivo está **fuera del recinto** y el leñador **dentro**, `pasoDeCamino` devuelve
primero el **portón** (`VillageManager.pasoParaCruzarElMuro`). Una vez fuera, el planificador de tala
ya puede llevar al árbol.

### B · No había harina de huesos visible

`puntoConHarina` solo miraba **despensa y almacén**; el log da **0 y 0**. La harina que produce el
compostero del granjero no se veía hasta que el granjero la vaciaba en la despensa, y el leñador se
quedaba sin abonar el plantón de `522,69,549`.

**Fix:** si los contenedores no tienen harina, el leñador busca un **compostero lleno**
(`ComposterBlock.LEVEL == MAX_LEVEL`), camina a su punto de apoyo, lo vacía y se lleva la harina.
El compostero se pone a nivel 0 y la harina va al inventario (o a la despensa si no cabe).

## 2 · Harina inicial en el almacén (25-nov-2026)

Además de la harina que ya hubiera, el almacén recibe **64 de harina de huesos**:

- `VillageStorage.remesaInicialHarina` las guarda en el almacén (cofre doble incluido) y
  `VillageSavedData.HarinaInicialEntregada` impide repetirlo al consumirlas.
- Se entrega también a **aldeas ya generadas** (la bandera va aparte de `RemesaInicialEntregada`), así
  que la partida actual recibe las 64 en el siguiente latido aunque su remesa general ya estuviera
  marcada.
- La despensa conserva sus 4 de la remesa inicial; no se duplican.

## 3 · Límites

- Si **no hay ningún compostero lleno** ni harina en los contenedores, el leñador sigue sin poder
  abonar: la solución es que el granjero llene/composte; el código no inventa harina.
- La arboleda interior necesita que el **granjero** produzca composta; el leñador solo la recoge.
- **No hay corrida runtime nueva**: el arreglo es de ruta/recogida y gate. Falta probar en la
  partida que el leñador cruza el portón, tala y abona.

## 3 · Evidencia de gates

- Gate completo: **BUILD SUCCESSFUL** (1m38s); skills 416, persistencia 553, ownership 59.
- `compileJava` y `lint_aldea --strict` verdes; `git diff --check` 0.
- No se añadieron pruebas unitarias específicas: la lógica de portón/compostero es de mundo y no
  tiene un fixture puro.

## 4 · Veredicto

El leñador ya no se rinde al reclamar un árbol de fuera (cruza por el portón) y puede abonar con la
harina del compostero lleno. **Runtime pendiente de la partida.**
