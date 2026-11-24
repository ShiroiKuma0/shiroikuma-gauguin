#!/usr/bin/env python3
"""Fork: generates the colour ROLE table of 白い熊 GNU Gauguin from the one table below.

Run from the repository root:  python3 tools/shiroikuma/gen_roles.py

Writes
  gauguin-app/src/main/kotlin/org/piepmeyer/gauguin/ui/customui/chrome/SkRoleTable.kt
                                                  the sections, the roles and their defaults
  gauguin-app/src/main/res/values/sk_roles.xml    one colour resource and one label per role
  gauguin-app/src/main/res/values-ja/sk_roles.xml the Japanese labels
  gauguin-app/src/main/res/values/sk_theme.xml    the overlay pointing theme attributes at roles

A role's default is one of
  "#AARRGGBB"            a literal (only the two primaries and the colours that carry meaning)
  ("=", ROLE)            another role
  ("=", ROLE, alpha)     another role at that alpha
  ("mix", fraction)      that far from BACKGROUND to ACCENT, opaque

A role whose key starts with "gauguin_ui_" and was shipped before the role table keeps that key,
so colours 白い熊 already set keep their meaning.
"""

# key, label, Japanese label, icon, the role that colours its icon (one of the seven SECTION_*).
# The order is the default order of the page: no two neighbours share a colour.
SECTIONS = [
    ("eximport", "Export / Import", "書き出し・読み込み", "EXPORT", "SECTION_REACH"),
    ("foundation", "Foundation", "基本", "PALETTE", "SECTION_BASE"),
    ("text", "Text", "テキスト", "TEXT", "SECTION_WORDS"),
    ("grid", "Grid", "盤面", "GRID", "SECTION_BASE"),
    ("keypad", "Keypad", "数字キー", "KEYCAP", "SECTION_CONTROLS"),
    ("top", "Top panel", "上部パネル", "PANEL", "SECTION_FRAME"),
    ("buttons", "Buttons", "ボタン", "POINTER", "SECTION_CONTROLS"),
    ("drawer", "Navigation drawer", "ドロワー", "MENU", "SECTION_FRAME"),
    ("dialogs", "Dialogs", "ダイアログ", "DIALOG", "SECTION_REACH"),
    ("switches", "Switches", "スイッチ", "TOGGLE", "SECTION_CONTROLS"),
    ("menus", "Menus", "メニュー", "LIST", "SECTION_REACH"),
    ("marks", "Check boxes and radio buttons", "チェックボックスとラジオボタン", "CHECK", "SECTION_CONTROLS"),
    ("lists", "Lists and cards", "一覧とカード", "LAYERS", "SECTION_FRAME"),
    ("settings", "Settings pages", "設定画面", "SLIDERS", "SECTION_BASE"),
    ("bars", "Bars", "バー", "DEVICE", "SECTION_FRAME"),
    ("toasts", "Toasts and hints", "通知とヒント", "SPARK", "SECTION_FLASH"),
    ("diagnostics", "Diagnostics", "診断", "GAUGE", "SECTION_WORDS"),
    ("maintenance", "Maintenance", "保守", "RESET", "SECTION_WARNING"),
]

