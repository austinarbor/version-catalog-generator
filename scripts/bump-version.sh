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
VERSION_RE='^([0-9]+)\.([0-9]+)\.([0-9]+)([-.][0-9A-Za-z.-]+)?$'
if [[ ! "$CURRENT" =~ $VERSION_RE ]]; then
  echo "error: could not parse current version '$CURRENT' from gradle.properties" >&2
  exit 1
fi
MAJOR="${BASH_REMATCH[1]}" MINOR="${BASH_REMATCH[2]}" PATCH="${BASH_REMATCH[3]}"
PRERELEASE="${BASH_REMATCH[4]}"

# A pre-release (e.g. 4.3.0-rc.1) bumps to its own release first, so
# patch -> 4.3.0, not 4.3.1. Same idea for minor/major (matches npm version).
case "$TARGET" in
  major)
    if [[ -n "$PRERELEASE" && "$MINOR" == 0 && "$PATCH" == 0 ]]; then
      NEW="${MAJOR}.0.0"
    else
      NEW="$((MAJOR + 1)).0.0"
    fi
    ;;
  minor)
    if [[ -n "$PRERELEASE" && "$PATCH" == 0 ]]; then
      NEW="${MAJOR}.${MINOR}.0"
    else
      NEW="${MAJOR}.$((MINOR + 1)).0"
    fi
    ;;
  patch)
    if [[ -n "$PRERELEASE" ]]; then
      NEW="${MAJOR}.${MINOR}.${PATCH}"
    else
      NEW="${MAJOR}.${MINOR}.$((PATCH + 1))"
    fi
    ;;
  *)
    if [[ ! "$TARGET" =~ $VERSION_RE ]]; then
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

# versions only contain [0-9A-Za-z.-], so '.' is the only regex metacharacter to escape
CURRENT_RE="${CURRENT//./\\.}"

for entry in "${FILES[@]}"; do
  file="${entry%%|*}"
  prefix="${entry#*|}"
  if ! grep -q "^${prefix}${CURRENT_RE}\$" "$file"; then
    echo "error: '${prefix}${CURRENT}' not found in $file" >&2
    exit 1
  fi
done

for entry in "${FILES[@]}"; do
  file="${entry%%|*}"
  prefix="${entry#*|}"
  # -i.bak works on both BSD (macOS) and GNU sed
  sed -i.bak "s/^${prefix}${CURRENT_RE}\$/${prefix}${NEW}/" "$file"
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
