package com.learn.javaagent.Agent02.web;

import java.awt.*;
import java.net.URI;

/**
 * Agent02 Web 模式入口：启动本地 HTTP 服务，通过浏览器访问聊天界面。
 *
 * <p>启动后访问 http://localhost:8080，若支持则自动打开浏览器。</p>
 */
public final class WebMain {

    public static void main(String[] args) throws Exception {
        WebServer server = new WebServer();
        server.start();

        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI("http://localhost:8080"));
            }
        } catch (Exception ignored) {
        }
    }
}
