You are a senior Android engineer, UI/UX designer, backend-integration engineer, and AI-agent systems architect.

Build a complete, production-ready **Android phone application (APK)** with a polished UI inspired by modern ChatGPT-style conversational apps.

## 1. PRIMARY OBJECTIVE

Create an Android app that acts as a full-featured mobile client for an **AI agentic server called Hermes running on my PC**.

The architecture must be:

**Android App → Network → Hermes Agentic Server on PC → Selected LLM / Tools / Agent Runtime**

The Android app should NOT attempt to run the agentic server locally unless explicitly required as an optional feature.

The Hermes server remains responsible for:

* AI agent execution
* LLM communication
* tool/function execution
* file processing
* web/research operations
* agent memory
* task execution
* MCP/tool integrations
* long-running/background agent jobs
* streaming responses
* model routing

The Android application should provide a powerful mobile interface for controlling and interacting with Hermes.

---

# 2. IMPORTANT ARCHITECTURE REQUIREMENT

Do not assume a proprietary Hermes API exists.

First determine the integration protocol from the Hermes server configuration/API documentation that I provide.

Support an adapter-based architecture so the Android app can communicate with Hermes through whichever API it exposes.

Preferred protocols, in order:

1. REST/HTTP
2. WebSocket
3. Server-Sent Events (SSE)
4. OpenAI-compatible API
5. A custom Hermes API adapter

Create a clean interface such as:

```text
HermesClient
 ├── RestHermesClient
 ├── WebSocketHermesClient
 ├── SSEHermesClient
 └── OpenAICompatibleHermesClient
```

Do NOT hard-code the application around one undocumented API.

If the Hermes API specification is missing, create a clearly isolated `HermesApiAdapter` layer and document exactly which endpoints/data structures I need to provide.

---

# 3. ANDROID TECHNOLOGY STACK

Use modern Android development practices.

Preferred stack:

* Kotlin
* Jetpack Compose
* Material 3
* Android Architecture Components
* MVVM or clean architecture
* Kotlin Coroutines
* Flow/StateFlow
* Retrofit/OkHttp where appropriate
* WebSocket support where appropriate
* Room for local persistence
* Android Keystore for sensitive credentials
* WorkManager for appropriate background synchronization
* Hilt for dependency injection

Use a modular architecture where practical.

Suggested structure:

```text
app/
core/
 ├── network/
 ├── database/
 ├── security/
 ├── common/
 └── ui/

data/
 ├── models/
 ├── repositories/
 └── datasources/

domain/
 ├── models/
 ├── repositories/
 └── usecases/

feature/
 ├── chat/
 ├── conversations/
 ├── models/
 ├── agents/
 ├── files/
 ├── tasks/
 ├── settings/
 └── server/

hermes/
 ├── api/
 ├── websocket/
 ├── adapters/
 └── models/
```

Keep the code maintainable and extensible.

---

# 4. CHATGPT-STYLE UI

Create a polished conversational interface.

The main screen should contain:

### Top bar

* New Chat button
* Current conversation title
* Connection/server status
* Selected model indicator
* Menu/settings button

### Main conversation area

Support:

* User messages
* Assistant messages
* Streaming assistant responses
* Markdown
* Code blocks
* Syntax highlighting
* Copy button
* Regenerate response
* Edit user message
* Delete message
* Retry failed request
* Share response
* Select/copy text
* Expand/collapse long content
* Tables
* Lists
* Links
* Quotes
* Inline formatting

Assistant responses should appear progressively as Hermes streams tokens/events.

Do NOT wait for the complete response before rendering it if streaming is available.

---

# 5. CHAT INPUT

Create a modern ChatGPT-like composer.

It must support:

* Multi-line text input
* Send button
* Stop generation button
* Attach file button
* Image attachment
* Camera attachment
* Voice input
* Model selector
* Agent/mode selector
* Optional tool permissions
* Prompt/context controls

The composer should intelligently change depending on whether Hermes is:

* idle
* processing
* streaming
* executing a tool
* waiting for user confirmation
* disconnected

---

# 6. CONVERSATION MANAGEMENT

Implement persistent conversations.

Users should be able to:

* Create new conversation
* Rename conversation
* Delete conversation
* Archive conversation
* Search conversations
* Pin conversations
* Sort conversations
* Continue previous conversations
* Duplicate a conversation
* Export conversation
* Share conversation

