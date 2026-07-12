# Capture Mobile

Cliente de captura espacial para Android e iOS.

## Estrategia

La experiencia compartida se implementará con Angular/Capacitor. Las capacidades espaciales críticas se expondrán mediante plugins nativos:

- Android: ARCore, Camera2 y sensores.
- iOS: ARKit y RoomPlan cuando el dispositivo lo soporte.
- Web: WebXR y vídeo como modo de compatibilidad, no como captura profesional principal.

## Datos producidos

- fotogramas RGB;
- profundidad cuando esté disponible;
- parámetros intrínsecos de cámara;
- muestras IMU;
- poses de cámara;
- estado y confianza del tracking;
- eventos de marcado manual;
- metadatos del dispositivo.

Los datos deben respetar `contracts/capture-session.schema.json` y subirse por bloques reanudables para soportar trabajo offline y redes inestables.
