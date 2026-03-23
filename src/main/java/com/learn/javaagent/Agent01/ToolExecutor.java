package com.learn.javaagent.Agent01;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 工具调用执行器。
 *
 * <p>根据 LLM 返回的 tool_calls 执行对应工具，并生成 OpenAI 兼容的 role=tool 消息。</p>
 *
 * <p>当前支持：</p>
 * <ul>
 *   <li>bash：解析 arguments 中的 command 字段，调用 BashTool 执行</li>
 *   <li>其他工具名：返回 "Error: unsupported tool xxx"</li>
 * </ul>
 */
public final class ToolExecutor {

    /** 黄色 ANSI 转义码，用于高亮显示执行的命令 */
    private static final String YEL = "\u001B[33m";
    /** 重置 ANSI 样式 */
    private static final String RST = "\u001B[0m";
    /** 控制台输出的截断长度，过长时只显示前 200 字符 */
    private static final int CONSOLE_TRUNCATE_LEN = 200;

    private final String bashToolName;

    /**
     * 从 LLMClient 获取 bash 工具名（与 AgentConfig 中定义一致）。
     */
    public ToolExecutor(LLMClient llm) {
        this(llm.bashToolName());
    }

    /**
     * 直接指定 bash 工具名（便于测试）。
     *
     * @param bashToolName 须与 tools 声明中的 function.name 一致
     */
    public ToolExecutor(String bashToolName) {
        this.bashToolName = bashToolName;
    }

    /**
     * 执行单条 tool_call，并构造 role=tool 消息。
     *
     * <p>tool_call 结构：id, function.name, function.arguments（JSON 字符串）</p>
     *
     * @param toolCall 来自 assistant 消息的 tool_calls 数组中的一条
     * @return 可追加到 messages 的 JsonObject，含 role、tool_call_id、content
     */
    public JsonObject executeToolCall(JsonObject toolCall) {
        String id = getStr(toolCall, "id");
        JsonObject fn = toolCall.has("function") && toolCall.get("function").isJsonObject()
                ? toolCall.getAsJsonObject("function") : new JsonObject();
        String name = getStr(fn, "name");
        String args = getStr(fn, "arguments");

        JsonObject toolMsg = new JsonObject();
        toolMsg.addProperty("role", "tool");
        toolMsg.addProperty("tool_call_id", id);

        if (!bashToolName.equals(name)) {
            toolMsg.addProperty("content", "Error: unsupported tool " + name);
            return toolMsg;
        }

        String cmd = parseCommand(args);
        System.out.println(YEL + "$ " + cmd + RST);

        String out = BashTool.run(cmd);
        // 控制台只显示部分输出，完整结果仍写入 tool 消息供 LLM 使用
        System.out.println(out.length() > CONSOLE_TRUNCATE_LEN ? out.substring(0, CONSOLE_TRUNCATE_LEN) + "..." : out);

        toolMsg.addProperty("content", out);
        return toolMsg;
    }

    /**
     * 从 function.arguments 的 JSON 字符串中解析出 command 字段。
     */
    private static String parseCommand(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.trim().isEmpty()) {
            return "";
        }
        try {
            JsonObject obj = JsonParser.parseString(argumentsJson).getAsJsonObject();
            return getStr(obj, "command");
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 从 JsonObject 中安全获取字符串值。
     */
    private static String getStr(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) {
            return "";
        }
        JsonElement e = o.get(key);
        return e.isJsonPrimitive() ? e.getAsString() : e.toString();
    }
}
