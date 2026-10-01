#!/usr/bin/env bash
#
# release.sh, run for real in throwaway clones of this repository -- ordinary releases, hotfixes,
# a failed bundle, the refusals -- with the builds stubbed and a throwaway key, and the branches,
# tags and files checked after each. Nothing here touches this checkout, the keystore or a remote.
#
#   ./scripts/check-release.sh
#
# Run it after changing release.sh. Written 2026-10-01 with the move to releasing from master; its
# first run found that a hotfix's evidence entry always conflicts with develop's.
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BRANCH="$(git -C "$ROOT" rev-parse --abbrev-ref HEAD)"
S="$(mktemp -d)"
trap 'rm -rf "$S"' EXIT
source "$ROOT/scripts/use-tooling.sh" >/dev/null
"$JAVA_HOME/bin/keytool" -genkeypair -keystore "$S/fake.jks" -storepass fakefake -keypass fakefake \
    -alias f -dname CN=Fake -keyalg RSA -validity 2 >/dev/null 2>&1

# This branch's release.sh, as develop; master as it is here. Everything that builds is stubbed.
fresh() {
  cd "$S" && rm -rf "$S/rt" && git clone -q "$ROOT" "$S/rt" && cd "$S/rt"
  git checkout -q -B develop "origin/$BRANCH"; git branch -q -f master origin/master
  printf '#!/usr/bin/env bash\nexit 0\n' > scripts/build-web-engine.sh
  printf '#!/usr/bin/env bash\necho "   (stub suite)"\n' > scripts/test-protracktor.sh
  cat > scripts/build-bundle.sh <<STUB
#!/usr/bin/env bash
set -e
cd "\$(dirname "\$0")/.."; source scripts/use-tooling.sh >/dev/null
[ -n "\${FAIL_BUNDLE:-}" ] && { echo "stub: wrong password"; exit 1; }
v=\$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts | head -1); c=\$(git rev-list --count HEAD)
mkdir -p dist; out=dist/protracktor-\$v-\$c.aab
echo "\$(git rev-parse --short HEAD) \$(git rev-parse --abbrev-ref HEAD)" > $S/built-from.txt
python3 -c "import zipfile; z=zipfile.ZipFile('\$out','w'); z.writestr('x.txt','x'); z.close()"
"\$JAVA_HOME/bin/jarsigner" -keystore $S/fake.jks -storepass fakefake "\$out" f >/dev/null
STUB
  chmod +x scripts/*.sh; git add -A scripts; git commit -q -m stubs
}
ok() { echo "  ✓ $*"; }; bad() { echo "  ✗ $*"; FAILED=1; }
FAILED=0

echo "A. --check changes nothing"; fresh
before=$(git for-each-ref --format='%(refname) %(objectname)' | md5sum); echo | ./scripts/release.sh 0.12.0 --check >/dev/null 2>&1; rc=$?
after=$(git for-each-ref --format='%(refname) %(objectname)' | md5sum)
[ $rc = 0 ] && ok "exit 0" || bad "exit $rc"; [ "$before" = "$after" ] && ok "refs unchanged" || bad "refs changed"
[ -z "$(git status --porcelain)" ] && ok "tree clean" || bad "tree dirty"

echo "B. an ordinary release from develop"; fresh
echo 0.12.0 | ./scripts/release.sh 0.12.0 > $S/rt-B.log 2>&1; rc=$?
[ $rc = 0 ] && ok "exit 0" || { bad "exit $rc"; tail -5 $S/rt-B.log; }
[ "$(git rev-parse master)" = "$(git rev-parse v0.12.0^{commit})" ] && ok "master is the tag" || bad "master is not the tag"
[ "$(git log -1 --format=%s master)" = "0.12.0" ] && ok "master's tip is the version commit, no merge commit" || bad "master tip: $(git log -1 --format=%s master)"
[ "$(git rev-parse develop^)" = "$(git rev-parse master)" ] && ok "develop = master + the evidence commit" || bad "develop is not master+1"
[ "$(git rev-parse --abbrev-ref HEAD)" = develop ] && ok "ends on develop" || bad "ends on $(git rev-parse --abbrev-ref HEAD)"
read built on < $S/built-from.txt; [ "$built" = "$(git rev-parse --short master)" ] && [ "$on" = master ] && ok "bundle built on master at the tag" || bad "built $built on $on"
grep -q "push-protracktor.sh master develop v0.12.0" $S/rt-B.log && ok "push line" || bad "push line"
git merge-base --is-ancestor master develop && ok "next release can fast-forward" || bad "master not in develop"

echo "C. the bundle fails (wrong password)"; fresh
echo 0.12.0 | FAIL_BUNDLE=1 ./scripts/release.sh 0.12.0 > $S/rt-C.log 2>&1; rc=$?
[ $rc != 0 ] && ok "exit $rc" || bad "exit 0"
[ "$(git rev-parse --abbrev-ref HEAD)" = develop ] && ok "back on develop" || bad "left on $(git rev-parse --abbrev-ref HEAD)"
grep -q "To finish: git checkout v0.12.0" $S/rt-C.log && ok "says how to finish" || bad "no way on"

echo "D. a hotfix from master"; fresh
echo 0.12.0 | ./scripts/release.sh 0.12.0 >/dev/null 2>&1
echo "work" >> docs/WISHLIST.md; git commit -qam "unreleased work on develop"
git checkout -q -b hotfix/fix master; echo "fix" >> docs/STATUS.md; git commit -qam "the fix"
echo 0.12.1 | ./scripts/release.sh 0.12.1 > $S/rt-D.log 2>&1; rc=$?
[ $rc = 0 ] && ok "exit 0" || { bad "exit $rc"; tail -5 $S/rt-D.log; }
[ "$(git rev-parse master)" = "$(git rev-parse v0.12.1^{commit})" ] && ok "master is the hotfix tag" || bad "master not at v0.12.1"
git merge-base --is-ancestor v0.12.0 master && ok "master moved forward only" || bad "master jumped"
git log --format=%s master | grep -q "unreleased work" && bad "develop's unreleased work leaked into the hotfix" || ok "develop's unreleased work not released"
git merge-base --is-ancestor hotfix/fix develop && ok "hotfix merged back into develop" || bad "hotfix not in develop"
git merge-base --is-ancestor master develop && ok "next release can fast-forward" || bad "master not in develop"
[ $(git rev-list --count develop) -gt $(git rev-list --count master) ] && ok "develop's versionCode above the release's" || bad "counts"
grep -q 'versionName = "0.12.1"' app/build.gradle.kts && ok "develop carries 0.12.1" || bad "develop's versionName: $(grep -o 'versionName = "[^"]*"' app/build.gradle.kts)"

[ "$(grep -c '^## 0.12' store/play-console/releases.md)" = 2 ] && [ "$(grep -m1 '^## ' store/play-console/releases.md | cut -c1-9)" = "## 0.12.1" ] && ok "develop's releases.md has both entries, 0.12.1 on top" || bad "releases.md: $(grep '^## 0.12' store/play-console/releases.md | tr '\n' ' ')"
[ -z "$(git status --porcelain)" ] && ok "tree clean" || bad "tree dirty"

echo "G. a hotfix whose code conflicts with develop"; fresh
echo 0.12.0 | ./scripts/release.sh 0.12.0 >/dev/null 2>&1
sed -i '1s/.*/develop line/' docs/STATUS.md; git commit -qam "develop edits line 1"
git checkout -q -b hotfix/clash master; sed -i '1s/.*/hotfix line/' docs/STATUS.md; git commit -qam "hotfix edits line 1"
echo 0.12.1 | ./scripts/release.sh 0.12.1 > $S/rt-G.log 2>&1; rc=$?
[ $rc = 0 ] && ok "release done" || bad "exit $rc"
grep -q "merge it by hand" $S/rt-G.log && ok "says to merge by hand" || bad "silent"
[ -z "$(git status --porcelain)" ] && ok "no half-merge left" || bad "half-merge left"
git merge-base --is-ancestor hotfix/clash develop && bad "merged anyway" || ok "develop untouched"
[ "$(head -1 docs/STATUS.md)" = "develop line" ] && ok "develop's line kept" || bad "line: $(head -1 docs/STATUS.md)"

echo "E. master holds something develop does not"; fresh
git checkout -q master; echo x >> docs/STATUS.md; git commit -qam "stray"; git checkout -q develop
echo | ./scripts/release.sh 0.12.0 --check > $S/rt-E.log 2>&1; rc=$?
[ $rc != 0 ] && grep -q "master has commits" $S/rt-E.log && ok "refused, says why" || bad "not refused: $rc"

echo "F. not on develop or a hotfix"; fresh
git checkout -q -b feature/x; echo | ./scripts/release.sh 0.12.0 --check > $S/rt-F.log 2>&1; rc=$?
[ $rc != 0 ] && grep -q "releases are made from develop" $S/rt-F.log && ok "refused" || bad "not refused"

echo; [ $FAILED = 0 ] && { echo "✅ release.sh: every scenario passed"; exit 0; } || { echo "❌ release.sh: a scenario failed"; exit 1; }
