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
import android.util.Base64
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
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
val root = LinearLayout(this).apply {
orientation = LinearLayout.VERTICAL
setBackgroundColor(Color.rgb(8, 20, 35))
}

val header = LinearLayout(this).apply {  
    orientation = LinearLayout.HORIZONTAL  
    gravity = Gravity.CENTER_VERTICAL  
    setPadding(dp(12), dp(8), dp(12), dp(8))  
    setBackgroundColor(Color.rgb(15, 42, 67))  
}  

val menu = TextView(this).apply {  
    text = "☰"  
    textSize = 28f  
    gravity = Gravity.CENTER  
    setTextColor(Color.WHITE)  
    setPadding(dp(8), dp(4), dp(16), dp(4))  
}  

val title = TextView(this).apply {  
    text = "103 IPTV Player"  
    textSize = 20f  
    gravity = Gravity.CENTER  
    setTextColor(Color.WHITE)  
}  

val search = TextView(this).apply {  
    text = "⌕"  
    textSize = 30f  
    gravity = Gravity.CENTER  
    setTextColor(Color.WHITE)  
    setPadding(dp(16), dp(4), dp(8), dp(4))  
}  

header.addView(menu)  
header.addView(  
    title,  
    LinearLayout.LayoutParams(0, dp(48), 1f)  
)  
header.addView(search)  
root.addView(header)  

val content = FrameLayout(this).apply {  
    setBackgroundColor(Color.rgb(8, 25, 42))  
}  

val empty = TextView(this).apply {  
    text = "103 IPTV PLAYER"  
    textSize = 24f  
    gravity = Gravity.CENTER  
    setTextColor(Color.rgb(25, 190, 165))  
}  

content.addView(  
    empty,  
    FrameLayout.LayoutParams(-1, -1)  
)  

root.addView(  
    content,  
    LinearLayout.LayoutParams(-1, 0, 1f)  
)  

val navigation = LinearLayout(this).apply {  
    orientation = LinearLayout.HORIZONTAL  
    gravity = Gravity.CENTER  
    setBackgroundColor(Color.rgb(12, 35, 55))  
}  

fun navItem(symbol: String, caption: String, action: () -> Unit) {  
    val item = LinearLayout(this).apply {  
        orientation = LinearLayout.VERTICAL  
        gravity = Gravity.CENTER  
        setPadding(0, dp(8), 0, dp(8))  
        isClickable = true  
        isFocusable = true  
        setOnClickListener { action() }  
    }  

    val icon = TextView(this).apply {  
        text = symbol  
        textSize = 25f  
        gravity = Gravity.CENTER  
        setTextColor(Color.WHITE)  
    }  

    val text = TextView(this).apply {  
        text = caption  
        textSize = 13f  
        gravity = Gravity.CENTER  
        setTextColor(Color.WHITE)  
    }  

    item.addView(icon)  
    item.addView(text)  

    navigation.addView(  
        item,  
        LinearLayout.LayoutParams(0, dp(66), 1f)  
    )  
}  

navItem("◉", "الرئيسية") {  
    empty.text = "103 IPTV PLAYER"  
}  

navItem("▶", "الوسائط المحلية") {  
    val intent = android.content.Intent(  
        android.content.Intent.ACTION_GET_CONTENT  
    ).apply {  
        type = "video/*"  
    }  

    try {  
        startActivityForResult(intent, 103)  
    } catch (_: Exception) {  
        message("تعذر فتح ملفات الفيديو")  
    }  
}  

navItem("+", "إضافة") {  
    showAddLinkForm()  
}  

root.addView(navigation)  
setContentView(root)  

menu.setOnClickListener {  
    AlertDialog.Builder(this)  
        .setTitle("103 IPTV Player")  
        .setItems(  
            arrayOf("الرئيسية", "إضافة رابط", "الروابط المحفوظة")  
        ) { _, which ->  
            when (which) {  
                0 -> empty.text = "103 IPTV PLAYER"  
                1 -> showAddLinkForm()  
                2 -> showSaved()  
            }  
        }  
        .show()  
}  

