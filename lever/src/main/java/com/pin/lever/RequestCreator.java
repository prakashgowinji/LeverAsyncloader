package com.pin.lever;

import android.app.Notification;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.DrawableRes;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.RemoteViews;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.pin.lever.BitmapHunter.forRequest;
import static com.pin.lever.MemoryPolicy.shouldReadFromMemoryCache;
import static com.pin.lever.Lever.LoadedFrom.MEMORY;
import static com.pin.lever.Lever.Priority;
import static com.pin.lever.LeverDrawable.setBitmap;
import static com.pin.lever.LeverDrawable.setPlaceholder;
import static com.pin.lever.RemoteViewsAction.AppWidgetAction;
import static com.pin.lever.RemoteViewsAction.NotificationAction;
import static com.pin.lever.Utils.OWNER_MAIN;
import static com.pin.lever.Utils.VERB_CHANGED;
import static com.pin.lever.Utils.VERB_COMPLETED;
import static com.pin.lever.Utils.VERB_CREATED;
import static com.pin.lever.Utils.checkMain;
import static com.pin.lever.Utils.checkNotMain;
import static com.pin.lever.Utils.createKey;
import static com.pin.lever.Utils.log;

@SuppressWarnings("UnusedDeclaration") // Public API.
public class RequestCreator {
    private static final AtomicInteger nextId = new AtomicInteger();

    private final Lever lever;
    private final Request.Builder data;

    private boolean noFade;
    private boolean deferred;
    private boolean setPlaceholder = true;
    private int placeholderResId;
    private int errorResId;
    private int memoryPolicy;
    private int networkPolicy;
    private Drawable placeholderDrawable;
    private Drawable errorDrawable;
    private Object tag;

    RequestCreator(Lever lever, Uri uri, int resourceId) {
        if (lever.shutdown) {
            throw new IllegalStateException(
                    "Lever instance already shut down. Cannot submit new requests.");
        }
        this.lever = lever;
        this.data = new Request.Builder(uri, resourceId, lever.defaultBitmapConfig);
    }

    @VisibleForTesting
    RequestCreator() {
        this.lever = null;
        this.data = new Request.Builder(null, 0, null);
    }

    /**
     * Explicitly opt-out to having a placeholder set when calling {@code into}.
     * <p>
     * By default, Lever will either set a supplied placeholder or clear the target
     * {@link ImageView} in order to ensure behavior in situations where views are recycled. This
     * method will prevent that behavior and retain any already set image.
     */
    public RequestCreator noPlaceholder() {
        if (placeholderResId != 0) {
            throw new IllegalStateException("Placeholder resource already set.");
        }
        if (placeholderDrawable != null) {
            throw new IllegalStateException("Placeholder image already set.");
        }
        setPlaceholder = false;
        return this;
    }

    /**
     * A placeholder drawable to be used while the image is being loaded. If the requested image is
     * not immediately available in the memory cache then this resource will be set on the target
     * {@link ImageView}.
     */
    public RequestCreator placeholder(@DrawableRes int placeholderResId) {
        if (!setPlaceholder) {
            throw new IllegalStateException("Already explicitly declared as no placeholder.");
        }
        if (placeholderResId == 0) {
            throw new IllegalArgumentException("Placeholder image resource invalid.");
        }
        if (placeholderDrawable != null) {
            throw new IllegalStateException("Placeholder image already set.");
        }
        this.placeholderResId = placeholderResId;
        return this;
    }

    /**
     * A placeholder drawable to be used while the image is being loaded. If the requested image is
     * not immediately available in the memory cache then this resource will be set on the target
     * {@link ImageView}.
     * <p>
     * If you are not using a placeholder image but want to clear an existing image (such as when
     * used in an {@link android.widget.Adapter adapter}), pass in {@code null}.
     */
    public RequestCreator placeholder(@NonNull Drawable placeholderDrawable) {
        if (!setPlaceholder) {
            throw new IllegalStateException("Already explicitly declared as no placeholder.");
        }
        if (placeholderResId != 0) {
            throw new IllegalStateException("Placeholder image already set.");
        }
        this.placeholderDrawable = placeholderDrawable;
        return this;
    }

    /** An error drawable to be used if the request image could not be loaded. */
    public RequestCreator error(@DrawableRes int errorResId) {
        if (errorResId == 0) {
            throw new IllegalArgumentException("Error image resource invalid.");
        }
        if (errorDrawable != null) {
            throw new IllegalStateException("Error image already set.");
        }
        this.errorResId = errorResId;
        return this;
    }

