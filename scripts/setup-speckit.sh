#!/usr/bin/env bash
set -euo pipefail

SPEC_KIT_VERSION="v0.12.11"

if ! command -v uv >/dev/null 2>&1; then
  echo "Error: uv is required. Install it from https://docs.astral.sh/uv/" >&2
  exit 1
fi

uv tool install specify-cli --force \
  --from "git+https://github.com/github/spec-kit.git@${SPEC_KIT_VERSION}"

specify self check
specify init --here --force \
  --integration codex \
  --integration-options="--skills"

echo "Spec Kit ${SPEC_KIT_VERSION} initialized for Codex skills mode."
echo "Review generated changes before committing; tooling upgrades must use a dedicated PR."
