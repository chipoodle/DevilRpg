# Visión: geometría de aldea adaptada al terreno

**Estado:** documentado, NO implementado. El radio/círculo actual es una
limitación de la implementación, no una decisión de diseño definitiva.

## Objetivo

La aldea debe adaptarse a un terreno irregular y seguir siendo transitable.

## Requisitos

- Huella de asentamiento irregular según el terreno local, no un círculo fijo.
- Tolerancia objetivo de **hasta 2 bloques** de elevación/depresión en zonas
  funcionales; es una **intención del usuario**, no una garantía validada.
- Áreas razonablemente llanas para casas, granjas, almacén, puestos y plaza.
- Escaleras, caminos, escalones o conexiones equivalentes donde el desnivel
  bloquee el paso.
- Accesibilidad de **todas** las áreas de trabajo y de los aldeanos que las usan.
- Murallas y entradas integradas con el borde real, no con un radio supuesto.

## Separación de conceptos

1. **Preparación del terreno**: relleno/recorte/nivelación local.
2. **Geometría del asentamiento**: huella, muro, puertas, distribución.
3. **Navegación**: rutas, escalones, puertas y comprobación de alcanzabilidad.

No se debe nivelar todo indiscriminadamente ni prometer transitabilidad universal
sin límites definidos.

## Validación requerida (futura)

- Huella irregular en varios biomas/relieves.
- Desniveles de 0–2 bloques y casos >2 documentados.
- Escaleras/pasos conectan cada área funcional.
- Rutas reales de aldeanos a parcelas, almacén, camas y puestos.
- Muro/entradas sin huecos ni escalones inseguros.

**Requiere diseño:** algoritmo de huella, presupuesto de terreno, criterios de
nivelación y aceptación runtime.
