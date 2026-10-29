# Web 固件更新控制台
远仓：git@github.com:qwetototo/gujianshengjiptai.git

http://1.15.88.116:18082/
Java 17 + Spring Boot + Vue 3 的 HTTP OTA 固件发布控制台。

## 数据持久化

- 固件元数据、操作日志和管理员账号保存在 MySQL；`backend/src/main/resources/db/migration/` 下的 Flyway 脚本会按顺序创建和升级表结构。
- 管理员表只允许一个 `admin` 账号，密码以 PBKDF2 哈希存储。首次启动时使用 `OTA_ADMIN_PASSWORD` 初始化；已有账号时不会用环境变量覆盖数据库密码。
- `.pack` 文件保存在 `OTA_STORAGE_DIR` 指向的文件系统目录，按产品（`product`）分目录；数据库只保存相对路径、大小和摘要。
- 同一产品保留最新版本和默认 5 个历史版本：重复上传会把上一版转为历史版本并发布新版本。固件记录 id 为自增整数。
- 删除固件会同时删除数据库记录和文件，并保留删除日志。备份时必须同时备份 MySQL 和固件目录。
- 本地默认连接 `127.0.0.1:3306/ceshi`，用户名 `root`。运行本地脚本时会安全提示输入数据库密码，密码不会写入项目文件。
- 已有数据库通过新增 Flyway 迁移升级，不要删除 `firmware`、`operation_log` 或 `flyway_schema_history`。

## 本地测试

需先启动本地 MySQL，并准备 Java 17 或更高版本的 JDK、Node.js 20.19+ 或 22.12+。Windows 可通过 `JAVA_HOME` 指向 JDK 安装目录。

1. 启动本地 MySQL 服务。
2. 在项目根目录运行 `run-local.ps1`，按提示输入本地 MySQL 密码。默认使用 `E:\固件暂存`；首次初始化的测试管理员为 `admin / 123456`。
3. 新开终端运行：

```powershell
cd frontend
npm ci
npm run dev
```

前端开发服务器默认使用 `http://localhost:5173`，端口占用时 Vite 会自动切换到下一个端口。Vite 将 `/api` 转发到后端 `8080`。

首次后端启动会通过 Flyway 创建业务表。升级到当前版本时，`V3` 会将已有当前版本和上一版合并迁移到完整版本记录中并删除旧的单上一版表；`V4` 会删除缺少清单字段的旧数据行，并把 `device_model` 列改名为 `product`（以产品标识为唯一设备键）。被 `V4` 删除的旧版本对应的固件文件不会自动清理，可按需手动删除存储目录中对应的历史文件。

每个产品保存一个最新版本和默认 5 个历史版本。可通过 `OTA_FIRMWARE_HISTORY_RETAIN_COUNT` 配置额外保留的历史版本数；超出数量时在下次成功发布后删除最旧版本记录及对应文件。

## 磁盘预警

后端启动 5 秒后检查固件存储实际数据卷，此后每 60 秒复查。剩余空间低于总空间 15% 或低于 10GB 时，控制台记录预警并在操作日志新增一条“磁盘预警”；空间恢复后写入“空间恢复”。前端每分钟刷新预警状态和操作日志。上传前会检查可用空间是否满足新文件大小及 2GB 安全余量。

相关值可通过环境变量调整：`OTA_STORAGE_WARNING_FREE_PERCENT`、`OTA_STORAGE_WARNING_FREE_BYTES`、`OTA_STORAGE_UPLOAD_RESERVE_BYTES`、`OTA_STORAGE_MONITOR_INTERVAL`。所有容量值单位为字节。

## 生产部署

服务器目标为 Ubuntu/Linux，部署使用 Docker Compose、MySQL 8.4 和 Nginx。生产环境从 `.env` 读取数据库、管理员和存储配置，`.env` 已加入忽略列表，不应提交或公开。

