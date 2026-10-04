# BedrockAPI —— 命名规范与使用速查卡

> 依据：Bedrock 1.20.0 客户端 `libminecraftpe.so`（NDK r25c / ELF64 AArch64）逆向成果。
> 参考目录树：`/workspace/MinecraftBedrockEdition__File__/`（5088 个文件占位，与 `paths.txt` 一致）。

---

## 一、类名后缀表

| 后缀 | 含义 | 必须包含 | MCRe 示例 |
|---|---|---|---|
| `System` | ECS 系统 | `tick(SystemRegistry)`、`order()` | `ChunkWindowSystem`、`TeleportSystem` |
| `Component` | ECS 组件（纯数据） | 实现 `Component` | `ChunkWindowComponent`、`TeleportRequestComponent` |
| `Definition` | 组件定义/绑定 | `bindType()`、`initialize()`、`componentType()` | `ChunkWindowDefinition` |
| `Request` | 异步请求 | 继承 `Request`，实现 `tryAdvance(int)` | `TeleportRequest`、`ChunkWindowMoveRequest` |
| `Data` / `Description` | 纯数据结构 | 无副作用 | `ChunkWindowData`、`FarLandsBandDescription` |
| `Registry` | 注册表/管理器 | 增删查 + 生命周期 | `WindowRegistry`、`FarLandsBandRegistry` |
| `Storage` | 存储层 | 读写持久化 | `FarLandsDataLayerStorage` |
| `Source` | 数据来源（可组合） | 只读产出 | `ChunkSource`、`NoiseSource` |
| `Factory` | 工厂 | `createXxx()` | `ChunkSectionFactory` |
| `Util` | 工具类（静态方法） | 私有方法 `_` 前缀 | `ChunkWindowUtil` |
| `Template` | 命令/字符串模板 | 继承 `CmdTemplate` | `TeleportCmdTemplate`、`RepositionCmdTemplate` |
| `Manager` | 生命周期管理（**谨慎用**） | 优先选 `System`/`Registry` | `FarLandsConfigManager` |

---

## 二、方法命名表

| 场景 | 命名 | 说明 |
|---|---|---|
| 取值 | `getXxx()` | 必然成功 |
| 取值（可失败） | `tryGetXxx()` | 返回 `Optional` / `@Nullable` |
| 创建 | `createXxx()` | 新建实例 |
| 判定 | `isXxx()` / `hasXxx()` | 布尔 |
| 序列化 | `serialize()` / `deserialize()` / `toString()` | — |
| 测试专用 | `testOnly_xxx()` | 生产代码不得调用 |
| 私有/内部 | `_xxx()` | 前缀下划线 |
| 每 Tick 推进 | `tick(...)` | 仅 `System` 类 |

---

## 三、包结构（= Bedrock 目录层级）

```
net.MinecraftTools.BedrockAPI
├── api/        模块边界接口（只放 interface，对应 Bedrock 的 11 个 Vanilla*）
├── command/    命令模板族
├── request/    异步请求 + 注册表
├── system/     ECS 三元（TickingSystem / Component / ComponentDefinition）
└── toggle/     编译期特性开关
```

**子包用小写**（对齐 Bedrock 目录风格，并规避 `system.System` vs `java.lang.System` 冲突）。

---

## 四、三条硬规矩

### 1. 禁止手拼命令字符串

```java
// ✅
String cmd = new TeleportCmdTemplate(CmdTarget.SELF, "~", "~", "~", 90f, 0f).toString();

// ❌ 相对记号 ~ ^ 和维度后缀会被破坏
String bad = "/tp " + target + " " + x + " " + y + " " + z;
```

### 2. 热路径用编译期常量

```java
// ✅ javac 常量折叠，死分支被彻底消除
if (FeatureToggles.ENABLE_MULTIDRAW_INDIRECT) { renderIndirect(); }

// ⚠️ 运行期查询，不支持常量折叠 —— 只用于冷路径/初始化/调试
if (FeatureToggles.isEnabled(Feature.MULTIDRAW_INDIRECT)) { ... }
```

### 3. 跨维度/跨区块一律走异步请求

```java
// ✅ 请求方只登记意图
registry.requests().add(new TeleportRequest(target, pos, dimension, 200));

// ❌ Bedrock 符号表里找不到任何同步跨维度传送
player.teleportTo(pos);  // 目标区块可能还没加载
```

---

## 五、自测约定

每个类带 `main()` 自测（沿用项目 `Math/_256Bit` 的风格），可直接：

```bash
javac -d out -encoding UTF-8 $(find src/main/java/net/MinecraftTools/BedrockAPI -name "*.java")

java -cp out net.MinecraftTools.BedrockAPI.toggle.FeatureToggles
java -cp out net.MinecraftTools.BedrockAPI.command.TeleportCmdTemplate
java -cp out net.MinecraftTools.BedrockAPI.request.RequestRegistry
java -cp out net.MinecraftTools.BedrockAPI.system.SystemRegistry
```

---

## 六、已落地清单

| 文件 | 对应 Bedrock | 状态 |
|---|---|---|
| `toggle/Feature.java` | `FeatureToggles` 枚举项 | ✅ 17 个特性 |
| `toggle/FeatureToggles.java` | `FeatureToggles::isEnabled` | ✅ 双轨（编译期常量 + 运行期覆盖） |
| `command/CmdTarget.java` | `CmdTarget` + `TemplateUtil::toString` | ✅ 含 7 个预置选择器 |
| `command/CmdTemplate.java` | `CmdTemplate` + `operator std::string()` | ✅ 延迟拼装 + 坐标校验 |
| `command/TeleportCmdTemplate.java` | `TeleportCmdTemplate`（6 重载） | ✅ 12 个重载 + 2 个工厂 |
| `command/RepositionCmdTemplate.java` | MCRe 专有 | ✅ 平移/缩放/复位/查询 |
| `system/Component.java` | `*Component` | ✅ 纯数据标记 |
| `system/ComponentDefinition.java` | `*Definition`（`bindType`/`initialize`） | ✅ 泛型定义接口 |
| `system/TickingSystem.java` | `ITickingSystem`（`tick(EntityRegistry&)`） | ✅ 含 `order()` 排序 |
| `system/SystemRegistry.java` | `EntityRegistry` + `EntitySystemsCollection` | ✅ 冻结保护 + 死组件回收 |
| `request/RequestState.java` | `*RequestComponent` 生命周期 | ✅ 5 态状态机 |
| `request/Request.java` | `*RequestComponent` | ✅ 超时 + 异常转 FAILED |
| `request/RequestRegistry.java` | `RequestPlayerChangeDimension` / `TryHandle...` | ✅ 跨线程投递 + 批量取消 |
| `request/DelayRequest.java` | `DelayRequest` / `DelayRequestQueue` | ✅ 带到期回调 |