    /** An error drawable to be used if the request image could not be loaded. */
    public RequestCreator error(@NonNull Drawable errorDrawable) {
        if (errorDrawable == null) {
            throw new IllegalArgumentException("Error image may not be null.");
        }
        if (errorResId != 0) {
            throw new IllegalStateException("Error image already set.");
        }
        this.errorDrawable = errorDrawable;
        return this;
    }

    /**
     * Assign a tag to this request. Tags are an easy way to logically associate
     * related requests that can be managed together e.g. paused, resumed,
     * or canceled.
     * <p>
     * You can either use simple {@link String} tags or objects that naturally
     * define the scope of your requests within your app such as a
     * {@link android.content.Context}, an {@link android.app.Activity}, or a
     * {@link android.app.Fragment}.
     *
     * <strong>WARNING:</strong>: Lever will keep a reference to the tag for
     * as long as this tag is paused and/or has active requests. Look out for
     * potential leaks.
     *
     * @see Lever#cancelTag(Object)
     * @see Lever#pauseTag(Object)
     * @see Lever#resumeTag(Object)
     */
    public RequestCreator tag(@NonNull Object tag) {
        if (tag == null) {
            throw new IllegalArgumentException("Tag invalid.");
        }
        if (this.tag != null) {
            throw new IllegalStateException("Tag already set.");
        }
        this.tag = tag;
        return this;
    }

    /**
     * Attempt to resize the image to fit exactly into the target {@link ImageView}'s bounds. This
     * will result in delayed execution of the request until the {@link ImageView} has been laid out.
     * <p>
     * <em>Note:</em> This method works only when your target is an {@link ImageView}.
     */
    public RequestCreator fit() {
        deferred = true;
        return this;
    }

    /** Internal use only. Used by {@link DeferredRequestCreator}. */
    RequestCreator unfit() {
        deferred = false;
        return this;
    }

    /** Internal use only. Used by {@link DeferredRequestCreator}. */
    RequestCreator clearTag() {
        this.tag = null;
        return this;
    }

    /** Internal use only. Used by {@link DeferredRequestCreator}. */
    Object getTag() {
        return tag;
    }

    /** Resize the image to the specified dimension size. */
    public RequestCreator resizeDimen(int targetWidthResId, int targetHeightResId) {
        Resources resources = lever.context.getResources();
        int targetWidth = resources.getDimensionPixelSize(targetWidthResId);
        int targetHeight = resources.getDimensionPixelSize(targetHeightResId);
        return resize(targetWidth, targetHeight);
    }

    /** Resize the image to the specified size in pixels. */
    public RequestCreator resize(int targetWidth, int targetHeight) {
        data.resize(targetWidth, targetHeight);
        return this;
    }

    /**
     * Crops an image inside of the bounds specified by {@link #resize(int, int)} rather than
     * distorting the aspect ratio. This cropping technique scales the image so that it fills the
     * requested bounds and then crops the extra.
     */
    public RequestCreator centerCrop() {
        data.centerCrop(Gravity.CENTER);
        return this;
    }

    /**
     * Crops an image inside of the bounds specified by {@link #resize(int, int)} rather than
     * distorting the aspect ratio. This cropping technique scales the image so that it fills the
     * requested bounds and then crops the extra, preferring the contents at {@code alignGravity}.
     */
    public RequestCreator centerCrop(int alignGravity) {
        data.centerCrop(alignGravity);
        return this;
    }

    /**
     * Centers an image inside of the bounds specified by {@link #resize(int, int)}. This scales
     * the image so that both dimensions are equal to or less than the requested bounds.
     */
    public RequestCreator centerInside() {
        data.centerInside();
        return this;
    }

    /**
     * Only resize an image if the original image size is bigger than the target size
     * specified by {@link #resize(int, int)}.
     */
    public RequestCreator onlyScaleDown() {
        data.onlyScaleDown();
        return this;
    }

    /** Rotate the image by the specified degrees. */
    public RequestCreator rotate(float degrees) {
        data.rotate(degrees);
        return this;
    }

    /** Rotate the image by the specified degrees around a pivot point. */
    public RequestCreator rotate(float degrees, float pivotX, float pivotY) {
        data.rotate(degrees, pivotX, pivotY);
        return this;
    }

