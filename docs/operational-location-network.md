# Operational location network

Status: proposed

Issue: #50

## 1. Purpose

The commercial evolution of AR Home requires more than multiple users sharing one inventory. A freelancer or small company operates across a network of physical places: warehouse, van, workshop, customer site, temporary job site and nested storage areas inside each one.

The product must model those places as connected operational locations and preserve inventory movement between them.

Example:

```text
Instalaciones Pepito S.L.
├── Almacén Pepito
│   └── Rack electricidad
├── Furgoneta Juanito
├── Furgoneta Pepito
├── Obra Calle Sagasta 24
└── Reforma Av. del Puerto
```

The core question becomes not only “where is this object?” but also:

- which operational location owns the current stock;
- who is responsible for it;
- how it moved there;
- what must be returned or replenished;
- whether that location has a spatial map for guided retrieval.

## 2. Core modeling rule

Users, inventory locations and spatial maps are separate concepts.

A van is a location even when its habitual technician changes. A job site is a location even when no user is currently assigned. A location may exist without an AR map and gain one later.

Conceptually:

```text
Organization / Workspace
        |
        +-- Users
        |
        +-- LocationNodes
               |
               +-- Inventory balances / units
               +-- Movements
               +-- optional SpatialMap linkage
```

## 3. Organization / workspace boundary

Commercial collaboration eventually needs a boundary that groups:

- users;
- locations;
- shared catalog/inventory;
- movements;
- permissions;
- audit history.

The first logistics implementation does not need to deliver full multi-tenant SaaS, but identifiers and persistence must not make that future impossible.

Working name: `Workspace` or `Organization`. Exact naming can be decided with the authorization slice.

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

- `id`: UUID;
- workspace/organization reference;
- `parentLocationId` optional;
- `name`;
- `type`;
- `status`: `ACTIVE | INACTIVE | CLOSED`;
- `activeFrom` optional;
- `activeUntil` optional;
- optional address/description metadata in a later slice;
- optional spatial-link reference;
- timestamps.

Location types should be data-compatible with new categories later; avoid coupling core logic to a fixed household-only hierarchy.

## 5. Hierarchy examples

A household can still be represented:

```text
Casa
└── Dormitorio
    └── Cajonera
        └── Cajón 2
```

A business can use the same logistics abstraction:

```text
Almacén Pepito
└── Zona electricidad
    └── Rack A
        └── Balda 3
            └── Caja 4
```

A vehicle is also a location tree:

```text
Furgoneta Juanito
└── Módulo izquierdo
    ├── Cajón 1
    └── Cajón 2
```

Detailed furniture geometry remains a concern of the spatial module. `LocationNode` is the operational/logistics representation and may link to richer spatial entities where available.

## 6. Users and assignments

A user can be assigned responsibility or access to locations without being the location itself.

Examples:

```text
Juanito -> primary technician -> Furgoneta Juanito
Juanito -> assigned -> Obra Sagasta
María   -> admin -> all locations
Pedro   -> temporary driver -> Furgoneta Juanito
```

Changing the assigned person must not move the van inventory.

The initial location specification does not hard-code a complete role model. Authorization receives its own slice.

## 7. Inventory per location

The system must answer both global and per-location quantities.

Example:

```text
Cable 2.5 mm²

Almacén Pepito       430 m
Furgoneta Juanito     85 m
Furgoneta Pepito      42 m
Obra Sagasta          26 m
--------------------------
TOTAL                 583 m
```

For serialized inventory, one active unit has one current operational location/custody state at a time, while retaining its expected/home storage location where useful.

For bulk inventory, balances are eventually derived or materialized from movement history.

## 8. Movement ledger

Locations are connected by inventory movements.

Initial movement semantics:

- `RECEIPT`: stock enters the network;
- `TRANSFER`: stock moves between locations;
- `ISSUE`: stock is consumed or leaves operational custody;
- `RETURN`: stock returns from use/job/custody;
- `ADJUSTMENT`: audited correction.

Example:

```text
08:00  Almacén -> Furgoneta Juanito      100 m cable
09:12  Furgoneta -> Obra Sagasta          40 m
13:46  ISSUE at Obra Sagasta              28 m
17:51  Obra Sagasta -> Furgoneta          12 m
```

