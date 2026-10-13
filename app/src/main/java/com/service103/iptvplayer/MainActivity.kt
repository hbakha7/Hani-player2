
package com.service103.iptvplayer

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import android.text.InputType
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Channel(
    val name: String,
    val url: String,
    val group: String = "عام",
    val logo: String = "",
    val type: String = "live",
    val id: String = ""
)

class MainActivity : AppCompatActivity() {

    private val bg = Color.rgb(10, 17, 29)
    private val panel = Color.rgb(22, 32, 49)
    private val accent = Color.rgb(25, 190, 165)
    private val white = Color.WHITE
    private val muted = Color.rgb(177, 190, 207)

    private val channels = mutableListOf<Channel>()
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var listContainer: LinearLayout? = null
    private var statusView: TextView? = null
    private var currentType = "live"
    private var currentGroup = "الكل"
    private var currentHost = ""
    private var currentUser = ""
    private var currentPass = ""
    private var fullscreenDialog: Dialog? = null
    private var fullscreenView: PlayerView? = null
    private var savedParent: ViewGroup? = null
    private var savedIndex = -1
    private var savedLayoutParams: ViewGroup.LayoutParams? = null

    private val prefs by lazy {
        getSharedPreferences("103_player_settings", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildHome()
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int = 14): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun label(
        value: String,
        size: Float = 15f,
        color: Int = white,
        bold: Boolean = false
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER_VERTICAL
        if (bold) setTypeface(null, Typeface.BOLD)
        setPadding(dp(10), dp(8), dp(10), dp(8))
    }

    private fun button(
        value: String,
        action: () -> Unit
    ) = Button(this).apply {
        text = value
        isAllCaps = false
        textSize = 14f
        setTextColor(white)
        background = rounded(accent)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
        ).apply {
            setMargins(dp(4), dp(4), dp(4), dp(4))
        }
    }

