# 圣泰台球 ECS 迁移说明

迁移时间：2026-11-13。当前主服务器为阿里云杭州 ECS `115.29.172.59`，旧深圳轻量服务器 `39.108.142.18` 仅暂时转发缓存中的旧地址请求。

## 已完成

- 将现有 `billiard_db` 数据导入 ECS 的 MySQL 8.4。源库与目标库的全部表数据导出 SHA-256 一致；目标库有 20 张表、28 张球桌、63 条开台订单、58 条服务记录、5 个用户。
- 将本地最新后端 JAR、前台网页构建产物和旧服务器的图片资源部署到 `/opt/shengtai`。后端只监听 `127.0.0.1:8080`，MySQL 只供本机使用。
- 配置 `shengtai.service`、Nginx 反向代理以及每天 04:15 的数据库备份定时器。备份在 `/opt/shengtai/backups`。
- 为 `www.yongzhoushengtai.homes` 配置 HTTPS。当前证书沿用旧服务器已签发的证书，有效期至 2027-02-02；**尚未在 ECS 上完成自动续期签发**。
- 阿里云权威 DNS 的 `www` 和根域 A 记录已指向 `115.29.172.59`。ECS 安全组已放行 80、443；请保持 8080 和 3306 不对公网开放。
- 旧服务器的业务后端已停止并禁用；Caddy 暂时将旧 IP 上的请求转发到新 ECS，避免 DNS 缓存期内两份数据库同时写入。旧数据库与旧备份保留作回退，暂勿释放轻量服务器。

## 验证结果和当前阻碍

- ECS 内部 `https://www.yongzhoushengtai.homes/api/tables`（解析到本机）返回 200；从旧云服务器访问新 ECS 的公网 443 也返回 200。新 Nginx 日志收到微信开发者工具的 `/api/tables` 请求，返回 200。
- 当前电脑使用 TLS 1.3 请求公网域名返回 200；TLS 1.2 握手被重置。公网 HTTP 返回阿里云 `Non-compliance ICP Filing`、403 页面。**因此尚不能认定手机小程序可用**；这不是更换小程序 API 地址可以修复的问题。
- 需要在阿里云备案控制台核查 `yongzhoushengtai.homes` 的网站ICP备案/接入状态及拦截原因。完成后，再从手机移动网络打开 `https://www.yongzhoushengtai.homes/api/health`，确认返回 `status: UP`，并真机验证小程序。
- 域名公网放通后，在 ECS 上用 Certbot 为 `www.yongzhoushengtai.homes` 签发证书并验证自动续期；现有证书不会因为复制到 ECS 而自动续期。
- 微信官方接口对当前 `WECHAT_SECRET` 返回 `40125`。现有小程序界面使用会员账号密码或演示登录，不调用 `/api/auth/wechat`；若启用微信授权登录，需要在微信公众平台取得有效 AppSecret，更新 `/etc/shengtai/backend.env` 后重启 `shengtai`。
- 新机保留了旧数据库中的管理员密码哈希。旧环境变量及本机旧部署记录中的管理员密码均未通过登录校验，说明当前管理员密码与这两处记录不一致；未擅自重置。需使用现行密码验证管理端，或在备份后明确执行重置。
- 宝塔面板端口 8888 当前公网可达。若保留面板，建议在安全组中把该端口来源限制为管理员 IP。

## 服务操作

```bash
systemctl status shengtai mysql nginx shengtai-backup.timer
curl -fsS http://127.0.0.1:8080/api/health
curl -ksS --resolve www.yongzhoushengtai.homes:443:127.0.0.1 \
  https://www.yongzhoushengtai.homes/api/tables
systemctl start shengtai-backup.service
```

服务配置在 `/etc/systemd/system/shengtai.service`、`/etc/nginx/sites-available/yongzhoushengtai.homes` 和 `/etc/shengtai/backend.env`。数据库及管理员凭据不写入仓库；本机忽略目录 `.local/cloud-deploy/cloud-secrets.json` 有原有配置。登录新 ECS 已配置本机 SSH 密钥；不要把私钥、服务器密码或微信 AppSecret 提交到仓库。

项目中的部署模板：`deploy/cloud-scripts/ecs-setup-db.py`、`ecs-shengtai.service`、`ecs-nginx.conf`、`ecs-backup.sh`、`ecs-backup.service` 和 `ecs-backup.timer`。

小程序 `miniprogram/app.js` 和 PC 客户端仍使用 `https://www.yongzhoushengtai.homes/api`。域名已切换到新 ECS，代码无需改为 IP。正式版小程序还需在微信后台配置该 HTTPS request 合法域名，并按既有发布流程上传和审核。
