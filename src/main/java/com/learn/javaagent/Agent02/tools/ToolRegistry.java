package com.learn.javaagent.Agent02.tools;

import com.google.gson.JsonArray;
import com.learn.javaagent.Agent02.runtime.TodoManager;
import com.learn.javaagent.Agent02.tools.impl.BashTool;
import com.learn.javaagent.Agent02.tools.impl.EditFileTool;
import com.learn.javaagent.Agent02.tools.impl.ReadFileTool;
import com.learn.javaagent.Agent02.tools.impl.TodoTool;
import com.learn.javaagent.Agent02.tools.impl.WriteFileTool;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 工具注册中心：维护标准工具列表、名称映射、OpenAI 声明与分发。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li><b>声明与执行同源</b>：openAiTools() 由 STANDARD_DECLARATIONS 构建，与 dispatch() 使用的注册表一致</li>
 *   <li><b>TodoTool 双实例</b>：STANDARD_DECLARATIONS 中的 TodoTool 仅用于 Schema；运行期由 withStandardTools(todoManager) 注入会话级 TodoManager</li>
 * </ul>
 */
public final class ToolRegistry {

    /** 标准工具声明列表，用于生成 openAiTools() 及 toolNames() */
    private static final List<Tool> STANDARD_DECLARATIONS = Collections.unmodifiableList(Arrays.asList(
            new BashTool(),
            new ReadFileTool(),
            new WriteFileTool(),
            new EditFileTool(),
            new TodoTool(new TodoManager())  // 仅用于 Schema，运行期由 withStandardTools 注入
    ));

    /** OpenAI 兼容的 tools 数组，供 AgentConfig 和 API 请求使用 */
    private static final JsonArray OPENAI_TOOLS = buildOpenAiTools(STANDARD_DECLARATIONS);

    private final Map<String, Tool> toolsByName;

    /**
     * 使用已有映射构造（传入的 Map 会被复制，避免外部修改）。
     */
    public ToolRegistry(Map<String, Tool> toolsByName) {
        this.toolsByName = new LinkedHashMap<>(Objects.requireNonNull(toolsByName, "toolsByName"));
    }

    /**
     * 空注册表，便于测试或仅注册部分工具。
     */
    public ToolRegistry() {
        this.toolsByName = new LinkedHashMap<>();
    }

    /**
     * 注册或覆盖某个工具。
     */
    public void register(Tool tool) {
        Objects.requireNonNull(tool, "tool");
        String name = tool.name();
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("tool name must be non-empty");
        }
        toolsByName.put(name, tool);
    }

    /**
     * 按工具名分发执行，未注册时返回错误文案。
     *
     * @param name           与 function.name 一致
     * @param argumentsJson  function.arguments 原始 JSON 字符串
     * @return 写入 role: tool 消息的 content
     */
    public String dispatch(String name, String argumentsJson) {
        Tool tool = toolsByName.get(name);
        if (tool == null) {
            return "Error: unsupported tool " + name;
        }
        try {
            return tool.execute(argumentsJson);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * 使用标准工具构建可分发注册表。
     */
    public static ToolRegistry withStandardTools() {
        return withStandardTools(new TodoManager());
    }

    /**
     * 使用标准工具构建可分发注册表，并注入会话级 {@link TodoManager}。
     *
     * @param todoManager 多步任务状态管理器
     */
    public static ToolRegistry withStandardTools(TodoManager todoManager) {
        ToolRegistry r = new ToolRegistry();
        List<Tool> standard = Arrays.asList(
                new BashTool(),
                new ReadFileTool(),
                new WriteFileTool(),
                new EditFileTool(),
                new TodoTool(Objects.requireNonNull(todoManager, "todoManager"))
        );
        for (Tool t : standard) {
            r.register(t);
        }
        return r;
    }

    /**
     * @return OpenAI 兼容 tools 声明数组（由标准工具列表一次性构建）。
     */
    public static JsonArray openAiTools() {
        return OPENAI_TOOLS;
    }

    /**
     * @return 逗号分隔的标准工具名（用于 system prompt 展示）。
     */
    public static String toolNames() {
        StringBuilder names = new StringBuilder();
        for (Tool t : STANDARD_DECLARATIONS) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(t.name());
        }
        return names.toString();
    }

    private static JsonArray buildOpenAiTools(List<Tool> tools) {
        JsonArray arr = new JsonArray();
        for (Tool t : tools) {
            arr.add(t.toOpenAiToolDeclaration());
        }
        return arr;
    }
}
