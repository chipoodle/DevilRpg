# P1 — Skills: árbol sin dependencia de cliente y primer gate automático (24-nov-2026)

**RUN_ID:** `av-skills-20261124-1040`
**Rama:** `feature/autonomous-village`
**INITIAL_HEAD:** `941ac75bef6ba2d2f4718525ad4e2f51bf8b8031` (docs de L1)
**TESTED_HEAD (código y pruebas):** se registra al commitear; el contenido probado es el de este informe.
**FINAL_HEAD:** commit de este informe.
**Origen previo:** auditoría `2026-11-24-PENDING-AUDIT.md` §2, donde las skills quedaron
`IMPLEMENTED` pero con **0 tests** y una dependencia de cliente en código común.

---

## 1 · Hallazgos corregidos

### 1.1 La construcción de la capability dependía del cliente

`PlayerSkillCapabilityImplementation` es una clase **común** (se instancia en el servidor dedicado al
entrar un jugador) y en su constructor llama a `ClientSkillBuilderFromJson.buildSkillTrees()`. Esa clase
usaba:

```java
Minecraft.getInstance().getResourceManager().getResource(...)
```

`Minecraft` es una clase de cliente. En un servidor dedicado, construir la capability podía lanzar
`NoClassDefFoundError`/NPE; no se había medido porque C4/POI no conectan jugadores. No se declara
"confirmado en runtime": se corrige la **dependencia estructural** y se demuestra que la construcción
funciona en una JVM **sin cliente**.

**Corrección:** el árbol se lee del **classpath** del mod
(`/assets/devilrpg/skills/root_complete.json`), que es el mismo contenido en cliente y servidor. Si el
recurso falta o no es un array, falla con un mensaje explícito en vez de continuar con `null`. Se retira
`@OnlyIn(Dist.CLIENT)` de la clase (era común de hecho) y se exponen `getRoots()`/`getAll()` para
diagnóstico y pruebas.

**Tradeoff declarado:** un resource pack ya no puede sobrescribir el árbol de skills; no era una función
documentada y el árbol es un recurso interno del mod. Se prefiere el comportamiento idéntico en ambos
lados.

### 1.2 La resolución de iconos usaba `DeferredHolder.get()` en código común

`JsonUtil.convertToItem` delegaba en `ModItems.getItemFromLocation`, que recorría los `DeferredHolder`
del mod y llamaba a `get()`. Si el árbol se construía antes de enlazar el registro —o en una prueba
headless sin el mod cargado— lanzaba `Trying to access unbound value`.

**Corrección:** `JsonUtil` resuelve contra `BuiltInRegistries.ITEM` (el registro real, que ya contiene
los objetos del mod) y devuelve `Items.AIR` para un id desconocido. Mismo resultado en producción y sin
dependencia del orden de inicialización. `ModItems.getItemFromLocation` queda sin uso por esta ruta; **no
se modifica** para no introducir un cambio masivo de finales de línea en un fichero CRLF (se documenta).

### 1.3 No existía ningún test del subsistema

**Corrección:** nuevo source set `src/skillTest/java` + tarea `skillCapabilityTest`, integrada en
`test`. Es headless (`Bootstrap`), sin cliente ni servidor, y comprueba:

| Bloque | Qué demuestra |
|---|---|
| Catálogo y árbol | 33 skills reales; 36 nodos (33 + 3 raíces); un nodo por `SkillEnum`; `capability` correcta; display, coste, nivel máximo > 0 y tipo de recurso para las 33 |
| Capability sin cliente | construcción headless; 33 puntos a 0; 33 máximos/costes/tipos/iconos; 6 `PowerEnum`; 3 loadouts; 0 activo; pasivos de las 18 activas sin NPE |
| NBT y partidas viejas | roundtrip de los mapas; partida sin `SOULVINE` recibe puntos/máximo/coste/tipo/icono **sin pisar** `CHARGE=7` del jugador |
| Registro de objetos | icono vanilla resuelto por el registro real; id desconocido → `AIR`; icono sin `item` → `JsonSyntaxException` |

---

## 2 · Archivos

