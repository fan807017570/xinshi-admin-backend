#!/bin/bash
# ============================================================
# xinshi-admin 守护进程脚本（Watchdog）
# 用法：./watchdog.sh {start|stop|status}
#
# 功能：
#   - 监控应用进程，异常挂掉时自动重启
#   - 崩溃循环保护（时间窗口内超过最大重启次数则放弃）
#   - 指数退避策略，避免频繁重启
#   - 结构化日志记录到 logs/watchdog.log
# ============================================================

set -e

# ---------- 目录定位 ----------
APP_DIR="$(cd "$(dirname "$0")" && pwd)"
STARTUP_SCRIPT="${APP_DIR}/startup.sh"
APP_PID_FILE="${APP_DIR}/app.pid"
WD_PID_FILE="${APP_DIR}/watchdog.pid"
WD_LOG="${APP_DIR}/logs/watchdog.log"
WD_STATE_FILE="${APP_DIR}/logs/.watchdog_state"
APP_LOGS="${APP_DIR}/logs"

# ---------- 可配置参数（环境变量覆盖）----------
WD_CHECK_INTERVAL="${WD_CHECK_INTERVAL:-10}"        # 健康检查间隔（秒）
WD_MAX_RETRIES="${WD_MAX_RETRIES:-5}"               # 时间窗口内最大重启次数
WD_CRASH_WINDOW="${WD_CRASH_WINDOW:-300}"           # 重启计数窗口（秒）
WD_BACKOFF_BASE="${WD_BACKOFF_BASE:-5}"              # 首次重启后退避秒数
WD_BACKOFF_MAX="${WD_BACKOFF_MAX:-120}"              # 最大退避秒数
WD_STARTUP_TIMEOUT="${WD_STARTUP_TIMEOUT:-60}"       # 启动后等待健康的超时秒数
WD_HEALTH_URL="${WD_HEALTH_URL:-http://localhost:${SERVER_PORT:-8080}/api/health}"

# ---------- 工具函数 ----------
log() {
    local level="$1"
    shift
    mkdir -p "${APP_LOGS}"
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [${level}] $*" | tee -a "${WD_LOG}"
}

# ---------- 检测应用进程是否存活 ----------
is_app_alive() {
    local pid=""

    # 方式1：从 PID 文件读取
    if [ -f "${APP_PID_FILE}" ]; then
        pid=$(cat "${APP_PID_FILE}" 2>/dev/null)
        if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
            # 进程存在，进一步检查健康接口
            if command -v curl &>/dev/null; then
                local http_code
                http_code=$(curl -s -o /dev/null -w "%{http_code}" -m 3 "${WD_HEALTH_URL}" 2>/dev/null || echo "000")
                if [ "$http_code" = "200" ]; then
                    return 0
                fi
                return 1
            fi
            # 无 curl，PID 存活就算 OK
            return 0
        fi
    fi

    # 方式2：PID 文件不可靠，通过进程名查找
    pid=$(ps -ef 2>/dev/null | grep "app\.jar" | grep -v grep | awk '{print $2}' | head -1)
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
        echo "$pid" > "${APP_PID_FILE}"
        return 0
    fi

    return 1
}

# ---------- 获取应用 PID ----------
get_app_pid() {
    if [ -f "${APP_PID_FILE}" ]; then
        cat "${APP_PID_FILE}" 2>/dev/null
    else
        ps -ef 2>/dev/null | grep "app\.jar" | grep -v grep | awk '{print $2}' | head -1
    fi
}

# ---------- 崩溃状态管理 ----------
read_crash_state() {
    if [ -f "${WD_STATE_FILE}" ]; then
        source "${WD_STATE_FILE}" 2>/dev/null || true
    fi
    CRASH_COUNT="${CRASH_COUNT:-0}"
    FIRST_CRASH_TIME="${FIRST_CRASH_TIME:-0}"
}

write_crash_state() {
    cat > "${WD_STATE_FILE}" << EOF
CRASH_COUNT=${CRASH_COUNT}
FIRST_CRASH_TIME=${FIRST_CRASH_TIME}
EOF
}

reset_crash_state() {
    CRASH_COUNT=0
    FIRST_CRASH_TIME=0
    write_crash_state
    rm -f "${WD_STATE_FILE}"
}