    /**
     * Attempt to decode the image using the specified config.
     * <p>
     * Note: This value may be ignored by {@link BitmapFactory}. See
     * {@link BitmapFactory.Options#inPreferredConfig its documentation} for more details.
     */
    public RequestCreator config(@NonNull Bitmap.Config config) {
        data.config(config);
        return this;
    }

    /**
     * Sets the stable key for this request to be used instead of the URI or resource ID when
     * caching. Two requests with the same value are considered to be for the same resource.
     */
    public RequestCreator stableKey(@NonNull String stableKey) {
        data.stableKey(stableKey);
        return this;
    }

    /**
     * Set the priority of this request.
     * <p>
     * This will affect the order in which the requests execute but does not guarantee it.
     * By default, all requests have {@link Priority#NORMAL} priority, except for
     * {@link #fetch()} requests, which have {@link Priority#LOW} priority by default.
     */
    public RequestCreator priority(@NonNull Priority priority) {
        data.priority(priority);
        return this;
    }

    /**
     * Add a custom transformation to be applied to the image.
     * <p>
     * Custom transformations will always be run after the built-in transformations.
     */
    // TODO show example of calling resize after a transform in the javadoc
    public RequestCreator transform(@NonNull Transformation transformation) {
        data.transform(transformation);
        return this;
    }

    /**
     * Add a list of custom transformations to be applied to the image.
     * <p>
     * Custom transformations will always be run after the built-in transformations.
     */
    public RequestCreator transform(@NonNull List<? extends Transformation> transformations) {
        data.transform(transformations);
        return this;
    }

    /**
     * Specifies the {@link MemoryPolicy} to use for this request. You may specify additional policy
     * options using the varargs parameter.
     */
    public RequestCreator memoryPolicy(@NonNull MemoryPolicy policy,
                                       @NonNull MemoryPolicy... additional) {
        if (policy == null) {
            throw new IllegalArgumentException("Memory policy cannot be null.");
        }
        this.memoryPolicy |= policy.index;
        if (additional == null) {
            throw new IllegalArgumentException("Memory policy cannot be null.");
        }
        if (additional.length > 0) {
            for (MemoryPolicy memoryPolicy : additional) {
                if (memoryPolicy == null) {
                    throw new IllegalArgumentException("Memory policy cannot be null.");
                }
                this.memoryPolicy |= memoryPolicy.index;
            }
        }
        return this;
    }

    /**
     * Specifies the {@link NetworkPolicy} to use for this request. You may specify additional policy
     * options using the varargs parameter.
     */
    public RequestCreator networkPolicy(@NonNull NetworkPolicy policy,
                                        @NonNull NetworkPolicy... additional) {
        if (policy == null) {
            throw new IllegalArgumentException("Network policy cannot be null.");
        }
        this.networkPolicy |= policy.index;
        if (additional == null) {
            throw new IllegalArgumentException("Network policy cannot be null.");
        }
        if (additional.length > 0) {
            for (NetworkPolicy networkPolicy : additional) {
                if (networkPolicy == null) {
                    throw new IllegalArgumentException("Network policy cannot be null.");
                }
                this.networkPolicy |= networkPolicy.index;
            }
        }
        return this;
    }

    /**
     * Set inPurgeable and inInputShareable when decoding. This will force the bitmap to be decoded
     * from a byte array instead of a stream, since inPurgeable only affects the former.
     * <p>
     * <em>Note</em>: as of API level 21 (Lollipop), the inPurgeable field is deprecated and will be
     * ignored.
     */
    public RequestCreator purgeable() {
        data.purgeable();
        return this;
    }

    /** Disable brief fade in of images loaded from the disk cache or network. */
    public RequestCreator noFade() {
        noFade = true;
        return this;
    }

    /**
     * Synchronously fulfill this request. Must not be called from the main thread.
     * <p>
     * <em>Note</em>: The result of this operation is not cached in memory because the underlying
     * {@link Cache} implementation is not guaranteed to be thread-safe.
     */
    public Bitmap get() throws IOException {
        long started = System.nanoTime();
        checkNotMain();

        if (deferred) {
            throw new IllegalStateException("Fit cannot be used with instance.");
        }
        if (!data.hasImage()) {
            return null;
        }

        Request finalData = createRequest(started);
        String key = createKey(finalData, new StringBuilder());

        Action action = new GetAction(lever, finalData, memoryPolicy, networkPolicy, tag, key);
        return forRequest(lever, lever.dispatcher, lever.cache, lever.stats, action).hunt();
    }

