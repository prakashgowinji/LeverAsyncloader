package com.pin.lever;

import android.content.Context;
import android.support.media.ExifInterface;
import android.net.Uri;
import java.io.IOException;
import okio.Okio;
import okio.Source;

import static android.content.ContentResolver.SCHEME_FILE;
import static android.support.media.ExifInterface.ORIENTATION_NORMAL;
import static android.support.media.ExifInterface.TAG_ORIENTATION;
import static com.pin.lever.Lever.LoadedFrom.DISK;

class FileRequestHandler extends ContentStreamRequestHandler {

    FileRequestHandler(Context context) {
        super(context);
    }

    @Override public boolean canHandleRequest(Request data) {
        return SCHEME_FILE.equals(data.uri.getScheme());
    }

    @Override public Result load(Request request, int networkPolicy) throws IOException {
        Source source = Okio.source(getInputStream(request));
        return new Result(null, source, DISK, getFileExifRotation(request.uri));
    }

    static int getFileExifRotation(Uri uri) throws IOException {
        ExifInterface exifInterface = new ExifInterface(uri.getPath());
        return exifInterface.getAttributeInt(TAG_ORIENTATION, ORIENTATION_NORMAL);
    }
}
