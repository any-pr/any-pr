/**
 * BedrockAPI —— 照搬 Minecraft Bedrock Edition（1.20.0 开发者版）工程铁律的 API 骨架。
 *
 * <h2>来源</h2>
 * 本包的设计依据是对 Bedrock 客户端 {@code libminecraftpe.so}（NDK r25c / ELF64 AArch64）
 * 的三份符号表逆向成果：
 * <ul>
 *   <li>{@code all_funcs_demangled.txt} —— .dynsym 全量（含 WEAK 测试符号，行为规格书）</li>
 *   <li>{@code exports_demangled.txt} —— radare2 iE 导出表（GLOBAL FUNC + OBJ vtable/typeinfo）</li>
 *   <li>{@code paths.txt} + {@code MinecraftBedrockEdition__File__/} —— 源码树目录结构</li>
 * </ul>
 *
 * <h2>Bedrock 工程铁律（逆向确认）</h2>
 * <ol>
 *   <li><b>命名空间 = 目录层级</b>：{@code world::level::block::actor::ChestBlockActor}</li>
 *   <li><b>类名 = 文件名</b>：{@code ChestBlockActor.cpp} → {@code ChestBlockActor}</li>
 *   <li><b>后缀即职责</b>：{@code System} / {@code Component} / {@code Definition} /
 *       {@code Data} / {@code Registry} / {@code Storage} / {@code Factory} / {@code Util} /
 *       {@code Template} / {@code Request}</li>
 *   <li><b>核心逻辑符号全隐藏</b>：{@code -fvisibility=hidden}，仅导出模块边界接口的 vtable
 *       （Bedrock 只导出 11 个 {@code Vanilla*} 接口类）</li>
 *   <li><b>异步请求组件 + 系统轮询</b>：无同步跨维度传送，一律 {@code Request} + {@code System.tick()}</li>
 *   <li><b>编译期特性开关</b>：{@code FeatureToggles} 让死分支被编译器彻底消除</li>
 *   <li><b>命令模板字符串透传</b>：{@code CmdTemplate} 保留 {@code ~} {@code ^} 与维度后缀，
 *       {@code toString()} 才拼装</li>
 * </ol>
 *
 * <h2>包结构约定</h2>
 * <pre>
 * net.MinecraftTools.BedrockAPI
 * ├── api/        模块边界接口（对应 Bedrock 的 11 个 Vanilla* 接口，只放接口）
 * ├── command/    命令模板族（CmdTemplate 延迟拼装）
 * ├── request/    异步请求 + 请求注册表（对应 *RequestComponent + System 轮询）
 * ├── system/     ECS 三元：TickingSystem / Component / ComponentDefinition
 * └── toggle/     编译期特性开关
 * </pre>
 *
 * <h2>命名注意</h2>
 * 子包名沿用 Bedrock 的<b>小写目录</b>风格（{@code command} 而非 {@code Command}），
 * 一是与 Bedrock 源码树一致，二是规避 {@code system.System} 与 {@code java.lang.System} 冲突；
 * 系统接口因此命名为 {@link net.MinecraftTools.BedrockAPI.system.TickingSystem}
 * （对应 Bedrock 的 {@code ITickingSystem}）。
 *
 * @since 2026-09-27
 */
package net.MinecraftTools.BedrockAPI;