    /**
     * Asynchronously fulfills the request without a {@link ImageView} or {@link Target}. This is
     * useful when you want to warm up the cache with an image.
     * <p>
     * <em>Note:</em> It is safe to invoke this method from any thread.
     */
    public void fetch() {
        fetch(null);
    }

    /**
     * Asynchronously fulfills the request without a {@link ImageView} or {@link Target},
     * and invokes the target {@link Callback} with the result. This is useful when you want to warm
     * up the cache with an image.
     * <p>
     * <em>Note:</em> The {@link Callback} param is a strong reference and will prevent your
     * {@link android.app.Activity} or {@link android.app.Fragment} from being garbage collected
     * until the request is completed.
     */
    public void fetch(@Nullable Callback callback) {
        long started = System.nanoTime();

        if (deferred) {
            throw new IllegalStateException("Fit cannot be used with fetch.");
        }
        if (data.hasImage()) {
            // Fetch requests have lower priority by default.
            if (!data.hasPriority()) {
                data.priority(Priority.LOW);
            }

            Request request = createRequest(started);
            String key = createKey(request, new StringBuilder());

            if (shouldReadFromMemoryCache(memoryPolicy)) {
                Bitmap bitmap = lever.quickMemoryCacheCheck(key);
                if (bitmap != null) {
                    if (lever.loggingEnabled) {
                        log(OWNER_MAIN, VERB_COMPLETED, request.plainId(), "from " + MEMORY);
                    }
                    if (callback != null) {
                        callback.onSuccess();
                    }
                    return;
                }
            }

            Action action =
                    new FetchAction(lever, request, memoryPolicy, networkPolicy, tag, key, callback);
            lever.submit(action);
        }
    }

    /**
     * Asynchronously fulfills the request into the specified {@link Target}. In most cases, you
     * should use this when you are dealing with a custom {@link android.view.View View} or view
     * holder which should implement the {@link Target} interface.
     * <p>
     * Implementing on a {@link android.view.View View}:
     * <blockquote><pre>
     * public class ProfileView extends FrameLayout implements Target {
     *   {@literal @}Override public void onBitmapLoaded(Bitmap bitmap, LoadedFrom from) {
     *     setBackgroundDrawable(new BitmapDrawable(bitmap));
     *   }
     *
     *   {@literal @}Override public void onBitmapFailed(Exception e, Drawable errorDrawable) {
     *     setBackgroundDrawable(errorDrawable);
     *   }
     *
     *   {@literal @}Override public void onPrepareLoad(Drawable placeHolderDrawable) {
     *     setBackgroundDrawable(placeHolderDrawable);
     *   }
     * }
     * </pre></blockquote>
     * Implementing on a view holder object for use inside of an adapter:
     * <blockquote><pre>
     * public class ViewHolder implements Target {
     *   public FrameLayout frame;
     *   public TextView name;
     *
     *   {@literal @}Override public void onBitmapLoaded(Bitmap bitmap, LoadedFrom from) {
     *     frame.setBackgroundDrawable(new BitmapDrawable(bitmap));
     *   }
     *
     *   {@literal @}Override public void onBitmapFailed(Exception e, Drawable errorDrawable) {
     *     frame.setBackgroundDrawable(errorDrawable);
     *   }
     *
     *   {@literal @}Override public void onPrepareLoad(Drawable placeHolderDrawable) {
     *     frame.setBackgroundDrawable(placeHolderDrawable);
     *   }
     * }
     * </pre></blockquote>
     * <p>
     * <em>Note:</em> This method keeps a weak reference to the {@link Target} instance and will be
     * garbage collected if you do not keep a strong reference to it. To receive callbacks when an
     * image is loaded use {@link #into(android.widget.ImageView, Callback)}.
     */
    public void into(@NonNull Target target) {
        long started = System.nanoTime();
        checkMain();

        if (target == null) {
            throw new IllegalArgumentException("Target must not be null.");
        }
        if (deferred) {
            throw new IllegalStateException("Fit cannot be used with a Target.");
        }

        if (!data.hasImage()) {
            lever.cancelRequest(target);
            target.onPrepareLoad(setPlaceholder ? getPlaceholderDrawable() : null);
            return;
        }

        Request request = createRequest(started);
        String requestKey = createKey(request);

        if (shouldReadFromMemoryCache(memoryPolicy)) {
            Bitmap bitmap = lever.quickMemoryCacheCheck(requestKey);
            if (bitmap != null) {
                lever.cancelRequest(target);
                target.onBitmapLoaded(bitmap, MEMORY);
                return;
            }
        }

        target.onPrepareLoad(setPlaceholder ? getPlaceholderDrawable() : null);

        Action action =
                new TargetAction(lever, target, request, memoryPolicy, networkPolicy, errorDrawable,
                        requestKey, tag, errorResId);
        lever.enqueueAndSubmit(action);
    }

