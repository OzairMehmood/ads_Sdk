package com.example.ads_sdk.presentation.nativead

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import com.example.ads_sdk.R
import com.example.ads_sdk.core.AdsManager
import java.util.Collections
import java.util.WeakHashMap

/**
 * RecyclerView wrapper that inserts native ad rows without forcing apps to
 * rewrite their content adapter. Native rows are destroyed when recycled.
 */
class NativeAdRecyclerAdapter(
    contentAdapter: RecyclerView.Adapter<out RecyclerView.ViewHolder>,
    private val adInterval: Int = DEFAULT_AD_INTERVAL
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    @Suppress("UNCHECKED_CAST")
    private val delegate = contentAdapter as RecyclerView.Adapter<RecyclerView.ViewHolder>
    private val activeNativeContainers = Collections.newSetFromMap(
        WeakHashMap<ViewGroup, Boolean>()
    )

    private val observer = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() = notifyDataSetChanged()
        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) = notifyDataSetChanged()
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = notifyDataSetChanged()
        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = notifyDataSetChanged()
        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) = notifyDataSetChanged()
    }

    init {
        require(adInterval > 0) { "adInterval must be greater than zero." }
        setHasStableIds(delegate.hasStableIds())
        delegate.registerAdapterDataObserver(observer)
    }

    override fun getItemCount(): Int {
        val contentCount = delegate.itemCount
        if (contentCount == 0) return 0
        return contentCount + contentCount / adInterval
    }

    override fun getItemViewType(position: Int): Int {
        return if (isAdPosition(position)) {
            AD_VIEW_TYPE
        } else {
            delegate.getItemViewType(toContentPosition(position))
        }
    }

    override fun getItemId(position: Int): Long {
        return if (isAdPosition(position)) {
            Long.MIN_VALUE + position
        } else {
            delegate.getItemId(toContentPosition(position))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == AD_VIEW_TYPE) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_native_ad_container, parent, false)
            return NativeAdHolder(view)
        }
        return delegate.onCreateViewHolder(parent, viewType)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is NativeAdHolder) {
            activeNativeContainers.add(holder.container)
            holder.container.visibility = View.VISIBLE
            AdsManager.loadNative(holder.container)
        } else {
            delegate.onBindViewHolder(holder, toContentPosition(position))
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is NativeAdHolder) {
            AdsManager.destroyNative(holder.container)
            activeNativeContainers.remove(holder.container)
        } else {
            delegate.onViewRecycled(holder)
        }
        super.onViewRecycled(holder)
    }

    override fun onFailedToRecycleView(holder: RecyclerView.ViewHolder): Boolean {
        return if (holder is NativeAdHolder) {
            AdsManager.destroyNative(holder.container)
            true
        } else {
            delegate.onFailedToRecycleView(holder)
        }
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        if (holder !is NativeAdHolder) delegate.onViewAttachedToWindow(holder)
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        if (holder is NativeAdHolder) {
            AdsManager.destroyNative(holder.container)
        } else {
            delegate.onViewDetachedFromWindow(holder)
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        delegate.onAttachedToRecyclerView(recyclerView)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        activeNativeContainers.toList().forEach(AdsManager::destroyNative)
        activeNativeContainers.clear()
        delegate.onDetachedFromRecyclerView(recyclerView)
    }

    fun release() {
        activeNativeContainers.toList().forEach(AdsManager::destroyNative)
        activeNativeContainers.clear()
        runCatching { delegate.unregisterAdapterDataObserver(observer) }
    }

    private fun isAdPosition(position: Int): Boolean {
        return position > 0 && (position + 1) % (adInterval + 1) == 0
    }

    private fun toContentPosition(position: Int): Int {
        val adsBefore = (position + 1) / (adInterval + 1)
        return position - adsBefore
    }

    private class NativeAdHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val container: FrameLayout = itemView.findViewById(R.id.sdk_native_ad_container)
    }

    private companion object {
        const val AD_VIEW_TYPE = Int.MIN_VALUE + 101
        const val DEFAULT_AD_INTERVAL = 6
    }
}
