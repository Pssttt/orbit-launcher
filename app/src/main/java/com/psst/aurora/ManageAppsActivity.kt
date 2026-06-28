package com.psst.aurora

import android.os.Bundle
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Full list of installed apps with a checkbox per app (checked = visible). */
class ManageAppsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = ConfigStore(this)
        val d = resources.displayMetrics.density
        val pad = (48 * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(config.baseColor)
            setPadding(pad, pad, pad, pad)
        }
        val title = TextView(this).apply {
            text = "Show / hide apps  ·  checked = visible"
            setTextColor(0xFF9A9AA6.toInt())
            textSize = 16f
            setPadding(0, 0, 0, (16 * d).toInt())
        }
        val list = ListView(this).apply { choiceMode = ListView.CHOICE_MODE_MULTIPLE }
        root.addView(title)
        root.addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT))
        setContentView(root)

        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) { AppRepository(this@ManageAppsActivity).loadApps() }
            list.adapter = ArrayAdapter(
                this@ManageAppsActivity,
                android.R.layout.simple_list_item_multiple_choice,
                apps.map { it.label }
            )
            apps.forEachIndexed { i, a -> list.setItemChecked(i, !config.isHidden(a.packageName)) }
            list.setOnItemClickListener { _, _, position, _ ->
                config.setHidden(apps[position].packageName, !list.isItemChecked(position))
            }
            list.requestFocus()
        }
    }
}
