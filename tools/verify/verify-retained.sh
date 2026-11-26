#!/usr/bin/env bash
# Verify a retained official-packaging receipt (FR-001 regression).
# usage: verify-retained.sh <receiptDir> <commit> <sourceRepo> [expectedJarSha256] [expectedJarBytes]
# Checks: SHA256SUMS integrity, expected JAR identity, evidence names the commit, the packager's
# source archive equals the exact commit tree, and the verifier 'packaged' gate.
set -euo pipefail
dir=$1
commit=$2
src=$(cd "$3" && pwd)
want_sha=${4:-}
want_bytes=${5:-}
name=project-raccoon

cd "$dir"
sha256sum -c --quiet SHA256SUMS
for f in "$name.jar" "$name.log" "$name.zip" fr-001-evidence.txt; do
	test -f "$f" || { echo "::error::receipt is missing $f"; exit 1; }
done
bytes=$(stat -c %s "$name.jar")
sha=$(sha256sum "$name.jar" | cut -d' ' -f1)
echo "::notice title=retained ${commit:0:8}::jar bytes=$bytes sha256=$sha"
if [ -n "$want_sha" ] && [ "$sha" != "$want_sha" ]; then
	echo "::error::retained JAR sha256 $sha != recorded $want_sha"; exit 1
fi
if [ -n "$want_bytes" ] && [ "$bytes" != "$want_bytes" ]; then
	echo "::error::retained JAR is $bytes bytes, recorded $want_bytes"; exit 1
fi
grep -qx "source.commit=$commit" fr-001-evidence.txt || { echo "::error::evidence does not name $commit"; exit 1; }
grep -qx "official.jar.sha256=$sha" fr-001-evidence.txt || { echo "::error::evidence JAR hash differs from retained JAR"; exit 1; }
grep -q "^upload.mode=disabled" fr-001-evidence.txt || { echo "::error::evidence does not record disabled upload"; exit 1; }

python3 -I - "$name.zip" "$src" "$commit" <<'EOF'
import subprocess, sys, zipfile
archive, src, commit = sys.argv[1:]
def git(*a):
	return subprocess.run(["git", "-C", src, *a], capture_output=True, check=True).stdout
tree = {p for p in git("ls-tree", "-r", "-z", "--name-only", commit).decode().split("\0") if p}
zf = zipfile.ZipFile(archive)
names = {n for n in zf.namelist() if not n.endswith("/")}
bad = [n for n in sorted(names & tree) if zf.read(n) != git("show", f"{commit}:{n}")]
missing, extra = sorted(tree - names), sorted(names - tree)
print(f"::notice title=source archive {commit[:8]}::matched={len(names & tree) - len(bad)} mismatched={len(bad)} missing={len(missing)} extra={len(extra)}")
for label, items in (("mismatched", bad), ("missing", missing), ("extra", extra)):
	for n in items:
		print(f"::error::source archive {label}: {n}")
sys.exit(1 if bad or missing or extra or not names else 0)
EOF

java "$src/tools/verify/FoundationVerifier.java" packaged "$src" "$name.jar" "$name.log" "$commit"
