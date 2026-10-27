package net.z33v.fukbd.android

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * Starts fukbd when the app process starts. Android creates content providers before any
 * service, so the decoder factory is installed before FUTO's input method can see a key
 * (design.md A-3). It serves no data.
 */
class FukbdInit : ContentProvider() {
    override fun onCreate(): Boolean {
        FukbdRuntime.init(context ?: return false)
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
