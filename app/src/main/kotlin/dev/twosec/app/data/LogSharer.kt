package dev.twosec.app.data

import android.content.Intent
import android.net.Uri

fun interface ShareIntentFactory {
    fun create(action: String, mimeType: String, streamUris: List<String>, flags: Int): Intent
}

object LogSharer {

    private const val ACTION_SEND_MULTIPLE: String = "android.intent.action.SEND_MULTIPLE"
    private const val MIME_TEXT_PLAIN: String = "text/plain"
    private const val FLAG_GRANT_READ_URI_PERMISSION: Int = 0x00000001

    fun buildShareIntent(
        uris: List<String>,
        factory: ShareIntentFactory = AndroidShareIntentFactory,
    ): Intent = factory.create(ACTION_SEND_MULTIPLE, MIME_TEXT_PLAIN, uris, FLAG_GRANT_READ_URI_PERMISSION)
}

object AndroidShareIntentFactory : ShareIntentFactory {
    override fun create(
        action: String,
        mimeType: String,
        streamUris: List<String>,
        flags: Int,
    ): Intent = Intent(action).apply {
        type = mimeType
        val parcelables = ArrayList<Uri>(streamUris.size)
        streamUris.forEach { parcelables.add(Uri.parse(it)) }
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, parcelables)
        addFlags(flags)
    }
}