record_crash() {
    local now
    now=$(date +%s)

    read_crash_state

    # 检查是否需要重置时间窗口
    if [ "${FIRST_CRASH_TIME}" -eq 0 ] || [ $((now - FIRST_CRASH_TIME)) -gt "${WD_CRASH_WINDOW}" ]; then
        CRASH_COUNT=1
        FIRST_CRASH_TIME=$now
    else
        CRASH_COUNT=$((CRASH_COUNT + 1))
    fi

    write_crash_state

    log "CRASH" "Crash #${CRASH_COUNT} in current window (window started $(date -d @${FIRST_CRASH_TIME} '+%Y-%m-%d %H:%M:%S' 2>/dev/null || date -r ${FIRST_CRASH_TIME} '+%Y-%m-%d %H:%M:%S' 2>/dev/null || echo 'unknown'))"

    # 检查是否超过最大重启次数
    if [ "${CRASH_COUNT}" -gt "${WD_MAX_RETRIES}" ]; then
        log "FATAL" "Crash loop detected: ${CRASH_COUNT} crashes within ${WD_CRASH_WINDOW}s. Giving up."
        return 1
    fi

    return 0
}

# ---------- 计算退避时间 ----------
compute_backoff() {
    local retry_count="$1"
    local backoff=$((WD_BACKOFF_BASE * (2 ** (retry_count - 1))))
    if [ "$backoff" -gt "${WD_BACKOFF_MAX}" ]; then
        backoff="${WD_BACKOFF_MAX}"
    fi
    echo "$backoff"
}

# ---------- 信号处理 ----------
cleanup_on_exit() {
    log "INFO" "Watchdog received shutdown signal, stopping..."

    # 停止应用
    if [ -f "${STARTUP_SCRIPT}" ]; then
        bash "${STARTUP_SCRIPT}" stop 2>/dev/null || true
    fi

    rm -f "${WD_PID_FILE}"
    log "INFO" "Watchdog stopped."
    exit 0
}

trap cleanup_on_exit SIGTERM SIGINT

# ---------- 监控主循环 ----------
monitor_loop() {
    log "INFO" "Watchdog monitor loop started"
    log "INFO" "Check interval: ${WD_CHECK_INTERVAL}s, Max retries: ${WD_MAX_RETRIES}, Crash window: ${WD_CRASH_WINDOW}s"
    log "INFO" "Health URL: ${WD_HEALTH_URL}"

    reset_crash_state

    # 先确认应用是否已启动，未启动则拉起
    if ! is_app_alive; then
        log "WARN" "Application is not running at watchdog start, launching..."
        if [ -f "${STARTUP_SCRIPT}" ]; then
            bash "${STARTUP_SCRIPT}" start 2>&1 | tee -a "${WD_LOG}" || true
        fi
        sleep "${WD_CHECK_INTERVAL}"
    else
        log "INFO" "Application is already running (PID: $(get_app_pid))"
    fi

    while true; do
        sleep "${WD_CHECK_INTERVAL}"

        if is_app_alive; then
            # 应用正常，检查是否需要重置崩溃窗口
            read_crash_state
            if [ "${FIRST_CRASH_TIME}" -ne 0 ]; then
                local now
                now=$(date +%s)
                if [ $((now - FIRST_CRASH_TIME)) -gt "${WD_CRASH_WINDOW}" ]; then
                    log "INFO" "Crash window expired, resetting crash counter"
                    reset_crash_state
                fi
            fi
            continue
        fi

        # --- 应用挂了 ---
        local dead_pid
        dead_pid=$(get_app_pid)
        log "CRASH" "Application not responding (last known PID: ${dead_pid:-unknown})"

        # 记录崩溃，检查是否超过阈值
        if ! record_crash; then
            # 超过最大重试次数，退出
            log "FATAL" "Watchdog exiting. Manual intervention required."
            rm -f "${WD_PID_FILE}"
            exit 1
        fi

        # 计算退避时间
        read_crash_state
        local backoff
        backoff=$(compute_backoff "${CRASH_COUNT}")
        log "RETRY" "Restarting in ${backoff}s (attempt ${CRASH_COUNT}/${WD_MAX_RETRIES})..."

        sleep "$backoff"

        # 重启应用
        log "START" "Restarting application..."
        if [ -f "${STARTUP_SCRIPT}" ]; then
            # 先确保旧进程已停止
            bash "${STARTUP_SCRIPT}" stop 2>/dev/null || true
            sleep 2

            # 启动
            if bash "${STARTUP_SCRIPT}" start 2>&1 | tee -a "${WD_LOG}"; then
                # 等待健康检查通过
                log "INFO" "Waiting for application to become healthy (timeout: ${WD_STARTUP_TIMEOUT}s)..."
                local waited=0
                while [ "$waited" -lt "${WD_STARTUP_TIMEOUT}" ]; do
                    sleep 2
                    waited=$((waited + 2))
                    if is_app_alive; then
                        log "RECOVER" "Application restarted successfully (new PID: $(get_app_pid), waited ${waited}s)"
                        break
                    fi
                done

                if ! is_app_alive; then
                    log "ERROR" "Application did not become healthy within ${WD_STARTUP_TIMEOUT}s after restart"
                fi
            else
                log "ERROR" "startup.sh start failed"
            fi
        else
            log "ERROR" "Startup script not found: ${STARTUP_SCRIPT}"
        fi
    done
}

