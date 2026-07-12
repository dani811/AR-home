# Spec Kit en AR Home

## Versión fijada

AR Home utiliza GitHub Spec Kit `v0.12.11`.

## Instalación local

Requisitos: Git, Python 3.11+ y `uv`.

```bash
uv tool install specify-cli --from git+https://github.com/github/spec-kit.git@v0.12.11
specify self check
specify init --here --force --integration codex --integration-options="--skills"
```

La inicialización debe ejecutarse desde la raíz del repositorio. Antes de aceptar cambios generados por una actualización de Spec Kit, se revisarán por separado de cualquier feature.

## Flujo obligatorio por feature

1. `speckit-constitution`: modificar principios solo cuando cambie la gobernanza.
2. `speckit-specify`: describir problema, usuarios, escenarios y resultados, sin diseñar la solución.
3. `speckit-clarify`: resolver ambigüedades que afecten alcance, seguridad, datos o UX.
4. `speckit-plan`: fijar arquitectura, contratos, decisiones y estrategia de pruebas.
5. `speckit-tasks`: producir tareas atómicas, ordenadas y verificables.
6. `speckit-analyze`: comprobar cobertura y consistencia antes de implementar.
7. `speckit-taskstoissues`: convertir tareas aprobadas en GitHub Issues.
8. `speckit-implement`: implementar una tarea o conjunto explícitamente acotado.
9. `speckit-converge`: detectar drift entre spec, plan, tareas y código.

## Reglas de atomicidad

Una tarea:

- tiene un único resultado observable;
- puede revisarse de forma independiente;
- enumera archivos o módulos afectados;
- incluye prueba o evidencia verificable;
- declara dependencias mediante IDs de tarea o issues;
- no mezcla refactor, infraestructura y feature salvo necesidad técnica demostrada;
- no debe exceder un PR razonablemente revisable.

## Estrategia de personalización

- `.specify/memory/constitution.md`: principios no negociables.
- `.specify/templates/overrides/`: cambios locales sobre plantillas base.
- `AGENTS.md`: responsabilidades y gates por rol.
- Presets: solo cuando una personalización sea reutilizable y estable.
- Extensions: solo para nuevas capacidades, tras auditoría de código y versión fijada.
- Bundles: se crearán cuando los roles estén validados en varias features.

Los overrides locales son deliberadamente pequeños. No copiamos todas las plantillas de Spec Kit: solo imponemos requisitos propios del dominio espacial y del monorepo.
