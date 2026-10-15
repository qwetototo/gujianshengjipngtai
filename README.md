# Web 固件更新控制台

Java 17 + Spring Boot + Vue 3 的 HTTP OTA 固件发布控制台。

## 数据持久化

- 固件元数据和操作日志保存在 MySQL；`backend/src/main/resources/db/migration/V1__init.sql` 是可直接查看的表结构，首次启动由 Flyway 自动建表。
- `.pack` 文件保存在 `OTA_STORAGE_DIR` 指向的文件系统目录，按设备类型分目录；数据库只保存相对路径、大小和 MD5。
- 同一设备类型只保留最新一条固件记录（`device_model` 唯一键）：重复上传会覆盖旧记录（id 不变）并删除旧文件，操作日志会记录被覆盖的版本。固件记录 id 为自增整数。
- 删除固件会同时删除数据库记录和文件，并保留删除日志。备份时必须同时备份 MySQL 和固件目录。
- 本地默认连接 `127.0.0.1:3306/ceshi`，用户名 `root`。运行本地脚本时会安全提示输入数据库密码，密码不会写入项目文件。
- 表结构变更后本地已有旧表需手动处理：删除 `firmware`、`operation_log`（及 `flyway_schema_history`）后重启，否则 Flyway 会因 V1 校验和不一致而失败。

## 本地测试

需先启动本地 MySQL，并准备 Java 17 或更高版本的 JDK、Node.js 20.19+ 或 22.12+。Windows 可通过 `JAVA_HOME` 指向 JDK 安装目录。

1. 启动本地 MySQL 服务。
2. 在项目根目录运行 `run-local.ps1`，按提示输入本地 MySQL 密码。默认使用 `E:\固件暂存`；测试管理员为 `admin / 123456`。
3. 新开终端运行：

```powershell
cd frontend
npm ci
npm run dev
```

前端开发服务器默认使用 `http://localhost:5173`，端口占用时 Vite 会自动切换到下一个端口。Vite 将 `/api` 转发到后端 `8080`。

首次后端启动会通过 Flyway 在配置的 MySQL 库创建 `firmware`、`operation_log`、`firmware_history` 和迁移历史表。`V2` 向前迁移只新增上一版记录表，不删除现有业务数据。

## 磁盘预警

后端启动 5 秒后检查固件对象存储实际数据卷，此后每 60 秒复查。剩余空间低于总空间 15% 或低于 10GB 时，控制台记录预警并在操作日志新增一条“磁盘预警”；空间恢复后写入“空间恢复”。前端每分钟刷新预警状态和操作日志。上传及回滚前会计算对象写入/复制的临时空间，剩余空间无法满足文件大小及 2GB 安全余量时拒绝操作。

相关值可通过环境变量调整：`OTA_STORAGE_WARNING_FREE_PERCENT`、`OTA_STORAGE_WARNING_FREE_BYTES`、`OTA_STORAGE_UPLOAD_RESERVE_BYTES`、`OTA_STORAGE_MONITOR_INTERVAL`。所有容量值单位为字节。

## 生产部署

服务器目标为 Ubuntu/Linux，部署使用 Docker Compose、MySQL 8.4 和 Nginx。生产环境从 `.env` 读取数据库、管理员和存储配置，`.env` 已加入忽略列表，不应提交或公开。

1. 在服务器安装 Docker Engine 与 Compose 插件。镜像内完成 Java 和前端构建，服务器无需安装 Java 或 Node.js。
2. 复制 `.env.prod.example` 为 `.env`。设置独立的 MySQL root 密码、应用数据库密码和至少 16 位随机管理员密码；`MYSQL_PASSWORD` 与 `OTA_DATABASE_PASSWORD` 必须相同。
3. 将 TLS 证书放入 `deploy/certs/fullchain.pem` 和 `deploy/certs/privkey.pem`。证书私钥不要加入版本控制。
4. 修改 `.env` 中的 `OTA_STORAGE_PATH` 为服务器固件存储目录，例如 `/srv/ota/firmware`，并将目录属主设为容器后端 UID `10001`（例如 `sudo install -d -o 10001 -g 10001 /srv/ota/firmware`）。固件按设备类型保存在该宿主机目录，MySQL 只保存文件相对路径、大小和 MD5。设置适当的备份和磁盘告警策略。
5. 将 `ota.example.com` 替换为你的真实域名并配置 DNS，确认 TLS 证书对应此域名。构建并启动：

```bash
docker compose --env-file .env -f docker-compose.prod.yml up -d --build
```

6. 启动并查看日志：

```bash
docker compose --env-file .env -f docker-compose.prod.yml logs -f backend nginx
```

MySQL 和 MinIO 只加入 Docker 内部网络，不映射公网端口；公网仅暴露 Nginx 的 80/443。生产管理员密码弱于 16 位会阻止后端启动。OTA 查询与固件下载公开，登录、上传、列表、删除、回滚、操作日志和磁盘状态接口要求管理员登录。数据库与 MinIO 数据目录必须纳入同一备份周期；升级前先备份两者。不要通过删除业务表或 `flyway_schema_history` 解决迁移校验问题，应修复迁移脚本或追加新的向前迁移。

### 本地 Docker 测试

复制 `.env.docker.example` 为 `.env` 后运行 `docker compose up -d --build`，访问 `http://localhost:18080`，测试用户为 `admin` / `123456`。本地使用独立 MySQL 数据卷，固件文件绑定到 `.env` 的 `OTA_STORAGE_PATH`（默认 `E:/固件暂存`），可直接在资源管理器查看。不要将测试密码用于生产；`docker compose down -v` 会删除数据库数据卷，但不会删除绑定目录里的固件文件。

## 主要接口

设备端（公开，无需登录）：

- `GET /api/firmware/version?device_model=设备类型&current_version=当前版本`：查询升级信息。服务器比较该设备最新固件的 `version` 与 `current_version`：不同则返回 `version`、`url`、`size`、`md5` 四字段；相同（或无该设备固件）则返回 `{"message": "No new version available"}`。版本号直接以版本字符串表示（如 `1.0.0`），无需单独的整数版本号。
- `GET /api/firmware/download/{id}`：下载 `.pack` 文件，`Content-Length` 与版本查询返回的 `size` 一致，响应体为二进制流。

管理端（需管理员登录）：

- `POST /api/auth/login`：管理员登录。
- `POST /api/admin/firmware/upload`：multipart 上传，字段 `file`、`device_model`、`version`、`uploader`、`description`；返回 `{code, message, data}`，`file_size`、`md5` 由服务器计算；同一设备类型仅显示一个当前版，每次覆盖保留一版上一版于该设备目录 `history` 下。
- `GET /api/admin/firmware`、`GET /api/admin/firmware/{id}`、`DELETE /api/admin/firmware/{id}`：列表、详情、删除。
- `POST /api/admin/firmware/{id}/rollback`：回滚到上一版；回滚后原当前版成为新的上一版。
- `GET /api/logs`、`GET /api/system/storage`：操作日志与磁盘状态。

接口 JSON 统一使用蛇形命名（如 `device_model`、`file_size`）。
