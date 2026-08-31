# Inventory identifiers and documents v1

Status: approved for implementation

Issue: #43

## 1. User value

AR Home must preserve the labels and documents that make a physical object usable after its box or paperwork is lost: serial numbers, barcodes, pairing/onboarding codes, manuals, quick-start guides, invoices and warranty evidence.

These records belong to inventory. They are not spatial anchors and must never be required for AR relocalization.

## 2. Separate semantic identity from barcode symbology

A barcode format does not define what the encoded value means.

Examples:

- EAN-13 can encode a product identifier;
- Code 128 can encode a serial number or arbitrary manufacturer value;
- Data Matrix can encode structured product traceability data;
- QR Code can encode a URL, a device onboarding payload, a serial number or arbitrary text.

Therefore the model stores both:

- `IdentifierType`: semantic meaning;
- `BarcodeSymbology`: physical encoding when known.

The application must not infer sensitivity or business meaning only from the barcode symbology.

## 3. ItemIdentifier

An identifier can belong either to the catalogue item or to one physical unit.

Fields:

- `id`: UUID;
- `itemId`: required;
- `unitId`: optional; when present the identifier belongs to that physical instance;
- `type`;
- `symbology`;
- `rawValue`;
- `normalizedValue` optional;
- `source`;
- `sensitivity`;
- `verified`;
- `capturedAt` optional;
- timestamps.

Initial `IdentifierType` values:

- `GTIN`;
- `EAN`;
- `UPC`;
- `SERIAL`;
- `MANUFACTURER_PART_NUMBER`;
- `MAC_ADDRESS`;
- `MATTER_SETUP`;
- `QR_PAYLOAD`;
- `OTHER`.

Initial `BarcodeSymbology` values:

- `EAN_13`;
- `UPC_A`;
- `CODE_128`;
- `GS1_128`;
- `DATA_MATRIX`;
- `QR_CODE`;
- `NONE`;
- `UNKNOWN`.

Initial `IdentifierSource` values:

- `SCANNED`;
- `MANUAL`;
- `IMPORTED`.

An external identifier never replaces the internal UUID.

## 4. Item versus unit identifiers

Identifiers describing the product/model belong to `InventoryItem`.

Examples:

- GTIN/EAN/UPC;
- manufacturer part number;
- model-level catalogue reference.

Identifiers describing one physical instance belong to `InventoryUnit`.

Examples:

- serial number;
- MAC address;
- device onboarding/pairing payload;
- manufacturer instance identifier.

A serialised item can therefore have both a model GTIN and one or more unit-specific identifiers.

## 5. ItemDocument

Documents and attachments are first-class records rather than free-form notes.

Fields:

- `id`: UUID;
- `itemId`: required;
- `unitId`: optional;
- `type`;
- `storageReference` or URI;
- `mimeType`;
- `language` optional;
- `version` optional;
- `sha256` optional;
- `sensitivity`;
- timestamps.

Initial `DocumentType` values:

- `MANUAL`;
- `QUICK_START`;
- `DATASHEET`;
- `WARRANTY`;
- `INVOICE`;
- `RECEIPT`;
- `PAIRING_LABEL`;
- `PHOTO`;
- `OTHER`.

A model manual normally belongs to `InventoryItem` and is shared by all units. An invoice, unit-specific warranty certificate or pairing-label photo can belong to one `InventoryUnit`.

Binary object storage and upload/download APIs are outside this first persistence slice. The contract stores only an opaque storage reference until the attachment subsystem is specified.

## 6. Sensitivity and secrets

Every identifier/document is classified as:

- `PUBLIC`;
- `PRIVATE`;
- `SECRET`.

Typical defaults:

- GTIN/EAN/public manual: `PUBLIC`;
- serial number, invoice, receipt: `PRIVATE`;
- pairing/onboarding credentials such as Matter setup payloads: `SECRET`.

Sensitivity is explicit and can be overridden where needed.

### SECRET requirements

A `SECRET` value:

- must not be emitted in application logs or exception messages;
- must not be indexed by general text search;
- must not appear in list/search DTOs;
- must only be returned by an explicit secret-reveal use case;
- requires encrypted-at-rest persistence before real secret payloads are enabled;
- must be redacted from diagnostics and telemetry.

Until encrypted-at-rest support exists, production persistence of raw `SECRET` values is forbidden. The model may store secret metadata and a protected storage reference.

## 7. Scanner behaviour contract

Future mobile registration can scan one camera frame and produce multiple candidate identifiers.

Example:

```text
scan label
  -> EAN-13 candidate -> item GTIN/EAN
  -> Code 128 candidate -> unit serial
  -> QR candidate -> URL or onboarding payload
  -> user confirms classification
```

Automatic parsing may suggest identifier type, but human confirmation remains authoritative when the payload is ambiguous or sensitive.

OCR, camera scanning and UI are not implemented by this specification.

## 8. Search behaviour

General inventory search may match:

- item name;
- brand/model;
- public/private non-secret identifiers where authorization permits;
- document metadata.

It must never index or match raw `SECRET` payloads.

Serial number lookup is an explicit supported use case. Secret pairing-code lookup should return the owning item/unit without returning the secret value until an explicit reveal action occurs.

## 9. Persistence constraints

The first schema slice must enforce:

- `item_id` always present;
- `unit_id`, when present, must reference a unit of the same `item_id`;
- identifier type and sensitivity always present;
- document type and sensitivity always present;
- values that are intended to be unique may receive scoped unique indexes in a later normalization slice rather than assuming all manufacturer identifiers are globally unique;
- item or unit deletion cascades to its identifier/document metadata as appropriate.

## 10. Acceptance criteria

The model can represent:

1. one item EAN plus a different Code 128 serial for a concrete unit;
2. a QR URL to a public PDF manual;
3. a Matter setup QR classified as `SECRET` without exposing it in general inventory responses;
4. one manual shared by all units of a model;
5. one invoice associated only with a specific purchased unit;
6. a serial-number search that resolves the physical unit;
7. an ambiguous QR payload whose semantic type remains `QR_PAYLOAD` until confirmed;
8. identifiers and documents without any dependency on ARCore or spatial markers.

## 11. First implementation slice

Implementation is limited to backend domain contracts, additive PostgreSQL migration and invariant tests for:

- `ItemIdentifier` metadata;
- `ItemDocument` metadata;
- identifier/document enums;
- sensitivity rules;
- parent item/unit consistency.

No scanner, OCR, document upload, secret-reveal API or encrypted secret storage is included yet.
