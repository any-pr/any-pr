package net.MinecraftTools.BedrockAPI.request;

/**
 * 异步请求状态 —— 对应 Bedrock {@code *RequestComponent} 的生命周期阶段。
 *
 * <h2>Bedrock 实证（符号表）</h2>
 * <pre>
 * PlayerChangeDimensionRequestComponent&amp; EntityRegistryBase::_addComponent&lt;PlayerChangeDimensionRequestComponent&gt;(EntityId)
 * PlayerWithDimensionRequest_RequestPlayerChangeDimension_AddsRequest
 * PlayerWithoutDimensionRequest_RequestPlayerChangeDimension_DoesNothing
 * PlayerToNewDimensionLevelComplete_TryHandleChangeDimensionRequestLevel_RequestRemoved
 * PlayerToNewDimensionLevelNotComplete_TryHandleChangeDimensionRequestLevel_RequestRetained
 * PlayerToSameDimension_TryHandleChangeDimensionRequestLevel_RequestRemoved
 * PlayerWithChangeDimensionRequest_LeavesGame_ChangeDimensionRequestIsRemoved
 * </pre>
 * 从测试名可反推出这套状态机的<b>全部转移规则</b>：
 * 目标就绪 → 移除；未就绪 → <b>保留</b>；同维度 → 直接移除；实体离场 → 清理。
 *
 * @since 2026-09-27
 */
public enum RequestState {

    /** 已入队，尚未被系统处理。 */
    PENDING,

    /** 系统已开始处理（例如已发起区块加载 / 已发送切维度包）。 */
    IN_PROGRESS,

    /** 成功落地，可回收。 */
    COMPLETED,

    /** 失败（超时 / 目标非法），可回收，附带失败原因。 */
    FAILED,

    /** 主动取消（实体离场 / 上游撤销），可回收。 */
    CANCELLED;

    /** 是否为终态（可回收）。 */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    /** 是否仍需保留在队列里继续等待。 */
    public boolean isPending() {
        return this == PENDING || this == IN_PROGRESS;
    }
}
