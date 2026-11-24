# SOAK MULTI-DÍA — arnés de aldea autónoma

Campaña de **estancia larga** sobre una aldea de producción, en un mundo desechable y un servidor
dedicado sin jugador. Es el criterio 8 del Master Plan (*«un soak multi-día acotado no muestra
asignaciones huérfanas, deriva de almacén ni bucles de inactividad permanentes»*).

## Qué hace

- Genera una aldea en `661,71,533` con `VillageManager.preGenerate` (misma ruta que producción).
- Fuerza los chunks del recinto y ejecuta `VillageManager.ejecutarMantenimientoParaArnes` cada 200 ticks
  (la ruta real de `tickVillageLife`, sin dobles).
- Corre `TICKS` ticks **de ventana** con *sprint* de Minecraft (sin esperar 50 ms por tick).
- Muestrea cada 200 ticks: población, 12 profesionales, cobertura de estaciones, duplicados, huérfanos,
  inactividad, comida de despensa, leña, hierro y total del almacén.
- Al cerrar: `saveEverything`, `soak.json`, `muestras.csv`, `fin.txt` y parada limpia.

## Contrato obligatorio

| Criterio | Umbral |
|---|---|
| Profesionales censados | 12/12 (`VillageGenerator.puestosDelPueblo()`) |
| Duplicados de `JOB_SITE` | 0 en toda muestra tras la gracia |
| Huérfanos (`POI` inexistente o fuera del muro) | 0 en toda muestra tras la gracia |
| Cobertura (`JOB_SITE` válido) | se reporta `con_puesto_max`/`con_puesto_fin`; **no es un FAIL duro** si baja por una baja real. El FAIL duro es que un profesional **presente** (no NITWIT) siga sin puesto más de 30 muestras. C4 cubre la cobertura 11/11 |
| Inactividad larga | 0 permanente (sin goal corriendo y sin moverse 12.000 ticks de día); los huecos de 2.400 ticks se reportan como pico |
| Comida de despensa | nunca negativa; no 0 durante más de 30 muestras seguidas |
| Población final | >0 |
| Guardado | `saveEverything` OK |
| Excepciones | sin `Exception in thread`/`ServerHangWatchdog` en `srv.log` (lo revisa el lanzador) |

Un aldeano puede morir o cambiar de estación: se tolera un hueco de hasta 6.000 ticks; una pérdida
permanente de cobertura sí falla. `ausentes_fin` se reporta, no se cuenta como "sin puesto".

## Uso

```powershell
# smoke (4.000 ticks)
.\tools\soak\lanzar-soak.ps1 -Run soak-smoke-YYYYMMDD-HHMM -Ticks 4000 -TimeoutMinutes 25

# campaña de 3 días de juego (72.000 ticks)
.\tools\soak\lanzar-soak.ps1 -Run soak-YYYYMMDD-HHMM -Ticks 72000 -TimeoutMinutes 45
```

El lanzador usa un `gameDirectory` propio (`build/soak/<run>/game`), no toca `run/saves` ni mundos
históricos, y no relanza un `RunId` con evidencia. No ejecutar otro Gradle/servidor a la vez.

## Evidencia

```
build/soak/<run>/
  soak.json        veredicto + métricas finales
  muestras.csv     una fila por muestra (200 ticks)
  fin.txt          SOAK_PASS/SOAK_FAIL con detalle
  sha.txt          SHA del código usado
  srv.log          log del servidor
  launcher.txt     resultado y escaneo de excepciones
```

## Límites declarados

- **No hay jugador conectado** y no se abre cliente. En producción la repoblación y el crecimiento viven
  en `VillageManager.manageNearby`, que exige un jugador cerca; este arnés no lo ejecuta, así que **no
  puede demostrar la reposición de un aldeano muerto**. Una baja se reporta (`ausentes_fin`,
  `con_puesto_fin`) y no se declara PASS de cobertura; C4 cubre 12/12 y 11/11.
- **No hay libro mayor de transacciones**: comida/leña/hierro/almacén son un *proxy* de deriva, no una
  prueba de conservación exacta por evento.
- **No hay asedio**: el objetivo es la aldea en paz durante días; A2/C4 cubren el asedio.
- Una aldea con todos los oficios cubiertos puede seguir teniendo comportamientos no medidos
  (calidad de rutas, tiempo de faena); este arnés no los convierte en PASS.
