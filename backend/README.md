# Backend

API principal de AR Home.

## Stack objetivo

- Java 21
- Spring Boot 3
- Maven Wrapper
- PostgreSQL + PostGIS
- Flyway
- RabbitMQ
- S3/MinIO

## Límites modulares iniciales

- `identity`: usuarios, organizaciones y permisos.
- `spaces`: sites, edificios, plantas y habitaciones.
- `capture`: sesiones, dispositivos, frames y estados de procesamiento.
- `spatial`: anclajes, rutas, modelos 2D/3D y publicaciones.
- `inventory`: muebles, contenedores, objetos y movimientos.
- `integrations`: Home Assistant, MQTT y webhooks.

La primera implementación debe mantenerse como monolito modular. Los workers espaciales se ejecutan fuera de la JVM y se comunican mediante contratos versionados y cola de trabajos.
