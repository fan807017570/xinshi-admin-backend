#!/bin/bash
# ============================================================
# xinshi-admin 服务管理脚本（独立部署版本）
# 用法：./startup.sh {start|stop|restart|status|watchdog}
# ============================================================

set -e

APP_NAME="xinshi-admin"
APP_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_JAR="${APP_DIR}/app.jar"
APP_LOGS="${APP_DIR}/logs"
GC_LOG_DIR="${APP_LOGS}/gc"
PID_FILE="${APP_DIR}/app.pid"

# ---------- JVM 参数 ----------
HEAP_MIN="${HEAP_MIN:-256m}"
HEAP_MAX="${HEAP_MAX:-4g}"
METASPACE_MIN="${METASPACE_MIN:-128m}"
METASPACE_MAX="${METASPACE_MAX:-256m}"
THREAD_STACK="${THREAD_STACK:-256k}"

GC_OPTS="-XX:+UseG1GC"
GC_OPTS="${GC_OPTS} -XX:MaxGCPauseMillis=200"
GC_OPTS="${GC_OPTS} -XX:G1HeapRegionSize=4m"
GC_OPTS="${GC_OPTS} -XX:InitiatingHeapOccupancyPercent=45"
GC_OPTS="${GC_OPTS} -XX:G1ReservePercent=10"
GC_OPTS="${GC_OPTS} -XX:+ParallelRefProcEnabled"
GC_OPTS="${GC_OPTS} -XX:+PrintGCDetails -XX:+PrintGCDateStamps"
GC_OPTS="${GC_OPTS} -XX:+PrintHeapAtGC -XX:+PrintGCApplicationStoppedTime"
GC_OPTS="${GC_OPTS} -Xloggc:${GC_LOG_DIR}/gc-%t.log"
GC_OPTS="${GC_OPTS} -XX:+UseGCLogFileRotation"
GC_OPTS="${GC_OPTS} -XX:NumberOfGCLogFiles=10 -XX:GCLogFileSize=50M"

OOM_OPTS="-XX:+HeapDumpOnOutOfMemoryError"
OOM_OPTS="${OOM_OPTS} -XX:HeapDumpPath=${APP_LOGS}/ -XX:+ExitOnOutOfMemoryError"

PERF_OPTS="-server -XX:+DisableExplicitGC -XX:+AlwaysPreTouch"
PERF_OPTS="${PERF_OPTS} -Djava.security.egd=file:/dev/./urandom"
PERF_OPTS="${PERF_OPTS} -Dfile.encoding=UTF-8"
PERF_OPTS="${PERF_OPTS} -Duser.timezone=Asia/Shanghai"

if [ "${JVM_DEBUG_ENABLED}" = "true" ]; then
    DEBUG_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=${JVM_DEBUG_PORT:-5005}"
else
    DEBUG_OPTS=""
fi

if [ "${JMX_ENABLED}" = "true" ]; then
    JMX_OPTS="-Dcom.sun.management.jmxremote"
    JMX_OPTS="${JMX_OPTS} -Dcom.sun.management.jmxremote.port=${JMX_PORT:-1099}"
    JMX_OPTS="${JMX_OPTS} -Dcom.sun.management.jmxremote.authenticate=false"
    JMX_OPTS="${JMX_OPTS} -Dcom.sun.management.jmxremote.ssl=false"
else
    JMX_OPTS=""
fi

JVM_OPTS="${DEBUG_OPTS} ${JMX_OPTS} ${PERF_OPTS}"
JVM_OPTS="${JVM_OPTS} -Xms${HEAP_MIN} -Xmx${HEAP_MAX}"
JVM_OPTS="${JVM_OPTS} -Xss${THREAD_STACK}"
JVM_OPTS="${JVM_OPTS} -XX:MetaspaceSize=${METASPACE_MIN} -XX:MaxMetaspaceSize=${METASPACE_MAX}"
JVM_OPTS="${JVM_OPTS} ${GC_OPTS} ${OOM_OPTS}"

SERVER_PORT="${SERVER_PORT:-8080}"

# 设置外部配置目录，application.yml 通过 ${XINSHI_CONFIG_DIR:/app/config} 引用
# Docker 环境默认 /app/config（volume 挂载），独立部署自动指向部署目录下的 config/
export XINSHI_CONFIG_DIR="${XINSHI_CONFIG_DIR:-${APP_DIR}/config}"

SPRING_OPTS="--spring.profiles.active=${SPRING_PROFILES_ACTIVE:-live}"
SPRING_OPTS="${SPRING_OPTS} --server.port=${SERVER_PORT}"