# ---------- 启动守护进程 ----------
start_watchdog() {
    # 检查自身是否已在运行
    if [ -f "${WD_PID_FILE}" ]; then
        local existing_pid
        existing_pid=$(cat "${WD_PID_FILE}" 2>/dev/null)
        if [ -n "$existing_pid" ] && kill -0 "$existing_pid" 2>/dev/null; then
            echo "[WARN] Watchdog is already running (PID: ${existing_pid})"
            exit 1
        fi
        rm -f "${WD_PID_FILE}"
    fi

    # 检查 startup.sh 是否存在
    if [ ! -f "${STARTUP_SCRIPT}" ]; then
        echo "[ERROR] Startup script not found: ${STARTUP_SCRIPT}"
        exit 1
    fi

    echo "========================================"
    echo "  xinshi-admin Watchdog"
    echo "========================================"
    echo "  Check Interval:  ${WD_CHECK_INTERVAL}s"
    echo "  Max Retries:     ${WD_MAX_RETRIES}"
    echo "  Crash Window:    ${WD_CRASH_WINDOW}s"
    echo "  Health URL:      ${WD_HEALTH_URL}"
    echo "  Log File:        ${WD_LOG}"
    echo "========================================"

    # 后台启动监控循环
    nohup bash "$0" _run_monitor >> "${WD_LOG}" 2>&1 &

    local wd_pid=$!
    echo "$wd_pid" > "${WD_PID_FILE}"

    log "INFO" "Watchdog started (PID: ${wd_pid})"

    # 短暂等待确认启动成功
    sleep 1
    if kill -0 "$wd_pid" 2>/dev/null; then
        echo "[INFO] Watchdog started successfully (PID: ${wd_pid})"
        echo "[INFO] View logs: tail -f ${WD_LOG}"
    else
        echo "[ERROR] Watchdog failed to start. Check logs: ${WD_LOG}"
        rm -f "${WD_PID_FILE}"
        exit 1
    fi
}

# ---------- 停止守护进程 ----------
stop_watchdog() {
    local wd_pid=""

    if [ -f "${WD_PID_FILE}" ]; then
        wd_pid=$(cat "${WD_PID_FILE}" 2>/dev/null)
    fi

    if [ -z "$wd_pid" ] || ! kill -0 "$wd_pid" 2>/dev/null; then
        wd_pid=$(ps -ef 2>/dev/null | grep "watchdog\.sh.*_run_monitor" | grep -v grep | awk '{print $2}' | head -1)
    fi

    if [ -z "$wd_pid" ]; then
        echo "[INFO] Watchdog is not running."
        rm -f "${WD_PID_FILE}"
        return 0
    fi

    echo "[INFO] Stopping watchdog (PID: ${wd_pid})..."

    # 发送 SIGTERM，触发 cleanup_on_exit（会同时停应用）
    kill "${wd_pid}" 2>/dev/null || true

    # 等待退出
    for i in $(seq 1 15); do
        if ! kill -0 "${wd_pid}" 2>/dev/null; then
            echo "[INFO] Watchdog stopped."
            rm -f "${WD_PID_FILE}"
            return 0
        fi
        sleep 1
    done

    # 强制杀掉
    echo "[WARN] Timeout, force killing watchdog (PID: ${wd_pid})..."
    kill -9 "${wd_pid}" 2>/dev/null || true
    sleep 1
    rm -f "${WD_PID_FILE}"
    echo "[INFO] Watchdog force stopped."
}

