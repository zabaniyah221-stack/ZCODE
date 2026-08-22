from pathlib import Path

ROOT = Path(__file__).resolve().parent


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def test_gpl_text_is_exactly_the_copy_packaged_in_apk():
    root = (ROOT / "LICENSE").read_bytes()
    packaged = (ROOT / "app/src/main/assets/licenses/GPL-3.0.txt").read_bytes()
    assert root == packaged
    text = root.decode("utf-8")
    for marker in (
        "GNU GENERAL PUBLIC LICENSE",
        "Version 3, 29 June 2007",
        "END OF TERMS AND CONDITIONS",
        "How to Apply These Terms to Your New Programs",
    ):
        assert marker in text


def test_independent_mit_notice_is_preserved_not_merely_named():
    root = (ROOT / "LICENSES/MIT.txt").read_bytes()
    packaged = (ROOT / "app/src/main/assets/licenses/MIT.txt").read_bytes()
    assert root == packaged
    text = root.decode("utf-8")
    assert "Permission is hereby granted, free of charge" in text
    assert "Copyright (c) 2026 ZCODE contributors" in text


def test_notice_has_exact_provenance_change_notice_and_packaged_copy():
    notice = read("NOTICE")
    assert notice == read("app/src/main/assets/licenses/NOTICE.txt")
    for marker in (
        "Copyright (C) 2026 Zaqi (muzape28-blip) and ZABACODE Contributors",
        "1e334ff07e83938e9b9b5c038649736bb758ed5a",
        "zabacode/plugins/implementations.py",
        "zabacode/plugins/registry.py",
        "ZCODE changes include",
        "https://github.com/muzape28-blip/ZCODE",
        "without warranty",
    ):
        assert marker in notice


def test_readme_and_about_do_not_return_to_mit_only_or_sole_holder_claims():
    readme = read("README.md")
    about = read("app/src/main/java/com/zaba/zcode/ui/settings/AboutScreen.kt")
    active = (readme + "\n" + about + "\n" + read("NOTICE")).lower()
    for forbidden in (
        "license-mit",
        "zcode dirilis di bawah [mit license]",
        "sesuai lisensi mit",
        "sole copyright holder",
        "sole owner",
    ):
        assert forbidden not in active
    assert "license-gplv3" in readme.lower()
    assert "licenses/GPL-3.0.txt" in about
    assert "software ini tanpa jaminan" in about.lower()
    assert "ZCODE_SOURCE_URL" in about


def test_combined_distribution_wording_is_consistent():
    readme = read("README.md")
    notice = read("NOTICE")
    assert "aplikasi gabungan" in readme
    assert "GPL-3.0-only" in notice
    assert "distribusi gabungan tetap\nGPLv3" in readme


def test_derived_source_headers_credit_contributors_without_relicense_claim():
    plugin = read("app/src/main/python/zcode_plugins.py")
    snippets = read("app/src/main/java/com/zaba/zcode/core/plugins/SnippetLibrary.kt")
    editor = read("editor-src/src/editor.js")
    combined = "\n".join((plugin, snippets, editor)).lower()
    assert "zabacode contributors" in combined
    assert "root notice" in combined
    assert "same author" not in combined
    assert "additionally licensed" not in combined


def test_production_workflow_verifies_exact_license_assets_in_apk():
    workflow = read(".github/workflows/production.yml")
    mirror = read("ci/workflows/production.yml")
    assert workflow == mirror
    assert "for license_asset in GPL-3.0.txt MIT.txt NOTICE.txt" in workflow
    assert 'unzip -p "$APK" "assets/licenses/$license_asset"' in workflow
    assert '| cmp - "app/src/main/assets/licenses/$license_asset"' in workflow
