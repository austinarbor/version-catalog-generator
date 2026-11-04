#!/usr/bin/env bash
# Bumps the project version in every file that tracks it.
#
# Usage:
#   scripts/bump-version.sh <new-version>     e.g. 4.3.0
#   scripts/bump-version.sh major|minor|patch  derive from current version
#   add --commit to also create a "bump version to X" commit
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

usage() {
  echo "usage: $0 <new-version|major|minor|patch> [--commit]" >&2
  exit 1
}

[[ $# -ge 1 ]] || usage
TARGET="$1"
COMMIT=false
[[ "${2:-}" == "--commit" ]] && COMMIT=true
[[ $# -le 2 ]] || usage
[[ $# -eq 1 || "$COMMIT" == true ]] || usage

CURRENT="$(sed -n 's/^version=//p' gradle.properties)"
if [[ ! "$CURRENT" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
  echo "error: could not parse current version '$CURRENT' from gradle.properties" >&2
  exit 1
fi
MAJOR="${BASH_REMATCH[1]}" MINOR="${BASH_REMATCH[2]}" PATCH="${BASH_REMATCH[3]}"

case "$TARGET" in
  major) NEW="$((MAJOR + 1)).0.0" ;;
  minor) NEW="${MAJOR}.$((MINOR + 1)).0" ;;
  patch) NEW="${MAJOR}.${MINOR}.$((PATCH + 1))" ;;
  *)
    if [[ ! "$TARGET" =~ ^[0-9]+\.[0-9]+\.[0-9]+([-.][0-9A-Za-z.-]+)?$ ]]; then
      echo "error: '$TARGET' is not a valid version" >&2
      exit 1
    fi
    NEW="$TARGET"
    ;;
esac

if [[ "$NEW" == "$CURRENT" ]]; then
  echo "error: version is already $CURRENT" >&2
  exit 1
fi

# file|sed-pattern-prefix pairs: the line starting with the prefix gets the new version
FILES=(
  "gradle.properties|version="
  "README.adoc|:version: "
  "plugin/src/docs/asciidoc/index.adoc|:version: "
)

for entry in "${FILES[@]}"; do
  file="${entry%%|*}"
  prefix="${entry#*|}"
  if ! grep -q "^${prefix}${CURRENT}\$" "$file"; then
    echo "error: '${prefix}${CURRENT}' not found in $file" >&2
    exit 1
  fi
done

for entry in "${FILES[@]}"; do
  file="${entry%%|*}"
  prefix="${entry#*|}"
  # -i.bak works on both BSD (macOS) and GNU sed
  sed -i.bak "s/^${prefix}${CURRENT//./\\.}\$/${prefix}${NEW}/" "$file"
  rm -f "$file.bak"
  echo "updated $file"
done

echo "bumped version $CURRENT -> $NEW"

if [[ "$COMMIT" == true ]]; then
  files=()
  for entry in "${FILES[@]}"; do files+=("${entry%%|*}"); done
  git add "${files[@]}"
  git commit -m "bump version to $NEW"
fi