# ---------- 查看状态 ----------
status_watchdog() {
    echo "========================================"
    echo "  xinshi-admin Watchdog Status"
    echo "========================================"

    # 守护进程状态
    local wd_pid=""
    if [ -f "${WD_PID_FILE}" ]; then
        wd_pid=$(cat "${WD_PID_FILE}" 2>/dev/null)
    fi
    if [ -z "$wd_pid" ] || ! kill -0 "$wd_pid" 2>/dev/null; then
        wd_pid=$(ps -ef 2>/dev/null | grep "watchdog\.sh.*_run_monitor" | grep -v grep | awk '{print $2}' | head -1)
    fi

    if [ -z "$wd_pid" ]; then
        echo "  Watchdog:   NOT running"
    else
        echo "  Watchdog:   RUNNING"
        echo "    PID:      ${wd_pid}"
        echo "    Uptime:   $(ps -o etime= -p "${wd_pid}" 2>/dev/null | tr -d ' ' || echo 'unknown')"
        echo "    Memory:   $(ps -o rss= -p "${wd_pid}" 2>/dev/null | awk '{printf "%.0f MB", $1/1024}' || echo 'unknown')"
    fi

    echo ""

    # 应用进程状态
    local app_pid
    app_pid=$(get_app_pid)
    if [ -z "$app_pid" ] || ! kill -0 "$app_pid" 2>/dev/null; then
        echo "  App:       NOT running"
    else
        echo "  App:       RUNNING"
        echo "    PID:      ${app_pid}"
        echo "    Uptime:   $(ps -o etime= -p "${app_pid}" 2>/dev/null | tr -d ' ' || echo 'unknown')"
        echo "    Memory:   $(ps -o rss= -p "${app_pid}" 2>/dev/null | awk '{printf "%.0f MB", $1/1024}' || echo 'unknown')"
        echo "    CPU:      $(ps -o %cpu= -p "${app_pid}" 2>/dev/null | tr -d ' ' || echo 'unknown')%"

        if command -v curl &>/dev/null; then
            local http_code
            http_code=$(curl -s -o /dev/null -w "%{http_code}" -m 3 "${WD_HEALTH_URL}" 2>/dev/null || echo "000")
            if [ "$http_code" = "200" ]; then
                echo "    Health:   OK"
            else
                echo "    Health:   FAIL (HTTP ${http_code})"
            fi
        fi
    fi

    echo ""

    # 崩溃状态
    read_crash_state
    if [ "${CRASH_COUNT:-0}" -gt 0 ]; then
        echo "  Crash Info:"
        echo "    Count:    ${CRASH_COUNT}/${WD_MAX_RETRIES}"
        echo "    Window:   started $(date -d @${FIRST_CRASH_TIME} '+%Y-%m-%d %H:%M:%S' 2>/dev/null || date -r ${FIRST_CRASH_TIME} '+%Y-%m-%d %H:%M:%S' 2>/dev/null || echo 'unknown')"
        echo "    Remaining: $((WD_MAX_RETRIES - CRASH_COUNT)) retries"
        echo ""
    else
        echo "  Crash Info: No recent crashes"
        echo ""
    fi

    echo "  Config:"
    echo "    Health URL:    ${WD_HEALTH_URL}"
    echo "    Check Interval: ${WD_CHECK_INTERVAL}s"
    echo "    Max Retries:    ${WD_MAX_RETRIES}"
    echo "    Crash Window:   ${WD_CRASH_WINDOW}s"
    echo "    Watchdog Log:   ${WD_LOG}"
    echo "========================================"
}

# ---------- Main ----------
case "${1:-start}" in
    start)
        start_watchdog
        ;;
    stop)
        stop_watchdog
        ;;
    status)
        status_watchdog
        ;;
    _run_monitor)
        # 内部命令：运行监控循环（由 start 后台调用）
        monitor_loop
        ;;
    *)
        echo "Usage: $0 {start|stop|status}"
        echo ""
        echo "Commands:"
        echo "  start    Start watchdog and application"
        echo "  stop     Stop watchdog and application"
        echo "  status   Show watchdog and application status"
        echo ""
        echo "Environment variables:"
        echo "  WD_CHECK_INTERVAL   Health check interval in seconds (default: 10)"
        echo "  WD_MAX_RETRIES      Max restarts in crash window (default: 5)"
        echo "  WD_CRASH_WINDOW     Crash counting window in seconds (default: 300)"
        echo "  WD_BACKOFF_BASE     Initial backoff seconds (default: 5)"
        echo "  WD_BACKOFF_MAX      Maximum backoff seconds (default: 120)"
        echo "  WD_STARTUP_TIMEOUT  Startup health wait timeout (default: 60)"
        echo "  WD_HEALTH_URL       Health check URL (default: http://localhost:8080/api/health)"
        exit 1
        ;;
esac
