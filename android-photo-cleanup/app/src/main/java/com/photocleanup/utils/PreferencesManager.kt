package com.photocleanup.utils

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var accountEmail: String?
        get() = prefs.getString(KEY_ACCOUNT_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_ACCOUNT_EMAIL, value).apply()

    var lastScanPageToken: String?
        get() = prefs.getString(KEY_LAST_PAGE_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_LAST_PAGE_TOKEN, value).apply()

    var totalDeletedCount: Int
        get() = prefs.getInt(KEY_TOTAL_DELETED, 0)
        set(value) = prefs.edit().putInt(KEY_TOTAL_DELETED, value).apply()

    fun clearSession() {
        prefs.edit()
            .remove(KEY_LAST_PAGE_TOKEN)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "photo_cleanup_prefs"
        private const val KEY_ACCOUNT_EMAIL = "account_email"
        private const val KEY_LAST_PAGE_TOKEN = "last_page_token"
        private const val KEY_TOTAL_DELETED = "total_deleted"
    }
}
