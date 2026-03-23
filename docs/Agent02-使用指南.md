# Agent02 使用指南

## 一、前置配置

### 1. 配置文件

在 `src/main/resources/agent.properties` 中配置（或使用环境变量）：

| 键 | 说明 | 示例 |
|----|------|------|
| API_KEY | 大模型 API 密钥 | sk-xxx |
| MODEL_ID | 模型 ID | qwen-max-latest |
| API_BASE_URL | API 网关地址（可选） | https://dashscope.aliyuncs.com/compatible-mode/v1 |

> 环境变量会覆盖文件配置。**请勿将真实 API_KEY 提交到公开仓库。**

### 2. 编译项目

```bash
mvn compile
```

---

## 二、启动方式

### 方式一：Web 界面（推荐）

```bash
mvn exec:java -DmainClass="com.learn.javaagent.Agent02.web.WebMain"
```

启动后浏览器访问 **http://localhost:8080**，使用图形界面聊天。

### 方式二：控制台 REPL

```bash
mvn exec:java
```

默认运行 Agent02 控制台版。若需运行 Agent01：
```bash
mvn exec:java -Dexec.mainClass="com.learn.javaagent.Agent01.Main"
```

### 方式二：IDEA

1. 打开 `src/main/java/com/learn/javaagent/Agent02/runtime/Main.java`
2. 右键 `Main` 类 → **Run 'Main.main()'**

### 方式三：命令行（需先编译）

```bash
mvn compile
java -cp "target/classes:$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout)" \
  com.learn.javaagent.Agent02.runtime.Main
```

---

## 三、使用方式

启动后进入 REPL 交互模式，提示符为 `用户输入 >>`：

```
用户输入 >> 帮我用 Python 写一个贪吃蛇小游戏
```

**退出**：输入 `q`、`exit` 或空行后回车。

### 建议对话流程（多步任务）

1. **先拆解任务**：模型会调用 `todo` 工具列出步骤
2. **按步骤执行**：模型依次调用 `write_file`、`bash` 等
3. **完成标记**：模型通过 `todo complete` 标记完成

**示例**：
```
用户输入 >> 创建一个 hello.txt 文件，内容写 "Hello Agent02"
```
模型会调用 `write_file` 工具完成任务。

```
用户输入 >> 帮我做一个简单的计算器程序，需要拆分成多个步骤
```
模型会先调用 `todo add` 拆解任务，再逐步执行。

---

## 四、可用工具

| 工具 | 用途 |
|------|------|
| bash | 执行 shell 命令 |
| read_file | 读取文件内容 |
| write_file | 创建或覆盖文件 |
| edit_file | 替换文件中的文本片段 |
| todo | 任务计划：add / start / complete / list 等 |

---

## 五、智能提醒

若连续 **3 轮**未调用 `todo` 工具，系统会自动注入提醒，引导模型更新任务计划。控制台会输出 `[提醒内容]` 和 `[提醒注入]`。

---

## 六、常见问题

**Q：API 报 401 / 403？**  
A：检查 API_KEY 是否正确，或改用环境变量：`export API_KEY=sk-xxx`。

**Q：找不到 agent.properties？**  
A：确保配置文件在 `src/main/resources/agent.properties`，编译后会出现在 classpath 中。

**Q：与 Agent01 的区别？**  
A：Agent02 支持 5 个工具（含 todo 任务规划），且支持多轮未调用 todo 时的自动提醒。
