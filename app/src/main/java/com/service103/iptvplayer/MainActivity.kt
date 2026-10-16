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
import android.os.Handler
import android.os.Looper
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
val id: String = "",
val referer: String? = null
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
private var directLinkPlayback = false
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
    handleExternalLink(intent)
}

override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleExternalLink(intent)
}

private fun handleExternalLink(incoming: android.content.Intent?) {
    if (incoming == null) return

    val uriLink = incoming.data?.toString().orEmpty()
    val sharedText = incoming.getStringExtra(android.content.Intent.EXTRA_TEXT).orEmpty()
    val extraUrl = incoming.getStringExtra("url").orEmpty()
        .ifBlank { incoming.getStringExtra("stream_url").orEmpty() }
    val link = sequenceOf(uriLink, extraUrl, sharedText)
        .map { it.trim() }
        .firstOrNull { it.startsWith("https://", true) || it.startsWith("http://", true) }
        ?: return

    val name = incoming.getStringExtra("name")
        ?: incoming.getStringExtra("channel_name")
        ?: "فيديو مستلم"

    // Keep the received URL in the saved library, then use this app's existing player.
    saveHomeLink(name, link, link.substringBefore("?").endsWith(".m3u", true) ||
        link.substringBefore("?").endsWith(".m3u8", true))

    window.decorView.post {
        playDirectFullscreen(link)
    }
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


private fun getSavedLinks(): JSONArray {
    return try {
        JSONArray(prefs.getString("saved_home_links", "[]") ?: "[]")
    } catch (_: Exception) {
        JSONArray()
    }
}

private fun saveHomeLink(name: String, url: String, isM3u: Boolean, referer: String = "") {
    val items = getSavedLinks()
    // Update an existing entry with the same URL instead of creating duplicates.
    val updated = JSONArray()
    var replaced = false
    for (i in 0 until items.length()) {
        val item = items.optJSONObject(i) ?: continue
        if (item.optString("url") == url) {
            if (!replaced) {
                updated.put(JSONObject().put("name", name).put("url", url).put("m3u", isM3u).put("referer", referer.trim()))
                replaced = true
            }
        } else {
            updated.put(item)
        }
    }
    if (!replaced) {
        updated.put(JSONObject().put("name", name).put("url", url).put("m3u", isM3u).put("referer", referer.trim()))
    }
    prefs.edit().putString("saved_home_links", updated.toString()).apply()
}

private fun renderSavedHomeLinks(content: FrameLayout, empty: TextView) {
    content.removeAllViews()
    val items = getSavedLinks()
    if (items.length() == 0) {
        empty.text = "103 IPTV PLAYER"
        content.addView(empty, FrameLayout.LayoutParams(-1, -1))
        return
    }

    val scroll = ScrollView(this)
    val list = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(12))
    }
    for (i in 0 until items.length()) {
        val item = items.optJSONObject(i) ?: continue
        val name = item.optString("name", "رابط محفوظ").ifBlank { "رابط محفوظ" }
        val url = item.optString("url", "")
        val isM3u = item.optBoolean("m3u", false)
        if (url.isBlank()) continue

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(Color.WHITE, 10)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (isM3u) {
                    loadM3u(url)
                } else {
                    channels.clear()
                    channels.add(Channel(name, url, "مباشر", type = "live", referer = item.optString("referer", "")))
                    currentType = "live"
                    directLinkPlayback = true
                    val directPlayerView = PlayerView(this@MainActivity).apply {
                        useController = false
                        setBackgroundColor(Color.BLACK)
                        keepScreenOn = true
                    }
                    playerView = directPlayerView
                    val directRoot = FrameLayout(this@MainActivity).apply {
                        setBackgroundColor(Color.BLACK)
                        addView(directPlayerView, FrameLayout.LayoutParams(-1, -1))
                    }
                    setContentView(directRoot)
                    directPlayerView.post { play(channels.first()) }
                }
            }
        }

        val playIcon = TextView(this).apply {
            text = "▶"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(85, 55, 15))
        }
        card.addView(playIcon, LinearLayout.LayoutParams(dp(48), dp(64)))

        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        details.addView(TextView(this@MainActivity).apply {
            text = name
            textSize = 16f
            setTextColor(Color.BLACK)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        details.addView(TextView(this@MainActivity).apply {
            text = url
            textSize = 11f
            setTextColor(Color.GRAY)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        })
        card.addView(details, LinearLayout.LayoutParams(0, dp(64), 1f))
        card.addView(TextView(this).apply {
            text = if (isM3u) "M3U" else url.substringAfterLast('.', "").uppercase().take(4).ifBlank { "TS" }
            textSize = 11f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(7), dp(5), dp(7), dp(5))
            background = rounded(Color.BLACK, 5)
        })
        list.addView(card, LinearLayout.LayoutParams(-1, dp(82)).apply {
            setMargins(0, 0, 0, dp(10))
        })
        card.setOnLongClickListener {
            AlertDialog.Builder(this@MainActivity)
                .setTitle(name)
                .setItems(arrayOf("تشغيل", "حذف من الرئيسية")) { _, choice ->
                    if (choice == 0) card.performClick() else {
                        val current = getSavedLinks()
                        val kept = JSONArray()
                        for (j in 0 until current.length()) {
                            val saved = current.optJSONObject(j) ?: continue
                            if (saved.optString("url") != url) kept.put(saved)
                        }
                        prefs.edit().putString("saved_home_links", kept.toString()).apply()
                        renderSavedHomeLinks(content, empty)
                    }
                }.show()
            true
        }
    }
    scroll.addView(list)
    content.addView(scroll, FrameLayout.LayoutParams(-1, -1))
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

