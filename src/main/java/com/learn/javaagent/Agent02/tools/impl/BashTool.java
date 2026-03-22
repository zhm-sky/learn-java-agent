package com.learn.javaagent.Agent02.tools.impl;

import com.google.gson.JsonObject;
import com.learn.javaagent.Agent02.tools.Tool;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * 在本机工作目录执行 shell 命令并返回合并后的标准输出（含简单安全拦截与超时）。
 *
 * @author 298751
 */
public final class BashTool implements Tool {

    private static final String[] BLOCKED = {"rm -rf /", "sudo", "shutdown", "reboot", "> /dev/"};
    private static final String YEL = "\u001B[33m";
    private static final String RST = "\u001B[0m";

    @Override
    public String name() {
        return "bash";
    }

    @Override
    public String description() {
        return "Execute shell commands in the project working directory.";
    }

    @Override
    public JsonObject parametersSchema() {
        JsonObject props = new JsonObject();
        props.add("command", Tool.stringProperty("Shell command to run"));
        return objectSchema(props, "command");
    }

    @Override
    public String execute(String argumentsJson) {
        JsonObject o = parseObject(argumentsJson);
        String cmd = str(o, "command");
        System.out.println(YEL + "$ " + cmd + RST);
        return run(cmd);
    }

    static String run(String command) {
        for (String b : BLOCKED) {
            if (command.contains(b)) {
                return "Error: Dangerous command blocked";
            }
        }
        boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
        ProcessBuilder pb = win
                ? new ProcessBuilder("cmd.exe", "/c", command)
                : new ProcessBuilder("/bin/sh", "-c", command);
        pb.directory(Paths.get("").toAbsolutePath().normalize().toFile());
        pb.redirectErrorStream(true);
        try {
            Process p = pb.start();
            if (!p.waitFor(120, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return "Error: Timeout (120s)";
            }
            String out = readAll(p.getInputStream()).trim();
            if (out.isEmpty()) {
                return "(no output)";
            }
            return out.length() > 50_000 ? out.substring(0, 50_000) : out;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private static String readAll(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = stream.read(chunk)) != -1) {
            buf.write(chunk, 0, n);
        }
        return new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }
}
