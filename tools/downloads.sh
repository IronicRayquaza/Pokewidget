#!/usr/bin/env bash
# Print how many times each released APK has been downloaded.
#
# GitHub counts this per release *asset* and does not show it anywhere in the web UI, so the
# number only exists through the API. Read it as an upper bound on people, not a user count:
# it is cumulative, never resets, does not deduplicate, and includes bots and mirrors.
#
# Needs `jq`. Works unauthenticated against a public repo; set GITHUB_TOKEN if you hit the
# 60-requests-an-hour limit.
set -euo pipefail

REPO="${1:-IronicRayquaza/Pokewidget}"

auth=()
[ -n "${GITHUB_TOKEN:-}" ] && auth=(-H "Authorization: Bearer $GITHUB_TOKEN")

curl --silent --show-error --fail "${auth[@]}" \
  -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/$REPO/releases?per_page=100" \
| jq -r '
    (["RELEASE", "ASSET", "DOWNLOADS"] | @tsv),
    (.[] | .tag_name as $tag | .assets[]
       | [$tag, .name, (.download_count | tostring)] | @tsv)
  ' \
| { command -v column >/dev/null && column -t -s "$(printf "\t")" || cat; }
