package com.learn.javaagent.Agent02.tools.impl;

import com.google.gson.JsonObject;
import com.learn.javaagent.Agent02.tools.Tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 在工作区内创建或覆盖文件（UTF-8），必要时自动创建父目录。
 *
 * <p>路径限制在工作区根下；content 允许空字符串。</p>
 */
public final class WriteFileTool implements Tool {

    @Override
    public String name() {
        return "write_file";
    }

    @Override
    public String description() {
        return "Create or overwrite a file (UTF-8) under the working directory.";
    }

    @Override
    public JsonObject parametersSchema() {
        JsonObject props = new JsonObject();
        props.add("path", Tool.stringProperty("Relative path under working directory"));
        props.add("content", Tool.stringProperty("Full file content to write"));
        return objectSchema(props, "path", "content");
    }

    @Override
    public String execute(String argumentsJson) {
        JsonObject o = parseObject(argumentsJson);
        String pathStr = str(o, "path");
        String content = str(o, "content");
        if (o.has("content") && !o.get("content").isJsonNull() && !o.get("content").isJsonPrimitive()) {
            return "Error: content must be a string";
        }
        try {
            Path path = resolveUnderCwd(pathStr);
            return write(path, content);
        } catch (IllegalArgumentException e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * @param path    已校验位于工作区内的目标路径
     * @param content 写入的完整文本（允许为空字符串）
     * @return 成功摘要或错误描述
     */
    static String write(Path path, String content) {
        String body = content == null ? "" : content;
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(path, body.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return "OK: wrote " + body.length() + " characters to " + path.getFileName();
        } catch (IOException e) {
            return "Error: " + e.getMessage();
        }
    }
}
