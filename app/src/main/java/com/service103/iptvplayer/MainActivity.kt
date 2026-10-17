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
import androidx.media3.common.MimeTypes
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
import java.util.UUID
import android.util.Base64
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
data class Channel(
val name: String,
val url: String,
val group: String = "Ø¹Ø§Ù…",
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
private var currentGroup = "Ø§Ù„ÙƒÙ„"  
private var currentHost = ""  
private var currentUser = ""  
private var currentPass = ""  
private var fullscreenDialog: Dialog? = null  
private var directLinkPlayback = false
private var incomingServerNames = arrayListOf<String>()
private var incomingServerUrls = arrayListOf<String>()
private var incomingServerReferers = arrayListOf<String>()
private var incomingSelectedServer = 0
private var incomingChannelName = ""
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
        ?: "ÙÙŠØ¯ÙŠÙˆ Ù…Ø³ØªÙ„Ù…"

    incomingServerNames = incoming.getStringArrayListExtra("server_names") ?: arrayListOf()
    incomingServerUrls = incoming.getStringArrayListExtra("server_urls") ?: arrayListOf()
    incomingServerReferers = incoming.getStringArrayListExtra("server_referers") ?: arrayListOf()
    incomingSelectedServer = incoming.getIntExtra("selected_server_index", 0)
        .coerceIn(0, (incomingServerUrls.size - 1).coerceAtLeast(0))
    incomingChannelName = name
    val referer = incoming.getStringExtra("referer") ?: incoming.getStringExtra("referrer")

    // Keep the received URL in the saved library, then use this app's existing player.
    saveHomeLink(name, link, link.substringBefore("?").endsWith(".m3u", true) ||
        link.substringBefore("?").endsWith(".m3u8", true), referer.orEmpty())

    channels.removeAll { it.name == name && it.group == "Ù…Ø¨Ø§Ø´Ø±" }
    channels.add(Channel(name, link, "Ù…Ø¨Ø§Ø´Ø±", type = "live", referer = referer))
    window.decorView.post { playDirectFullscreen(link) }
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
        val name = item.optString("name", "Ø±Ø§Ø¨Ø· Ù…Ø­ÙÙˆØ¸").ifBlank { "Ø±Ø§Ø¨Ø· Ù…Ø­ÙÙˆØ¸" }
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
                    channels.add(Channel(name, url, "Ù…Ø¨Ø§Ø´Ø±", type = "live", referer = item.optString("referer", "")))
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
            text = "â–¶"
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
                .setItems(arrayOf("ØªØ´ØºÙŠÙ„", "Ø­Ø°Ù Ù…Ù† Ø§Ù„Ø±Ø¦ÙŠØ³ÙŠØ©")) { _, choice ->
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
    text = "â˜°"  
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
    text = "âŒ•"  
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

navItem("â—‰", "Ø§Ù„Ø±Ø¦ÙŠØ³ÙŠØ©") {
    renderSavedHomeLinks(content, empty)
}  

navItem("â–¶", "Ø§Ù„ÙˆØ³Ø§Ø¦Ø· Ø§Ù„Ù…Ø­Ù„ÙŠØ©") {  
    val intent = android.content.Intent(  
        android.content.Intent.ACTION_GET_CONTENT  
    ).apply {  
        type = "video/*"  
    }  

    try {  
        startActivityForResult(intent, 103)  
    } catch (_: Exception) {  
        message("ØªØ¹Ø°Ø± ÙØªØ­ Ù…Ù„ÙØ§Øª Ø§Ù„ÙÙŠØ¯ÙŠÙˆ")  
    }  
}  

navItem("+", "Ø¥Ø¶Ø§ÙØ©") {  
    showAddLinkForm()  
}  

root.addView(navigation)  
setContentView(root)  

menu.setOnClickListener {  
    AlertDialog.Builder(this)  
        .setTitle("103 IPTV Player")  
        .setItems(  
            arrayOf("Ø§Ù„Ø±Ø¦ÙŠØ³ÙŠØ©", "Ø¥Ø¶Ø§ÙØ© Ø±Ø§Ø¨Ø·", "Ø§Ù„Ø±ÙˆØ§Ø¨Ø· Ø§Ù„Ù…Ø­ÙÙˆØ¸Ø©")  
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
        hint = "Ø§Ø¨Ø­Ø« Ø¹Ù† Ù‚Ù†Ø§Ø© Ø£Ùˆ Ù…Ø­ØªÙˆÙ‰"  
        setTextColor(Color.WHITE)  
        setHintTextColor(Color.LTGRAY)  
    }  

    AlertDialog.Builder(this)  
        .setTitle("Ø¨Ø­Ø«")  
        .setView(input)  
        .setPositiveButton("Ø¨Ø­Ø«") { _, _ ->  
            val query = input.text.toString().trim()  
            val found = channels.filter {  
                it.name.contains(query, ignoreCase = true)  
            }  

            if (found.isEmpty()) {  
                message("Ù„Ø§ ØªÙˆØ¬Ø¯ Ù†ØªØ§Ø¦Ø¬ Ù…Ø·Ø§Ø¨Ù‚Ø©")  
            } else {  
                AlertDialog.Builder(this)  
                    .setTitle("Ù†ØªØ§Ø¦Ø¬ Ø§Ù„Ø¨Ø­Ø«")  
                    .setItems(found.map { it.name }.toTypedArray()) { _, i ->  
                        play(found[i])  
                        openFullscreen()  
                    }  
                    .setNegativeButton("Ø¥ØºÙ„Ø§Ù‚", null)  
                    .show()  
            }  
        }  
        .setNegativeButton("Ø¥Ù„ØºØ§Ø¡", null)  
        .show()  
}

}

private fun showAddLinkForm() {
val root = makeColumn()
val scroll = ScrollView(this)
val form = makeColumn()

form.addView(  
    label("Ø¥Ø¶Ø§ÙØ© Ø±Ø§Ø¨Ø· Ø¬Ø¯ÙŠØ¯", 22f, accent, true).apply {  
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

// Restore the last entered values so all headers stay together for the next playback.
nameInput.setText(prefs.getString("add_name", ""))
urlInput.setText(prefs.getString("add_url", ""))
userAgentInput.setText(prefs.getString("add_user_agent", ""))
referrerInput.setText(prefs.getString("add_referrer", ""))
authInput.setText(prefs.getString("add_auth", ""))
cookieInput.setText(prefs.getString("add_cookie", ""))
licenseInput.setText(prefs.getString("add_license", ""))

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
        listOf("Ø§Ù„Ø±Ø§Ø¨Ø· ÙƒÙ…Ø§ Ù‡Ùˆ", "HTTP", "HTTPS")  
    )  
    background = rounded(Color.rgb(12, 30, 47), 5)  
}  

form.addView(  
    scheme,  
    LinearLayout.LayoutParams(-1, dp(54)).apply {  
        setMargins(0, dp(5), 0, dp(12))  
    }  
)  

form.addView(label("DRM Scheme", 17f, white))
val drmChoices = listOf("Ø¨Ø¯ÙˆÙ† DRM", "clearkey", "widevine", "playready")
val drmScheme = Spinner(this).apply {
    adapter = ArrayAdapter(
        this@MainActivity,
        android.R.layout.simple_spinner_dropdown_item,
        drmChoices
    )
    background = rounded(Color.rgb(12, 30, 47), 5)
    setSelection(drmChoices.indexOf(prefs.getString("add_drm_scheme", "Ø¨Ø¯ÙˆÙ† DRM")).let { if (it >= 0) it else 0 })
}
form.addView(
    drmScheme,
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

statusView = label("Ø¬Ø§Ù‡Ø²", 14f, accent)  
form.addView(statusView)  

form.addView(button("ØªØ´ØºÙŠÙ„ Ø§Ù„Ø±Ø§Ø¨Ø·") {  
    val name = nameInput.text.toString().trim()
    if (name.isBlank()) {
        message("Ø£Ø¯Ø®Ù„ Ø§Ø³Ù…Ù‹Ø§ Ù„Ù„Ø±Ø§Ø¨Ø· Ø­ØªÙ‰ ÙŠØ¸Ù‡Ø± ÙÙŠ Ø§Ù„Ø±Ø¦ÙŠØ³ÙŠØ©")
        nameInput.requestFocus()
        return@button
    }

    var link = urlInput.text.toString().trim()  

    if (link.isBlank()) {  
        message("Ø£Ø¯Ø®Ù„ Ø±Ø§Ø¨Ø· Ø§Ù„ØªØ´ØºÙŠÙ„")  
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
        message("Ø§Ù„Ø±Ø§Ø¨Ø· ØºÙŠØ± ØµØ­ÙŠØ­")  
        return@button  
    }  

    // Save all request headers and DRM selection before any playback path is chosen.
    prefs.edit()
        .putString("add_name", name)
        .putString("add_url", link)
        .putString("add_user_agent", userAgentInput.text.toString().trim())
        .putString("add_referrer", referrerInput.text.toString().trim())
        .putString("add_auth", authInput.text.toString().trim())
        .putString("add_cookie", cookieInput.text.toString().trim())
        .putString("add_license", licenseInput.text.toString().trim())
        .putString("add_drm_scheme", drmScheme.selectedItem.toString())
        .apply()

    // Ø­ÙØ¸ Ø§Ù„Ø§Ø³Ù… ÙˆØ§Ù„Ø±Ø§Ø¨Ø· ÙÙŠ Ø§Ù„Ø±Ø¦ÙŠØ³ÙŠØ©ØŒ Ù…Ø¹ ØªØ­Ø¯ÙŠØ« Ø§Ù„Ø±Ø§Ø¨Ø· Ø¨Ø¯Ù„ ØªÙƒØ±Ø§Ø±Ù‡.
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
            message("ØªØ¹Ø°Ø± ÙØªØ­ Ø§Ù„Ø±Ø§Ø¨Ø· ÙÙŠ Ø§Ù„Ù…ØªØµÙØ­")  
        }  
        return@button  
    }  

    if (webPlayerCheck.isChecked) {  
        message("Ù…Ø´ØºÙ„ WEB PLAYER ØºÙŠØ± Ù…ÙØ¹Ù‘Ù„ Ø­Ø§Ù„ÙŠÙ‹Ø§")  
        return@button  
    }  

    // Ø­ÙØ¸ Ø­Ù‚ÙˆÙ„ Ø§Ù„Ø±Ø§Ø¨Ø· Ù„Ø¥ØªØ§Ø­ØªÙ‡Ø§ Ø¹Ù†Ø¯ ÙØªØ­ Ø§Ù„Ù†Ù…ÙˆØ°Ø¬ Ù…Ø±Ø© Ø£Ø®Ø±Ù‰  
    prefs.edit()  
        .putString("add_name", nameInput.text.toString())  
        .putString("add_url", link)  
        .putString("add_user_agent", userAgentInput.text.toString())  
        .putString("add_referrer", referrerInput.text.toString())  
        .putString("add_auth", authInput.text.toString())  
        .putString("add_cookie", cookieInput.text.toString())  
        .putString("add_license", licenseInput.text.toString())
        .putString("add_drm_scheme", drmScheme.selectedItem.toString())
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
                group = "Ù…Ø¨Ø§Ø´Ø±",  
                type = "live",  
                referer = referrerInput.text.toString().trim()  
            )  
        )  

        currentType = "live"
        currentGroup = "Ø§Ù„ÙƒÙ„"

        // ØªØ¬Ø§ÙˆØ² ØµÙØ­Ø© Ø§Ù„Ù…ÙƒØªØ¨Ø© ØªÙ…Ø§Ù…Ù‹Ø§: Ø§ÙØªØ­ Ø§Ù„Ù…Ø´ØºÙ„ Ù…Ø¨Ø§Ø´Ø±Ø© Ø¨Ø¹Ø¯ Ø§Ù„Ø¶ØºØ· Ø¹Ù„Ù‰ "ØªØ´ØºÙŠÙ„ Ø§Ù„Ø±Ø§Ø¨Ø·".
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

form.addView(button("Ø±Ø¬ÙˆØ¹ Ù„Ù„Ø±Ø¦ÙŠØ³ÙŠØ©") {  
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
        .setTitle("Ø¥Ø¶Ø§ÙØ© Ù‚Ø§Ø¦Ù…Ø© M3U")  
        .setView(input)  
        .setNegativeButton("Ø¥Ù„ØºØ§Ø¡", null)  
        .setPositiveButton("ØªØ­Ù…ÙŠÙ„") { _, _ ->  
            val link = input.text.toString().trim()  
            if (link.startsWith("http://") ||  
                link.startsWith("https://")) {  
                prefs.edit().putString("last_m3u", link).apply()  
                loadM3u(link)  
            } else {  
                message("Ø£Ø¯Ø®Ù„ Ø±Ø§Ø¨Ø·Ù‹Ø§ ØµØ­ÙŠØ­Ù‹Ø§ ÙŠØ¨Ø¯Ø£ Ø¨Ù€ http Ø£Ùˆ https")  
            }  
        }.show()  
}

private fun askDirect() {
val input = EditText(this).apply {
hint = "Ø±Ø§Ø¨Ø· ÙÙŠØ¯ÙŠÙˆ MP4 Ø£Ùˆ M3U8"
setTextColor(white)
setHintTextColor(muted)
inputType = InputType.TYPE_CLASS_TEXT or
InputType.TYPE_TEXT_VARIATION_URI
}

AlertDialog.Builder(this)  
    .setTitle("ØªØ´ØºÙŠÙ„ Ø±Ø§Ø¨Ø· Ù…Ø¨Ø§Ø´Ø±")  
    .setView(input)  
    .setNegativeButton("Ø¥Ù„ØºØ§Ø¡", null)  
    .setPositiveButton("ØªØ´ØºÙŠÙ„") { _, _ ->  
        val link = input.text.toString().trim()  

        if (link.startsWith("http://") ||  
            link.startsWith("https://")) {  

            channels.clear()  
            channels.add(  
                Channel("ÙÙŠØ¯ÙŠÙˆ Ù…Ø¨Ø§Ø´Ø±", link, "Ù…Ø¨Ø§Ø´Ø±")  
            )  

            currentType = "live"

playDirectFullscreen(link)

} else {  
            message("Ø§Ù„Ø±Ø§Ø¨Ø· ØºÙŠØ± ØµØ­ÙŠØ­")  
        }  
    }  
    .show()

}

private fun playDirectFullscreen(link: String) {
    val channel = channels.firstOrNull { it.url == link }
        ?: Channel(
            name = incomingChannelName.ifBlank { "ÙÙŠØ¯ÙŠÙˆ Ù…Ø¨Ø§Ø´Ø±" },
            url = link,
            group = "Ù…Ø¨Ø§Ø´Ø±",
            type = "live"
        )

    currentType = "live"
    directLinkPlayback = true

    // Create only a black player surface; do not open the library/home screen.
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

    directPlayerView.post {
        play(channel)
        openFullscreen()
    }
}

private fun askXtream() {  
    val layout = makeColumn()  

    val host = EditText(this).apply {  
        hint = "Server URL Ù…Ø«Ù„ http://server:8080"  
        setTextColor(white)  
        setHintTextColor(muted)  
        inputType = InputType.TYPE_CLASS_TEXT or  
            InputType.TYPE_TEXT_VARIATION_URI  
    }  

    val user = EditText(this).apply {  
        hint = "Ø§Ø³Ù… Ø§Ù„Ù…Ø³ØªØ®Ø¯Ù…"  
        setTextColor(white)  
        setHintTextColor(muted)  
    }  

    val pass = EditText(this).apply {  
        hint = "ÙƒÙ„Ù…Ø© Ø§Ù„Ù…Ø±ÙˆØ±"  
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
        .setTitle("Ø§ØªØµØ§Ù„ Xtream Codes")  
        .setView(layout)  
        .setNegativeButton("Ø¥Ù„ØºØ§Ø¡", null)  
        .setPositiveButton("Ø§ØªØµØ§Ù„ ÙˆØ­ÙØ¸") { _, _ ->  
            val h = host.text.toString().trim().trimEnd('/')  
            val u = user.text.toString().trim()  
            val p = pass.text.toString()  

            if (!(h.startsWith("http://") ||  
                h.startsWith("https://")) ||  
                u.isBlank() || p.isBlank()) {  
                message("ØªØ­Ù‚Ù‚ Ù…Ù† Ø§Ù„Ø®Ø§Ø¯Ù… ÙˆØ§Ø³Ù… Ø§Ù„Ù…Ø³ØªØ®Ø¯Ù… ÙˆÙƒÙ„Ù…Ø© Ø§Ù„Ù…Ø±ÙˆØ±")  
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
        "Ø¢Ø®Ø± Ù‚Ø§Ø¦Ù…Ø© M3U",  
        "Ø­Ø³Ø§Ø¨ Xtream Ø§Ù„Ù…Ø­ÙÙˆØ¸"  
    )  

    AlertDialog.Builder(this)  
        .setTitle("Ø§Ù„Ù…Ø­ÙÙˆØ¸Ø§Øª")  
        .setItems(options) { _, which ->  
            when (which) {  
                0 -> {  
                    val link = prefs.getString("last_m3u", "")  
                    if (!link.isNullOrBlank()) loadM3u(link)  
                    else message("Ù„Ø§ ØªÙˆØ¬Ø¯ Ù‚Ø§Ø¦Ù…Ø© Ù…Ø­ÙÙˆØ¸Ø©")  
                }  
                1 -> {  
                    val h = prefs.getString("xt_host", "") ?: ""  
                    val u = prefs.getString("xt_user", "") ?: ""  
                    val p = prefs.getString("xt_pass", "") ?: ""  
                    if (h.isBlank() || u.isBlank() || p.isBlank()) {  
                        message("Ù„Ø§ ÙŠÙˆØ¬Ø¯ Ø­Ø³Ø§Ø¨ Xtream Ù…Ø­ÙÙˆØ¸")  
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
    setStatus("Ø¬Ø§Ø±Ù ØªØ­Ù…ÙŠÙ„ Ø§Ù„Ù‚Ø§Ø¦Ù…Ø©...")  

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
                setStatus("Ù„Ù… Ù†Ø¬Ø¯ Ø±ÙˆØ§Ø¨Ø· ØµØ§Ù„Ø­Ø© ÙÙŠ Ø§Ù„Ù‚Ø§Ø¦Ù…Ø©")  
                return@launch  
            }  

            channels.clear()  
            channels.addAll(parsed)  
            currentType = "live"  
            showLibrary()  
            setStatus("ØªÙ… ØªØ­Ù…ÙŠÙ„ ${parsed.size} Ø±Ø§Ø¨Ø·Ù‹Ø§")  
        } catch (e: Exception) {  
            setStatus("ÙØ´Ù„ ØªØ­Ù…ÙŠÙ„ Ø§Ù„Ù‚Ø§Ø¦Ù…Ø©")  
            message("ØªØ¹Ø°Ø± ØªØ­Ù…ÙŠÙ„ Ø§Ù„Ù‚Ø§Ø¦Ù…Ø©:\n${e.message}")  
        }  
    }  
}  

private fun parseM3u(data: String): List<Channel> {  
    val out = mutableListOf<Channel>()  
    var name = "Ù‚Ù†Ø§Ø©"  
    var group = "Ø¹Ø§Ù…"  
    var logo = ""  

    data.lineSequence().forEach { raw ->  
        val line = raw.trim()  

        if (line.startsWith("#EXTINF", true)) {  
            name = line.substringAfter(",", "Ù‚Ù†Ø§Ø©")  
                .trim().ifBlank { "Ù‚Ù†Ø§Ø©" }  

            group = Regex(  
                """group-title=["']([^"']+)["']""",  
                RegexOption.IGNORE_CASE  
            ).find(line)?.groupValues?.get(1) ?: "Ø¹Ø§Ù…"  

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
            name = "Ù‚Ù†Ø§Ø©"  
            group = "Ø¹Ø§Ù…"  
            logo = ""  
        }  
    }  

    return out  
}  

private fun loadXtream(type: String) {  
    currentType = type  
    setStatus("Ø¬Ø§Ø±Ù Ø§Ù„Ø§ØªØµØ§Ù„ Ø¨Ø®Ø§Ø¯Ù… Xtream...")  

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

                val name = obj.optString("name", "Ù…Ø­ØªÙˆÙ‰")  
                val group = obj.optString(  
                    "category_name",  
                    if (type == "live") "Ù‚Ù†ÙˆØ§Øª Ù…Ø¨Ø§Ø´Ø±Ø©"  
                    else if (type == "movie") "Ø£ÙÙ„Ø§Ù…"  
                    else "Ù…Ø³Ù„Ø³Ù„Ø§Øª"  
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
                setStatus("Ù„Ù… ÙŠØ±Ø¬Ø¹ Ø§Ù„Ø®Ø§Ø¯Ù… Ø£ÙŠ Ø¹Ù†Ø§ØµØ±")  
                message(  
                    "Ù„Ù… ÙŠØªÙ… Ø§Ù„Ø¹Ø«ÙˆØ± Ø¹Ù„Ù‰ Ù…Ø­ØªÙˆÙ‰. ØªØ­Ù‚Ù‚ Ù…Ù† Ø¨ÙŠØ§Ù†Ø§Øª Ø§Ù„Ø­Ø³Ø§Ø¨ ÙˆØµÙ„Ø§Ø­ÙŠØ§ØªÙ‡."  
                )  
                return@launch  
            }  

            channels.clear()  
            channels.addAll(parsed)  
            showLibrary()  
            setStatus("ØªÙ… ØªØ­Ù…ÙŠÙ„ ${parsed.size} Ø¹Ù†ØµØ±Ù‹Ø§")  
        } catch (e: Exception) {  
            setStatus("ØªØ¹Ø°Ø± Ø§Ù„Ø§ØªØµØ§Ù„")  
            message(  
                "ÙØ´Ù„ Ø§ØªØµØ§Ù„ Xtream:\n" +  
                (e.message ?: "ØªØ­Ù‚Ù‚ Ù…Ù† Ø§Ù„Ø®Ø§Ø¯Ù… ÙˆØ§Ù„Ø¨ÙŠØ§Ù†Ø§Øª")  
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

(refererOverride?.trim()?.takeIf { it.isNotEmpty() }
    ?: prefs.getString("add_referrer", "").orEmpty().trim())
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
                    val causeDetails = generateSequence<Throwable>(error) { it.cause }
                        .take(6)
                        .joinToString("\n") { cause ->
                            "${cause.javaClass.simpleName}: ${cause.message ?: "Ø¨Ø¯ÙˆÙ† ØªÙØ§ØµÙŠÙ„"}"
                        }
                    val responseHeaders = details?.headerFields
                        ?.entries
                        ?.filter { it.key != null && it.key.equals("Content-Type", true) }
                        ?.joinToString { "${it.key}: ${it.value?.joinToString()}" }
                        .orEmpty()

                    val diagnostic = buildString {
                        append("ØªØ¹Ø°Ø± Ø§Ù„ØªØ´ØºÙŠÙ„: ${error.errorCodeName}")
                        if (status != null) append("\nHTTP: $status")
                        if (responseHeaders.isNotBlank()) append("\n$responseHeaders")
                        append("\n\nØªÙØ§ØµÙŠÙ„ Ø§Ù„Ø®Ø·Ø£:\n$causeDetails")
                        if (error.errorCodeName.contains("PARSING_CONTAINER", true)) {
                            append("\n\nÙ‡Ø°Ø§ Ø§Ù„Ø®Ø·Ø£ Ù„Ø§ ÙŠØ«Ø¨Øª Ø£Ù† Referer ÙˆØ­Ø¯Ù‡ Ù‡Ùˆ Ø§Ù„Ø³Ø¨Ø¨. Ù‚Ø¯ ØªÙƒÙˆÙ† Ø§Ø³ØªØ¬Ø§Ø¨Ø© Ø§Ù„Ø®Ø§Ø¯Ù… ØºÙŠØ± ÙÙŠØ¯ÙŠÙˆ Ø£Ùˆ Ø£Ù† ØµÙŠØºØ© Ø§Ù„Ø¨Ø« ØºÙŠØ± Ù…Ø¯Ø¹ÙˆÙ…Ø©.")
                        }
                    }
                    message(diagnostic)
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
        "Ù…Ø¨Ø§Ø´Ø±" to "live",  
        "Ø£ÙÙ„Ø§Ù…" to "movie",  
        "Ù…Ø³Ù„Ø³Ù„Ø§Øª" to "series"  
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

    content.addView(button("â›¶ Ù…Ù„Ø¡ Ø§Ù„Ø´Ø§Ø´Ø©") {  
        openFullscreen()  
    })  

    content.addView(button("Ø§Ù„ØµÙˆØª: Ø±ÙØ¹ / Ø®ÙØ¶") {  
        showVolume()  
    })  

    val groupLabel = label("Ø§Ù„Ù…Ø¬Ù…ÙˆØ¹Ø©", 14f, muted)  
    content.addView(groupLabel)  

    val groups = channels.map { it.group }.distinct()  
    val spinner = Spinner(this)  
    spinner.adapter = ArrayAdapter(  
        this,  
        android.R.layout.simple_spinner_dropdown_item,  
        listOf("Ø§Ù„ÙƒÙ„") + groups  
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
        currentGroup == "Ø§Ù„ÙƒÙ„" || it.group == currentGroup  
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
                "ØªØ¸Ù‡Ø± Ø£ÙˆÙ„ 200 Ù†ØªÙŠØ¬Ø© Ù„ØªÙ‚Ù„ÙŠÙ„ Ø§Ø³ØªÙ‡Ù„Ø§Ùƒ Ø§Ù„Ø°Ø§ÙƒØ±Ø©.",  
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
                        "title", "Ø­Ù„Ù‚Ø© ${i + 1}"  
                    )  
                    val url =  
                        "$currentHost/series/${enc(currentUser)}" +  
                        "/${enc(currentPass)}/$id.$ext"  
                    choices.add(  
                        Channel(name, url, "Ø§Ù„Ø­Ù„Ù‚Ø§Øª", "", "episode")  
                    )  
                }  
            }  

            if (choices.isEmpty()) {  
                message("Ù„Ù… ÙŠØ¹Ø«Ø± Ø§Ù„Ø®Ø§Ø¯Ù… Ø¹Ù„Ù‰ Ø­Ù„Ù‚Ø§Øª Ù„Ù‡Ø°Ø§ Ø§Ù„Ù…Ø³Ù„Ø³Ù„")  
                return@launch  
            }  

            AlertDialog.Builder(this@MainActivity)  
                .setTitle(series.name)  
                .setItems(  
                    choices.map { it.name }.toTypedArray()  
                ) { _, which ->  
                    play(choices[which])  
                }  
                .setNegativeButton("Ø¥ØºÙ„Ø§Ù‚", null)  
                .show()  
        } catch (e: Exception) {  
            message("ØªØ¹Ø°Ø± ØªØ­Ù…ÙŠÙ„ Ø§Ù„Ø­Ù„Ù‚Ø§Øª:\n${e.message}")  
        }  
    }  
}

private fun play(ch: Channel) {
try {
// Ø¥Ù†Ø´Ø§Ø¡ Ù…Ø´ØºÙ‘Ù„ Ø¬Ø¯ÙŠØ¯ Ù„Ø¶Ù…Ø§Ù† ØªØ·Ø¨ÙŠÙ‚ Ø±Ø¤ÙˆØ³ Ø§Ù„Ø·Ù„Ø¨Ø§Øª Ø§Ù„Ø­Ø§Ù„ÙŠØ©
playerView?.player = null
player?.release()
player = createPlaybackPlayer(ch.referer)

if (playerView == null) {  
        showLibrary()  
    }  

    playerView?.player = player  

    val itemBuilder = MediaItem.Builder()
        .setUri(ch.url)
        .setMediaId(ch.name)

    // Some providers expose an HLS stream behind an URL ending in index.html
    // or another non-standard path. In that case Media3 otherwise treats it
    // as a progressive file and throws UnrecognizedInputFormatException.
    // Only apply this fallback to the specific index.html-style endpoint;
    // normal .ts/.mp4/.m3u8 URLs keep their usual detection.
    val normalizedPath = try {
        android.net.Uri.parse(ch.url).path.orEmpty().lowercase()
    } catch (_: Exception) {
        ""
    }
    if (normalizedPath.endsWith("/index.html") || normalizedPath == "index.html") {
        itemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
    }

    val drmName = prefs.getString("add_drm_scheme", "Ø¨Ø¯ÙˆÙ† DRM").orEmpty().lowercase()
    val licenseUrl = prefs.getString("add_license", "").orEmpty().trim()
    if (drmName != "Ø¨Ø¯ÙˆÙ† drm" && drmName.isNotBlank()) {
        if (licenseUrl.isBlank()) {
            message("Ø§Ø®ØªØ±Øª DRM Ù„ÙƒÙ† Ø®Ø§Ù†Ø© License ÙØ§Ø±ØºØ©. Ø£Ø¯Ø®Ù„ Ø±Ø§Ø¨Ø· Ø®Ø§Ø¯Ù… Ø§Ù„ØªØ±Ø®ÙŠØµ.")
            return
        }
        val drmUuid: UUID = when (drmName) {
            "clearkey" -> C.CLEARKEY_UUID
            "widevine" -> C.WIDEVINE_UUID
            "playready" -> C.PLAYREADY_UUID
            else -> {
                message("Ù†ÙˆØ¹ DRM ØºÙŠØ± Ù…Ø¹Ø±ÙˆÙ")
                return
            }
        }
        val licenseHeaders = getPlaybackHeaders(ch.referer)
        itemBuilder.setDrmConfiguration(
            MediaItem.DrmConfiguration.Builder(drmUuid)
                .setLicenseUri(android.net.Uri.parse(licenseUrl))
                .setLicenseRequestHeaders(licenseHeaders)
                .build()
        )
    }
    val item = itemBuilder.build()

    player?.setMediaItem(item)  
    player?.prepare()  
    player?.playWhenReady = true  

    setStatus("Ù‚ÙŠØ¯ Ø§Ù„ØªØ´ØºÙŠÙ„: ${ch.name}")
    // Ø§ÙØªØ­ Ø§Ù„Ø¨Ø« Ø§Ù„Ù…Ø¨Ø§Ø´Ø± Ø¨Ø§Ù„Ø¹Ø±Ø¶ ÙˆÙ…Ù„Ø¡ Ø§Ù„Ø´Ø§Ø´Ø© Ù…Ø¨Ø§Ø´Ø±Ø©.
    if (ch.type == "live") {
        playerView?.post { openFullscreen() }
    }
} catch (e: Exception) {  
    message("ØªØ¹Ø°Ø± Ø¨Ø¯Ø¡ Ø§Ù„ØªØ´ØºÙŠÙ„:\n${e.message}")  
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
        // Make "Ù…Ù„Ø¡ Ø§Ù„ØµÙˆØ±Ø©" the default mode whenever fullscreen playback opens.
        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
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
    val back = textButton("â€¹", 36f).apply {
        contentDescription = "Ø±Ø¬ÙˆØ¹"
        setOnClickListener { dialog.dismiss() }
    }
    topBar.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
    topBar.addView(View(this), LinearLayout.LayoutParams(0, dp(48), 1f))
    frame.addView(topBar, FrameLayout.LayoutParams(-1, dp(48), Gravity.TOP))

    // Server circles overlay the video without a long dark bar behind them.
    var serverButtonsContainer: LinearLayout? = null
    if (incomingServerUrls.size > 1 && incomingServerNames.size == incomingServerUrls.size) {
        val serverBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 0)
            setBackgroundColor(Color.TRANSPARENT)
        }
        serverButtonsContainer = serverBar
        incomingServerNames.forEachIndexed { index, serverName ->
            val serverButton = TextView(this).apply {
                text = serverName.ifBlank { "S${index + 1}" }
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (index == incomingSelectedServer) Color.rgb(25, 190, 165) else Color.argb(135, 35, 35, 35))
                    setStroke(dp(1), if (index == incomingSelectedServer) Color.WHITE else Color.LTGRAY)
                }
                setOnClickListener {
                    if (index == incomingSelectedServer) return@setOnClickListener
                    incomingSelectedServer = index
                    val targetUrl = incomingServerUrls.getOrNull(index) ?: return@setOnClickListener
                    val targetReferer = incomingServerReferers.getOrNull(index).orEmpty()
                    val channel = Channel(incomingChannelName.ifBlank { "Ø¨Ø« Ù…Ø¨Ø§Ø´Ø±" }, targetUrl,
                        "Ù…Ø¨Ø§Ø´Ø±", type = "live", referer = targetReferer)
                    channels.removeAll { it.name == channel.name && it.group == "Ù…Ø¨Ø§Ø´Ø±" }
                    channels.add(channel)
                    play(channel)
                    fullPlayer.player = player
                    incomingServerNames.forEachIndexed { buttonIndex, _ ->
                        (serverBar.getChildAt(buttonIndex) as? TextView)?.background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(if (buttonIndex == incomingSelectedServer) Color.rgb(25, 190, 165) else Color.argb(135, 35, 35, 35))
                            setStroke(dp(1), if (buttonIndex == incomingSelectedServer) Color.WHITE else Color.LTGRAY)
                        }
                    }
                }
            }
            val params = LinearLayout.LayoutParams(dp(48), dp(48))
            params.marginEnd = dp(8)
            serverBar.addView(serverButton, params)
        }
        val serverParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.LEFT)
        serverParams.topMargin = dp(54)
        serverParams.leftMargin = dp(12)
        frame.addView(serverBar, serverParams)
    }

    var showControls: () -> Unit = {}

    val pauseButton = textButton("â…¡", 31f).apply {
        contentDescription = "ØªØ´ØºÙŠÙ„ Ø£Ùˆ Ø¥ÙŠÙ‚Ø§Ù Ù…Ø¤Ù‚Øª"
        setOnClickListener {
            val p = player ?: return@setOnClickListener
            if (p.isPlaying) p.pause() else p.play()
            text = if (p.isPlaying) "â…¡" else "â–¶"
            showControls()
        }
    }

    val rewindButton = textButton("â†¶\n10", 15f).apply {
        contentDescription = "Ø±Ø¬ÙˆØ¹ 10 Ø«ÙˆØ§Ù†Ù"
        setOnClickListener {
            val p = player ?: return@setOnClickListener
            p.seekTo((p.currentPosition - 10_000L).coerceAtLeast(0L))
            showControls()
        }
    }
    val forwardButton = textButton("â†·\n10", 15f).apply {
        contentDescription = "ØªÙ‚Ø¯ÙŠÙ… 10 Ø«ÙˆØ§Ù†Ù"
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

    val cast = textButton("â–£", 25f).apply {
        contentDescription = "Ø§Ù„Ø¨Ø« Ø¥Ù„Ù‰ Ø´Ø§Ø´Ø© Ø£Ø®Ø±Ù‰"
        setOnClickListener {
            message("Ù„Ø¥Ø¶Ø§ÙØ© Chromecast ÙŠÙ„Ø²Ù… Ø±Ø¨Ø· Ù…ÙƒØªØ¨Ø© Google Cast Ø¨Ø§Ù„ØªØ·Ø¨ÙŠÙ‚.")
            showControls()
        }
    }
    val audioTracks = textButton("â—‰", 24f).apply {
        contentDescription = "Ù…Ø³Ø§Ø±Ø§Øª Ø§Ù„ØµÙˆØª ÙˆØ§Ù„ØªØ±Ø¬Ù…Ø©"
        setOnClickListener {
            val p = player
            if (p == null) return@setOnClickListener
            val tracks = p.currentTracks
            val details = tracks.groups.joinToString("\n") { group ->
                val type = when (group.type) {
                    C.TRACK_TYPE_AUDIO -> "ØµÙˆØª"
                    C.TRACK_TYPE_TEXT -> "ØªØ±Ø¬Ù…Ø©"
                    C.TRACK_TYPE_VIDEO -> "ÙÙŠØ¯ÙŠÙˆ"
                    else -> "Ù…Ø³Ø§Ø±"
                }
                "$type: ${group.length} Ø®ÙŠØ§Ø±"
            }.ifBlank { "Ù„Ø§ ØªÙˆØ¬Ø¯ Ù…Ø³Ø§Ø±Ø§Øª Ø¥Ø¶Ø§ÙÙŠØ© Ù…ØªØ§Ø­Ø©." }
            message(details)
            showControls()
        }
    }
    val settings = textButton("âš™", 25f).apply {
        contentDescription = "Ø¥Ø¹Ø¯Ø§Ø¯Ø§Øª Ø§Ù„Ù…Ø´ØºÙ„"
        setOnClickListener {
            val popup = PopupMenu(this@MainActivity, this)
            listOf("Ù…Ù„Ø§Ø¡Ù…Ø© Ø§Ù„Ø´Ø§Ø´Ø©", "Ù…Ù„Ø¡ Ø§Ù„ØµÙˆØ±Ø©", "ØªÙƒØ¨ÙŠØ± Ø§Ù„ØµÙˆØ±Ø©", "Ù…Ø¹Ù„ÙˆÙ…Ø§Øª Ø§Ù„Ø¨Ø«").forEach { popup.menu.add(it) }
            popup.setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    "Ù…Ù„Ø§Ø¡Ù…Ø© Ø§Ù„Ø´Ø§Ø´Ø©" -> fullPlayer.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    "Ù…Ù„Ø¡ Ø§Ù„ØµÙˆØ±Ø©" -> fullPlayer.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                    "ØªÙƒØ¨ÙŠØ± Ø§Ù„ØµÙˆØ±Ø©" -> fullPlayer.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    "Ù…Ø¹Ù„ÙˆÙ…Ø§Øª Ø§Ù„Ø¨Ø«" -> message("${player?.currentMediaItem?.mediaId.orEmpty()}\n${player?.videoFormat?.width ?: 0} Ã— ${player?.videoFormat?.height ?: 0}")
                }
                true
            }
            popup.show()
            showControls()
        }
    }
    val fullscreen = textButton("â›¶", 27f).apply {
        contentDescription = "Ø¥ØºÙ„Ø§Ù‚ Ù…Ù„Ø¡ Ø§Ù„Ø´Ø§Ø´Ø©"
        setOnClickListener { dialog.dismiss() }
    }
    val display = textButton("â–­", 26f).apply {
        contentDescription = "ØªØºÙŠÙŠØ± Ù†Ù…Ø· Ø¹Ø±Ø¶ Ø§Ù„ÙÙŠØ¯ÙŠÙˆ"
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
        serverButtonsContainer?.visibility = View.VISIBLE
        handler.removeCallbacks(hideControls)
        handler.postDelayed(hideControls, 4500)
    }
    hideControls = Runnable {
        controlsVisible = false
        topBar.visibility = View.GONE
        bottomPanel.visibility = View.GONE
        serverButtonsContainer?.visibility = View.GONE
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
                    pauseButton.text = if (p.isPlaying) "â…¡" else "â–¶"
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
            // Returning from an external app launch should return to HANI TV,
            // not show the player's library page.
            finish()
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
    dialog.window?.let { fullscreenWindow ->
        fullscreenWindow.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
        fullscreenWindow.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Allow the video surface to extend into the landscape display cutout area.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            fullscreenWindow.attributes = fullscreenWindow.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            fullscreenWindow.setDecorFitsSystemWindows(false)
        }
    }
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
        .setTitle("Ù…Ø³ØªÙˆÙ‰ Ø§Ù„ØµÙˆØª")  
        .setView(seek)  
        .setPositiveButton("ØªÙ…", null)  
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
        .setPositiveButton("Ø­Ø³Ù†Ù‹Ø§", null)  
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
