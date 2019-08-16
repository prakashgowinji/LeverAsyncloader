package com.pin.lever;

import android.graphics.Bitmap;

class GetAction extends Action<Void> {
    GetAction(Lever lever, Request data, int memoryPolicy, int networkPolicy, Object tag,
              String key) {
        super(lever, null, data, memoryPolicy, networkPolicy, 0, null, key, tag, false);
    }

    @Override void complete(Bitmap result, Lever.LoadedFrom from) {
    }

    @Override public void error(Exception e) {
    }
}
