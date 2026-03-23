package com.learn.javaagent.Agent01;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * LLM 访问客户端，封装与 Chat Completions API 的通信。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>从 AgentConfig 加载 API 地址、密钥、模型、system 提示词等配置</li>
 *   <li>组装请求体：system 消息 + 多轮对话 + tools + tool_choice + max_tokens</li>
 *   <li>发起 HTTP POST 请求并解析响应</li>
 * </ul>
 *
 * <p>兼容 OpenAI API 格式，可与阿里云通义、OpenAI、本地部署模型等对接。</p>
 */
public final class LLMClient {

    private static final Gson GSON = new Gson();
    /** 连接超时时间（毫秒） */
    private static final int CONNECT_TIMEOUT_MS = 30_000;
    /** 读取超时时间（毫秒），LLM 生成可能较慢故设较长 */
    private static final int READ_TIMEOUT_MS = 600_000;

    private final AgentConfig.RuntimeConfig runtime;

    /**
     * 默认构造：从 agent.properties 或环境变量加载配置。
     *
     * @throws IOException            配置文件读取失败
     * @throws IllegalStateException 缺少必填项 API_KEY 或 MODEL_ID
     */
    public LLMClient() throws IOException {
        this(AgentConfig.loadRuntime());
    }

    /**
     * 使用已有配置构造（便于单元测试或外部注入配置）。
     *
     * @param runtime 运行期配置，不能为 null
     */
    public LLMClient(AgentConfig.RuntimeConfig runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    /**
     * 返回 bash 工具在 tools 定义中的名称，供 ToolExecutor 识别并分发。
     */
    public String bashToolName() {
        return AgentConfig.TOOL_NAME_BASH;
    }

    /**
     * 发送 Chat Completions 请求。
     *
     * <p>组装完整请求体：</p>
     * <ul>
     *   <li>messages: [system] + conversationMessages</li>
     *   <li>model: 配置的模型 ID</li>
     *   <li>tools: bash 工具定义</li>
     *   <li>tool_choice: "auto"（由模型决定是否调用工具）</li>
     *   <li>max_tokens: 单次生成上限</li>
     * </ul>
     *
     * @param conversationMessages 多轮对话消息（不含 system）
     * @return 服务端返回的 JSON 字符串
     */
    public String completeChat(JsonArray conversationMessages) throws IOException {
        JsonArray messages = new JsonArray();

        // 插入 system 消息
        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content", runtime.getSystemPrompt());
        messages.add(sys);

        // 追加用户传入的对话历史
        for (JsonElement e : conversationMessages) {
            messages.add(e);
        }

        JsonObject body = new JsonObject();
        body.addProperty("model", runtime.getModelId());
        body.add("messages", messages);
        body.add("tools", AgentConfig.tools());
        body.addProperty("tool_choice", AgentConfig.DEFAULT_TOOL_CHOICE);
        body.addProperty("max_tokens", AgentConfig.DEFAULT_MAX_TOKENS);

        return postChatCompletions(body);
    }

    /**
     * 内部方法：将请求体序列化并 POST 到 API。
     */
    private String postChatCompletions(JsonObject requestBody) throws IOException {
        return postJson(runtime.getApiBaseUrl(), runtime.getApiKey(), GSON.toJson(requestBody));
    }

    /**
     * 发起 JSON POST 请求。
     *
     * @param baseUrl     API 根地址（如 https://api.openai.com/v1），会自动拼接 /chat/completions
     * @param bearerToken Bearer 鉴权令牌
     * @param jsonBody    请求体 JSON 字符串
     * @return 响应体字符串
     */
    private static String postJson(String baseUrl, String bearerToken, String jsonBody) throws IOException {
        String url = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setDoOutput(true);

        try (java.io.OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String text = readFully(stream);
        if (code != 200) {
            throw new IllegalStateException("HTTP " + code + ": " + text);
        }
        return text;
    }

    /**
     * 将输入流完整读取为 UTF-8 字符串。
     */
    private static String readFully(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = stream.read(chunk)) != -1) {
            buf.write(chunk, 0, n);
        }
        return new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }
}
