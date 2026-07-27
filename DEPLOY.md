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
| `tail -f logs/app.log` | 实时查看应用日志 |
| `tail -f logs/gc/gc-*.log` | 查看 GC 日志 |

## 目录结构

```
xinshi-admin-{version}/
├── app.jar              # Spring Boot 可执行 jar
├── startup.sh           # 服务管理脚本（start|stop|restart|status）
├── VERSION.txt          # 构建版本信息
├── config/              # 外部配置文件（可在服务器上直接修改）
│   └── application-live.properties
├── logs/                # 日志目录（运行时生成）
│   └── gc/              # GC 日志
├── resources/           # 资源文件
│   └── picture/
├── dist/                # 前端构建产物（如有）
└── scripts/mysql/       # 数据库初始化 SQL
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
