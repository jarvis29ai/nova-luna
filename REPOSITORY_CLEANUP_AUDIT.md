# Repository Cleanup Audit

Branch: `chore/nova-luna-final-cleanup`
HEAD: `f3b54558e48121a3ba65063327e4b002feed4181`

Confirmed:
- `f3b54558` is present in the current branch history.
- SafetyGate is untouched in the current diff.
- The phase 18/19 proof evidence files that were briefly absent during inspection were restored and are intentionally not part of the remaining diff.

## 1. Valid Phase 3235 implementation or test work

- `.gitignore` — ignores local build caches, IDE files, logs, env files, and temp outputs that were polluting the tree; proposed action: stage with the implementation/config commit.
- `app/src/main/AndroidManifest.xml` — tightens package visibility and removes broad `QUERY_ALL_PACKAGES` use in favor of explicit queries; proposed action: stage with the implementation commit.
- `app/src/debug/AndroidManifest.xml` — adds the debug-only failover receiver and keeps it out of release builds; proposed action: stage with the implementation commit.
- `app/src/main/java/com/nova/luna/brain/BrainRouter.kt`, `app/src/main/java/com/nova/luna/brain/BrainService.kt`, `app/src/main/java/com/nova/luna/brain/CommandBrain.kt`, `app/src/main/java/com/nova/luna/brain/ModelRuntimeLoader.kt`, `app/src/main/java/com/nova/luna/brain/PhoneGemmaRuntime.kt`, `app/src/main/java/com/nova/luna/modelinstall/ModelInstallBrainRouterBridge.kt` — phase 35 failover routing, runtime verification, and logger plumbing; proposed action: stage together in the main implementation commit.
- `app/src/main/java/com/nova/luna/brain/FailoverOverrideMarkers.kt`, `app/src/main/java/com/nova/luna/util/AndroidNovaLogger.kt`, `app/src/main/java/com/nova/luna/util/NoOpNovaLogger.kt`, `app/src/main/java/com/nova/luna/util/NovaLogger.kt` — shared failover marker mapping plus logger abstraction; proposed action: stage together in the main implementation commit.
- `app/src/androidTest/java/com/nova/luna/brain/Phase35MultiModelFailoverAndroidTest.kt` — physical-device phase 35 failover instrumentation test; proposed action: stage with the implementation commit.
- `app/src/debug/java/com/nova/luna/brain/FailoverDebugReceiver.kt` — debug-only failover control receiver; proposed action: stage with the implementation commit.
- `app/src/test/java/com/nova/luna/brain/Phase35MultiModelFailoverTest.kt`, `app/src/test/java/com/nova/luna/modelinstall/ModelInstallBrainRouterBridgeResolverTest.kt`, `app/src/test/java/com/nova/luna/brain/BrainServicePhase6Test.kt`, `app/src/test/java/com/nova/luna/brain/MultiModelRoleSelectorTest.kt`, `app/src/test/java/com/nova/luna/brain/Phase4TestFixtures.kt`, `app/src/test/java/com/nova/luna/modelinstall/ModelBrainDownloadPresenterTest.kt` — unit-test updates for failover, readiness, and install/report behavior; proposed action: stage with the implementation commit.
- `gradle.properties`, `wear/build.gradle`, `wear/src/main/AndroidManifest.xml` — build/config housekeeping needed for the current Android/Flutter/wear build shape; proposed action: stage with the implementation commit unless you want to split build config into a separate housekeeping commit.

## 2. Valid earlier Phase 14 implementation or test work

- `app/src/main/cpp/gguf-stub-fallback.cpp` — obsolete stub native fallback removed now that the real llama.cpp path is in place; no current references remain in CMake or source search, so the deletion was committed in the source/process cleanup commit.

## 3. Intentional stale-document cleanup

