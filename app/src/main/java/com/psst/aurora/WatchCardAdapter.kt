package com.psst.aurora

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WatchCardAdapter(
    private val items: List<WatchItem>,
    private val onClick: (WatchItem) -> Unit,
    private val onFocus: (Int, View, Boolean) -> Unit,
    private val scope: CoroutineScope
) : RecyclerView.Adapter<WatchCardAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val poster: ImageView = view.findViewById(R.id.poster)
        val title: TextView = view.findViewById(R.id.watchTitle)
        val progress: ProgressBar = view.findViewById(R.id.watchProgress)
        val ring: View = view.findViewById(R.id.focusRing)
        var loadJob: Job? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_watch_card, parent, false)
        v.cameraDistance = 9000f * v.resources.displayMetrics.density
        val radius = 14f * v.resources.displayMetrics.density
        v.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        v.clipToOutline = true
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onViewRecycled(holder: VH) {
        holder.loadJob?.cancel()
        holder.loadJob = null
        holder.poster.setImageDrawable(null)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val density = holder.itemView.resources.displayMetrics.density
        val accent = if (item.accent != 0) item.accent else 0xFF8B5CF6.toInt()

        holder.itemView.apply {
            scaleX = 1f; scaleY = 1f; rotationY = 0f; alpha = 1f
        }
        holder.ring.alpha = 0f
        holder.title.text = item.title

        if (item.progress >= 0f) {
            holder.progress.visibility = View.VISIBLE
            holder.progress.progress = (item.progress * 100).toInt()
            holder.progress.progressTintList = ColorStateList.valueOf(accent)
        } else {
            holder.progress.visibility = View.GONE
        }

        loadPoster(holder, item)

        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            val row = v.parent as? RecyclerView
            if (hasFocus) {
                v.rotationY = 0f
                v.animate().scaleX(1.12f).scaleY(1.12f)
                    .setDuration(260).setInterpolator(OvershootInterpolator(1.4f)).start()
                holder.ring.background = ringDrawable(accent, density)
                holder.ring.animate().alpha(1f).setDuration(150).start()
            } else {
                v.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
                holder.ring.animate().alpha(0f).setDuration(150).start()
                row?.let { rv -> rv.post { CoverFlow.transform(rv) } }
            }
            onFocus(accent, v, hasFocus)
        }
    }

    private fun loadPoster(holder: VH, item: WatchItem) {
        holder.loadJob?.cancel()
        val uri = item.posterUri
        if (uri.isNullOrEmpty()) { holder.poster.setImageDrawable(null); return }
        ImageLoader.cached(uri)?.let { holder.poster.setImageBitmap(it); return }
        holder.poster.setImageDrawable(null)
        val ctx = holder.itemView.context.applicationContext
        holder.loadJob = scope.launch {
            val bmp = withContext(Dispatchers.IO) { ImageLoader.load(ctx, uri) }
            if (bmp != null && holder.bindingAdapterPosition != RecyclerView.NO_POSITION) {
                holder.poster.setImageBitmap(bmp)
                holder.poster.alpha = 0f
                holder.poster.animate().alpha(1f).setDuration(220).start()
            }
        }
    }

    private fun ringDrawable(accent: Int, density: Float): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = 14f * density
            setStroke((3f * density).toInt(), accent)
            setColor(Color.TRANSPARENT)
        }
}