Y, K = "#FFFFFF00", "#FF000000"
R = lambda role, a=None: ("=", role) if a is None else ("=", role, a)
P = "gauguin_ui_"
ROLES = [  # NAME, preference key, section, label, Japanese label, default
    ("BACKGROUND", P + "background", "foundation", "Background", "背景", K),
    ("ACCENT", P + "accent", "foundation", "Accent", "アクセント", Y),
    ("TEXT", P + "text", "foundation", "Text", "文字", R("ACCENT")),
    ("SURFACE", P + "surface", "foundation", "Surfaces", "面", R("BACKGROUND")),
    ("BORDER", P + "border_color", "foundation", "Frames", "枠", R("ACCENT")),
    ("NOTE", P + "note", "foundation", "Explanations (dim gold)", "説明文（暗い金色）", ("mix", 0.785)),

    ("TEXT_SECONDARY", P + "text_secondary", "text", "Secondary text", "補助の文字", R("NOTE")),
    ("TEXT_HINT", P + "text_hint", "text", "Hints", "入力の手がかり", ("mix", 0.55)),
    ("TEXT_DISABLED", P + "text_disabled", "text", "Disabled text", "無効の文字", R("TEXT", 0.38)),
    ("HEADING", P + "heading", "text", "Headings", "見出し", R("ACCENT")),
    ("LINK", P + "link", "text", "Links", "リンク", R("ACCENT")),
    ("TEXT_SELECTION", P + "text_selection", "text", "Text selection", "文字の選択範囲", R("ACCENT", 0.35)),
    ("ICON", P + "icon", "text", "Icons", "アイコン", R("ACCENT")),
    ("ICON_ACTIVE", P + "icon_active", "text", "Icons, active", "アイコン（有効時）", R("ACCENT")),
    ("ERROR", P + "ui_error", "text", "Errors", "エラー", "#FFFF0000"),
    ("ON_ACCENT", P + "on_accent", "text", "Text on an accent ground", "アクセント色の上の文字", R("BACKGROUND")),
    ("FIELD_TEXT", P + "field_text", "text", "Text fields, text", "入力欄の文字", R("TEXT")),
    ("FIELD_FRAME", P + "field_frame", "text", "Text fields, frame and underline", "入力欄の枠と下線", R("ACCENT")),

    ("GRID_BACKGROUND", P + "grid_background", "grid", "Grid background", "盤面の背景", R("BACKGROUND")),
    ("GRID_CELL_BACKGROUND", P + "grid_cell_background", "grid", "Cell background", "マスの背景", R("BACKGROUND")),
    ("GRID_SELECTED_BACKGROUND", P + "grid_selected_background", "grid", "Selected cell", "選択中のマス", ("mix", 0.2)),
    ("CAGE_BORDER", P + "cage_border_color", "grid", "Cage lines", "ブロックの線", R("ACCENT")),
    ("CELL_BORDER", P + "cell_border_color", "grid", "Cell lines", "マスの線", ("mix", 0.785)),
    ("VALUE", P + "value_color", "grid", "Numbers", "数字", R("TEXT")),
    ("VALUE_SELECTED", P + "value_selected_color", "grid", "Number in the selected cell", "選択中のマスの数字", R("TEXT")),
    ("CAGE_TEXT", P + "cage_text_color", "grid", "Cage result and operator", "ブロックの計算結果と演算記号", R("TEXT")),
    ("POSSIBLES", P + "possibles_color", "grid", "Pencil marks", "候補の数字", ("mix", 0.785)),
    ("GRID_ERROR", P + "error_color", "grid", "Mistakes", "間違い", "#FFFF5555"),
    ("GRID_WARNING", P + "warning_color", "grid", "Warnings", "警告", "#FFFFAA00"),
    ("GRID_CHEATED", P + "cheated_color", "grid", "Revealed cells", "答えを表示したマス", "#FF806000"),
    ("GRID_LAST_MODIFIED", P + "last_modified_color", "grid", "Last changed cell", "最後に変更したマス", ("mix", 0.1)),

    ("KEY_BACKGROUND", P + "key_background", "keypad", "Key ground", "キーの地", R("BACKGROUND")),
    ("KEY_TEXT", P + "key_text_color", "keypad", "Key label", "キーの文字", R("ACCENT")),
    ("KEY_BORDER", P + "key_border_color", "keypad", "Key frame", "キーの枠", R("ACCENT")),

    ("TOP_BACKGROUND", P + "top_background", "top", "Background", "背景", R("BACKGROUND")),
    ("TOP_FOREGROUND", P + "top_foreground", "top", "Text and icons", "文字とアイコン", R("ACCENT")),

    ("PILL_FRAME", P + "pill_frame", "buttons", "Frame", "枠", R("ACCENT")),
    ("PILL_TEXT", P + "pill_text", "buttons", "Label", "文字", R("ACCENT")),
    ("PILL_GROUND", P + "pill_ground", "buttons", "Ground", "地", "#00000000"),
    ("DELETE", P + "delete", "buttons", "Buttons that delete", "削除するボタン", "#FFFF0000"),
    ("FAB_FRAME", P + "fab_frame", "buttons", "Floating button, ring", "フローティングボタンの輪", R("ACCENT")),
    ("FAB_ICON", P + "fab_icon", "buttons", "Floating button, icon", "フローティングボタンのアイコン", R("ACCENT")),
    ("FAB_GROUND", P + "fab_ground", "buttons", "Floating button, ground", "フローティングボタンの地", R("BACKGROUND")),
    ("SEGMENT_SELECTED", P + "segment_selected", "buttons", "Chosen segment or chip", "選択中の区分とチップ", ("mix", 0.2)),

    ("DRAWER_BACKGROUND", P + "drawer_background", "drawer", "Background", "背景", R("BACKGROUND")),
    ("DRAWER_TEXT", P + "drawer_text_color", "drawer", "Text", "文字", R("TEXT")),
    ("DRAWER_FRAME", P + "drawer_frame", "drawer", "Frame", "枠", R("BORDER")),

    ("DIALOG_GROUND", P + "dialog_background", "dialogs", "Background", "背景", R("BACKGROUND")),
    ("DIALOG_FRAME", P + "dialog_border_color", "dialogs", "Frame", "枠", R("ACCENT")),
    ("DIALOG_TITLE", P + "dialog_title", "dialogs", "Title", "題名", R("TEXT")),
    ("DIALOG_TEXT", P + "dialog_text_color", "dialogs", "Text", "文字", R("TEXT")),
    ("DIALOG_BUTTON_TEXT", P + "dialog_button_text", "dialogs", "Button label", "ボタンの文字", R("PILL_TEXT")),
    ("DIALOG_BUTTON_FRAME", P + "dialog_button_frame", "dialogs", "Button frame", "ボタンの枠", R("PILL_FRAME")),
    ("DIALOG_MARK", P + "dialog_mark", "dialogs", "Check boxes and radio buttons", "チェックボックスとラジオボタン", R("MARK_ON")),
    ("DIALOG_DELETE", P + "dialog_delete", "dialogs", "Buttons that delete", "削除するボタン", R("DELETE")),

    ("SWITCH_ON", P + "switch_on", "switches", "On", "オン", R("ACCENT")),
    ("SWITCH_OFF", P + "switch_off", "switches", "Off", "オフ", ("mix", 0.55)),

    ("MENU_GROUND", P + "menu_ground", "menus", "Background", "背景", R("BACKGROUND")),
    ("MENU_FRAME", P + "menu_frame", "menus", "Frame", "枠", R("ACCENT")),
    ("MENU_LABEL", P + "menu_label", "menus", "Label", "文字", R("TEXT")),
    ("MENU_ROW_FRAME", P + "menu_row_frame", "menus", "Row frame", "行の枠", R("ACCENT")),
    ("MENU_DIVIDER", P + "menu_divider", "menus", "Divider", "区切り線", ("mix", 0.7)),
    ("ICON_CONNECT", P + "icon_connect", "menus", "Icon: start, restart, check", "アイコン：開始・やり直し・確認", "#FF00A550"),
    ("ICON_MESSAGE", P + "icon_message", "menus", "Icon: write, report", "アイコン：書く・報告", "#FF0066FF"),
    ("ICON_CONTACT", P + "icon_contact", "menus", "Icon: games and statistics", "アイコン：ゲームと統計", "#FF00B0FF"),
    ("ICON_SHARE", P + "icon_share", "menus", "Icon: save, load, export", "アイコン：保存・読み込み・書き出し", "#FFFF8000"),
    ("ICON_VIEW", P + "icon_view", "menus", "Icon: view, open, settings", "アイコン：表示・開く・設定", R("ACCENT")),
    ("ICON_BLOCK", P + "icon_block", "menus", "Icon: reveal, lock, keep secret", "アイコン：答えを表示・ロック・秘密", "#FFFF3D7F"),

    ("MARK_ON", P + "mark_on", "marks", "Chosen", "選択中", R("ACCENT")),
    ("MARK_OFF", P + "mark_off", "marks", "Not chosen", "未選択", ("mix", 0.55)),

    ("LIST_TEXT", P + "list_text_color", "lists", "Text", "文字", R("TEXT")),
    ("LIST_DIVIDER", P + "list_divider_color", "lists", "Divider", "区切り線", ("mix", 0.785)),
    ("LIST_PRESSED", P + "list_pressed", "lists", "Pressed row", "押している行", R("ACCENT", 0.3)),
    ("LIST_SELECTED", P + "list_selected", "lists", "Selected row", "選択中の行", ("mix", 0.2)),
    ("LIST_SELECTED_TEXT", P + "list_selected_text", "lists", "Selected row, text", "選択中の行の文字", R("TEXT")),
    ("CARD_GROUND", P + "card_ground", "lists", "Card ground", "カードの地", R("SURFACE")),
    ("OUTLINE", P + "outline", "lists", "Outlines", "輪郭線", R("BORDER")),
    ("OUTLINE_VARIANT", P + "outline_variant", "lists", "Outlines, quiet", "輪郭線（控えめ）", ("mix", 0.55)),
    ("CHART_ONE", P + "chart_one", "lists", "Statistics, first series", "統計：一つ目の系列", "#FF00B0FF"),
    ("CHART_TWO", P + "chart_two", "lists", "Statistics, second series", "統計：二つ目の系列", "#FFFF8000"),

    ("SETTINGS_TITLE", P + "settings_title", "settings", "Titles", "項目名", R("TEXT")),
    ("SETTINGS_SUMMARY", P + "settings_summary", "settings", "Explanations", "説明文", R("NOTE")),
    ("SETTINGS_ICON", P + "settings_icon", "settings", "Icons without a role", "役割のないアイコン", R("ACCENT")),
    ("SETTINGS_SLIDER", P + "settings_slider", "settings", "Sliders", "スライダー", R("ACCENT")),
    ("SECTION_BASE", P + "section_base", "settings", "Section icon: the base", "見出しアイコン：基本", R("ACCENT")),
    ("SECTION_CONTROLS", P + "section_controls", "settings", "Section icon: controls", "見出しアイコン：操作するもの", "#FF00A550"),
    ("SECTION_WORDS", P + "section_words", "settings", "Section icon: words", "見出しアイコン：言葉", "#FF00B0FF"),
    ("SECTION_FRAME", P + "section_frame", "settings", "Section icon: the frame", "見出しアイコン：枠組み", "#FF0066FF"),
    ("SECTION_REACH", P + "section_reach", "settings", "Section icon: reaching out", "見出しアイコン：外とのやり取り", "#FFFF8000"),
    ("SECTION_WARNING", P + "section_warning", "settings", "Section icon: warning", "見出しアイコン：警告", "#FFFF0000"),
    ("SECTION_FLASH", P + "section_flash", "settings", "Section icon: a brief flash", "見出しアイコン：一瞬の表示", "#FFFF3D7F"),

    ("STATUS_BAR", P + "status_bar", "bars", "Status bar", "ステータスバー", R("BACKGROUND")),
    ("NAVIGATION_BAR", P + "navigation_bar", "bars", "Navigation bar", "ナビゲーションバー", R("BACKGROUND")),
    ("BOTTOM_BAR_GROUND", P + "bottom_bar_ground", "bars", "Bottom bar, ground", "下部バーの地", R("SURFACE")),
    ("BOTTOM_BAR_ICON", P + "bottom_bar_icon", "bars", "Bottom bar, icons", "下部バーのアイコン", R("ACCENT")),

    ("SNACK_GROUND", P + "snack_ground", "toasts", "Message, ground", "メッセージの地", R("BACKGROUND")),
    ("SNACK_TEXT", P + "snack_text", "toasts", "Message, text", "メッセージの文字", R("TEXT")),
    ("SNACK_FRAME", P + "snack_frame", "toasts", "Message, frame", "メッセージの枠", R("ACCENT")),
    ("HINT_GROUND", P + "hint_ground", "toasts", "Mistake check, ground", "間違い確認の地", R("BACKGROUND")),
    ("HINT_TEXT", P + "hint_text", "toasts", "Mistake check, no mistakes", "間違い確認：間違いなし", R("TEXT")),
    ("HINT_ERROR", P + "hint_error", "toasts", "Mistake check, mistakes found", "間違い確認：間違いあり", "#FFFF0000"),
]