    private fun makeColumn() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(10), dp(12), dp(12))
        setBackgroundColor(bg)
    }

    private fun buildHome() {
        val root = makeColumn()
        val scroll = ScrollView(this)
        val content = makeColumn()

        val logo = label("103", 40f, accent, true)
        logo.gravity = Gravity.CENTER
        content.addView(logo)

        val title = label("103 IPTV PLAYER", 24f, white, true)
        title.gravity = Gravity.CENTER
        content.addView(title)

        val subtitle = label(
            "مشغّل قنواتك وأفلامك ومسلسلاتك",
            14f, muted
        )
        subtitle.gravity = Gravity.CENTER
        content.addView(subtitle)

        content.addView(button("إضافة قائمة M3U / M3U8") {
            askM3u()
        })

        content.addView(button("اتصال Xtream Codes") {
            askXtream()
        })

        content.addView(button("تشغيل رابط فيديو مباشر") {
            askDirect()
        })

        content.addView(button("الروابط والحسابات المحفوظة") {
            showSaved()
        })

        statusView = label("جاهز", 14f, accent)
        content.addView(statusView)

        scroll.addView(content)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )
        setContentView(root)
    }

    private fun askM3u() {
        val input = EditText(this).apply {
            hint = "https://example.com/list.m3u"
            setTextColor(white)
            setHintTextColor(muted)
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_URI
        }

        val saved = prefs.getString("last_m3u", "")
        input.setText(saved)

        AlertDialog.Builder(this)
            .setTitle("إضافة قائمة M3U")
            .setView(input)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("تحميل") { _, _ ->
                val link = input.text.toString().trim()
                if (link.startsWith("http://") ||
                    link.startsWith("https://")) {
                    prefs.edit().putString("last_m3u", link).apply()
                    loadM3u(link)
                } else {
                    message("أدخل رابطًا صحيحًا يبدأ بـ http أو https")
                }
            }.show()
    }

    private fun askDirect() {
        val input = EditText(this).apply {
            hint = "رابط فيديو MP4 أو M3U8"
            setTextColor(white)
            setHintTextColor(muted)
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_URI
        }

        AlertDialog.Builder(this)
            .setTitle("تشغيل رابط مباشر")
            .setView(input)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("تشغيل") { _, _ ->
                val link = input.text.toString().trim()
                if (link.startsWith("http://") ||
                    link.startsWith("https://")) {
                    channels.clear()
                    channels.add(
                        Channel("فيديو مباشر", link, "مباشر")
                    )
                    currentType = "live"
                    showLibrary()
                    play(channels.first())
                } else {
                    message("الرابط غير صحيح")
                }
            }.show()
    }

    private fun askXtream() {
        val layout = makeColumn()

        val host = EditText(this).apply {
            hint = "Server URL مثل http://server:8080"
            setTextColor(white)
            setHintTextColor(muted)
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_URI
        }

        val user = EditText(this).apply {
            hint = "اسم المستخدم"
            setTextColor(white)
            setHintTextColor(muted)
        }

        val pass = EditText(this).apply {
            hint = "كلمة المرور"
            setTextColor(white)
            setHintTextColor(muted)
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        host.setText(prefs.getString("xt_host", ""))
        user.setText(prefs.getString("xt_user", ""))

        layout.addView(host)
        layout.addView(user)
        layout.addView(pass)

        AlertDialog.Builder(this)
            .setTitle("اتصال Xtream Codes")
            .setView(layout)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("اتصال وحفظ") { _, _ ->
                val h = host.text.toString().trim().trimEnd('/')
                val u = user.text.toString().trim()
                val p = pass.text.toString()

                if (!(h.startsWith("http://") ||
                    h.startsWith("https://")) ||
                    u.isBlank() || p.isBlank()) {
                    message("تحقق من الخادم واسم المستخدم وكلمة المرور")
                    return@setPositiveButton
                }

                currentHost = h
                currentUser = u
                currentPass = p

                prefs.edit()
                    .putString("xt_host", h)
                    .putString("xt_user", u)
                    .putString("xt_pass", p)
                    .apply()

                loadXtream("live")
            }.show()
    }

    private fun showSaved() {
        val options = arrayOf(
            "آخر قائمة M3U",
            "حساب Xtream المحفوظ"
        )

        AlertDialog.Builder(this)
            .setTitle("المحفوظات")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val link = prefs.getString("last_m3u", "")
                        if (!link.isNullOrBlank()) loadM3u(link)
                        else message("لا توجد قائمة محفوظة")
                    }
                    1 -> {
                        val h = prefs.getString("xt_host", "") ?: ""
                        val u = prefs.getString("xt_user", "") ?: ""
                        val p = prefs.getString("xt_pass", "") ?: ""
                        if (h.isBlank() || u.isBlank() || p.isBlank()) {
                            message("لا يوجد حساب Xtream محفوظ")
                        } else {
                            currentHost = h
                            currentUser = u
                            currentPass = p
                            loadXtream("live")
                        }
                    }
                }
            }.show()
    }

    private fun loadM3u(link: String) {
        setStatus("جارٍ تحميل القائمة...")

        lifecycleScope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    val conn = URL(link).openConnection()
                        as HttpURLConnection
                    conn.connectTimeout = 15000
                    conn.readTimeout = 20000
                    conn.setRequestProperty(
                        "User-Agent", "103IPTVPlayer/1.0"
                    )
                    try {
                        if (conn.responseCode !in 200..299) {
                            error("HTTP ${conn.responseCode}")
                        }
                        conn.inputStream.bufferedReader().use {
                            it.readText()
                        }
                    } finally {
                        conn.disconnect()
                    }
                }

                val parsed = parseM3u(data)
                if (parsed.isEmpty()) {
                    setStatus("لم نجد روابط صالحة في القائمة")
                    return@launch
                }

                channels.clear()
                channels.addAll(parsed)
                currentType = "live"
                showLibrary()
                setStatus("تم تحميل ${parsed.size} رابطًا")
            } catch (e: Exception) {
                setStatus("فشل تحميل القائمة")
                message("تعذر تحميل القائمة:\n${e.message}")
            }
        }
    }

    private fun parseM3u(data: String): List<Channel> {
        val out = mutableListOf<Channel>()
        var name = "قناة"
        var group = "عام"
        var logo = ""

        data.lineSequence().forEach { raw ->
            val line = raw.trim()

            if (line.startsWith("#EXTINF", true)) {
                name = line.substringAfter(",", "قناة")
                    .trim().ifBlank { "قناة" }

                group = Regex(
                    """group-title=["']([^"']+)["']""",
                    RegexOption.IGNORE_CASE
                ).find(line)?.groupValues?.get(1) ?: "عام"

                logo = Regex(
                    """tvg-logo=["']([^"']+)["']""",
                    RegexOption.IGNORE_CASE
                ).find(line)?.groupValues?.get(1) ?: ""
            } else if (
                line.startsWith("http://") ||
                line.startsWith("https://")
            ) {
                out.add(
                    Channel(name, line, group, logo, "live")
                )
                name = "قناة"
                group = "عام"
                logo = ""
            }
        }

        return out
    }

    private fun loadXtream(type: String) {
        currentType = type
        setStatus("جارٍ الاتصال بخادم Xtream...")

        lifecycleScope.launch {
            try {
                val safeU = enc(currentUser)
                val safeP = enc(currentPass)
                val action = when (type) {
                    "live" -> "get_live_streams"
                    "movie" -> "get_vod_streams"
                    else -> "get_series"
                }

                val endpoint =
                    "$currentHost/player_api.php" +
                    "?username=$safeU&password=$safeP&action=$action"

                val data = withContext(Dispatchers.IO) {
                    val conn = URL(endpoint).openConnection()
                        as HttpURLConnection
                    conn.connectTimeout = 15000
                    conn.readTimeout = 25000
                    conn.setRequestProperty(
                        "User-Agent", "103IPTVPlayer/1.0"
                    )
                    try {
                        if (conn.responseCode !in 200..299) {
                            error("HTTP ${conn.responseCode}")
                        }
                        conn.inputStream.bufferedReader().use {
                            it.readText()
                        }
                    } finally {
                        conn.disconnect()
                    }
                }

                val arr = JSONArray(data)
                val parsed = mutableListOf<Channel>()

                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString(
                        if (type == "series") "series_id"
                        else if (type == "movie") "stream_id"
                        else "stream_id"
                    )

                    if (id.isBlank() || id == "null") continue

                    val name = obj.optString("name", "محتوى")
                    val group = obj.optString(
                        "category_name",
                        if (type == "live") "قنوات مباشرة"
                        else if (type == "movie") "أفلام"
                        else "مسلسلات"
                    )

                    val logo = obj.optString(
                        if (type == "live") "stream_icon"
                        else if (type == "movie") "stream_icon"
                        else "cover"
                    ).let { if (it == "null") "" else it }

                    val ext = obj.optString(
                        "container_extension", "ts"
                    ).ifBlank {
                        if (type == "movie") "mp4" else "ts"
                    }

                    val url = when (type) {
                        "live" ->
                            "$currentHost/live/$safeU/$safeP/$id.$ext"
                        "movie" ->
                            "$currentHost/movie/$safeU/$safeP/$id.$ext"
                        else -> ""
                    }

                    parsed.add(
                        Channel(name, url, group, logo, type, id)
                    )
                }

                if (parsed.isEmpty()) {
                    setStatus("لم يرجع الخادم أي عناصر")
                    message(
                        "لم يتم العثور على محتوى. تحقق من بيانات الحساب وصلاحياته."
                    )
                    return@launch
                }

                channels.clear()
                channels.addAll(parsed)
                showLibrary()
                setStatus("تم تحميل ${parsed.size} عنصرًا")
            } catch (e: Exception) {
                setStatus("تعذر الاتصال")
                message(
                    "فشل اتصال Xtream:\n" +
                    (e.message ?: "تحقق من الخادم والبيانات")
                )
            }
        }
    }

    private fun enc(value: String) =
        URLEncoder.encode(value, "UTF-8")

    private fun showLibrary() {
        val root = makeColumn()
        val scroll = ScrollView(this)
        val content = makeColumn()

        val title = label("103 IPTV PLAYER", 22f, accent, true)
        title.gravity = Gravity.CENTER
        content.addView(title)

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        listOf(
            "مباشر" to "live",
            "أفلام" to "movie",
            "مسلسلات" to "series"
        ).forEach { (caption, type) ->
            val b = Button(this).apply {
                text = caption
                isAllCaps = false
                setTextColor(white)
                background = rounded(panel)
                setOnClickListener {
                    if (currentHost.isNotBlank()) {
                        loadXtream(type)
                    } else {
                        val filtered = channels.filter {
                            it.type == type ||
                                (type == "live" && it.type == "live")
                        }
                        channels.clear()
                        channels.addAll(filtered)
                        currentType = type
                        renderItems()
                    }
                }
            }
            tabs.addView(
                b,
                LinearLayout.LayoutParams(0, dp(48), 1f)
            )
        }
        content.addView(tabs)

        playerView = PlayerView(this).apply {
            useController = true
            setBackgroundColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(-1, dp(225))
            keepScreenOn = true
        }
        content.addView(playerView)

        content.addView(button("⛶ ملء الشاشة") {
            openFullscreen()
        })

        content.addView(button("الصوت: رفع / خفض") {
            showVolume()
        })

        val groupLabel = label("المجموعة", 14f, muted)
        content.addView(groupLabel)

        val groups = channels.map { it.group }.distinct()
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("الكل") + groups
        )
        content.addView(spinner)

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(listContainer)

        spinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {}

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    currentGroup = spinner.selectedItem.toString()
                    renderItems()
                }
            }

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        if (player == null) {
            player = ExoPlayer.Builder(this).build()
            player?.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    message(
                        "تعذر تشغيل المحتوى.\n" +
                        error.errorCodeName
                    )
                }
            })
        }
        playerView?.player = player
        renderItems()
    }

    private fun renderItems() {
        val list = listContainer ?: return
        list.removeAllViews()

        val filtered = channels.filter {
            currentGroup == "الكل" || it.group == currentGroup
        }.take(200)

        filtered.forEach { ch ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(panel)
                setPadding(dp(6), dp(4), dp(6), dp(4))
            }

            val icon = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
                setImageResource(android.R.drawable.ic_media_play)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }

            row.addView(icon)

            if (ch.logo.startsWith("http")) {
                lifecycleScope.launch {
                    val bitmap = withContext(Dispatchers.IO) {
                        try {
                            URL(ch.logo).openStream().use {
                                android.graphics.BitmapFactory.decodeStream(it)
                            }
                        } catch (_: Exception) {
                            null
                        }
                    }
                    if (bitmap != null && !isFinishing) {
                        icon.setImageBitmap(bitmap)
                    }
                }
            }

            val details = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            details.addView(label(ch.name, 14f, white, true))
            details.addView(label(ch.group, 11f, muted))
            row.addView(
                details,
                LinearLayout.LayoutParams(0, -2, 1f)
            )

            row.setOnClickListener {
                if (ch.type == "series") {
                    loadEpisodes(ch)
                } else {
                    play(ch)
                }
            }

            list.addView(
                row,
                LinearLayout.LayoutParams(-1, dp(68)).apply {
                    setMargins(0, dp(3), 0, dp(3))
                }
            )
        }

        if (channels.size > 200) {
            list.addView(
                label(
                    "تظهر أول 200 نتيجة لتقليل استهلاك الذاكرة.",
                    12f, muted
                )
            )
        }
    }

    private fun loadEpisodes(series: Channel) {
        lifecycleScope.launch {
            try {
                val endpoint =
                    "$currentHost/player_api.php" +
                    "?username=${enc(currentUser)}" +
                    "&password=${enc(currentPass)}" +
                    "&action=get_series_info&series_id=${enc(series.id)}"

                val data = withContext(Dispatchers.IO) {
                    URL(endpoint).openConnection().getInputStream()
                        .bufferedReader().use { it.readText() }
                }

                val info = JSONObject(data)
                val episodes = info.optJSONObject("episodes")
                    ?: JSONObject()
                val choices = mutableListOf<Channel>()

                val keys = episodes.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val season = episodes.optJSONArray(key)
                        ?: continue

                    for (i in 0 until season.length()) {
                        val ep = season.optJSONObject(i) ?: continue
                        val id = ep.optString("id")
                        if (id.isBlank() || id == "null") continue
                        val ext = ep.optString(
                            "container_extension", "mp4"
                        )
                        val name = ep.optString(
                            "title", "حلقة ${i + 1}"
                        )
                        val url =
                            "$currentHost/series/${enc(currentUser)}" +
                            "/${enc(currentPass)}/$id.$ext"
                        choices.add(
                            Channel(name, url, "الحلقات", "", "episode")
                        )
                    }
                }

                if (choices.isEmpty()) {
                    message("لم يعثر الخادم على حلقات لهذا المسلسل")
                    return@launch
                }

                AlertDialog.Builder(this@MainActivity)
                    .setTitle(series.name)
                    .setItems(
                        choices.map { it.name }.toTypedArray()
                    ) { _, which ->
                        play(choices[which])
                    }
                    .setNegativeButton("إغلاق", null)
                    .show()
            } catch (e: Exception) {
                message("تعذر تحميل الحلقات:\n${e.message}")
            }
        }
    }

    private fun play(ch: Channel) {
        try {
            if (player == null) {
                player = ExoPlayer.Builder(this).build()
            }
            if (playerView == null) {
                showLibrary()
            }
            playerView?.player = player

            val item = MediaItem.Builder()
                .setUri(ch.url)
                .setMediaId(ch.name)
                .build()

            player?.setMediaItem(item)
            player?.prepare()
            player?.playWhenReady = true
            setStatus("قيد التشغيل: ${ch.name}")
        } catch (e: Exception) {
            message("تعذر بدء التشغيل:\n${e.message}")
        }
    }

    
