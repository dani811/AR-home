#!/usr/bin/env bash
set -euo pipefail

SPEC_KIT_VERSION="v0.12.11"
INTEGRATION="${SPEC_KIT_INTEGRATION:-codex}"

if ! command -v uv >/dev/null 2>&1; then
  echo "uv is required. Install it from https://docs.astral.sh/uv/ and rerun." >&2
  exit 1
fi

uv tool install specify-cli --force \
  --from "git+https://github.com/github/spec-kit.git@${SPEC_KIT_VERSION}"

args=(init --here --force --integration "${INTEGRATION}")
if [[ "${INTEGRATION}" == "codex" ]]; then
  args+=(--integration-options="--skills")
fi

specify "${args[@]}"
specify self check

echo "Spec Kit ${SPEC_KIT_VERSION} initialized for ${INTEGRATION}."
