# Nova / Luna

Nova / Luna is a phone-first, offline-first Android assistant project.

- Nova is the male persona.
- Luna is the female persona.
- Flutter is the UI layer.
- Kotlin / native Android is the execution layer.
- Local model assets live under [`models/`](models/) and are kept out of Git.
- SafetyGate and user confirmation stay in control of sensitive actions.

Authoritative status and full architecture details live in:

[`NOVA_LUNA_FINAL_MASTER_REPORT.md`](NOVA_LUNA_FINAL_MASTER_REPORT.md)

## Current shape

- Root Android app is the installable product.
- Flutter is integrated into the native project.
- Accessibility-based phone control is part of the app.
- Local model installation and routing code is present.
- Voice input, TTS, memory, diagnostics, and safety logic are in the repo.

## Repository layout

- `app/` - root Android app
- `flutter_app/` - Flutter UI module
- `wear/` - Wear OS companion scaffold
- `models/` - local model storage, ignored by Git
- `docs/` - support documentation
- `REPOSITORY_CLEANUP_AUDIT.md` - cleanup inventory and audit notes

## Safety boundaries

- OTP, CAPTCHA, login, payment, and similar protected flows remain human-controlled.
- The assistant does not bypass Android security boundaries.
- The assistant is not a backend-dependent cloud assistant by default.

## Support docs

- [`docs/LOCAL_LLM_SETUP.md`](docs/LOCAL_LLM_SETUP.md)
- [`docs/NOVA_LUNA_MODEL_INSTALL_AND_IMPORT.md`](docs/NOVA_LUNA_MODEL_INSTALL_AND_IMPORT.md)
- [`docs/PHONE_ONLY_RUNTIME.md`](docs/PHONE_ONLY_RUNTIME.md)
- [`docs/SECURITY_AND_LIMITATIONS.md`](docs/SECURITY_AND_LIMITATIONS.md)
