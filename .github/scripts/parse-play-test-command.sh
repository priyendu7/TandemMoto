#!/usr/bin/env bash
# Parses a "/play-test <sha> [tester-<name>]" PR comment.
# Usage: parse-play-test-command.sh "<comment body>"
# Prints "sha=<sha>" and "target=<internal-sharing|tester-name>" lines; exits 1 on invalid input.
set -euo pipefail

body="$(printf '%s' "${1:-}" | tr -d '\r')"
# Only the first line counts; anything after it is free text for humans.
first_line="${body%%$'\n'*}"
first_line="$(printf '%s' "$first_line" | tr 'A-F' 'a-f')"
# Trim trailing whitespace.
first_line="${first_line%"${first_line##*[![:space:]]}"}"

re='^/play-test ([0-9a-f]{7,40})( (tester-[a-z0-9][a-z0-9-]{0,39}))?$'
if [[ ! "$first_line" =~ $re ]]; then
  echo "Invalid command. Usage: /play-test <commit-sha> [tester-<name>]" >&2
  exit 1
fi

echo "sha=${BASH_REMATCH[1]}"
echo "target=${BASH_REMATCH[3]:-internal-sharing}"
