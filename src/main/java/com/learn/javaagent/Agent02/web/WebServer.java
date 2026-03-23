package com.learn.javaagent.Agent02.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.learn.javaagent.Agent02.client.LLMClient;
import com.learn.javaagent.Agent02.runtime.AgentLoop;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Web 服务：提供 HTTP 接口与静态页面，替代控制台 REPL。
 *
 * <p>访问 http://localhost:8080 使用聊天界面。</p>
 */
public final class WebServer {

    private static final int PORT = 8080;
    private static final String INDEX_HTML = "/index.html";
    private static final String API_CHAT = "/api/chat";

    private final LLMClient llm;
    private final AgentLoop agentLoop;
    private final JsonArray history = new JsonArray();

    public WebServer() throws IOException {
        this.llm = new LLMClient();
        this.agentLoop = new AgentLoop();
    }

    public void start() throws IOException {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new InetSocketAddress(PORT), 0);

        server.createContext("/", this::handleRoot);
        server.createContext(API_CHAT, this::handleChat);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();

        System.out.println("Agent02 Web 已启动: http://localhost:" + PORT);
        System.out.println("按 Ctrl+C 停止");
    }

    private void handleRoot(com.sun.net.httpserver.HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/") || path.equals(INDEX_HTML)) {
            serveResource(ex, "web/index.html", "text/html; charset=UTF-8");
        } else if (path.equals("/style.css")) {
            serveResource(ex, "web/style.css", "text/css; charset=UTF-8");
        } else if (path.equals("/app.js")) {
            serveResource(ex, "web/app.js", "application/javascript; charset=UTF-8");
        } else {
            sendResponse(ex, 404, "Not Found", "text/plain");
        }
    }

    private void serveResource(com.sun.net.httpserver.HttpExchange ex, String resourcePath, String contentType) throws IOException {
        InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (in == null) {
            sendResponse(ex, 404, "Not Found", "text/plain");
            return;
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) != -1) {
            buf.write(chunk, 0, n);
        }
        byte[] body = buf.toByteArray();
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(200, body.length);
        ex.getResponseBody().write(body);
        ex.close();
    }

    private void handleChat(com.sun.net.httpserver.HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            sendResponse(ex, 405, "Method Not Allowed", "text/plain");
            return;
        }

        String body = readRequestBody(ex);
        JsonObject req = JsonParser.parseString(body).getAsJsonObject();
        String message = req.has("message") ? req.get("message").getAsString() : "";

        if (message.trim().isEmpty()) {
            sendJson(ex, 400, jsonObject("error", "message 不能为空"));
            return;
        }

        try {
            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");
            userMsg.addProperty("content", message.trim());
            history.add(userMsg);

            agentLoop.run(history, llm);

            JsonObject last = history.get(history.size() - 1).getAsJsonObject();
            String reply = "";
            if (last.has("content") && !last.get("content").isJsonNull() && last.get("content").isJsonPrimitive()) {
                reply = last.get("content").getAsString();
            }

            sendJson(ex, 200, jsonObject("reply", reply));
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(ex, 500, jsonObject("error", e.getMessage()));
        }
    }

    private static String readRequestBody(com.sun.net.httpserver.HttpExchange ex) throws IOException {
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private static JsonObject jsonObject(String key, String value) {
        JsonObject o = new JsonObject();
        o.addProperty(key, value);
        return o;
    }

    private void sendJson(com.sun.net.httpserver.HttpExchange ex, int code, JsonObject body) throws IOException {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    private void sendResponse(com.sun.net.httpserver.HttpExchange ex, int code, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }
}