# Theme attribute -> role. Upstream's own attributes and Material's; the overlay holds no value.
ATTRS = [
    ("android:windowBackground", "BACKGROUND"), ("android:colorBackground", "BACKGROUND"),
    ("colorSurface", "BACKGROUND"), ("colorSurfaceVariant", "CARD_GROUND"), ("colorSurfaceContainer", "CARD_GROUND"),
    ("colorSurfaceContainerHigh", "CARD_GROUND"), ("colorSurfaceContainerHighest", "CARD_GROUND"),
    ("colorSurfaceContainerLow", "CARD_GROUND"), ("colorSurfaceContainerLowest", "CARD_GROUND"),
    ("colorSurfaceDim", "BACKGROUND"), ("colorSurfaceBright", "BACKGROUND"),
    ("colorOnSurface", "TEXT"), ("colorOnBackground", "TEXT"), ("colorOnSurfaceVariant", "TEXT_SECONDARY"),
    ("colorPrimary", "ACCENT"), ("colorSecondary", "ACCENT"), ("colorTertiary", "ACCENT"), ("colorAccent", "ACCENT"),
    ("colorPrimaryInverse", "ACCENT"),
    ("colorOnPrimary", "ON_ACCENT"), ("colorOnSecondary", "ON_ACCENT"), ("colorOnTertiary", "ON_ACCENT"),
    ("colorOnError", "ON_ACCENT"),
    ("colorPrimaryContainer", "SEGMENT_SELECTED"), ("colorSecondaryContainer", "SEGMENT_SELECTED"),
    ("colorTertiaryContainer", "SEGMENT_SELECTED"), ("colorErrorContainer", "SEGMENT_SELECTED"),
    ("colorOnPrimaryContainer", "LIST_SELECTED_TEXT"), ("colorOnSecondaryContainer", "LIST_SELECTED_TEXT"),
    ("colorOnTertiaryContainer", "LIST_SELECTED_TEXT"),
    ("colorPrimaryFixed", "SEGMENT_SELECTED"), ("colorPrimaryFixedDim", "SEGMENT_SELECTED"),
    ("colorSecondaryFixed", "SEGMENT_SELECTED"), ("colorSecondaryFixedDim", "SEGMENT_SELECTED"),
    ("colorTertiaryFixed", "SEGMENT_SELECTED"), ("colorTertiaryFixedDim", "SEGMENT_SELECTED"),
    ("colorOnPrimaryFixed", "LIST_SELECTED_TEXT"), ("colorOnPrimaryFixedVariant", "LIST_SELECTED_TEXT"),
    ("colorOnSecondaryFixed", "LIST_SELECTED_TEXT"), ("colorOnSecondaryFixedVariant", "LIST_SELECTED_TEXT"),
    ("colorOnTertiaryFixed", "LIST_SELECTED_TEXT"), ("colorOnTertiaryFixedVariant", "LIST_SELECTED_TEXT"),
    ("colorError", "ERROR"), ("colorOnErrorContainer", "ERROR"),
    ("colorOutline", "OUTLINE"), ("colorOutlineVariant", "OUTLINE_VARIANT"),
    ("colorSurfaceInverse", "SNACK_GROUND"), ("colorOnSurfaceInverse", "SNACK_TEXT"),
    ("colorControlNormal", "ICON"), ("colorControlActivated", "ICON_ACTIVE"), ("colorControlHighlight", "LIST_PRESSED"),
    ("android:textColor", "TEXT"), ("android:textColorTertiary", "TEXT_HINT"), ("android:textColorHint", "TEXT_HINT"),
    ("android:textColorHighlight", "TEXT_SELECTION"), ("android:textColorLink", "LINK"),
    ("android:editTextColor", "FIELD_TEXT"), ("editTextColor", "FIELD_TEXT"),
    ("android:statusBarColor", "STATUS_BAR"), ("android:navigationBarColor", "NAVIGATION_BAR"),
    # upstream's own
    ("colorCustomColor1", "CHART_ONE"), ("colorOnCustomColor1", "ON_ACCENT"),
    ("colorCustomColor1Container", "SEGMENT_SELECTED"), ("colorOnCustomColor1Container", "LIST_SELECTED_TEXT"),
    ("colorCustomColor2", "CHART_TWO"), ("colorOnCustomColor2", "ON_ACCENT"),
    ("colorCustomColor2Container", "SEGMENT_SELECTED"), ("colorOnCustomColor2Container", "LIST_SELECTED_TEXT"),
    ("colorMainTopPanelForeground", "TOP_FOREGROUND"), ("colorMainTopPanelBackground", "TOP_BACKGROUND"),
    ("colorMainHintPopupSuccessForeground", "HINT_TEXT"), ("colorMainHintPopupSuccessBackground", "HINT_GROUND"),
    ("colorMainHintPopupErrorsForeground", "HINT_ERROR"), ("colorMainHintPopupErrorsBackground", "HINT_GROUND"),
    ("colorMainFabForeground", "FAB_ICON"), ("colorMainFabBackground", "FAB_GROUND"),
    ("colorGridValue", "VALUE"), ("colorGridSelected", "GRID_SELECTED_BACKGROUND"),
    ("colorGridSelectedText", "VALUE_SELECTED"), ("colorGridCage", "CAGE_BORDER"),
    ("colorGridCageText", "CAGE_TEXT"), ("colorGridWarningText", "GRID_WARNING"),
]

