# Product vision: spatial micro-logistics

Status: direction-setting

Issue: #48

## 1. Vision

AR Home starts as a personal inventory product, but the long-term product is not a home inventory application.

The product should evolve into a lightweight spatial logistics platform that knows:

- what an object is;
- which physical unit it is;
- how many exist;
- whether it is usable and available;
- who currently has it;
- what it belongs to or works with;
- where it normally belongs;
- where it is physically located;
- how to guide a person to it;
- what happened to it over time.

The working product statement is:

> Know what you have. Know where it is. Operate it in the real world.

The home is the first proving environment, not a permanent domain boundary.

## 2. Product thesis

Traditional inventory tools are good at representing digital records. Warehouse systems are good at representing coded locations and process. Neither assumption should define this product.

Our thesis is that small physical operations need a digital representation that remains connected to the real environment.

The product therefore combines four layers:

1. inventory identity and quantity;
2. custody, condition and history;
3. logical and physical storage structure;
4. visual/spatial localization and interaction.

AR is not the whole product. It is a differentiating interface and localization capability on top of a reusable inventory and logistics core.

## 3. Core product model

The conceptual stack is:

```text
IDENTITY
what is it / which unit / identifiers / documents

INVENTORY
quantity / min-max / lots later / relations / kits

CUSTODY
available / reserved / in use / loaned / lost / repair

LOGISTICS
receipts / issues / transfers / adjustments / returns

SPACE
site / room / vehicle / rack / cabinet / drawer / bin

SPATIAL UX
find / guide / verify / pick / return / overlay
```

Each layer must remain useful without the layers below it.

## 4. Target users by evolution stage

### Stage A - personal inventory

Initial environment:

- home;
- garage;
- storage room;
- hobby workshop;
- collections and equipment.

Value:

- remember what exists;
- find an object quickly;
- keep serials, invoices, pairing labels and manuals;
- know what is broken, loaned or missing;
- keep minimum stock for consumables.

### Stage B - professionals and micro-businesses

Natural early commercial targets:

- electricians;
- plumbers;
- installers;
- maintenance teams;
- repair workshops;
- makerspaces;
- photographers and production crews;
- event equipment teams;
- small laboratories;
- tool and equipment rental operations.

These users often have valuable assets, consumables and spare parts without needing a heavyweight WMS.

### Stage C - lightweight warehouses and field operations

Later environments:

- small warehouses;
- stock rooms;
- service depots;
- vans and mobile stock;
- multi-site maintenance operations;
- small ecommerce fulfillment.

The product should handle these without rewriting the inventory domain.

## 5. Differentiation

The goal is not to compete as another inventory CRUD.

The defensible product combination is:

### Spatial inventory

A digital inventory record can resolve to a real physical location, not only a textual code.

### Markerless relocalization

The spatial system should recognize mapped environments using natural visual/geometric evidence. QR codes and barcodes may identify products or support workflows, but they must never be required for spatial relocalization.

### Visual retrieval

The user can ask for an object and be guided to the correct room, furniture, rack, drawer, bin or zone.

### Visual operations

The camera can become an operational interface for:

- finding;
- picking;
- returning;
- verification;
- cycle counting;
- low-stock inspection;
- set completeness.

### Physical-unit passport

A serialized unit can accumulate a durable history of identity, documents, condition, custody, repairs, relationships and location.

### Flexible templates

Different industries can model their own objects without backend deployments while universal logistics concepts remain first-class domain objects.

## 6. Product evolution

### Phase 0 - prove spatial viability

The current #20 gate remains foundational.

Prove that a fresh session can relocalize reliably enough to support useful object retrieval. Do not build a commercial promise on unproven localization accuracy.

### Phase 1 - useful inventory

Deliver a product that is valuable even before advanced AR is perfect:

- catalog items and physical units;
- stock policy;
- condition and availability;
- loans;
- relationships;
- identifiers and documents;
- templates and custom attributes;
- conventional hierarchical placement;
- search and retrieval.

### Phase 2 - operational inventory

Introduce movement and verification semantics:

