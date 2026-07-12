# Spec Kit workflow

AR Home uses Spec Kit as the mandatory feature-development workflow.

## Install and initialize

```bash
./scripts/setup-spec-kit.sh
```

The script pins Spec Kit to `v0.12.11` and initializes the current repository. Set `SPEC_KIT_INTEGRATION` to use another supported integration.

## Required sequence

1. `speckit.constitution` — update governing principles only in a dedicated governance change.
2. `speckit.specify` — define problem, user value, scope and measurable acceptance.
3. `speckit.clarify` — resolve material ambiguity before technical planning.
4. `speckit.plan` — define architecture, contracts, security, operations and test strategy.
5. `speckit.tasks` — produce atomic, dependency-ordered tasks.
6. `speckit.analyze` — check consistency and coverage before implementation.
7. `speckit.taskstoissues` — create traceable GitHub issues when appropriate.
8. `speckit.implement` — implement only approved tasks.
9. `speckit.converge` — compare code with artifacts and append remaining work.

Codex skills mode exposes the same capabilities as `$speckit-*` skills rather than slash commands.

## Artifact layout

```text
.specify/
├── memory/constitution.md
└── templates/overrides/
    ├── spec-template.md
    ├── plan-template.md
    └── tasks-template.md
specs/
└── NNN-feature-name/
    ├── spec.md
    ├── plan.md
    ├── tasks.md
    ├── research.md
    ├── data-model.md
    └── contracts/
```

## Role selection

The feature plan names accountable review roles from `docs/engineering/roles.md`. Roles are selected by impact, not by repository folder alone.

## Change policy

- Spec Kit tooling upgrades are separate from product-spec changes.
- Community extensions, presets and bundles require source review and a dedicated PR.
- Project-local overrides are the authoritative AR Home customization layer.
- Feature implementation may not silently modify its approved specification.
