# El ganadero se asfixiaba en una valla mientras recogía el corral (24-nov-2026)

**RUN_ID:** `av-soak-20261124-1130` / `av-soak-20261124-1200`
**Rama:** `feature/autonomous-village`
**Origen:** soak multi-día sobre aldea de producción, mundo desechable sin jugador.
**Evidencia del fallo:** `build/soak/soak-diag-20261124-1150/srv.log` (líneas 798-802), run de 12.000 ticks.
**Fix probado en:** diagnóstico `soak-diag2-20261124-1200` (12.000 ticks) y campaña oficial
`soak-20261124-1208` (72.000 ticks).

---

## 1 · Síntoma medido

En el soak de 12.000 ticks, el ganadero **Zacarías** murió:

```text
Villager Villager['Zacarias (Ganadero)
Recogiendo el corral'/305, l='ServerLevel[world]', x=708.50, y=55.00, z=533.50] died,
message: 'Zacarias (Ganadero)
Recogiendo el corral suffocated in a wall'
```

- Posición: `708.5, 55.0, 533.5` — el **corral anexo**, fuera de la muralla, a la cota del corral.
- Actividad: `Recogiendo el corral` (`VillagerAnimalFarmGoal`, fase `RECOGER`).
- Población: 16 → 15; cobertura de estaciones 11 → 10.
- No hubo asedio, ni jugador, ni mob atacante: la muerte fue por **estar dentro de un bloque**.

## 2 · Causa raíz

`VillagerAnimalFarmGoal.tick()` comprobaba el desatasco **solo en la rama de navegación**
(`distancia > alcance`, línea ~373). Si el ganadero ya estaba en el destino (recogiendo un huevo/lana,
trabajando) y un animal lo empujaba contra una valla o un muro del corral, el goal seguía en la rama de
trabajo y **no llamaba** a `VillageManager.desatascarSiEstaEncajado`.

El latido de aldea sí lo comprueba para todos los aldeanos, pero solo cada `VILLAGE_POLL_TICKS` (10 s).
La asfixia puede consumar la muerte antes de esa pasada. Además, el freno del desatasco es por celda
(200 ticks para la misma celda), así que la red de 10 s no garantiza sacarlo a tiempo.

## 3 · Corrección

`VillagerAnimalFarmGoal.tick()` llama a `VillageManager.desatascarSiEstaEncajado(villager)` **al
principio de cada tick**, antes de mirar la distancia o la fase:

```java
if (VillageManager.desatascarSiEstaEncajado(villager)) {
    mejorDistancia = Double.MAX_VALUE;
    stuckTicks = 0;
    return;
}
```

Es el mismo patrón que ya usan `VillagerLumberjackGoal`, `VillagerFarmGoal`, `VillagerPickupGoal`,
`VillagerCollectGoal` y `VillagerClericGoal`. El ayudante no toca nada si la celda de los pies está
libre; si está metido en una puerta/portón, lo abre; si está en un bloque, lo saca a una casilla segura.

**Archivo:** `src/main/java/com/chipoodle/devilrpg/entity/goal/VillagerAnimalFarmGoal.java`.

No se tocó la selección de destinos, el corral, la cría, el sacrificio, la entrega, la navegación, los
presupuestos ni C4/POI.

## 4 · Evidencia

| Run | Ticks | Resultado | Población | Cobertura | Muerte |
|---|---:|---|---:|---:|---|
| `soak-diag-20261124-1150` (antes) | 12.000 | FAIL (cobertura + muerte) | 15 | 10/11 | **Zacarías, asfixiado en 708,55,533** |
| `soak-diag2-20261124-1200` (después) | 12.000 | **SOAK_PASS** | 16 | 11/11 | 0 |
| `soak-final-20261124-1120` (oficial, `552e9b57`) | 72.000 | `SOAK_PASS_CON_BAJAS` | 11 | 8/11 | **0 asfixias**; 5 muertes por hambre (hallazgo aparte) |

El diagnóstico posterior no reprodujo la muerte y mantuvo 12/12 profesionales, 11/11 estaciones, 0
duplicados, 0 huérfanos, 0 inactivos permanentes y guardado OK. La campaña oficial de 72k **no**
reprodujo la asfixia; sí encontró un problema distinto de comida, documentado en
`2026-11-24-SOAK-MULTIDAY-CAMPAIGN.md` §4.

## 5 · Límites

- **No hay un test headless** del goal: `desatascarSiEstaEncajado` necesita un `ServerLevel` real. La
  evidencia es runtime (soak).
- La muerte fue **estocástica** (un animal empuja al aldeano contra una valla); una campaña sin muerte
  reduce el riesgo, no demuestra probabilidad cero. El patrón (comprobación por tick) es el mismo que ya
  usan los demás oficios.
- La reparación de la asfixia **no** repuebla al aldeano: la reposición depende de
  `VillageManager.manageNearby` (jugador cerca) y no se ejecuta en el soak sin jugador; ver el informe
  del soak.

## 6 · Veredicto

**Defecto real corregido** con la modificación mínima y el patrón existente del proyecto. La campaña de
12k posterior no reprodujo la asfixia; la campaña oficial de 72k (`552e9b57`) tampoco la reprodujo, pero
encontró 5 muertes por hambre —un problema distinto, abierto— documentado en
`2026-11-24-SOAK-MULTIDAY-CAMPAIGN.md`.