by_name = {r[0]: r for r in ROLES}
section_keys = [s[0] for s in SECTIONS]


def rgba(h):
    v = int(h[1:], 16)
    return [(v >> 24) & 255, (v >> 16) & 255, (v >> 8) & 255, v & 255]


def value(name):
    d = by_name[name][5]
    if isinstance(d, str):
        return rgba(d)
    if d[0] == "=":
        c = value(d[1])
        return [int(c[0] * d[2]), c[1], c[2], c[3]] if len(d) == 3 else c
    a, b = value("BACKGROUND"), value("ACCENT")
    return [255] + [round(a[i] + (b[i] - a[i]) * d[1]) for i in (1, 2, 3)]


def hexs(c):
    return "#%02X%02X%02X%02X" % tuple(c)


def kdefault(d):
    if isinstance(d, str):
        return "0x%s.toInt()" % d[1:]
    if d[0] == "=":
        return "of(SkRole.%s)" % d[1] if len(d) == 2 else "alpha(of(SkRole.%s), %sf)" % (d[1], d[2])
    return "mix(%sf)" % d[1]


def esc(s):
    return s.replace("'", "\\'")


J = "gauguin-app/src/main/kotlin/org/piepmeyer/gauguin/ui/customui/chrome/SkRoleTable.kt"
V = "gauguin-app/src/main/res/values/"
VJ = "gauguin-app/src/main/res/values-ja/"
HEAD = "GENERATED by tools/shiroikuma/gen_roles.py — edit the table there, never this file."

