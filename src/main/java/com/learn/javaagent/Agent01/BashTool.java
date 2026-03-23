package com.learn.javaagent.Agent01;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * Bash 命令执行工具。
 *
 * <p>在本机当前工作目录下执行 shell 命令，并返回合并后的 stdout+stderr。</p>
 *
 * <p>安全措施：</p>
 * <ul>
 *   <li>黑名单拦截危险命令（如 rm -rf /、sudo、shutdown 等）</li>
 *   <li>执行超时 120 秒，超时则强制终止进程</li>
 *   <li>输出长度限制 50_000 字符，防止内存溢出</li>
 * </ul>
 *
 * <p>跨平台：Windows 使用 cmd.exe，Unix 使用 /bin/sh。</p>
 */
final class BashTool {

    /** 黑名单关键词，包含任意一项则拒绝执行 */
    private static final String[] BLOCKED = {"rm -rf /", "sudo", "shutdown", "reboot", "> /dev/"};
    /** 最大执行时间（秒） */
    private static final int TIMEOUT_SECONDS = 120;
    /** 输出最大长度，超出则截断 */
    private static final int MAX_OUTPUT_LENGTH = 50_000;

    private BashTool() {
    }

    /**
     * 执行 shell 命令并返回输出。
     *
     * @param command 要执行的完整命令字符串
     * @return 命令输出，空输出返回 "(no output)"，异常或拦截时返回 "Error: ..."
     */
    static String run(String command) {
        // 安全检查：拦截黑名单中的危险命令
        for (String blocked : BLOCKED) {
            if (command.contains(blocked)) {
                return "Error: Dangerous command blocked";
            }
        }

        // 根据操作系统选择 shell
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        ProcessBuilder pb = isWindows
                ? new ProcessBuilder("cmd.exe", "/c", command)
                : new ProcessBuilder("/bin/sh", "-c", command);

        // 工作目录设为当前目录
        pb.directory(Paths.get("").toAbsolutePath().normalize().toFile());
        // 将 stderr 合并到 stdout，便于统一读取
        pb.redirectErrorStream(true);

        try {
            Process p = pb.start();
            boolean finished = p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return "Error: Timeout (" + TIMEOUT_SECONDS + "s)";
            }

            String out = readAll(p.getInputStream()).trim();
            if (out.isEmpty()) {
                return "(no output)";
            }
            return out.length() > MAX_OUTPUT_LENGTH ? out.substring(0, MAX_OUTPUT_LENGTH) : out;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * 将输入流完整读取为 UTF-8 字符串。
     */
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