    /**
     * Asynchronously fulfills the request into the specified {@link RemoteViews} object with the
     * given {@code viewId}. This is used for loading bitmaps into a {@link Notification}.
     */
    public void into(@NonNull RemoteViews remoteViews, @IdRes int viewId, int notificationId,
                     @NonNull Notification notification) {
        into(remoteViews, viewId, notificationId, notification, null);
    }

    /**
     * Asynchronously fulfills the request into the specified {@link RemoteViews} object with the
     * given {@code viewId}. This is used for loading bitmaps into a {@link Notification}.
     */
    public void into(@NonNull RemoteViews remoteViews, @IdRes int viewId, int notificationId,
                     @NonNull Notification notification, @Nullable String notificationTag) {
        into(remoteViews, viewId, notificationId, notification, notificationTag, null);
    }

    /**
     * Asynchronously fulfills the request into the specified {@link RemoteViews} object with the
     * given {@code viewId}. This is used for loading bitmaps into a {@link Notification}.
     */
    public void into(@NonNull RemoteViews remoteViews, @IdRes int viewId, int notificationId,
                     @NonNull Notification notification, @Nullable String notificationTag, Callback callback) {
        long started = System.nanoTime();

        if (remoteViews == null) {
            throw new IllegalArgumentException("RemoteViews must not be null.");
        }
        if (notification == null) {
            throw new IllegalArgumentException("Notification must not be null.");
        }
        if (deferred) {
            throw new IllegalStateException("Fit cannot be used with RemoteViews.");
        }
        if (placeholderDrawable != null || placeholderResId != 0 || errorDrawable != null) {
            throw new IllegalArgumentException(
                    "Cannot use placeholder or error drawables with remote views.");
        }

        Request request = createRequest(started);
        String key = createKey(request, new StringBuilder()); // Non-main thread needs own builder.

        RemoteViewsAction action =
                new NotificationAction(lever, request, remoteViews, viewId, notificationId, notification,
                        notificationTag, memoryPolicy, networkPolicy, key, tag, errorResId, callback);

        performRemoteViewInto(action);
    }

    /**
     * Asynchronously fulfills the request into the specified {@link RemoteViews} object with the
     * given {@code viewId}. This is used for loading bitmaps into all instances of a widget.
     */
    public void into(@NonNull RemoteViews remoteViews, @IdRes int viewId,
                     @NonNull int[] appWidgetIds) {
        into(remoteViews, viewId, appWidgetIds, null);
    }

    /**
     * Asynchronously fulfills the request into the specified {@link RemoteViews} object with the
     * given {@code viewId}. This is used for loading bitmaps into all instances of a widget.
     */
    public void into(@NonNull RemoteViews remoteViews, @IdRes int viewId, @NonNull int[] appWidgetIds,
                     Callback callback) {
        long started = System.nanoTime();

        if (remoteViews == null) {
            throw new IllegalArgumentException("remoteViews must not be null.");
        }
        if (appWidgetIds == null) {
            throw new IllegalArgumentException("appWidgetIds must not be null.");
        }
        if (deferred) {
            throw new IllegalStateException("Fit cannot be used with remote views.");
        }
        if (placeholderDrawable != null || placeholderResId != 0 || errorDrawable != null) {
            throw new IllegalArgumentException(
                    "Cannot use placeholder or error drawables with remote views.");
        }

        Request request = createRequest(started);
        String key = createKey(request, new StringBuilder()); // Non-main thread needs own builder.

        RemoteViewsAction action =
                new AppWidgetAction(lever, request, remoteViews, viewId, appWidgetIds, memoryPolicy,
                        networkPolicy, key, tag, errorResId, callback);

        performRemoteViewInto(action);
    }

