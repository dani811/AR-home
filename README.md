# AR Home Inventory

Inventario espacial para vivienda con posicionamiento 3D, guiado AR e integración posterior con Home Assistant.

La identidad de un mueble **no depende de que un modelo visual lo clasifique perfectamente**: el alta comienza con una caja 3D orientada confirmada por el usuario y la relocalización combina geometría, posición, categoría y evidencia visual.

## Estado actual

### Backend espacial

Implementado en `backend/`:

- Java 21 y Spring Boot 3.
- PostgreSQL con migraciones Flyway.
- Espacios independientes y marcos de coordenadas persistentes.
- Instancias físicas de muebles con pose, bounding box, método de reconocimiento y confianza.
- Compartimentos anidados: módulos, cajones, baldas y organizadores.
- Posición de objetos relativa al mueble o compartimento.
- Resolución de pose mundial mediante composición de transformaciones.
- Ranking de relocalización de muebles conocidos.
- Validación de consistencia y pruebas de matemáticas 3D.

Consulta [`docs/spatial-model.md`](docs/spatial-model.md) para las decisiones de dominio.

### Editor Angular + Three.js

Implementado en `frontend/`:

- Angular standalone con TypeScript estricto.
- Visor Three.js responsive.
- Caja 3D orientada con modos mover, girar y escalar.
- `OrbitControls` y `TransformControls` con snaps de 5 cm y 5 grados.
- Edición numérica de posición, dimensiones y giro vertical.
- Conversión directa a `Transform3D` y `Bounds3D` del backend.
- Creación de espacios y registro de muebles mediante la API real.
- Liberación explícita de controles y recursos WebGL.
- Build de producción validado mediante GitHub Actions.

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

### Backend y PostgreSQL

Requisitos: Docker y Docker Compose.

```bash
docker compose up
```

- API: `http://localhost:8080`
- Health: `http://localhost:8080/actuator/health`
- PostgreSQL: `localhost:5432`

### Editor web

Requisito: Node.js 22.

```bash
cd frontend
npm install
npm start
```

El proxy de desarrollo reenvía `/api` a `http://localhost:8080`. Abre `http://localhost:4200`.

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
    "translation": { "x": 1.2, "y": 1.2, "z": 2.8 },
    "rotation": { "x": 0.0, "y": 0.0, "z": 0.0, "w": 1.0 }
  },
  "bounds": { "width": 2.0, "height": 2.4, "depth": 0.6 },
  "recognitionMode": "MANUAL_BOUNDING_BOX",
  "confidence": 1.0,
  "visualDescriptor": null
}
```

## Próximos incrementos

1. Captura móvil con `getUserMedia` y superposición del editor sobre la cámara.
2. WebXR/ARCore para obtener pose y hit-test en dispositivos compatibles.
3. Persistencia de observaciones visuales y relocalización entre sesiones.
4. Editor visual de cajones, baldas y otros compartimentos.
5. Adaptador con Homestead Inventory y Home Assistant.
6. Navegación AR hasta el compartimento y el objeto.

## Licencia

Proyecto propietario en evolución. Antes de distribución pública o explotación comercial debe añadirse al repositorio el texto de licencia definitivo.
