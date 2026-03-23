package com.learn.javaagent.Agent02.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.learn.javaagent.Agent02.client.LLMClient;

import java.util.Scanner;

/**
 * Agent02 程序主入口。
 *
 * <p>多工具 Agent：bash、read_file、write_file、edit_file、todo。支持任务规划与智能提醒。</p>
 *
 * <p>退出：输入 q / exit / 空行 / EOF。</p>
 */
public final class Main {

    /** 青色 ANSI，高亮提示符 */
    private static final String CYN = "\u001B[36m";
    /** 重置 ANSI */
    private static final String RST = "\u001B[0m";

    private Main() {
    }

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