Store conversation metadata locally using Room.

Example:

```text
Conversation
 ├── id
 ├── title
 ├── createdAt
 ├── updatedAt
 ├── selectedModel
 ├── agentId
 └── metadata

Message
 ├── id
 ├── conversationId
 ├── role
 ├── content
 ├── timestamp
 ├── status
 ├── attachments
 ├── toolCalls
 └── metadata
```

---

# 7. LLM MODEL SELECTION

This is a critical requirement.

The application must allow me to select the LLM model that Hermes should use.

Create a dedicated model selector.

Example:

```text
Model

○ GPT-5
○ GPT-5.6
○ Claude
○ Gemini
○ Llama
○ Qwen
○ DeepSeek
○ Local Model
○ Custom Model
```

Do NOT hard-code these specific models as if they necessarily exist.

Instead, obtain the available models dynamically from Hermes whenever its API supports model discovery.

For example:

```http
GET /v1/models
```

or an equivalent Hermes endpoint.

Display:

```text
Provider
Model
Context window
Capabilities
Availability
Local/Remote
```

When the user selects a model:

```text
Android App
      ↓
Selected Model = XYZ
      ↓
Hermes API
      ↓
Agent configuration/model selection
      ↓
XYZ
```

Every subsequent request for that conversation must use the selected model.

The model selection must NOT merely change the UI label.

It must actually be sent to Hermes using the correct API field.

For example, if the Hermes API supports:

```json
{
  "model": "MODEL_ID",
  "messages": [...]
}
```

send the selected model ID.

If Hermes uses a different model-selection mechanism, implement that mechanism through the adapter.

---

# 8. MODEL PROVIDER MANAGEMENT

Create a model/provider management screen.

Allow the user to see:

```text
Providers

OpenAI
Anthropic
Google
OpenRouter
Ollama
LM Studio
Custom
```

But only display providers actually exposed/configured by Hermes when possible.

The Android application should NOT unnecessarily store provider API keys if Hermes already manages those credentials.

Prefer:

**Android → Hermes → Provider**

instead of:

**Android → Provider directly**

unless a direct-provider mode is explicitly implemented as an optional feature.

---

# 9. AGENT SELECTION

The Hermes server is an agentic runtime.

Therefore support multiple agents if Hermes exposes them.

Create an Agent selector:

```text
Agent

General Assistant
Research Agent
Coding Agent
Browser Agent
File Agent
Custom Agent
```

Again, retrieve the actual list dynamically from Hermes where possible.

Each agent may expose:

* name
* description
* capabilities
* tools
* model
* permissions
* system configuration

Allow the user to select the appropriate agent before starting a task.

---

# 10. AGENTIC EXECUTION UI

Do not make Hermes look like a simple chatbot.

The UI should clearly represent agent activity.

For example:

```text
Assistant

Thinking...
↓
Searching web...
↓
Reading source...
↓
Running tool...
↓
Analyzing results...
↓
Generating response...
```

Create collapsible activity cards.

Example:

```text
┌────────────────────────────┐
│ 🔧 Tool execution          │
│ browser.search             │
│ Completed                  │
└────────────────────────────┘
```

Support event types such as:

```text
message
thinking
tool_call
tool_result
agent_started
agent_finished
error
approval_required
file_created
file_updated
task_progress
status
```

Do not expose sensitive internal reasoning or hidden chain-of-thought.

Display only safe agent status, tool activity, summaries, and results provided by Hermes.

---

# 11. HUMAN APPROVAL / PERMISSIONS

If Hermes requires approval before executing a potentially sensitive tool, display an interactive approval card.

Example:

```text
Hermes wants to execute:

Tool: shell.execute

Command:
npm install ...

[Cancel] [Approve]
```

The approval must be sent back to Hermes through the proper API.

Support:

* Approve
* Reject
* Approve once
* Cancel task

Do not automatically approve sensitive operations.

---

# 12. LONG-RUNNING TASKS

The app must support agent tasks that continue for an extended period.

Create a Tasks screen.

Example:

```text
Tasks

Research AI market
● Running

Build Android application
● Running

Analyze documents
✓ Completed

Website research
✕ Failed
```

Each task should show:

* Task name
* Status
* Start time
* Duration
* Progress if available
* Agent
* Model
* Recent activity
* Result

