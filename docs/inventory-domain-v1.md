# Inventory domain v1

Status: proposed

Issue: #40

## 1. User value

AR Home must not stop at answering where an object is. It must also answer what the object is, how many exist, whether enough stock remains, whether a concrete unit is usable or unavailable, whether it belongs to another object or set, and whether it is currently outside the home because it is on loan.

This domain is independent from localization. Spatial placement answers where an object belongs; inventory answers identity, quantity, state, custody and relationships.

## 2. Core modeling decision

The model separates an item definition from a physical instance.

### InventoryItem

Represents the thing being catalogued as a product/type, for example:

- `Pila AA Energizer`;
- `Tornillo M4 x 20`;
- `Taladro Bosch GSB 18V`.

Attributes that apply to every instance belong here.

### InventoryUnit

Represents a concrete physical instance when individual tracking matters, for example:

- one specific drill with a serial number;
- one laptop;
- one camera body;
- one battery pack.

Attributes that can differ per physical instance belong here.

### Tracking mode

`InventoryItem.trackingMode` is one of:

- `BULK`: quantity-based tracking; no unit row is required for every piece;
- `SERIALIZED`: each relevant physical instance has an `InventoryUnit`.

This prevents stock policy from being mixed with per-instance state.

## 3. InventoryItem

Required:

- `id`: internal UUID;
- `name`;
- `categoryId`;
- `trackingMode`;
- `unitOfMeasure`;
- timestamps.

Optional descriptive data:

- description;
- brand;
- model;
- manufacturer part number;
- internal SKU;
- GTIN/EAN/UPC;
- notes.

Search metadata:

- tags;
- aliases/synonyms in a later slice.

External identifiers never replace the internal identifier.

## 4. Categories and tags

Categories are hierarchical and user-extensible.

Example:

```text
Herramientas
  -> Herramientas electricas
     -> Taladros

Ferreteria
  -> Fijacion
     -> Tornillos
```

A category expresses the primary taxonomy. Tags express orthogonal search facets such as `12V`, `coche`, `camping`, `repuesto`, `USB-C`.

Categories must not be a hard-coded Java enum.

## 5. Stock policy

Stock policy belongs to `InventoryItem`.

Fields:

- `minStock` optional;
- `maxStock` optional;
- `reorderPoint` optional;
- later: preferred replenishment quantity.

Invariants:

- values are non-negative;
- if min and max are both present, `minStock <= maxStock`;
- if reorder point is present and max is present, `reorderPoint <= maxStock`.

For serialized items, available stock can be derived from active units. For bulk items, quantity will be maintained through stock movements/balances in a later slice.

Do not store a manually maintained `lowStock` boolean. It is derived from policy and current quantity.

## 6. InventoryUnit

Fields:

- `id`: UUID;
- `itemId`;
- serial number / instance identifier optional;
- `condition`;
- `availability`;
- acquired date optional;
- purchase price optional;
- warranty until optional;
- notes;
- timestamps.

### Condition

Physical/functional condition is independent from availability:

- `NEW`;
- `GOOD`;
- `WORN`;
- `DAMAGED`;
- `BROKEN`;
- `UNDER_REPAIR`;
- `UNTESTED`.

### Availability

Custody/availability is separate:

- `AVAILABLE`;
- `RESERVED`;
- `IN_USE`;
- `LOANED`;
- `LOST`;
- `DISPOSED`.

Examples of valid combinations:

- `GOOD + LOANED`;
- `DAMAGED + AVAILABLE`;
- `BROKEN + UNDER_REPAIR` is represented as `condition=BROKEN`, `availability=IN_USE` only if a repair workflow later needs custody semantics; otherwise repair state remains in condition.

No top-level booleans such as `broken`, `loaned`, `available` are introduced.

## 7. Loans

A loan is a historical record, not a flag.

Fields:

- `id`;
- direction: `OUTGOING | INCOMING`;
- counterparty display name;
- optional contact/reference in a later privacy-reviewed slice;
- `lentAt`;
- `expectedReturnAt` optional;
- `returnedAt` optional;
- notes;
- either a serialized unit or an item + quantity for bulk stock.

An active outgoing serialized-unit loan implies that the unit is not currently available.

Returning an object closes the loan record; history is retained.

## 8. Relationships

Inventory relationships form a graph independent from spatial containment.

Initial relation types:

- `COMPONENT_OF`;
- `PART_OF_SET`;
- `ACCESSORY_OF`;
- `CONSUMABLE_FOR`;
- `SPARE_FOR`;
- `COMPATIBLE_WITH`.

A relation may include a required quantity.

Examples:

```text
battery -> COMPONENT_OF -> drill
charger -> ACCESSORY_OF -> drill
drill_bit -> PART_OF_SET -> drill_case
vacuum_bag -> CONSUMABLE_FOR -> vacuum_cleaner
spare_filter -> SPARE_FOR -> air_purifier
```

Future functionality can derive set completeness or missing components from these relationships.

## 9. Location contract

The existing spatial module remains authoritative for placement inside the mapped environment.

Inventory must distinguish:

- home placement: where the object belongs when stored;
- current custody/availability: whether it is currently present, loaned, lost, in use, etc.

A loan must not delete the object's home placement. When the object returns, the application can guide the user back to its expected storage location.

The current `item_placement.item_id` legacy boundary is not changed in this specification PR. Integration will receive its own migration slice.

## 10. Later extensions deliberately anticipated

The v1 model should leave room for, but does not implement yet:

- stock movement ledger: receipt, consumption, adjustment, transfer, return;
- lot/batch and expiry date;
- receipts, invoices and warranty documents;
- maintenance and repair history;
- replacement cost/value;
- supplier/store;
- last physically verified timestamp;
- inventory audit/cycle count;
- automatic low-stock alerts;
- overdue-loan reminders;
- expiry/warranty reminders;
- OCR/barcode-assisted registration;
- AI-assisted category and attribute suggestions;
- category-specific structured attributes;
- ownership/household member;
- multi-user authorization.

Barcode/GTIN assistance is cataloguing input only. It is not a spatial marker and is never required for relocalization.

## 11. First implementation slice

The first code PR after approval of this specification is limited to domain contracts, persistence schema and invariant tests for:

1. `InventoryCategory`;
2. `InventoryItem`;
3. `InventoryUnit`;
4. `ItemRelation`;
5. `Loan`;
6. stock policy fields.

It must be additive and must not modify Android localization or #20 behavior.

## 12. Acceptance criteria

The model can represent all of the following without contradictory flags:

1. Twelve AA batteries with min stock 4 and max stock 24.
2. A unique drill that is in good condition but loaned out since a known timestamp.
3. A broken vacuum cleaner that still has a known home placement.
4. A drill battery that is a component of one item and compatible with another.
5. A drill bit set containing multiple related items.
6. A returned loan whose history remains queryable.
7. An item with a known EAN/GTIN while retaining its internal UUID.
8. A user-defined category hierarchy without application deployment.

## 13. Non-goals

This slice does not implement:

- automatic visual object recognition;
- final inventory UI;
- notifications;
- vendor integrations;
- spatial recognition changes;
- movement accounting;
- household authorization.

## 14. Privacy and security notes

Inventory content, purchase data and loan counterparties are private household data.

The initial model stores only the counterparty display name required to identify a loan. Contact details are deferred until authorization, retention and data minimization rules are specified.

## 15. Rationale

The separation between product/type and physical instance follows common inventory and traceability semantics: product identifiers describe the trade item class while serialisation identifies an individual instance. Min/max replenishment policy is likewise an item-level planning concern.
