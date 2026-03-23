package com.learn.javaagent.Agent02.config;

import com.google.gson.JsonArray;
import com.learn.javaagent.Agent02.tools.ToolRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Agent02 配置中心。
 *
 * <p>与 Agent01 类似，增加：</p>
 * <ul>
 *   <li>{@link #tools()} 由 {@link ToolRegistry#openAiTools()} 单一来源生成，保证 API 声明与运行期分发一致</li>
 *   <li>System 提示词包含工具名列表及 todo 使用指引</li>
 * </ul>
 */
public final class AgentConfig {

    /** 配置文件（位于 classpath / {@code src/main/resources}） */
    public static final String CONFIG_RESOURCE = "agent.properties";

    public static final String KEY_API_KEY = "API_KEY";
    public static final String KEY_MODEL_ID = "MODEL_ID";
    public static final String KEY_API_BASE_URL = "API_BASE_URL";
    public static final String KEY_API_AUTH_TOKEN = "API_AUTH_TOKEN";

    /**
     * 未配置 {@link #KEY_API_BASE_URL} 时使用的网关根路径（不含 {@code /chat/completions}）。
     */
    public static final String DEFAULT_API_BASE_URL =
            "https://dashscope.aliyuncs.com/compatible-mode/v1";

    /** Chat Completions：最大生成 token */
    public static final int DEFAULT_MAX_TOKENS = 8000;
    /** Chat Completions：工具选择策略 */
    public static final String DEFAULT_TOOL_CHOICE = "auto";

    private static final JsonArray TOOLS = buildToolDeclarations();

    private AgentConfig() {
    }

    /**
     * 由 {@link ToolRegistry} 组装的 OpenAI 兼容 {@code tools} 数组。
     */
    private static JsonArray buildToolDeclarations() {
        return ToolRegistry.openAiTools();
    }

    /**
     * OpenAI 兼容 {@code tools} 数组（{@code type:function} + {@code parameters}）。
     */
    public static JsonArray tools() {
        return TOOLS;
    }

    /**
     * 默认 system 提示词（含当前工作目录与 {@link ToolRegistry} 中的工具名列表）。
     */
    public static String systemPrompt() {
        String cwd = Paths.get("").toAbsolutePath().normalize().toString();
        return "You are a coding agent at " + cwd
                + ". You may use tools: " + ToolRegistry.toolNames()
                + ". Use the todo tool to plan multi-step tasks. Mark in_progress before starting, completed when done.\n" +
                "Prefer tools over prose";
    }

    /**
     * 从 classpath 与环境变量加载原始键值（环境变量覆盖文件）；并处理 {@code API_BASE_URL} 与 {@code API_AUTH_TOKEN} 互斥。
     */
    public static Map<String, String> load() throws IOException {
        Map<String, String> m = new HashMap<>();
        ClassLoader cl = AgentConfig.class.getClassLoader();
        InputStream in = cl != null ? cl.getResourceAsStream(CONFIG_RESOURCE)
                : ClassLoader.getSystemResourceAsStream(CONFIG_RESOURCE);
        if (in != null) {
            Properties p = new Properties();
            try (InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                p.load(r);
            }
            for (String name : p.stringPropertyNames()) {
                m.put(name, p.getProperty(name));
            }
        }
        m.putAll(System.getenv());
        String base = m.get(KEY_API_BASE_URL);
        if (base != null && !base.trim().isEmpty()) {
            m.remove(KEY_API_AUTH_TOKEN);
        }
        return m;
    }

    /**
     * 从配置 Map 解析 API 根地址，空则用默认值。
     */
    public static String apiBaseUrl(Map<String, String> config) {
        String u = config.get(KEY_API_BASE_URL);
        return (u == null || u.trim().isEmpty()) ? DEFAULT_API_BASE_URL : u.trim();
    }

    /**
     * 校验必填项并封装为一次运行所需配置。
     *
     * @throws IllegalStateException 缺少 {@link #KEY_API_KEY} 或 {@link #KEY_MODEL_ID}
     */
    public static RuntimeConfig loadRuntime() throws IOException {
        Map<String, String> raw = load();
        String apiKey = raw.get(KEY_API_KEY);
        String modelId = raw.get(KEY_MODEL_ID);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("Missing " + KEY_API_KEY);
        }
        if (modelId == null || modelId.trim().isEmpty()) {
            throw new IllegalStateException("Missing " + KEY_MODEL_ID);
        }
        return new RuntimeConfig(
                apiKey.trim(),
                modelId.trim(),
                apiBaseUrl(raw),
                systemPrompt()
        );
    }

    /**
     * 运行期配置快照（由 {@link #loadRuntime()} 生成）。
     */
    public static final class RuntimeConfig {
        private final String apiKey;
        private final String modelId;
        private final String apiBaseUrl;
        private final String systemPrompt;

        RuntimeConfig(String apiKey, String modelId, String apiBaseUrl, String systemPrompt) {
            this.apiKey = apiKey;
            this.modelId = modelId;
            this.apiBaseUrl = apiBaseUrl;
            this.systemPrompt = systemPrompt;
        }

        public String getApiKey() {
            return apiKey;
        }

        public String getModelId() {
            return modelId;
        }

        public String getApiBaseUrl() {
            return apiBaseUrl;
        }

        public String getSystemPrompt() {
            return systemPrompt;
        }
    }
}
