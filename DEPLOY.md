# xinshi-admin 部署说明

## 环境要求

- JDK 8+
- MySQL 5.7+（或 8.0）
- Maven 3.6+（仅构建时需要）
- 操作系统：Linux（推荐 CentOS 7+ / Ubuntu 18.04+）

## 构建打包

```bash
# 使用默认版本号（时间戳）
./build/build.sh

# 指定版本号
./build/build.sh v2.8.0
```

构建产物位于 `dist/xinshi-admin-{version}.tar.gz`（同时保留在 `target/` 下）。

## 快速部署

### 1. 上传并解压

```bash
tar -xzf xinshi-admin-20260708-040206.tar.gz
cd xinshi-admin-20260708-040206
```

### 2. 修改配置文件

部署包中的配置文件位于 JAR 包外部，可以直接编辑，无需重新打包。

部署包仅包含生产环境（live profile）配置，`startup.sh` 默认以 `SPRING_PROFILES_ACTIVE=live` 启动：

```bash
# 编辑生产环境配置
vim config/application-live.properties
```

主要配置项：
- `spring.datasource.url` — 数据库连接地址
- `spring.datasource.username` — 数据库用户名
- `spring.datasource.password` — 数据库密码
- `xinshi.scheool.transcript.output-dir` — 成绩单输出目录
- `xinshi.deepseek.api-key` — DeepSeek API Key（可选）

### 3. 初始化数据库

```bash
# 按序号依次执行 SQL 脚本
mysql -u YOUR_USER -p < scripts/mysql/001_school_class_score_management.sql
mysql -u YOUR_USER -p < scripts/mysql/002_school_class_score_management_seed.sql
mysql -u YOUR_USER -p < scripts/mysql/003_school_class_score_management_add_comment_fields.sql
mysql -u YOUR_USER -p < scripts/mysql/004_role_backend_driven.sql
```

### 4. 启动服务

```bash
./startup.sh start
```

日志文件位于 `logs/` 目录。

## 运维命令

| 命令 | 说明 |
|------|------|
| `./startup.sh start` | 启动服务 |
| `./startup.sh stop` | 停止服务 |
| `./startup.sh restart` | 重启服务 |
| `./startup.sh status` | 查看服务状态 |
| `./startup.sh watchdog start` | 启动守护进程（自动重启模式） |
| `./startup.sh watchdog stop` | 停止守护进程和应用 |
| `./startup.sh watchdog status` | 查看守护进程和应用状态 |
| `tail -f logs/app.log` | 实时查看应用日志 |
| `tail -f logs/watchdog.log` | 查看守护进程日志 |
| `tail -f logs/gc/gc-*.log` | 查看 GC 日志 |

## Watchdog 守护进程（自动重启）

部署包包含 `watchdog.sh`，用于监控应用进程并在异常挂掉时自动重新拉起。

### 启动方式

```bash
# 通过 startup.sh
./startup.sh watchdog start

# 或直接调用
./watchdog.sh start
```

### 工作原理

- 每 10 秒通过 PID 检测 + `/api/health` 健康接口检查应用状态
- 应用异常退出时，自动调用 `startup.sh start` 重新拉起
- 采用指数退避策略（5s → 10s → 20s → ... → 最长 120s）避免频繁重启
- 5 分钟内最多重启 5 次，超过阈值则停止并记录 FATAL 日志，需人工介入
- 超过 5 分钟崩溃窗口后，重启计数器自动重置

### 配置参数（环境变量）

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `WD_CHECK_INTERVAL` | 10 | 健康检查间隔（秒） |
| `WD_MAX_RETRIES` | 5 | 时间窗口内最大重启次数 |
| `WD_CRASH_WINDOW` | 300 | 重启计数窗口（秒） |
| `WD_BACKOFF_BASE` | 5 | 初始退避秒数 |
| `WD_BACKOFF_MAX` | 120 | 最大退避秒数 |
| `WD_STARTUP_TIMEOUT` | 60 | 启动后等待健康超时秒数 |
| `WD_HEALTH_URL` | http://localhost:8080/api/health | 健康检查 URL |

### 崩溃恢复日志示例

```
[2026-08-02 14:35:00] [CRASH] Application PID 12346 not responding
[2026-08-02 14:35:05] [RETRY] Restarting in 5s (attempt 1/5)...
[2026-08-02 14:35:15] [RECOVER] Application restarted successfully (new PID: 12400)
```

超过最大重试次数的日志：
```
[2026-08-02 14:40:00] [FATAL] Crash loop detected: 5 crashes in 300s. Giving up.
```

## systemd 服务（备选方案）

如果服务器支持 systemd，可使用部署包中的 `xinshi-admin.service` 替代 watchdog.sh：

```bash
# 1. 编辑服务文件中部署路径等配置
vim xinshi-admin.service

# 2. 安装服务
sudo cp xinshi-admin.service /etc/systemd/system/
sudo systemctl daemon-reload

# 3. 启动并设置开机自启
sudo systemctl start xinshi-admin
sudo systemctl enable xinshi-admin

# 4. 查看状态
sudo systemctl status xinshi-admin
```

systemd 通过 `Restart=always` 实现自动重启，`StartLimitBurst=5` + `StartLimitInterval=300` 防止 crash loop。

## 目录结构

```
xinshi-admin-{version}/
├── app.jar                 # Spring Boot 可执行 jar
├── startup.sh              # 服务管理脚本（start|stop|restart|status|watchdog）
├── watchdog.sh             # 守护进程脚本（异常自动重启）
├── xinshi-admin.service    # systemd 服务文件（备选方案）
├── VERSION.txt             # 构建版本信息
├── config/                 # 外部配置文件（可在服务器上直接修改）
│   └── application-live.properties
├── logs/                   # 日志目录（运行时生成）
│   ├── gc/                 # GC 日志
│   └── watchdog.log        # 守护进程日志
├── resources/              # 资源文件
│   └── picture/
├── dist/                   # 前端构建产物（如有）
└── scripts/mysql/          # 数据库初始化 SQL
```

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `HEAP_MIN` | 256m | 最小堆内存 |
| `HEAP_MAX` | 4g | 最大堆内存 |
| `SERVER_PORT` | 8080 | 服务端口 |
| `SPRING_PROFILES_ACTIVE` | live | Spring Profile（固定为 live） |
| `JVM_DEBUG_ENABLED` | false | 是否开启远程调试（端口 5005） |
| `JMX_ENABLED` | false | 是否开启 JMX 监控（端口 1099） |

示例 — 使用自定义端口和堆内存启动：

```bash
SERVER_PORT=9090 HEAP_MIN=512m HEAP_MAX=2g ./startup.sh start
```