Allow:

* Open
* Pause if supported
* Resume if supported
* Cancel
* Delete
* View logs/activity
* View final result

---

# 13. BACKGROUND TASK SYNCHRONIZATION

If Hermes supports long-running tasks, the Android app should be able to disconnect and reconnect without losing state.

When the user returns:

```text
Synchronizing with Hermes...
```

Then retrieve current task/conversation state.

Use WorkManager only where appropriate.

Do not keep a permanent foreground service running unless technically necessary.

Handle:

* network loss
* app suspension
* phone restart
* Hermes restart
* connection timeout

---

# 14. REAL-TIME STREAMING

Implement real-time streaming.

Preferred architecture:

```text
Hermes
   ↓
WebSocket/SSE
   ↓
HermesClient
   ↓
Flow<Event>
   ↓
ViewModel
   ↓
Jetpack Compose UI
```

The UI should update immediately as events arrive.

Example:

```text
Assistant:
I researched the topic and found...
```

while the response is still being generated.

Handle reconnects gracefully.

---

# 15. FILE SUPPORT

Allow users to attach files.

Support, where Hermes supports them:

* TXT
* PDF
* DOCX
* CSV
* JSON
* Markdown
* Images
* ZIP
* Source code
* Other files exposed by Hermes

Display upload progress.

Example:

```text
📄 research.pdf
Uploading... 72%
```

After upload:

```text
📄 research.pdf ✓
```

Send files to Hermes using the correct API mechanism.

Do not assume that Hermes accepts raw file bytes in chat requests.

Use its documented file/upload API if available.

---

# 16. IMAGE SUPPORT

If the selected model/agent supports images:

Allow:

* Camera
* Gallery
* Image preview
* Remove image
* Multiple image attachments if supported

The application should detect capabilities from the selected model where possible.

For example:

```text
Vision: ✓
Image generation: ✕
Audio: ✓
Tools: ✓
```

---

# 17. VOICE INPUT

Implement optional voice input.

Use Android speech recognition where appropriate.

Flow:

```text
🎤
↓
Speech recognition
↓
Text
↓
Chat composer
```

The user should be able to edit the transcription before sending it.

If Hermes exposes audio/voice APIs, architect the integration so those can be added later.

---

# 18. CONNECTION CONFIGURATION

Create a Server Connection screen.

Fields:

```text
Hermes Server URL
Connection type
Port
Authentication
API Key / Token
TLS
Connection timeout
```

Example:

```text
Server URL

http://192.168.1.100:8000
```

Add:

**Test Connection**

Result:

```text
✓ Connected to Hermes

Version: ...
API: ...
Models: 8
Agents: 4
```

Never hard-code my PC's IP address.

---

# 19. LOCAL NETWORK SUPPORT

The primary use case is connecting the phone to Hermes running on my PC over the local network.

Provide clear support for:

```text
Phone
  │
  │ Wi-Fi
  ↓
Router
  │
  ↓
PC
  │
  ↓
Hermes Server
```

Explain in the setup documentation that Hermes must listen on an address reachable from the phone rather than only `127.0.0.1`.

Do not assume a specific port.

Allow the user to configure it.

Support HTTPS when configured.

Do not disable TLS certificate validation in production.

If development-only self-signed certificates are supported, isolate that option behind an explicit development setting and clearly warn the user.

---

# 20. REMOTE ACCESS

Design the networking layer so remote access can later work through:

* VPN
* Tailscale
* Cloudflare Tunnel
* Reverse proxy
* HTTPS endpoint

Do not expose the Hermes server directly to the public internet by default.

The app should work with a normal URL:

```text
https://hermes.example.com
```

without requiring UI changes.

---

# 21. AUTHENTICATION

Support the authentication method provided by Hermes.

Possible mechanisms:

* API key
* Bearer token
* JWT
* Basic authentication
* Session authentication
* OAuth if exposed by Hermes

Never log authentication credentials.

Store sensitive tokens using:

**Android Keystore / encrypted storage**

Do not store secrets in:

* SharedPreferences plaintext
* source code
* Git
* APK resources
* logs

---

# 22. SETTINGS

Create a comprehensive settings screen.

Sections:

### Server

* Hermes URL
* Authentication
* Connection test
* Connection status

### AI

