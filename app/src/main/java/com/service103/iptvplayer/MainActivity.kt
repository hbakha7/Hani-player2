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

private fun saveHomeLink(name: String, url: String, isM3u: Boolean) {
    val items = getSavedLinks()
    // Update an existing entry with the same URL instead of creating duplicates.
    val updated = JSONArray()
    var replaced = false
    for (i in 0 until items.length()) {
        val item = items.optJSONObject(i) ?: continue
        if (item.optString("url") == url) {
            if (!replaced) {
                updated.put(JSONObject().put("name", name).put("url", url).put("m3u", isM3u))
                replaced = true
            }
        } else {
            updated.put(item)
        }
    }
    if (!replaced) {
        updated.put(JSONObject().put("name", name).put("url", url).put("m3u", isM3u))
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
                    channels.add(Channel(name, url, "مباشر", type = "live"))
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
        saveHomeLink(name, link, m3uCheck.isChecked)
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
 
