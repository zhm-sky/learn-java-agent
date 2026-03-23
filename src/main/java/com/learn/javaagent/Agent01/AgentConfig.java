package com.learn.javaagent.Agent01;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Agent 配置中心。
 *
 * <p>集中管理：</p>
 * <ul>
 *   <li>配置键常量（API_KEY、MODEL_ID、API_BASE_URL 等）</li>
 *   <li>默认值与工具定义（bash）</li>
 *   <li>从 agent.properties 或环境变量加载配置（环境变量优先）</li>
 *   <li>System 提示词、RuntimeConfig 封装</li>
 * </ul>
 */
public final class AgentConfig {

    /** 配置文件路径，位于 classpath（如 src/main/resources/agent.properties） */
    public static final String CONFIG_RESOURCE = "agent.properties";

    public static final String KEY_API_KEY = "API_KEY";
    public static final String KEY_MODEL_ID = "MODEL_ID";
    public static final String KEY_API_BASE_URL = "API_BASE_URL";
    public static final String KEY_API_AUTH_TOKEN = "API_AUTH_TOKEN";

    /**
     * 默认 API 根地址（阿里云通义兼容模式）。
     * 未配置 API_BASE_URL 时使用，不包含 /chat/completions 路径。
     */
    public static final String DEFAULT_API_BASE_URL =
            "https://dashscope.aliyuncs.com/compatible-mode/v1";

    /** Chat Completions 单次最大生成 token 数 */
    public static final int DEFAULT_MAX_TOKENS = 8000;
    /** 工具选择策略："auto" 表示由模型决定是否调用工具 */
    public static final String DEFAULT_TOOL_CHOICE = "auto";
    /** bash 工具名，须与 tools() 中的 function.name 一致 */
    public static final String TOOL_NAME_BASH = "bash";

    /** OpenAI 兼容的 tools 定义：声明 bash 工具及其参数 schema */
    private static final JsonArray TOOLS = JsonParser.parseString(
            "[{\"type\":\"function\",\"function\":{"
                    + "\"name\":\"bash\",\"description\":\"Run a shell command.\","
                    + "\"parameters\":{\"type\":\"object\","
                    + "\"properties\":{\"command\":{\"type\":\"string\"}},\"required\":[\"command\"]}}}]"
    ).getAsJsonArray();

    private AgentConfig() {
    }

    /**
     * 返回 Chat Completions 请求所需的 tools 数组。
     */
    public static JsonArray tools() {
        return TOOLS;
    }

    /**
     * 生成默认 system 提示词，包含当前工作目录信息。
     */
    public static String systemPrompt() {
        String cwd = Paths.get("").toAbsolutePath().normalize().toString();
        return "You are a coding agent at " + cwd + ". Use bash to solve tasks. Act, don't explain.";
    }

    /**
     * 加载原始配置：先读 agent.properties，再被环境变量覆盖。
     *
     * <p>当配置了 API_BASE_URL（自定义网关）时，会移除 API_AUTH_TOKEN，避免鉴权方式冲突。</p>
     *
     * @return 键值对 Map，键为大写配置名
     */
    public static Map<String, String> load() throws IOException {
        Map<String, String> m = new HashMap<>();

        // 从 classpath 读取 agent.properties
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

        // 环境变量覆盖文件配置
        m.putAll(System.getenv());

        // 使用自定义 API 地址时，移除 API_AUTH_TOKEN（与 API_KEY 互斥）
        String base = m.get(KEY_API_BASE_URL);
        if (base != null && !base.trim().isEmpty()) {
            m.remove(KEY_API_AUTH_TOKEN);
        }
        return m;
    }

    /**
     * 从配置 Map 中解析 API 根地址，空则用默认值。
     */
    public static String apiBaseUrl(Map<String, String> config) {
        String u = config.get(KEY_API_BASE_URL);
        return (u == null || u.trim().isEmpty()) ? DEFAULT_API_BASE_URL : u.trim();
    }

    /**
     * 加载并校验配置，封装为 RuntimeConfig。
     *
     * @return 运行期配置快照
     * @throws IllegalStateException 缺少 API_KEY 或 MODEL_ID
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
     * 运行期配置快照，由 loadRuntime() 生成，供 LLMClient 使用。
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
