rootProject.name = "blog-mcp"

// ADR 0010: the app in `core`, and one module per embedding provider.
include(
    "core",
    "providers:onnx",
    "providers:gemma2-common",
    "providers:gemma2-server",
)