1. 在服务器安装 Docker Engine 与 Compose 插件。镜像内完成 Java 和前端构建，服务器无需安装 Java 或 Node.js。
2. 复制 `.env.prod.example` 为 `.env`。设置独立的 MySQL root 密码、应用数据库密码和管理员初始密码；`MYSQL_PASSWORD` 与 `OTA_DATABASE_PASSWORD` 必须相同。
3. 将 TLS 证书放入 `deploy/certs/fullchain.pem` 和 `deploy/certs/privkey.pem`。证书私钥不要加入版本控制。
4. 修改 `.env` 中的 `OTA_STORAGE_PATH` 为服务器固件存储目录，例如 `/srv/ota/firmware`，并将目录属主设为容器后端 UID `10001`（例如 `sudo install -d -o 10001 -g 10001 /srv/ota/firmware`）。固件按设备类型分目录保存，MySQL 保存版本信息、文件相对路径和摘要。设置适当的备份和磁盘告警策略。
5. 将 `ota.example.com` 替换为你的真实域名并配置 DNS，确认 TLS 证书对应此域名。构建并启动：

```bash
docker compose --env-file .env -f docker-compose.prod.yml up -d --build
```

6. 启动并查看日志：

```bash
docker compose --env-file .env -f docker-compose.prod.yml logs -f backend nginx
```

MySQL 只加入 Docker 内部网络，不映射公网端口；公网仅暴露 Nginx 的 80/443。管理员密码不限制长度与复杂度，但不能为空。OTA 查询与固件下载公开，登录、上传、列表、删除、操作日志和磁盘状态接口要求管理员登录。数据库与固件宿主机目录必须纳入备份；升级前先备份两者。不要通过删除业务表或 `flyway_schema_history` 解决迁移校验问题，应修复迁移脚本或追加新的向前迁移。

### 本地 Docker 测试

复制 `.env.docker.example` 为 `.env` 后运行 `docker compose up -d --build`，访问 `http://localhost:18080`，测试用户为 `admin` / `123456`。本地使用独立 MySQL 数据卷，固件文件绑定到 `.env` 的 `OTA_STORAGE_PATH`（默认 `E:/固件暂存`），可直接在资源管理器查看。不要将测试密码用于生产；`docker compose down -v` 会删除数据库数据卷，但不会删除绑定目录里的固件文件。

### 忘记管理员密码

停止后端后，将运行环境中的 `OTA_ADMIN_PASSWORD` 改为新密码，再连接正在使用的 MySQL 数据库并执行：

```sql
DELETE FROM admin_user WHERE username = 'admin';
```

重新启动后端时，系统会用新密码生成 PBKDF2 哈希并重建唯一的 `admin` 记录。Docker 部署需更新 `.env`；IntelliJ 本地运行则修改 Run Configuration 的环境变量。不要删除整张管理员表或其他业务表。

## 主要接口

设备端（公开，无需登录）：

- `GET /api/firmware/version?product=产品标识`：返回该产品当前版本的完整嵌入式清单（17 个固定字段 + 可选 `release_notes`）。无论设备是否已是该版本，始终返回完整清单，是否需要升级由设备/App 自行比较；固定字段由后端填写，`size`、`md5`、`sha256`、`package_id` 和下载地址由平台生成，清单正文不超过 2048 字节。
- `GET /api/firmware/download/{id}`：下载 `.pack` 文件，`Content-Length` 与版本查询返回的 `size` 一致，响应体为二进制流。

管理端（需管理员登录）：

- `POST /api/auth/login`：管理员登录。
- `POST /api/admin/firmware/upload`：multipart 上传，需填写 `product`、`hardware_id`、`partition_id`、`sdk_compat`、`target_build`、`version`、`version_code`、`work_size`、`uploader`；`description`、`release_notes` 可选。大小、MD5 和 SHA-256 由服务器计算。
- `GET /api/admin/firmware`、`GET /api/admin/firmware/{id}`、`DELETE /api/admin/firmware/{id}`：列表、详情、删除。
- `GET /api/logs`、`GET /api/system/storage`：操作日志与磁盘状态。

接口 JSON 统一使用蛇形命名（如 `product`、`file_size`）。

## 在线修改密码

登录后点击侧栏“修改密码”，填写当前密码和两次新密码。新密码不限制强度，允许 `123456`，但不能为空。密码以 PBKDF2 哈希保存，修改后所有登录会话立即失效，需要重新登录。修改结果持久化在数据库，重启不会被 `OTA_ADMIN_PASSWORD` 覆盖；该环境变量用于首次初始化和忘记密码时重建账号。

接口：`POST /api/auth/password`，要求有效 Bearer 登录令牌，JSON 为 `current_password`、`new_password`，成功返回 204。无需数据库表结构迁移；部署需重建后端镜像并同步前端 dist。
