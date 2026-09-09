"""
Editor Intelligence Package for ZCODE (v1.0.23).

Layers:
- Jedi (Completion & Go-to definition)
- Parso (AST Parsing & error recovery)
- Pyflakes (Linting & Static Analysis)
- Cabe (McCabe Complexity Analysis)

Provenance: port dari PR #31 (head 8775958, agen eksternal) dengan adaptasi
v1.0.23: rope_layer TIDAK di-port (ditunda — kontrak rename fail-closed belum
dibuat; lihat docs/RFC_V1023_SPIKE_INTELLIGENCE.md §8). Bagian ZCODE (GPLv3).
"""


class SpikeError(Exception):
    """Base class for all Spike Intelligence Engine errors."""
    pass


class JediError(SpikeError):
    """Exception raised by Jedi layer operations."""
    pass


class ParsoError(SpikeError):
    """Exception raised by Parso layer operations."""
    pass


class PyflakesError(SpikeError):
    """Exception raised by Pyflakes layer operations."""
    pass


class CabeError(SpikeError):
    """Exception raised by Cabe layer operations."""
    pass


__all__ = [
    "SpikeError",
    "JediError",
    "ParsoError",
    "PyflakesError",
    "CabeError",
]
