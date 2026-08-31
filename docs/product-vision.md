# Product vision: spatial micro-logistics
Status: direction-setting
Issue: #48
## 1. Vision
AR Home starts as a personal inventory product, but the long-term product is a lightweight spatial logistics platform.
It should know:
- what an object is and which physical unit it is;
- how many exist and what stock policy applies;
- whether a unit is usable, available, reserved, loaned, lost or under repair;
- who currently has it;
- what it belongs to, contains or works with;
- where it normally belongs and where it is physically located;
- how to guide a person to it;
- what happened to it over time.
Working statement:
> Know what you have. Know where it is. Operate it in the real world.
The home is the first proving environment, not a permanent domain boundary.
## 2. Product thesis
Small physical operations often sit between two bad extremes: ad-hoc spreadsheets/notes and heavyweight warehouse software.
Our thesis is that they need a digital inventory that remains connected to the real environment.
The product combines:
1. identity and quantity;
2. custody, condition and history;
3. logical and physical storage structure;
4. visual/spatial localization and interaction.
AR is not the whole product. It is a differentiating interface and localization capability on top of a reusable inventory/logistics core.
## 3. Core product stack
```text
IDENTITY   item / unit / identifiers / documents
INVENTORY  quantity / min-max / relations / kits / lots later
CUSTODY    available / reserved / in use / loaned / lost / repair
LOGISTICS  receipt / issue / transfer / adjustment / return
SPACE      site / room / vehicle / rack / cabinet / drawer / bin
SPATIAL UX find / guide / verify / pick / return / overlay
```
Each layer must remain useful without requiring the layer below it.
## 4. Target users
### Stage A - personal inventory
Initial environments: home, garage, storage room, hobby workshop, collections and equipment.
Value: know what exists, find it quickly, retain serials/documents, track broken/loaned/missing items and manage consumable minimums.
### Stage B - professionals and micro-businesses
Natural early commercial targets include electricians, plumbers, installers, maintenance teams, repair workshops, makerspaces, production/event crews, small laboratories, rental operations and field-service teams with vans.
These users have valuable assets, consumables and spares without necessarily needing a heavyweight WMS.
### Stage C - lightweight warehouses
Later environments include small warehouses, stock rooms, service depots, mobile stock, multi-site maintenance and small ecommerce fulfillment.
The inventory domain must support these without a rewrite.
## 5. Differentiation
The goal is not another inventory CRUD. The defensible combination is:
### Spatial inventory
A digital record can resolve to a real physical location, not only a textual location code.
### Markerless relocalization
Mapped environments are recognized through natural visual/geometric evidence. QR/barcodes may identify products or support workflows, but never become mandatory spatial anchors.
### Visual retrieval and operations
The user can be guided to the correct room, rack, cabinet, drawer, bin or zone. The camera can support finding, picking, returning, verification, cycle counting and set completeness.
### Physical-unit passport
A serialized unit accumulates identity, documents, condition, custody, repairs, relations and location history.
### Flexible templates
Different industries can model their own attributes without backend deployments while universal logistics concepts remain first-class.
## 6. Product evolution
### Phase 0 - prove spatial viability
The #20 gate remains foundational. Do not build commercial promises on unproven relocalization accuracy.
### Phase 1 - useful inventory
Deliver value even before advanced AR is perfect:
- item and unit catalog;
- stock policy;
- condition and availability;
- loans and relations;
- identifiers and documents;
- templates/custom attributes;
- conventional hierarchical placement;
- search and retrieval.
### Phase 2 - operational inventory
Add:
- stock movement ledger;
- receipt, issue/consumption, transfer, adjustment and return;
- reservation;
- last verified timestamp;
- cycle count and audit history;
- lot/batch and expiry where justified.
### Phase 3 - micro-logistics
Generalize storage beyond household furniture:
```text
Site -> Area -> Rack/Cabinet/Vehicle -> Shelf/Drawer/Bin
```
Both must fit:
```text
Home > Bedroom > Dresser > Drawer 2
Warehouse A > Aisle 4 > Rack B > Shelf 2 > Bin 07
```
This likely requires a generic `LocationNode` or equivalent, linked to the richer spatial/furniture model.
### Phase 4 - spatial logistics
Add guided find, guided return, visual picking routes, AR storage overlays, visual verification, spatially assisted cycle counts and multiple mapped sites.
### Phase 5 - platform
Only after repeated usage: public API, webhooks, shared template packs, import/export ecosystem, partner integrations, optional marketplace and vertical workflows.
## 7. AI role
AI reduces registration and search friction; it is not the source of truth.
High-value uses:
- recognize labels/packaging and read serials/model numbers;
- classify codes and suggest item/template;
- extract structured attributes;
- locate likely manuals/datasheets;
- suggest duplicates;
- natural-language inventory queries;
- assist template creation.
Long-term registration goal:
```text
camera -> identify -> extract -> suggest -> confirm -> place
```
Important inventory state remains user- or source-confirmed.
## 8. Monetization thesis
Monetization grows with operational value, collaboration and scale. It must not depend on advertising or selling customer inventory/location data.
Packaging below is a hypothesis to validate, not a commitment to exact limits or prices.
### Free / Personal
Purpose: acquisition and proof of value.
Possible scope: limited inventory/locations, basic search, barcode/identifier capture, basic placement and data export.
The free tier must be genuinely useful.
### Personal Pro
Value trigger: serious personal inventories and advanced enthusiasts.
Potential premium value:
- larger/unlimited inventory;
- documents and richer history;
- advanced templates/custom fields;
- reminders;
- spatial maps and AR find;
- richer backup/export;
- AI-assisted registration allowance.
### Team / SMB
Primary future commercial tier. Value trigger: inventory becomes shared operational infrastructure.
Potential capabilities:
- multiple users, roles and permissions;
- shared sites and locations;
- custody/check-out;
- audit history and movement ledger;
- cycle counts and picking/return workflows;
- multiple vehicles/stock rooms;
- operational dashboards and team spatial maps.
Pricing should mainly reflect collaboration and operational scale, not arbitrary field lock-in.
### Business
Only when demand exists: multiple sites, advanced permissions, API/webhooks, SSO, retention/audit controls, reporting, priority support and integration assistance.
Do not build enterprise overhead before SMB pull exists.
## 9. Additional revenue options
### AI usage
Usage allowance or credits for expensive OCR/visual/document enrichment. Core inventory operations must not require AI credits.
### Advanced spatial capability
Multi-map management and advanced guided workflows may be premium when they provide measurable value.
### Industry/template packs
Paid vertical packs are valid when they contain real workflow/domain value, not merely a list of fields. Examples: electrical installation, workshop tooling, production equipment, IT assets and maintenance spares.
### Template marketplace
A later third-party ecosystem may use revenue sharing after demand and governance are proven.
### Services
For business customers where justified: migration, onboarding, location mapping, workflow configuration and integration support. Services should accelerate SaaS adoption, not turn the product into bespoke consulting.
## 10. Monetization principles
1. No advertising-led model.
2. Do not sell customer inventory, location or operational data.
3. Customer data remains exportable.
4. Core records do not become inaccessible if a paid feature is removed.
5. Charge for operational value, scale, collaboration and expensive compute.
6. Keep pricing simple until evidence justifies complexity.
7. Preserve a path from one user to a team without migrating to a different product.
## 11. Product moat
The moat is the combination:
- structured inventory graph and physical-unit histories;
- relations/kits and configurable templates;
- spatial map linked to inventory;
- reliable relocalization;
- low-friction visual registration;
- operational history/workflow data.
Barcode scanning alone is not a moat. AR alone is not a moat. A generic inventory database alone is not a moat.
## 12. Architecture implications
The direction imposes constraints now:
- inventory remains independent from ARCore;
- spatial providers remain replaceable;
- the domain must not assume a home;
- furniture is useful structure but not the universal logistics abstraction;
- inventory relations and spatial containment remain separate graphs;
- movement history becomes append-oriented rather than mutable stock flags;
- workspace/tenant concepts wait until collaboration requires them;
- item/unit identifiers remain independent of barcode symbology;
- documents and secrets remain explicit domains;
- custom fields never replace first-class business concepts;
- location/inventory data require strong privacy boundaries.
## 13. Product and business metrics
Optimize real-world outcomes, not record count.
Product measures:
- successful item retrievals;
- median search-to-physical-retrieval time;
- percentage of inventory with verified location;
- inventory accuracy after cycle count;
- successful guided picks/returns;
- percentage of serialized assets with known custody;
- time to register an item;
- relocalization success rate and latency.
Business measures:
- activation after first useful find;
- weekly active inventories/teams;
- retained users/teams;
- operational movements per active team;
- conversion after demonstrated value;
- paid retention/churn.
## 14. Go-to-market hypothesis
Do not start by replacing enterprise WMS.
The first commercial wedge is a user currently operating with some mix of spreadsheets, notes, messaging apps, memory, physical labels and ad-hoc storage conventions.
The ideal early business has enough physical complexity to feel pain but not enough process maturity to justify a heavyweight warehouse platform.
We win if setup is dramatically easier than traditional warehouse software and finding/operating stock is materially faster than manual search.
## 15. Risks and guardrails
- Spatial reliability: bad guidance destroys trust; claims remain evidence-gated.
- Registration friction: capture speed is a strategic metric.
- Over-modeling: do not build every WMS concept before demand.
- Enterprise distraction: avoid premature SSO/procurement/bespoke integration work.
- Template chaos: preserve stable keys, versioning, validation and first-class concepts.
- Privacy: maps, serials, invoices and pairing credentials are sensitive product data.
## 16. Current non-goals
This vision does not authorize immediate implementation of:
- full WMS workflows;
- ERP/accounting replacement;
- shipping-carrier orchestration;
- enterprise SSO;
- marketplace infrastructure;
- autonomous visual stock counting;
- pricing enforcement;
- multi-tenant SaaS infrastructure before collaboration requires it.
Immediate work remains proving localization and building a robust inventory foundation.
## 17. Directional summary
```text
personal inventory
  -> operational inventory
    -> micro-logistics
      -> spatial logistics
        -> extensible platform
```
Durable principle:
> Start in the home if that is where we can prove the experience. Build the domain so the same object can later live in a workshop, van, stock room or warehouse without changing what the product fundamentally is.
Refs: #20, #40, #43, #47, #48.
