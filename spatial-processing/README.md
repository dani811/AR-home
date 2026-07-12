# Spatial Processing

Workers de procesamiento espacial y visión artificial.

## Pipeline inicial

1. Validación e ingestión de la sesión.
2. Selección de keyframes.
3. Optimización del grafo de poses y cierre de bucle.
4. Fusión RGB-D y generación de nube de puntos.
5. Reconstrucción y simplificación de malla.
6. Extracción de paredes, suelo, puertas y habitaciones.
7. Generación del plano 2D semántico.
8. Detección y segmentación de muebles.
9. Fusión multivista y creación de instancias persistentes.
10. Generación de nodos y aristas del recorrido virtual.

## Stack objetivo

- Python 3.12
- OpenCV
- Open3D
- PyTorch
- Pydantic
- workers RabbitMQ

Cada trabajo debe ser idempotente, versionar sus algoritmos y publicar artefactos reproducibles en almacenamiento compatible con S3.
