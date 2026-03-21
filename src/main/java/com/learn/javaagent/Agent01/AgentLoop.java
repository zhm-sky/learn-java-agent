package com.learn.javaagent.Agent01;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;

/**
 * Chat Completions + tool_calls 循环：通过 {@link LLMClient} 请求模型；
 * 若有工具调用则委托 {@link ToolExecutor} 执行并写回 tool 消息。
 *
 * @author 298751
 */
public final class AgentLoop {

    private AgentLoop() {
    }

    /**
     * 在 {@code messages} 上就地追加 assistant / tool 消息，直至本轮无 {@code tool_calls}。
     */
    public static void run(JsonArray messages, LLMClient llm) throws IOException {
        ToolExecutor tools = new ToolExecutor(llm);
        while (true) {
            // 请求体（system、model、tools 等）由 LLMClient#completeChat 统一封装
            JsonObject root = JsonParser.parseString(llm.completeChat(messages)).getAsJsonObject();
            if (root.has("error") && !root.get("error").isJsonNull()) {
                throw new IllegalStateException("API error: " + root.get("error"));
            }
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.size() == 0) {
                throw new IllegalStateException("Missing choices");
            }

            JsonObject assistant = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            messages.add(assistant.deepCopy());

            if (!assistant.has("tool_calls") || assistant.get("tool_calls").isJsonNull()
                    || assistant.getAsJsonArray("tool_calls").size() == 0) {
                return;
            }

            for (JsonElement tc : assistant.getAsJsonArray("tool_calls")) {
                messages.add(tools.executeToolCall(tc.getAsJsonObject()));
            }
        }
    }
}