    /**
     * Asynchronously fulfills the request into the specified {@link ImageView}.
     * <p>
     * <em>Note:</em> This method keeps a weak reference to the {@link ImageView} instance and will
     * automatically support object recycling.
     */
    public void into(ImageView target) {
        into(target, null);
    }

    /**
     * Asynchronously fulfills the request into the specified {@link ImageView} and invokes the
     * target {@link Callback} if it's not {@code null}.
     * <p>
     * <em>Note:</em> The {@link Callback} param is a strong reference and will prevent your
     * {@link android.app.Activity} or {@link android.app.Fragment} from being garbage collected. If
     * you use this method, it is <b>strongly</b> recommended you invoke an adjacent
     * {@link Lever#cancelRequest(android.widget.ImageView)} call to prevent temporary leaking.
     */
    public void into(ImageView target, Callback callback) {
        long started = System.nanoTime();
        checkMain();

        if (target == null) {
            throw new IllegalArgumentException("Target must not be null.");
        }

        if (!data.hasImage()) {
            lever.cancelRequest(target);
            if (setPlaceholder) {
                setPlaceholder(target, getPlaceholderDrawable());
            }
            return;
        }

        if (deferred) {
            if (data.hasSize()) {
                throw new IllegalStateException("Fit cannot be used with resize.");
            }
            int width = target.getWidth();
            int height = target.getHeight();
            if (width == 0 || height == 0) {
                if (setPlaceholder) {
                    setPlaceholder(target, getPlaceholderDrawable());
                }
                lever.defer(target, new DeferredRequestCreator(this, target, callback));
                return;
            }
            data.resize(width, height);
        }

        Request request = createRequest(started);
        String requestKey = createKey(request);

        if (shouldReadFromMemoryCache(memoryPolicy)) {
            Bitmap bitmap = lever.quickMemoryCacheCheck(requestKey);
            if (bitmap != null) {
                lever.cancelRequest(target);
                setBitmap(target, lever.context, bitmap, MEMORY, noFade, lever.indicatorsEnabled);
                if (lever.loggingEnabled) {
                    log(OWNER_MAIN, VERB_COMPLETED, request.plainId(), "from " + MEMORY);
                }
                if (callback != null) {
                    callback.onSuccess();
                }
                return;
            }
        }

        if (setPlaceholder) {
            setPlaceholder(target, getPlaceholderDrawable());
        }

        Action action =
                new ImageViewAction(lever, target, request, memoryPolicy, networkPolicy, errorResId,
                        errorDrawable, requestKey, tag, callback, noFade);

        lever.enqueueAndSubmit(action);
    }

    private Drawable getPlaceholderDrawable() {
        if (placeholderResId != 0) {
            if (Build.VERSION.SDK_INT >= 21) {
                return lever.context.getDrawable(placeholderResId);
            } else if (Build.VERSION.SDK_INT >= 16) {
                return lever.context.getResources().getDrawable(placeholderResId);
            } else {
                TypedValue value = new TypedValue();
                lever.context.getResources().getValue(placeholderResId, value, true);
                return lever.context.getResources().getDrawable(value.resourceId);
            }
        } else {
            return placeholderDrawable; // This may be null which is expected and desired behavior.
        }
    }

    /** Create the request optionally passing it through the request transformer. */
    private Request createRequest(long started) {
        int id = nextId.getAndIncrement();

        Request request = data.build();
        request.id = id;
        request.started = started;

        boolean loggingEnabled = lever.loggingEnabled;
        if (loggingEnabled) {
            log(OWNER_MAIN, VERB_CREATED, request.plainId(), request.toString());
        }

        Request transformed = lever.transformRequest(request);
        if (transformed != request) {
            // If the request was changed, copy over the id and timestamp from the original.
            transformed.id = id;
            transformed.started = started;

            if (loggingEnabled) {
                log(OWNER_MAIN, VERB_CHANGED, transformed.logId(), "into " + transformed);
            }
        }

        return transformed;
    }

    private void performRemoteViewInto(RemoteViewsAction action) {
        if (shouldReadFromMemoryCache(memoryPolicy)) {
            Bitmap bitmap = lever.quickMemoryCacheCheck(action.getKey());
            if (bitmap != null) {
                action.complete(bitmap, MEMORY);
                return;
            }
        }

        if (placeholderResId != 0) {
            action.setImageResource(placeholderResId);
        }

        lever.enqueueAndSubmit(action);
    }
}