* Default model
* Default agent
* Temperature if supported
* Max tokens if supported
* Streaming
* Tool permissions

### Appearance

* Light
* Dark
* System
* Dynamic colors
* Font size

### Chat

* Enter to send
* Show tool activity
* Show timestamps
* Auto-scroll
* Markdown rendering

### Data

* Clear local conversations
* Export data
* Import data
* Clear cache

### About

* App version
* Hermes connection information
* Licenses

Only expose AI parameters that Hermes actually supports.

---

# 23. SEARCH

Implement global search across local conversation history.

Search:

* Conversation titles
* User messages
* Assistant messages

Provide:

```text
Search
──────────────
"Android"

3 conversations found
```

Tapping a result should navigate directly to the relevant message.

---

# 24. EXPORT

Support exporting conversations where practical.

Formats:

* Markdown
* JSON
* Plain text

Example:

```text
Conversation
Model: XYZ
Agent: Research

User:
...

Assistant:
...
```

---

# 25. ERROR HANDLING

Create user-friendly error states.

Examples:

### Hermes offline

```text
Hermes is unreachable.

Check:
• PC is running
• Hermes is running
• Phone is on the same network
• Server URL is correct

[Retry]
[Connection Settings]
```

### Authentication failure

```text
Authentication failed.

Check your Hermes credentials.
```

### Model unavailable

```text
The selected model is currently unavailable.

[Select another model]
[Retry]
```

### Network timeout

Provide retry functionality.

Never silently discard user messages.

---

# 26. OFFLINE BEHAVIOR

The app should gracefully detect offline status.

Allow users to:

* View previous conversations
* Search local history
* Read cached responses

If the user sends a message while offline, clearly indicate that it has not yet been sent.

Do not falsely show it as successfully delivered.

---

# 27. UI/UX REQUIREMENTS

The UI should feel like a modern premium AI application.

Use:

* Material 3
* Smooth animations
* Rounded cards
* Clean typography
* Proper spacing
* Dark mode
* Responsive layouts
* Accessibility
* Touch-friendly controls

Avoid copying ChatGPT's branding, logos, or proprietary visual assets.

Create an original interface inspired by modern AI chat applications.

---

# 28. PHONE-FIRST NAVIGATION

Use a navigation structure such as:

```text
Home / Chat
│
├── Conversation
├── Conversations
├── Tasks
├── Agents
├── Models
├── Files
└── Settings
```

For phones, use a navigation drawer or bottom navigation where appropriate.

For larger Android devices, adapt the UI responsively.

---

# 29. SECURITY REQUIREMENTS

Treat the Hermes server as a privileged backend.

Never:

* hard-code secrets
* expose API keys in logs
* trust arbitrary TLS certificates
* execute shell commands locally from the Android app
* bypass Hermes authentication
* automatically approve dangerous agent operations

Validate server responses.

Use HTTPS for remote deployments.

Implement request timeouts.

Handle certificate errors correctly.

Sanitize rendered Markdown/HTML.

Protect local database data where practical.

---

# 30. API ABSTRACTION

Create domain-level interfaces rather than tying UI directly to HTTP.

For example:

```kotlin
interface HermesRepository {

    suspend fun getServerInfo(): ServerInfo

    suspend fun getModels(): List<Model>

    suspend fun getAgents(): List<Agent>

    suspend fun createConversation(): Conversation

    suspend fun sendMessage(
        conversationId: String,
        message: String,
        modelId: String?,
        agentId: String?
    )

    fun streamConversation(
        conversationId: String
    ): Flow<HermesEvent>

    suspend fun approveToolCall(
        taskId: String,
        approvalId: String
    )

    suspend fun rejectToolCall(
        taskId: String,
        approvalId: String
    )

    suspend fun cancelTask(
        taskId: String
    )
}
```

Adapt this to the real Hermes API.

---

# 31. MODEL SELECTION DATA FLOW

Implement this carefully.

When opening the model selector:

```text
Android
 ↓
Hermes
 ↓
Fetch available models
 ↓
Display models
```

When selecting a model:

```text
User selects model
 ↓
Save selected model ID
 ↓
Start/send request
 ↓
Hermes receives model ID
 ↓
Hermes routes request to selected LLM
```

When Hermes reports that the model is unavailable:

```text
Model unavailable
 ↓
Show error
 ↓
Offer model selector
```

