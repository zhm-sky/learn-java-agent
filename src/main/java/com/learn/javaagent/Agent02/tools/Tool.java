package com.learn.javaagent.Agent02.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 统一工具契约：每个工具类同时提供名称、参数 schema 与执行逻辑。
 * <p>
 * 新增工具：实现本接口并加入 {@link ToolRegistry} 的标准列表，即可同时参与 API 声明与本地分发。
 * </p>
 *
 * @author 298751
 */
public interface Tool {

    /**
     * @return 与 Chat Completions {@code tools[].function.name} 一致
     */
    String name();

    /**
     * @return 供模型阅读的简短英文说明
     */
    String description();

    /**
     * @return OpenAI JSON Schema 形态的 {@code parameters} 对象（含 {@code type}、{@code properties}、{@code required}）
     */
    JsonObject parametersSchema();

    /**
     * 执行工具逻辑。
     *
     * @param argumentsJson 模型返回的 {@code function.arguments} 原始 JSON 字符串
     * @return 写入对话 {@code role: tool} 的 {@code content} 文本（成功说明或错误信息）
     */
    String execute(String argumentsJson);

    /**
     * 组装为 OpenAI 兼容的单条 {@code tools[]} 元素（{@code type:function}）。
     */
    default JsonObject toOpenAiToolDeclaration() {
        JsonObject fn = new JsonObject();
        fn.addProperty("name", name());
        fn.addProperty("description", description());
        fn.add("parameters", parametersSchema());
        JsonObject outer = new JsonObject();
        outer.addProperty("type", "function");
        outer.add("function", fn);
        return outer;
    }

    /**
     * 构建字符串字段 schema：{@code { "type":"string", "description": ... }}。
     */
    static JsonObject stringProperty(String description) {
        JsonObject p = new JsonObject();
        p.addProperty("type", "string");
        p.addProperty("description", description);
        return p;
    }

    /**
     * 构建 object schema（含 properties 与 required）。
     */
    default JsonObject objectSchema(JsonObject properties, String... requiredKeys) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        JsonArray req = new JsonArray();
        for (String k : requiredKeys) {
            req.add(k);
        }
        schema.add("required", req);
        return schema;
    }

    /**
     * 将参数字符串解析为 JSON 对象；非法或空串时返回空对象。
     */
    default JsonObject parseObject(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.trim().isEmpty()) {
            return new JsonObject();
        }
        try {
            return JsonParser.parseString(argumentsJson).getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    /**
     * 读取字符串属性；缺失或非原始类型时返回空串。
     */
    default String str(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return "";
        }
        JsonElement e = o.get(key);
        return e.isJsonPrimitive() ? e.getAsString() : e.toString();
    }

    /**
     * @param userPath 相对工作目录的路径片段
     * @return 规范化后的绝对路径
     * @throws IllegalArgumentException 路径为空或解析后不在工作区根下
     */
    default Path resolveUnderCwd(String userPath) {
        if (userPath == null || userPath.trim().isEmpty()) {
            throw new IllegalArgumentException("path is empty");
        }
        Path base = Paths.get("").toAbsolutePath().normalize();
        Path resolved = base.resolve(userPath.trim()).normalize();
        if (!resolved.startsWith(base)) {
            throw new IllegalArgumentException("path escapes working directory");
        }
        return resolved;
    }
}
