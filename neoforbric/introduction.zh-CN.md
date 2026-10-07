# Forbric —— 架构与内部实现

[English](introduction.md) | 简体中文

写给 mod 开发者和加载器开发者。本文求精确，不求浅显：按 Forbric 实际执行的顺序讲清它做了什么，并直接写出真实的类型名和文件名。本文描述的是 **`main` 分支**，而不是某个发布版；想了解发布版包含什么、玩家如何安装，请读 [README](README.zh-CN.md)。

> **本文描述的是哪份代码。** 仓库里有两代实现。`forbric-kernel/`，即*自主内核*，`main` 分支交付的、`forbric-kernel-installer/` 安装的都是它，它也是本文的主题。`forbric-loader/` 是第一代（*焊接方案*：以真正的 fabric-loader/Knot 为宿主，同时驱动 FML 和 FancyModLoader）。它在安装好的实例里已不再运行，但内核构建和测试所依据的工具与暂存产物仍由它构建，见 [§14](#14-forbric-loader-还有什么用)。

术语：

| 术语 | 在本文中的含义 |
| --- | --- |
| **生态（ecosystem）** | Fabric、传统 MinecraftForge、NeoForge，对应 `net.forbric.api.Ecosystem.FABRIC` / `FORGE` / `NEOFORGE` |
| **Forge 系（Forge family）** | MinecraftForge 与 NeoForge 的合称。两套运行时、两种清单（`META-INF/mods.toml`、`META-INF/neoforge.mods.toml`）、两条事件总线 |
| **合并基底（merged base）** | `patched-mc-merged-26.2.jar`：带有两个 Forge 系补丁的 Minecraft 26.2，按字节合并成一个 jar |
| **载体（carrier）** | 某一个 Forge 系的运行时 jar（`neoforge-runtime.jar`、`forge-runtime-interop.jar`），作为被动的 ABI 提供者加载：它的类存在，但它的加载器生命周期从不运行 |
| **引导侧 / 游戏侧（boot side / game side）** | 前者指由系统类加载器加载的代码，后者指由 `ForbricClassLoader` 定义的代码 |
| **第三方（guest）** | 凡是属于第三方 mod 的东西（第三方 mixin、第三方 jar） |

---

## 1. 要解决的问题

三个加载器都默认整个进程归自己管。把它们的 mod 放在一起跑，会以五种相互独立的方式出问题；内核的每个部分都在应对其中一种。

1. **启动权。** Fabric 启动 Knot；MinecraftForge 和 NeoForge 启动 ModLauncher/BootstrapLauncher，外加一个 JPMS 模块层。两个转换型类加载器，意味着每个游戏类都有两份定义。
2. **一个类，三套补丁。** 两个 Forge 系都会给*同一批* `net.minecraft` 类打补丁，插入的钩子各不相同，而一个 JVM 里每个类只能有一个版本。总得有某种机制逐类、逐方法地决定保留谁的方法体，然后还得应付落败方那边的 mod 原本的预期。
3. **生命周期与注册窗口。** 每个生态都有自己的阶段、自己的总线、自己那段允许写注册表的窗口，对冻结何时发生也各有各的看法。
4. **可见性。** 每个加载器都维护自己的 mod 列表。一个 mod 问自己的加载器“装了 Sodium 吗？”或“我在哪个平台上？”，得到的答案在单加载器实例里成立，放到这里就错了。
5. **为另一个游戏写的第三方字节码。** Fabric mixin 是对着原版字节码写的；MinecraftForge mod 是对着 MinecraftForge 打过补丁的游戏写的。到了合并基底上，锚点挪了位置，方法被拆开，字段改了类型，lambda 重新编号，父类也换了。

在 26.2 上，命名空间*不在*上面这几条之列：游戏发布时用的就是 Mojmap 名称，26.2 的 Forge 系 mod 按 Mojmap 编译，Fabric 的 mod 也一样（`KernelMappingResolver` 的 javadoc 记录了一次对 fabric-api 和 Jade 的常量池扫描，没有发现任何 intermediary 符号）。内核采用恒等映射：`TransformContext(…, "named")`，`KernelMappingResolver` 对每次查询都原样返回输入。

mod API 同样不在其列。Forbric 不重新实现 Fabric API、MinecraftForge API 或 NeoForge API：mod 调用的是装进来的那个真正的 Fabric API mod，以及载体里真正的 MinecraftForge/NeoForge 类。内核掌管的是原本由加载器掌管的部分：类加载、发现、生命周期、注册窗口，以及 Fabric Loader 自己的 API（§5.1）；此外还有合并带来的、不得不做的修复。其中有两类修复是复现原有行为，而不是调用它：一是 NeoForge 的 coremod 改写，由内核自己执行（§5.2）；二是钩子在合并中落败的事件，由内核重新发出（§8）。

## 2. 方案总览

一个 JVM，一个转换型类加载器，一个生命周期，一次注册表冻结。任何真正的加载器生命周期都不会启动：没有 Knot，没有 ModLauncher，没有 FancyModLoader 的发现流程，也没有模块层。

```
system class loader  (BOOT side)
 ├─ forbric-kernel.jar            net.forbric.kernel.{boot,classloading,discovery,fabric,transform,mixin,
 │                                 access,metadata,mapping,interop,ui,util,soak}, net.forbric.api,
 │                                 vendored net.fabricmc.api / net.fabricmc.loader surface
 └─ its dependencies               ASM, sponge-mixin, SAT4J, NightConfig, tiny-remapper, class-tweaker, mapping-io
     │  new ForbricClassLoader(owned, parent)
     ▼
ForbricClassLoader  (GAME side — the only loader that defines game/ecosystem classes)
 owned jars, in this order (KernelOwnedClasspath.compose):
   1. patched-mc-merged-26.2.jar           --gameJar
   2. forge-runtime-interop.jar,            --runtimeJar   (the two carriers)
      neoforge-runtime.jar
   3. Minecraft's own libraries             --libraryPath  (owned: mods mixin into DataFixerUpper & co.)
   4. kernel-bundled: mixinextras-fabric.jar, forbric-kernel-runtime.jar   (extracted to .forbric-kernel/lib/)
   5. guest mod jars: Forge-family, then Fabric, nested jars included; newest version first within one mod id
```

### 2.1 引导侧与游戏侧

`forbric-kernel.jar` 由父加载器加载，不引用任何游戏类型。凡是引用 `net.minecraft.*`、`net.minecraftforge.*`、`net.neoforged.*` 或 `net.fabricmc.fabric.*` 的代码，都放在 `src/runtime/java` 里（`net.forbric.kernel.runtime` 包及其 `soak`/`transfer` 子包，共 130 个文件）：以 `compileOnly` 方式针对暂存的游戏产物编译，打包成 `forbric-kernel-runtime.jar`，内嵌在引导 jar 的 `META-INF/jars/` 下，启动时由 `KernelBundledJars` 解出，再作为托管 jar 加载。

引导侧通过*字符串*访问游戏侧（`Class.forName`、`getMethod`、ASM owner 名）。`KernelRuntimeClasses` 登记了所有这样的字符串；启动时，`KernelRuntimeClasses.verify(loader)` 会经由已经组装好的流水线把整条接缝加载一遍。这样一来，如果引导 jar 构建时缺了游戏侧那一半，或者游戏侧某个方法改了名，就会在日志最前面失败，而不是等到某个 mod 构造到一半才失败。游戏侧只有一个类 `net.forbric.kernel.runtime.KernelHudLayer` 仍由 `KernelHudBridge` 在运行时用 ASM `ClassWriter` 生成；游戏侧其余代码都是编译而来。

### 2.2 `ForbricClassLoader` 与委派表

`classloading.ForbricClassLoader` 是一个扁平的、不涉及 JPMS 的 `URLClassLoader`。`loadClass` 按以下顺序查询 `DelegationPolicy`：

- **ALWAYS_PARENT**：JDK、ASM（`org.objectweb.asm.`）、Mixin（`org.spongepowered.asm.`）、log4j/slf4j、NightConfig（某个载体自带一份未做 shade 的旧版本，否则它会按子优先规则胜出）、`net.fabricmc.api.`、`net.fabricmc.loader.api.`、`FabricLoaderInternals` 里列出的 Fabric Loader 内部类（按精确名称匹配）、`net.forbric.api.`，以及内核的引导包（`boot`、`classloading`、`transform`、`mixin`、`access`、`mapping`、`metadata`、`discovery`、`fabric`、`util`、`interop`）。整个 JVM 恰好只有一份。
- **ALWAYS_GAME**：`net.minecraft.`、`com.mojang.blaze3d.`、`net.minecraftforge.`、`net.neoforged.`、`net.fabricmc.fabric.`、`net.forbric.kernel.runtime.`、`com.llamalad7.mixinextras.` 以及 Mixin 的合成包。要么在这里定义，要么根本不定义。
- **其余情况走子优先**：哪个托管 jar 里有这个类就在这里定义，否则交给父加载器。

`tryDefineGameClass` 读取字节，先跑 Mixin 前的转换链（`setTransformer`），再跑 Mixin 阶段（`setMixinTransformer`），最后带着一个真实的 `ProtectionDomain` 调用 `defineClass`（以该 jar 作为代码来源，JourneyMap、spark 等 mod 就是靠它找到自己的 jar）。这个加载器的其他职责：

- **Mixin 前的字节。** `getPreMixinClassBytes` 给 Mixin 提供走完转换链、尚未织入的字节；如果给的是织入后的字节，织入器就会把自己的输出再织入一遍。
- **生成的类。** `putGeneratedClass` 存放由转换器合成的类（class-tweaker 枚举扩展）；如果读到 `null` 且没有对应的生成条目，Mixin 就知道该由自己来合成。
- **备援 jar。** `setRescueJars` 设定的是在跨 jar 仲裁中被取代的那些 jar；*只有*在没有任何托管 jar 含有该类时才会查它们，因此它们无法遮蔽胜出方（§4.3）。
- **jar 所属生态。** `setJarFamilies` 记录每个 mod jar 被仲裁归入了哪个生态；`familyOfClass` / `familyOfResource` 为环境剥离器和加载器探测改写器提供依据。
- **可重入的定义。** 在 Mixin 第一次 `select()` 期间构造的第三方配置插件，可能会去加载当前正在定义的类；`define` 会取回已经完成的那份定义，而不是因重复定义抛出 `LinkageError` 而失败。
- **包清单。** 定义包时会带上所属 jar 的清单属性，因为真正的 FML 会读取 `Package.getImplementationVersion()`。

### 2.3 三个游戏产物

它们都不在仓库里，也不在任何 Forbric 下载包里：每一个都嵌有 Mojang、MinecraftForge 或 NeoForge 的代码。它们在哪台机器上运行，就在哪台机器上构建：给玩家用的由安装器构建（§13），给开发者用的由 `forbric-loader/run/` 下的脚本构建（§14）。

| 产物 | 说明 |
| --- | --- |
| `patched-mc-merged-26.2.jar` | 原版 26.2 + 打过 MinecraftForge 补丁的 26.2 + 打过 NeoForge 补丁的 26.2，由 `net.forbric.tools.MergedBaseBuilder` 按字节合并。以 NeoForge 的类为基础，再把 Forge 的类拼接进来；已提交的报告 `forbric-loader/run/merged-base/merge-conflicts.txt` 记录的类数为 `forge=193 neo=10163 MERGED=612`，冲突为 `CONFLICTS: methods=1000 fields=8 STRUCTURAL(superclass/field)=15` |
| `neoforge-runtime.jar` | NeoForge 的 `-universal` jar 加上它的 userdev 配置声明的库，合并成一个 jar（`NeoForgeRuntimeBuilder`） |
| `forge-runtime-interop.jar` | MinecraftForge 的 `-universal` jar 加上它的运行时库（`ForgeRuntimeBuilder`），再由 `net.forbric.tools.RuntimeInteropPatcher` 打补丁：合并时替 NeoForge 加宽了一些接口，而 Forge 自己编译好的实现已经满足不了它们。以坐标 `net.forbric:forge-runtime` 暂存 |

载体是*被动*的：它们的类会被定义，它们的事件总线也会被使用，但它们所属加载器的发现、排序和生命周期从不运行。内核只提供它们的代码会查询的身份信息（`PassiveSeeder`，§3.2）。

## 3. 启动顺序

### 3.1 入口

`boot.KernelClientLaunch.main` 和 `boot.KernelServerLaunch.main` 都只有一行：

```java
int code = CompatibilityLaunchBoundary.run(() -> KernelBoot.launch(KernelBoot.Side.CLIENT, args));
if (code != 0) System.exit(code);
```

兼容性拒绝只会在 `CompatibilityLaunchBoundary` 这一处变成进程退出（退出码 `78`，§12.4），安装损坏而被拒绝的启动也只在这里退出（退出码 `2`，§3.2 第 0 步）。其他任何离开引导过程的异常都会先在这里写进 `latest.log`（消息和堆栈），然后原样重新抛出，因为启动器展示的是这个文件，而不是 stderr。`KernelBoot.launch` 自己处理 `--gameJar`、`--runtimeJar`（可重复，一个值里也可以用路径分隔符连接多个 jar —— PCL2 这类启动器遇到重复的参数只保留最后一个）和 `--libraryPath`；其余参数，以及 `--` 之后的全部内容，都转给游戏的 `Main.main`。专用服务器不接受 `--gameDir`，所以 `KernelBoot` 在服务端会把它去掉。游戏版本从基底 jar 的 `version.json` 读取（读不到时回退为 `26.2`）。

### 3.2 `KernelBoot.launch` 的执行顺序

0. **启动输入** —— `LaunchInputCheck.require(gameJars, runtimeJars)`，按内容判断，并且在从这些 jar 里读取任何东西之前进行：基底的 `Block` 必须实现两个 Forge 系各自的扩展接口（即合并基底）；每个 `--runtimeJar` 必须完整携带一个 Forge 系（加载器 SPI、`ModContainer`、`FMLLoader`、`FMLEnvironment` 和它自己的 `mods.toml`；只有 `mods.toml` 的 jar 会被报告为该 Forge 系的一个 mod）；两个 Forge 系都必须由这次启动拥有的某个 jar 携带。不通过时，每个问题和修复办法（重新运行安装器，*Built artifacts* 留空）都写进日志，并以退出码 `2` 停止；`-Dforbric.launchInputCheck=off` 只发出警告。Issue #13：空的"运行时" jar 曾经在后面每一步都得到一个合法的空结果，最后在 `KernelRuntimeClasses.verify` 里死在 stderr 上，`latest.log` 里只有五行 INFO。这项检查只看条目名，不看每一个类；它留给后续步骤处理的情况列在该类的 javadoc 里。
1. **跨 jar 仲裁预扫描** —— `DuplicateModArbiter.arbitrate(mods/, envType)` 清点所有根候选和内嵌候选，在任一生态的发现开始之前先定下唯一的选择（§4.3）。
2. **载体版本** —— `EcosystemVersions.record(runtimeJars)`，这样一旦某个 mod 的 `versionRange` 载体满足不了，发现它的当下就能报出来。
3. **Forge 系发现** —— 所有带 Forge 系清单的 `mods/*.jar`，再加上仲裁方案选中的 JarJar 子 jar（`META-INF/jarjar/`），后者取自该方案在 `.forbric-kernel/candidates/` 下按内容寻址解压出的副本（旧的解压器写入 `.forbric-kernel/jarjar/`，只在仲裁关闭时运行）。
4. **在场信息** —— `ModPresence.publishForgeFamily(…)` 必须在构建 Fabric 侧之前调用，因为 Fabric 侧会把它读回去。
5. **Fabric 发现** —— `KernelFabricEcosystem.scan`，然后对所有内嵌 jar 的并集再做一轮仲裁（`DuplicateModArbiter.arbitrateNested`）；落败方从两份列表中都移除。
6. **Mixin 配置声明** —— Forge 系配置，来源包括 mod jar、内嵌 mod jar *以及载体*（NeoForge 自己的 `neoforge.mixins.json`）。
7. **构建 Fabric 生态** —— `KernelFabricEcosystem.build` 创建 `FabricLoader` 视图（§5.1）。
8. **托管 classpath** —— `KernelOwnedClasspath.compose`（顺序见 §2）。
9. **静态审计** —— 在加载任何东西之前扫一遍所有第三方 jar：`PortingLayerAudit`（自带 `net.neoforged.*`/`net.minecraftforge.*` 的 Fabric jar）、`FabricApiModuleLossAudit`、`FieldDriftAudit`、`MergedBaseUncalledMethods.scanGuests`、`AbiLinkAudit`（jar 里引用了、却在任何载体、基底或已安装的 jar 中都不存在的 Forge 系类）。
10. **类加载器** —— `new ForbricClassLoader(owned, bootLoader)`、备援 jar、jar 家族、`LoaderProbePolicy.bindGuestLoader`、`KernelFabricLauncher.install`（mod 的 `addToClassPath` 最终落到这里）。
11. **转换链** —— 91 个 `chain.register(…)` 调用点（§6）。
12. `loader.setTransformer((name, bytes) -> chain.applyBeforeMixin(name, bytes, ctx))`；上下文类加载器换成游戏类加载器；绑定 `KernelLifecycle`、`KernelHudBridge`、`LootTableEventDispatch`；`KernelLoadReport` 和 `CrashAttribution` 拿到运行目录（以及各自的关闭钩子）。
13. **加载器身份，先于 Mixin** —— `PassiveSeeder.seedNeoForgePaths`、`seedNeoForgeLoader`、`seedForgeFmlLoader`、`publishForgeLoadingList`。这一步必须早于第一个经过 Mixin 转换器的类，因为 Mixin 会在那一刻构造所有第三方 `IMixinConfigPlugin`，而这些配置插件会在 `<clinit>` 里读取 `FMLPaths`/`FMLLoader`。MinecraftForge 的 `FMLEnvironment` 在一次性的 `<clinit>` 里把 `dist` 缓存进 `static final` 字段；谁先碰到它，它的值就永远由谁决定。
14. **Mixin** —— 先放 Fabric 配置，再追加 Forge 系配置（§7.1）；注册之前先调用 `MixinConfigOwners.publish`；`KernelMixinBootstrap.init`。
15. `PassiveSeeder.reportDependencies()` —— 放在 Mixin 之后，因为它要报告的内容有一半（写给另一个 mod、却没有挂上的 mixin）是在 Mixin 解析配置时才记录下来的。
16. `KernelRuntimeClasses.verify(loader)` —— 让内核自己的游戏侧过一遍已经搭好的流水线。
17. `PassiveSeeder.seedAll` —— NeoForge 的 `FMLLoader`、`ModList`、路径；MinecraftForge 身份（幂等）。
18. 审计报告（其中有 `MixinOverlapLint`，§7.6）、`KernelLoadReport.writeEvidence()`，然后是 `CompatibilityDecision.requireContinuation(isClient)` —— 进入游戏之前的决策点（§12.4）。
19. `KernelFabricEcosystem.runPreLaunch()` —— Fabric `preLaunch` 入口点，在 Mixin 之后、任何游戏类之前运行。
20. 加载入口类（`net.minecraft.server.dedicated.DedicatedServer` / `…client.gui.screens.TitleScreen` 是普查标志点；实际调用的是游戏的 `Main`）。如果 `LifecycleHookInjector` 没找到它的触发点，**内核拒绝启动**（`missedRequiredExcision()`）。
21. `Main.main(gameArgs)` —— 原版启动流程，其中真正的加载器触发点已被重定向。

### 3.3 被重定向的触发点

两个入口点在合并中都由 NeoForge 胜出。`transform.LifecycleHookInjector` 在每个入口点里重定向一条 `invokestatic`（改 owner 和 name，描述符不变）：

| 侧 | 合并基底中真正的调用 | 现在调用 |
| --- | --- | --- |
| 服务端 | `net.neoforged.neoforge.server.loading.ServerModLoader.load(Z)V`，位于 `net.minecraft.server.Main.main`，在 `Bootstrap.bootStrap()` 之后 | `KernelLifecycle.onServerModLoading(boolean)`，随后是 Fabric 的 `Hooks.startServer(null, null)` 标记（`-Dforbric.fabricHooks=off` 会省掉它） |
| 客户端 | `net.neoforged.neoforge.client.loading.ClientModLoader.begin()V`，位于 `net.minecraft.client.main.Main.main`，在 `Bootstrap.validate()` 之后、`new Minecraft` 之前 | `KernelLifecycle.onClientModLoading()` |

客户端稍后对两个 Forge 系各自 `ClientModLoader` 的调用（`finish`、`completeModLoading`）由 `MethodBodyNeuter` 替换成存根；`setupModResourcePacks` 则改为重定向到 `KernelLifecycle.onClientResourcePacks`（§9.2）。

在客户端，同一个注入器还让 `Main.logEarlyException` 先调用 `KernelLifecycle.onEarlyStartupFailure`。这是原版给 `Main.main` 开头三步（检测版本、构造参数解析器、解析参数）准备的处理器：它只打印到 stderr，随后 `main` 直接退出（249、252、251）而不抛出异常，所以没有这个钩子时，结束游戏的那个错误永远到不了 `latest.log`。

### 3.4 原生注册窗口 —— `KernelLifecycle.driveNativeRegistration`

两侧走的是同一套步骤（源码注释里给它们编了号）：

- **0** 预置 MinecraftForge 的 `LoadingModList`；接上 Forge 的 `LogicalSidedProvider` 执行器；在客户端加载每个载体的内置翻译（`CarrierLanguages`）。
- **1** 注册 NeoForge 的基线注册表（`PassiveSeeder.seedNeoForgeRegistries`）；应用 NeoForge 自己的注册表修改（同步标志、回调）。
- **2** `registerNeoForgeContent` —— 注册窗口本身：
  1. 在内核创建的总线和容器上构造 `NeoForgeMod`（`KernelModContainerFactory`）；
  2. 构造每一个 Forge 系 `@Mod`（`KernelModLoader.constructMods`，§5.2/5.3）；
  3. 注册第三方 `@EventBusSubscriber` 类（`KernelEventSubscribers.registerAll`）；
  4. 向两个 Forge 系发布 `FMLConstructModEvent`；注入 MinecraftForge 的 capability；
  5. NeoForge `GameData.vanillaSnapshot`，然后**解冻**；发布 `NewRegistryEvent`；
  6. 按 NeoForge 的注册顺序，在每条总线上为每个注册表触发 `RegisterEvent`；然后是 MinecraftForge 基线（`KernelForgeBaseline.register`）；
  7. 打开 `minecraft:root`，运行 Fabric `main` 入口点（在客户端，只有 `-Dforbric.fabricMainInConstructor=off` 时才在这里运行；见 §3.5）；
  8. 属性事件、生成位置规则、`BlockEntityTypeAddBlocksEvent`、mod 添加的游戏规则分类、NeoForge 的提示框追加器；
  9. `closeRegistrationWindow`（在 `finally` 里）：建立方块→物品的关联，**冻结**，重建 NeoForge 的方块状态→id 映射，重新排序 NeoForge 的创造模式物品栏标签页。
- **2a–2c3** 校验 REGISTRATION 桥；在 `ModList` 中发布 NeoForge 基线；加载 STARTUP/COMMON 配置（客户端上还有 CLIENT）；接上两个载体自己的 `@EventBusSubscriber` 类；安装客户端重载监听器桥。
- **3a** 声明数据包注册表（`DataPackRegistryEvent.NewRegistry`，Fabric 动态注册表双向镜像）—— 在客户端会推迟到 Fabric 入口点之后（`DatapackRegistryDeclaration`）。
- **3a2** 启动游戏总线（`startGameBuses`）—— 必须在初始化阶段之前，因为在尚未启动的总线上调用 `IEventBus.post` 会静默返回。
- **3b**（服务端）向两个 Forge 系的第三方 mod 发布初始化生命周期（`fireModSetupLifecycle`）：通用初始化、专用服务器初始化、NeoForge 的 `RegistrationEvents.init()`（逐步执行，见 `RegistrationEventSteps` —— 它会发布 `RegisterCapabilitiesEvent` 和 `RegisterDataMapTypesEvent`）、IMC 入队/处理、加载完成；各阶段之间，延迟任务在每个 Forge 系各自使用的线程上运行（`NeoDeferredWork`；失败由 `DeferredWorkFailures` 读回）。随后写出 `load-report.txt`，并再次询问 `CompatibilityDecision.requireContinuation` —— 这是加载结束时的决策点。在客户端，这些阶段（包括通用初始化）全部推迟到 `onNeoClientSetup`（§3.5），因为此时 `Minecraft.getInstance()` 还是 null。
- **3b2** 打开晚注册的配置（在构造或初始化期间注册的）。
- **3c**（服务端）关闭 NeoForge 的负载注册阶段（`setupNeoForgeNetwork`）。这一步必须放在初始化之后：mod 会在 `FMLCommonSetupEvent` 里注册负载。

注册窗口只有一个，冻结也只有一次。焊接方案撞上的那堵“Tags not bound”墙（两个生态轮流重新冻结）在这里不可能出现。

### 3.5 `Minecraft.<init>` 里的客户端专用钩子

Fabric 和 NeoForge 在构造函数里需要的状态正好相反，所以内核设了两个锚点：

- `ClientEntrypointHookInjector` → `KernelLifecycle.onClientEntrypoints()`，在 `Options` 存在之前：重新打开注册表（以及 MinecraftForge 的注册表闸门），构造那些因为过早去拿 `Minecraft` 而被暂缓的 MinecraftForge mod，先运行 Fabric `main` 再运行 `client` 入口点（即 Fabric `Hooks.startClient` 的顺序），重新关闭并重新冻结，打开晚注册的 CLIENT 配置，然后声明数据包注册表。这次冻结前后就是 Fabric 的注册表冻结点：装了 fabric-registry-sync 时，Fabric mod 挂在 `BuiltInRegistries.freeze()` 头尾、或 `bootStrap()` 里调用 `freeze()` 前后的 `@Inject` 在这里运行（`FabricFreezeHookMixinAdapter`），也就是原生 Fabric 冻结的地方——入口点之后，`Minecraft.getInstance()` 已经有值。LiquidBounce 用这样的注入建它的创造模式标签页，留在 `Bootstrap` 里时拿不到客户端，直接崩溃。
- `NeoClientSetupHookInjector` 挂在合并基底调用 `ClientModLoader.finish()` 的位置 → `KernelLifecycle.onNeoClientSetup()`，在 `options` 赋值之后：校验 CLIENT_INIT 桥；预加载客户端资源管理器（MinecraftForge 在第一次资源重载内部运行 mod 加载，它的 mod 都指望在客户端初始化时就能读到自己的资源；`-Dforbric.clientResourcePreload=off`）；然后是 `fireClientSetupLifecycle`：通用初始化、客户端初始化、`RegistrationEvents.init()`、IMC、加载完成 —— 和真正的 NeoForge 一样，此时注册表处于冻结状态 —— 接着写出 `load-report.txt` 并做加载结束时的决策（`requireClientContinuation`），再处理晚注册的配置，最后关闭负载注册阶段。

## 4. 发现与仲裁

### 4.1 读取清单

- `discovery.ForbricModDiscoverer` 按描述文件给 jar 分类 —— `fabric.mod.json`、`META-INF/mods.toml`、`META-INF/neoforge.mods.toml` —— 并报告一个 jar 携带的*每一份*清单。最终加载哪一份由策略决定（§4.2）。
- `metadata.forge.ModsTomlParser`（净室实现；TOML 词法分析用 NightConfig），配合 `ForgeModsToml`、`ForgeModEntry`、`ForgeDependency`；`ForgeVersionRangeTranslator` 把 Maven 版本范围转成 Fabric 风格的谓词，所以 `net.forbric.api.UnifiedDependency` 只有一种写法（由 `VersionPredicate` 求值）。`UnifiedDependency` 还保留了 Fabric 里没有对应说法的两个维度：顺序（`BEFORE`/`AFTER`）和适用的侧。
- `fabric.FabricModMetadataParser` 是完整的 `fabric.mod.json` v1 读取器（入口点，包括适配器形式；`jars`；按侧区分的 `mixins`；`accessWidener`；`custom`）。`fabric.FabricModDiscovery` 会顺着 Fabric JiJ 继续发现（解压缓存在 `.forbric-kernel/jij/`）；`environment` 排除了当前侧的 mod 会被跳过，和 Fabric 上一样。
- 内嵌的 Fabric mod 按 fabric-loader 0.19.5 的 `ModSolver` 的规则决定加不加载（`fabric.NestedFabricRequirements`，由 `NestedCandidateInventory` 执行；没有选择计划时由 `FabricModDiscovery` 执行）：对 `minecraft` 或 `java` 的 `depends` 不包含当前版本，或者 `breaks` 包含当前版本，它就不加载；硬依赖只能由这些被排除的 mod 满足、或者只被它们内嵌的内嵌 mod 也一起不加载。实例是 ViaFabric：它在 `viafabric-mc26-2` 旁边还内嵌了 `viafabric-mc26-1`（`minecraft >=26.1 <=26.1.2`），原生只加载 `viafabric-mc26-2`。只判定 Fabric mod 在自己的 `jars` 里声明的 jar。NeoForge/MinecraftForge mod 内嵌的 jar 不判定，哪怕里面只有一个 `fabric.mod.json`，因为 Fabric Loader 从不打开没有 `fabric.mod.json` 的 jar。父 jar 的 `META-INF/jarjar/metadata.json` 声明过的子 jar 归 FML 管，FML 把它当成父 mod 的库加载；只是放在 `META-INF/jars/` 或 `META-INF/jarjar/` 里、在这个文件里没有条目的 jar，没有任何原生加载器会加载，内核保留它是因为内核对 Forge 系 mod 的遍历一直都会走这两个目录。两条路都能走到的 jar 保留，不管遍历先走了哪条。有一处和原生不同：内核要加载的某个 mod 硬依赖某个 id，而会加载的 mod 里没有一个满足这条要求时，被排除的副本中满足要求的那些会被留下，连同把它一路内嵌到某个已加载父 mod 的那些 jar，并打一行 WARN：`[Forbric/JiJ] nested <id> <version> in <parent> loaded although Fabric Loader would leave it out (<原因>): <依赖方> requires <id> <范围>, and nothing else installed meets that`。如果已安装的副本没有一个满足要求，而且会加载的 mod 里根本没有这个 id，就留下所有被排除的副本（规则加入之前内核本来就加载它们），WARN 的结尾换成 `<依赖方> requires <id> <范围>; no installed build meets that, and no other <id> would load`。依赖方在 `mods/` 里时原生会拒绝启动；内核在缺依赖时也照样加载这种 mod，所以把提供者排除掉只会让它少一个依赖。最终仍被排除的每个 mod 都打一行 `[Forbric/JiJ] nested <id> <version> in <parent> left out: <原因>`，它内嵌的 mod 也一样：被排除的 jar 仍会被打开，这样已加载的 mod 需要其中某个 jar 时才看得见。`mods/` 里的 jar 从不在这里判定；`fabricloader`/`mixinextras` 的范围、读不懂的范围、以及没有任何已安装 mod 能满足的依赖都不会让任何东西被排除。没有选择计划时（`-Dforbric.crossJarArbitration=off`），`FabricModDiscovery` 只走 `mods/` 和 Fabric 的 `jars`，所以 `KernelBoot.scanFabricMods` 把它读不到的 jar 的硬依赖交给它：Forge 系 mod 的，以及它们 JarJar 里那些 jar 的 Fabric 清单的（这些 jar 在这条路上不是 Fabric 容器，但在 classpath 上）。`-Dforbric.nestedRequirements=off` 恢复为全部加载；`off:<id>,<id>` 只放过这几个 id（内核不读 Fabric 的 `config/fabric_loader_dependencies.json`）。
- `discovery.ModAnnotationScanner` 按字节码描述符查找 `@Mod` 类；`ModFileScanner` 构建完整的 `ModFileScanData`（NeoForge 和 MinecraftForge 的形态不同：`EnumHolder` 与 `EnumData`），因为 JEI、Jade、Sophisticated Core 和 Sodium 都通过 `ModList.getAllScanData()` 找自己的插件。
- `net.forbric.api.DiscoveredMod` 是唯一的 mod 模型。

`--scan` 模式（`boot.Main --scan --mods <dir> --report out.json`）只运行发现，并写出确定性的 JSON；`run/diff-oracle.sh` 用一个独立的 Python 读取器解析同样的清单，与它交叉核对。

### 4.2 一个 jar，多份清单 —— `MultiLoaderArbiter`

通用 jar 为每个加载器各带一份清单和一个胶水类。如果不仲裁，三个生态都会初始化它。`MultiLoaderArbiter.ownerOf(jar)` 按偏好挑出一个 —— 默认是 **NeoForge、MinecraftForge、Fabric**（`-Dforbric.multiLoaderPreference=…`）。Forge 系排在前面，是因为它们的基线在合并基底上始终存在；NeoForge 排在 MinecraftForge 之前，是因为合并中大部分由 NeoForge 胜出。如果一个 jar 带着某个加载器残留的清单、却没有对应的实现，就不会交给那个加载器。jar 仍然留在 classpath 上；这里只决定它的身份。

### 4.3 两个 jar，同一个 mod id —— `DuplicateModArbiter`

把一个 Fabric 整合包和一个 NeoForge 整合包合在一起，同一个 id 下就会出现两个*文件*。落败方必须**移出** classpath（否则按“第一个 URL 胜出”的规则，它可能遮蔽胜出方，还会带进自己的 mixin 配置）。

- **清点** —— `NestedCandidateInventory` 遍历每一个根 jar 和内嵌 jar（深度 ≤ 8，有防 zip 炸弹的字节上限，不限归档数量），构建出一张由候选和父→子边组成的图；内嵌 jar 解压到 `.forbric-kernel/candidates/` 下，以 SHA-256 为键。
- **选择** —— `ReachableCandidateSelector` 为整个实例构建一个布尔模型（内嵌候选只能经由被选中的父 jar 存在），并用 SAT4J 求解；`JointCandidateSelector` 提供子句：硬依赖/软依赖、`breaks`/`incompatible` 互斥，以及来自 `CandidateContractScanner` 的符号契约（只采用强到足以约束选择的证据 —— 例如某个 mixin 必需的成员）。搜索按工作量设限，从不按时钟设限（`CONFLICT_BUDGET`、`VARIABLE_LIMIT`、`-Dforbric.arbitrationMaxNodes`，默认 100 000），所以同一个文件夹在任何机器上都会选出同样的 jar。
- **偏好** —— 顶层重复：`-Dforbric.dupeIdPreference`，未设置时回退到 `multiLoaderPreference`。内嵌重复：`-Dforbric.nestedDupePreference`，默认 NeoForge、Fabric、MinecraftForge —— 之所以这样排，是因为多加载器库针对各加载器的构建会把该加载器缺少的阶段换成存根，而这个顺序能让调用空方法的调用方最少（javadoc 里记录了定下这个顺序的两个案例）。
- **覆盖设置** —— `-Dforbric.modOwner=sodium=fabric,…` 或 `<rundir>/forbric-mods.txt`（每行一条 `<mod id> = <loader>`；实例第一次出现重复时，内核会写出一份带注释的模板）。命令行优先于文件。
- **关掉的 jar** —— `<rundir>/forbric-disabled.txt` 列出 `mods/` 里的 jar 文件名（注释和写错的行与 `forbric-mods.txt` 的处理方式相同）。`DisabledMods` 让这些 jar 不进入扫描，所以它们不会成为声明；它们进入 `Decision.suppressedJars`，但绝不进入 `rescueJars`；`-Dforbric.crossJarArbitration=off` 时仍会缓存一份只含这些 jar 的决定。`load-report.txt` 会列出它们。
- **残留处理** —— 落败的生态会得到一个仅在场的别名，让 `isLoaded(id)` 仍能作答（`Decision.aliases`）；对于已加载的 mod，它在另一个生态的构建可以作为最后手段借出缺失的类（`rescueJars`）；`ArbitratedAwayClasses` 统计落败构建里有、胜出方却缺少的内容。`MergeReport` 写出 `.forbric-kernel/merge-report.txt`，逐条解释每项决定。
- `-Dforbric.crossJarArbitration=off` 会完全关闭这一机制。

### 4.4 回答“我在哪个加载器上？”和“X 装了吗？”

- `LoaderProbePolicy` + `transform.LoaderProbeRewriter`（COREMOD 阶段）改写单加载器第三方类里的 `Class.forName` 调用点，让平台探测（`FMLLoader`、`FabricLoader`）按该 jar 被仲裁到的生态作答（`-Dforbric.loaderProbes=off`）。
- `net.forbric.api.ModPresence` 给出跨生态的答案。`transform.ForeignModPresenceInjector` 把它以逻辑或并入两个 Forge 系的 `ModList.isLoaded`；Fabric 侧把每个 Forge 系 mod 注册成仅在场的容器（只有身份 —— 没有入口点、mixin 或资源），让 `FabricLoader.isModLoaded` 能作答；Forge 系的列表也预置了 Fabric mod（`-Dforbric.crossEcosystemPresence=off` 恢复单加载器的答案）。`net.forbric.api.ModIds` 负责映射各生态拼写不同的 id（`cloth-config` / `cloth_config`）。
- `ModConstructionOrder` 按拓扑顺序构造 Forge 系 mod —— 一个 mod 排在它依赖的、以及它声明 `AFTER` 的所有 mod 之后；并列时按字母序；遇到环会点名报出，不会悄悄拆开（`-Dforbric.modOrder=name` 恢复按文件名排序）。Fabric mod 按 Fabric Loader 自己的顺序初始化，即按 mod id 排序（`FabricLoadOrder`；`-Dforbric.fabricOrder=off` 让它们回到拓扑顺序）。

## 5. 由内核驱动的三个生态

### 5.1 Fabric —— 不运行任何 Fabric Loader 代码

- `fabric.KernelFabricLoader` *就是* `FabricLoader` 单例：它是内核状态的一个视图，入口点索引在 mod 代码运行之前就已冻结。`KernelModContainer` 通过 zip `FileSystem` 暴露每个 jar；`KernelLanguageAdapters` 支持 `languageAdapters`（fabric-language-kotlin）；`KernelObjectShare`、`KernelVersion`、`KernelMappingResolver`（恒等映射）补齐了整套 API。
- 面向 mod 的 API 以原名内置：`src/main/java/net/fabricmc/api`（7 个文件）和 `src/main/java/net/fabricmc/loader`（30 个文件：`loader.api.*`，加上 mod 实际会链接到的那一小部分内部类——`FabricLoaderImpl`、`ModContainerImpl`、`EntrypointStorage`、`Hooks`、`FabricLauncher`/`FabricLauncherBase`、`DefaultLanguageAdapter`、`StringUtil`，以及旧版的 `net.fabricmc.loader.FabricLoader`）。`FabricLoaderInternals` 按确切名称把这些内部类固定交给父加载器；`-Dforbric.fabricImpl=off` 则不提供它们。报告的 API 级别是 `KernelFabricEcosystem.FABRIC_LOADER_API_LEVEL = "0.19.3"`。
- 入口点：`preLaunch` 在 Mixin 之后（§3.2 第 19 步）；`main` 在注册窗口内（服务端）或在 `Minecraft.<init>` 里（客户端，默认）；`client` 在 `Minecraft.<init>` 里；`server` 在专用服务器上。每个 mod 的入口点都隔离调用——抛出异常的 mod 会记录下来并跳过——调用时该 mod 对应的 NeoForge `ModContainer` 处于激活状态（`KernelForeignShimContext`），因为 Fabric mod 可能持有某个多加载器库的 NeoForge 构建。
- Fabric 的访问加宽器 / class tweaker 在 `ACCESS` 阶段运行（`access.ClassTweakerTransformer`）；`@Environment` 剥离在 `ENV_STRIP` 阶段（`EnvironmentStripTransformer`，只作用于仲裁归 Fabric 的 jar 里的类）。

### 5.2 NeoForge

- **身份** —— `PassiveSeeder` 创建当前的 `FMLLoader`、`FMLPaths`，以及根据本次启动选中的 jar 构建的 `LoadingModList` 和 `ModList`——不做发现，没有模块层，不排序。
- **构造** —— `KernelModLoader`：一个由 `BusBuilder` 构建的 `IEventBus`，加上一个内核的 `ModContainer`（`net.forbric.kernel.runtime.KernelModContainer`）；构造函数参数按类型填充（`IEventBus`、`Dist`、`ModContainer`）。
- **总线** —— 内核自己发布的 mod 总线事件都走 `KernelLifecycle.postModBusEvent`；初始化阶段走 `runtime.KernelNeoSetup`；延迟任务在 NeoForge 原本运行它的那个线程上运行（`NeoDeferredWork`）。
- **枚举扩展** —— `NeoEnumExtensions` 把每个 jar 的 `META-INF/enumextensions.json` 交给 NeoForge 自己的 `RuntimeEnumExtender`；`NeoEnumExtensionInjector` 在 COREMOD 阶段接近末尾的位置注册（这个位置很关键：它会在那一刻定义 FML 类），而且只在有 mod 声明了扩展时才注册。
- **Coremod** —— NeoForge 的 coremod jar 从不加载；`transform.NativeCoremodParity` 代为执行它的改写（花盆 `potted`、生物群系气候/效果、结构设置、`finalizeSpawn`），时机在 Mixin *之后*，和 NeoForge 运行它们的时机一致。

### 5.3 MinecraftForge

- **身份** —— `PassiveSeeder.seedForgeFmlLoader`（Mixin 前，§3.2）；`ForgeLoadingListHolderInjector` 让 `LoadingModListImpl$1LazyInit` 读取内核发布的列表（`net.forbric.api.ForgeLoadingList`），而不是一个只有真正的加载器才会填充的字段；`ForgeLauncherInfoInjector` 代为应答 `FMLLoader` 里三个依赖 ModLauncher 的方法；`ForgeBindingsLookupInjector` 在没有 FML 模块层的情况下解析 `Bindings`；`ForgeSecureJarStandIn` 在没有 ModLauncher 的情况下给预置的 `ModFile` 提供 `SecureJar`。
- **构造** —— `KernelForgeModContext` 生成每个 Forge mod 构造函数都要接收的三件套：EventBus 7 的 `BusGroup` + `FMLModContainer` + `FMLJavaModLoadingContext`。在早期窗口里就去碰 `Minecraft` 的 mod 会推迟到 `onClientEntrypoints` 再构造，MinecraftForge 原本就在那里构造自己的 mod。
- **加载状态** —— `KernelLifecycle.setForgeLoadingState` 翻转 `loadingStateValid`；`publishForgeGatherStates` 把 `VALIDATE … LOAD_REGISTRIES` 记为已完成，这样 `ModLoader.hasCompletedState` 给出的才是实情（`-Dforbric.forgeLoadingStates=off`）。
- **Capability** —— 合并把 `Entity`/`BlockEntity`/`Level` 放到了 NeoForge 的附件继承体系之下，所以 `transform.ForgeCapabilityCompositionTransformer` 把 MinecraftForge 的 `CapabilityProvider` 组合进这些根类型，`ForgeCapabilityTokenInjector` 驱动 Forge 的 `CapabilityTokenSubclass` 插件（`-Dforbric.forgeCapabilities=off`）。`CapabilityUseAudit` 列出使用 MinecraftForge capability 的 jar；如果组合被关掉，或者没有落到每一个根类型上，就把这些 jar 标为 DEGRADED。
- **枚举扩展** —— `ForgeEnumExtensionInjector` 驱动 MinecraftForge 自己的处理器（无条件注册；除非 Forge 的 mod 列表里超过两个 mod，否则它什么都不处理）。
- **配置** —— `KernelForgeConfigLoad` 逐个打开真正的 MinecraftForge 配置，保留 Forge 的读取器、事件、保存和文件监视器；退出时由 `interop.ClientShutdown` 停掉这些监视器（`ExitHookInjector`：客户端上是 `Minecraft.close`，服务端上是 `DedicatedServer.onServerExit`）。

### 5.4 事件之外的跨生态服务

- **网络** —— 在 Fabric API 和 NeoForge 共用同一个原版频道 id 的地方，`interop.PayloadInterop` 按运行时的负载类选择自定义负载编解码器；`CommonNetworkInteropInjector` 仲裁两边都要占用的 `c:version` / `c:register` 频道（`-Dforbric.commonNetworkInterop=off`）；`RegistrySyncParityInjector` + `KernelForgeWrapperSync` 借助 Forge 自己的 `GameData.injectSnapshot`，把 NeoForge 的注册表同步（装了 fabric-api 时还有 fabric-api 的）应用到 MinecraftForge 包装的注册表上；`KernelRegistryRevert` 在断开连接时恢复连接之前的 id；`NetworkChannelCensus` 比对已注册的频道和已声明的频道。合并后的 `ServerGamePacketListenerImpl.handleCustomPayload` 是 MinecraftForge 的重写：它问一下 `ForgeHooks.onCustomPayload`，把答案丢掉，永远走不到 NeoForge，所以每个 NeoForge mod 在游玩阶段发给服务端的包都没人收（Carry On 的"搬运键按下"包就是其中之一，所以它什么都搬不起来）。`CommonNetworkInteropInjector` 把丢掉的答案改成分支：MinecraftForge 没收、而 NeoForge 注册过的负载交给 `NetworkRegistry.handleModdedPayload`。直接调它而不是调 `super`，因为 fabric-api 注入在 super 里的处理器只服务配置阶段的监听器，对游玩阶段的会抛 `Unknown addon`（`-Dforbric.playPayloadFallThrough=off` 关闭）。`KernelClientSmoke` 的 `-Dforbric.clientSmokeCarry=<tick>` 演练用游戏自己的键盘和鼠标输入把整条链跑一遍。
- **物品/流体/能量传输** —— `KernelTransferInterop` + `runtime/transfer/` 桥接 Fabric 的传输 API、NeoForge 的 `ResourceHandler` 和 MinecraftForge capability，装了 Team Reborn Energy 时也桥接它（`-Dforbric.transferBridge=off`、`-Dforbric.hopperFabricStorage=off`）。只在相关 API 存在时启用，是否存在通过查找资源来判断。

## 6. 转换流水线

`transform.TransformPhase` 规定了顺序：`RAW_PATCH`、`DEOBF_REMAP`、`ENV_STRIP`、`ACCESS`、`COREMOD`、`FABRIC_BUILTIN`、`MIXIN`。`TransformChain` 运行链上的阶段（`RAW_PATCH` … `FABRIC_BUILTIN`）；同一阶段内，先按 `predepends` 做拓扑排序，再按 `sortIndex`，最后按注册顺序。`MIXIN` 是终结阶段，不能注册进链里。在 26.2 上，`RAW_PATCH` 和 `DEOBF_REMAP` 里什么都没注册。

`KernelBoot` 注册了什么（91 个调用点，部分有条件）：

| 阶段 | 注册内容 |
| --- | --- |
| `ENV_STRIP` | `EnvironmentStripTransformer` |
| `ACCESS` | `ClassTweakerTransformer`（Fabric），以及由每个 mod jar **和两个载体**的 `META-INF/accesstransformer*.cfg` 构建的 `access.AccessTransformer`——载体的 AT 之所以要紧，是因为合并在哪里保留了另一个 Forge 系的方法体，就在哪里连带保留了那个方法体的访问修饰符（`MenuScreens.register` 合并后成了 private） |
| `COREMOD` | 86 项注册：加载器探测改写器、Mixin 织入器插槽（`FmlContextLoaderRewriter`、`ModuleClassLoaderInitInjector`）、`GuestMixinPluginGuard`、生命周期重定向、`ForbricMergedBaseCompatTransformer`、capability 组合，以及 `transform/` 里 70 个 `*Injector` 类中的大部分，每个类要么修复一处合并弄坏的具名接缝，要么接上一座桥（§8） |
| `FABRIC_BUILTIN` | `RestoredAccessTransformer`（给 COREMOD 恢复出来的成员重新应用访问修饰符）、`MergedBaseFrameRecomputer`（第三方类的栈映射帧引用了合并删掉的父类时，重新计算这些帧） |

Mixin 后阶段（`KernelMixinBootstrap`）是固定的组合：

```
Mixin (via MixinWeaverSlot) → NativeCoremodParity → PostMixinFixups → InterfaceDefaultConflictRepair
                           → ForgeTransferShapeAudit.certify
```

按类别列出值得一提的修复（每个类是因为哪个案例写出来的，请看它的 javadoc）：

- **合并不变量** —— `ForbricMergedBaseCompatTransformer`（lambda 引导句柄与 static 属性的冲突、MinecraftForge 的 `getFluidType()` 桥、按键映射的 `MAP` 初始化器、把原版的 `KeyMapping.MAP` 作为“按键 → 映射”的视图加回去（`KernelKeyMappingMap`；两个生态都把它改了类型，而 LiquidBounce 在打开界面时的每次按键都会读它）、在 NeoForge 重新编译时把参数为 byte 的调用绑到了它扩展接口的 `writeByte(byte)`（这个方法只是转发到原版的 `writeByte(int)`）的地方，改回调用原版的 `FriendlyByteBuf.writeByte(int)`（10 个网络 `write` 方法里共 14 处；ViaFabricPlus 的能力标志重定向锚定的是原版的调用；`-Dforbric.vanillaWriteByte=off` 关闭），以及把基底里写死的、指向 `net/forbric/loader/impl/…` 的调用重定向到 `net.forbric.kernel.interop`）、`DuplicateLambdaPruneInjector`（只按名字匹配的 mixin 选择器会绑上去的孤立 lambda）、`WidenedFieldTwinInjector`（改过类型的字段的原版描述符孪生字段）、`MethodBodyNeuter`。
- **包与数据** —— `DataPackHookInjector`、`ClientPackHookInjector`、`PackMetadataFailSoftInjector`、`PackOverlayMutabilityInjector`、`NullPackGuardInjector`、`PackScreenHiddenFilterInjector`、`RegistryDirectoryOwnerInjector`、`RegistryAliasParityInjector`（§9）。
- **UI** —— `ModsButtonRedirector`（两个 Forge 系的暂停菜单 lambda 都打开 `KernelModListScreen`）、`HudElementBridgeInjector`、`CreativePagerBridgeInjector`、`EarlyKeyMappingRegistrationInjector`、`CreativeSearchTreesInjector`。最后这个修的是生产者和消费者被合并劈到两边：合并后的 `SessionSearchTrees` 里，原版的 `updateCreativeTooltips(Provider, List)` / `updateCreativeTags(List)`（以及 `getSearchTree`）保留的是 MinecraftForge 的方法体，把搜索树存进一个私有 map；而创造模式物品栏界面是 NeoForge 的，只读 `CreativeModeTabSearchRegistry`。界面只有在 `CreativeModeTabs.tryRebuildTabContents` 报告标签页变了时才自己重建搜索树，所以一个自己重建标签页、再用原版方法刷新搜索的 mod（TCDCommons，每次进世界都这么做）会让整局的创造模式搜索都搜不到东西。现在这三个方法体改为调用 `KernelCreativeSearch`，对每个带搜索栏的标签页走 NeoForge 的带 key 方法；`-Dforbric.creativeSearchTrees=off` 关闭。`-Dforbric.clientSmokeCreativeSearch=<tick>[,query…]` 会在真实的创造模式界面里打字搜索并把结果网格写进日志。
- **插桩** —— `ClientSmokeTickInjector`（除非 `-Dforbric.clientSmoke=true`，否则不起作用）、`EventChainAuditInjector`（除非 `-Dforbric.eventChainAudit=<report>`，否则不起作用）、`ServerTickSamplerInjector`、`CompatibilityPromptTickInjector`、`ServerCompatibilityTickInjector`。
- **加固** —— `ChunkExecutorGuardInjector`（服务器停止后，拒收提交给其区块执行器的任务；`-Dforbric.chunkExecutorGuard=off`）。

承诺会落到某个类上的转换器要声明这个类（`AnchorSet`）；`AnchorLedger` 会报告经过转换器却没有被改动的类（记为一次 **Miss**，立即以 ERROR 级别记日志），并在普查标志点报告从未加载过的类。`RepairDriftCensus`（`run/compat/repair-drift.sh`）把这些声明拿到一个*候选*游戏构建上重放——升级载体时要问的正是这个问题。

## 7. 合并基底上的 Mixin

### 7.1 内核就是 Mixin 服务

`mixin.ForbricMixinService` 通过 `META-INF/services/org.spongepowered.asm.service.IMixinService` 注册（连同 `ForbricMixinServiceBootstrap` 和 `ForbricGlobalPropertyService`）；Mixin 库用的是 Fabric 的分支（`net.fabricmc:sponge-mixin`，版本见 `gradle.properties` 里的 `mixin_version`），MixinExtras 则是内核自带的 `mixinextras-fabric`（放在游戏侧，因为它生成的 `LocalRef` 类必须和游戏共用同一个加载器）。`KernelMixinBootstrap.init` 绑定服务、注册每个配置、安装 `KernelMixinErrorHandler`、把织入器装成流水线的最后一级，然后把环境推进到 `INIT` 和 `DEFAULT`。配置的准备（以及插件的构造）发生在第一个经过转换器的类上，也就是 `KernelRuntimeClasses.verify`。

顺序：Fabric 配置按 Fabric Loader 的顺序，Forge 系配置追加在后面。Mixin 按优先级排序，注册顺序只用来决定同优先级的先后，所以验证最少的那一组会成为最外层，包在已知可靠的栈外面。`MixinWeaverSlot` 让第三方 mod 能按 NeoForge 允许的方式替换织入器（LibJF 包装了 `FMLMixinClassProcessor.transformer`）。

### 7.2 Mixin 读取之前，第三方配置会被怎样处理

`ForbricMixinService` 会改写每个配置的 JSON：

1. **整份配置的拦截** —— `MixinConfigPolicy.isDisabled`：`MergedBaseMixinCompat` 里的内置列表（`-Dforbric.mergedBaseCompat=off` 去掉这份列表；`-Dforbric.enableMixinConfigs` 把某个配置强制加回来），再加上 `-Dforbric.disableMixinConfigs`（逗号分隔，支持末尾的 `*` 通配）。
2. **放宽** —— 名字不以 `forbric` 开头的配置都是第三方配置，一律放宽：`injectors.defaultRequire → 0`、`overwrites.requireAnnotations → false`、`required → false`。单个注入器上的 `require`/`expect` 仍然优先。`-Dforbric.relaxGuestMixins=off` 恢复严格行为；放宽关闭时，`-Dforbric.relaxMixinOverwrites` 会放宽指定的配置；`-Dforbric.mixinDiagnostics` 让注入要求保持严格（这样每一处不匹配都会报出来），同时保留 `required=false`。
3. **逐个丢弃 mixin** —— 取 `KernelGuestMixinAdapter.unfitMixins`（见下文）、手工列表 `MergedBaseMixinCompat.SUPPRESSED_MIXINS` 和 `-Dforbric.suppressMixins=config:Mixin,…` 三者的并集，再减去 `-Dforbric.keepMixins`。丢弃一个 mixin，也会连带丢弃所有依赖它添加的接口的 mixin。

### 7.3 `MixinFit` —— 看能否解析，不看出处

`KernelGuestMixinAdapter` 让 `MixinFit.evaluate` 解析第三方 mixin 点名的每个锚点——每个 `@Shadow`、每个注入器的目标方法、每个 `@At(target=…)`——解析对象是合并后目标类经过**转换链之后**的字节：

| 判定 | 含义 | 默认动作 |
| --- | --- | --- |
| `FIT` | 所有锚点都能解析 | 应用 |
| `PARTIAL` | 部分能解析 | **应用**（只有在 `-Dforbric.mixinFit=strict` 下才丢弃）；总会报告 |
| `UNFIT` | 一个都解析不了 | 丢弃 |
| `HAZARD` | 能顺利应用，但 @Shadow 了一个被合并弄成孤立的字段 | 丢弃 |

细化规则：纯 accessor/invoker mixin 永远保留；由另一个 mod 的 mixin 满足的锚点（`ForeignMixinTargets`、`MixinAddedMembers`）算作已解析；`@Group` 注入器按组整体判定；如果一个注入器只绑定在合并后的游戏里没有任何地方调用的合并基底方法上，它就**不算**已解析（活性检查，`MergedBaseUncalledMethods`，`-Dforbric.mixinFit.liveness=off`），除非某个已安装的 mod 调用了这个方法；只按名字写的 `@Inject` 选择器，如果它绑到的方法（Mixin 取目标类里第一个同名方法）不是 handler 写给的那个（该方法的全部参数再加上与返回类型对应的 callback，或者只有 callback），Mixin 会报 "Invalid descriptor" 拒绝，这种也**不算**已解析（`-Dforbric.mixinFit.handlerFit=off`）：合并基底可能在原版方法的位置上放了载体的重载，不管 mod 属于哪个生态。`MixinOverloadPin` 会钉住的选择器（见 7.4）按它落到的那个重载来判，所以钉住和这条规则不会同时作用在一个注入器上；`@Surrogate` 只按 Mixin 查找它的方式算数——handler 的名字、与绑到方法的 callback 描述符完全一致、注解是可见的。如果这个名字恰好只绑到一个方法、handler 也不抓局部变量，并且它的某个 `@At` 在那个方法里一定能找到注入点（`HEAD`；方法里有返回时的 `RETURN`/`TAIL`；成员确实在方法里、并且数量超过 `ordinal` 的 `INVOKE`、`INVOKE_ASSIGN`、`FIELD`、`NEW`；带 slice 的一律不算），这个缺失就是一次*拒绝*（`MixinFit.Rejection`）：Mixin 在找到的每个注入点上检查 handler，在第一个点上直接抛异常，不看 `require`，异常让这个 mixin 对该类的应用整体失败——排在它后面的注入器全部跟着丢，配置仍是 required 的话整个游戏也停。如果不能确定有注入点，Mixin 可能一个点也找不到，什么也不注入、也不抛异常，所以这种绑定仍按普通缺失处理，日志那一行会写明原因（`-Dforbric.mixinFit.rejectionPoint=off` 恢复旧做法，把这种绑定都当成拒绝）。所以带着拒绝的 mixin——不管是 `PARTIAL`、因为在另一个 mod 的类上有缺失而保留的，还是 `UNFIT` 但因为另一个 mod 的 mixin 也改这个类而保留的——都不会原样保留：如果 mixin 里没有别的代码调用它、它不在 `@Group` 里、也没有哪个目标按原写法绑上它，就在 Mixin 读到 mixin 之前把这一个注入器剪掉（`GuestInjectorPruner`，见 7.4，对 Mixin 实际收到的节点、带着目标类的代码再按同一条规则问一次），mixin 的其余部分照常应用，剪掉的注入器记一条 `CONFIRMED` 发现，作者自己给它定的次数（`require`，没有就看 `defaultRequire`）至少为一时算 required；剪不了就像 `UNFIT` 一样整个丢掉。被内核修复整个顶替的 mixin（`SupersededMixins`）总是整个丢掉，那一行不向玩家发问，看到修复生效后自动消解。按名字保留的 mixin（`MergedBaseMixinCompat.KEPT_MIXINS`、`-Dforbric.keepMixins`）不经判定，原样交给 Mixin。`-Dforbric.guestInjectorPruner.refused=off` 让这种 mixin 照旧交给 Mixin，`PARTIAL` 汇总里也会单独计数。`PARTIAL` 和 `UNFIT` 的 mixin 都会先交给 `MixinRetarget` 问一次，改写后缺的锚点更少、是适配器会保留的 mixin、而且没有多出拒绝，就采用这份计划——`UNFIT` 的改写不能留下任何拒绝（`MixinRetarget.adopt`）。内核按名字压掉的 mixin 根本不判定，既没有判定日志，也不计入 `PARTIAL` 数。`MixinFitReport` 离线执行同样的判定：`MixinFitReport <merged-base.jar> <mods-dir> [--verbose]`。

