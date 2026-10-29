# CMHR-Box

基于 [v2rayNG](https://github.com/2dust/v2rayNG)（Xray 内核）的 Android 代理客户端，界面使用 [telegram-11-ui](https://github.com/cmhr0086/telegram-11-ui)（tgui）。
包名 `com.cmhr.box`，与 1.x（CCHR-Box，`com.cchr.box`）并存，不做覆盖升级。

## 结构

```
upstream/v2rayNG/         子模块，锁定上游 tag，不直接修改
upstream/telegram-11-ui/  子模块，tgui 界面库
patches/                  对 v2rayNG 的补丁（应用到 .patched/ 副本）
scripts/                  apply-patches.sh、build-native.sh、check-boundaries.sh
buildSrc/                 接口地址配置（EndpointConfiguration）
engine-api/               代理引擎接口，不依赖任何上游
engine-v2rayng/           适配器，唯一允许 import com.v2ray.ang.* 的模块
cmhr-core/                邀请码、订阅、用量、公告、应用控制
app/                      壳：Application、tgui 页面、依赖注入
```

依赖方向：`app → cmhr-core → engine-api`，`engine-v2rayng → engine-api, :v2rayng`。
`app` 只在 `AppGraph` 里引用 `engine-v2rayng`；`app`、`cmhr-core` 不得 import `com.v2ray.ang.*`（`scripts/check-boundaries.sh` 检查）。

## 构建

需要 JDK 21、Android SDK（platform 37、build-tools 37）、NDK `30.0.16248370`（编译 hev）和 `28.2.13676358`（tgui 的 rlottie）、CMake 3.22.1。

```bash
git submodule update --init
scripts/build-native.sh      # 下载与子模块 gitlink 对应的 libv2ray.aar，编译 hev-socks5-tunnel 到 native/
scripts/apply-patches.sh     # 复制 V2rayNG/app 到 .patched/ 并按编号打补丁，复制原生库
./gradlew :app:assembleDebug
```

`local.properties` 至少需要 `sdk.dir=...`。

## 服务地址

仓库不包含任何服务地址，开源构建中以下功能全部停用。自行部署时按需配置：

| 键 | 作用 |
|---|---|
| `CMHR_SUBSCRIPTION_ENDPOINT` | `POST {inviteCode, versionCode}` → `{subscriptionUrl}`，失败时非 2xx + `{message}` |
| `CMHR_ANNOUNCEMENT_ENDPOINT` | `GET` → `{enabled, title, content, updatedAt}` |
| `CMHR_APP_CONTROL_ENDPOINT` | `GET` → `{serviceEnabled, minVersionCode, updateUrl, updateMessage, disabledMessage}` |
| `CMHR_DEFAULT_UPDATE_URL` | 应用控制没有给出有效 https 更新链接时使用的备用链接 |

读取顺序：Gradle `-P` → 同名环境变量 → `local.properties`（已被 Git 忽略）→ 空。非空值必须是不带用户凭据和片段的 https 地址；任一为空只停用对应功能。

## 升级 v2rayNG

1. `git -C upstream/v2rayNG fetch --tags && git -C upstream/v2rayNG checkout <新 tag>`
2. 对照新 tag 的 `V2rayNG/gradle.properties` 更新根目录 `gradle.properties` 中复制过来的那一段。
3. `scripts/build-native.sh && scripts/apply-patches.sh`，补丁打不上时脚本会直接失败（不允许模糊匹配）。
4. `./gradlew :v2rayng:testPlaystoreDebugUnitTest :cmhr-core:testDebugUnitTest :app:assembleDebug`
5. 提交子模块指针。

## 许可证

本项目以 GPL-3.0 发布，见 [LICENSE](LICENSE)。
v2rayNG 为 GPL-3.0；telegram-11-ui 源自 Telegram for Android，为 GPL-2.0 或更高版本。各上游的版权与许可声明保留在各自子模块中。
