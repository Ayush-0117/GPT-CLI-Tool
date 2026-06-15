# GPT CLI Tool 🤖

A powerful, multi-provider AI chat assistant that runs directly in your terminal. Supports **OpenAI**, **Google Gemini**, **Grok**, and **DeepSeek** — switchable on the fly.

![Java](https://img.shields.io/badge/Java-21+-blue?logo=openjdk)
![Maven](https://img.shields.io/badge/Maven-3.9+-C71A36?logo=apachemaven)
![License](https://img.shields.io/badge/License-MIT-green)

---

## ✨ Features

| Feature | Description |
|---|---|
| 🔄 **Multi-Provider** | Switch between OpenAI, Gemini, Grok, and DeepSeek mid-session |
| 🌊 **Streaming Responses** | Real-time token-by-token output with animated spinner |
| 📁 **File Context** | Add local files/directories as context for the AI |
| 🖼️ **Image Support** | Attach images to your prompts (Gemini & OpenAI) |
| 🔍 **Deep Research** | Scrape Google Search results and feed them to the AI |
| 💻 **Shell Execution** | AI suggests commands → you approve → it runs them natively |
| 💾 **Session History** | Conversations saved to MongoDB for later review |
| 🎨 **Rich Terminal UI** | ANSI colors, markdown rendering, styled boxes |
| 🧠 **OS-Aware** | Automatically detects OS, user, and time for context |

---

## 🚀 Quick Start

### Prerequisites

- **Java 21+** — [Download](https://adoptium.net)
- **Maven 3.9+** — [Download](https://maven.apache.org/install.html)
- **MongoDB** *(optional)* — for session history persistence

### Installation

**Linux / macOS:**
```bash
git clone https://github.com/YOUR_USERNAME/ChatGPT_CLI_Tool.git
cd ChatGPT_CLI_Tool
./install.sh
```

**Windows (PowerShell):**
```powershell
git clone https://github.com/YOUR_USERNAME/ChatGPT_CLI_Tool.git
cd ChatGPT_CLI_Tool
.\install.ps1
```

After installation, type `gpt` in any terminal to launch.

### Manual Setup

If you prefer not to use the installer:

```bash
# 1. Build the project
mvn clean package -DskipTests

# 2. Run directly
java -jar target/gpt-cli-tool-1.0-SNAPSHOT.jar

# 3. Or use the launcher script
./gpt        # Linux / macOS
gpt.bat      # Windows
```

---

## ⚙️ Configuration

1. Copy the example environment file:
   ```bash
   cp .env.example .env
   ```

2. Edit `.env` with your API keys:
   ```env
   # At least one API key is required
   GEMINI_API_KEY=your_gemini_key_here
   OPENAI_API_KEY=your_openai_key_here
   GROK_API_KEY=your_grok_key_here
   DEEPSEEK_API_KEY=your_deepseek_key_here

   # Active provider (openai | gemini | grok | deepseek)
   ACTIVE_PROVIDER=gemini

   # Optional: override the default model
   # MODEL=gemini-2.5-flash

   # Optional: MongoDB for session history
   # MONGO_URI=mongodb://localhost:27017

   # Optional: Google Custom Search (for :deep command)
   # GOOGLE_SEARCH_API_KEY=your_key
   # GOOGLE_SEARCH_CX=your_cx_id
   ```

> **Tip:** You can also set these as system environment variables instead of using a `.env` file. System env vars take priority.

---

## 📖 Usage

### Commands

| Command | Action |
|---|---|
| `:help` | Show available commands |
| `:exit` | Save session and exit |
| `:clear` | Clear conversation context |
| `:provider <name>` | Switch AI provider (openai, gemini, grok, deepseek) |
| `:history` | List past sessions (requires MongoDB) |
| `:add <path>` | Add a file or directory to the AI's context |
| `:image <path>` | Attach an image to your next message |
| `:deep <query>` | Perform deep web research on a topic |
| `!<command>` | Execute a shell command directly |

### Examples

```
➜ You: What is the time complexity of quicksort?

➜ You: :add src/main/java/com/gptcli/CLI.java
✔ Added CLI.java to context.

➜ You: Can you review this file for bugs?

➜ You: :provider openai
✔ Switched to provider: openai

➜ You: :deep latest developments in quantum computing

➜ You: !ls -la
```

---

## 🏗️ Architecture

```
com.gptcli/
├── Main.java              # Entry point
├── CLI.java               # Interactive loop, command dispatch
├── Config.java            # .env + env var configuration
├── CommandRunner.java     # Shell command execution
├── ContextManager.java    # File/directory context loading
├── FileManager.java       # File read/write operations
├── MongoStorage.java      # MongoDB session persistence
├── model/
│   ├── Message.java       # Chat message POJO
│   └── Session.java       # Session POJO
├── service/
│   ├── ChatClient.java    # Provider interface (Strategy pattern)
│   ├── GeminiChatClient.java
│   ├── OpenAIChatClient.java
│   ├── GenericChatClient.java   # Grok, DeepSeek, etc.
│   ├── DeepResearchService.java # Google Search + web scraping
│   └── ModelService.java       # Auto-selects best model
└── util/
    └── Formatter.java     # ANSI colors, markdown, UI
```

---

## 🧪 Tests

```bash
mvn test
```

Runs 16 unit tests covering:
- `CommandRunnerTest` — shell execution
- `ConfigTest` — environment variable loading
- `MessageTest` — model construction
- `GeminiChatClientTest` — API interaction (mocked)
- `FormatterTest` — terminal formatting

---

## 🤝 Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/awesome`)
3. Commit your changes (`git commit -m 'Add awesome feature'`)
4. Push to the branch (`git push origin feature/awesome`)
5. Open a Pull Request

---

## 📄 License

This project is open source and available under the [MIT License](LICENSE).
