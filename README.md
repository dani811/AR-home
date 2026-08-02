# AR Home Inventory

Inventario espacial para vivienda con posicionamiento 3D, guiado AR e integración posterior con Home Assistant.

La rama `develop` contiene ahora el primer vertical slice ejecutable del núcleo espacial. La identidad de un mueble **no depende de que un modelo visual lo clasifique perfectamente**: el alta comienza con una caja 3D orientada confirmada por el usuario y la relocalización combina geometría, posición, categoría y evidencia visual.

## Estado actual

Implementado en `backend/`:

- Java 21 y Spring Boot 3.
- PostgreSQL con migraciones Flyway.
- Espacios independientes y marcos de coordenadas persistentes.
- Instancias físicas de muebles con pose, bounding box, método de reconocimiento y confianza.
- Compartimentos anidados: módulos, cajones, baldas y organizadores.
- Posición de objetos relativa al mueble o compartimento.
- Resolución de pose mundial mediante composición de transformaciones.
- Ranking de relocalización de muebles conocidos.
- Validación de consistencia entre muebles, compartimentos y objetos.
- Pruebas unitarias de matemáticas 3D y lógica espacial.
- CI de Maven mediante GitHub Actions.

Consulta [`docs/spatial-model.md`](docs/spatial-model.md) para las decisiones de dominio.

## Jerarquía espacial

```text
world
└── space
    └── furniture instance
        └── compartment
            └── nested compartment
                └── item placement
```

Cada nivel almacena una transformación local con traslación en metros y rotación mediante cuaternión. Un cambio en la pose de un mueble desplaza todo su contenido sin reescribir cada objeto.

## Ejecución local

Requisitos: Docker y Docker Compose.

```bash
docker compose up
```

- API: `http://localhost:8080`
- Health: `http://localhost:8080/actuator/health`
- PostgreSQL: `localhost:5432`

También puede ejecutarse únicamente el backend con Java 21 y Maven:

```bash
cd backend
mvn spring-boot:run
```

## API espacial inicial

```text
POST /api/v1/spatial/spaces
POST /api/v1/spatial/spaces/{spaceId}/furniture
POST /api/v1/spatial/spaces/{spaceId}/furniture/relocalization-candidates
POST /api/v1/spatial/furniture/{furnitureId}/compartments
PUT  /api/v1/spatial/items/{itemId}/placement
GET  /api/v1/spatial/items/{itemId}/placement
GET  /api/v1/spatial/items/{itemId}/world-pose
```

### Alta manual asistida de un mueble

```json
{
  "name": "Armario dormitorio",
  "category": "WARDROBE",
  "spaceTransform": {
    "translation": { "x": 1.2, "y": 0.0, "z": 2.8 },
    "rotation": { "x": 0.0, "y": 0.0, "z": 0.0, "w": 1.0 }
  },
  "bounds": { "width": 2.0, "height": 2.4, "depth": 0.6 },
  "recognitionMode": "MANUAL_BOUNDING_BOX",
  "confidence": 1.0
}
```

### Relocalización

El cliente AR puede enviar una pose aproximada, dimensiones observadas y similitudes visuales calculadas localmente. El backend devuelve candidatos con puntuaciones desglosadas, evitando una decisión opaca e irreversible.

## Próximos incrementos

1. Cliente Angular/Three.js para dibujar y ajustar la caja orientada del mueble.
2. Captura WebXR/ARCore y persistencia de observaciones visuales.
3. Relocalización entre sesiones y calibración de espacios.
4. Adaptador con Homestead Inventory y Home Assistant.
5. Navegación AR hasta el compartimento y el objeto.

## Licencia

Proyecto propietario en evolución. Antes de distribución pública o explotación comercial debe añadirse al repositorio el texto de licencia definitivo.
