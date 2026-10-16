# Docker tooling

`docker compose --profile preview up --build` builds a static web preview on localhost:3000. Telegram and AI APIs are unavailable in this standalone preview; it is intended for frontend inspection.

`docker compose --profile checks run --build --rm backend-checks` runs JVM checks in JDK 21. Windows-specific DPAPI/native integration is skipped on Linux. Windows release validation remains in GitHub Actions.

The signed-in desktop application runs on Windows: TDLib ships as a Windows native library and credentials use Windows DPAPI. These images do not wrap the actual desktop session. Build the portable exe with `scripts/build.ps1` on Windows.
