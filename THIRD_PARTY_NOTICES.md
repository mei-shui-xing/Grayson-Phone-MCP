# Third-party notices

Android Remote Control MCP is a modified fork of [danielealbano/android-remote-control-mcp](https://github.com/danielealbano/android-remote-control-mcp). The upstream Git history and MIT `LICENSE.md` are retained.

- `vendor/cloudflared` is the Cloudflare `cloudflared` submodule, Apache-2.0; its `LICENSE` is retained in the submodule.
- `vendor/ngrok-java` is the upstream fork submodule at `danielealbano/ngrok-java`, dual Apache-2.0 or MIT; its `LICENSE` is retained.
- The Android/Kotlin runtime dependencies are resolved from the Gradle version catalog and lock metadata and carry their own licenses. A complete generated dependency-license bundle/SBOM remains required before a production binary release.
- DB-IP geolocation data, Google Play services artifacts, native tunnel binaries and any downloaded build tools remain subject to their upstream distribution terms. Do not redistribute local caches, credentials or downloaded artifacts merely because they are present on a build machine.
