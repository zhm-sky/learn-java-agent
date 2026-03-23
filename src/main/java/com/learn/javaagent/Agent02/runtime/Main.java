package com.learn.javaagent.Agent02.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.learn.javaagent.Agent02.client.LLMClient;

import java.util.Scanner;

/**
 * Agent02 程序主入口。
 *
 * <p>在 Agent01 基础上演进为多工具模式：</p>
 * <ul>
 *   <li>初始化 LLMClient、AgentLoop（含 TodoManager 与标准工具集）</li>
 *   <li>控制台 REPL 交互，用户输入后调用 AgentLoop.run 执行推理</li>
 *   <li>退出条件：q / exit / 空行 / EOF</li>
 * </ul>
 */
public final class Main {

    /** 青色 ANSI 转义码，用于高亮提示符 */
    private static final String CYN = "\u001B[36m";
    /** 重置 ANSI 样式 */
    private static final String RST = "\u001B[0m";

    private Main() {
    }

    /**
     * 主方法：启动多工具 Agent 交互循环。
     *
     * @param args 命令行参数（未使用）
     * @throws Exception 配置加载或 API 调用异常
     */
    public static void main(String[] args) throws Exception {
        LLMClient llm = new LLMClient();
        AgentLoop loop = new AgentLoop();
        JsonArray history = new JsonArray();

        try (Scanner in = new Scanner(System.in, "UTF-8")) {
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

                JsonObject userMsg = new JsonObject();
                userMsg.addProperty("role", "user");
                userMsg.addProperty("content", line);
                history.add(userMsg);

                loop.run(history, llm);

                JsonObject last = history.get(history.size() - 1).getAsJsonObject();
                if (last.has("content") && !last.get("content").isJsonNull() && last.get("content").isJsonPrimitive()) {
                    System.out.println(last.get("content").getAsString());
                }
                System.out.println();
            }
        }
    }
}
