#!/usr/bin/env bash
# ============================================================
# mock_h5_bound_parent.sh
# 一键 mock「已绑定微信 + 已绑定家长/学生」的 H5 成绩查询入口，
# 打开页面即处于 READY 状态，跳过微信授权和注册流程。
#
# 做的事：
#   1. 在 application-local.properties 中打开 h5.score-query.enabled
#      （需重启后端生效）
#   2. 向数据库写入：
#      - school_wechat_app      模拟公众号（appid 可换）
#      - school_wechat_account  模拟微信账号，parent_user_id 指向家长用户
#      - school_h5_session      一个长期有效的 H5 会话（token 可换）
#   3. 打印浏览器访问 URL 和设置会话 Cookie 的 JS 片段
#
# 用法：
#   ./mock_h5_bound_parent.sh
# 可选环境变量覆盖默认值，例如：
#   H5_MOCK_APPID=wxabc12345678 H5_MOCK_TOKEN=my-token ./mock_h5_bound_parent.sh
#
# 说明：
#   - appid 必须匹配后端 requireApp 的格式：^wx[0-9A-Za-z]{8,64}$
#   - 后端会话校验是 sha256(token) 与 school_h5_session.token_hash 比对，
#     无盐无密钥，因此脚本可离线算出哈希直接入库
#   - 幂等：重复执行不会产生脏数据
# ============================================================
set -euo pipefail

H5_MOCK_APPID="${H5_MOCK_APPID:-wxmock12345678}"
H5_MOCK_TOKEN="${H5_MOCK_TOKEN:-mock-h5-token-001}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3307}"
DB_USER="${DB_USER:-bank_test}"
DB_PASS="${DB_PASS:-bank_test}"
DB_NAME="${DB_NAME:-xinshi_admin}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_FILE="$SCRIPT_DIR/../src/main/resources/application-local.properties"

# --- sha256(hex)，兼容 macOS shasum 与 Linux sha256sum ---
sha256() {
  if command -v shasum >/dev/null 2>&1; then
    printf '%s' "$1" | shasum -a 256 | cut -d' ' -f1
  else
    printf '%s' "$1" | sha256sum | cut -d' ' -f1
  fi
}

MYSQL() {
  mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" 2>/dev/null "$@"
}

# --- 参数校验 ---
if ! printf '%s' "$H5_MOCK_APPID" | grep -qE '^wx[0-9A-Za-z]{8,64}$'; then
  echo "错误：appid 不合法（$H5_MOCK_APPID），必须匹配 ^wx[0-9A-Za-z]{8,64}$" >&2
  exit 1
fi

TOKEN_HASH="$(sha256 "$H5_MOCK_TOKEN")"
OPENID_HMAC="$(sha256 "mock-openid-$H5_MOCK_APPID")"
DUMMY_CSRF="$(sha256 "mock-csrf")"

# --- 1/4 打开功能开关（幂等）---
if grep -q '^h5.score-query.enabled' "$CONFIG_FILE" 2>/dev/null; then
  echo "[1/4] h5.score-query.enabled 已配置，跳过"
else
  printf '\n# H5 成绩查询开关（mock 脚本自动添加）\nh5.score-query.enabled=true\n' >> "$CONFIG_FILE"
  echo "[1/4] 已向 application-local.properties 添加 h5.score-query.enabled=true"
fi

# --- 2/4 模拟公众号 ---
MYSQL -e "INSERT INTO school_wechat_app
  (appid, app_secret_ref, oauth_callback_url, h5_entry_url, score_query_enabled, status)
  VALUES ('$H5_MOCK_APPID', 'mock-secret',
          'http://localhost:5173/api/h5/wechat/callback',
          'http://localhost:5173/h5/score', 1, 1)
  ON DUPLICATE KEY UPDATE score_query_enabled = 1, status = 1"
echo "[2/4] 模拟公众号已就绪：appid=$H5_MOCK_APPID"

# --- 3/4 家长用户与学生绑定 ---
# 优先复用已有的 parent_wang（id=4），不存在则创建模拟家长
PARENT_ID="$(MYSQL -N -e "SELECT id FROM sys_user WHERE login_name='parent_wang' AND status=1 AND is_deleted=0 LIMIT 1")"
if [ -z "$PARENT_ID" ]; then
  MYSQL -e "INSERT INTO sys_user (login_name, password_hash, real_name)
            VALUES ('parent_mock_wx', '{noop}mock', '微信模拟家长')"
  PARENT_ID="$(MYSQL -N -e "SELECT id FROM sys_user WHERE login_name='parent_mock_wx' LIMIT 1")"