- stock movement ledger;
- receipt;
- consumption/issue;
- transfer;
- adjustment;
- return;
- reservations;
- last verified timestamp;
- cycle counts;
- audit history;
- lot/batch and expiry where justified.

### Phase 3 - micro-logistics

Generalize physical storage beyond household furniture:

```text
Site
  -> Area
     -> Rack / Cabinet / Vehicle
        -> Shelf / Drawer / Bin
```

The domain must be able to represent both:

```text
Home > Bedroom > Dresser > Drawer 2
```

and:

```text
Warehouse A > Aisle 4 > Rack B > Shelf 2 > Bin 07
```

This likely requires a generic `LocationNode` or equivalent logistics abstraction while preserving links to the richer spatial/furniture model.

### Phase 4 - spatial logistics

Spatial capabilities become operational:

- guided find;
- guided return-to-home-location;
- visual picking routes;
- AR overlays for storage zones;
- visual verification of expected location;
- spatially assisted cycle counts;
- multiple mapped areas/sites.

### Phase 5 - platform

Only after repeated real usage:

- public API;
- webhooks/integrations;
- shared template packs;
- import/export ecosystem;
- partner integrations;
- optional template marketplace;
- specialized vertical workflows.

## 7. AI role

AI should reduce operational friction, not become the source of truth.

High-value uses:

- recognize labels and packaging;
- read serials and model numbers;
- classify scanned codes;
- suggest an item/template;
- extract structured attributes;
- find likely manuals/datasheets;
- suggest duplicate items;
- natural-language inventory queries;
- help build custom templates.

The user or authoritative source confirms important inventory state.

A long-term registration goal is:

```text
camera -> identify -> extract -> suggest -> confirm -> place
```

rather than a long manual form.

## 8. Monetization thesis

Monetization should grow with operational value, collaboration and scale. It must not depend on advertising or selling customer inventory data.

Packaging below is a hypothesis to validate, not a commitment to exact limits or prices.

### Free / Personal

Purpose: acquisition and proof of value.

Possible scope:

- limited inventory size or locations;
- basic categories and search;
- basic barcode/identifier capture;
- basic placement;
- data export.

The free tier should be genuinely useful and should demonstrate the find-and-organize loop.

### Personal Pro

Value trigger: serious personal inventories and advanced enthusiasts.

Potential premium capabilities:

- larger/unlimited inventory;
- documents and richer history;
- advanced templates/custom fields;
- reminders;
- spatial maps and AR find;
- richer backup/export;
- advanced AI-assisted registration allowance.

### Team / SMB

Primary future commercial tier.

Value trigger: inventory becomes shared operational infrastructure.

Capabilities may include:

- multiple users;
- roles and permissions;
- shared sites/locations;
- custody/check-out;
- audit history;
- stock movement ledger;
- cycle counts;
- picking/return workflows;
- multiple vehicles or stock rooms;
- operational dashboards;
- team-level spatial maps.

Pricing should primarily reflect collaboration and operational scale rather than locking individual records behind arbitrary fields.

### Business

For organizations requiring controls and integration:

- multiple sites;
- advanced permissions;
- API/webhooks;
- SSO when demand exists;
- retention/audit controls;
- advanced reporting;
- priority support;
- deployment/integration assistance where commercially justified.

Do not prematurely build enterprise features before SMB pull exists.

## 9. Additional monetization options

Potential optional revenue streams:

### AI usage

Usage-based allowance or credits for expensive operations such as visual/OCR enrichment or external document enrichment.

Core inventory actions must not require AI credits.

### Advanced spatial capability

Spatial mapping, multi-map management or advanced guided workflows can be premium capabilities if they provide measurable operational value.

Do not paywall basic ownership or export of inventory data.

### Industry/template packs

Curated templates and workflows for specific verticals may become paid packs if they include meaningful domain work, not merely a list of fields.

Examples:

- electrical installation;
- workshop tooling;
- photography/production;
- IT assets;
- maintenance spares.

### Template marketplace

A later ecosystem may support third-party template/workflow packs with revenue sharing. This requires proven demand and governance before implementation.

### Services

For business customers only when justified:

- data migration;
- initial inventory onboarding;
- location mapping;
- workflow configuration;
- integration support.

