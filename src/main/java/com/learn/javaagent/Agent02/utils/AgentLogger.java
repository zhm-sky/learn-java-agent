package com.learn.javaagent.Agent02.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Agent02 运行日志：模型推理、工具参数、最终回复、提醒注入。
 */
public final class AgentLogger {

    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();

    private AgentLogger() {
    }

    /**
     * 打印提醒内容。
     */
    public static void logReminderContent(String reminder) {
        System.out.println("[提醒内容] " + reminder);
    }

    /**
     * 打印提醒注入信息。
     */
    public static void logReminderInjected(int decisionRound, String toolCallId) {
        System.out.println("[提醒注入] round=" + decisionRound + ", tool_call_id=" + toolCallId);
    }

    /**
     * 打印模型推理（若当前模型返回该字段）。
     */
    public static void logModelReasoning(int decisionRound, String reasoning) {
        if (reasoning != null && !reasoning.isEmpty()) {
            System.out.println("[模型推理] round=" + decisionRound + ", content=" + reasoning);
        }
    }

    /**
     * 打印最终回复文本。
     */
    public static void logFinalReply(int decisionRound, String content) {
        if (content != null && !content.isEmpty()) {
            System.out.println("[大模型回复] round=" + decisionRound + ", content=" + content);
        } else {
            System.out.println("[大模型回复] round=" + decisionRound + ", content=(empty)");
        }
    }

    /**
     * 打印助手工具调用参数。
     * <p>
     * 输出格式固定为：
     * Tool Call &lt;toolName&gt;
     * &lt;pretty-printed arguments json&gt;
     * </p>
     */
    public static void logToolCallArguments(JsonArray toolCalls, int decisionRound) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            System.out.println("Tool Call (none)");
            System.out.println("[]");
            return;
        }
        for (JsonElement element : toolCalls) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject call = element.getAsJsonObject();
            JsonObject fn = call.has("function") && call.get("function").isJsonObject()
                    ? call.getAsJsonObject("function") : new JsonObject();
            String toolName = str(fn, "name");
            String argsRaw = str(fn, "arguments");
            System.out.println("Tool Call " + toolName);
            System.out.println(prettyArguments(argsRaw));
        }
    }

    /**
     * 将工具参数字符串格式化为易读文本；JSON 会进行 pretty print。
     *
     * @param raw tool call arguments 原始字符串
     * @return 可直接打印的参数文本
     */
    private static String prettyArguments(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "[]";
        }
        try {
            JsonElement parsed = JsonParser.parseString(raw);
            if (parsed.isJsonArray() || parsed.isJsonObject()) {
                return PRETTY_GSON.toJson(parsed);
            }
            return parsed.toString();
        } catch (Exception ignore) {
            return raw;
        }
    }

    private static String str(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return "";
        }
        return obj.get(key).isJsonPrimitive() ? obj.get(key).getAsString() : obj.get(key).toString();
    }
}
