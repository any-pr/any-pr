#Written by Buger2008(With AI) License:MIT
import os
import requests

url = "https://maas-api.unisound.com/v1/systemone"

API_KEY = os.environ.get("UNISOUND_API_KEY", "KEY")

headers = {
    "Authorization": f"Bearer {API_KEY}",
    "Content-Type": "application/json",
}

# 规则部分：结构化、放在 state 里，作为固定上下文
RULES = """
You are a localization decision engine for a CodeSMART 2013 for VB6 Chinese localization project.
Given ONE string extracted from the project's string table, decide:
  TRANSLATE  -> it is still English natural-language UI text and should be localized.
  SKIP       -> it must be kept as-is.

## Context
- The project is partially localized into Chinese. Some strings are already Chinese.
- Strings may be: English UI text, Chinese UI text, identifiers, paths, file names,
  SQL fragments, VB6 keywords, version numbers, punctuation, or mixed content.
- Only English natural-language UI text should be translated.

## Decision procedure (apply in order, first match wins)
Step 1. If the string contains ANY CJK character -> SKIP.   (rule C1)
Step 2. If the string contains NO ASCII letters and NO digits -> SKIP.  (rule C2)
Step 3. Apply SKIP rules S1..S10. If any matches -> SKIP.
Step 4. Apply TRANSLATE rules T1..T6. If any matches -> TRANSLATE.
Step 5. Otherwise -> SKIP.

## CJK rule (highest priority)
C1. Any string containing Chinese/Japanese/Korean characters is already localized
    or intentionally mixed. Output SKIP, regardless of other content.
    Examples: "书签已删除。", "当前插入位置：", "'. 插入位置",
              "确定要删除所选书签吗？", "跳过标有“CSEH： Skip”的构件/组件".

## Pure-symbol rule (second priority)
C2. If the string contains NO ASCII letter (A-Z, a-z) and NO digit (0-9),
    output SKIP, even if it contains ':' or other punctuation.
    Examples: "...", "()", "[]", "|", ", ", " _", "&", "\r\n", "\t", ") '", ") ".

## SKIP rules (identifiers, code, paths, brands)
S1.  Pure identifier: no whitespace, only letters/digits/underscore,
     and NOT a natural English word in context.
     Examples: "FontSize", "Fake", "Unassigned", "CodeSMART", "VB6".
S2.  Dotted class/method name: segments joined by '.', at least one segment
     starts with an uppercase letter followed by lowercase (PascalCase).
     Examples: "AxBookmarks.CBookmark.Init",
               "AxCodeAnalyzer.VBCaPreprocess.PreProcess".
S2b. Dotted name where ALL segments are ALL-CAPS, digits, or known file
     extensions -> treat as identifier or file name.
     Examples: "VB6.GMR", "VB6.GMR.DLL", "MSVBVM60.DLL".
S3.  CamelCase or concatenated words without spaces.
     Examples: "BeforeFormatPage", "imgNode", "FontFace", "InsertCSBmks".
S4.  File path, registry key, or file name (contains '\' or ends with a known
     extension like .dll/.lyt/.mdb/.udp).
     Examples: "Settings\Bookmark", "iwf0.lyt", "AxCS.dll".
S5.  Pure number or version.
     Examples: "2.0", "32770", "#P0#", "6.0.81.76".
S6.  VB6 keyword / SQL fragment / code statement.
     Examples: "If * Then *", "SELECT * FROM ...", "[Get]", "[Let]", "[Set]",
               "End Property", "Exit Sub".
S7.  Single English word WITHOUT whitespace and WITHOUT punctuation.
     This rule applies ONLY when the string is exactly one word made of letters
     (optionally ending with a letter). If the string has spaces, '&', '...',
     ':', or any punctuation, S7 does NOT apply.
     Examples (SKIP): "Code", "Line", "Flag", "True", "False", "Name".
     Non-examples (do NOT match S7): "Current insert position:" (has spaces
     and ':'), "Loading..." (has '...').
S8.  Product/brand name with no sentence structure.
     Examples: "Microsoft Visual Basic", "Windows", "CodeSMART".
S9.  Menu accelerator with ONLY '&' + one short token, and no natural-language
     verb/noun phrase. Examples: "&", "&&". (Note: "&Start", "&Copy" are
     TRANSLATE, see T2.)
S10. Command-line / format string with placeholders only, no natural language.
     Examples: "%s", "%d %s", "{}".

## TRANSLATE rules (English UI text)
T1. Complete English sentence or phrase with natural-language structure
    (subject/verb/object, or a UI prompt). Contains at least two English words
    and at least one lowercase word.
    Examples: "Are you sure you want to clear this pane?",
              "Are you sure you want to clear this pane?".
T2. Menu/button text containing '&' accelerator AND at least one English word.
    Examples: "&Start", "&Copy", "&Copy Region", "&Delete".
T3. Text containing '...' as a UI hint AND at least one English word.
    Examples: "Loading...", "Searching...".
    NOTE: "..." alone is SKIP (C2), not T3.
T4. Text containing ':' that is part of a natural-language prompt.
    Examples: "Analyzing member:", "Warning:", "Current insert position:".
    IMPORTANT: T4 takes precedence over S7. Any string with whitespace and a
    trailing or embedded ':' in a natural-language context is TRANSLATE.
T5. Compiler / error / warning message in English.
    Examples: "'Option Explicit' statement not found in the component's code.".
T6. Natural-language string containing escape sequences like \b, \e, \t used
    as inline formatting markers.
    Examples: "The class \bmust have an \baccessor member named: \eItem".

## Priority and disambiguation
- Step 1 (C1 CJK) and Step 2 (C2 pure symbols) ALWAYS win, before any S/T rule.
- Among S and T rules: if a string matches both, SKIP wins.
- S7 must NEVER match a string that has whitespace or punctuation.
- T4 must NEVER be overridden by S7 for strings with whitespace and ':'.
- Default when nothing matches: SKIP.

## Output format
Respond with exactly ONE token, uppercase: TRANSLATE or SKIP.
No explanation, no quotes, no punctuation.
"""

