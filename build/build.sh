#!/bin/bash
# ============================================================
# xinshi-admin 项目打包脚本
# 用法：./build/build.sh [version]
#   version  可选，自定义版本号，默认使用时间戳（如 20260727-093000）
#
# 输出：target/xinshi-admin-{version}.tar.gz
# ============================================================

set -e

# ---------- 目录定位 ----------
BUILD_DIR="$(cd "$(dirname "$0")" && pwd)"        # build/ 目录
BACKEND_DIR="$(cd "${BUILD_DIR}/.." && pwd)"       # xinshi-admin-backend/ 目录
ROOT_DIR="$(cd "${BACKEND_DIR}/.." && pwd)"        # xinshi-admin/ 项目根目录

# ---------- 项目信息 ----------
PROJECT_NAME="xinshi-admin"
VERSION="${1:-$(date +%Y%m%d-%H%M%S)}"

# ---------- 路径定义 ----------
MAVEN_BUILD_DIR="${BACKEND_DIR}/target"
PACKAGE_NAME="${PROJECT_NAME}-${VERSION}"
PACKAGE_DIR="${MAVEN_BUILD_DIR}/${PACKAGE_NAME}"
DIST_DIR="${ROOT_DIR}/dist"
OUTPUT_DIST_DIR="${BACKEND_DIR}/dist"

# ---------- 颜色输出 ----------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }
step()  { echo -e "${BLUE}[STEP]${NC} $1"; }

echo ""
echo "========================================"
echo "  ${PROJECT_NAME} 项目打包"
echo "========================================"
echo "  Version:     ${VERSION}"
echo "  Backend:     ${BACKEND_DIR}"
echo "  Output:      ${MAVEN_BUILD_DIR}/${PACKAGE_NAME}.tar.gz"
echo "========================================"
echo ""

# ============================================================
# Step 1: Maven 编译打包
# ============================================================
step "1/9 Maven clean package..."
cd "${BACKEND_DIR}"
mvn clean package -DskipTests -q
info "Maven build success"