for n, key, sec, *_ in ROLES:
    assert sec in section_keys, sec
for a, r in ATTRS:
    assert r in by_name, r
for s in SECTIONS:
    assert s[4] in by_name, s[4]
assert len({r[1] for r in ROLES}) == len(ROLES), "duplicate preference key"
prev = None
for s in SECTIONS:
    assert s[4] != prev, "neighbouring sections share a colour: " + s[0]
    prev = s[4]

kt = ["// " + HEAD, "", "package org.piepmeyer.gauguin.ui.customui.chrome", "",
      "import androidx.annotation.ColorRes", "import androidx.annotation.StringRes", "import org.piepmeyer.gauguin.R", "",
      "/**", " * A top-level section of the 白い熊 GNU Gauguin UI page, in its default order. [icon] and [color] are",
      " * shared by the section's heading and its chip, so the two cannot drift apart.", " */",
      "enum class SkSection(", "    val key: String,", "    @StringRes val labelRes: Int,", "    val icon: SkIcons.Icon,",
      "    private val colorRole: String,", ") {"]
kt += ["    %s(\"%s\", R.string.sk_section_%s, SkIcons.Icon.%s, \"%s\")," % (k.upper(), k, k, icon, role)
       for k, _, _, icon, role in SECTIONS]
kt += ["    ;", "", "    /** The role that colours the section's icon: one of the seven section-icon colours */",
       "    val color: SkRole get() = SkRole.valueOf(colorRole)", "}", "", "/**",
       " * One settable colour. [key] is its preference key (absent = the default); [colorRes] is the",
       " * colour resource the theme overlay names for it, fed at run time by SkPalette.", " */",
       "enum class SkRole(", "    val key: String,", "    val section: SkSection,", "    @StringRes val labelRes: Int,",
       "    @ColorRes val colorRes: Int,", ") {"]
