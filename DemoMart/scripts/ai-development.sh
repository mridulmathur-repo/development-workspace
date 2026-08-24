#!/bin/sh
set -eu

project_root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
repository_root="$(git -C "$project_root" rev-parse --show-toplevel)"
patch_file="${AI_PATCH_FILE:-}"

if [ -z "$patch_file" ] || [ ! -f "$patch_file" ]; then
  echo "AI_PATCH_FILE must point to the generated patch." >&2
  exit 1
fi

patch_files="$(git -C "$repository_root" apply --numstat -- "$patch_file" | awk -F '\t' '{print $3}')"
if [ -z "$patch_files" ]; then
  echo "The generated patch is empty." >&2
  exit 1
fi

for changed_file in $patch_files; do
  case "$changed_file" in
    DemoMart/*) ;;
    *) echo "Patch attempts to change a file outside DemoMart: $changed_file" >&2; exit 1 ;;
  esac
done

git -C "$repository_root" apply --check -- "$patch_file"
git -C "$repository_root" apply -- "$patch_file"

cd "$project_root"
mvn -q test
npm --prefix frontend run build

echo "AI patch applied and regression checks passed."
