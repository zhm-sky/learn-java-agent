package com.learn.javaagent.Agent02.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 管理多步任务计划：维护任务项与状态，并强制“同一时间只能有一个 in_progress”。
 *
 * @author 298751
 */
public final class TodoManager {

    /** 任务状态：待办 → 进行中 → 完成 */
    public enum Status {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        CANCELLED;

        /**
         * 兼容模型输入的状态字符串（忽略大小写）。
         *
         * @param value 外部输入
         * @return 对应状态
         */
        public static Status from(String value) {
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException("status is empty");
            }
            return Status.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
    }

    /**
     * 计划中的单条任务项。
     */
    public static final class TodoItem {
        private final String id;
        private String title;
        private Status status;

        TodoItem(String id, String title) {
            this.id = id;
            this.title = title;
            this.status = Status.PENDING;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public Status getStatus() {
            return status;
        }
    }

    private final List<TodoItem> items = new ArrayList<>();
    private int nextId = 1;

    /**
     * 新增待办任务。
     *
     * @param title 任务标题
     * @return 新任务快照
     */
    public synchronized TodoItem add(String title) {
        String normalized = normalizeTitle(title);
        TodoItem item = new TodoItem("task-" + nextId++, normalized);
        items.add(item);
        return item;
    }

    /**
     * 更新任务标题，不修改状态。
     *
     * @param id 任务 id
     * @param title 新标题
     * @return 更新后任务
     */
    public synchronized TodoItem updateTitle(String id, String title) {
        TodoItem item = findById(id);
        item.title = normalizeTitle(title);
        return item;
    }

    /**
     * 将指定任务置为进行中；若已有其它进行中任务则拒绝。
     *
     * @param id 任务 id
     * @return 更新后任务
     */
    public synchronized TodoItem start(String id) {
        TodoItem target = findById(id);
        TodoItem inProgress = findInProgress();
        if (inProgress != null && !inProgress.id.equals(target.id)) {
            throw new IllegalStateException("another task already in_progress: " + inProgress.id);
        }
        target.status = Status.IN_PROGRESS;
        return target;
    }

    /**
     * 将指定任务标记为完成；要求该任务当前是进行中，防止跳步。
     *
     * @param id 任务 id
     * @return 更新后任务
     */
    public synchronized TodoItem complete(String id) {
        TodoItem target = findById(id);
        if (target.status != Status.IN_PROGRESS) {
            throw new IllegalStateException("task must be in_progress before complete: " + target.id);
        }
        target.status = Status.COMPLETED;
        return target;
    }

    /**
     * 直接设置任务状态（用于兼容通用更新动作），并校验唯一进行中约束。
     *
     * @param id 任务 id
     * @param status 新状态
     * @return 更新后任务
     */
    public synchronized TodoItem setStatus(String id, Status status) {
        TodoItem target = findById(id);
        if (status == Status.IN_PROGRESS) {
            TodoItem inProgress = findInProgress();
            if (inProgress != null && !inProgress.id.equals(target.id)) {
                throw new IllegalStateException("another task already in_progress: " + inProgress.id);
            }
        }
        target.status = status;
        return target;
    }

    /**
     * @return 当前计划的 JSON 快照（含 items 与计数信息）
     */
    public synchronized JsonObject snapshot() {
        JsonObject out = new JsonObject();
        JsonArray arr = new JsonArray();
        int pending = 0;
        int inProgress = 0;
        int completed = 0;
        int cancelled = 0;
        for (TodoItem item : items) {
            JsonObject o = new JsonObject();
            o.addProperty("id", item.id);
            o.addProperty("title", item.title);
            o.addProperty("status", item.status.name().toLowerCase(Locale.ROOT));
            arr.add(o);
            switch (item.status) {
                case PENDING:
                    pending++;
                    break;
                case IN_PROGRESS:
                    inProgress++;
                    break;
                case COMPLETED:
                    completed++;
                    break;
                case CANCELLED:
                    cancelled++;
                    break;
                default:
                    break;
            }
        }
        out.add("items", arr);
        out.addProperty("total", items.size());
        out.addProperty("pending", pending);
        out.addProperty("in_progress", inProgress);
        out.addProperty("completed", completed);
        out.addProperty("cancelled", cancelled);
        out.addProperty("summary", summaryText());
        return out;
    }

    /**
     * @return 适合 nag 提醒的简短摘要
     */
    public synchronized String summaryText() {
        TodoItem inProgress = findInProgress();
        String current = inProgress == null ? "none" : inProgress.id + " (" + inProgress.title + ")";
        int pending = 0;
        int completed = 0;
        for (TodoItem item : items) {
            if (item.status == Status.PENDING) {
                pending++;
            } else if (item.status == Status.COMPLETED) {
                completed++;
            }
        }
        return "current_in_progress=" + current + ", pending=" + pending + ", completed=" + completed;
    }

    private static String normalizeTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("title is empty");
        }
        return title.trim();
    }

    private TodoItem findById(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("id is empty");
        }
        for (TodoItem item : items) {
            if (item.id.equals(id.trim())) {
                return item;
            }
        }
        throw new IllegalArgumentException("todo item not found: " + id);
    }

    private TodoItem findInProgress() {
        for (TodoItem item : items) {
            if (item.status == Status.IN_PROGRESS) {
                return item;
            }
        }
        return null;
    }
}