- `README.md`, `docs/NOVA_LUNA_MODEL_INSTALL_AND_IMPORT.md` — refreshed to match the current repo layout, current support docs, and the real Windows staging path for model import; proposed action: stage with the docs cleanup commit.
- `docs/ARCHITECTURE.md`, `docs/COMMUNICATION_MODEL_REPORT.md`, `docs/CURRENT_ARCHITECTURE_REPORT.md`, `docs/FINAL_ANDROID_NATIVE_RELEASE_READINESS_REPORT.md`, `docs/FINAL_PHONE_SMOKE_TEST_REPORT.md`, `docs/FINAL_REMAINING_PHONE_SMOKE_TEST_REPORT.md`, `docs/MANUAL_PHONE_TEST_CHECKLIST.md`, `docs/MANUAL_PHONE_TEST_RESULTS.md`, `docs/NOVA_LUNA_A_TO_Z_PROJECT_REPORT.md`, `docs/NOVA_LUNA_BRAIN_A_TO_Z_CURRENT_REPORT.md`, `docs/NOVA_LUNA_FLOWCHARTS.md`, `docs/NOVA_LUNA_FULL_ARCHITECTURE_AND_WORKFLOW_REPORT.md`, `docs/NOVA_LUNA_FULL_PROJECT_MODEL_FLOW_AUDIT_REPORT.md`, `docs/NOVA_LUNA_PROGRESS_CHECKPOINT.md`, `docs/NOVA_LUNA_PROJECT_SHORT_SUMMARY.md`, `docs/ROADMAP.md`, `docs/WORK_AND_PROCESS_REPORT.md`, `docs/WORK_PROCESS_REPORT.md` — historical architecture/workflow/process reports that appear superseded by the current master report and support docs; proposed action: stage only if the cleanup policy is to remove the legacy doc set.
- `docs/NOVA_LUNA_MODEL_INSTALL_PHASE_1_REPORT.md`, `docs/NOVA_LUNA_MODEL_INSTALL_PHASE_2_REPORT.md`, `docs/NOVA_LUNA_MODEL_INSTALL_PHASE_3_REPORT.md`, `docs/NOVA_LUNA_MODEL_INSTALL_PHASE_4_REPORT.md`, `docs/NOVA_LUNA_MODEL_INSTALL_PHASE_5_REPORT.md`, `docs/NOVA_LUNA_MODEL_INSTALL_PHASE_6_REPORT.md`, `docs/NOVA_LUNA_MODEL_INSTALL_PHASE_7_REPORT.md`, `docs/NOVA_LUNA_MODEL_INSTALL_PHASE_8_REPORT.md`, `docs/PHASE_1_HANDS_REPORT.md`, `docs/PHASE_2_EARS_REPORT.md`, `docs/PHASE_3_MOUTH_REPORT.md`, `docs/PHASE_4_FACE_REPORT.md`, `docs/PHASE_5_UNIFIED_MODEL_INTEGRATION_REPORT.md`, `docs/PHASE_6_REAL_PHONE_TEST_REPORT.md`, `docs/PHASE_7_LOCAL_LLM_TECHNICAL_REPORT.md`, `docs/PHASE_8_MEMORY_PERSONALIZATION_REPORT.md`, `docs/PHASE_27_VOICE_FLOW_REPORT.md`, `docs/PHASE_29_SCREEN_UNDERSTANDING_REPORT.md`, `docs/PHASE_30_CONFIRMATION_SYSTEM_REPORT.md`, `docs/PHASE_18_TO_30_GREEN_REVIEW_REPORT.md`, `docs/PHASE_18_TO_30_REAL_PHONE_SMOKE_TEST.md` — historical phase reports and smoke-test docs that look redundant next to the current master report; proposed action: keep only if the cleanup policy is to retain every historical phase narrative.

## 4. Orchestration/process artifact