Services should accelerate SaaS adoption, not turn the product into bespoke consulting.

## 10. Monetization principles

1. No advertising-led business model.
2. Do not sell customer inventory, location or operational data.
3. Customer data remains exportable.
4. Core records must not become inaccessible when a paid feature is removed.
5. Charge for ongoing operational value, scale, collaboration and expensive compute.
6. Avoid pricing complexity before product-market evidence exists.
7. Preserve a path from single user to team without data migration into a different product.

## 11. Product moat

A sustainable advantage is expected from the combination, not any isolated feature:

- accumulated structured inventory graph;
- physical-unit histories;
- relationships and kits;
- configurable templates;
- spatial map linked to inventory;
- reliable relocalization;
- low-friction visual registration;
- operational history and workflow data;
- increasingly good recommendations from real usage.

Barcode scanning alone is not a moat. AR alone is not a moat. A generic inventory database alone is not a moat.

## 12. Architecture implications

The product direction imposes constraints now:

- inventory must remain independent from ARCore;
- spatial providers remain replaceable;
- the domain must not assume a home;
- furniture is useful spatial structure but not the universal logistics abstraction;
- logical inventory relations and spatial containment remain separate graphs;
- movement history should become append-oriented rather than mutable stock flags;
- workspace/tenant concepts should be introduced only when collaboration requires them, but identifiers should not block that future;
- item/unit identifiers must remain independent of barcode symbology;
- documents and secrets remain explicit domains;
- custom fields must not replace first-class business concepts;
- location and inventory data need strong privacy boundaries.

## 13. North-star outcomes

The product should optimize real-world outcomes rather than number of records created.

Candidate north-star measures:

- successful item retrievals;
- median time from search to physical retrieval;
- percentage of inventory with verified location;
- inventory accuracy after cycle count;
- successful guided picks/returns;
- percentage of serialized assets with current custody known.

Supporting product metrics:

- time to register an item;
- relocalization success rate and latency;
- search-to-find conversion;
- weekly active inventories;
- retained active users/teams;
- number of operational movements per active team;
- paid conversion after demonstrated value;
- paid retention/churn.

## 14. Go-to-market hypothesis

Do not start by selling a replacement for enterprise WMS.

The first commercial wedge should be users currently operating with combinations of:

- spreadsheets;
- notes;
- messaging apps;
- memory;
- physical labels;
- ad-hoc storage conventions.

The ideal early business has enough physical complexity to feel pain, but not enough process maturity to justify a heavyweight warehouse system.

The product wins if setup is dramatically easier than deploying traditional warehouse software and retrieval is materially faster than searching manually.

## 15. Risks

### Spatial reliability

If localization is unreliable, AR guidance damages trust. Spatial claims must remain gated by measured evidence.

### Registration friction

A powerful model is irrelevant if entering inventory takes too long. Capture speed is a strategic metric.

### Over-modeling

Do not build every WMS concept before users need it. Prefer independently useful slices.

### Enterprise distraction

SSO, complex procurement and bespoke integrations can consume the roadmap before product-market fit. Avoid them until pull is demonstrated.

### Template chaos

Custom fields can fragment data. Preserve stable keys, versioning, validation and first-class universal concepts.

### Privacy

Spatial maps, equipment inventories, serials, invoices and pairing credentials are sensitive. Privacy/security are product properties, not later add-ons.

## 16. Non-goals for the current stage

This vision does not authorize immediate implementation of:

- full WMS workflows;
- ERP replacement;
- accounting;
- shipping-carrier orchestration;
- enterprise SSO;
- marketplace infrastructure;
- autonomous visual stock counting;
- pricing enforcement;
- multi-tenant SaaS infrastructure before collaboration requires it.

The immediate work remains proving localization and building a robust inventory foundation.

## 17. Directional summary

The intended evolution is:

```text
personal inventory
    -> operational inventory
        -> micro-logistics
            -> spatial logistics
                -> extensible platform
```

The durable product principle is:

> Start in the home if that is where we can prove the experience. Build the domain so the same object can later live in a workshop, van, stock room or warehouse without changing what the product fundamentally is.

Refs: #20, #40, #43, #47.