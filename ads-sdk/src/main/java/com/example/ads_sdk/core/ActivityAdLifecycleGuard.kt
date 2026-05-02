package com.example.ads_sdk.core

import android.app.Activity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

/** Weak lifecycle binding used while a full-screen ad is active. */
internal class ActivityAdLifecycleGuard(activity: Activity) {
    private val activityRef = WeakReference(activity)
    private val destroyed = AtomicBoolean(activity.isFinishing || activity.isDestroyed)
    private var ownerRef = WeakReference<LifecycleOwner?>(null)

    private val observer = object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            destroyed.set(true)
            owner.lifecycle.removeObserver(this)
        }
    }

    init {
        (activity as? LifecycleOwner)?.let { owner ->
            ownerRef = WeakReference(owner)
            owner.lifecycle.addObserver(observer)
        }
    }

    fun getActivityOrNull(): Activity? {
        val activity = activityRef.get() ?: return null
        return if (!destroyed.get() && !activity.isFinishing && !activity.isDestroyed) activity else null
    }

    fun release() {
        destroyed.set(true)
        ownerRef.get()?.lifecycle?.removeObserver(observer)
        ownerRef.clear()
        activityRef.clear()
    }
}
