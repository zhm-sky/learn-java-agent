package com.learn.javaagent.Agent01;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Scanner;

/**
 * 程序主入口类。
 *
 * <p>功能说明：</p>
 * <ul>
 *   <li>初始化 LLM 客户端（从 agent.properties 或环境变量加载配置）</li>
 *   <li>在控制台提供 REPL 交互循环，用户输入问题后调用 AgentLoop 执行 Agent 推理</li>
 *   <li>退出条件：输入 q/exit、空行、或 EOF（Ctrl+D）</li>
 * </ul>
 */
public final class Main {

    /** 青色 ANSI 转义码，用于高亮提示符 */
    private static final String CYN = "\u001B[36m";
    /** 重置 ANSI 转义码，恢复终端默认样式 */
    private static final String RST = "\u001B[0m";

    private Main() {
    }

    /**
     * 主方法：启动 Agent 交互循环。
     *
     * @param args 命令行参数（当前未使用）
     * @throws Exception 配置加载失败或 API 调用异常时抛出
     */
    public static void main(String[] args) throws Exception {
        LLMClient llm = new LLMClient();
        // 对话历史，格式符合 OpenAI Chat Completions API 的 messages 结构
        JsonArray history = new JsonArray();
        Scanner in = new Scanner(System.in, "UTF-8");

        while (true) {
            System.out.print(CYN + "用户输入 >> " + RST);
            if (!in.hasNextLine()) {
                break;
            }
            String line = in.nextLine();
            if (line == null) {
                break;
            }
            line = line.trim();
            if (line.isEmpty() || "q".equalsIgnoreCase(line) || "exit".equalsIgnoreCase(line)) {
                break;
            }

            // 将用户输入包装为 user 角色消息并加入历史
            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");
            userMsg.addProperty("content", line);
            history.add(userMsg);

            // 执行 Agent 循环：LLM 推理 + 工具调用，直至模型返回最终文本回复
            AgentLoop.run(history, llm);

            // 打印最后一轮 assistant 的文本回复（通常是最新一条消息）
            JsonObject last = history.get(history.size() - 1).getAsJsonObject();
            if (last.has("content") && !last.get("content").isJsonNull() && last.get("content").isJsonPrimitive()) {
                System.out.println(last.get("content").getAsString());
            }
            System.out.println();
        }
    }
}
