#!/usr/bin/env bash
# custom-checks.sh — 项目自定义检查脚本
# 在 pre-commit 时自动运行（如果存在），退出码非 0 阻止提交
set -euo pipefail
echo "  (custom-checks.sh — 编辑此文件添加项目自定义检查)"

ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"

# 反射收口（DEC-021 执行记录 2026-10-17）：表现层禁止反射直摸类路径。
# ArchUnit 只查字节码直接依赖，静态 grep 是可用的补充面——运行时拼接类名字符串
# 仍属已声明盲区（不假装全覆盖）。
TARGETS=(
  "$ROOT/app/src/main/kotlin/com/xcwk/scalehub/feature"
  "$ROOT/app/src/main/kotlin/com/xcwk/scalehub/ui"
)
EXISTING=()
for d in "${TARGETS[@]}"; do [ -d "$d" ] && EXISTING+=("$d"); done
if [ "${#EXISTING[@]}" -gt 0 ]; then
  HITS="$(grep -rn --include='*.kt' -E 'Class\.forName|java\.lang\.reflect|kotlin\.reflect\.' "${EXISTING[@]}" || true)"
  if [ -n "$HITS" ]; then
    echo "  [FAIL] 表现层禁止反射（Class.forName / reflect 包）——该依赖请经 application 端口表达："
    echo "$HITS"
    exit 1
  fi
fi

# 状态收口（DEC-024 组 D）：业务状态机所在模块，单个文件的可变字段数（不含 Job 句柄）
# 超过阈值即判定「状态没被收口成类型」。
# 阈值依据：MeasureSession 收口后为 5（phase / 设备绑定 / 倒计时秒数 / 贴士索引 /
# 上次体重缓存），Job 句柄按资源不计入。旧实现同文件有 22 个字段且全部参与判定，
# 任意组合都合法，编译器无从拦截——这条规则就是给同类回归设的机械闸门。
# 已知豁免：infrastructure/ble/AndroidBleLink.kt 的 14 个字段主要是框架句柄
# （gatt/scanner/callback/listener），属传输层资源绑定，不是业务相位；其真实状态
# 只有 connecting/writeInFlight/subscribeIndex 三项，故不纳入本规则（见 DevNotes 债务项）。
STATE_MAX=8
STATE_ROOT="$ROOT/application/src/main"
if [ -d "$STATE_ROOT" ]; then
  while IFS= read -r f; do
    [ -z "$f" ] && continue
    total=$(grep -cE '^[[:space:]]+private var ' "$f" 2>/dev/null || true)
    jobs=$(grep -E '^[[:space:]]+private var ' "$f" 2>/dev/null | grep -c 'Job?' || true)
    state=$(( ${total:-0} - ${jobs:-0} ))
    if [ "$state" -gt "$STATE_MAX" ]; then
      echo "  [FAIL] 可变状态字段过多（$state > ${STATE_MAX}）：$f"
      echo "         把并列可变字段收进 sealed 相位 + 纯 reduce，再让守卫检查非法转移（DEC-024）"
      exit 1
    fi
  done < <(find "$STATE_ROOT" -name '*.kt' -type f 2>/dev/null || true)
fi

# 分层纯度（ArchUnit 的源码面补充）：domain/application 必须保持纯 JVM。
# 曾经为了把 Activity Context 传进 connectGatt，在 BlePort 上写成 setActivityContext(Any?)
# ——编译器确实会拦 android.* 的直接 import，但拦不住用 Any 偷渡，所以另设此闸：
# 只要内层 import 了 android/androidx 就说明平台概念又漏进内层了，应回头改成
# 由 infrastructure 自行跟踪前台 Activity，而不是给端口开洞。
INNER_ROOTS=("$ROOT/domain/src/main" "$ROOT/application/src/main")
for d in "${INNER_ROOTS[@]}"; do
  [ -d "$d" ] || continue
  HITS="$(grep -rn --include='*.kt' --include='*.java' -E '^import (android|androidx)\.' "$d" || true)"
  if [ -n "$HITS" ]; then
    echo "  [FAIL] 内层模块（domain/application）禁止依赖 Android API——保持纯 JVM："
    echo "$HITS"
    exit 1
  fi
done

# 表现层不得直连基础设施：feature/ui 只能依赖 application 端口与 domain。
# ArchUnit 查的是字节码直接依赖，这条查源码 import，能在不跑 Gradle 时先拦一道。
PRES_ROOTS=("$ROOT/app/src/main/kotlin/com/xcwk/scalehub/feature" "$ROOT/app/src/main/kotlin/com/xcwk/scalehub/ui")
EXISTING=()
for d in "${PRES_ROOTS[@]}"; do [ -d "$d" ] && EXISTING+=("$d"); done
if [ "${#EXISTING[@]}" -gt 0 ]; then
  HITS="$(grep -rn --include='*.kt' -E '^import com\.xcwk\.scalehub\.infrastructure' "${EXISTING[@]}" || true)"
  if [ -n "$HITS" ]; then
    echo "  [FAIL] 表现层禁止直接 import infrastructure——请经 BleGate / 端口转发："
    echo "$HITS"
    exit 1
  fi
fi

# 源码质量（死 import / 重复实现 / 相似度分叉）
# 容错：模板项目树内可能没有该脚本（它是项目自有扩展，不在 gov-owned.list）。
# 跳过时必须留痕——静默跳过会让规则看起来生效而实际空转。
# 注意：bash 在 if/then 体内忽略 set -e，必须显式传递 python 退出码，
# 否则 gov-code-check 失败仍会落到下面的 exit 0（历史空转事故，DEC-046）。
CODE_CHECK="$ROOT/scripts/governance/gov-code-check.py"
if [ -f "$CODE_CHECK" ]; then
  # DEC-072: 快层只查暂存区（GOV_CODE_CHECK_STAGED=1），慢层保持全仓
  if [ "${GOV_CODE_CHECK_STAGED:-0}" = "1" ]; then
    python3 "$CODE_CHECK" --staged
  else
    python3 "$CODE_CHECK"
  fi
  code=$?
  if [ "$code" -ne 0 ]; then
    exit "$code"
  fi
else
  echo "  (gov-code-check.py 不在树内，跳过源码质量检测)"
fi

exit 0
