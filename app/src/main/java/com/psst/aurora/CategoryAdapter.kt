package com.psst.aurora

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class CategoryAdapter(
    private val categories: List<Category>,
    private val config: ConfigStore,
    private val onClick: (AppEntry) -> Unit,
    private val onLongClick: (AppEntry) -> Unit,
    private val onFocus: (AppEntry, android.view.View, Boolean) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.VH>() {

    private val sharedPool = RecyclerView.RecycledViewPool()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.categoryTitle)
        val row: RecyclerView = view.findViewById(R.id.appRow)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        val vh = VH(v)
        vh.row.layoutManager = RowLayoutManager(parent.context)
        vh.row.setRecycledViewPool(sharedPool)
        (v as? ViewGroup)?.clipChildren = false
        CoverFlow.attach(vh.row)
        return vh
    }

    override fun getItemCount() = categories.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val cat = categories[position]
        holder.title.text = cat.name.uppercase()
        holder.row.adapter = AppCardAdapter(cat.apps, config, onClick, onLongClick, onFocus)
    }
}