| Archivo | Cambio |
|---|---|
| `src/main/java/.../client/gui/scrollableskillscreen/model/ClientSkillBuilderFromJson.java` | lectura por classpath, sin `Minecraft`, errores explícitos, `getRoots`/`getAll` |
| `src/main/java/com/chipoodle/devilrpg/util/JsonUtil.java` | iconos contra `BuiltInRegistries.ITEM`, fallback `AIR` |
| `src/skillTest/java/.../SkillCapabilityIntegrationTest.java` | **nuevo** test headless (376 aserciones) |
| `build.gradle` | source set `skillTest`, tarea `skillCapabilityTest`, dependencia en `test` |

No se tocó `SkillEnum`, el JSON, los ejecutores, la UI, los payloads, el NBT de jugador ni el balance.

---

## 3 · Criterios de aceptación y evidencia

| # | Criterio | Evidencia | Resultado |
|---|---|---|---|
| 1 | Las 33 skills tienen nodo, coste y display | `skillCapabilityTest` bloque 1: 36 nodos, 33 capabilities, 0 cruzadas | **PASS (376)** |
| 2 | La capability se construye sin cliente | bloque 2: `new PlayerSkillCapabilityImplementation()` en JVM headless; antes la ruta `Minecraft` lo impedía | **PASS** |
| 3 | Los mapas por defecto están completos | bloque 2: 33 en puntos/máximos/costes/tipos/iconos; 6 poderes; 3 loadouts | **PASS** |
| 4 | Una partida vieja recibe las skills nuevas sin pisar valores | bloque 3: `SOULVINE` agregada, `CHARGE=7` conservado | **PASS** |
| 5 | El registro real resuelve iconos | bloque 4: vanilla resuelto, desconocido → `AIR`, sin `item` falla claro | **PASS** |
| 6 | Gates verdes | ver §4 | **PASS** |

**Límites declarados:**

- **No hay corrida de servidor dedicado con un jugador conectado.** El test demuestra que la
  construcción no necesita `Minecraft`; la aceptación runtime de la capability en un servidor real queda
  pendiente.
- **No se reejecutó la UI de cliente** (`SkillScreen`, `SkillWidget`, HUD). El cambio de carga del JSON
  conserva el mismo contenido, pero no se abrió un cliente.
- **Doble ejecución cliente/servidor** (`onKeyInput` envía el payload y ejecuta en cliente; el servidor
  vuelve a ejecutar): sigue sin revisarse. No es un defecto confirmado y no se ha tocado.
- **Resource packs** ya no pueden sobrescribir `root_complete.json` (tradeoff de §1.1).

---

## 4 · Pruebas ejecutadas

| Comando | Resultado |
|---|---|
| `.\gradlew.bat test compileJava -Pa2nDiagnostics=true -Pa2nProbeOnly=true --console=plain --offline` | **BUILD SUCCESSFUL** (1m38s) |
| `SKILL_CAPABILITY_INTEGRATION_TEST` | **OK (376 assertions)** |
| `VILLAGE_PERSISTENCE_INTEGRATION_TEST` | **OK (544)** |
| `VILLAGE_JOB_SITE_OWNERSHIP_POLICY_TEST` | **OK (39)** |
| `policyTest` (80 clases) | **OK** |
| `NAV_SCENARIO_CONTRACT_TEST` / POI / `NAV_SEARCH_BASELINE_TEST` | **OK (253 / 15 / 119)** |
| `python tools\lint_aldea.py --strict` | **OK**, exit 0 |
| `git diff --cached --check` | **0 errores** |

**Antes/después:** skills 0 → **376** aserciones. No se modificaron las aserciones existentes ni se
debilitó ningún gate.

---

## 5 · Estado del subsistema

| Elemento | Antes | Ahora |
|---|---|---|
| Catálogo/árbol | implementado, sin test | implementado + test (33/33) |
| Construcción en servidor | dependía de `Minecraft` | classpath, headless probado |
| Iconos | `DeferredHolder` (unbound en headless) | registro real, fallback `AIR` |
| Partidas viejas | implementado, sin test | test con `CHARGE=7` preservado |
| UI/loadouts | implementado | sin cambios, runtime no medido |
| Doble ejecución cliente/servidor | sin revisar | sigue sin revisar (deuda) |
| Servidor dedicado con jugador | sin medir | sin medir (deuda) |

---

## 6 · Veredicto

**P1 cerrado en su parte ejecutable:** el árbol de skills ya no depende del cliente y el subsistema tiene
su primer gate automático (376 aserciones) dentro de `test`. No se declara aceptación runtime de la UI ni
del servidor dedicado; esas dos deudas quedan explícitas.
