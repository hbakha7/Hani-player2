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
                    as HttpUR
