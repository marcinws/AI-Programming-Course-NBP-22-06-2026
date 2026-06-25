#!/bin/bash
# Fallback command hook: blocks direct `mvn dependency:get` calls and instructs
# Claude to research security before retrying. The agent-based hook on Edit/Write
# handles pom.xml edits with autonomous security research; this script covers CLI installs.

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty')

# Extract artifact coordinates from -Dartifact=groupId:artifactId:version
ARTIFACT=$(echo "$COMMAND" | grep -oP '(?<=-Dartifact=)\S+' | head -1)

if [[ -z "$ARTIFACT" ]]; then
  exit 0
fi

GROUP_ID=$(echo "$ARTIFACT" | cut -d: -f1)
ARTIFACT_ID=$(echo "$ARTIFACT" | cut -d: -f2)
VERSION=$(echo "$ARTIFACT" | cut -d: -f3)

echo "Security gate: Maven dependency install intercepted." >&2
echo "Artifact: $GROUP_ID:$ARTIFACT_ID:$VERSION" >&2
echo "" >&2
echo "Required before proceeding:" >&2
echo "  1. Search NVD (https://nvd.nist.gov/vuln/search?query=${GROUP_ID}+${ARTIFACT_ID}) for CVEs" >&2
echo "  2. Check OSV (https://osv.dev/?q=${GROUP_ID}%3A${ARTIFACT_ID}) for security advisories" >&2
echo "  3. Confirm the version $VERSION has no critical or high severity issues" >&2
echo "" >&2
echo "If the dependency is safe, retry the install. If not, propose a safe alternative." >&2
exit 2
