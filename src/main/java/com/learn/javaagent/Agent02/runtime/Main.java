package com.learn.javaagent.Agent02.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.learn.javaagent.Agent02.client.LLMClient;

import java.util.Scanner;

/**
 * 程序入口：初始化 {@link LLMClient}，控制台 REPL 中调用 {@link AgentLoop#run}。
 *
 * @author 298751
 */
public final class Main {

    private static final String CYN = "\u001B[36m";
    private static final String RST = "\u001B[0m";

    private Main() {
    }

    /**
     * 交互循环直至 q / exit / 空行 / EOF。
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

                JsonObject u = new JsonObject();
                u.addProperty("role", "user");
                u.addProperty("content", line);
                history.add(u);
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