renderSavedHomeLinks(content, empty)  

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
    renderSavedHomeLinks(content, empty)
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
                0 -> renderSavedHomeLinks(content, empty)  
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
    if (name.isBlank()) {
        message("أدخل اسمًا للرابط حتى يظهر في الرئيسية")
        nameInput.requestFocus()
        return@button
    }

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

    // حفظ الاسم والرابط في الرئيسية، مع تحديث الرابط بدل تكراره.
    if (!browserCheck.isChecked && !webPlayerCheck.isChecked) {
        saveHomeLink(name, link, m3uCheck.isChecked, referrerInput.text.toString())
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
                type = "live",  
                referer = referrerInput.text.toString().trim()  
            )  
        )  

        currentType = "live"
        currentGroup = "الكل"

        // تجاوز صفحة المكتبة تمامًا: افتح المشغل مباشرة بعد الضغط على "تشغيل الرابط".
        directLinkPlayback = true
        val directPlayerView = PlayerView(this).apply {
            useController = false
            setBackgroundColor(Color.BLACK)
            keepScreenOn = true
        }
        playerView = directPlayerView
        val directRoot = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(directPlayerView, FrameLayout.LayoutParams(-1, -1))
        }
        setContentView(directRoot)

        playerView?.post {
            channels.firstOrNull()?.let { channel ->
                play(channel)
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

private fun getPlaybackHeaders(refererOverride: String? = null): Map<String, String> {
val headers = mutableMapOf<String, String>()

prefs.getString("add_user_agent", "")  
    ?.trim()  
    ?.takeIf { it.isNotEmpty() }  
    ?.let { headers["User-Agent"] = it }  

(refererOverride ?: prefs.getString("add_referrer", "").orEmpty())
    .trim()
    .takeIf { it.isNotEmpty() }
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



private fun createPlaybackPlayer(refererOverride: String? = null): ExoPlayer {
    val headers = getPlaybackHeaders(refererOverride).toMutableMap()

    val userAgent = headers["User-Agent"]
        ?.takeIf { it.isNotBlank() }

    if (userAgent == null) {
        headers.remove("User-Agent")
    }

    // A channel-specific Referer takes precedence over the global field.
    val referrer = refererOverride?.trim().orEmpty()
    if (refererOverride != null) {
        headers.remove("Referer")
        if (referrer.isNotBlank()) headers["Referer"] = referrer
    }

    val httpFactory = DefaultHttpDataSource.Factory()
        .setDefaultRequestProperties(headers)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15000)
        .setReadTimeoutMs(20000)

    if (userAgent != null) {
        httpFactory.setUserAgent(userAgent)
    }

    return ExoPlayer.Builder(this)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(this)
                .setDataSourceFactory(httpFactory)
        )
        .build()
        .also { exo ->
            exo.addListener(object : Player.Listener {
                override fun onPlayerError(
                    error: PlaybackException
                ) {
                    val details = generateSequence(error.cause) {
                        it.cause
                    }.filterIsInstance<
                        androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
                    >().firstOrNull()

                    val status = details?.responseCode

                    message(
                        if (status != null) {
                            "خطأ HTTP: $status"
                        } else {
                            "تعذر التشغيل: ${error.errorCodeName}\n" +
                                (error.cause?.message
                                    ?: "لا توجد تفاصيل إضافية")
                        }
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
player = createPlaybackPlayer(ch.referer)

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
    // افتح البث المباشر بالعرض وملء الشاشة مباشرة.
    if (ch.type == "live") {
        playerView?.post { openFullscreen() }
    }
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

    val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    val frame = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

    val fullPlayer = PlayerView(this).apply {
        useController = false
        player = this@MainActivity.player
        // Fill the landscape screen similarly to the reference player.
        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        keepScreenOn = true
        setShutterBackgroundColor(Color.BLACK)
        setBackgroundColor(Color.BLACK)
    }
    frame.addView(fullPlayer, FrameLayout.LayoutParams(-1, -1))

    val handler = Handler(Looper.getMainLooper())
    var controlsVisible = true

    fun textButton(symbol: String, size: Float = 25f): TextView = TextView(this).apply {
        text = symbol
        textSize = size
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setPadding(dp(4), dp(2), dp(4), dp(2))
        setShadowLayer(3f, 1f, 1f, Color.BLACK)
        isClickable = true
        isFocusable = true
    }

    val topBar = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(2), dp(8), dp(2))
    }
    val back = textButton("‹", 36f).apply {
        contentDescription = "رجوع"
        setOnClickListener { dialog.dismiss() }
    }
    topBar.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
    topBar.addView(View(this), LinearLayout.LayoutParams(0, dp(48), 1f))
    frame.addView(topBar, FrameLayout.LayoutParams(-1, dp(48), Gravity.TOP))

    var showControls: () -> Unit = {}

    val pauseButton = textButton("Ⅱ", 31f).apply {
        contentDescription = "تشغيل أو إيقاف مؤقت"
        setOnClickListener {
            val p = player ?: return@setOnClickListener
            if (p.isPlaying) p.pause() else p.play()
            text = if (p.isPlaying) "Ⅱ" else "▶"
            showControls()
        }
    }

    val rewindButton = textButton("↶\n10", 15f).apply {
        contentDescription = "رجوع 10 ثوانٍ"
        setOnClickListener {
            val p = player ?: return@setOnClickListener
            p.seekTo((p.currentPosition - 10_000L).coerceAtLeast(0L))
            showControls()
        }
    }
    val forwardButton = textButton("↷\n10", 15f).apply {
        contentDescription = "تقديم 10 ثوانٍ"
        setOnClickListener {
            val p = player ?: return@setOnClickListener
            val d = p.duration
            val target = p.currentPosition + 10_000L
            p.seekTo(if (d > 0 && d != C.TIME_UNSET) target.coerceAtMost(d) else target)
            showControls()
        }
    }

    val bottomPanel = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(2), dp(12), dp(4))
        background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
            intArrayOf(Color.argb(190, 0, 0, 0), Color.TRANSPARENT)
        )
    }

    val actionBar = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }

    fun addControl(view: View, width: Int = 42) {
        actionBar.addView(view, LinearLayout.LayoutParams(dp(width), dp(46)))
    }

    val cast = textButton("▣", 25f).apply {
        contentDescription = "البث إلى شاشة أخرى"
        setOnClickListener {
            message("لإضافة Chromecast يلزم ربط مكتبة Google Cast بالتطبيق.")
            showControls()
        }
    }
    val audioTracks = textButton("◉", 24f).apply {
        contentDescription = "مسارات الصوت والترجمة"
        setOnClickListener {
            val p = player
            if (p == null) return@setOnClickListener
            val tracks = p.currentTracks
            val details = tracks.groups.joinToString("\n") { group ->
                val type = when (group.type) {
                    C.TRACK_TYPE_AUDIO -> "صوت"
                    C.TRACK_TYPE_TEXT -> "ترجمة"
                    C.TRACK_TYPE_VIDEO -> "فيديو"
                    else -> "مسار"
                }
                "$type: ${group.length} خيار"
            }.ifBlank { "لا توجد مسارات إضافية متاحة." }
            message(details)
            showControls()
        }
    }
    val settings = textButton("⚙", 25f).apply {
        contentDescription = "إعدادات المشغل"
        setOnClickListener {
            val popup = PopupMenu(this@MainActivity, this)
            listOf("ملاءمة الشاشة", "ملء الصورة", "تكبير الصورة", "معلومات البث").forEach { popup.menu.add(it) }
            popup.setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    "ملاءمة الشاشة" -> fullPlayer.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    "ملء الصورة" -> fullPlayer.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                    "تكبير الصورة" -> fullPlayer.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    "معلومات البث" -> message("${player?.currentMediaItem?.mediaId.orEmpty()}\n${player?.videoFormat?.width ?: 0} × ${player?.videoFormat?.height ?: 0}")
                }
                true
            }
            popup.show()
            showControls()
        }
    }
    val fullscreen = textButton("⛶", 27f).apply {
        contentDescription = "إغلاق ملء الشاشة"
        setOnClickListener { dialog.dismiss() }
    }
    val display = textButton("▭", 26f).apply {
        contentDescription = "تغيير نمط عرض الفيديو"
        setOnClickListener {
            fullPlayer.resizeMode = when (fullPlayer.resizeMode) {
                androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT ->
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL ->
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
            showControls()
        }
    }

    // One single control row, in the same order and near the bottom as the reference.
    addControl(rewindButton, 48)
    addControl(pauseButton, 48)
    addControl(forwardButton, 48)
    addControl(cast, 48)
    addControl(audioTracks, 48)
    addControl(settings, 48)
    addControl(fullscreen, 48)
    addControl(display, 48)
    bottomPanel.addView(actionBar)

    val timeRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    fun timeText(ms: Long): String {
        if (ms < 0 || ms == C.TIME_UNSET) return "00:00"
        val total = ms / 1000
        return String.format(java.util.Locale.US, "%02d:%02d", total / 60, total % 60)
    }
    val elapsed = TextView(this).apply {
        text = "00:00"
        textSize = 14f
        setTextColor(Color.LTGRAY)
        setTypeface(null, Typeface.BOLD)
    }
    val duration = TextView(this).apply {
        text = "00:00"
        textSize = 14f
        setTextColor(Color.LTGRAY)
        setTypeface(null, Typeface.BOLD)
        gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
    }
    val seek = SeekBar(this).apply {
        max = 1000
        progressTintList = android.content.res.ColorStateList.valueOf(Color.LTGRAY)
        thumbTintList = android.content.res.ColorStateList.valueOf(Color.LTGRAY)
        progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.argb(120, 255, 255, 255))
        setPadding(0, 0, 0, 0)
    }
    timeRow.addView(elapsed, LinearLayout.LayoutParams(dp(58), dp(28)))
    timeRow.addView(seek, LinearLayout.LayoutParams(0, dp(28), 1f))
    timeRow.addView(duration, LinearLayout.LayoutParams(dp(58), dp(28)))
    bottomPanel.addView(timeRow)
    frame.addView(bottomPanel, FrameLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))

    seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) {
                val d = player?.duration ?: C.TIME_UNSET
                if (d > 0 && d != C.TIME_UNSET) player?.seekTo(d * progress / 1000L)
            }
        }
        override fun onStartTrackingTouch(bar: SeekBar?) {}
        override fun onStopTrackingTouch(bar: SeekBar?) {}
    })

    lateinit var hideControls: Runnable
    showControls = {
        controlsVisible = true
        topBar.visibility = View.VISIBLE
        bottomPanel.visibility = View.VISIBLE
        handler.removeCallbacks(hideControls)
        handler.postDelayed(hideControls, 4500)
    }
    hideControls = Runnable {
        controlsVisible = false
        topBar.visibility = View.GONE
        bottomPanel.visibility = View.GONE
    }
    val updateProgress = object : Runnable {
        override fun run() {
            if (dialog.isShowing) {
                val p = player
                if (p != null) {
                    val d = p.duration
                    elapsed.text = timeText(p.currentPosition)
                    duration.text = timeText(d)
                    if (d > 0 && d != C.TIME_UNSET && !seek.isPressed) {
                        seek.progress = ((p.currentPosition * 1000L) / d).toInt().coerceIn(0, 1000)
                    } else if (d <= 0 || d == C.TIME_UNSET) {
                        seek.progress = 0
                    }
                    pauseButton.text = if (p.isPlaying) "Ⅱ" else "▶"
                }
                handler.postDelayed(this, 500)
            }
        }
    }

    frame.setOnClickListener { if (controlsVisible) hideControls.run() else showControls() }
    fullPlayer.setOnClickListener { if (controlsVisible) hideControls.run() else showControls() }
    dialog.setContentView(frame)
    dialog.setOnDismissListener {
        handler.removeCallbacks(updateProgress)
        handler.removeCallbacks(hideControls)
        fullPlayer.player = null
        (fullPlayer.parent as? ViewGroup)?.removeView(fullPlayer)
        if (directLinkPlayback) {
            directLinkPlayback = false
            original.player = null
            playerView = null
            requestedOrientation = oldOrientation
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            fullscreenDialog = null
            buildHome()
            return@setOnDismissListener
        }
        if (original.parent == null) parent.addView(original, oldIndex.coerceIn(0, parent.childCount), oldParams)
        original.player = player
        original.useController = true
        requestedOrientation = oldOrientation
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        fullscreenDialog = null
    }

    fullscreenDialog = dialog
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    dialog.show()
    dialog.window?.setLayout(-1, -1)
    dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    dialog.window?.decorView?.systemUiVisibility = (
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    )
    handler.post(updateProgress)
    handler.postDelayed(hideControls, 4500)
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
