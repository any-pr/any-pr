#!/usr/bin/env sh
# Install gate-check as this repository's pre-commit hook.
#
#   sh tools/gate-check/install-hook.sh
#
# The hook runs `preflight.py --staged`, so it judges exactly what is about to
# be recorded, not the state of the working tree. Exit code 2 (nothing staged)
# is treated as success; anything else stops the commit and prints one line on
# what to run next.
#
# To remove it: delete .git/hooks/pre-commit. To bypass it once:
# git commit --no-verify.
set -eu

root=$(git rev-parse --show-toplevel)
hook="$root/.git/hooks/pre-commit"

if [ -e "$hook" ] && ! grep -q "tools/gate-check" "$hook" 2>/dev/null; then
    echo "a different pre-commit hook already exists at $hook" >&2
    echo "move it aside first; this script will not overwrite it" >&2
    exit 1
fi

cat > "$hook" <<'HOOK'
#!/usr/bin/env sh
# Installed by tools/gate-check/install-hook.sh -- delete this file to remove.
set -u

root=$(git rev-parse --show-toplevel)
status=0
python3 "$root/tools/gate-check/preflight.py" --staged >/dev/null || status=$?

case "$status" in
    0|2) exit 0 ;;   # 0 = within the gate, 2 = nothing staged
esac

echo ""
echo "gate-check: this commit would be closed by the auto-merge gate."
python3 "$root/tools/gate-check/preflight.py" --staged --explain || true
echo ""
echo "Bypass once with: git commit --no-verify"
exit 1
HOOK

chmod +x "$hook"
echo "installed $hook"
