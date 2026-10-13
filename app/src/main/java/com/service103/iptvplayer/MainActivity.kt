package com.service103.iptvplayer

import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
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
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Channel(val name: String, val url: String, val group: String = "Channels")

class MainActivity : AppCompatActivity() {
    private val bg = Color.rgb(4, 65, 72)
    private val blue = Color.rgb(75, 177, 232)
    private val white = Color.WHITE
    private val channels = mutableListOf<Channel>()
    private lateinit var root: LinearLayout
    private var playerView: PlayerView? = null
    private var player: ExoPlayer? = null
    private var status: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildHome()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float, color: Int = white, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

    private fun button(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label; isAllCaps = false; textSize = 16f
            setTextColor(Color.rgb(40, 139, 197))
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(-1, dp(54)).apply {
                setMargins(dp(18), dp(7), dp(18), dp(7))
            }
        }

    private fun buildHome() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(bg); setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(24), 0, dp(24))
        }
        content.addView(text("◉", 76f, blue, true), LinearLayout.LayoutParams(-1, dp(115)))
        content.addView(text("103 IPTV Player", 29f, white, true))
        content.addView(text("مشغل الوسائط والقنوات الخاصة بك", 17f))
        content.addView(text("أضف رابطًا تملك حق استخدامه لبدء المشاهدة", 13f, 0xFFB7DDE4.toInt()))
        content.addView(button("M3U / M3U8 URL") { askM3u() })
        content.addView(button("Xtream Codes") { askXtream() })
        content.addView(button("FG Code") { askFg() })
        content.addView(button("رابط فيديو مباشر") { askDirect() })
        status = text("جاهز لإضافة قائمة تشغيل", 14f, 0xFFB7DDE4.toInt())
        content.addView(status)
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun askM3u() {
        val box = EditText(this).apply { hint = "https://example.com/list.m3u"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI }
        dialog("إضافة قائمة M3U", box) {
            val link = box.text.toString().trim()
            if (link.isNotBlank()) loadM3u(link)
        }
    }

    
