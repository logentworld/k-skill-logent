#!/usr/bin/env python3
"""upstream 각 스킬의 `skill.json`에서 플러그인 카탈로그와 내장 메타데이터를 생성한다.

원본 메타데이터는 upstream 각 스킬 디렉터리의 `skill.json` 한 곳만 읽어요.
`skills()`는 동기 API라 호스트 자산을 읽을 수 없으므로 이름·설명을 DEX에 넣고,
JVM 테스트가 생성 자산과 내장 목록이 같은지 확인해요.
"""
from __future__ import annotations

import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT_KOTLIN = ROOT / "plugin/src/main/kotlin/com/sleepysoong/logent/kskill/KSkillCatalog.kt"
OUT_CATALOG = ROOT / "plugin/src/main/assets/plugins/k-skill/catalog.json"
MAX_DESCRIPTION = 500


def kotlin_string(value: str) -> str:
    out = []
    for ch in value:
        if ch == "\\":
            out.append("\\\\")
        elif ch == '"':
            out.append('\\"')
        elif ch == "$":
            out.append("\\$")
        elif ch == "\n":
            out.append("\\n")
        elif ch == "\r":
            out.append("\\r")
        elif ch == "\t":
            out.append("\\t")
        elif ord(ch) < 0x20 or ord(ch) == 0x7F:
            out.append(f"\\u{ord(ch):04x}")
        else:
            out.append(ch)
    return "".join(out)


def utf16_length(text: str) -> int:
    return len(text.encode("utf-16-le")) // 2


def clamp(text: str) -> str:
    text = text.strip()
    if utf16_length(text) <= MAX_DESCRIPTION:
        return text
    budget = MAX_DESCRIPTION - 1
    kept = []
    used = 0
    for ch in text:
        width = utf16_length(ch)
        if used + width > budget:
            break
        kept.append(ch)
        used += width
    return "".join(kept).rstrip() + "…"


def load_entries() -> list[dict[str, str]]:
    entries = {}
    for path in sorted(ROOT.glob("*/skill.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        name = str(data.get("name", "")).strip()
        description = clamp(str(data.get("description", "")))
        if not name or name in entries:
            raise SystemExit(f"이름이 비었거나 중복됐어요: {name!r} ({path})")
        if utf16_length(description) > MAX_DESCRIPTION:
            raise SystemExit(f"설명이 너무 길어요: {name}")
        entries[name] = description
    if not entries:
        raise SystemExit("k-skill 카탈로그가 비어 있어요.")
    return [{"name": name, "description": entries[name]} for name in sorted(entries)]


def main() -> int:
    data = load_entries()
    OUT_CATALOG.parent.mkdir(parents=True, exist_ok=True)
    OUT_CATALOG.write_text(
        json.dumps(data, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    lines = [
        "package com.sleepysoong.logent.kskill",
        "",
        "/**",
        " * upstream 각 스킬의 `skill.json`에서 생성한 k-skill 카탈로그예요.",
        f" * `skills()`가 네트워크 없이 {len(data)}개 이름·설명을 노출하도록 DEX에 함께 넣어요.",
        " * 설명은 PluginSkill 계약의 500자 상한에 맞춰 자르고, 본문은 `PluginSkill.read`가 npx로 가져와요.",
        " */",
        "internal object KSkillCatalog {",
        "    data class Entry(val name: String, val description: String)",
        "",
        "    val entries: List<Entry> = listOf(",
    ]
    for item in data:
        lines.append(
            f'        Entry("{kotlin_string(item["name"])}", "{kotlin_string(item["description"])}"),'
        )
    lines += ["    )", "}", ""]
    OUT_KOTLIN.parent.mkdir(parents=True, exist_ok=True)
    OUT_KOTLIN.write_text("\n".join(lines), encoding="utf-8")
    print(f"k-skill {len(data)}개를 생성했어요.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