search.setOnClickListener {  
    val input = EditText(this).apply {  
        hint = "ابحث عن قناة أو محتوى"  
        setTextColor(Color.WHITE)  
        setHintTextColor(Color.LTGRAY)  
    }  

    AlertDialog.Builder(this)  
        .setTitle("بحث")  
        .setView(input)  
        .setPositiveButton("بحث") { _, _ ->  
            val query = input.text.toString().trim()  
            val found = channels.filter {  
                it.name.contains(query, ignoreCase = true)  
            }  

            if (found.isEmpty()) {  
                message("لا توجد نتائج مطابقة")  
            } else {  
                AlertDialog.Builder(this)  
                    .setTitle("نتائج البحث")  
                    .setItems(found.map { it.name }.toTypedArray()) { _, i ->  
                        play(found[i])  
                        openFullscreen()  
                    }  
                    .setNegativeButton("إغلاق", null)  
                    .show()  
            }  
        }  
        .setNegativeButton("إلغاء", null)  
        .show()  
}

}

private fun showAddLinkForm() {
val root = makeColumn()
val scroll = ScrollView(this)
val form = makeColumn()

form.addView(  
    label("إضافة رابط جديد", 22f, accent, true).apply {  
        gravity = Gravity.CENTER  
    }  
)  

fun inputField(hintText: String, uri: Boolean = false): EditText {  
    return EditText(this).apply {  
        hint = hintText  
        setTextColor(Color.WHITE)  
        setHintTextColor(Color.LTGRAY)  
        textSize = 16f  
        setSingleLine(true)  
        inputType = if (uri) {  
            InputType.TYPE_CLASS_TEXT or  
                InputType.TYPE_TEXT_VARIATION_URI  
        } else {  
            InputType.TYPE_CLASS_TEXT  
        }  
        setPadding(dp(14), dp(12), dp(14), dp(12))  
        background = rounded(Color.rgb(12, 30, 47), 5)  
    }  
}  

fun addField(field: EditText) {  
    form.addView(  
        field,  
        LinearLayout.LayoutParams(-1, dp(66)).apply {  
            setMargins(0, dp(5), 0, dp(14))  
        }  
    )  
}  

val nameInput = inputField("Name")  
val urlInput = inputField(  
    "Url Link (M3U,M3U8,TS,MP4...)",  
    true  
)  
val userAgentInput = inputField("User-Agent")  
val referrerInput = inputField("Referrer")  
val authInput = inputField("Authentication")  
val cookieInput = inputField("Cookie (name=joe;app=xpola;id=value ...)")  
val licenseInput = inputField("License")  

addField(nameInput)  
addField(urlInput)  
addField(userAgentInput)  
addField(referrerInput)  
addField(authInput)  
addField(cookieInput)  
addField(licenseInput)  

form.addView(label("Scheme", 17f, white))  

val scheme = Spinner(this).apply {  
    adapter = ArrayAdapter(  
        this@MainActivity,  
        android.R.layout.simple_spinner_dropdown_item,  
        listOf("الرابط كما هو", "HTTP", "HTTPS")  
    )  
    background = rounded(Color.rgb(12, 30, 47), 5)  
}  

form.addView(  
    scheme,  
    LinearLayout.LayoutParams(-1, dp(54)).apply {  
        setMargins(0, dp(5), 0, dp(12))  
    }  
)  

fun option(text: String, checked: Boolean = false) =  
    CheckBox(this).apply {  
        this.text = text  
        isChecked = checked  
        setTextColor(Color.WHITE)  
        textSize = 15f  
        buttonTintList =  
            android.content.res.ColorStateList.valueOf(  
                Color.rgb(190, 235, 240)  
            )  
    }  

val m3uCheck = option("is M3u Link ?")  
val browserCheck = option("WEB BROWSER")  
val exoCheck = option("EXO PLAYER", true)  
val webPlayerCheck = option("WEB PLAYER")  

val row1 = LinearLayout(this).apply {  
    orientation = LinearLayout.HORIZONTAL  
    gravity = Gravity.CENTER_VERTICAL  
    addView(  
        m3uCheck,  
        LinearLayout.LayoutParams(0, dp(48), 1f)  
    )  
    addView(  
        browserCheck,  
        LinearLayout.LayoutParams(0, dp(48), 1f)  
    )  
}  

val row2 = LinearLayout(this).apply {  
    orientation = LinearLayout.HORIZONTAL  
    gravity = Gravity.CENTER_VERTICAL  
    addView(  
        exoCheck,  
        LinearLayout.LayoutParams(0, dp(48), 1f)  
    )  
    addView(  
        webPlayerCheck,  
        LinearLayout.LayoutParams(0, dp(48), 1f)  
    )  
}  

exoCheck.setOnCheckedChangeListener { _, checked ->  
    if (checked) webPlayerCheck.isChecked = false  
}  

webPlayerCheck.setOnCheckedChangeListener { _, checked ->  
    if (checked) exoCheck.isChecked = false  
}  

form.addView(row1)  
form.addView(row2)  

statusView = label("جاهز", 14f, accent)  
form.addView(statusView)  

form.addView(button("تشغيل الرابط") {  
    val name = nameInput.text.toString().trim()  
        .ifBlank { "فيديو مباشر" }  

    var link = urlInput.text.toString().trim()  

    if (link.isBlank()) {  
        message("أدخل رابط التشغيل")  
        return@button  
    }  

    when (scheme.selectedItemPosition) {  
        1 -> if (!link.startsWith("http://") &&  
            !link.startsWith("https://")) {  
            link = "http://$link"  
        }  
        2 -> if (!link.startsWith("http://") &&  
            !link.startsWith("https://")) {  
            link = "https://$link"  
        }  
    }  

    if (!link.startsWith("http://") &&  
        !link.startsWith("https://")) {  
        message("الرابط غير صحيح")  
        return@button  
    }  

    if (browserCheck.isChecked) {  
        try {  
            startActivity(  
                android.content.Intent(  
                    android.content.Intent.ACTION_VIEW,  
                    android.net.Uri.parse(link)  
                )  
            )  
        } catch (_: Exception) {  
            message("تعذر فتح الرابط في المتصفح")  
        }  
        return@button  
    }  

    if (webPlayerCheck.isChecked) {  
        message("مشغل WEB PLAYER غير مفعّل حاليًا")  
        return@button  
    }  

    // حفظ حقول الرابط لإتاحتها عند فتح النموذج مرة أخرى  
    prefs.edit()  
        .putString("add_name", nameInput.text.toString())  
        .putString("add_url", link)  
        .putString("add_user_agent", userAgentInput.text.toString())  
        .putString("add_referrer", referrerInput.text.toString())  
        .putString("add_auth", authInput.text.toString())  
        .putString("add_cookie", cookieInput.text.toString())  
        .putString("add_license", licenseInput.text.toString())  
        .apply()  

    if (m3uCheck.isChecked) {  
        prefs.edit().putString("last_m3u", link).apply()  
        loadM3u(link)  
    } else {  
        channels.clear()  
        channels.add(  
            Channel(  
                name = name,  
                url = link,  
                group = "مباشر",  
                type = "live"  
            )  
        )  

        currentType = "live"  
        currentGroup = "الكل"  
        showLibrary()  

        playerView?.post {  
            channels.firstOrNull()?.let { channel ->  
                play(channel)  
                openFullscreen()  
            }  
        }  
    }  
})  

form.addView(button("رجوع للرئيسية") {  
    buildHome()  
})  

scroll.addView(form)  
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

playDirectFullscreen(link)

} else {  
            message("الرابط غير صحيح")  
        }  
    }  
    .show()

}

