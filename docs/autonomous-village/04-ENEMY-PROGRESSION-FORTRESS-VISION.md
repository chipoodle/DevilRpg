# Visión: progresión enemiga y sociedad de fortaleza

**Estado:** documentado, NO implementado. No confundir con la navegación enemiga
y el asedio a aldeas ya existentes.

## Requisitos

1. Zombies agresivos empiezan débiles.
2. Su fuerza progresa con el **tiempo de mundo**, no solo con proximidad del
   jugador, actividad o distancia al spawn.
3. Permanecer en una zona segura no debe congelar la progresión.
4. Los enemigos construyen y amplían una **fortaleza**.
5. Defienden la fortaleza colectivamente, con roles y respuestas coordinadas.
6. Construcción, defensa, progresión y asedio comparten objetivos estratégicos y
   recursos acotados.

## Distinciones

- **Existente:** navegación de asaltantes, asedio a la aldea, guaridas y milicia.
- **Futuro:** escalado temporal, fortaleza construida, economía enemiga, defensa
  grupal coordinada.
- La documentación actual de diseño/roadmap marca la fortaleza como pendiente;
  no hay clases de fortaleza ni escalado temporal implementadas.

## Decisiones pendientes

- Fórmula y persistencia del escalado temporal.
- Presupuesto de recursos/construcción.
- Identidad y coordinación de roles enemigos.
- Interacción con aldeas, asedios y guardado/reload.

**Requiere diseño:** antes de implementar, definir métricas de progresión y un
escenario de aceptación acotado.
