package com.learn.javaagent.Agent01;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 根据模型返回的 {@code tool_calls} 单条记录执行对应工具，并生成 OpenAI 兼容的 {@code role: tool} 消息正文。
 * <p>
 * 当前仅支持 {@link LLMClient#bashToolName()} 与 {@link BashTool} 的映射；其余工具名返回错误说明字符串。
 * </p>
 *
 * @author 298751
 */
public final class ToolExecutor {

    private static final String YEL = "\u001B[33m";
    private static final String RST = "\u001B[0m";

    private final String bashToolName;

    /**
     * @param llm 用于取得配置中的 bash 工具名（与 {@link AgentConfig#TOOL_NAME_BASH} 一致）
     */
    public ToolExecutor(LLMClient llm) {
        this(llm.bashToolName());
    }

    /**
     * @param bashToolName 与 {@code tools} 声明中 function.name 一致
     */
    public ToolExecutor(String bashToolName) {
        this.bashToolName = bashToolName;
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
        if (!bashToolName.equals(name)) {
            tool.addProperty("content", "Error: unsupported tool " + name);
            return tool;
        }
        String cmd = parseCommand(args);
        System.out.println(YEL + "$ " + cmd + RST);
        String out = BashTool.run(cmd);
        System.out.println(out.length() > 200 ? out.substring(0, 200) : out);
        tool.addProperty("content", out);
        return tool;
    }

    /**
     * 从 function.arguments JSON 中取出 {@code command} 字段。
     */
    private static String parseCommand(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.trim().isEmpty()) {
            return "";
        }
        try {
            return str(JsonParser.parseString(argumentsJson).getAsJsonObject(), "command");
        } catch (Exception e) {
            return "";
        }
    }

    private static String str(JsonObject o, String k) {
        if (!o.has(k) || o.get(k).isJsonNull()) {
            return "";
        }
        JsonElement e = o.get(k);
        return e.isJsonPrimitive() ? e.getAsString() : e.toString();
    }
}
