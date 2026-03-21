package com.learn.javaagent.Agent01;

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
final class BashTool {

    private static final String[] BLOCKED = {"rm -rf /", "sudo", "shutdown", "reboot", "> /dev/"};

    private BashTool() {
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
