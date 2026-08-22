#!/usr/bin/env bash
#
# Shell-level validation for scripts/rikkahub-data.sh.
# Uses a test-double `adb` in PATH so no device is touched.
#
set -u

HERE="$(cd "$(dirname "$0")" && pwd)"
TOOL="$HERE/rikkahub-data.sh"
FAIL=0

run_fake_adb() {
  # $1 = behavior: ok-runas | fail-runas
  local behavior="$1"
  local dir; dir="$(mktemp -d)"
  cat > "$dir/adb" <<EOF
#!/usr/bin/env bash
if [[ "\$1" == "-s" ]]; then shift 2; fi
case "\$1" in
  exec-out) shift; shift; # run-as ...
    if [[ "$behavior" == "fail-runas" ]]; then
      exit 1
    fi
    echo "uid=10000(package) gid=10000"; exit 0
    ;;
  *) echo "unexpected adb call: \$*" >&2; exit 2 ;;
esac
EOF
  chmod +x "$dir/adb"
  echo "$dir"
}

check() {
  local desc="$1" expected_exit="$2"; shift 2
  local out
  out="$("$@" 2>&1)"
  local code=$?
  if [[ "$code" != "$expected_exit" ]]; then
    echo "FAIL [$desc]: exit=$code expected=$expected_exit"
    echo "  out: $out"
    FAIL=1
  else
    echo "ok   [$desc]"
  fi
}

# 1. no subcommand -> usage, nonzero
check "no subcommand" 1 "$TOOL"

# 2. missing --serial
check "missing --serial" 1 "$TOOL" check

# 3. run-as unavailable (non-debuggable) -> clear error
D1="$(run_fake_adb fail-runas)"
check "run-as unavailable" 1 env "PATH=$D1:$PATH" "$TOOL" check --serial X --package p

# 4. run-as ok
D2="$(run_fake_adb ok-runas)"
out="$(env "PATH=$D2:$PATH" "$TOOL" check --serial X --package p 2>&1)"
[[ "$out" == *"run-as ok"* ]] && echo "ok   [run-as ok]" || { echo "FAIL [run-as ok]: $out"; FAIL=1; }

# 5. restore without --confirm -> error
check "restore missing --confirm" 1 "$TOOL" restore --serial X --input /tmp/whatever.tar

# 6. restore with --confirm but missing --input -> error
check "restore missing --input" 1 "$TOOL" restore --serial X --confirm

# 7. metadata with nonexistent apk -> error
check "metadata nonexistent apk" 1 "$TOOL" metadata --serial X --apk /nonexistent.apk

rm -rf "$D1" "$D2"
[[ "$FAIL" == 0 ]] && echo "ALL PASS" || { echo "SOME FAILED"; exit 1; }
