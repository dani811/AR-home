# Modelo espacial de AR Home

## Decisión principal

El sistema no depende de reconocer automáticamente un mueble. El alta fiable comienza con una **caja tridimensional orientada confirmada por el usuario**. La visión artificial queda como ayuda para proponer la caja, relocalizar una instancia conocida y elevar su confianza.

## Jerarquía de coordenadas

```text
world
└── space
    └── furniture instance
        └── compartment
            └── nested compartment
                └── item placement
```

Cada nodo almacena una transformación local compuesta por:

- Traslación en metros: `(x, y, z)`.
- Rotación mediante cuaternión normalizado: `(x, y, z, w)`.

La pose mundial de un objeto se calcula componiendo las transformaciones desde la raíz:

```text
T_world_item = T_world_space
             · T_space_furniture
             · T_furniture_compartment...
             · T_container_item
```

Una recalibración de la habitación o el movimiento de un mueble no obliga a modificar todos sus objetos.

## Agregados implementados

### Space

Representa un mapa espacial independiente: vivienda, garaje, trastero o planta. `worldTransform` permite relacionarlo posteriormente con un gemelo digital global.

### FurnitureInstance

Representa **ese mueble físico concreto**, no una categoría genérica. Incluye:

- Pose relativa al espacio.
- Dimensiones de su bounding box.
- Método de reconocimiento.
- Confianza.
- Descriptor visual opcional.

### Compartment

Representa baldas, cajones, puertas, módulos y organizadores. Admite anidación recursiva y mantiene su pose relativa al padre.

### ItemPlacement

Enlaza un identificador de inventario externo con una pose relativa al mueble o compartimento. Registra precisión, método de captura y confianza.

## Relocalización de muebles conocidos

El endpoint de relocalización no intenta decidir con un único clasificador opaco. Ordena las instancias conocidas combinando:

- Proximidad a la pose observada: 45 %.
- Similitud de dimensiones: 30 %.
- Similitud visual calculada por el cliente: 20 %.
- Coincidencia de categoría: 5 %.

La respuesta incluye cada puntuación parcial. Los pesos son una primera política explícita y deberán calibrarse con datos reales.

## API inicial

```text
POST /api/v1/spatial/spaces
POST /api/v1/spatial/spaces/{spaceId}/furniture
POST /api/v1/spatial/spaces/{spaceId}/furniture/relocalization-candidates
POST /api/v1/spatial/furniture/{furnitureId}/compartments
PUT  /api/v1/spatial/items/{itemId}/placement
GET  /api/v1/spatial/items/{itemId}/placement
GET  /api/v1/spatial/items/{itemId}/world-pose
```

## Flujo de producto recomendado

1. El usuario selecciona una estancia o crea un espacio.
2. La cámara propone un volumen de mueble; el usuario corrige sus ocho esquinas o dimensiones.
3. Se guarda una `FurnitureInstance` con `MANUAL_BOUNDING_BOX`.
4. El usuario añade baldas/cajones mediante divisiones del volumen local.
5. Los objetos se posicionan respecto al compartimento, nunca directamente respecto a la cámara.
6. Observaciones posteriores generan descriptores visuales para relocalización, pero no sustituyen la identidad persistida.

## Siguiente vertical slice

El siguiente incremento debe añadir captura WebXR/ARCore, almacenamiento de observaciones visuales y un endpoint de recalibración del espacio que mantenga estables los muebles y objetos ya registrados.
