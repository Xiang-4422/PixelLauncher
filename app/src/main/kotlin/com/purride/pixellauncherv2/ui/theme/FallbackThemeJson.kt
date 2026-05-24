package com.purride.pixellauncherv2.ui.theme

import com.purride.pixellauncherv2.render.PixelTheme

internal object FallbackThemeJson {
    private val day = """
        {
          "id": "day",
          "label": "Day",
          "mode": "light",
          "colors": {
            "surface": {
              "appBackground": "#F8FAFC",
              "pixelGrid": "#E2E8F0",
              "panel": "#FFFFFF",
              "panelSubtle": "#F1F5F9"
            },
            "text": {
              "primary": "#0F172A",
              "secondary": "#334155",
              "muted": "#64748B",
              "inverse": "#FFFFFF"
            },
            "statusBar": {
              "text": "#0F172A",
              "mutedText": "#64748B",
              "divider": "#2563EB",
              "charging": "#16A34A",
              "searchText": "#0F172A",
              "searchPlaceholder": "#64748B"
            },
            "drawer": {
              "itemText": "#0F172A",
              "itemTextMuted": "#94A3B8",
              "searchText": "#0F172A",
              "searchPlaceholder": "#64748B"
            },
            "settings": {
              "itemTitle": "#0F172A",
              "itemValue": "#475569"
            },
            "button": {
              "text": "#0F172A",
              "border": "#2563EB",
              "pressedFill": "#DBEAFE",
              "disabledText": "#94A3B8"
            },
            "sms": {
              "sender": "#2563EB",
              "timestamp": "#64748B",
              "body": "#0F172A",
              "draftBorder": "#2563EB"
            },
            "semantic": {
              "success": "#16A34A",
              "warning": "#D97706",
              "danger": "#DC2626",
              "info": "#2563EB"
            }
          }
        }
    """.trimIndent()

    private val night = """
        {
          "id": "night",
          "label": "Night",
          "mode": "dark",
          "colors": {
            "surface": {
              "appBackground": "#0B1020",
              "pixelGrid": "#1E293B",
              "panel": "#111827",
              "panelSubtle": "#162033"
            },
            "text": {
              "primary": "#F8FAFC",
              "secondary": "#CBD5E1",
              "muted": "#94A3B8",
              "inverse": "#0F172A"
            },
            "statusBar": {
              "text": "#F8FAFC",
              "mutedText": "#94A3B8",
              "divider": "#60A5FA",
              "charging": "#4ADE80",
              "searchText": "#F8FAFC",
              "searchPlaceholder": "#94A3B8"
            },
            "drawer": {
              "itemText": "#F8FAFC",
              "itemTextMuted": "#64748B",
              "searchText": "#F8FAFC",
              "searchPlaceholder": "#94A3B8"
            },
            "settings": {
              "itemTitle": "#F8FAFC",
              "itemValue": "#CBD5E1"
            },
            "button": {
              "text": "#F8FAFC",
              "border": "#60A5FA",
              "pressedFill": "#1E3A8A",
              "disabledText": "#64748B"
            },
            "sms": {
              "sender": "#60A5FA",
              "timestamp": "#94A3B8",
              "body": "#F8FAFC",
              "draftBorder": "#60A5FA"
            },
            "semantic": {
              "success": "#4ADE80",
              "warning": "#FBBF24",
              "danger": "#F87171",
              "info": "#60A5FA"
            }
          }
        }
    """.trimIndent()

    val byTheme: Map<PixelTheme, String> = mapOf(
        PixelTheme.DAY to day,
        PixelTheme.NIGHT to night,
    )
}
