package co.camilo.lienzo

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.AlarmClock
import android.text.InputType
import android.text.TextUtils
import android.text.format.DateFormat
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * Lienzo: un launcher que arranca en blanco.
 *  - Mantener presionado en un espacio vacío: agregar reloj, nota, atajo a app o cambiar fondo.
 *  - Arrastrar un bloque para moverlo. Mantener presionado sobre un bloque: editar o quitar.
 *  - Deslizar hacia arriba: lista de todas las apps con buscador.
 */
class MainActivity : Activity() {

    private lateinit var canvas: FrameLayout
    private lateinit var hint: TextView
    private lateinit var drawer: AppDrawer
    private lateinit var store: BlockStore

    private val blocks = mutableListOf<Block>()
    private val views = mutableMapOf<String, View>()
    private var touchSlop = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = BlockStore(this)
        touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        val root = FrameLayout(this)

        canvas = FrameLayout(this)
        root.addView(canvas, FrameLayout.LayoutParams(MATCH, MATCH))

        hint = TextView(this).apply {
            text = "Mantén presionado para agregar algo\nDesliza hacia arriba para ver tus apps"
            setTextColor(0xCCFFFFFF.toInt())
            gravity = Gravity.CENTER
            textSize = 16f
            setShadowLayer(6f, 0f, 2f, Color.BLACK)
        }
        root.addView(hint, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))

        drawer = AppDrawer(this) { pkg -> launchApp(pkg) }
        root.addView(drawer, FrameLayout.LayoutParams(MATCH, MATCH))

        setContentView(root)

        // Gestos sobre el espacio vacío
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onLongPress(e: MotionEvent) {
                root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                showAddMenu(e.x, e.y)
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 != null &&
                    e1.y - e2.y > dp(80) &&
                    vy < -600 &&
                    abs(vy) > abs(vx)
                ) {
                    drawer.open()
                    return true
                }
                return false
            }
        })
        root.setOnTouchListener { _, e -> detector.onTouchEvent(e) }

        blocks.addAll(store.load())
        blocks.forEach { mount(it) }
        updateHint()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Al tocar el botón de inicio estando en el cajón, se vuelve al lienzo
        if (drawer.isOpen) drawer.close()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Un launcher no se cierra con atrás; solo cierra el cajón si está abierto
        if (drawer.isOpen) drawer.close()
    }

    // ---------- Bloques ----------

    private fun addBlock(type: String, x: Float, y: Float, data: String = ""): Block {
        val block = Block(UUID.randomUUID().toString(), type, x - dp(40), y - dp(30), data)
        blocks.add(block)
        mount(block)
        store.save(blocks)
        updateHint()
        return block
    }

    private fun mount(block: Block) {
        val view = buildView(block)
        canvas.addView(view, FrameLayout.LayoutParams(WRAP, WRAP))
        view.x = block.x
        view.y = block.y
        views[block.id] = view
        attachTouch(view, block)
        // Cuando ya tiene tamaño, se asegura de que quede dentro de la pantalla
        view.post {
            val maxX = (canvas.width - view.width).toFloat().coerceAtLeast(0f)
            val maxY = (canvas.height - view.height).toFloat().coerceAtLeast(0f)
            view.x = view.x.coerceIn(0f, maxX)
            view.y = view.y.coerceIn(0f, maxY)
            block.x = view.x
            block.y = view.y
        }
    }

    private fun removeBlock(block: Block) {
        views.remove(block.id)?.let { canvas.removeView(it) }
        blocks.remove(block)
        store.save(blocks)
        updateHint()
    }

    private fun updateHint() {
        hint.visibility = if (blocks.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun buildView(block: Block): View {
        val ctx: Context = this
        return when (block.type) {
            "clock" -> LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    TextClock(ctx).apply {
                        format12Hour = "h:mm"
                        format24Hour = "HH:mm"
                        textSize = 64f
                        setTextColor(Color.WHITE)
                        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                        setShadowLayer(8f, 0f, 2f, 0x99000000.toInt())
                    },
                    LinearLayout.LayoutParams(WRAP, WRAP)
                )
                val datePattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM")
                addView(
                    TextClock(ctx).apply {
                        format12Hour = datePattern
                        format24Hour = datePattern
                        textSize = 16f
                        setTextColor(0xDDFFFFFF.toInt())
                        setShadowLayer(6f, 0f, 1f, 0x99000000.toInt())
                    },
                    LinearLayout.LayoutParams(WRAP, WRAP)
                )
            }

            "note" -> TextView(ctx).apply {
                text = noteText(block)
                setTextColor(Color.WHITE)
                textSize = 16f
                minWidth = dp(140)
                maxWidth = dp(260)
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = GradientDrawable().apply {
                    setColor(0x80000000.toInt())
                    cornerRadius = dp(18).toFloat()
                }
            }

            "app" -> LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                val icon = ImageView(ctx)
                icon.setImageDrawable(
                    try {
                        ctx.packageManager.getApplicationIcon(block.data)
                    } catch (e: Exception) {
                        ctx.getDrawable(android.R.drawable.sym_def_app_icon)
                    }
                )
                addView(icon, LinearLayout.LayoutParams(dp(56), dp(56)))
                addView(
                    TextView(ctx).apply {
                        text = appLabel(block.data)
                        setTextColor(Color.WHITE)
                        textSize = 12f
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        gravity = Gravity.CENTER
                        maxWidth = dp(80)
                        setShadowLayer(6f, 0f, 1f, Color.BLACK)
                    },
                    LinearLayout.LayoutParams(WRAP, WRAP)
                )
            }

            else -> View(ctx)
        }
    }

    private fun noteText(block: Block): String =
        if (block.data.isBlank()) "Toca para escribir" else block.data

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    // ---------- Toques sobre un bloque: tocar, arrastrar, mantener ----------

    private fun attachTouch(view: View, block: Block) {
        var downX = 0f
        var downY = 0f
        var startX = 0f
        var startY = 0f
        var dragging = false
        var longPressed = false

        val longPress = Runnable {
            if (!dragging) {
                longPressed = true
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                showBlockMenu(block)
            }
        }

        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = v.x
                    startY = v.y
                    dragging = false
                    longPressed = false
                    v.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && hypot(dx, dy) > touchSlop) {
                        dragging = true
                        v.removeCallbacks(longPress)
                    }
                    if (dragging) {
                        val maxX = (canvas.width - v.width).toFloat().coerceAtLeast(0f)
                        val maxY = (canvas.height - v.height).toFloat().coerceAtLeast(0f)
                        v.x = (startX + dx).coerceIn(0f, maxX)
                        v.y = (startY + dy).coerceIn(0f, maxY)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    v.removeCallbacks(longPress)
                    if (dragging) {
                        block.x = v.x
                        block.y = v.y
                        store.save(blocks)
                    } else if (!longPressed) {
                        onBlockTap(block)
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    v.removeCallbacks(longPress)
                    true
                }

                else -> false
            }
        }
    }

    private fun onBlockTap(block: Block) {
        when (block.type) {
            "note" -> editNote(block)
            "app" -> launchApp(block.data)
            "clock" -> try {
                startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS))
            } catch (e: ActivityNotFoundException) {
                // No hay app de reloj, no pasa nada
            }
        }
    }

    // ---------- Menús y diálogos ----------

    private fun showAddMenu(x: Float, y: Float) {
        val items = arrayOf("Reloj", "Nota", "Atajo a una app", "Cambiar fondo")
        AlertDialog.Builder(this)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> addBlock("clock", x, y)
                    1 -> editNote(addBlock("note", x, y))
                    2 -> drawer.open { entry -> addBlock("app", x, y, entry.pkg) }
                    3 -> changeWallpaper()
                }
            }
            .show()
    }

    private fun showBlockMenu(block: Block) {
        val items = if (block.type == "note") arrayOf("Editar", "Quitar") else arrayOf("Quitar")
        AlertDialog.Builder(this)
            .setItems(items) { _, which ->
                if (items[which] == "Editar") editNote(block) else removeBlock(block)
            }
            .show()
    }

    private fun editNote(block: Block) {
        val input = EditText(this).apply {
            setText(block.data)
            hint = "Escribe tu nota"
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 3
            gravity = Gravity.TOP
        }
        AlertDialog.Builder(this)
            .setTitle("Nota")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                block.data = input.text.toString().trim()
                (views[block.id] as? TextView)?.text = noteText(block)
                store.save(blocks)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun changeWallpaper() {
        try {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Cambiar fondo"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Este celular no permite cambiar el fondo desde aquí", Toast.LENGTH_LONG).show()
        }
    }

    private fun launchApp(pkg: String) {
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "No se pudo abrir la app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