private fun openFullscreen() {
    val original = playerView ?: return
    val parent = original.parent as? ViewGroup ?: return

    val oldOrientation = requestedOrientation
    val oldIndex = parent.indexOfChild(original)
    val oldParams = original.layoutParams

    parent.removeView(original)

    val dialog = Dialog(
        this,
        android.R.style.Theme_Black_NoTitleBar_Fullscreen
    )

    val frame = FrameLayout(this).apply {
        setBackgroundColor(Color.BLACK)
    }

    val fullPlayer = PlayerView(this).apply {
        useController = true
        this.player = this@MainActivity.player
        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
        layoutParams = FrameLayout.LayoutParams(-1, -1)
    }

    frame.addView(fullPlayer)

    val exit = Button(this).apply {
        text = "✕"
        textSize = 20f
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = rounded(Color.DKGRAY)
        setOnClickListener { dialog.dismiss() }
    }

    frame.addView(
        exit,
        FrameLayout.LayoutParams(dp(52), dp(48)).apply {
            gravity = Gravity.TOP or Gravity.END
            setMargins(dp(8), dp(8), dp(8), dp(8))
        }
    )

    dialog.setContentView(frame)

    dialog.setOnDismissListener {
        fullPlayer.player = null
        (fullPlayer.parent as? ViewGroup)?.removeView(fullPlayer)

        if (original.parent == null) {
            parent.addView(
                original,
                oldIndex.coerceIn(0, parent.childCount),
                oldParams
            )
        }

        original.player = player
        requestedOrientation = oldOrientation
        fullscreenDialog = null
    }

    fullscreenDialog = dialog
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    dialog.show()
    dialog.window?.setFlags(
        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
    )
}

        

    private fun showVolume() {
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)

        val seek = SeekBar(this).apply {
            this.max = max
            progress = current
        }

        AlertDialog.Builder(this)
            .setTitle("مستوى الصوت")
            .setView(seek)
            .setPositiveButton("تم", null)
            .show()

        seek.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    bar: SeekBar?, value: Int, fromUser: Boolean
                ) {
                    if (fromUser) {
                        audio.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            value, 0
                        )
                    }
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            }
        )
    }

    private fun setStatus(value: String) {
        runOnUiThread {
            statusView?.text = value
        }
    }

    private fun message(value: String) {
        if (isFinishing) return
        AlertDialog.Builder(this)
            .setMessage(value)
            .setPositiveButton("حسنًا", null)
            .show()
    }

    override fun onDestroy() {
        fullscreenDialog?.dismiss()
        playerView?.player = null
        fullscreenView?.player = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
