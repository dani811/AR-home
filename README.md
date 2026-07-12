# 📦 AR Home Inventory

Plataforma de inventario espacial para viviendas, almacenes y comercios. Permite capturar recorridos interiores, generar representaciones 2D/3D, identificar muebles y vincular objetos de inventario a ubicaciones persistentes.

## Objetivos

- inventario jerárquico por espacio, habitación, mueble y contenedor;
- recorrido virtual basado en keyframes y poses de cámara;
- plano 2D semántico y modelo 3D GLB;
- captura móvil con ARCore y RoomPlan/ARKit;
- reconocimiento y seguimiento multivista de muebles;
- integración opcional con Home Assistant.

## Arquitectura

```text
AR-home/
├── frontend/                 # Angular + Three.js
├── backend/                  # Java 21 + Spring Boot 3
├── capture-mobile/           # Capacitor + adaptadores AR nativos
├── spatial-processing/       # Python/OpenCV/Open3D/PyTorch
├── contracts/                # Esquemas espaciales versionados
├── infrastructure/           # PostGIS, RabbitMQ y MinIO
├── ha-panel/                 # Panel de Home Assistant
├── home-assistant-addon/     # Empaquetado para Home Assistant
└── docs/                     # Arquitectura y decisiones técnicas
```

Consulta [`docs/architecture.md`](docs/architecture.md) para el diseño del sistema y [`contracts/capture-session.schema.json`](contracts/capture-session.schema.json) para el primer contrato de captura.

## Stack objetivo

| Área | Tecnología |
|---|---|
| Web | Angular, Three.js, WebXR básico |
| Backend | Java 21, Spring Boot 3 |
| Datos | PostgreSQL, PostGIS |
| Mensajería | RabbitMQ |
| Artefactos | S3 / MinIO |
| Captura | Capacitor, ARCore, ARKit, RoomPlan |
| Procesamiento | Python 3.12, OpenCV, Open3D, PyTorch |
| Integraciones | REST, WebSocket, MQTT, Home Assistant |

## Desarrollo dirigido por especificaciones

AR Home utiliza GitHub Spec Kit con versión fijada. La constitución del proyecto, los overrides y los roles de revisión viven en:

- [`.specify/memory/constitution.md`](.specify/memory/constitution.md)
- [`.specify/templates/overrides/`](.specify/templates/overrides/)
- [`AGENTS.md`](AGENTS.md)
- [`docs/spec-kit.md`](docs/spec-kit.md)

Instalación reproducible:

```bash
bash scripts/setup-speckit.sh
```

Cada feature sigue el flujo: especificar, aclarar, planificar, generar tareas atómicas, analizar consistencia, implementar y converger.

## Estado

El repositorio está en fase de bootstrap arquitectónico. La prioridad es cerrar el contrato de captura y construir una primera vertical funcional:

1. crear un espacio;
2. iniciar una sesión de captura;
3. almacenar poses y keyframes;
4. generar un recorrido navegable;
5. marcar muebles manualmente;
6. vincular inventario.

La reconstrucción automática avanzada y el reconocimiento de muebles se incorporarán después de validar esa vertical.

## Desarrollo local

Los servicios base pueden iniciarse con:

```bash
docker compose -f infrastructure/docker-compose.yml up -d
```

Las credenciales incluidas son únicamente para desarrollo local.

## Licencia

Business Source License 1.1, pendiente de concretar la fecha y licencia de cambio antes de publicación comercial.
