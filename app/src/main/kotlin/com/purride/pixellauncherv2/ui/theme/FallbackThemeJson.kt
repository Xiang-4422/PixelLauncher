package com.purride.pixellauncherv2.ui.theme

import com.purride.pixellauncherv2.launcher.PixelTheme

internal object FallbackThemeJson {
    private val day = """
        {
          "id": "day",
          "label": "Day",
          "mode": "light",
          "colors": {
            "surface": {
              "bezelColor": "#FFFFFF",
              "offPixelColor": "#F1F5F9",
              "panel": "#FFFFFF",
              "panelSubtle": "#E2E8F0"
            },
            "text": {
              "primary": "#000000",
              "secondary": "#1E293B",
              "muted": "#334155",
              "inverse": "#FFFFFF"
            },
            "statusBar": {
              "text": "#000000",
              "mutedText": "#334155",
              "batteryHigh": "#15803D",
              "batteryMedium": "#B45309",
              "batteryLow": "#B91C1C",
              "searchText": "#000000",
              "searchPlaceholder": "#334155"
            },
            "drawer": {
              "itemText": "#000000",
              "itemTextMuted": "#475569",
              "searchText": "#000000",
              "searchPlaceholder": "#334155"
            },
            "settings": {
              "itemTitle": "#000000",
              "itemValue": "#000000"
            },
            "button": {
              "text": "#000000",
              "border": "#1D4ED8",
              "pressedFill": "#BFDBFE",
              "disabledText": "#475569"
            },
            "sms": {
              "sender": "#1D4ED8",
              "timestamp": "#334155",
              "body": "#000000",
              "draftBorder": "#1D4ED8"
            },
            "semantic": {
              "success": "#15803D",
              "warning": "#B45309",
              "danger": "#B91C1C",
              "info": "#1D4ED8"
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
              "bezelColor": "#0B1020",
              "offPixelColor": "#1E293B",
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
              "batteryHigh": "#4ADE80",
              "batteryMedium": "#FACC15",
              "batteryLow": "#F87171",
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
