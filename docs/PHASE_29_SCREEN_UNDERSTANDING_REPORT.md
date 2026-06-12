# Phase 29: Screen Understanding Layer Report

## 1. Goal
Build a real screen-understanding layer for Nova/Luna to enable intelligent app control based on Android AccessibilityService data.

## 2. What Was Implemented
- Screen Snapshot and Element models.
- Accessibility Screen Reader (traverse tree).
- Screen Classifier (detects screen type and risk signals).
- UI Element Finder (with fuzzy matching and risk awareness).
- Safe Next Step Planner (implements safe flows, blocks risky ones).
- Screen Recovery Controller (handles failures safely).
- Integrated with `ActionExecutor` (Phase 28 flows now use Screen Understanding).

## 3. Files Created/Changed
- app/src/main/java/com/nova/luna/screen/ScreenSnapshot.kt (New)
- app/src/main/java/com/nova/luna/screen/ScreenElement.kt (New)
- app/src/main/java/com/nova/luna/screen/ScreenElementType.kt (New)
- app/src/main/java/com/nova/luna/screen/ScreenUnderstandingResult.kt (New)
- app/src/main/java/com/nova/luna/screen/AccessibilityScreenReader.kt (New)
- app/src/main/java/com/nova/luna/screen/AndroidAccessibilityScreenReader.kt (New)
- app/src/main/java/com/nova/luna/screen/ScreenClassifier.kt (New)
- app/src/main/java/com/nova/luna/screen/DefaultScreenClassifier.kt (New)
- app/src/main/java/com/nova/luna/screen/ScreenType.kt (New)
- app/src/main/java/com/nova/luna/screen/ScreenElementFinder.kt (New)
- app/src/main/java/com/nova/luna/screen/DefaultScreenElementFinder.kt (New)
- app/src/main/java/com/nova/luna/screen/ElementQuery.kt (New)
- app/src/main/java/com/nova/luna/screen/ElementMatch.kt (New)
- app/src/main/java/com/nova/luna/screen/ScreenStepPlanner.kt (New)
- app/src/main/java/com/nova/luna/screen/DefaultScreenStepPlanner.kt (New)
- app/src/main/java/com/nova/luna/screen/ScreenStep.kt (New)
- app/src/main/java/com/nova/luna/screen/ScreenStepResult.kt (New)
- app/src/main/java/com/nova/luna/screen/ScreenRecoveryController.kt (New)
- app/src/main/java/com/nova/luna/screen/DefaultScreenRecoveryController.kt (New)
- app/src/main/java/com/nova/luna/screen/RecoveryStrategy.kt (New)
- app/src/main/java/com/nova/luna/screen/ScreenUnderstandingController.kt (New)
- app/src/test/java/com/nova/luna/screen/ScreenClassifierTest.kt (New)
- app/src/main/java/com/nova/luna/executor/ActionExecutor.kt (Modified)

## 4. Architecture Summary
The system utilizes Android's `AccessibilityService` to capture a `ScreenSnapshot`. This is then passed to a `ScreenClassifier` to determine context (e.g., YouTube search, payment screen). A `ScreenStepPlanner` calculates the next safe `ScreenStep` based on the intent and screen type. `ActionExecutor` invokes these components. `SafetyGate` remains the ultimate authority, and `ScreenRecoveryController` manages retries or falls back to human input upon failure.

## 5. Safety Rules Enforced
- High-risk screens (Payment, Login, OTP, CAPTCHA) trigger `HUMAN_REQUIRED` actions.
- Automatic actions are only performed when screen type is understood and safe.
- Low-confidence elements are rejected.
- Retries are limited to prevent infinite loops.

## 6. Phase 29 Status
PASS (Build and tests pass).
