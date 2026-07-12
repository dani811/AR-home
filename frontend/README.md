# Frontend

Aplicación Angular de AR Home.

## Responsabilidades

- gestión de proyectos y espacios;
- visor de plano 2D;
- visor 3D con Three.js y modelos GLB;
- navegación mediante nodos fotográficos;
- selección de muebles por raycasting;
- gestión de contenedores y objetos;
- seguimiento del progreso de procesamiento mediante SSE o WebSocket;
- modo básico de captura web cuando el dispositivo no disponga de integración nativa.

## Estructura prevista

- `core`: autenticación, cliente API y configuración.
- `features/projects`: proyectos y espacios.
- `features/capture`: sesiones y control de calidad.
- `features/map-2d`: plano semántico.
- `features/viewer-3d`: escena Three.js.
- `features/inventory`: muebles, contenedores y objetos.
- `shared`: componentes y contratos compartidos.