private fun playDirectFullscreen(link: String) {
    val channel = channels.firstOrNull { it.url == link }
        ?: Channel(
            name = "فيديو مباشر",
            url = link,
            group = "مباشر",
            type = "live"
        )

    currentType = "live"
    showLibrary()

    playerView?.post {
        play(channel)
        openFullscreen()
    }
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

private fun getPlaybackHeaders(): Map<String, String> {
val headers = mutableMapOf<String, String>()

prefs.getString("add_user_agent", "")  
    ?.trim()  
    ?.takeIf { it.isNotEmpty() }  
    ?.let { headers["User-Agent"] = it }  

prefs.getString("add_referrer", "")  
    ?.trim()  
    ?.takeIf { it.isNotEmpty() }  
    ?.let { headers["Referer"] = it }  

prefs.getString("add_cookie", "")  
    ?.trim()  
    ?.takeIf { it.isNotEmpty() }  
    ?.let { headers["Cookie"] = it }  

val auth = prefs.getString("add_auth", "")?.trim().orEmpty()  

if (auth.isNotEmpty()) {  
    headers["Authorization"] = when {  
        auth.startsWith("Basic ", true) ||  
        auth.startsWith("Bearer ", true) -> auth  

        auth.contains(":") -> {  
            val encoded = Base64.encodeToString(  
                auth.toByteArray(Charsets.UTF_8),  
                Base64.NO_WRAP  
            )  
            "Basic $encoded"  
        }  

        else -> auth  
    }  
}  

return headers

}

private fun createPlaybackPlayer(): ExoPlayer {
val httpFactory = DefaultHttpDataSource.Factory()
.setUserAgent(
prefs.getString("add_user_agent", "")
?.trim()
?.takeIf { it.isNotEmpty() }
?: "103IPTVPlayer/1.0"
)
.setDefaultRequestProperties(getPlaybackHeaders())
.setConnectTimeoutMs(15000)
.setReadTimeoutMs(20000)

return ExoPlayer.Builder(this)  
    .setMediaSourceFactory(  
        DefaultMediaSourceFactory(this)  
            .setDataSourceFactory(httpFactory)  
    )  
    .build()  
    .also { exo ->  
        exo.addListener(object : Player.Listener {  
            override fun onPlayerError(error: PlaybackException) {  
                message(  
                    "تعذر تشغيل المحتوى:\n${error.errorCodeName}"  
                )  
            }  
        })  
    }

}

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
player = createPlaybackPlayer()

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

        if (ch.type == "live" || ch.type == "episode") {
            playerView?.post {
                openFullscreen()
            }
        }
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
// إنشاء مشغّل جديد لضمان تطبيق رؤوس الطلبات الحالية
playerView?.player = null
player?.release()
player = createPlaybackPlayer()

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
if (fullscreenDialog?.isShowing == true) return

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
    player = this@MainActivity.player  
    resizeMode =  
        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT  
    keepScreenOn = true  
    setShutterBackgroundColor(Color.BLACK)  
}  