private fun askDirect() {
    val box = EditText(this).apply {
        hint = "https://example.com/video.m3u8"
        inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_URI
    }

    dialog("تشغيل رابط فيديو", box) {
        val link = box.text.toString().trim()

        if (link.isNotBlank()) {
            channels.clear()
            channels.add(Channel("Video", link, "Direct"))
            showChannels()
            play(channels.first())
        }
    }
}


    private fun askXtream() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8)) }
        val host = EditText(this).apply { hint = "Server URL (https://server:port)"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI }
        val user = EditText(this).apply { hint = "Username" }
        val pass = EditText(this).apply { hint = "Password"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        layout.addView(host); layout.addView(user); layout.addView(pass)
        AlertDialog.Builder(this).setTitle("Xtream Codes").setView(layout)
            .setNegativeButton("إلغاء", null).setPositiveButton("اتصال") { _, _ ->
                val h = host.text.toString().trim().trimEnd('/')
                val u = user.text.toString().trim()
                val p = pass.text.toString()
                if (h.isNotBlank() && u.isNotBlank() && p.isNotBlank()) loadXtream(h, u, p)
                else message("أدخل الخادم واسم المستخدم وكلمة المرور")
            }.show()
    }

    private fun askFg() {
        message("رمز FG ليس معيارًا عامًا. لا يمكن تفعيله إلا إذا زوّدك مقدم الخدمة بتوثيق أو API متوافق. استخدم M3U أو Xtream إذا كانا متاحين.")
    }

    private fun dialog(title: String, field: EditText, onOk: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setView(field)
            .setNegativeButton("إلغاء", null).setPositiveButton("متابعة") { _, _ -> onOk() }.show()
    }

    private fun loadM3u(link: String) {
        setStatus("جارٍ تحميل القائمة…")
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val conn = URL(link).openConnection() as HttpURLConnection
                    conn.connectTimeout = 15000; conn.readTimeout = 20000
                    conn.setRequestProperty("User-Agent", "103IPTVPlayer/1.0")
                    try { conn.inputStream.bufferedReader().use { it.readText() } }
                    finally { conn.disconnect() }
                }
                val parsed = parseM3u(result)
                if (parsed.isEmpty()) { setStatus("لم يتم العثور على روابط تشغيل صالحة"); return@launch }
                channels.clear(); channels.addAll(parsed)
                showChannels()
                setStatus("تم تحميل ${channels.size} قناة/رابط")
            } catch (e: Exception) {
                setStatus("تعذر تحميل القائمة: ${e.message ?: "تحقق من الرابط والاتصال"}")
            }
        }
    }

    private fun parseM3u(data: String): List<Channel> {
        val out = mutableListOf<Channel>()
        var name = "Channel"; var group = "Channels"
        data.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.startsWith("#EXTINF", true)) {
                name = line.substringAfter(",", "Channel").trim().ifBlank { "Channel" }
                group = Regex("group-title=[\\\"']([^\\\"']+)[\\\"']", RegexOption.IGNORE_CASE)
                    .find(line)?.groupValues?.get(1) ?: "Channels"
            } else if (line.isNotBlank() && !line.startsWith("#") &&
                (line.startsWith("http://") || line.startsWith("https://"))) {
                out.add(Channel(name, line, group)); name = "Channel"
            }
        }
        return out
    }

    private fun loadXtream(host: String, user: String, pass: String) {
        setStatus("جارٍ الاتصال بخادم Xtream…")
        lifecycleScope.launch {
            try {
                val safeU = URLEncoder.encode(user, "UTF-8")
                val safeP = URLEncoder.encode(pass, "UTF-8")
                val endpoint = "$host/player_api.php?username=$safeU&password=$safeP&action=get_live_streams"
                val data = withContext(Dispatchers.IO) {
                    val conn = URL(endpoint).openConnection() as HttpURLConnection
                    conn.connectTimeout = 15000; conn.readTimeout = 20000
                    try { conn.inputStream.bufferedReader().use { it.readText() } }
                    finally { conn.disconnect() }
                }
                val arr = JSONArray(data)
                val parsed = mutableListOf<Channel>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("stream_id")
                    val n = obj.optString("name", "Channel")
                    val ext = obj.optString("container_extension", "ts").ifBlank { "ts" }
                    val cat = obj.optString("category_name", "Live TV")
                    if (id.isNotBlank()) parsed.add(Channel(n, "$host/live/$safeU/$safeP/$id.$ext", cat))
                }
                if (parsed.isEmpty()) { setStatus("لم تظهر قنوات؛ تحقق من بيانات Xtream وصلاحيات الحساب"); return@launch }
                channels.clear(); channels.addAll(parsed); showChannels()
                setStatus("تم تحميل ${channels.size} قناة")
            } catch (e: Exception) {
                setStatus("فشل اتصال Xtream: ${e.message ?: "تحقق من البيانات"}")
            }
        }
    }

    private fun showChannels() {
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(10), dp(10), dp(10)) }
        content.addView(text("103 IPTV Player", 24f, white, true))
        content.addView(text("القنوات والمجموعات", 18f, blue, true))
        val groups = channels.map { it.group }.distinct()
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("كل المجموعات") + groups)
        content.addView(spinner)
        playerView = PlayerView(this).apply { useController = true; layoutParams = LinearLayout.LayoutParams(-1, dp(220)) }
        content.addView(playerView)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(list)
        val sc = ScrollView(this); sc.addView(content); setContentView(sc)
        fun refresh(group: String) {
            list.removeAllViews()
            channels.filter { group == "كل المجموعات" || it.group == group }.forEach { ch ->
                val b = Button(this).apply {
                    text = "${ch.name}\n${ch.group}"; isAllCaps = false
                    setOnClickListener { play(ch) }
                }
                list.addView(b, LinearLayout.LayoutParams(-1, dp(58)))
            }
        }
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                refresh(spinner.selectedItem.toString())
            }
        }
        refresh("كل المجموعات")
    }

    private fun play(ch: Channel) {
        try {
            if (player == null) {
                player = ExoPlayer.Builder(this).build()
                playerView?.player = player
                player?.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        message("تعذر تشغيل هذا الرابط. قد يحتاج إلى صلاحيات أو تنسيق غير مدعوم أو قد يكون منتهي الصلاحية.")
                    }
                })
            }
            player?.setMediaItem(MediaItem.fromUri(ch.url))
            player?.prepare(); player?.play()
            Toast.makeText(this, "تشغيل: ${ch.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { message("تعذر تشغيل الرابط: ${e.message}") }
    }

    private fun setStatus(s: String) { runOnUiThread { status?.text = s } }
    private fun message(s: String) = AlertDialog.Builder(this).setMessage(s).setPositiveButton("حسنًا", null).show()
    override fun onDestroy() { player?.release(); player = null; super.onDestroy() }
}