# ---------- 启动 ----------
start_service() {
    if [ -f "${PID_FILE}" ] && kill -0 "$(cat "${PID_FILE}")" 2>/dev/null; then
        echo "[WARN] ${APP_NAME} is already running (PID: $(cat "${PID_FILE}"))"
        exit 1
    fi

    mkdir -p "${APP_LOGS}" "${GC_LOG_DIR}"

    if [ ! -f "${APP_JAR}" ]; then
        echo "[ERROR] Jar file not found: ${APP_JAR}"
        exit 1
    fi

    echo "========================================"
    echo "  Application: ${APP_NAME}"
    echo "========================================"
    echo "  Profile:     ${SPRING_PROFILES_ACTIVE:-live}"
    echo "  Port:        ${SERVER_PORT}"
    echo "  Heap:        ${HEAP_MIN} ~ ${HEAP_MAX}"
    echo "  Logs:        ${APP_LOGS}"
    echo "========================================"

    nohup java ${JVM_OPTS} -jar ${APP_JAR} ${SPRING_OPTS} \
        > "${APP_LOGS}/app.log" 2>&1 &

    echo $! > "${PID_FILE}"
    echo "[INFO] ${APP_NAME} started (PID: $(cat "${PID_FILE}"))"

    # 等待启动完成（最多 60 秒）
    echo -n "[INFO] Waiting for startup"
    for i in $(seq 1 60); do
        if curl -sf "http://localhost:${SERVER_PORT}/api/health" > /dev/null 2>&1; then
            echo ""
            echo "[INFO] ${APP_NAME} is ready!"
            return 0
        fi
        echo -n "."
        sleep 1
    done
    echo ""
    echo "[WARN] Startup wait timed out. Check logs: tail -f ${APP_LOGS}/app.log"
}

# ---------- 停止 ----------
stop_service() {
    if [ ! -f "${PID_FILE}" ]; then
        PID=$(ps -ef | grep "app\.jar" | grep -v grep | awk '{print $2}')
        if [ -z "$PID" ]; then
            echo "[INFO] ${APP_NAME} is not running."
            return 0
        fi
    else
        PID=$(cat "${PID_FILE}")
    fi

    if ! kill -0 "$PID" 2>/dev/null; then
        echo "[INFO] ${APP_NAME} is not running (stale PID file)."
        rm -f "${PID_FILE}"
        return 0
    fi

    echo "[INFO] Stopping ${APP_NAME} (PID: ${PID})..."
    kill "$PID" 2>/dev/null || true

    for i in $(seq 1 30); do
        if ! kill -0 "$PID" 2>/dev/null; then
            echo "[INFO] ${APP_NAME} stopped."
            rm -f "${PID_FILE}"
            return 0
        fi
        sleep 1
    done

    echo "[WARN] Timeout, force killing (PID: ${PID})..."
    kill -9 "$PID" 2>/dev/null || true
    sleep 1
    rm -f "${PID_FILE}"
    echo "[INFO] ${APP_NAME} force stopped."
}

# ---------- 状态 ----------
status_service() {
    PID=""
    if [ -f "${PID_FILE}" ]; then
        PID=$(cat "${PID_FILE}")
    fi
    if [ -z "$PID" ] || ! kill -0 "$PID" 2>/dev/null; then
        PID=$(ps -ef | grep "app\.jar" | grep -v grep | awk '{print $2}')
    fi

    if [ -z "$PID" ]; then
        echo "[STATUS] ${APP_NAME} is NOT running."
        return 0
    fi

    echo "[STATUS] ${APP_NAME} is RUNNING"
    echo "  PID:      ${PID}"
    echo "  Port:     $(netstat -tlnp 2>/dev/null | grep "${PID}/java" | awk '{print $4}' | head -1 || echo 'unknown')"
    echo "  Uptime:   $(ps -o etime= -p "${PID}" 2>/dev/null | tr -d ' ' || echo 'unknown')"
    echo "  Memory:   $(ps -o rss= -p "${PID}" 2>/dev/null | awk '{printf "%.0f MB", $1/1024}' || echo 'unknown')"
    echo "  CPU:      $(ps -o %cpu= -p "${PID}" 2>/dev/null | tr -d ' ' || echo 'unknown')%"

    if command -v curl &>/dev/null; then
        HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -m 3 \
            "http://localhost:${SERVER_PORT:-8080}/api/health" 2>/dev/null || echo "000")
        if [ "$HTTP_CODE" = "200" ]; then
            echo "  Health:   OK"
        else
            echo "  Health:   FAIL (HTTP ${HTTP_CODE})"
        fi
    fi
}

# ---------- Watchdog 守护进程 ----------
WATCHDOG_SCRIPT="${APP_DIR}/watchdog.sh"

watchdog_service() {
    local cmd="${1:-start}"

    if [ ! -f "${WATCHDOG_SCRIPT}" ]; then
        echo "[ERROR] Watchdog script not found: ${WATCHDOG_SCRIPT}"
        exit 1
    fi

    case "${cmd}" in
        start)
            bash "${WATCHDOG_SCRIPT}" start
            ;;
        stop)
            bash "${WATCHDOG_SCRIPT}" stop
            ;;
        status)
            bash "${WATCHDOG_SCRIPT}" status
            ;;
        *)
            echo "Usage: $0 watchdog {start|stop|status}"
            exit 1
            ;;
    esac
}

# ---------- Main ----------
case "${1:-start}" in
    start)   start_service ;;
    stop)    stop_service ;;
    restart) stop_service; sleep 2; start_service ;;
    status)  status_service ;;
    watchdog)
        watchdog_service "${2:-start}"
        ;;
    *)
        echo "Usage: $0 {start|stop|restart|status|watchdog}"
        echo ""
        echo "Commands:"
        echo "  start              Start the application"
        echo "  stop               Stop the application"
        echo "  restart            Restart the application"
        echo "  status             Show application status"
        echo "  watchdog start     Start application with auto-restart watchdog"
        echo "  watchdog stop      Stop watchdog and application"
        echo "  watchdog status    Show watchdog and application status"
        exit 1
        ;;
esac