# Few-shot 示例（可选，但强烈建议）
FEW_SHOT = [
    # --- C1: 含中文 -> SKIP ---
    {"text": "书签已删除。", "label": "SKIP"},
    {"text": "当前插入位置：", "label": "SKIP"},
    {"text": "'. 插入位置", "label": "SKIP"},
    {"text": "确定要删除所选书签吗？", "label": "SKIP"},
    {"text": "跳过标有“CSEH： Skip”的构件/组件", "label": "SKIP"},

    # --- C2: 纯符号 -> SKIP ---
    {"text": "...", "label": "SKIP"},
    {"text": "()", "label": "SKIP"},
    {"text": "&", "label": "SKIP"},
    {"text": "\r\n", "label": "SKIP"},
    {"text": ") '", "label": "SKIP"},
    {"text": " _", "label": "SKIP"},

    # --- S1/S2/S2b/S3/S4/S5/S6/S7/S8 ---
    {"text": "FontSize", "label": "SKIP"},
    {"text": "CodeSMART", "label": "SKIP"},
    {"text": "AxBookmarks.CBookmark.Init", "label": "SKIP"},
    {"text": "AxCodeAnalyzer.VBCaPreprocess.PreProcess", "label": "SKIP"},
    {"text": "VB6.GMR", "label": "SKIP"},
    {"text": "VB6.GMR.DLL", "label": "SKIP"},
    {"text": "BeforeFormatPage", "label": "SKIP"},
    {"text": "InsertCSBmks", "label": "SKIP"},
    {"text": "Settings\\Bookmark", "label": "SKIP"},
    {"text": "2.0", "label": "SKIP"},
    {"text": "If * Then *", "label": "SKIP"},
    {"text": "[Get]", "label": "SKIP"},
    {"text": "Code", "label": "SKIP"},
    {"text": "Line", "label": "SKIP"},
    {"text": "Microsoft Visual Basic", "label": "SKIP"},

    # --- T1/T2/T3/T4/T5/T6 ---
    {"text": "Are you sure you want to clear this pane?", "label": "TRANSLATE"},
    {"text": "&Start", "label": "TRANSLATE"},
    {"text": "&Copy Region", "label": "TRANSLATE"},
    {"text": "Loading...", "label": "TRANSLATE"},
    {"text": "Searching...", "label": "TRANSLATE"},
    {"text": "Analyzing member:", "label": "TRANSLATE"},
    {"text": "Current insert position:", "label": "TRANSLATE"},
    {"text": "Warning:", "label": "TRANSLATE"},
    {"text": "'Option Explicit' statement not found in the component's code.", "label": "TRANSLATE"},
    {"text": "The class \\bmust have an \\baccessor member named: \\eItem", "label": "TRANSLATE"},
]


def result(text: str) -> str:
    # 把 few-shot 拼进 state，作为参考
    examples = "\n".join(
        f'Text: {e["text"]}\nLabel: {e["label"]}' for e in FEW_SHOT
    )

    payload = {
        "model": "u2-decision",
        "state": f"{RULES}\n\n## Examples\n{examples}",
        "questions": {
            "decision": {
                "type": "noul",
                "instructions": (
                    "Decide whether the following VB6 string should be translated. "
                    "Answer with exactly one token: TRANSLATE or SKIP.\n"
                    f"Text: <<<{text}>>>"
                ),
            },
        },
    }

    response = requests.post(url, headers=headers, json=payload, timeout=30)
    response.raise_for_status()
    print("status:", response.status_code)
    print("raw   :", response.text)

    try:
        data = response.json()
        # 根据实际返回结构调整，下面只是示例
        label = (
            data.get("answers", {}).get("decision")
            or data.get("decision")
            or data.get("result")
        )
        return label
    except ValueError:
        return response.text


if __name__ == "__main__":
    test_cases = [
        "类未注册：搜索此对象？",
        "书签已删除。",
        "AxBookmarks.CBookmark.Init",
        "CodeSMART",
        "确定要删除所选书签吗？",
        "AxBookmarks.CBookmark.InsertCSBmks",
        "VB6.GMR",
        "AxBookmarks.CBookmark.Insert",
        "当前插入位置：",
        "'. 插入位置",
        "\r\n",
        ") ",
        "书签类型未...",
        "AxBookmarks.FInsertBmk.Init",
        "AxBookmarks.FInsertBmk.GetOption",
        " _",
        "AxBookmarks.FRemoveBmk.Init",
        "VB6.GMR.DLL",
        "Current insert position:",
    ]

    for t in test_cases:
        print(test_cases.index(t))
        label = result(t)
        print(t)
