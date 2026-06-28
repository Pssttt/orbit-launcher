package com.psst.aurora

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope

class CategoryAdapter(
    private val rows: List<HomeRow>,
    private val config: ConfigStore,
    private val onClick: (AppEntry) -> Unit,
    private val onLongClick: (AppEntry, String) -> Unit,   // app + row name (Favorites / Recent / category)
    private val onFocus: (AppEntry, View, Boolean) -> Unit,
    private val onWatchClick: (WatchItem) -> Unit,
    private val onWatchFocus: (Int, View, Boolean) -> Unit,
    private val scope: CoroutineScope
) : RecyclerView.Adapter<CategoryAdapter.VH>() {

    private val sharedPool = RecyclerView.RecycledViewPool()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.categoryTitle)
        val row: RecyclerView = view.findViewById(R.id.appRow)
    }

    override fun getItemViewType(position: Int): Int =
        if (rows[position] is HomeRow.Watch) TYPE_WATCH else TYPE_APPS

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        val vh = VH(v)
        vh.row.layoutManager = RowLayoutManager(parent.context)
        // Only app rows share the pool; watch cards use a different view layout.
        if (viewType == TYPE_APPS) vh.row.setRecycledViewPool(sharedPool)
        (v as? ViewGroup)?.clipChildren = false
        CoverFlow.attach(vh.row)
        return vh
    }

    override fun getItemCount() = rows.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        when (val row = rows[position]) {
            is HomeRow.Apps -> {
                holder.title.text = row.category.name.uppercase()
                holder.row.adapter = AppCardAdapter(
                    row.category.apps, config, onClick,
                    { app -> onLongClick(app, row.category.name) }, onFocus
                )
            }
            is HomeRow.Watch -> {
                holder.title.text = row.title.uppercase()
                holder.row.adapter = WatchCardAdapter(row.items, onWatchClick, onWatchFocus, scope)
            }
        }
    }

    companion object {
        private const val TYPE_APPS = 0
        private const val TYPE_WATCH = 1
    }
}
