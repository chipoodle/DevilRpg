# F1 — Cierre del hambre: ración al rendirse el goal de taberna (24-nov-2026)

**RUN_ID:** `av-f1-20261124-1755`
**Rama:** `feature/autonomous-village`
**Código:** `fa6a63f4`
**Origen:** `2026-11-24-F1-PLAZA-WAYPOINT-REGRESSION.md`.

---

## 1 · La pieza que faltaba

El comentario de `VillagerTavernGoal` prometía, desde el principio:

> *Si el aldeano no puede ir a la taberna (no hay taberna, la aldea está siendo atacada o no hay
> comida), el reparto del minuto le da su ración como siempre: nadie se muere de hambre por no llegar
> a la mesa.*

No se cumplía: cuando el goal se rendía (`stuckTicks >= STUCK_LIMIT`), el aldeano seguía con hambre
hasta morir, aunque la despensa estuviera llena. Eso explica las 8 muertes del mejor run anterior
(`soak-f1-west`) y las 30 de la plaza: **no era falta de comida, era el goal rindiéndose sin relevo**.

## 2 · Corrección

En `canContinueToUse`, al rendirse:

```java
VillageManager.marcarPuntoFallido(villager, usandoDespensa ? destinoComida : mesa);
if (VillagePantry.comida(level, center) >= COMIDA_MINIMA && VillageManager.tieneHambre(level, villager)) {
    comer(level, false); // ración REAL de la despensa, en el sitio
}
return false;
```

`comer(level, false)`:

- retira **1 punto real** de la despensa (`VillagePantry.sacarComida`),
- marca `ultimaComida` y **reduce 25** de hambre,
- consume el `tieneHambre` de 3 min (no es comida gratis ni bucle),
- registra el suceso `Comio su racion` (no “en la taberna”), y el log `[C1-MEAL] … sitio=racion-en-el-sitio`.

No se teletransporta al aldeano ni se toca la navegación: come donde está, con comida real de la aldea.

## 3 · Evidencia

Mismo mundo que antes moría (`3188456493687723571`), 72.000 ticks:

| Run | Código | Población | Comida | Muertes | Hambre |
|---|---|---:|---|---:|---:|
| `soak-f1-west` (antes) | `2ebf8ef4` | 22 | 164 | **8** | 7 |
| `soak-f1-fallback` (después) | `fa6a63f4` | **29** | **88** | **1** | **0** |

- **0 muertes por hambre.**
- La única baja es **una caída**: `Zacarias (Sin oficio) Volviendo al pueblo hit the ground too hard`
  en `729,64,542`. No es hambre; es un defecto aparte (**F2**).
- 45 raciones de rendición consumidas; la despensa se mantuvo en **88** (no se vació).
- 11/11 profesionales con puesto, 0 duplicados, 0 huérfanos, 0 sin puesto, 0 inactivos permanentes,
  guardado OK.
- `SOAK_PASS_CON_BAJAS` por la caída; **F1 (hambre) cerrado**, soak global aún no aceptado por F2.

## 4 · Criterios F1

| # | Criterio | Evidencia | Resultado |
|---|---|---|---|
| 1 | 72k sin muertes por hambre | `soak-f1-fallback`: **0** | **PASS** |
| 2 | La ración es real y limitada | 45 `[C1-MEAL] sitio=racion-en-el-sitio`, comida 88 | PASS |
| 3 | Sin regresiones de asignaciones | 11/11, 0 dup/huérf./sin puesto/idle | PASS |
| 4 | Sin regresiones de tests | `test` 1m13s, persistencia 553 / ownership 54 / skills 376 | PASS |

## 5 · Límites y siguiente (F2)

- **F2 abierta:** la caída `729,64,542` “Volviendo al pueblo” mata a 1 aldeano (también apareció en
  runs anteriores). Es un defecto de **daño por caída/terreno**, no de hambre. El soak global exige 0
  bajas; F2 es el siguiente objetivo.
- El aldeano come en el sitio cuando no llega a la mesa; la ruta/terreno sigue siendo mejorable, pero
  ya **no mata de hambre**. No se declara la aldea perfecta; se cierra el criterio de hambre.
