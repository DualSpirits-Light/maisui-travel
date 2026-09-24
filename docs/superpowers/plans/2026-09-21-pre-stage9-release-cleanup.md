# 第 9 阶段前的发布清理检查点

日期：2026-09-21

用户要求先暂停第 9 阶段，并删除旧 GitHub Release，正式版再发布。

- 仓库：`DualSpirits-Light/maisui-travel`。
- 已通过授权 GitHub API 删除全部 7 个 Release：`v0.2.1`、`v0.2.2`、`v0.2.3`、`v0.2.4`、`v0.2.5`、`v0.3.1`、`v0.4.0`。
- 删除后分别通过授权和公开 API 复查，Release 数量为 0。没有新建 Release、提交代码或部署服务。
- 7 个 Git 标签仍存在；标签和提交历史不因删除 Release 而自动删除。没有删除标签，因为用户明确要求的是 Release。
- 此前 Release 或 APK 中已公开过的 Key 应在对应服务控制台撤销或轮换；删除页面及附件不能使旧 Key 失效。预发布更新源目前可能指向已删除的附件，正式版发布前需在第 9/10 阶段修复并复核。
- 小红书预告素材位于 `marketing/xiaohongshu/teaser-2026-09-21/`，包含四张 1080×1440 PNG、`post.txt` 和可直接取用的 ZIP。文案仅描述已有功能，明确标注 Android 开发中、暂未开放下载。
