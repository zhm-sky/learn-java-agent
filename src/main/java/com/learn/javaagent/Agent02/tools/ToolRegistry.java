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
 * 工具注册中心：维护标准工具列表、名称映射、OpenAI tools 声明与分发能力。
 *
 * @author 298751
 */
public final class ToolRegistry {

    private static final List<Tool> STANDARD_DECLARATIONS = Collections.unmodifiableList(Arrays.asList(
            new BashTool(),
            new ReadFileTool(),
            new WriteFileTool(),
            new EditFileTool(),
            new TodoTool(new TodoManager())
    ));

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
     * 按工具名执行；未注册时返回固定错误文案。
     *
     * @param name            工具名
     * @param argumentsJson   {@code function.arguments}
     * @return 供 tool 消息使用的 content
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
