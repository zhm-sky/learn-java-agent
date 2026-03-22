package com.learn.javaagent.Agent02.tools.impl;

import com.google.gson.JsonObject;
import com.learn.javaagent.Agent02.tools.Tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 对文本文件做一次精确替换：将首次出现的 {@code old_string} 替换为 {@code new_string}。
 *
 * @author 298751
 */
public final class EditFileTool implements Tool {

    @Override
    public String name() {
        return "edit_file";
    }

    @Override
    public String description() {
        return "Apply a single targeted edit: replace the unique first occurrence of old_string with new_string.";
    }

    @Override
    public JsonObject parametersSchema() {
        JsonObject props = new JsonObject();
        props.add("path", Tool.stringProperty("Relative path under working directory"));
        props.add("old_string", Tool.stringProperty(
                "Literal substring to replace (must occur exactly once in file)"));
        props.add("new_string", Tool.stringProperty(
                "Replacement text (may be empty to delete old_string)"));
        return objectSchema(props, "path", "old_string", "new_string");
    }

    @Override
    public String execute(String argumentsJson) {
        JsonObject o = parseObject(argumentsJson);
        String pathStr = str(o, "path");
        String oldString = str(o, "old_string");
        String newString = str(o, "new_string");
        if (oldString.isEmpty()) {
            return "Error: old_string is required";
        }
        try {
            Path path = resolveUnderCwd(pathStr);
            return apply(path, oldString, newString);
        } catch (IllegalArgumentException e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * @param path      已校验位于工作区内的文件路径
     * @param oldString 必须唯一匹配一次片段（首次出现处替换）
     * @param newString 替换后的内容，可为空（表示删除该片段）
     * @return 成功说明或错误描述
     */
    static String apply(Path path, String oldString, String newString) {
        if (oldString == null) {
            return "Error: old_string is null";
        }
        if (!Files.isRegularFile(path)) {
            return "Error: not a regular file or does not exist: " + path;
        }
        try {
            String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            int idx = content.indexOf(oldString);
            if (idx < 0) {
                return "Error: old_string not found in file";
            }
            int second = content.indexOf(oldString, idx + oldString.length());
            if (second >= 0) {
                return "Error: old_string is not unique in file (multiple matches)";
            }
            String next = content.substring(0, idx)
                    + (newString == null ? "" : newString)
                    + content.substring(idx + oldString.length());
            Files.write(path, next.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return "OK: applied single replacement in " + path.getFileName();
        } catch (IOException e) {
            return "Error: " + e.getMessage();
        }
    }
}