frame.addView(  
    fullPlayer,  
    FrameLayout.LayoutParams(-1, -1)  
)  

val controls = LinearLayout(this).apply {  
    orientation = LinearLayout.HORIZONTAL  
    gravity = Gravity.CENTER  
    setPadding(dp(6), dp(4), dp(6), dp(4))  
    background = rounded(Color.argb(160, 0, 0, 0), 24)  
    elevation = dp(8).toFloat()  
}  

fun controlButton(value: String): TextView {  
    return TextView(this).apply {  
        text = value  
        textSize = 13f  
        setTextColor(Color.WHITE)  
        gravity = Gravity.CENTER  
        setPadding(dp(10), dp(7), dp(10), dp(7))  
        background = rounded(Color.argb(100, 55, 55, 55), 20)  
        isClickable = true  
        isFocusable = true  
    }  
}  

val audio = getSystemService(AUDIO_SERVICE) as AudioManager  
val stream = AudioManager.STREAM_MUSIC  
val maxVolume = audio.getStreamMaxVolume(stream)  
var previousVolume =  
    audio.getStreamVolume(stream).coerceAtLeast(1)  

val volumeDown = controlButton("−")  
volumeDown.setOnClickListener {  
    audio.adjustStreamVolume(  
        stream,  
        AudioManager.ADJUST_LOWER,  
        0  
    )  
}  

