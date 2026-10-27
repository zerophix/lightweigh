#!/usr/bin/env bash
# T-1019: 压测脚本与基线
#
# 用于对 CloudBase REST API 进行负载测试，生成基线指标。
# 支持并发数、请求总数、是否使用认证 token 等参数。
#
# 用法：
#   bash scripts/ops/load-test.sh                          # 默认 10 并发 × 100 请求
#   bash scripts/ops/load-test.sh --concurrency 50 --total 1000
#   bash scripts/ops/load-test.sh --token <access_token>   # 带认证
#
# 输出：scripts/ops/load-test-result.csv
#
# 来源: docs/ops/capacity-plan.md

set -euo pipefail

# ---- 默认参数 ----
CONCURRENCY=10
TOTAL=100
TOKEN=""
BASE_URL="${BACKEND_BASE:-https://bodyfat-d0gj8wdw1bfe9b421.api.tcloudbasegateway.com}"
OUTPUT="scripts/ops/load-test-result.csv"
ENDPOINT="/v1/rdb/rest/feedbacks"

# ---- 解析参数 ----
while [[ $# -gt 0 ]]; do
    case "$1" in
        --concurrency) CONCURRENCY="$2"; shift 2 ;;
        --total) TOTAL="$2"; shift 2 ;;
        --token) TOKEN="$2"; shift 2 ;;
        --base-url) BASE_URL="$2"; shift 2 ;;
        --endpoint) ENDPOINT="$2"; shift 2 ;;
        *) echo "未知参数: $1"; exit 1 ;;
    esac
done

echo "=== 负载测试 ==="
echo "  并发数: $CONCURRENCY"
echo "  总请求: $TOTAL"
echo "  目标: ${BASE_URL}${ENDPOINT}"
echo "  输出: $OUTPUT"
echo ""

# ---- 初始化 CSV ----
echo "request_id,status_code,latency_ms,success,timestamp" > "$OUTPUT"

# ---- 压测函数 ----
run_request() {
    local req_id=$1
    local ts=$(date +%s%3N)
    local start=$(date +%s%3N)

    local headers=()
    if [[ -n "$TOKEN" ]]; then
        headers+=(-H "Authorization: Bearer ${TOKEN}")
    fi

    local http_code
    http_code=$(curl -s -o /dev/null -w "%{http_code}" \
        --connect-timeout 5 \
        --max-time 30 \
        "${headers[@]}" \
        "${BASE_URL}${ENDPOINT}")

    local end=$(date +%s%3N)
    local latency=$((end - start))
    local success="false"
    if [[ "$http_code" =~ ^2[0-9][0-9]$ ]]; then
        success="true"
    fi

    echo "${req_id},${http_code},${latency},${success},${ts}" >> "$OUTPUT"
}

# ---- 执行压测 ----
echo "开始压测..."
start_time=$(date +%s%3N)

# 使用 xargs 实现并发
seq 1 "$TOTAL" | xargs -n 1 -P "$CONCURRENCY" -I {} bash -c "run_request {}"

end_time=$(date +%s%3N)
total_elapsed=$((end_time - start_time))

# ---- 统计结果 ----
total_reqs=$(tail -n +2 "$OUTPUT" | wc -l)
success_count=$(tail -n +2 "$OUTPUT" | grep -c ",true,")
fail_count=$((total_reqs - success_count))
success_rate=$(echo "scale=2; $success_count * 100 / $total_reqs" | bc 2>/dev/null || echo "0")
avg_latency=$(tail -n +2 "$OUTPUT" | cut -d, -f3 | awk '{s+=$1; n++} END {if(n>0) printf "%.1f", s/n; else print 0}')
p95_latency=$(tail -n +2 "$OUTPUT" | cut -d, -f3 | sort -n | awk "NR==$((total_reqs * 95 / 100)) {print}")

echo ""
echo "=== 结果 ==="
echo "  总请求: $total_reqs"
echo "  成功: $success_count (${success_rate}%)"
echo "  失败: $fail_count"
echo "  平均延迟: ${avg_latency}ms"
echo "  P95 延迟: ${p95_latency}ms"
echo "  总耗时: ${total_elapsed}ms"
echo "  结果已保存: $OUTPUT"

# ---- 告警检查 ----
threshold_success=95
threshold_p95=5000

if (( $(echo "$success_rate < $threshold_success" | bc -l) )); then
    echo "  ⚠️  告警: 成功率 ${success_rate}% < ${threshold_success}%"
fi
if [[ -n "$p95_latency" ]] && (( $(echo "$p95_latency > $threshold_p95" | bc -l) )); then
    echo "  ⚠️  告警: P95 延迟 ${p95_latency}ms > ${threshold_p95}ms"
fi
