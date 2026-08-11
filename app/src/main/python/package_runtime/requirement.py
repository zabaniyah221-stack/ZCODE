"""
requirement — parser requirement Python (SPEC-001 §12 Manual Install).

Dibangun di atas `packaging.requirements.Requirement` (sudah ter-bundle bersama
pip 23.3.1 di Chaquopy). Yang dilakukan di sini:
- validasi ketat: tolak input yang bukan requirement (shell command, flag pip,
  path, URL, dll) — Manual Install ADALAH requirement interface, bukan terminal.
- parse ke dict sederhana agar Kotlin bisa menampilkan field.
- dukungan: `requests`, `requests==2.32.3`, `pydantic>=2,<3`, `numpy==1.26.*`,
  `flask[async]`, dan baris-baris requirements.txt (komentar #, baris kosong).
"""
import re

from packaging.requirements import Requirement
from packaging.utils import canonicalize_name

# Karakter/pattern yang menandakan ini bukan requirement biasa.
_FORBIDDEN_PATTERNS = [
    r"\brm\b", r"\bcurl\b", r"\bwget\b", r"\bmkdir\b", r"\bcd\s", r"\bsudo\b",
    r"&&", r"\|\|", r";\s*$", r"`", r"\$\(", r"--trusted-host", r"--index-url",
    r"--extra-index-url", r"--target", r"--upgrade", r"--force",
    r"^[-+]",           # flag / opsi CLI
]

_REQ_PATTERN = re.compile(
    r"^[A-Za-z0-9._-]+(\[[A-Za-z0-9,._-]*\])?"
    r"(\s*(===|==|!=|<=|>=|<|>|~=)\s*[A-Za-z0-9.*+!\-]+"
    r"(\s*,\s*(===|==|!=|<=|>=|<|>|~=)\s*[A-Za-z0-9.*+!\-]+)*)?"
    r"(\s*;\s*.+)?$"
)


class RequirementError(ValueError):
    """Requirement tidak valid / dilarang — pesan siap tampil ke user."""


def _looks_forbidden(text: str) -> str | None:
    for pat in _FORBIDDEN_PATTERNS:
        if re.search(pat, text):
            return pat
    return None


def parse_requirement(text: str) -> dict:
    """
    Parse satu baris requirement. Return dict:
    {name, canonical_name, extras: [..], specifier: str, marker: str|None, raw}
    Raise RequirementError dengan pesan ramah bila tidak valid.
    """
    raw = (text or "").strip()
    if not raw:
        raise RequirementError("Requirement kosong.")
    if len(raw) > 500:
        raise RequirementError("Requirement terlalu panjang (maks 500 karakter).")

    # cek dini pola requirement sebelum packaging (pesan error lebih ramah)
    if not _REQ_PATTERN.match(raw):
        forbidden = _looks_forbidden(raw)
        if forbidden:
            raise RequirementError(
                "Input mengandung pola yang dilarang (%s).\n"
                "Manual Install hanya menerima requirement Python, "
                "bukan perintah shell/opsi pip." % forbidden
            )
        raise RequirementError(
            "Format requirement tidak dikenali: '%s'.\n"
            "Contoh: requests, requests==2.32.3, pydantic>=2,<3, numpy==1.26.*, flask[async]"
            % raw
        )

    try:
        req = Requirement(raw)
    except Exception as e:
        raise RequirementError("Requirement tidak valid: %s" % e)

    if req.url:
        raise RequirementError("Install dari URL/VCS tidak didukung (wheel-only, MVP).")

    return {
        "name": req.name,
        "canonical_name": canonicalize_name(req.name),
        "extras": sorted(req.extras),
        "specifier": str(req.specifier) if req.specifier else "",
        "marker": str(req.marker) if req.marker else None,
        "raw": raw,
    }


def validate_requirement_text(text: str) -> str | None:
    """Return pesan error atau None bila aman (dipakai Kotlin pre-check ringan)."""
    try:
        parse_requirement(text)
        return None
    except RequirementError as e:
        return str(e)


def parse_requirement_json(text: str) -> str:
    """Wrapper JSON-string untuk Kotlin (error → dict {ok:false} bukan exception)."""
    import json
    try:
        r = parse_requirement(text)
        r["ok"] = True
        return json.dumps(r, default=str)
    except RequirementError as e:
        return json.dumps({"ok": False, "error": str(e)})


def parse_requirements_file(content: str) -> list[str]:
    """Parse isi requirements.txt → daftar baris requirement bersih."""
    out = []
    for line in (content or "").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        # dukung komentar inline (sebelum tanda # — hati-hati marker pakai ;)
        if " #" in line:
            line = line.split(" #", 1)[0].strip()
        if not line:
            continue
        parse_requirement(line)  # validasi → raise kalau ada baris nakal
        out.append(line)
    return out
