# Phase 7 Technical Report: Local LLM Runtime

## Overview
Phase 7 introduces a local on-device LLM runtime (Gemma 3n, Qwen 3) to Luna/Nova, enhancing reasoning and multilingual understanding without compromising the privacy-first, cloud-free core. The LLM acts as an "intelligent router" and "reasoning bridge" when rule-based parsers have low confidence.

## Core Components

### 1. Local LLM Manager (`com.nova.luna.llm`)
- **`LocalLlmManager`**: Central orchestrator managing model selection, prompt construction, and output parsing.
- **`LocalLlmReadinessChecker`**: Validates device capability (min 2GB/4GB RAM) and model availability before execution.
- **`LocalLlmPromptBuilder`**: Constructs system-optimized prompts with strict JSON schema constraints.
- **`LocalLlmOutputParser`**: Surgical extraction of candidate actions from LLM JSON output.

### 2. Rule-First Routing Policy
The `CommandBrain` was refactored to implement a hierarchical decision chain:
1. **Pending Confirmations**: Highest priority; resolves existing flows.
2. **Unified Rule-Based Routing**: Fast, deterministic routing using regex and keyword handlers.
3. **LLM Enhancement**: Invoked only if Rule-Based confidence is < 0.75. Provides deep reasoning for complex or mixed-language requests.
4. **Mandatory Safety Gating**: All outputs (Rule or LLM) MUST pass through `SafetyGate` before execution.

### 3. Safety and Privacy Hardening
- **LLM Boundary**: LLM output is limited to candidate JSON actions; it has no direct execution privileges.
- **Data Redaction**: Prompts are sanitized to remove PII (OTPs, PINs, card numbers) before being sent to the local model.
- **Action Verification**: LLM-proposed actions are subject to screen state verification and user confirmation for high-risk intents.

## Implementation Details

### Hierarchical Model Stack
1. **Gemma 3n (4-bit)**: Primary reasoning engine for complex task planning.
2. **Qwen 3 Small**: Specialized backup for Hindi/Hinglish and multilingual nuances.
3. **Gemma 3 270M / Phi-4 mini**: Lightweight fallbacks for low-memory devices.

### Codebase Integration
- **`CommandBrain.kt`**: Integrated LLM into the `process` loop. Fixed session continuity regressions by bridging memory session types with executor states.
- **`SafetyGate.kt`**: Expanded patterns to handle drafted actions vs. final executions (e.g., allowing "checkout" while gating "order now").
- **`AssistantSession.kt`**: Added real-time feedback listeners for "thinking" and "routing" states to improve user experience during LLM processing.

## Verification Results
- **Unit Tests**: 465/465 tests passed (100% green).
- **Regressions Fixed**:
  - Bare-word fallback prioritization (Phase 1).
  - Music session playback control persistence (Phase 2).
  - Online AI consent flow replay integrity (Phase 5).
  - Cross-brain grocery memory persistence (Phase 6).
- **Stability**: Resolved multiple `NullPointerException` and `RuntimeException` cases in test environments by making context-sensitive components safer.

## Conclusion
Phase 7 is COMPLETE. Luna is now "intelligent" while remaining "safe" and "local-first". The system is ready for real-world deployment on high-performance Android devices.