fi

STUDENT_ID="$(MYSQL -N -e "SELECT s.id FROM school_student s WHERE s.status=1 AND s.is_deleted=0 ORDER BY s.id LIMIT 1")"
if [ -z "$STUDENT_ID" ]; then
  echo "警告：库中没有有效学生，页面将进入 ACCOUNT_INCOMPLETE 状态" >&2
else
  BOUND_CNT="$(MYSQL -N -e "SELECT COUNT(*) FROM school_student_parent WHERE parent_user_id=$PARENT_ID")"
  if [ "$BOUND_CNT" = "0" ]; then
    MYSQL -e "INSERT INTO school_student_parent
              (student_id, parent_user_id, relation_type, is_primary)
              VALUES ($STUDENT_ID, $PARENT_ID, 'father', 1)"
  fi
  echo "[3/4] 家长已绑定学生：sys_user id=$PARENT_ID -> student id=$STUDENT_ID"
fi

# --- 4/4 微信账号 + 有效会话 ---
MYSQL -e "INSERT INTO school_wechat_account
  (appid, openid_hmac, openid_ciphertext, parent_user_id, status)
  VALUES ('$H5_MOCK_APPID', '$OPENID_HMAC', 0x6D6F636B, $PARENT_ID, 1)
  ON DUPLICATE KEY UPDATE parent_user_id = $PARENT_ID, status = 1"
ACCOUNT_ID="$(MYSQL -N -e "SELECT id FROM school_wechat_account WHERE appid='$H5_MOCK_APPID' AND openid_hmac='$OPENID_HMAC' LIMIT 1")"

MYSQL -e "INSERT INTO school_h5_session
  (token_hash, csrf_token_hash, wechat_account_id, parent_user_id, appid, status, expires_at)
  VALUES ('$TOKEN_HASH', '$DUMMY_CSRF', $ACCOUNT_ID, $PARENT_ID, '$H5_MOCK_APPID', 1, '2099-12-31 23:59:59')
  ON DUPLICATE KEY UPDATE
    csrf_token_hash = VALUES(csrf_token_hash),
    wechat_account_id = VALUES(wechat_account_id),
    parent_user_id = VALUES(parent_user_id),
    status = 1,
    expires_at = '2099-12-31 23:59:59'"
echo "[4/4] 微信账号 + H5 会话已就绪（token_hash=${TOKEN_HASH}）"

# --- 使用说明 ---
echo
echo "========== Mock H5 已绑定账号就绪 =========="
echo "appid : $H5_MOCK_APPID"
echo "token : $H5_MOCK_TOKEN"
echo "家长  : sys_user id=${PARENT_ID}（已绑定学生 id=${STUDENT_ID:-无}）"
echo
echo "1) 重启后端使 h5.score-query.enabled=true 生效（IntelliJ 里重新 Run）"
echo "2) 浏览器打开："
echo "   http://localhost:5173/h5/score?appid=$H5_MOCK_APPID"
echo "3) 在 DevTools Console 里执行（设置会话 Cookie）："
echo "   document.cookie = 'XINSHI_H5_SESSION=$H5_MOCK_TOKEN; path=/'"
echo "4) 刷新页面，应直接进入成绩查询（flowState=READY）"
echo
echo "验证（后端重启后）："
echo "   curl -s -H 'Cookie: XINSHI_H5_SESSION=$H5_MOCK_TOKEN' \\"
echo "     'http://localhost:8080/api/h5/bootstrap?appid=$H5_MOCK_APPID'"

# --- 自动验证（后端未重启时会提示维护中）---
echo
VERIFY="$(curl -s -m 3 -H "Cookie: XINSHI_H5_SESSION=$H5_MOCK_TOKEN" "http://localhost:8080/api/h5/bootstrap?appid=$H5_MOCK_APPID" || true)"
if printf '%s' "$VERIFY" | grep -q '"flowState"'; then
  echo "验证结果：$(printf '%s' "$VERIFY" | grep -o '"flowState":"[A-Z_]*"')"
elif printf '%s' "$VERIFY" | grep -q 'FEATURE_MAINTENANCE'; then
  echo "验证结果：FEATURE_MAINTENANCE —— 功能开关尚未生效，请重启后端后重试"
else
  echo "验证结果：后端无响应（$VERIFY）"
fi
