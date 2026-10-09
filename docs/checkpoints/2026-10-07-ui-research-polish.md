# 2026-10-07 小红书调研与界面优化检查点

## 范围与状态

用户授权继续小红书多帖及评论调研、资深 UI 优化和模拟器体验。已阅读8篇帖子，报告保留来源和偏差；主 Agent 整合，Astra 审查行程与最终截图，独立子 Agent 完成账本/页面标题。最新构建0.5.2-preview/13；无提交、无 Release、无部署，已有未提交改动保留。

## 实现入口

- MainActivity + UiControls + ic_nav_*：统一导航、选中语义、按宽度重排按钮、输入错误定位、字段标题关联。
- ItineraryTimelineUi：中文紧凑日期、地点卡层级、详情入口，锁定和标签保留，只有 tagNames 的旧数据也显示。
- FinanceUi + PageUi：操作间距、金额独立完整展示、可滚动分摊明细、图表字号和标题适配。
- tests/android：新增 CompactTimelineUiTest(15)、FinancePolishUiTest(66)、ControlsPolishUiTest(20)，-UiPolishOnly 合计101。StageThreeTimelineTest 对齐新的日期与编辑入口。
- 两个旧测试在本次运行出现动画/无障碍节点更新时序失败：WebDavSettingsUiTest 点击改为重取可用节点、最多等5秒；PlannerUiTests 返回上一步后清缓存重取字段，最多等5秒，仍必须验证“苏州”值不丢失。没有修改对应生产业务逻辑。

## 验证记录

最终APK目录：`../../work/build-ui-research-final-1007`。版本13 / 0.5.2-preview；132147650字节；SHA256 `262a9521c5695472563ec2a773b299ec0f8e8769c378f2db8da80ee69f8c4b76`。发布证书一致，扫描7个已知本机凭据值命中0文件。

- 正常界面专项101通过：`ui-polish-final-focused-1007.log`。
- 720×1280/density320/font1.3专项101通过：`ui-polish-final-large-1007.log`。
- 主题稳定后专项101通过：`ui-polish-final-stable-1007.log`。之前深色行程截图截在系统栏变色动画中；等待600ms重拍后状态栏/手势条白色，源码不需改。
- 中间构建全回归551通过，但不是最终APK验收依据。
- 最终APK前两次全回归分别停在旧 WebDAV 点击和快速规划字段节点；失败日志保留，不覆盖。最终重测556项通过（`ui-polish-final-full-stable-1007.log`，退出0）。

截图：`../../work/ui-polish-final-stable-1007`（正常主题稳定）及`ui-polish-final-large-1007`（小屏大字）。模拟器专用emulator-5580，已恢复1080×2400/density420/font1.0；未触碰真机和emulator-5584。

- 最终旅行体验专项61项通过：`ui-polish-final-travel-draft-1007.log`，退出0，覆盖草稿、地点选择、预约保护和 AA。

## 交付与边界

桌面：`E:/Users/11145/Desktop/麦穗旅序/测试版-界面体验优化-20261007/`，含APK、SHA256、两份报告与真实界面截图。最终验证已完成，报告和本检查点已同步。

未重测真实在线地图、AI、GPS、热力、真机相机/下载。候选地点池、今天页面、资料夹、免安装协作只是后续建议，不能当作已实现。
