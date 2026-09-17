"""
Pyflakes Layer Wrapper for ZCODE Engine Spike Intelligence.

Handles fast, in-memory static analysis and linting without file I/O or executing code.
"""
"""
Provenance: port dari PR #31 (head 8775958) — bagian dari ZCODE (GPLv3).
Adaptasi v1.0.23: lihat docs/RFC_V1023_SPIKE_INTELLIGENCE.md §8.
"""


import logging
import ast
from typing import List, Dict, Any
from . import PyflakesError

logger = logging.getLogger(__name__)

class PyflakesLinter:
    """Wrapper around Pyflakes static analysis library."""

    def __init__(self):
        self._pyflakes = None

    def _ensure_pyflakes(self):
        if self._pyflakes is None:
            try:
                import pyflakes.api
                import pyflakes.reporter
                import pyflakes.messages
                self._pyflakes = {
                    "api": pyflakes.api,
                    "reporter": pyflakes.reporter,
                    "messages": pyflakes.messages
                }
            except ImportError as e:
                logger.warning(f"Pyflakes library not available: {e}")
                raise PyflakesError(f"Pyflakes library is not installed: {e}") from e

    def is_available(self) -> bool:
        """Check if Pyflakes is available."""
        try:
            self._ensure_pyflakes()
            return True
        except PyflakesError:
            return False

    def lint(self, code: str, filename: str = "untitled.py") -> List[Dict[str, Any]]:
        """
        Run Pyflakes linting on code.
        Returns a list of issue dictionaries.
        """
        if not code:
            return []

        try:
            self._ensure_pyflakes()

            class ListReporter:
                def __init__(self):
                    self.messages = []

                def unexpectedError(self, filename, msg):
                    self.messages.append({
                        "filename": filename,
                        "line": 1,
                        "column": 0,
                        "message": f"Unexpected error: {msg}",
                        "severity": "error"
                    })

                def syntaxError(self, filename, msg, lineno, offset, text):
                    self.messages.append({
                        "filename": filename,
                        "line": lineno or 1,
                        "column": offset or 0,
                        "message": f"SyntaxError: {msg}",
                        "severity": "error"
                    })

                def flake(self, message):
                    self.messages.append({
                        "filename": getattr(message, "filename", filename),
                        "line": getattr(message, "lineno", 1),
                        "column": getattr(message, "col", 0),
                        "message": message.message % message.message_args if hasattr(message, "message_args") and message.message_args else str(message),
                        "severity": "warning"
                    })

            reporter = ListReporter()
            self._pyflakes["api"].check(code, filename, reporter)
            return reporter.messages

        except PyflakesError:
            # Fallback using builtin ast parsing if pyflakes is absent
            return self._fallback_ast_lint(code, filename)
        except Exception as e:
            logger.error(f"Error during Pyflakes linting: {e}")
            return []

    def _fallback_ast_lint(self, code: str, filename: str) -> List[Dict[str, Any]]:
        """Fallback linting using built-in ast module if Pyflakes is missing."""
        try:
            ast.parse(code, filename=filename)
            return []
        except SyntaxError as se:
            return [{
                "filename": filename,
                "line": se.lineno or 1,
                "column": se.offset or 0,
                "message": f"SyntaxError: {se.msg}",
                "severity": "error"
            }]
        except Exception as e:
            return [{
                "filename": filename,
                "line": 1,
                "column": 0,
                "message": f"Parsing Error: {str(e)}",
                "severity": "error"
            }]
