package com.psst.aurora

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppCardAdapter(
    private val apps: List<AppEntry>,
    private val config: ConfigStore,
    private val onClick: (AppEntry) -> Unit,
    private val onLongClick: (AppEntry) -> Unit,
    private val onFocus: (AppEntry, View, Boolean) -> Unit
) : RecyclerView.Adapter<AppCardAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val banner: ImageView = view.findViewById(R.id.banner)
        val fallback: TextView = view.findViewById(R.id.fallbackLabel)
        val ring: View = view.findViewById(R.id.focusRing)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_card, parent, false)
        v.cameraDistance = 9000f * v.resources.displayMetrics.density
        // explicit rounded outline -> rounds both the clip AND the elevation shadow
        val radius = 14f * v.resources.displayMetrics.density
        v.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        v.clipToOutline = true
        return VH(v)
    }

    override fun getItemCount() = apps.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = apps[position]
        bindImage(holder, app)
        resetTransforms(holder)
        holder.itemView.setBackgroundResource(R.drawable.card_bg)   // reset recycled glass

        val accent = config.resolveAccent(app.accent)
        val density = holder.itemView.resources.displayMetrics.density

        // configurable card size
        val lp = holder.itemView.layoutParams
        lp.width = (240 * density * config.cardScale).toInt()
        lp.height = (135 * density * config.cardScale).toInt()
        holder.itemView.layoutParams = lp

        holder.itemView.setOnClickListener { onClick(app) }
        holder.itemView.setOnLongClickListener { onLongClick(app); true }
        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            val row = v.parent as? RecyclerView
            if (hasFocus) {
                v.rotationY = 0f  // focused card faces the viewer
                // no translationZ/elevation: its shadow clips square across nested rows.
                // Focus is shown via scale + accent ring + reactive glow instead.
                v.animate().scaleX(1.12f).scaleY(1.12f)
                    .setDuration(260).setInterpolator(OvershootInterpolator(1.4f)).start()
                holder.ring.background = ringDrawable(accent, density)
                holder.ring.animate().alpha(1f).setDuration(150).start()
            } else {
                v.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
                holder.ring.animate().alpha(0f).setDuration(150).start()
                row?.let { rv -> rv.post { CoverFlow.transform(rv) } }
            }
            onFocus(app, v, hasFocus)
        }
    }

    private fun resetTransforms(holder: VH) {
        holder.itemView.apply {
            scaleX = 1f; scaleY = 1f; translationZ = 0f; rotationX = 0f; rotationY = 0f; alpha = 1f
        }
        holder.ring.alpha = 0f
    }

    private fun ringDrawable(accent: Int, density: Float): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = 14f * density
            setStroke((3f * density).toInt(), accent)
            setColor(Color.TRANSPARENT)
        }

    private fun bindImage(holder: VH, app: AppEntry) {
        val customPath = config.customIconPath(app.packageName)
        val bundled = Banners.map[app.packageName]
        when {
            customPath != null -> {
                val bmp = BitmapFactory.decodeFile(customPath)
                if (bmp != null) { holder.banner.setImageBitmap(bmp); showBanner(holder) }
                else fallback(holder, app)
            }
            bundled != null -> { holder.banner.setImageResource(bundled); showBanner(holder) }
            else -> { holder.banner.setImageBitmap(BannerFactory.get(app)); showBanner(holder) }
        }
    }

    private fun showBanner(holder: VH) {
        holder.banner.visibility = View.VISIBLE
        holder.fallback.visibility = View.GONE
    }

    private fun fallback(holder: VH, app: AppEntry) {
        holder.banner.visibility = View.GONE
        holder.fallback.visibility = View.VISIBLE
        holder.fallback.text = app.label
    }
}
