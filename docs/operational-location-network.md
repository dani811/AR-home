# Operational location network

Status: proposed

Issue: #50

## 1. Purpose

The commercial evolution of AR Home requires more than multiple users sharing one inventory. A freelancer or small company operates across warehouses, vans, workshops, customer sites and temporary jobs.

The product must model those places as connected operational locations and preserve inventory movement between them.

```text
Instalaciones Pepito S.L.
├── Almacén Pepito
├── Furgoneta Juanito
├── Furgoneta Pepito
├── Obra Calle Sagasta 24
└── Reforma Av. del Puerto
```

The system should answer not only where an object is, but which location holds it, who is responsible, how it arrived there and what must return or be replenished.

## 2. Modeling rule

Users, logistics locations and spatial maps are separate concepts.

A van remains a location when its driver changes. A job site remains a location when nobody is assigned. A location may work without AR and gain a spatial map later.

```text
Workspace
├── Users
└── LocationNodes
    ├── inventory
    ├── movements
    └── optional spatial link
```

## 3. Workspace boundary

Commercial collaboration eventually needs a boundary grouping users, locations, shared inventory, movements, permissions and audit history.

Working name: `Workspace` or `Organization`.

The first logistics slice need not implement multi-tenant SaaS, but persistence must not block that future.

## 4. LocationNode

`LocationNode` is the generic logistics hierarchy.

Initial types:

- `SITE`;
- `WAREHOUSE`;
- `JOB_SITE`;
- `VEHICLE`;
- `ROOM`;
- `AREA`;
- `RACK`;
- `CABINET`;
- `SHELF`;
- `DRAWER`;
- `BIN`;
- `OTHER`.

Anticipated fields:

- `id` UUID;
- workspace reference;
- optional `parentLocationId`;
- `name`;
- `type`;
- `status`: `ACTIVE | INACTIVE | CLOSED`;
- optional `activeFrom` / `activeUntil`;
- optional spatial-link reference;
- timestamps.

The hierarchy must support both:

```text
Casa > Dormitorio > Cajonera > Cajón 2
```

and:

```text
Almacén > Zona electricidad > Rack A > Balda 3 > Caja 4
```

Detailed furniture geometry remains in the spatial module. `LocationNode` is the operational representation.

## 5. Users and assignments

A user may be assigned responsibility or access to a location without becoming that location.

```text
Juanito -> primary technician -> Furgoneta Juanito
Juanito -> assigned -> Obra Sagasta
Pedro   -> temporary driver -> Furgoneta Juanito
María   -> admin -> all locations
```

Changing the assigned person must not move vehicle inventory.

Authorization receives its own later slice.

## 6. Inventory per location

The system must answer global and per-location stock.

```text
Cable 2.5 mm²
Almacén Pepito       430 m
Furgoneta Juanito     85 m
Furgoneta Pepito      42 m
Obra Sagasta          26 m
TOTAL                 583 m
```

For serialized inventory, one active unit has one current operational location/custody state while retaining its expected/home location where useful.

For bulk inventory, balances are eventually derived or materialized from movement history.

## 7. Movement ledger

Locations are connected by append-oriented inventory movements.

Initial semantics:

- `RECEIPT`: stock enters the network;
- `TRANSFER`: stock moves between locations;
- `ISSUE`: stock is consumed or leaves operational custody;
- `RETURN`: stock returns;
- `ADJUSTMENT`: audited correction.

```text
08:00 Almacén -> Furgoneta Juanito     100 m
09:12 Furgoneta -> Obra Sagasta         40 m
13:46 ISSUE at Obra Sagasta              28 m
17:51 Obra Sagasta -> Furgoneta          12 m
```

Current quantities must not become unrelated mutable flags with no history.

Transaction boundaries, balance materialization and in-transit stock are separate implementation decisions.

## 8. Temporary job sites

`JOB_SITE` is a first-class temporary operational location.

It can hold:

- responsible users;
- material sent;
- serialized tools;
- consumed material;
- remaining stock;
- optional spatial map;
- lifecycle dates/status.

Closing a job site requires explicit reconciliation:

```text
remaining stock
  -> warehouse
  -> vehicle
  -> another job site
  -> issue/consume
  -> adjustment with reason
```

History remains queryable after `CLOSED`.

## 9. Commercial workflows

### Prepare vehicle

Compare tomorrow's required inventory with the vehicle's current stock and produce a warehouse picking/load list.

```text
Taladro             OK
Multímetro          OK
Cable 2.5 mm²       12 / 30 m -> need 18
Diferencial         missing
C16                  12 / 8   -> OK
Tacos                 5 / 20  -> need 15
```

### Job-site reconciliation

Answer what was sent, remains, was consumed, must return and which serialized tools are still present.

### Asset custody

```text
Bosch GSB #003
Current location: Obra Sagasta
Responsible: Juanito
Home: Almacén > Armario herramientas > Balda 2
```

### Cross-location search

The product should eventually answer:

- Where is the multimeter?
- What stock is in Juanito's van?
- What remains at Sagasta?
- Which tools have not returned?
- What needs loading tomorrow?

## 10. Spatial integration

Spatial mapping is optional per location.

A warehouse, van, workshop or job site may later support:

- AR-guided find;
- guided picking;
- guided return;
- visual verification;
- cycle-count assistance;
- storage-zone overlays.

The logistics core remains functional without a spatial map.

Barcodes and QR codes may support identity/workflow but never become required anchors for markerless relocalization.

## 11. Commercial packaging direction

This capability is a likely monetization boundary.

```text
PERSONAL
personal spaces and inventory

PRO
freelancer
warehouse + vehicle + active jobs

TEAM / SMB
multiple users, vehicles and job sites
permissions, movements, audit
shared operational maps

BUSINESS
multiple sites
API/integrations
advanced controls/reporting
```

Pricing should reflect collaboration, operational scale and workflow value, not customer data ownership or export restrictions.

## 12. Architecture guardrails

- inventory does not depend on ARCore;
- `LocationNode` is logistics structure, not spatial geometry;
- users and locations remain distinct;
- spatial containment and inventory relations remain separate graphs;
- item identity remains independent from location;
- movement history is append-oriented;
- household terminology must not leak into universal APIs;
- authorization, invoicing and workforce scheduling remain separate concerns.

## 13. Acceptance examples

The model must eventually support:

1. One company with one warehouse, two vans and two active job sites.
2. Juanito assigned to a van and Pedro using it tomorrow without moving its stock.
3. Cable moving warehouse -> van -> job site with partial consumption and return.
4. A serialized multimeter at a job site while its home remains a warehouse cabinet.
5. A closed job site retaining complete movement history.
6. Remaining tracked inventory explicitly reconciled on close.
7. Global stock aggregated across locations.
8. A location gaining an AR map later without changing inventory identity.

## 14. Non-goals for first slices

Do not implement yet:

- accounting/invoicing;
- ERP replacement;
- CRM;
- GPS fleet tracking;
- workforce scheduling;
- route optimization;
- enterprise authorization;
- automatic replenishment;
- shipping/carrier workflows.

Near-term implementation should establish generic locations and movement semantics first. Collaboration and commercial workflows build on that foundation.

Refs: #50, #48, #40, #20.
