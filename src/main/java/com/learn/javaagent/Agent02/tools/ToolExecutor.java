package com.learn.javaagent.Agent02.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.learn.javaagent.Agent02.runtime.TodoManager;

import java.util.Objects;

/**
 * 工具调用执行器，将 tool_calls 转为 role: tool 消息。
 *
 * <p>与 Agent01 的 ToolExecutor 不同：本类不感知具体工具类型，所有执行委托给 {@link ToolRegistry#dispatch}，
 * 扩展新工具只需在 ToolRegistry 中注册，无需修改本类。</p>
 */
public final class ToolExecutor {

    private final ToolRegistry registry;

    /**
     * 使用标准工具集（含新建的 TodoManager）。
     */
    public ToolExecutor() {
        this(ToolRegistry.withStandardTools());
    }

    /**
     * 使用会话级 {@link TodoManager} 构建标准工具集。
     *
     * @param todoManager 多步任务状态管理器
     */
    public ToolExecutor(TodoManager todoManager) {
        this(ToolRegistry.withStandardTools(todoManager));
    }

    /**
     * @param registry 自定义工具注册中心（测试或裁剪工具集时使用）
     */
    public ToolExecutor(ToolRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /**
     * 解析一条 {@code tool_calls[]} 元素，执行工具并返回待追加到对话历史的 tool 消息。
     *
     * @param toolCall 含 {@code id}、{@code function.name}、{@code function.arguments}
     * @return {@code role=tool}、{@code tool_call_id}、{@code content} 的 JSON 对象
     */
    public JsonObject executeToolCall(JsonObject toolCall) {
        String id = str(toolCall, "id");
        JsonObject fn = toolCall.has("function") && toolCall.get("function").isJsonObject()
                ? toolCall.getAsJsonObject("function") : new JsonObject();
        String name = str(fn, "name");
        String args = str(fn, "arguments");

        JsonObject toolMsg = new JsonObject();
        toolMsg.addProperty("role", "tool");
        toolMsg.addProperty("tool_call_id", id);

        String content = registry.dispatch(name, args);
        toolMsg.addProperty("content", content);
        return toolMsg;
    }

    /** 从 JsonObject 安全获取字符串值 */
    private static String str(JsonObject o, String k) {
        if (!o.has(k) || o.get(k).isJsonNull()) {
            return "";
        }
        JsonElement e = o.get(k);
        return e.isJsonPrimitive() ? e.getAsString() : e.toString();
    }
}
