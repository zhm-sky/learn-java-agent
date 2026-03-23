package com.learn.javaagent.Agent01;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;

/**
 * Agent 推理循环控制器。
 *
 * <p>实现 OpenAI 风格的 ReAct 循环：</p>
 * <ol>
 *   <li>将当前消息历史发给 LLM，获取 assistant 回复</li>
 *   <li>若 assistant 包含 tool_calls，则交由 ToolExecutor 执行每个工具调用</li>
 *   <li>将工具执行结果作为 role=tool 的消息追加到历史，再次请求 LLM</li>
 *   <li>重复直至模型不再调用工具，返回纯文本回复</li>
 * </ol>
 */
public final class AgentLoop {

    private AgentLoop() {
    }

    /**
     * 执行完整的 Agent 推理循环，就地修改 messages 并追加 assistant / tool 消息。
     *
     * @param messages 对话历史（user/assistant/tool 消息），会被就地追加新消息
     * @param llm      LLM 客户端，用于发起 Chat Completions 请求
     * @throws IOException 网络或 IO 异常
     */
    public static void run(JsonArray messages, LLMClient llm) throws IOException {
        ToolExecutor tools = new ToolExecutor(llm);

        while (true) {
            // 调用 API，请求体（system、model、tools 等）由 LLMClient 内部封装
            String responseJson = llm.completeChat(messages);
            JsonObject root = JsonParser.parseString(responseJson).getAsJsonObject();

            // 检查 API 返回的业务错误
            if (root.has("error") && !root.get("error").isJsonNull()) {
                throw new IllegalStateException("API error: " + root.get("error"));
            }
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new IllegalStateException("Missing choices");
            }

            // 取出第一条 choice 的 message，即 assistant 的回复
            JsonObject assistant = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            messages.add(assistant.deepCopy());

            // 若无工具调用，说明模型已给出最终文本回复，结束循环
            if (!assistant.has("tool_calls") || assistant.get("tool_calls").isJsonNull()
                    || assistant.getAsJsonArray("tool_calls").isEmpty()) {
                return;
            }

            // 执行每个 tool_call，将结果作为 tool 消息追加到历史
            for (JsonElement tc : assistant.getAsJsonArray("tool_calls")) {
                JsonObject toolResult = tools.executeToolCall(tc.getAsJsonObject());
                messages.add(toolResult);
            }
        }
    }
}
