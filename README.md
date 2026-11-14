# JH GetWay

**Personal AI API Gateway, Key Vault, Smart Router, MCP Bridge & Workflow Orchestrator for Android**  
Built by **JH Soft Corporation**

---

## Overview

**JH GetWay** turns your Android device into a local-first AI gateway and control center. It unifies OpenAI, Anthropic Claude, Google Gemini, OpenRouter, Groq, DeepSeek, Mistral, Cohere, Hugging Face, Ollama, LM Studio, and custom OpenAI-compatible endpoints behind unified API surfaces (`/v1/chat/completions`, `/v1/responses`, `/v1/messages`, `/v1/models`, and `/health`) with automatic smart routing, multi-key rotation, token telemetry, MCP tool bridging, and DAG workflow execution.

---

## Key Capabilities

- **Unified Local HTTP Gateway (Ktor CIO)**:
  - Runs an embedded Ktor CIO server inside an explicit user-initiated `GatewayForegroundService` (`specialUse`).
  - Defaults strictly to loopback binding (`127.0.0.1:8787`) with fail-closed bearer authentication, per-device scopes (`CHAT`, `MODELS_READ`, `ADMIN`), model allowlists, and token-bucket rate limiting.
  - Supports **Remote Gateway** HTTPS client mode with encrypted bearer token storage and health checks.
- **Encrypted API Key Vault**:
  - Encrypts all upstream API keys, device tokens, agent tokens, local AI server tokens, and MCP environment variables with **Android Keystore AES-256-GCM** (`CryptoManager`).
  - Supports multiple keys per provider with `PRIORITY`, `ROUND_ROBIN`, and `FAILOVER` selection policies.
- **Smart Router Engine**:
  - Modes: `AUTOMATIC`, `LOWEST_COST`, `FASTEST`, `PRIORITY_ORDER`, `ROUND_ROBIN`, and `MANUAL`.
  - Custom alias & provider routing rules (`RoutingRuleEntity`), automatic fallback chains, and live routing decision monitoring.
- **Local AI & Coding Agent Companion**:
  - Connects to Ollama, LM Studio, llama.cpp, vLLM, or LocalAI with explicit Local Network Access consent and policies (`LOCAL_ONLY`, `LOCAL_FIRST`, `LOCAL_CLOUD_FALLBACK`, `CLOUD_FIRST`).
  - Provisions scoped tokens, UTC daily token quota tracking (with `ACTUAL` / `ESTIMATED` / `UNKNOWN` provenance), and ready-to-copy config snippets for **Codex CLI**, **OpenCode**, **Cline**, **Continue**, and **Aider**.
- **MCP (Model Context Protocol) Bridge (Hardened)**:
  - Discovers tools, resources, and prompts over Streamable HTTP / SSE JSON-RPC 2.0 endpoints.
  - Enforces Android process sandboxing (no native subprocess spawning for `STDIO`), 256 KB bounded response parsing, JSON-RPC `id` / `2.0` validation, `isError` checking, and mandatory interactive approval prompts (`ASK_BEFORE_RUNNING`) for dangerous tools.
- **Visual DAG Workflow Engine**:
  - Executes multi-node workflows (`INPUT`, `PROMPT`, `LLM_CALL`, `CONDITION`, `ROUTER`, `MCP_TOOL`, `HTTP_REQUEST`, `CODE_TRANSFORM`, `MERGE`, `OUTPUT`) with cycle detection, reachability/config validation, bounded parallel fan-out, branch-skip propagation, per-node retries, cancellation, and workflow versioning.
- **Analytics, Playground & Bilingual UI**:
  - Single-chat streaming and multi-model side-by-side comparison Playground.
  - Time-bucketed request/latency Canvas chart, provider/model/device/range filters, Actual Billed Cost vs Estimated Cost separation, and token telemetry accuracy breakdown.
  - Full **English** and **Bengali (বাংলা)** localization with Light/Dark/System themes.
- **App Lock & Encrypted Backup (V2)**:
  - PBKDF2WithHmacSHA256 App Lock PIN with exponential lockout and optional `BiometricPrompt`.
  - Passphrase-encrypted `JHBACKUP_V2` export/preview/restore (PBKDF2 + AES-256-GCM) with conflict resolution and configurable retention pruning.

---

## Architecture

- **UI**: Jetpack Compose + Material Design 3 + Navigation Compose (`app/src/main/java/com/example/ui/`)
- **State**: `GatewayViewModel` (`StateFlow` / `MutableStateFlow`) + `AppPreferences` (`DataStore`)
- **Storage**: AndroidX Room (`JhGetWayDatabase`, schema v4 with exported schemas under `app/schemas/`)
- **Networking**: OkHttp 4 (`ProviderHttpClient`, `McpHttpClient`) with SSRF & DNS-rebinding protection (`NetworkAddressClassifier.validateEndpointSecurity` & `pinnedSafeDns`), Ktor CIO (`LocalGatewayHttpServer`)
- **Security**: `CryptoManager` (Android Keystore AES-256-GCM), `AppLockManager` (PBKDF2 + Biometric), `AccessControl` (`DeviceScopePolicy`), `MaskingUtils`

---

## Building, GitHub Releases & In-App Auto-Updates

### Local Build & Verification
Run verification via Gradle (uses container `gradle` binary):

```bash
gradle :app:compileDebugKotlin
gradle :app:testDebugUnitTest --console=plain
```

### Automatic GitHub APK Builds & Releases
Every push to `main`, `master`, or any `v*` tag triggers `.github/workflows/android-release.yml`, which:
1. Runs all JVM and Robolectric unit tests (`:app:testDebugUnitTest`).
2. Automatically increments `versionCode` (`100 + run_number`) and `versionName` (`1.0.<run_number>` or tag name) and embeds `GITHUB_REPOSITORY` into `BuildConfig.GITHUB_REPO`.
3. Builds signed release and debug APKs (`JH-GetWay-v*-release.apk`) plus `update-manifest.json` (with SHA-256 checksum).
4. Publishes a **GitHub Release** with the APK and manifest attached.

For seamless in-place updates on Android devices without uninstalling, configure these optional repository secrets in **GitHub -> Settings -> Secrets and variables -> Actions**:
- `KEYSTORE_BASE64`: Base64-encoded `.jks` keystore (`keyAlias = upload`)
- `STORE_PASSWORD`: Keystore password
- `KEY_PASSWORD`: Key password

### Mobile In-App Updates
APKs built by the GitHub workflow automatically know their repository slug (`BuildConfig.GITHUB_REPO`) and check GitHub Releases on launch (or manually via **Settings -> App Updates (GitHub Releases)**), download the new APK with SHA-256 verification, and launch the Android package installer.

For privacy details see [`docs/PRIVACY_AND_SECURITY.md`](docs/PRIVACY_AND_SECURITY.md). For third-party library licenses see [`docs/DEPENDENCY_LICENSES.md`](docs/DEPENDENCY_LICENSES.md).
