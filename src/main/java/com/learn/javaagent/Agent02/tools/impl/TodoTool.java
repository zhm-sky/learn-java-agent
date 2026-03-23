package com.learn.javaagent.Agent02.tools.impl;

import com.google.gson.JsonObject;
import com.learn.javaagent.Agent02.runtime.TodoManager;
import com.learn.javaagent.Agent02.tools.Tool;

import java.util.Locale;
import java.util.Objects;

/**
 * 任务计划工具：add/update/start/complete/set_status/list。
 *
 * <p>与 {@link TodoManager} 绑定，支持多步任务的创建、状态流转与快照查看。
 * 会话级：每个 AgentLoop 一个 TodoManager，保证多轮对话中计划状态一致。</p>
 */
public final class TodoTool implements Tool {

    private final TodoManager todoManager;

    /**
     * 绑定会话级 TodoManager。
     *
     * @param todoManager 不能为 null，通常由 AgentLoop 创建
     */
    public TodoTool(TodoManager todoManager) {
        this.todoManager = Objects.requireNonNull(todoManager, "todoManager");
    }

    @Override
    public String name() {
        return "todo";
    }

    @Override
    public String description() {
        return "Manage a step-by-step plan with stateful todo items.";
    }

    @Override
    public JsonObject parametersSchema() {
        JsonObject props = new JsonObject();
        props.add("action", Tool.stringProperty("One of: add, update, start, complete, set_status, list"));
        props.add("id", Tool.stringProperty("Todo item id, required for update/start/complete/set_status"));
        props.add("title", Tool.stringProperty("Todo title, required for add or update"));
        props.add("status", Tool.stringProperty("Status for set_status: pending/in_progress/completed/cancelled"));
        return objectSchema(props, "action");
    }

    @Override
    public String execute(String argumentsJson) {
        JsonObject o = parseObject(argumentsJson);
        String action = str(o, "action").trim().toLowerCase(Locale.ROOT);
        try {
            switch (action) {
                case "add":
                    return ok("added", todoManager.add(str(o, "title")).getId());
                case "update":
                    return ok("updated", todoManager.updateTitle(str(o, "id"), str(o, "title")).getId());
                case "start":
                    return ok("started", todoManager.start(str(o, "id")).getId());
                case "complete":
                    return ok("completed", todoManager.complete(str(o, "id")).getId());
                case "set_status":
                    return ok("status_updated", todoManager
                            .setStatus(str(o, "id"), TodoManager.Status.from(str(o, "status"))).getId());
                case "list":
                    return listOnly();
                default:
                    return error("unsupported action: " + action);
            }
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    private String ok(String action, String id) {
        JsonObject res = new JsonObject();
        res.addProperty("ok", true);
        res.addProperty("action", action);
        res.addProperty("id", id);
        res.add("todo", todoManager.snapshot());
        return res.toString();
    }

    private String listOnly() {
        JsonObject res = new JsonObject();
        res.addProperty("ok", true);
        res.addProperty("action", "list");
        res.add("todo", todoManager.snapshot());
        return res.toString();
    }

    private static String error(String message) {
        JsonObject res = new JsonObject();
        res.addProperty("ok", false);
        res.addProperty("error", message == null ? "unknown error" : message);
        return res.toString();
    }
}
