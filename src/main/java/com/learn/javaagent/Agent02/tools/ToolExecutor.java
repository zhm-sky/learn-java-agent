package com.learn.javaagent.Agent02.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.learn.javaagent.Agent02.runtime.TodoManager;

import java.util.Objects;

/**
 * 根据模型返回的 {@code tool_calls} 单条记录执行对应工具，并生成 OpenAI 兼容的 {@code role: tool} 消息正文。
 * <p>
 * 执行由 {@link ToolRegistry} 完成；本类不感知具体工具类型（无 bash 等分支）。
 * </p>
 *
 * @author 298751
 */
public final class ToolExecutor {

    private final ToolRegistry registry;

    /**
     * 使用 {@link ToolRegistry#withStandardTools()} 内置工具集。
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

        JsonObject tool = new JsonObject();
        tool.addProperty("role", "tool");
        tool.addProperty("tool_call_id", id);

        String out = registry.dispatch(name, args);
        tool.addProperty("content", out);
        return tool;
    }

    private static String str(JsonObject o, String k) {
        if (!o.has(k) || o.get(k).isJsonNull()) {
            return "";
        }
        JsonElement e = o.get(k);
        return e.isJsonPrimitive() ? e.getAsString() : e.toString();
    }
}
