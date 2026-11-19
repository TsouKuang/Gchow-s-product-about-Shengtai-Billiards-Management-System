# Gchow’s product about “圣泰台球管理系统”

永州圣泰台球俱乐部管理系统，包含 Windows 前台工作台、微信小程序和共用业务后端。

项目由 Spring Boot 后端、MySQL 数据库、Vue 管理界面、Electron PC 客户端和原生微信小程序组成。

## 当前入口

- 桌面“毕业设计/启动毕业设计.cmd”启动现用 PC 客户端。
- PC 包目录：`desktop/release-sync-filters/win-unpacked`。
- PC 和微信小程序共用云端接口：https://www.yongzhoushengtai.homes/api 。
- 本地开发数据库是 127.0.0.1:3306 的 billiard_db，业务账号 shengtai_app。私有配置保存在 `.local`。
- 云端与本地数据库分别保存数据，不自动双向同步。不要把本地快照直接覆盖云端业务数据。

## 源码与生成物

| 目录 | 用途 |
| --- | --- |
| backend/src/main/java | 后端接口、业务与数据库升级 |
| backend/src/main/resources/schema.sql | 当前建表定义的维护入口 |
| backend/src/main/resources/service-catalog-*.json | 初始化与增量目录迁移，版本号表示升级顺序，不能当作旧文件删除 |
| backend/src/main/resources/static/assets | 后端内置图片 |
| admin-web/src | PC 管理界面源码 |
| admin-web/public/assets | PC 图片源文件，Electron 需要独立提供静态图片，不能仅因与后端相同就删除 |
| desktop | Electron 客户端源码及打包配置 |
| miniprogram | 微信小程序源码 |
| database | MySQL 历史快照 |
| docs/asset-sources | 素材参考、来源和生成记录 |
| scripts/windows | MySQL、实验通道和微信配置脚本 |
| deploy | 部署配置及维护脚本 |

前端 dist、后端 target 和 desktop/web 都是生成物，不在这些目录直接修改代码。现用 MySQL 数据目录、上传图片、密钥与账号配置不参与源码清理。

克隆后的启动步骤见 [公开源码使用说明](docs/公开源码使用说明.md)，部署配置见 [云端部署说明](deploy/云端部署说明.md)。

## 检查与构建

最终版的模块分工、模板维护和格式规范见 [源码维护说明](docs/源码维护说明.md)。

需要 Java 17 或以上、Maven 与 Node.js。首次使用时在 admin-web、desktop 分别执行 npm ci。

```powershell
npm --prefix admin-web run check
npm --prefix admin-web run format:check
npm --prefix admin-web run check:mini
npm --prefix admin-web run test:ui
mvn -B package
npm --prefix admin-web run build
npm --prefix desktop run pack
```

根 Maven 项目只聚合现用 backend 模块。PC pack/dist 会自动先构建网页，再替换 desktop/web，避免历史 hash 文件不断累积。

## 本地开发与部署

运行 Start-Shengtai.ps1 启动本机 3306 数据库及开发服务。网页：http://127.0.0.1:5173 ，后端健康检查：http://127.0.0.1:8080/api/health 。

H2 依赖用于自动化测试和独立演示，需要保留；闲置的旧 H2 数据文件已移出项目。其他课程项目的 MySQL 数据库不属于本项目清理范围。

云端部署位置为 /opt/shengtai，当前使用阿里云 ECS 并通过 systemd 管理；迁移状态和公网限制见 [ECS 迁移说明](deploy/ECS迁移说明.md)。后端修改需重新打包部署，PC 界面修改需重新构建资源。小程序目录和图片由云端获取，小程序代码修改仍需通过开发者工具上传。

数据库升级类和 app_migration 记录负责兼容历史订单与价格。用户自选图片位于 backend/uploads，私有设置位于 .local，不能作为无用文件删除。

早期 Java 客户端、网页手机预览、旧 3307 初始化脚本和旧部署包已移出项目。清理清单与恢复包位于 Codex 工作区 outputs。
