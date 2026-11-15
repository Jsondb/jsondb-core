#!/usr/bin/env bash
#
# Copyright (c) 2026 Farooq Khan
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to
# deal in the Software without restriction, including without limitation the
# rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
# sell copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
# THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
#
# Creates the GitHub release for a version that has been published to Maven Central: tags the
# current commit "v<version>", pushes the tag, and creates the release from that version's section
# of CHANGELOG.md, marked as the latest release. See RELEASING.md.
#
# Usage: scripts/github-release.sh 1.1.0

set -euo pipefail

if [ $# -ne 1 ]; then
  echo "Usage: $0 <version>, e.g. $0 1.1.0" >&2
  exit 1
fi
version="$1"
cd "$(dirname "$0")/.."

# The release must point at the commit that was published, which is the commit that builds this version
built=$(./gradlew -q help --console=plain | sed -n 's/^Build Version: //p')
if [ "$built" != "$version" ]; then
  echo "This commit builds version '$built', not '$version'. Check out the commit that was published." >&2
  exit 1
fi
if [ -n "$(git status --porcelain)" ]; then
  echo "The working tree has uncommitted changes; release from a clean checkout." >&2
  exit 1
fi

notes=$(awk -v heading="## $version" '$0 == heading {found=1; next} found && /^## / {exit} found {print}' CHANGELOG.md)
if [ -z "$(printf '%s' "$notes" | tr -d '[:space:]')" ]; then
  echo "CHANGELOG.md has no '## $version' section." >&2
  exit 1
fi

notes="$notes

### Installation

\`\`\`xml
<dependency>
  <groupId>io.jsondb</groupId>
  <artifactId>jsondb-core</artifactId>
  <version>$version</version>
</dependency>
\`\`\`

[Maven Central](https://central.sonatype.com/artifact/io.jsondb/jsondb-core/$version)"

git tag -a "v$version" -m "jsondb-core $version"
git push origin "v$version"
gh release create "v$version" --verify-tag --latest --title "jsondb-core $version" --notes "$notes"
