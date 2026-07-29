package com.purride.pixellauncherv2.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap

class ContactNameResolver(
    private val context: Context,
) {
    // 来信接收器（主线程）与后台刷新线程会并发读写，必须用并发容器。
    private val cache = ConcurrentHashMap<String, String>()

    fun displayName(address: String): String {
        val normalized = normalizeAddress(address)
        if (normalized.isEmpty()) return ""
        cache[normalized]?.let { return it }
        val resolved = queryDisplayName(address).trim()
        cache[normalized] = resolved
        return resolved
    }

    private fun queryDisplayName(address: String): String {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return ""
        }
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(address),
        )
        val cursor = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null,
            )
        }.getOrNull() ?: return ""

        cursor.use { queryCursor ->
            if (!queryCursor.moveToFirst()) return ""
            val displayNameIndex = queryCursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
            if (displayNameIndex < 0) return ""
            return queryCursor.getString(displayNameIndex).orEmpty()
        }
    }

    private fun normalizeAddress(address: String): String {
        return buildString {
            address.forEach { char ->
                if (char.isDigit() || char == '+') append(char)
            }
        }.ifBlank { address.trim() }
    }
}
