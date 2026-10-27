# Scripts SQL históricos (V3..V12)

Estos scripts se aplicaban **a mano** en cada schema (`padilla_dev`, `padilla_staging`, `padilla`)
antes de PRO-003. Quedan como referencia; **ya no se ejecutan**.

Desde PRO-003 (2026-10-27) el schema lo administra **Flyway**:

- `src/main/resources/db/migration/V1__baseline.sql` = estado equivalente a V3..V12 (sacado de `padilla_dev`).
- Migraciones nuevas: `V13__...`, `V14__...` en adelante.
- Ambientes que ya tenían las tablas hacen baseline en la versión 12 (`spring.flyway.baseline-version=12`):
  Flyway no ejecuta V1 ahí y aplica solo de V13 en adelante.
- **staging y prod tienen Flyway apagado** (`spring.flyway.enabled=false`) porque sus schemas no
  están al día con V12. Conciliarlos requiere OK de Jorge (tarjeta I1 / PRO-003).

V1 y V2 nunca estuvieron en el repo (users, properties y contracts se crearon por Hibernate).