val mute = controlButton("🔊")  
mute.setOnClickListener {  
    val current = audio.getStreamVolume(stream)  

    if (current > 0) {  
        previousVolume = current  
        audio.setStreamVolume(stream, 0, 0)  
        mute.text = "🔇"  
    } else {  
        audio.setStreamVolume(  
            stream,  
            previousVolume.coerceIn(  
                1, maxVolume.coerceAtLeast(1)  
            ),  
            0  
        )  
        mute.text = "🔊"  
    }  
}  

val volumeUp = controlButton("+")  
volumeUp.setOnClickListener {  
    audio.adjustStreamVolume(  
        stream,  
        AudioManager.ADJUST_RAISE,  
        0  
    )  
}  

val aspect = controlButton("FIT ▾")  
aspect.setOnClickListener {  
    val popup = PopupMenu(this, aspect)  

    listOf("FIT", "FULL", "ZOOM").forEach {  
        popup.menu.add(it)  
    }  

    popup.setOnMenuItemClickListener { item ->  
        when (item.title.toString()) {  
            "FIT" -> {  
                fullPlayer.resizeMode =  
                    androidx.media3.ui.AspectRatioFrameLayout  
                        .RESIZE_MODE_FIT  
            }  

            "FULL" -> {  
                fullPlayer.resizeMode =  
                    androidx.media3.ui.AspectRatioFrameLayout  
                        .RESIZE_MODE_FILL  
            }  

            "ZOOM" -> {  
                fullPlayer.resizeMode =  
                    androidx.media3.ui.AspectRatioFrameLayout  
                        .RESIZE_MODE_ZOOM  
            }  
        }  

        aspect.text = "${item.title} ▾"  
        true  
    }  

    popup.show()  
}  
val quality = controlButton("الجودة ▾")

quality.setOnClickListener {
    val popup = PopupMenu(this, quality)

    listOf(
        "تلقائي",
        "480p",
        "720p",
        "1080p"
    ).forEach {
        popup.menu.add(it)
    }

    popup.setOnMenuItemClickListener { item ->
        val selected = item.title.toString()

        val maxHeight = when (selected) {
            "480p" -> 480
            "720p" -> 720
            "1080p" -> 1080
            else -> Int.MAX_VALUE
        }

        val currentPlayer = player

        if (currentPlayer != null) {
            currentPlayer.trackSelectionParameters =
                currentPlayer.trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoSize(
                        Int.MAX_VALUE,
                        maxHeight
                    )
                    .build()

            quality.text = "$selected ▾"
        }

        true
    }

    popup.show()
}
val close = controlButton("✕")  
close.setOnClickListener {  
    dialog.dismiss()  
}  

listOf(
    volumeDown,
    mute,
    volumeUp,
    aspect,
    quality,
    close
).forEach { btn ->
    controls.addView(  
        btn,  
        LinearLayout.LayoutParams(  
            ViewGroup.LayoutParams.WRAP_CONTENT,  
            dp(40)  
        ).apply {  
            setMargins(dp(2), 0, dp(2), 0)  
        }  
    )  
}  

frame.addView(  
    controls,  
    FrameLayout.LayoutParams(  
        ViewGroup.LayoutParams.WRAP_CONTENT,  
        ViewGroup.LayoutParams.WRAP_CONTENT,  
        Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL  
    ).apply {  
        setMargins(dp(4), dp(4), dp(4), dp(18))  
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
requestedOrientation =  
    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE  

dialog.show()  
dialog.window?.setLayout(-1, -1)  
dialog.window?.addFlags(  
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