Never silently substitute another model unless the user explicitly enables automatic fallback.

If automatic fallback exists, clearly display which model was actually used.

---

# 32. CAPABILITY DISCOVERY

Do not assume all Hermes installations expose the same functionality.

At connection time, discover capabilities where possible.

Example:

```json
{
  "streaming": true,
  "models": true,
  "agents": true,
  "tools": true,
  "file_uploads": true,
  "tasks": true,
  "approvals": true
}
```

Use these capabilities to dynamically enable/disable UI functionality.

If an API feature is unavailable:

* hide it or disable it gracefully
* explain why when appropriate
* do not crash

---

# 33. HERMES API ADAPTER

Create a dedicated integration layer.

For example:

```text
HermesApiAdapter
│
├── ServerInfo
├── Models
├── Agents
├── Conversations
├── Messages
├── Streaming
├── Tasks
├── Tools
├── Approvals
└── Files
```

Document every endpoint used.

For each endpoint document:

```text
HTTP method
URL
Authentication
Request body
Response body
Streaming format
Error responses
```

If Hermes API documentation is not provided, DO NOT invent endpoint behavior and present it as real.

Instead implement an interface and provide clearly marked placeholders.

---

# 34. DEVELOPMENT MOCK MODE

Implement a mock Hermes backend for development.

This allows the UI to be developed without the real PC server.

The mock should simulate:

* models
* agents
* conversations
* streaming
* tool activity
* task progress
* errors
* approvals

Make it possible to switch between:

```text
Mock Hermes
Real Hermes
```

through a development configuration.

Do not ship mock mode enabled by default in production.

---

# 35. LOGGING

Implement structured logging for development.

Logs should include:

```text
Connection
Request ID
Endpoint
Status code
Latency
Streaming state
Task state
```

Never log:

* API keys
* bearer tokens
* passwords
* private credentials
* sensitive conversation contents unless explicitly enabled for debugging

---

# 36. TESTING

Create tests for:

### Unit tests

* Model selection
* Repository
* API adapters
* Authentication handling
* Event parsing
* Conversation persistence
* Error mapping

### UI tests

* Send message
* Streaming response
* Model selection
* Agent selection
* Conversation creation
* File attachment
* Approval dialog
* Server configuration

### Integration tests

Test:

```text
Android
 ↓
Hermes API
 ↓
Mock Hermes
```

Include malformed responses and network failures.

---

# 37. BUILD REQUIREMENTS

The final project must be buildable into an APK.

Provide:

```text
./gradlew assembleDebug
```

and, where configured:

```text
./gradlew assembleRelease
```

The project should contain:

* Gradle configuration
* AndroidManifest
* resources
* Kotlin source
* Compose UI
* tests
* README
* API integration documentation
* sample configuration
* ProGuard/R8 configuration if needed

Do not leave the application as a conceptual prototype.

---

# 38. README

Create a detailed README containing:

## Installation

How to build the APK.

## Hermes configuration

How to configure Hermes so the Android device can reach it.

## Network setup

Example:

```text
PC IP: 192.168.1.100
Hermes port: 8000
Phone URL:
http://192.168.1.100:8000
```

Clearly state that these are examples and must be replaced with the actual values.

## Authentication

Explain how to configure the authentication method supported by Hermes.

## Model selection

Explain how the app discovers models and passes the selected model to Hermes.

## Troubleshooting

Include:

* connection refused
* timeout
* authentication errors
* model unavailable
* WebSocket failure
* TLS problems
* firewall issues

---

# 39. WINDOWS/PC NETWORKING CONSIDERATIONS

Assume the Hermes server may be running on a Windows PC.

Document:

* Windows Firewall inbound rules
* binding Hermes to the appropriate network interface
* finding the PC's LAN IP
* ensuring phone and PC are reachable
* testing the Hermes endpoint from the phone

Do not instruct users to expose privileged ports unnecessarily.

---

# 40. DESIGN THE APPLICATION FOR FUTURE EXTENSIONS

Use extensible interfaces so future functionality can be added without rewriting the app.

Potential future features:

* Multiple Hermes servers
* Server profiles
* Cloud sync
* Push notifications
* Android share-sheet integration
* Android widgets
* Wear OS companion
* Voice conversations
* Image generation
* Remote desktop/task monitoring
* MCP server management

Do not implement these unless required, but keep the architecture extensible.

