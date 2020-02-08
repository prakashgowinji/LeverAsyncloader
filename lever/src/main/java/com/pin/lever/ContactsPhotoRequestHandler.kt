package com.pin.lever

import android.content.ContentResolver
import android.content.Context
import android.content.UriMatcher
import android.provider.ContactsContract
import com.pin.lever.Lever.LoadedFrom
import okio.Okio
import java.io.IOException
import java.io.InputStream

internal class ContactsPhotoRequestHandler(private val context: Context) :
    RequestHandler() {
    companion object {
        /** A lookup uri (e.g. content://com.android.contacts/contacts/lookup/3570i61d948d30808e537)  */
        private const val ID_LOOKUP = 1
        /** A contact thumbnail uri (e.g. content://com.android.contacts/contacts/38/photo)  */
        private const val ID_THUMBNAIL = 2
        /** A contact uri (e.g. content://com.android.contacts/contacts/38)  */
        private const val ID_CONTACT = 3
        /**
         * A contact display photo (high resolution) uri
         * (e.g. content://com.android.contacts/display_photo/5)
         */
        private const val ID_DISPLAY_PHOTO = 4
        private var matcher: UriMatcher? = null

        init {
            matcher = UriMatcher(UriMatcher.NO_MATCH)
            matcher!!.addURI(
                ContactsContract.AUTHORITY,
                "contacts/lookup/*/#",
                ID_LOOKUP
            )
            matcher!!.addURI(
                ContactsContract.AUTHORITY,
                "contacts/lookup/*",
                ID_LOOKUP
            )
            matcher!!.addURI(
                ContactsContract.AUTHORITY,
                "contacts/#/photo",
                ID_THUMBNAIL
            )
            matcher!!.addURI(
                ContactsContract.AUTHORITY,
                "contacts/#",
                ID_CONTACT
            )
            matcher!!.addURI(
                ContactsContract.AUTHORITY,
                "display_photo/#",
                ID_DISPLAY_PHOTO
            )
        }
    }

    override fun canHandleRequest(data: Request?): Boolean {
        val uri = data!!.uri
        return ContentResolver.SCHEME_CONTENT == uri!!.scheme && ContactsContract.Contacts.CONTENT_URI.host == uri.host && matcher!!.match(
            data.uri
        ) != UriMatcher.NO_MATCH
    }

    @Throws(IOException::class)
    override fun load(
        request: Request?,
        networkPolicy: Int
    ): Result? {
        val `is` = getInputStream(request) ?: return null
        return Result(Okio.source(`is`), LoadedFrom.DISK)
    }

    @Throws(IOException::class)
    private fun getInputStream(data: Request?): InputStream? {
        val contentResolver = context.contentResolver
        var uri = data!!.uri
        return when (matcher!!.match(uri)) {
            ID_LOOKUP -> {
                uri = ContactsContract.Contacts.lookupContact(contentResolver, uri)
                if (uri == null) {
                    null
                } else ContactsContract.Contacts.openContactPhotoInputStream(
                    contentResolver,
                    uri,
                    true
                )
            }
            ID_CONTACT -> ContactsContract.Contacts.openContactPhotoInputStream(
                contentResolver,
                uri,
                true
            )
            ID_THUMBNAIL, ID_DISPLAY_PHOTO -> contentResolver.openInputStream(
                uri!!
            )
            else -> throw IllegalStateException("Invalid uri: $uri")
        }
    }

}