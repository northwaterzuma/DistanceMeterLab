# AI测距实验室（DistanceMeterLab）

这是一个 Android APK 原型工程，目标是整合“测距仪 / AR 标尺 / ARPlan / Smart Measure”类工具的核心体验。当前版本采用 Android WebView + 本地 HTML/JavaScript 实现，不依赖第三方 SDK，方便后续接入 ARCore、ML Kit 或自训练计数模型。

## 已包含功能

- 相机预览与自动对焦能力
- 距离估算：输入镜头高度后，对准地面底部点取点
- 高度估算：底部取点后，再对准顶部点取点
- AR 标尺原型：基于估算距离，在画面中点选起点和终点估算长度
- AI 计数原型：本地视觉启发式计数，适合高对比、重复细长目标；不是商用品质 AI 模型
- 水平仪 / 挂画校准：读取横滚角，判断是否挂歪
- 量角器：相对角度读取与归零
- 屏幕直尺：基于 PPI 的临时屏幕尺
- 拍照分享：将测量结果叠加到图片中，通过系统分享面板分享
- 参数设置：镜头高度、相机视角、角度修正、水平校准、角度反向

## 精度说明

当前版本不是 ARCore 真三维平面追踪，而是“相机 + 姿态传感器 + 三角函数”的估算方案。精度取决于：

1. 手机姿态传感器质量；
2. 镜头高度是否填写准确；
3. 手机是否稳定；
4. 目标底部是否与手机处于同一地面平面；
5. 室内近距离通常好于户外远距离。

若要达到商用 AR 标尺精度，建议后续接入 ARCore Depth / Plane API。

## 构建 APK

### GitHub Actions

仓库已内置 `Build Android APK` 工作流。推送到 `main` 分支后会自动构建，也可以在 GitHub 的 Actions 页面手动运行。

构建成功后，在该次 workflow run 的 Artifacts 中下载 `DistanceMeterLab-debug-apk`。

### Android Studio

1. 用 Android Studio 打开本目录。
2. 等待 Gradle 同步。
3. 点击 `Build > Build Bundle(s) / APK(s) > Build APK(s)`。
4. 生成文件通常位于 `app/build/outputs/apk/debug/app-debug.apk`。

### 命令行

本机有 Android SDK 和 Gradle 时执行：

```bash
gradle assembleDebug
```

或生成 Gradle Wrapper 后执行：

```bash
./gradlew assembleDebug
```

## 后续升级路线

- 接入 ARCore：真实平面检测、点云、深度、房间扫描、面积 / 体积 / 门窗尺寸。
- 接入 AI 计数：针对串串、钢筋等数据采集标注，训练 YOLO / RT-DETR / MediaPipe Tasks Lite 模型。
- 加入项目保存：房间、墙面、测量记录、图片导出、PDF。
- 加入订阅 / 广告：免费版基础功能，高级导出、无限项目、无广告订阅。
