package com.learn.javaagent.Agent02.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.learn.javaagent.Agent02.client.LLMClient;
import com.learn.javaagent.Agent02.tools.ToolExecutor;
import com.learn.javaagent.Agent02.utils.AgentLogger;

import java.io.IOException;
import java.util.Objects;

/**
 * Agent02 核心循环控制器。
 *
 * <p>在 Agent01 基础上增加：</p>
 * <ul>
 *   <li>多工具分发（通过 ToolRegistry，不感知具体工具类型）</li>
 *   <li>Todo 使用计数：连续 N 轮未调用 todo 时注入提醒（nag），引导模型更新任务计划</li>
 * </ul>
 *
 * <p>循环逻辑与 Agent01 一致：请求模型 → 解析 tool_calls → 执行工具 → 追加 tool 消息 → 直至无工具调用。</p>
 */
public final class AgentLoop {

    /** 连续多少轮未调用 todo 时触发提醒注入 */
    private static final int TODO_NAG_THRESHOLD = 3;

    private final ToolExecutor tools;
    private final TodoManager todoManager;
    private int roundsSinceTodo;

    /**
     * 默认构造：创建会话级 {@link TodoManager} 并接入标准工具。
     */
    public AgentLoop() {
        this(new TodoManager());
    }

    /**
     * 使用外部传入的会话级 {@link TodoManager}，便于跨轮持久化任务计划状态。
     *
     * @param todoManager 多步任务状态管理器
     */
    public AgentLoop(TodoManager todoManager) {
        this.todoManager = Objects.requireNonNull(todoManager, "todoManager");
        this.tools = new ToolExecutor(this.todoManager);
        this.roundsSinceTodo = 0;
    }

    /**
     * 在 {@code messages} 上就地追加 assistant / tool 消息，直至本轮无 {@code tool_calls}。
     */
    public void run(JsonArray messages, LLMClient llm) throws IOException {
        int decisionRound = 1;
        while (true) {
            // 请求体（system、model、tools 等）由 LLMClient#completeChat 统一封装
            JsonObject root = JsonParser.parseString(llm.completeChat(messages)).getAsJsonObject();
            if (root.has("error") && !root.get("error").isJsonNull()) {
                throw new IllegalStateException("API error: " + root.get("error"));
            }
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new IllegalStateException("Missing choices");
            }

            JsonObject assistant = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            boolean hasToolCalls = hasToolCalls(assistant);
            logAssistantDecision(assistant, decisionRound, !hasToolCalls);
            messages.add(assistant.deepCopy());

            if (!hasToolCalls) {
                return;
            }

            JsonArray toolCalls = assistant.getAsJsonArray("tool_calls");
            boolean calledTodo = containsTodoCall(toolCalls);
            if (calledTodo) {
                roundsSinceTodo = 0;
            } else {
                roundsSinceTodo++;
            }

            String nagReminder = shouldInjectNag(calledTodo) ? buildNagReminder() : "";
            if (!nagReminder.isEmpty()) {
                AgentLogger.logReminderContent(nagReminder);
            }

            boolean nagInjected = false;
            for (JsonElement tc : toolCalls) {
                JsonObject toolCall = tc.getAsJsonObject();
                JsonObject toolMessage = tools.executeToolCall(toolCall);
                if (!nagInjected && !nagReminder.isEmpty()) {
                    String original = str(toolMessage, "content");
                    toolMessage.addProperty("content", original + "\n\n" + nagReminder);
                    AgentLogger.logReminderInjected(decisionRound, str(toolMessage, "tool_call_id"));
                    nagInjected = true;
                }
                messages.add(toolMessage);
            }
            decisionRound++;
        }
    }

    private boolean shouldInjectNag(boolean calledTodoInRound) {
        return !calledTodoInRound && roundsSinceTodo >= TODO_NAG_THRESHOLD;
    }

    private String buildNagReminder() {
        return "[REMINDER] You have gone " + roundsSinceTodo
                + " rounds without calling todo. Update the plan before proceeding. "
                + "Current todo snapshot: " + todoManager.summaryText();
    }

    private static boolean containsTodoCall(JsonArray toolCalls) {
        for (JsonElement tc : toolCalls) {
            if (!tc.isJsonObject()) {
                continue;
            }
            JsonObject obj = tc.getAsJsonObject();
            if (!obj.has("function") || !obj.get("function").isJsonObject()) {
                continue;
            }
            JsonObject fn = obj.getAsJsonObject("function");
            if ("todo".equals(str(fn, "name"))) {
                return true;
            }
        }
        return false;
    }

    private static String str(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return "";
        }
        return obj.get(key).isJsonPrimitive() ? obj.get(key).getAsString() : obj.get(key).toString();
    }

    /**
     * 打印模型返回的决策信息，用于观察“回答/调用工具”分流路径。
     * <p>
     * 这里不依赖模型是否公开“思考文本”，而是打印可观测的决策结果：
     * assistant 文本、工具调用列表、以及下一步路由依据。
     * </p>
     *
     * @param assistant 模型返回的 assistant message
     */
    private static void logAssistantDecision(JsonObject assistant, int decisionRound, boolean isFinalReply) {
        String reasoning = str(assistant, "reasoning_content");
        AgentLogger.logModelReasoning(decisionRound, reasoning);

        if (isFinalReply) {
            AgentLogger.logFinalReply(decisionRound, str(assistant, "content"));
        }

        if (assistant.has("tool_calls") && !assistant.get("tool_calls").isJsonNull()) {
            AgentLogger.logToolCallArguments(assistant.getAsJsonArray("tool_calls"), decisionRound);
        } else {
            AgentLogger.logToolCallArguments(null, decisionRound);
        }
    }

    private static boolean hasToolCalls(JsonObject assistant) {
        if (!assistant.has("tool_calls") || assistant.get("tool_calls").isJsonNull()) {
            return false;
        }
        JsonArray arr = assistant.getAsJsonArray("tool_calls");
        return arr != null && !arr.isEmpty();
    }
}
