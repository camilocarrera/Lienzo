package co.camilo.lienzo

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Un bloque es cualquier cosa que la persona pone en su pantalla.
 * type: "clock" (reloj y fecha), "note" (nota de texto) o "app" (atajo a una app).
 * data: el texto de la nota o el nombre de paquete de la app.
 */
data class Block(
    val id: String,
    val type: String,
    var x: Float,
    var y: Float,
    var data: String = ""
)

/** Guarda y recupera los bloques en las preferencias del celular. */
class BlockStore(context: Context) {

    private val prefs = context.getSharedPreferences("lienzo", Context.MODE_PRIVATE)

    fun load(): MutableList<Block> {
        val out = mutableListOf<Block>()
        try {
            val arr = JSONArray(prefs.getString(KEY, "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Block(
                        id = o.getString("id"),
                        type = o.getString("type"),
                        x = o.getDouble("x").toFloat(),
                        y = o.getDouble("y").toFloat(),
                        data = o.optString("data", "")
                    )
                )
            }
        } catch (e: Exception) {
            // Si algo salió mal al leer, se parte de una pantalla en blanco.
        }
        return out
    }

    fun save(blocks: List<Block>) {
        val arr = JSONArray()
        for (b in blocks) {
            arr.put(
                JSONObject()
                    .put("id", b.id)
                    .put("type", b.type)
                    .put("x", b.x.toDouble())
                    .put("y", b.y.toDouble())
                    .put("data", b.data)
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object {
        const val KEY = "blocks"
    }
}
