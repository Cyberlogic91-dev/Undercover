package app.undercover.game

import android.content.Context
import android.content.Intent
import android.graphics.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Génère de belles images (fin de partie, podium) et les partage via Android. */
object ShareImage {
    private const val W = 1080

    private class Fonts(ctx: Context) {
        val black: Typeface = ResourcesCompat.getFont(ctx, R.font.montserrat_black) ?: Typeface.DEFAULT_BOLD
        val bold: Typeface = ResourcesCompat.getFont(ctx, R.font.montserrat_bold) ?: Typeface.DEFAULT_BOLD
        val regular: Typeface = ResourcesCompat.getFont(ctx, R.font.montserrat_regular) ?: Typeface.DEFAULT
    }

    private fun paint(tf: Typeface, size: Float, color: Int, align: Paint.Align = Paint.Align.CENTER) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = size; this.color = color; textAlign = align }

    private fun background(c: Canvas, h: Int, theme: SeasonTheme) {
        c.drawPaint(Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0xFF161A36.toInt(), 0xFF080A1A.toInt(), 0xFF03040C.toInt()), null, Shader.TileMode.CLAMP)
        })
        val blobs = listOf(Triple(.15f, .12f, theme.aurora[0]), Triple(.95f, .10f, theme.aurora[1]), Triple(.3f, .7f, theme.aurora[2]), Triple(.8f, .85f, theme.aurora[3]))
        blobs.forEach { (x, y, col) ->
            val cx = W * x; val cy = h * y; val r = W * .6f
            c.drawCircle(cx, cy, r, Paint().apply { shader = RadialGradient(cx, cy, r, col.toArgb(), Color.TRANSPARENT, Shader.TileMode.CLAMP) })
        }
    }

    private fun header(ctx: Context, c: Canvas, f: Fonts, top: Float): Float {
        ContextCompat.getDrawable(ctx, R.drawable.ic_logo)?.let { d -> d.setBounds(W / 2 - 110, top.toInt(), W / 2 + 110, top.toInt() + 220); d.draw(c) }
        val y = top + 290
        val pu = paint(f.black, 74f, Color.WHITE, Paint.Align.LEFT)
        val pc = paint(f.black, 74f, Teal.toArgb(), Paint.Align.LEFT)
        val w1 = pu.measureText("UNDER"); val w2 = pc.measureText("COVER")
        val x = (W - w1 - w2) / 2
        c.drawText("UNDER", x, y, pu); c.drawText("COVER", x + w1, y, pc)
        return y + 40
    }

    private fun footer(c: Canvas, f: Fonts, h: Int) {
        c.drawText("Développé par Mickaël Monestier", W / 2f, h - 50f, paint(f.regular, 30f, 0xFF9AA3B8.toInt()))
    }

    private fun avatar(c: Canvas, f: Fonts, img: ImageBitmap?, name: String, cx: Float, cy: Float, r: Float, ring: Int) {
        c.drawCircle(cx, cy, r + 6, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ring })
        val clip = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
        c.save(); c.clipPath(clip)
        if (img != null) {
            c.drawBitmap(img.asAndroidBitmap(), null, RectF(cx - r, cy - r, cx + r, cy + r), Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            val cols = listOf(0xFFFFB020, 0xFF3DDC97, 0xFF5AA9FF, 0xFFFF7AB6, 0xFFB38CFF, 0xFFFF8A4C, 0xFF4FD1C5)
            c.drawCircle(cx, cy, r, Paint().apply { color = cols[(name.trim().lowercase().hashCode() and 0x7fffffff) % cols.size].toInt() })
            val initials = name.trim().split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
            val p = paint(f.black, r * .8f, 0xFF111111.toInt())
            c.drawText(initials, cx, cy - (p.descent() + p.ascent()) / 2, p)
        }
        c.restore()
    }

    private fun roundRect(c: Canvas, l: Float, t: Float, r: Float, b: Float, fill: Int, stroke: Int? = null) {
        val rect = RectF(l, t, r, b)
        c.drawRoundRect(rect, 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill })
        if (stroke != null) c.drawRoundRect(rect, 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = stroke; style = Paint.Style.STROKE; strokeWidth = 4f })
    }

    private fun fit(text: String, p: Paint, max: Float): String {
        if (p.measureText(text) <= max) return text
        var t = text
        while (t.length > 1 && p.measureText("$t…") > max) t = t.dropLast(1)
        return "$t…"
    }

    /** Image de fin de partie. */
    fun gameResult(ctx: Context, vm: GameViewModel): Bitmap {
        val f = Fonts(ctx)
        val ps = vm.players
        val h = 1000 + ps.size * 130 + 160
        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        background(c, h, vm.theme)
        var y = header(ctx, c, f, 60f)
        val (title, col) = when (vm.outcome) {
            Outcome.CIVILS -> "Les Civils gagnent !" to Civil
            Outcome.IMPOSTORS -> "Les imposteurs gagnent !" to Under
            Outcome.MRWHITE -> "Mr. White a trouvé le mot !" to White
            null -> "" to Amber
        }
        y += 70
        c.drawText(title, W / 2f, y, paint(f.black, 62f, col.toArgb()))
        // mots
        y += 50
        roundRect(c, 60f, y, W - 60f, y + 200, 0xB8161A30.toInt(), 0x33FFFFFF)
        c.drawText("Mot des Civils", W * .27f, y + 70, paint(f.regular, 32f, 0xFF9AA3B8.toInt()))
        c.drawText("Mot Undercover", W * .73f, y + 70, paint(f.regular, 32f, 0xFF9AA3B8.toInt()))
        val pw = paint(f.black, 50f, Civil.toArgb())
        c.drawText(fit(vm.civilWord, pw, W * .4f), W * .27f, y + 145, pw)
        val pu = paint(f.black, 50f, Under.toArgb())
        c.drawText(fit(vm.undercoverWord, pu, W * .4f), W * .73f, y + 145, pu)
        y += 260
        // joueurs
        val winners = ps.filter {
            when (vm.outcome) {
                Outcome.CIVILS -> it.role == Role.CIVIL
                Outcome.IMPOSTORS -> it.role != Role.CIVIL
                Outcome.MRWHITE -> it.role == Role.MRWHITE
                null -> false
            }
        }.map { it.id }.toSet()
        ps.forEach { p ->
            val win = p.id in winners
            roundRect(c, 60f, y, W - 60f, y + 112, if (win) 0x33FFC21A else 0x99161A30.toInt(), if (win) Amber.toArgb() else null)
            avatar(c, f, vm.avatarOf(p.id), p.name, 130f, y + 56, 40f, p.role.color().toArgb())
            val pn = paint(f.bold, 40f, Color.WHITE, Paint.Align.LEFT)
            c.drawText(fit((if (win) "👑 " else "") + p.name + if (p.bodyguard) " 🛡️" else "", pn, 520f), 200f, y + 70, pn)
            c.drawText(p.role.label, W - 100f, y + 70, paint(f.black, 38f, p.role.color().toArgb(), Paint.Align.RIGHT))
            y += 130
        }
        c.drawText(SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRANCE).format(Date()), W / 2f, y + 50, paint(f.regular, 30f, 0xFF9AA3B8.toInt()))
        footer(c, f, h)
        return bmp
    }

    /** Image du podium de la soirée. */
    fun podium(ctx: Context, vm: GameViewModel): Bitmap {
        val f = Fonts(ctx)
        val rank = vm.eveningRanking()
        val rest = rank.drop(3)
        val h = 1500 + rest.size * 100 + 120
        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        background(c, h, vm.theme)
        var y = header(ctx, c, f, 60f)
        y += 80
        c.drawText("🏆 Championnat de la soirée", W / 2f, y, paint(f.black, 56f, Amber.toArgb()))
        y += 55
        c.drawText("${vm.eveningGames} partie(s) · " + SimpleDateFormat("d MMMM yyyy", Locale.FRANCE).format(Date()),
            W / 2f, y, paint(f.regular, 32f, 0xFF9AA3B8.toInt()))
        // podium : 2e, 1er, 3e
        val base = y + 720
        val slots = listOf(1 to (W * .2f), 0 to (W * .5f), 2 to (W * .8f))
        val heights = listOf(330f, 240f, 170f)
        val colors = listOf(0xFFFFC21A.toInt(), 0xFFC9D2E3.toInt(), 0xFFE0904A.toInt())
        slots.forEach { (i, cx) ->
            val e = rank.getOrNull(i) ?: return@forEach
            val top = base - heights[i]
            c.drawRoundRect(RectF(cx - 140, top, cx + 140, base), 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, top, 0f, base, colors[i], Color.argb(120, Color.red(colors[i]), Color.green(colors[i]), Color.blue(colors[i])), Shader.TileMode.CLAMP)
            })
            c.drawText("${i + 1}", cx, top + 110, paint(f.black, 100f, 0xFF14183A.toInt()))
            val r = if (i == 0) 85f else 70f
            avatar(c, f, vm.avatarOfName(e.first), e.first, cx, top - r - 40, r, colors[i])
            if (i == 0) c.drawText("👑", cx, top - 2 * r - 50, paint(f.regular, 80f, Color.WHITE))
            val pn = paint(f.bold, 40f, Color.WHITE)
            c.drawText(fit(e.first, pn, 300f), cx, base + 60, pn)
            c.drawText("${e.second} pts · ${e.third} vict.", cx, base + 105, paint(f.regular, 30f, 0xFF9AA3B8.toInt()))
        }
        y = base + 170
        rest.forEachIndexed { k, e ->
            roundRect(c, 80f, y, W - 80f, y + 84, 0x99161A30.toInt())
            c.drawText("${k + 4}.", 130f, y + 56, paint(f.black, 36f, 0xFF9AA3B8.toInt(), Paint.Align.LEFT))
            val pn = paint(f.bold, 36f, Color.WHITE, Paint.Align.LEFT)
            c.drawText(fit(e.first, pn, 500f), 220f, y + 56, pn)
            c.drawText("${e.second} pts", W - 120f, y + 56, paint(f.black, 36f, Amber.toArgb(), Paint.Align.RIGHT))
            y += 100
        }
        footer(c, f, h)
        return bmp
    }

    /** Enregistre l'image et ouvre le menu de partage Android (WhatsApp, SMS…). */
    fun share(ctx: Context, bmp: Bitmap, name: String, text: String) {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "$name.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "Partager le résultat").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
