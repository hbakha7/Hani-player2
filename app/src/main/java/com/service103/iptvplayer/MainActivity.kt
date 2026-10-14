package com.service103.iptvplayer

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import org.json.JSONObject
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private data class Channel(
    val name: String,
    val url: String,
    val group: String = "عام",
    val logo: String = "",
    val type: String = "live",
    val id: String = ""
)

class MainActivity : ComponentActivity() {
    private val bg = Color.rgb(12, 16, 25)
    private val panel = Color.rgb(25, 32, 46)
    private val accent = Color.rgb(28, 140, 255)
    private val white = Color.WHITE
    private val muted = Color.rgb(175, 185, 200)
    private val prefs by lazy { getSharedPreferences("103_player_settings", MODE_PRIVATE) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val items = mutableListOf<Channel>()
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var fullscreenDialog: AlertDialog? = null
    private var statusView: TextView? = null
    private var currentTitle = "103 IPTV Player"
    private var currentUrl = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildHome()
    }

    private fun rounded(color: Int, radius: Float = 14f): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = radius
    }

    private fun baseColumn(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(18, 18, 18, 18)
        setBackgroundColor(bg)
    }

    private fun label(text: String, size: Float = 16f, color: Int = white, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun button(text: String, action: () -> Unit): Button = Button(this).apply {
        this.text = text; setTextColor(white); textSize = 14f
        background = rounded(accent, 12f)
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun field(hint: String, value: String = "", secret: Boolean = false, multiline: Boolean = false): EditText = EditText(this).apply {
        setText(value); this.hint = hint; setHintTextColor(Color.LTGRAY); setTextColor(white)
        textSize = 15f; setPadding(14, 10, 14, 10); background = rounded(panel, 10f)
        inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        if (multiline) { minLines = 2; gravity = Gravity.TOP; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10 }
    }

    private fun addGap(parent: LinearLayout, h: Int = 10) { parent.addView(View(this), LinearLayout.LayoutParams(1, h)) }

    private fun buildHome() {
        val root = baseColumn()
        root.addView(label("103 IPTV Player", 25f, white, true))
        root.addView(label("مشغّل البث والقوائم التي تملك حق استخدامها", 13f, muted))
        addGap(root, 18)
        root.addView(button("▶ تشغيل رابط فيديو مباشر") { askDirect() }, LinearLayout.LayoutParams(-1, 54).apply { bottomMargin = 10 })
        root.addView(button("☷ إضافة قائمة M3U / M3U8") { askM3u() }, LinearLayout.LayoutParams(-1, 54).apply { bottomMargin = 10 })
        root.addView(button("▣ الاتصال بـ Xtream Codes") { askXtream() }, LinearLayout.LayoutParams(-1, 54).apply { bottomMargin = 10 })
        root.addView(button("＋ إضافة رابط مع إعدادات متقدمة") { showAddLinkForm() }, LinearLayout.LayoutParams(-1, 54).apply { bottomMargin = 10 })
        root.addView(button("★ الروابط والحسابات المحفوظة") { showSaved() }, LinearLayout.LayoutParams(-1, 54).apply { bottomMargin = 10 })
        addGap(root, 12)
        statusView = label("جاهز للتشغيل", 13f, muted)
        root.addView(statusView)
        root.addView(label("القنوات المحمّلة: ${items.size}", 13f, muted))
        setContentView(root)
    }

    private fun showAddLinkForm() {
        val scroll = ScrollView(this)
        val form = baseColumn()
        form.addView(label("إضافة رابط وإعدادات التشغيل", 22f, white, true)); addGap(form)
        val name = field("اسم الرابط", prefs.getString("link_name", "") ?: "")
        val url = field("URL الرابط", prefs.getString("link_url", "") ?: "")
        val ua = field("User-Agent (اختياري)", prefs.getString("link_ua", "") ?: "")
        val ref = field("Referer (اختياري)", prefs.getString("link_ref", "") ?: "")
        val auth = field("Authorization / بيانات المصادقة (اختياري)", prefs.getString("link_auth", "") ?: "")
        val cookie = field("Cookie (اختياري)", prefs.getString("link_cookie", "") ?: "")
        val license = field("License URL (DRM عند الحاجة)", prefs.getString("link_license", "") ?: "")
        listOf(name, url, ua, ref, auth, cookie, license).forEach { form.addView(it) }
        form.addView(label("نوع عرض الرابط", 14f, muted))
        val mode = Spinner(this)
        mode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("EXO PLAYER", "WEB BROWSER", "WEB PLAYER"))
        form.addView(mode)
        val m3uCheck = CheckBox(this).apply { text = "هذا رابط قائمة M3U/M3U8"; setTextColor(white); isChecked = prefs.getBoolean("link_is_m3u", false) }
        form.addView(m3uCheck)
        addGap(form, 12)
        form.addView(button("حفظ وتشغيل") {
            val link = url.text.toString().trim()
            if (link.isBlank()) { message("أدخل الرابط أولاً"); return@button }
            prefs.edit().putString("link_name", name.text.toString()).putString("link_url", link)
                .putString("link_ua", ua.text.toString()).putString("link_ref", ref.text.toString())
                .putString("link_auth", auth.text.toString()).putString("link_cookie", cookie.text.toString())
                .putString("link_license", license.text.toString()).putBoolean("link_is_m3u", m3uCheck.isChecked).apply()
            if (m3uCheck.isChecked) loadM3u(link) else if (mode.selectedItemPosition == 1) {
                try { startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link))) }
                catch (_: Exception) { message("تعذّر فتح الرابط في المتصفح") }
            } else if (mode.selectedItemPosition == 2) message("مشغل WEB PLAYER غير مفعّل في هذه النسخة؛ استخدم EXO PLAYER")
            else play(Channel(name.text.toString().ifBlank { "فيديو مباشر" }, link))
        }, LinearLayout.LayoutParams(-1, 52))
        form.addView(button("رجوع") { buildHome() }, LinearLayout.LayoutParams(-1, 50).apply { topMargin = 8 })
        scroll.addView(form); setContentView(scroll)
    }

    private fun askDirect() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 8, 20, 0) }
        val title = field("اسم الفيديو (اختياري)")
        val link = field("ألصق رابط MP4 أو M3U8", prefs.getString("last_direct_url", "") ?: "")
        box.addView(title); box.addView(link)
        AlertDialog.Builder(this).setTitle("تشغيل رابط مباشر").setView(box)
            .setPositiveButton("تشغيل") { _, _ ->
                val u = link.text.toString().trim()
                if (u.isNotEmpty()) { prefs.edit().putString("last_direct_url", u).apply(); playDirectFullscreen(u, title.text.toString().ifBlank { "فيديو مباشر" }) }
                else message("أدخل رابط الفيديو")
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun playDirectFullscreen(link: String, title: String = "فيديو مباشر") {
        play(Channel(title, link, type = "live"))
        // The fullscreen dialog is opened after PlayerView is prepared.
        window.decorView.postDelayed({ if (!isFinishing) openFullscreen() }, 250)
    }

    private fun askM3u() {
        val input = field("رابط قائمة M3U / M3U8", prefs.getString("last_m3u", "") ?: "")
        AlertDialog.Builder(this).setTitle("تحميل قائمة").setView(input)
            .setPositiveButton("تحميل") { _, _ -> val u = input.text.toString().trim(); if (u.isNotEmpty()) loadM3u(u) }
            .setNegativeButton("إلغاء", null).show()
    }

    private fun askXtream() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 8, 20, 0) }
        val host = field("Server URL", prefs.getString("xt_host", "") ?: "")
        val user = field("Username", prefs.getString("xt_user", "") ?: "")
        val pass = field("Password", prefs.getString("xt_pass", "") ?: "", secret = true)
        box.addView(host); box.addView(user); box.addView(pass)
        AlertDialog.Builder(this).setTitle("Xtream Codes").setView(box)
            .setPositiveButton("اتصال") { _, _ ->
                val h = host.text.toString().trim().trimEnd('/'); val u = user.text.toString().trim(); val p = pass.text.toString()
                if (h.isNotEmpty() && u.isNotEmpty() && p.isNotEmpty()) {
                    prefs.edit().putString("xt_host", h).putString("xt_user", u).putString("xt_pass", p).apply(); loadXtream(h, u, p)
                } else message("أكمل بيانات الاتصال")
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun requestHeaders(): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        prefs.getString("link_ua", "")?.takeIf { it.isNotBlank() }?.let { headers["User-Agent"] = it }
        prefs.getString("link_ref", "")?.takeIf { it.isNotBlank() }?.let { headers["Referer"] = it }
        prefs.getString("link_cookie", "")?.takeIf { it.isNotBlank() }?.let { headers["Cookie"] = it }
        prefs.getString("link_auth", "")?.takeIf { it.isNotBlank() }?.let {
            headers["Authorization"] = when {
                it.startsWith("Bearer ", true) || it.startsWith("Basic ", true) -> it
                else -> it
            }
        }
        return headers
    }

    private fun loadM3u(link: String) {
        setStatus("جارٍ تحميل القائمة…")
        scope.launch(Dispatchers.IO) {
            try {
                val conn = URL(link).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000; conn.readTimeout = 20000
                val headers = requestHeaders()
                if (!headers.containsKey("User-Agent")) conn.setRequestProperty("User-Agent", "103 IPTV Player")
                headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                val text = conn.inputStream.bufferedReader().use { it.readText() }; conn.disconnect()
                val parsed = parseM3u(text)
                withContext(Dispatchers.Main) {
                    items.clear(); items.addAll(parsed); prefs.edit().putString("last_m3u", link).apply()
                    setStatus("تم تحميل ${items.size} عنصر"); showLibrary()
                }
            } catch (e: Exception) { withContext(Dispatchers.Main) { setStatus("فشل تحميل القائمة"); message("تعذّر تحميل القائمة: ${e.message ?: "خطأ اتصال"}") } }
        }
    }

    private fun parseM3u(text: String): List<Channel> {
        val out = mutableListOf<Channel>(); var name = "قناة"; var group = "عام"; var logo = ""
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.startsWith("#EXTINF", true)) {
                name = line.substringAfterLast(',').trim().ifBlank { "قناة" }
                group = Regex("group-title=\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(line)?.groupValues?.get(1) ?: "عام"
                logo = Regex("tvg-logo=\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(line)?.groupValues?.get(1) ?: ""
            } else if (line.isNotEmpty() && !line.startsWith("#") && (line.startsWith("http://") || line.startsWith("https://"))) {
                out.add(Channel(name, line, group, logo)); name = "قناة"; group = "عام"; logo = ""
            }
        }
        return out
    }

    private fun loadXtream(host: String, username: String, password: String) {
        setStatus("جارٍ الاتصال بخادم Xtream…")
        scope.launch(Dispatchers.IO) {
            try {
                val api = "$host/player_api.php?username=${enc(username)}&password=${enc(password)}"
                val json = URL(api).readText(); JSONObject(json)
                val liveJson = URL("$api&action=get_live_streams").readText()
                val arr = org.json.JSONArray(liveJson); val result = mutableListOf<Channel>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("stream_id")
                    val nm = o.optString("name", "قناة $id")
                    val cat = o.optString("category_name", "عام")
                    val logo = o.optString("stream_icon", "")
                    result.add(Channel(nm, "$host/live/${enc(username)}/${enc(password)}/$id.ts", cat, logo, "live", id))
                }
                withContext(Dispatchers.Main) { items.clear(); items.addAll(result); setStatus("تم تحميل ${items.size} قناة"); showLibrary() }
            } catch (e: Exception) { withContext(Dispatchers.Main) { setStatus("فشل الاتصال"); message("تعذّر الاتصال بخادم Xtream: ${e.message ?: "تحقق من البيانات"}") } }
        }
    }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    private fun showSaved() {
        val options = listOf("تحميل آخر قائمة M3U", "الاتصال بحساب Xtream المحفوظ", "تشغيل آخر رابط مباشر", "رجوع")
        AlertDialog.Builder(this).setTitle("الروابط والحسابات المحفوظة").setItems(options.toTypedArray()) { _, which ->
            when (which) {
                0 -> prefs.getString("last_m3u", "")?.takeIf { it.isNotBlank() }?.let { loadM3u(it) } ?: message("لا توجد قائمة محفوظة")
                1 -> { val h = prefs.getString("xt_host", "") ?: ""; val u = prefs.getString("xt_user", "") ?: ""; val p = prefs.getString("xt_pass", "") ?: ""; if (h.isNotBlank() && u.isNotBlank() && p.isNotBlank()) loadXtream(h, u, p) else askXtream() }
                2 -> prefs.getString("last_direct_url", "")?.takeIf { it.isNotBlank() }?.let { playDirectFullscreen(it) } ?: askDirect()
                else -> buildHome()
            }
        }.show()
    }

    private fun showLibrary() {
        val root = baseColumn()
        root.addView(label("المكتبة", 23f, white, true))
        root.addView(label("${items.size} عنصر", 13f, muted)); addGap(root)
        val search = field("ابحث عن قناة أو مجموعة")
        root.addView(search)
        val scroll = ScrollView(this); val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        fun render(query: String = "") {
            list.removeAllViews()
            items.filter { it.name.contains(query, true) || it.group.contains(query, true) }.take(300).forEach { ch ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(14, 12, 14, 12); background = rounded(panel, 12f) }
                row.addView(label(ch.name, 16f, white, true)); row.addView(label("${ch.group}  •  ${ch.type}", 12f, muted))
                row.setOnClickListener { play(ch) }
                list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 })
            }
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { render(s?.toString() ?: "") }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        render()
        root.addView(button("الصفحة الرئيسية") { buildHome() }, LinearLayout.LayoutParams(-1, 50).apply { topMargin = 8 })
        setContentView(root)
    }

    private fun createPlayer(): ExoPlayer {
        val headers = requestHeaders()
        val dataSourceFactory = DefaultHttpDataSource.Factory().setUserAgent(headers["User-Agent"] ?: "103 IPTV Player").setAllowCrossProtocolRedirects(true)
        if (headers["Referer"] != null) dataSourceFactory.setDefaultRequestProperties(mapOf("Referer" to headers.getValue("Referer")))
        val all = headers.filterKeys { it != "User-Agent" && it != "Referer" }
        if (all.isNotEmpty()) dataSourceFactory.setDefaultRequestProperties(all)
        return ExoPlayer.Builder(this).setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory)).build()
    }

    private fun play(ch: Channel) {
        currentTitle = ch.name; currentUrl = ch.url
        try { player?.release() } catch (_: Exception) {}
        player = createPlayer()
        player?.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { setStatus("خطأ في التشغيل: ${error.errorCodeName}") }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) setStatus("يعمل الآن: $currentTitle")
                if (playbackState == Player.STATE_BUFFERING) setStatus("جارٍ الاتصال بالبث…")
            }
        })
        playerView = PlayerView(this).apply { this.player = this@MainActivity.player; useController = true; setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS); setBackgroundColor(Color.BLACK) }
        player?.setMediaItem(MediaItem.fromUri(ch.url)); player?.prepare(); player?.playWhenReady = true
        openFullscreen()
    }

    private fun openFullscreen() {
        val p = player ?: return
        val pv = playerView ?: return
        try { requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE } catch (_: Exception) {}
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(8, 4, 8, 4); setBackgroundColor(Color.rgb(10, 10, 10)) }
        val title = label(currentTitle, 14f, white, true)
        bar.addView(title, LinearLayout.LayoutParams(0, 48, 1f))
        val quality = button("الجودة") { showQualityMenu() }
        bar.addView(quality, LinearLayout.LayoutParams(-2, 44))
        val volume = button("الصوت") { showVolume() }
        bar.addView(volume, LinearLayout.LayoutParams(-2, 44).apply { leftMargin = 6 })
        val close = button("✕") { fullscreenDialog?.dismiss() }
        bar.addView(close, LinearLayout.LayoutParams(48, 44).apply { leftMargin = 6 })
        (pv.parent as? ViewGroup)?.removeView(pv)
        layout.addView(bar, LinearLayout.LayoutParams(-1, -2)); layout.addView(pv, LinearLayout.LayoutParams(-1, 0, 1f))
        fullscreenDialog?.dismiss()
        fullscreenDialog = AlertDialog.Builder(this).setView(layout).create().also { dialog ->
            dialog.window?.setBackgroundDrawableResource(android.R.color.black)
            dialog.setOnDismissListener {
                try { requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED } catch (_: Exception) {}
                buildHome()
            }
            dialog.show()
            dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            dialog.window?.decorView?.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        }
    }

    private fun showQualityMenu() {
        val p = player ?: return
        val options = arrayOf("تلقائي (أفضل جودة)", "منخفضة — حتى 480p", "متوسطة — حتى 720p", "عالية — حتى 1080p")
        AlertDialog.Builder(this).setTitle("اختيار جودة البث").setItems(options) { _, which ->
            val builder = p.trackSelectionParameters.buildUpon()
            when (which) {
                0 -> builder.setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                1 -> builder.setMaxVideoSize(854, 480)
                2 -> builder.setMaxVideoSize(1280, 720)
                3 -> builder.setMaxVideoSize(1920, 1080)
            }
            p.trackSelectionParameters = builder.build()
            setStatus("تم تغيير إعداد الجودة: ${options[which]}")
        }.show()
    }

    private fun showVolume() {
        val p = player ?: return
        val seek = SeekBar(this).apply { max = 100; progress = (p.volume * 100).toInt() }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 12, 24, 0); addView(label("مستوى الصوت", 15f, white)); addView(seek) }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) p.volume = progress / 100f }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        AlertDialog.Builder(this).setTitle("الصوت").setView(box).setPositiveButton("تم", null).show()
    }

    private fun setStatus(text: String) { statusView?.text = text }
    private fun message(text: String) { if (!isFinishing) AlertDialog.Builder(this).setMessage(text).setPositiveButton("حسنًا", null).show() }

    override fun onBackPressed() {
        if (fullscreenDialog?.isShowing == true) fullscreenDialog?.dismiss() else buildHome()
    }

    override fun onDestroy() {
        scope.cancel()
        fullscreenDialog?.dismiss()
        playerView?.player = null
        player?.release(); player = null
        super.onDestroy()
    }
}
