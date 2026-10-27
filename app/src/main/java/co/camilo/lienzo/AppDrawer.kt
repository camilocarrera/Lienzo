package co.camilo.lienzo

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

class AppEntry(val label: String, val pkg: String, val icon: Drawable)

/**
 * Cajón de apps que sube desde abajo. Tiene buscador.
 * Se puede abrir en modo normal (al tocar una app se abre) o en modo
 * elegir (al tocar una app se devuelve a quien la pidió, para crear un atajo).
 */
class AppDrawer(
    private val activity: Activity,
    private val onLaunch: (String) -> Unit
) : FrameLayout(activity) {

    private var all: List<AppEntry> = emptyList()
    private var shown: List<AppEntry> = emptyList()
    private var pickCallback: ((AppEntry) -> Unit)? = null

    private val title = TextView(activity)
    private val search = EditText(activity)
    private val list = ListView(activity)

    val isOpen: Boolean
        get() = visibility == View.VISIBLE

    private val appsAdapter = object : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int): Any = shown[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = (convertView as? LinearLayout) ?: LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), dp(10), dp(20), dp(10))
                addView(ImageView(activity), LinearLayout.LayoutParams(dp(44), dp(44)))
                addView(
                    TextView(activity).apply {
                        setTextColor(Color.WHITE)
                        textSize = 17f
                        setPadding(dp(16), 0, 0, 0)
                    },
                    LinearLayout.LayoutParams(WRAP, WRAP)
                )
            }
            val entry = shown[position]
            (row.getChildAt(0) as ImageView).setImageDrawable(entry.icon)
            (row.getChildAt(1) as TextView).text = entry.label
            return row
        }
    }

    init {
        visibility = GONE
        isClickable = true // se queda con los toques para que no pasen al lienzo
        setBackgroundColor(0xF0101014.toInt())

        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(48), dp(8), 0)
        }

        title.apply {
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }

        search.apply {
            hint = "Buscar"
            setHintTextColor(0x88FFFFFF.toInt())
            setTextColor(Color.WHITE)
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_GO
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }

        list.apply {
            adapter = appsAdapter
            divider = null
            dividerHeight = 0
            cacheColorHint = Color.TRANSPARENT
        }

        column.addView(title, LinearLayout.LayoutParams(MATCH, WRAP))
        column.addView(search, LinearLayout.LayoutParams(MATCH, WRAP))
        column.addView(list, LinearLayout.LayoutParams(MATCH, 0, 1f))
        addView(column, LayoutParams(MATCH, MATCH))

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Enter en el teclado abre la primera coincidencia
        search.setOnEditorActionListener { _, _, _ ->
            shown.firstOrNull()?.let { choose(it) }
            true
        }

        list.setOnItemClickListener { _, _, position, _ -> choose(shown[position]) }
    }

    fun open(onPick: ((AppEntry) -> Unit)? = null) {
        pickCallback = onPick
        title.text = if (onPick == null) "Todas las apps" else "Elige una app"
        search.setText("")
        refresh()
        animate().cancel()
        visibility = VISIBLE
        val h = if (height > 0) height else resources.displayMetrics.heightPixels
        translationY = h.toFloat()
        animate().translationY(0f).setDuration(220).start()
        list.setSelection(0)
    }

    fun close() {
        hideKeyboard()
        pickCallback = null
        val h = if (height > 0) height else resources.displayMetrics.heightPixels
        animate().translationY(h.toFloat()).setDuration(180)
            .withEndAction { visibility = GONE }
            .start()
    }

    private fun choose(entry: AppEntry) {
        val callback = pickCallback
        close()
        if (callback != null) callback(entry) else onLaunch(entry.pkg)
    }

    private fun refresh() {
        val pm = activity.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        all = pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != activity.packageName }
            .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm)) }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
        applyFilter()
    }

    private fun applyFilter() {
        val q = search.text.toString().trim()
        shown = if (q.isEmpty()) all else all.filter { it.label.contains(q, ignoreCase = true) }
        appsAdapter.notifyDataSetChanged()
    }

    private fun hideKeyboard() {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