---

# 41. MULTI-SERVER SUPPORT

Prefer a server profile architecture.

Example:

```text
Servers

● Home PC
  192.168.1.100:8000

○ Office PC
  192.168.0.50:8000

○ Remote Hermes
  https://hermes.example.com
```

The user can switch servers.

Each profile should store:

* Name
* URL
* Authentication reference
* Connection type
* Last connection state

Do not duplicate credentials unnecessarily.

---

# 42. CHAT STATE CONSISTENCY

Ensure the Android client and Hermes server cannot easily become inconsistent.

Use stable IDs:

```text
serverId
conversationId
messageId
taskId
eventId
```

Handle:

* duplicate events
* reconnects
* missing events
* reordered events
* partially streamed responses

Where possible, request the latest server state after reconnecting.

---

# 43. STREAMING EVENT NORMALIZATION

Different Hermes versions may emit different event formats.

Normalize them internally:

```kotlin
sealed class HermesEvent {

    data class TextDelta(
        val messageId: String,
        val text: String
    ) : HermesEvent()

    data class ToolStarted(
        val taskId: String,
        val toolName: String
    ) : HermesEvent()

    data class ToolFinished(
        val taskId: String,
        val toolName: String,
        val summary: String?
    ) : HermesEvent()

    data class ApprovalRequired(
        val taskId: String,
        val approvalId: String,
        val description: String
    ) : HermesEvent()

    data class Completed(
        val messageId: String
    ) : HermesEvent()

    data class Error(
        val message: String
    ) : HermesEvent()
}
```

Adapt this structure to the real Hermes protocol.

---

# 44. PERFORMANCE

Optimize for phone hardware.

Requirements:

* Lazy message rendering
* Efficient Markdown rendering
* Pagination for large conversations
* Avoid unnecessary recomposition
* Efficient database queries
* Image compression/thumbnails
* Streaming without memory leaks
* Proper coroutine cancellation

A conversation containing thousands of messages should not freeze the UI.

---

# 45. ACCESSIBILITY

Support:

* Screen readers
* Content descriptions
* Dynamic font scaling
* High contrast
* Minimum touch targets
* Keyboard navigation where applicable

Do not communicate state solely through color.

---

# 46. FINAL DELIVERABLE

Produce the complete Android project.

Before finalizing:

1. Build the application.
2. Run tests.
3. Fix compilation errors.
4. Fix obvious UI issues.
5. Verify the Hermes integration layer.
6. Verify model selection.
7. Verify streaming.
8. Verify reconnect behavior.
9. Verify authentication handling.
10. Verify conversation persistence.

Then provide:

```text
1. Project source code
2. APK build instructions
3. Hermes integration instructions
4. API adapter documentation
5. Configuration instructions
6. Testing instructions
7. Troubleshooting guide
```

---

# 47. IMPORTANT IMPLEMENTATION RULES

Do not fabricate Hermes API endpoints.

Do not assume a model-selection API exists unless documented.

Do not silently replace the model selected by the user.

Do not put LLM provider credentials in the Android application if Hermes already manages them.

Do not expose Hermes's privileged tools directly from the Android app.

Do not expose hidden chain-of-thought.

Do not make the app dependent on a hard-coded PC IP.

Do not assume the Hermes server runs on a particular port.

Do not make localhost (`127.0.0.1`) the default remote connection address.

Do not treat a successful HTTP connection as proof that the Hermes API is compatible; validate the API/capabilities.

---

# 48. FIRST STEP BEFORE WRITING THE IMPLEMENTATION

Before generating the final code, inspect the Hermes API information I provide.

Create a short integration analysis containing:

```text
Hermes API protocol:
Base URL:
Authentication:
Models endpoint:
Agents endpoint:
Conversation endpoint:
Message endpoint:
Streaming mechanism:
Task endpoint:
Tool/approval endpoint:
File endpoint:
Available capabilities:
Model selection mechanism:
```

Then map the real Hermes API to the Android architecture above.

If any information is missing, explicitly identify the missing information and implement the integration behind an adapter rather than inventing it.

After that, generate the complete implementation.

The end result should be a polished Android application that feels like a modern ChatGPT-style client while functioning as a **mobile control and conversation interface for my Hermes agentic server running on my PC**, with **real model selection and real-time agent execution/status support**.
