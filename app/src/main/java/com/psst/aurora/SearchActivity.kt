package com.psst.aurora

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Type-to-filter search across all installed apps. */
class SearchActivity : AppCompatActivity() {

    private lateinit var config: ConfigStore
    private var all: List<AppEntry> = emptyList()
    private var filtered: List<AppEntry> = emptyList()
    private lateinit var list: ListView

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
        val input = EditText(this).apply {
            hint = "Search apps"
            setTextColor(0xFFF2F2F5.toInt())
            setHintTextColor(0xFF9A9AA6.toInt())
            textSize = 20f
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setSingleLine()
        }
        list = ListView(this)
        root.addView(input)
        root.addView(list, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT))
        setContentView(root)

        lifecycleScope.launch {
            all = withContext(Dispatchers.IO) { AppRepository(this@SearchActivity).loadApps() }
            filtered = all
            render()
        }

        input.addTextChangedListener { text ->
            val q = text?.toString()?.trim().orEmpty()
            filtered = if (q.isEmpty()) all else all.filter { it.label.contains(q, ignoreCase = true) }
            render()
        }
        input.requestFocus()

        list.setOnItemClickListener { _, _, pos, _ ->
            filtered.getOrNull(pos)?.let { app ->
                config.recordLaunch(app.packageName)
                runCatching { startActivity(app.launchIntent) }
                finish()
            }
        }
    }

    private fun render() {
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, filtered.map { it.label })
    }
}
