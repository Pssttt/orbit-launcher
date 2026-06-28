package com.psst.aurora

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Reorder, rename, and create categories. */
class CategoriesActivity : AppCompatActivity() {

    private lateinit var config: ConfigStore
    private lateinit var list: ListView
    private var apps: List<AppEntry> = emptyList()
    private val newLabel = "+  New category"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = ConfigStore(this)
        val d = resources.displayMetrics.density
        val pad = (48 * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(config.baseColor)
            setPadding(pad, pad, pad, pad)
        }
        val title = TextView(this).apply {
            text = "Edit categories"
            setTextColor(0xFFF2F2F5.toInt()); textSize = 24f
            setPadding(0, 0, 0, (16 * d).toInt())
        }
        list = ListView(this)
        root.addView(title)
        root.addView(list)
        setContentView(root)

        lifecycleScope.launch {
            apps = withContext(Dispatchers.IO) { AppRepository(this@CategoriesActivity).loadApps() }
            refresh()
            list.requestFocus()
        }
        list.setOnItemClickListener { _, _, pos, _ ->
            val cats = config.categoryOrder()
            if (pos >= cats.size) promptCreate() else showItemMenu(cats[pos], pos)
        }
    }

    private fun refresh() {
        val items = config.categoryOrder() + newLabel
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
    }

    private fun showItemMenu(name: String, pos: Int) {
        AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(arrayOf("Move up", "Move down", "Rename")) { _, which ->
                val order = config.categoryOrder().toMutableList()
                when (which) {
                    0 -> if (pos > 0) { order.swap(pos, pos - 1); config.setCategoryOrder(order); refresh() }
                    1 -> if (pos < order.size - 1) { order.swap(pos, pos + 1); config.setCategoryOrder(order); refresh() }
                    2 -> promptRename(name)
                }
            }
            .show()
    }

    private fun promptRename(old: String) {
        val input = EditText(this).apply { setText(old) }
        AlertDialog.Builder(this)
            .setTitle("Rename category")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val new = input.text.toString().trim()
                if (new.isNotEmpty() && new != old) {
                    val affected = apps.filter { (config.categoryOverrideOf(it.packageName) ?: it.defaultCategory) == old }.map { it.packageName }
                    config.renameCategory(old, new, affected)
                    refresh()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptCreate() {
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("New category")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty() && name !in config.categoryOrder()) {
                    config.setCategoryOrder(config.categoryOrder() + name); refresh()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun <T> MutableList<T>.swap(a: Int, b: Int) { val t = this[a]; this[a] = this[b]; this[b] = t }
}