for n, key, sec, *_ in ROLES:
    low = n.lower()
    kt.append("    %s(\"%s\", SkSection.%s, R.string.sk_role_label_%s, R.color.sk_role_%s)," % (n, key, sec.upper(), low, low))
kt += ["}", "", "/** The default of [role]: the two primaries are written here once, everything else derives */",
       "internal fun skRoleDefault(", "    role: SkRole,", "    of: (SkRole) -> Int,", "    alpha: (Int, Float) -> Int,",
       "    mix: (Float) -> Int,", "): Int =", "    when (role) {"]
kt += ["        SkRole.%s -> %s" % (r[0], kdefault(r[5])) for r in ROLES]
kt += ["    }", ""]
open(J, "w", encoding="utf-8").write("\n".join(kt))

x = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- Fork: the colour ROLES as resources. " + HEAD,
     "     Each name is fed at run time by SkPalette with the colour its role has (Android 11+).",
     "     The values here are each role's computed default, which an older system shows.",
     "     Default values/ only: a qualified copy would beat the run-time value. -->",
     '<resources xmlns:tools="http://schemas.android.com/tools" tools:ignore="UnusedResources">']
x += ['    <color name="sk_role_%s">%s</color>' % (r[0].lower(), hexs(value(r[0]))) for r in ROLES]
x += ['    <string name="sk_section_%s">%s</string>' % (s[0], esc(s[1])) for s in SECTIONS]
x += ['    <string name="sk_role_label_%s">%s</string>' % (r[0].lower(), esc(r[3])) for r in ROLES]
x += ["</resources>", ""]
open(V + "sk_roles.xml", "w", encoding="utf-8").write("\n".join(x))

j = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- Fork: the Japanese labels of the colour roles. " + HEAD + " -->",
     "<resources>"]
j += ['    <string name="sk_section_%s">%s</string>' % (s[0], esc(s[2])) for s in SECTIONS]
j += ['    <string name="sk_role_label_%s">%s</string>' % (r[0].lower(), esc(r[4])) for r in ROLES]
j += ["</resources>", ""]
open(VJ + "sk_roles.xml", "w", encoding="utf-8").write("\n".join(j))

t = ['<?xml version="1.0" encoding="utf-8"?>',
     "<!-- Fork: the 白い熊 theme overlay, applied on top of upstream's theme in code (SkTheme.install).",
     "     " + HEAD, "     It holds no colour value: every colour attribute upstream's layouts and Material's widgets",
     "     read is pointed at a role colour resource (sk_roles.xml). -->",
     '<resources xmlns:tools="http://schemas.android.com/tools">',
     '    <style name="ThemeOverlay.Shiroikuma" parent="">']
t += ['        <item name="%s"%s>@color/sk_role_%s</item>'
      % (a, ' tools:ignore="NewApi"' if a == "android:navigationBarColor" else "", r.lower()) for a, r in ATTRS]
t += ['        <item name="android:textColorPrimary">@color/sk_text_primary</item>',
      '        <item name="android:textColorSecondary">@color/sk_text_secondary</item>',
      '        <item name="elevationOverlayEnabled">false</item>',
      "        <!-- the panel of a popup menu is drawn in code (PillMenuItemView) -->",
      '        <item name="popupMenuBackground">@drawable/sk_empty</item>',
      "        <!-- tells our own views that the overlay is on this context -->",
      '        <item name="skThemed">true</item>',
      '        <item name="viewInflaterClass">org.piepmeyer.gauguin.ui.customui.chrome.SkViewInflater</item>',
      "    </style>", "</resources>", ""]
open(V + "sk_theme.xml", "w", encoding="utf-8").write("\n".join(t))
print(len(ROLES), "roles,", len(ATTRS), "attributes,", len(SECTIONS), "sections")
