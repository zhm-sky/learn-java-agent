package com.learn.javaagent.Agent02.tools.impl;

import com.google.gson.JsonObject;
import com.learn.javaagent.Agent02.tools.Tool;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 读取工作区内文件的 UTF-8 文本内容。
 *
 * @author 298751
 */
public final class ReadFileTool implements Tool {

    /** 与 {@link BashTool} 类似的输出上限，避免超大文件撑爆上下文 */
    private static final int MAX_CHARS = 50_000;

    @Override
    public String name() {
        return "read_file";
    }

    @Override
    public String description() {
        return "Read file contents (UTF-8 text) under the working directory.";
    }

    @Override
    public JsonObject parametersSchema() {
        JsonObject props = new JsonObject();
        props.add("path", Tool.stringProperty("Relative path under working directory"));
        return objectSchema(props, "path");
    }

    @Override
    public String execute(String argumentsJson) {
        JsonObject o = parseObject(argumentsJson);
        String pathStr = str(o, "path");
        try {
            Path path = resolveUnderCwd(pathStr);
            return read(path);
        } catch (IllegalArgumentException e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * @param path 已校验位于工作区内的文件路径
     * @return 文件内容或错误描述
     */
    static String read(Path path) {
        if (!Files.isRegularFile(path)) {
            return "Error: not a regular file or does not exist: " + path;
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (text.length() > MAX_CHARS) {
                return text.substring(0, MAX_CHARS) + "\n... (truncated, " + text.length() + " chars total)";
            }
            return text.isEmpty() ? "(empty file)" : text;
        } catch (IOException e) {
            return "Error: " + e.getMessage();
        }
    }
}