Movement history should be append-oriented and auditable. Current quantities must not be represented as unrelated mutable flags with no history.

A later design will determine transaction boundaries, materialized balances and in-transit state.

## 9. Temporary job sites

`JOB_SITE` is a first-class temporary location.

It may have:

- activation/start date;
- responsible users;
- material sent;
- serialized tools present;
- consumed material;
- remaining stock;
- optional spatial map;
- close date/status.

Closing a job site requires reconciliation of tracked inventory:

```text
remaining stock
  -> warehouse
  -> vehicle
  -> another job site
  -> issue/consume
  -> adjustment with reason
```

History remains queryable after `CLOSED`.

## 10. Commercial workflows enabled

### Prepare vehicle

Given planned work and required inventory, compare requirements with vehicle stock.

Example:

```text
Tomorrow: electrical panel installation

Taladro              OK
Multímetro           OK
Cable 2.5 mm²        12 / 30 m  -> need 18
Diferencial          missing
C16                   12 / 8     -> OK
Tacos                  5 / 20    -> need 15
```

The product can generate a warehouse picking/load list.

### Job-site reconciliation

Answer:

- what was sent;
- what remains;
- what was consumed;
- what must be returned;
- which serialized tools are still present.

### Asset custody

For a physical unit:

```text
Bosch GSB #003
Current location: Obra Sagasta
Responsible user: Juanito
Home location: Almacén > Armario herramientas > Balda 2
```

### Cross-location search

The application should eventually answer natural questions such as:

- Where is the multimeter?
- What stock is in Juanito's van?
- What remains at Sagasta?
- Which tools have not returned?
- What needs loading tomorrow?

## 11. Spatial integration

Spatial mapping is optional per operational location.

A warehouse, van, workshop or job site may later link to a persistent spatial map and support:

- AR-guided find;
- guided picking;
- guided return;
- visual verification;
- cycle-count assistance;
- storage-zone overlays.

The logistics core remains functional when no spatial map exists.

Barcodes and QR codes may support identity and workflow but never become required anchors for markerless relocalization.

## 12. Commercial packaging direction

This network capability is a likely monetization boundary.

Directional packaging:

```text
PERSONAL
personal spaces and inventory

PRO
freelancer
warehouse + vehicle + active jobs

TEAM / SMB
multiple users
multiple vehicles/job sites
permissions
movements/audit
shared operational maps

BUSINESS
multiple sites
API/integrations
advanced controls/reporting
```

Pricing should reflect collaboration, operational scale and workflow value rather than preventing customers from owning or exporting their inventory data.

## 13. Architecture guardrails

- inventory does not depend on ARCore;
- `LocationNode` is logistics structure, not a replacement for spatial geometry;
- users and locations remain distinct;
- spatial containment and inventory relations remain separate graphs;
- item identity remains independent from physical location;
- movement history is append-oriented;
- household terminology must not leak into universal inventory/logistics APIs;
- authorization, invoicing and workforce scheduling are separate concerns.

## 14. Acceptance examples

The model must eventually support all of these without special-case household logic:

1. One company has one warehouse, two vans and two active job sites.
2. Juanito is assigned to a van; Pedro uses it tomorrow without moving its inventory.
3. 100 m of cable travels warehouse -> van -> job site with partial consumption and return.
4. A serialized multimeter is currently at a job site while its home location remains a warehouse cabinet.
5. A closed job site retains complete historical movements.
6. Remaining tracked inventory must be explicitly reconciled when a job site closes.
7. Global stock can be aggregated across active locations.
8. A location works before spatial mapping and can gain an AR map later.

## 15. Non-goals for the first implementation slices

Do not implement yet:

- accounting or invoicing;
- ERP replacement;
- customer CRM;
- GPS fleet tracking;
- workforce scheduling;
- route optimization;
- enterprise authorization;
- automatic replenishment;
- shipping/carrier workflows.

The near-term implementation should first establish generic locations and movement semantics, then collaboration and commercial workflows can build on that foundation.

Refs: #50, #48, #40, #20.