- `.cursor/rules/00-master-ai-platform.mdc`, `.cursor/rules/00-nova-luna-core.mdc`, `.cursor/rules/10-flutter-phone-first.mdc`, `.cursor/rules/20-agent-coordination.mdc`, `.cursor/rules/30-safety-no-fake-code.mdc`, `.cursor/rules/luna_nova_contractor_skill.mdc` — agent-rule files and process instructions, not runtime product code; these were intentionally removed in the source/process cleanup commit.
- `.claude/orchestration/STATE.md`, `.claude/orchestration/logs/claude-live-caseA-verification/full_logcat.txt`, `.claude/orchestration/tasks/TASK-001.md`, `.claude/orchestration/tasks/TASK-002.md`, `.claude/orchestration/tasks/TASK-003-REVIEW.md`, `.claude/skills/multi-ai-orchestrator/SKILL.md`, `.claude/skills/multi-ai-orchestrator/templates/REVIEW-template.md`, `.claude/skills/multi-ai-orchestrator/templates/STATE-template.md`, `.claude/skills/multi-ai-orchestrator/templates/TASK-template.md`, `.claude/skills/multi-ai-orchestrator/templates/WORKER-HANDOFF-template.md` — orchestration notes, handoffs, and templates; proposed action: leave uncommitted per the repo policy.
- `NOVA_LUNA_PRODUCTION_BLUEPRINT_AND_PHASE_PROMPTS.md`, `PHASE_32_TO_35_CLAUDE_ORCHESTRATOR.md` — large planning/orchestration prompts that describe process, not source behavior; proposed action: leave uncommitted.
- `phase_32_35_evidence/phase_2/PHASE_2_FINAL_REPORT.md`, `phase_32_35_evidence/phase_4/PHASE_4_FINAL_REPORT.md` — generated phase reports for the cleanup workflow; proposed action: leave uncommitted unless the repo is supposed to preserve every worker handoff.
- `REPOSITORY_CLEANUP_AUDIT.md` — this cleanup inventory itself; proposed action: stage and commit with the docs/audit cleanup set so the repository keeps the written decision record.

## 5. Generated build/cache artifact

- `phase32_initial_unit_test_output.txt`, `phase33_qwen05b_raw.txt`, `phase33_qwen15b_raw.txt` — raw command output captured during earlier proof work; proposed action: leave uncommitted.
- `phase_32_35_evidence/phase_2/01_android_unit_tests.txt`, `phase_32_35_evidence/phase_2/02_android_debug_apk.txt`, `phase_32_35_evidence/phase_2/03_android_test_apk.txt`, `phase_32_35_evidence/phase_2/04_flutter_analyze.txt`, `phase_32_35_evidence/phase_2/05_flutter_tests.txt`, `phase_32_35_evidence/phase_2/06_flutter_debug_apk.txt`, `phase_32_35_evidence/phase_2/07_artifact_inventory.txt`, `phase_32_35_evidence/phase_2/08_git_diff_check.txt` — generated build/test evidence and artifact inventory; proposed action: leave uncommitted.
- `phase_32_35_evidence/phase_3/01_device_state.txt`, `phase_32_35_evidence/phase_3/02_device_properties.txt`, `phase_32_35_evidence/phase_3/03_apk_install.txt`, `phase_32_35_evidence/phase_3/04_model_inventory.txt`, `phase_32_35_evidence/phase_3/06_phase35_logcat.txt`, `phase_32_35_evidence/phase_3/anr_dumps.txt`, `phase_32_35_evidence/phase_3/crash_dumps.txt`, `phase_32_35_evidence/phase_3/logcat_caseB_only.txt`, `phase_32_35_evidence/phase_3/logcat_full_suite.txt`, `phase_32_35_evidence/phase_3/logcat_phase35_lines.txt`, `phase_32_35_evidence/phase_3/phase35_logcat_filtered.txt` — generated device/proof logs and dumps; proposed action: leave uncommitted.
- `phase_32_35_evidence/phase_4/01_starting_git_state.txt`, `phase_32_35_evidence/phase_4/04_manifest_merge_debug.txt`, `phase_32_35_evidence/phase_4/05_security_changes.txt`, `phase_32_35_evidence/phase_4/07_cleanup_inventory_before.txt`, `phase_32_35_evidence/phase_4/08_android_unit_tests.txt`, `phase_32_35_evidence/phase_4/08_cleanup_actions.txt`, `phase_32_35_evidence/phase_4/09_android_debug_build.txt`, `phase_32_35_evidence/phase_4/10_android_test_build.txt`, `phase_32_35_evidence/phase_4/11_release_manifest_verification.txt`, `phase_32_35_evidence/phase_4/12_final_git_state.txt` — generated cleanup and verification logs; proposed action: leave uncommitted.

## 6. Unrelated user work

- None identified.

## 7. Suspicious or unknown

- `t \`n===== 1. REPOSITORY STATE ===== -ForegroundColor Cyan` — garbled root filename with an embedded PowerShell fragment; provenance is unclear and it must not be deleted without explicit authorization.