# ============================================================
# Step 2: 定位 JAR 文件
# ============================================================
step "2/9 Locating jar artifact..."
JAR_FILE=$(ls "${MAVEN_BUILD_DIR}"/*.jar 2>/dev/null | grep -v 'sources' | grep -v 'javadoc' | head -1)
if [ -z "${JAR_FILE}" ] || [ ! -f "${JAR_FILE}" ]; then
    error "Jar file not found in ${MAVEN_BUILD_DIR}"
    echo "       Available files:"
    ls -la "${MAVEN_BUILD_DIR}"/*.jar 2>/dev/null || echo "       (none)"
    exit 1
fi
info "Found: $(basename "${JAR_FILE}")"

# ============================================================
# Step 3: 创建部署目录结构
# ============================================================
step "3/9 Creating deployment directory structure..."
rm -rf "${PACKAGE_DIR}"
mkdir -p "${PACKAGE_DIR}/config"
mkdir -p "${PACKAGE_DIR}/logs/gc"
mkdir -p "${PACKAGE_DIR}/scripts/mysql"
mkdir -p "${PACKAGE_DIR}/resources"
info "Directory structure created"

# ============================================================
# Step 4: 复制 JAR，重命名为 app.jar
# ============================================================
step "4/9 Copying application jar..."
cp "${JAR_FILE}" "${PACKAGE_DIR}/app.jar"
info "app.jar copied"

# ============================================================
# Step 5: 复制配置文件（仅 live，生产环境部署包）
# ============================================================
step "5/9 Copying configuration files (live profile only)..."
if [ -f "${BACKEND_DIR}/config/application-live.properties" ]; then
    cp "${BACKEND_DIR}/config/application-live.properties" "${PACKAGE_DIR}/config/"
    info "application-live.properties copied"
else
    warn "application-live.properties not found"
fi
# 同时复制 application.properties 作为默认配置（如有）
if [ -f "${BACKEND_DIR}/config/application.properties" ]; then
    cp "${BACKEND_DIR}/config/application.properties" "${PACKAGE_DIR}/config/"
    info "application.properties copied"
fi

# ============================================================
# Step 6: 复制数据库脚本
# ============================================================
step "6/9 Copying SQL scripts..."
if [ -d "${BACKEND_DIR}/scripts/mysql" ] && [ "$(ls -A "${BACKEND_DIR}/scripts/mysql" 2>/dev/null)" ]; then
    cp "${BACKEND_DIR}/scripts/mysql/"*.sql "${PACKAGE_DIR}/scripts/mysql/" 2>/dev/null || true
    info "$(ls "${PACKAGE_DIR}/scripts/mysql/" | wc -l | tr -d ' ') SQL scripts copied"
else
    warn "No SQL scripts found, skipping"
fi

# ============================================================
# Step 7: 复制资源文件
# ============================================================
step "7/9 Copying resource files..."
RESOURCE_COUNT=0
if [ -d "${BACKEND_DIR}/resources" ] && [ "$(ls -A "${BACKEND_DIR}/resources" 2>/dev/null)" ]; then
    cp -r "${BACKEND_DIR}/resources/"* "${PACKAGE_DIR}/resources/"
    RESOURCE_COUNT=$(find "${PACKAGE_DIR}/resources" -type f | wc -l | tr -d ' ')
fi
# 如果存在前端构建产物（dist 目录），一并复制
if [ -d "${DIST_DIR}" ] && [ "$(ls -A "${DIST_DIR}" 2>/dev/null)" ]; then
    mkdir -p "${PACKAGE_DIR}/dist"
    cp -r "${DIST_DIR}/"* "${PACKAGE_DIR}/dist/"
    DIST_COUNT=$(find "${PACKAGE_DIR}/dist" -type f | wc -l | tr -d ' ')
    info "${RESOURCE_COUNT} resource files + ${DIST_COUNT} frontend dist files copied"
else
    info "${RESOURCE_COUNT} resource files copied (no frontend dist)"
fi

# ============================================================
# Step 8: 复制启动脚本并打包
# ============================================================
step "8/9 Creating deploy archive..."

# 复制启动脚本
if [ -f "${BACKEND_DIR}/startup.sh" ]; then
    cp "${BACKEND_DIR}/startup.sh" "${PACKAGE_DIR}/"
    chmod +x "${PACKAGE_DIR}/startup.sh"
    info "startup.sh copied and made executable"
else
    warn "startup.sh not found, skipping"
fi

# 复制守望进程脚本
if [ -f "${BACKEND_DIR}/watchdog.sh" ]; then
    cp "${BACKEND_DIR}/watchdog.sh" "${PACKAGE_DIR}/"
    chmod +x "${PACKAGE_DIR}/watchdog.sh"
    info "watchdog.sh copied and made executable"
else
    warn "watchdog.sh not found, skipping"
fi

# 复制 systemd 服务文件
if [ -f "${BACKEND_DIR}/xinshi-admin.service" ]; then
    cp "${BACKEND_DIR}/xinshi-admin.service" "${PACKAGE_DIR}/"
    info "xinshi-admin.service copied"
else
    warn "xinshi-admin.service not found, skipping"
fi

# 生成版本信息文件
cat > "${PACKAGE_DIR}/VERSION.txt" << EOF
========================================
  ${PROJECT_NAME}
========================================
Version:     ${VERSION}
Build Date:  $(date '+%Y-%m-%d %H:%M:%S')
Profile:     live (default)
Built By:    $(whoami)
Host:        $(hostname)
========================================
EOF

# 打包
cd "${MAVEN_BUILD_DIR}"
tar -czf "${PACKAGE_NAME}.tar.gz" "${PACKAGE_NAME}"

# 清理临时目录
rm -rf "${PACKAGE_DIR}"

# ============================================================
# Step 9: 复制 tar 包到 dist 目录
# ============================================================
step "9/9 Copying archive to dist/..."
mkdir -p "${OUTPUT_DIST_DIR}"
cp "${MAVEN_BUILD_DIR}/${PACKAGE_NAME}.tar.gz" "${OUTPUT_DIST_DIR}/"
info "Archive copied to ${OUTPUT_DIST_DIR}/${PACKAGE_NAME}.tar.gz"

# ============================================================
# 输出结果
# ============================================================
ARCHIVE_PATH="${MAVEN_BUILD_DIR}/${PACKAGE_NAME}.tar.gz"
DIST_ARCHIVE_PATH="${OUTPUT_DIST_DIR}/${PACKAGE_NAME}.tar.gz"
ARCHIVE_SIZE=$(du -h "${ARCHIVE_PATH}" | cut -f1)

echo ""
echo "========================================"
echo "  ${GREEN}Build Complete!${NC}"
echo "========================================"
echo "  Package: ${ARCHIVE_PATH}"
echo "  Dist:    ${DIST_ARCHIVE_PATH}"
echo "  Size:    ${ARCHIVE_SIZE}"
echo "  Profile: live (default)"
echo ""
echo "  Deploy steps:"
echo "    tar -xzf ${PACKAGE_NAME}.tar.gz"
echo "    cd ${PACKAGE_NAME}"
echo ""
echo "    1. Edit config:        vim config/application-live.properties"
echo "    2. Init database:      mysql -u USER -p < scripts/mysql/ddl_init.sql"
echo "    3. Start service:      ./startup.sh start"
echo "       (or with watchdog): ./startup.sh watchdog start"
echo "    4. Check status:       ./startup.sh status"
echo "========================================"
