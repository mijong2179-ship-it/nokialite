package kr.tntlabs.featurephone

import android.Manifest
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale
import kotlin.math.abs

// ── 실버 바디 팔레트 (2004 애니콜) ─────────────────────
private val BodyTop = Color(0xFFE6E8EC)
private val BodyBottom = Color(0xFFBDC1C7)
private val KeyTop = Color(0xFFF8F9FB)
private val KeyBottom = Color(0xFFD2D5DA)
private val KeyEdge = Color(0xFF9AA0A8)
private val Legend = Color(0xFF1F2227)
private val LegendDim = Color(0xFF6A7079)
private val Bezel = Color(0xFF232529)
private val OkBlueTop = Color(0xFF5A7FE0)
private val OkBlueBottom = Color(0xFF2B4BB0)
private val CallGreen = Color(0xFF1FA33A)
private val EndRed = Color(0xFFD6362B)
private val KeyShadow = Color(0xFF3A3E45)

enum class PhoneKey { SOFT_L, SOFT_R, CALL, END, UP, DOWN, LEFT, RIGHT, OK,
    D1, D2, D3, D4, D5, D6, D7, D8, D9, STAR, D0, HASH }

private val PhoneKey.digitChar: Char?
    get() = when (this) {
        PhoneKey.D0 -> '0'; PhoneKey.D1 -> '1'; PhoneKey.D2 -> '2'; PhoneKey.D3 -> '3'
        PhoneKey.D4 -> '4'; PhoneKey.D5 -> '5'; PhoneKey.D6 -> '6'; PhoneKey.D7 -> '7'
        PhoneKey.D8 -> '8'; PhoneKey.D9 -> '9'; PhoneKey.STAR -> '*'; PhoneKey.HASH -> '#'
        else -> null
    }

// 목록·게임 화면에서 숫자키를 방향키로 (2↑ 8↓ 4← 6→ 5확인)
private fun PhoneKey.asNav(): PhoneKey = when (this) {
    PhoneKey.D2 -> PhoneKey.UP; PhoneKey.D8 -> PhoneKey.DOWN
    PhoneKey.D4 -> PhoneKey.LEFT; PhoneKey.D6 -> PhoneKey.RIGHT
    PhoneKey.D5 -> PhoneKey.OK; else -> this
}

enum class BuiltinKind { CALC, SMS, SNAKE, CAMERA, YOUTUBE, SETTINGS, PHONE, CONTACTS, GALLERY }

sealed class MenuEntry(val label: String) {
    class App(label: String, val component: ComponentName, val icon: ImageBitmap?) : MenuEntry(label)
    class Builtin(label: String, val kind: BuiltinKind) : MenuEntry(label)
    class SetHome : MenuEntry("기본 홈으로 설정")
    class HomeSettings : MenuEntry("기본 홈 원복 (홈 설정)")
    class SystemSettings : MenuEntry("시스템 설정")
}

enum class Mode { IDLE, MENU, SETTINGS, CALC, SMS_LIST, SMS_THREAD, SMS_COMPOSE, SNAKE, CAMERA, YOUTUBE }

class MainActivity : ComponentActivity() {

    private var toneGen: ToneGenerator? = null
    private var pendingDial: String? = null
    var onSmsPermission: ((Boolean) -> Unit)? = null

    private val callPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val num = pendingDial; pendingDial = null
        if (num != null) placeCall(num, allowDirect = granted)
    }

    private val smsPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> onSmsPermission?.invoke(result[Manifest.permission.READ_SMS] == true) }

    var onCameraPermission: ((Boolean) -> Unit)? = null
    private val cameraPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onCameraPermission?.invoke(it) }
    fun requestCameraPermission() = cameraPermLauncher.launch(Manifest.permission.CAMERA)

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insets = WindowInsetsControllerCompat(window, window.decorView)
        insets.hide(WindowInsetsCompat.Type.systemBars())
        insets.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        toneGen = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 85) }.getOrNull()
        setContent { PhoneScreen() }
    }

    override fun onDestroy() {
        toneGen?.release(); toneGen = null
        super.onDestroy()
    }

    fun playDtmf(c: Char) {
        val tone = when (c) {
            in '0'..'9' -> c - '0'
            '*' -> ToneGenerator.TONE_DTMF_S
            '#' -> ToneGenerator.TONE_DTMF_P
            else -> return
        }
        toneGen?.startTone(tone, 140)
    }

    fun playClick() { toneGen?.startTone(ToneGenerator.TONE_DTMF_1, 25) }

    fun tick() {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        v.vibrate(VibrationEffect.createOneShot(12, 80))
    }

    fun hasPermission(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    fun requestSmsPermissions() = smsPermLauncher.launch(
        arrayOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS)
    )

    fun requestCall(num: String) {
        if (num.isBlank()) return
        if (hasPermission(Manifest.permission.CALL_PHONE)) placeCall(num, allowDirect = true)
        else { pendingDial = num; callPermLauncher.launch(Manifest.permission.CALL_PHONE) }
    }

    private fun placeCall(num: String, allowDirect: Boolean) {
        val action = if (allowDirect) Intent.ACTION_CALL else Intent.ACTION_DIAL
        runCatching { startActivity(Intent(action, Uri.parse("tel:$num"))) }
            .onFailure { runCatching { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num"))) } }
    }

    fun openCallLog() {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW).setType("vnd.android.cursor.dir/calls")) }
    }

    fun launchApp(component: ComponentName) {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            )
        }
    }

    fun requestHomeRole() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
            roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
        } else openHomeSettings()
    }

    fun openHomeSettings() { runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } }
    fun openSystemSettings() { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) } }
    fun openGallery() {
        runCatching {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW).setType("image/*").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    fun openContacts() {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("content://contacts/people/"))) }
    }
}

// ── 루트 ──────────────────────────────────────────────
@Composable
fun PhoneScreen() {
    val activity = LocalContext.current as MainActivity
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(Mode.IDLE) }
    var digits by remember { mutableStateOf("") }
    var selIndex by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }   // 비-상태 클래스(계산기/멀티탭) 갱신용

    val calc = remember { CalcState() }
    val snake = remember { SnakeGame() }
    val web = remember { WebHolder() }
    val camera = remember { CameraHolder() }
    var cameraGranted by remember { mutableStateOf(activity.hasPermission(Manifest.permission.CAMERA)) }

    var convs by remember { mutableStateOf(emptyList<Conversation>()) }
    var threadMsgs by remember { mutableStateOf(emptyList<SmsMsg>()) }
    var threadName by remember { mutableStateOf("") }
    var threadAddr by remember { mutableStateOf("") }
    var threadScroll by remember { mutableIntStateOf(0) }
    var composeTo by remember { mutableStateOf("") }
    var editingTo by remember { mutableStateOf(true) }
    val composeBody = remember { MultitapState() }
    var composeStatus by remember { mutableStateOf<String?>(null) }

    val apps by produceState(emptyList<MenuEntry>()) {
        value = withContext(Dispatchers.IO) {
            val pm = activity.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val collator = Collator.getInstance(Locale.KOREAN)
            pm.queryIntentActivities(intent, 0)
                .filter { it.activityInfo.packageName != activity.packageName }
                .map {
                    MenuEntry.App(
                        it.loadLabel(pm).toString(),
                        ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                        runCatching { it.loadIcon(pm).toBitmap(96, 96).asImageBitmap() }.getOrNull()
                    )
                }
                .sortedWith(compareBy(collator) { it.label })
        }
    }
    val builtins = remember {
        listOf(
            MenuEntry.Builtin("문자메시지", BuiltinKind.SMS),
            MenuEntry.Builtin("카메라", BuiltinKind.CAMERA),
            MenuEntry.Builtin("유튜브", BuiltinKind.YOUTUBE),
            MenuEntry.Builtin("계산기", BuiltinKind.CALC),
            MenuEntry.Builtin("스네이크", BuiltinKind.SNAKE),
            MenuEntry.Builtin("설정", BuiltinKind.SETTINGS),
            MenuEntry.Builtin("전화", BuiltinKind.PHONE),
            MenuEntry.Builtin("연락처", BuiltinKind.CONTACTS),
            MenuEntry.Builtin("갤러리", BuiltinKind.GALLERY)
        )
    }
    val settingsEntries = remember {
        listOf(MenuEntry.SetHome(), MenuEntry.HomeSettings(), MenuEntry.SystemSettings())
    }
    val entries = if (mode == Mode.SETTINGS) settingsEntries else builtins + apps

    fun loadConversations() {
        scope.launch {
            convs = withContext(Dispatchers.IO) {
                runCatching { SmsRepo.conversations(activity) }.getOrDefault(emptyList())
            }
        }
    }

    fun openSms() {
        selIndex = 0; mode = Mode.SMS_LIST
        if (activity.hasPermission(Manifest.permission.READ_SMS)) loadConversations()
        else {
            activity.onSmsPermission = { if (it) loadConversations() }
            activity.requestSmsPermissions()
        }
    }

    fun openThread(c: Conversation) {
        threadName = c.name; threadAddr = c.address; threadScroll = 0
        mode = Mode.SMS_THREAD
        scope.launch {
            threadMsgs = withContext(Dispatchers.IO) {
                runCatching { SmsRepo.thread(activity, c.address) }.getOrDefault(emptyList())
            }
        }
    }

    fun openCompose(to: String) {
        composeTo = to; editingTo = to.isEmpty()
        composeBody.setText(""); composeStatus = null
        mode = Mode.SMS_COMPOSE
    }

    fun sendSms() {
        composeBody.commit()
        if (composeTo.isBlank() || composeBody.text.isBlank()) {
            composeStatus = "번호와 내용을 입력하십시오"; return
        }
        if (!activity.hasPermission(Manifest.permission.SEND_SMS)) {
            activity.onSmsPermission = { }
            activity.requestSmsPermissions(); composeStatus = "권한 허용 후 다시 전송"; return
        }
        scope.launch {
            val ok = withContext(Dispatchers.IO) { SmsRepo.send(activity, composeTo, composeBody.text) }
            composeStatus = if (ok) "전송되었습니다" else "전송 실패"
            if (ok) { composeBody.setText(""); tick++ }
        }
    }

    fun activate(entry: MenuEntry) {
        when (entry) {
            is MenuEntry.App -> { activity.launchApp(entry.component); mode = Mode.IDLE }
            is MenuEntry.Builtin -> when (entry.kind) {
                BuiltinKind.CALC -> { calc.clear(); mode = Mode.CALC }
                BuiltinKind.SMS -> openSms()
                BuiltinKind.SNAKE -> { snake.reset(); mode = Mode.SNAKE }
                BuiltinKind.YOUTUBE -> mode = Mode.YOUTUBE
                BuiltinKind.SETTINGS -> { selIndex = 0; mode = Mode.SETTINGS }
                BuiltinKind.PHONE -> { activity.openCallLog(); mode = Mode.IDLE }
                BuiltinKind.CONTACTS -> { activity.openContacts(); mode = Mode.IDLE }
                BuiltinKind.GALLERY -> { activity.openGallery(); mode = Mode.IDLE }
                BuiltinKind.CAMERA -> {
                    mode = Mode.CAMERA
                    if (!cameraGranted) {
                        activity.onCameraPermission = { cameraGranted = it }
                        activity.requestCameraPermission()
                    }
                }
            }
            is MenuEntry.SetHome -> { activity.requestHomeRole(); mode = Mode.IDLE }
            is MenuEntry.HomeSettings -> { activity.openHomeSettings(); mode = Mode.IDLE }
            is MenuEntry.SystemSettings -> { activity.openSystemSettings(); mode = Mode.IDLE }
        }
    }

    fun navList(key: PhoneKey, size: Int, onOk: () -> Unit) {
        when (key) {
            PhoneKey.UP, PhoneKey.LEFT -> selIndex = (selIndex - 1).coerceAtLeast(0)
            PhoneKey.DOWN, PhoneKey.RIGHT -> selIndex = (selIndex + 1).coerceAtMost((size - 1).coerceAtLeast(0))
            PhoneKey.OK -> onOk()
            else -> {}
        }
    }

    fun onKey(raw: PhoneKey) {
        activity.tick()
        tick++
        val c = raw.digitChar
        val navMode = mode == Mode.MENU || mode == Mode.SETTINGS || mode == Mode.SMS_LIST ||
            mode == Mode.SNAKE || mode == Mode.SMS_THREAD || mode == Mode.YOUTUBE || mode == Mode.CAMERA
        val key = if (navMode) raw.asNav() else raw

        if (c != null && key == raw) {
            when (mode) {
                Mode.IDLE -> { digits += c; activity.playDtmf(c) }
                Mode.CALC -> {
                    activity.playClick()
                    when (c) { '*' -> calc.digit('.'); '#' -> calc.equals(); else -> calc.digit(c) }
                }
                Mode.SMS_COMPOSE -> {
                    activity.playClick()
                    if (editingTo) { if (c.isDigit()) composeTo += c }
                    else when (c) {
                        '#' -> if (composeBody.mode == InputMode.EN) composeBody.toggleCase() else composeBody.doubleConsonant()
                        '*' -> composeBody.toggleMode()
                        else -> composeBody.press(c)
                    }
                }
                else -> activity.playClick()
            }
            return
        }
        activity.playClick()

        if (key == PhoneKey.END) {
            digits = ""; mode = Mode.IDLE; snake.running = false; return
        }

        when (mode) {
            Mode.IDLE -> when (key) {
                PhoneKey.CALL ->
                    if (digits.isNotEmpty()) { activity.requestCall(digits); digits = "" } else activity.openCallLog()
                PhoneKey.SOFT_L ->
                    if (digits.isNotEmpty()) { activity.requestCall(digits); digits = "" } else { selIndex = 0; mode = Mode.MENU }
                PhoneKey.SOFT_R ->
                    if (digits.isNotEmpty()) digits = digits.dropLast(1) else { selIndex = 0; mode = Mode.SETTINGS }
                PhoneKey.OK -> if (digits.isEmpty()) { selIndex = 0; mode = Mode.MENU }
                else -> {}
            }
            Mode.MENU -> when (key) {
                PhoneKey.SOFT_R -> mode = Mode.IDLE
                PhoneKey.OK, PhoneKey.CALL, PhoneKey.SOFT_L -> entries.getOrNull(selIndex)?.let(::activate)
                PhoneKey.LEFT -> selIndex = (selIndex - 1).coerceAtLeast(0)
                PhoneKey.RIGHT -> selIndex = (selIndex + 1).coerceAtMost(entries.lastIndex)
                PhoneKey.UP -> if (selIndex - 3 >= 0) selIndex -= 3
                PhoneKey.DOWN -> if (selIndex + 3 <= entries.lastIndex) selIndex += 3
                else -> {}
            }
            Mode.SETTINGS -> when (key) {
                PhoneKey.SOFT_R -> mode = Mode.IDLE
                PhoneKey.CALL, PhoneKey.SOFT_L -> entries.getOrNull(selIndex)?.let(::activate)
                else -> navList(key, entries.size) { entries.getOrNull(selIndex)?.let(::activate) }
            }
            Mode.CALC -> when (key) {
                PhoneKey.UP -> calc.op("+"); PhoneKey.DOWN -> calc.op("-")
                PhoneKey.LEFT -> calc.op("×"); PhoneKey.RIGHT -> calc.op("÷")
                PhoneKey.OK, PhoneKey.SOFT_L, PhoneKey.CALL -> calc.equals()
                PhoneKey.SOFT_R ->
                    if (calc.expression.isEmpty() && calc.result == null) mode = Mode.MENU else calc.backspace()
                else -> {}
            }
            Mode.SMS_LIST -> when (key) {
                PhoneKey.SOFT_R -> mode = Mode.MENU
                PhoneKey.SOFT_L -> openCompose("")
                PhoneKey.CALL -> convs.getOrNull(selIndex)?.let { activity.requestCall(it.address) }
                else -> navList(key, convs.size) { convs.getOrNull(selIndex)?.let(::openThread) }
            }
            Mode.SMS_THREAD -> when (key) {
                PhoneKey.SOFT_R -> mode = Mode.SMS_LIST
                PhoneKey.SOFT_L, PhoneKey.OK -> openCompose(threadAddr)
                PhoneKey.CALL -> activity.requestCall(threadAddr)
                PhoneKey.UP -> threadScroll--
                PhoneKey.DOWN -> threadScroll++
                else -> {}
            }
            Mode.SMS_COMPOSE -> when (key) {
                PhoneKey.SOFT_R -> when {
                    editingTo && composeTo.isNotEmpty() -> composeTo = composeTo.dropLast(1)
                    editingTo -> mode = Mode.SMS_LIST
                    composeBody.text.isNotEmpty() -> composeBody.backspace()
                    else -> if (threadAddr.isNotEmpty() && composeTo == threadAddr) mode = Mode.SMS_THREAD
                    else editingTo = true
                }
                PhoneKey.OK, PhoneKey.SOFT_L, PhoneKey.CALL ->
                    if (editingTo) { if (composeTo.isNotEmpty()) editingTo = false }
                    else if (key == PhoneKey.OK) composeBody.commit() else sendSms()
                PhoneKey.UP -> editingTo = true
                PhoneKey.DOWN -> if (composeTo.isNotEmpty()) editingTo = false
                PhoneKey.LEFT -> if (!editingTo) composeBody.backspace()
                PhoneKey.RIGHT -> if (!editingTo) composeBody.space()
                else -> {}
            }
            Mode.YOUTUBE -> when (key) {
                PhoneKey.UP -> web.scroll(-320); PhoneKey.DOWN -> web.scroll(320)
                PhoneKey.LEFT -> if (!web.back()) mode = Mode.MENU
                PhoneKey.SOFT_L -> web.home()
                PhoneKey.SOFT_R -> if (!web.back()) mode = Mode.MENU
                else -> {}
            }
            Mode.CAMERA -> when (key) {
                PhoneKey.OK, PhoneKey.SOFT_L, PhoneKey.CALL -> camera.shoot(activity)
                PhoneKey.LEFT, PhoneKey.RIGHT -> camera.front = !camera.front
                PhoneKey.SOFT_R -> mode = Mode.MENU
                else -> {}
            }
            Mode.SNAKE -> when (key) {
                PhoneKey.UP -> snake.turn(0, -1); PhoneKey.DOWN -> snake.turn(0, 1)
                PhoneKey.LEFT -> snake.turn(-1, 0); PhoneKey.RIGHT -> snake.turn(1, 0)
                PhoneKey.OK, PhoneKey.SOFT_L -> snake.toggle()
                PhoneKey.SOFT_R -> { snake.running = false; mode = Mode.MENU }
                else -> {}
            }
        }
    }

    BackHandler(enabled = mode != Mode.IDLE || digits.isNotEmpty()) { onKey(PhoneKey.END) }

    Column(Modifier.fillMaxSize().brushedMetal()) {
        Spacer(Modifier.height(8.5.dp))
        Row(
            Modifier.fillMaxWidth(0.88f).align(Alignment.CenterHorizontally),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            BlackBand()
            Earpiece()
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SideDeco(antennaRight = true)
                SideDeco(antennaRight = true)
            }
        }
        Spacer(Modifier.height(16.dp))
        // 넓은 베젤의 스크린 프레임
        Box(
            Modifier.fillMaxWidth(0.88f).weight(1f).align(Alignment.CenterHorizontally)
                .shadow(6.dp, RoundedCornerShape(14.dp), clip = false, ambientColor = KeyShadow, spotColor = KeyShadow)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFF4F5F7), Color(0xFF8E939B))))
                .padding(3.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Bezel)
                .padding(horizontal = 22.dp, vertical = 26.dp)
                .clip(RoundedCornerShape(3.dp))
        ) {
            when (mode) {
                Mode.IDLE -> LcdPhoto { if (digits.isEmpty()) LcdIdle() else LcdDial(digits) }
                Mode.MENU -> LcdPanel { LcdGrid(entries, selIndex, { selIndex = it }, ::activate) }
                Mode.SETTINGS -> LcdPanel { LcdMenu("설정", entries, selIndex, { selIndex = it }, ::activate) }
                Mode.CALC -> LcdPanel { LcdCalc(calc, tick) }
                Mode.SMS_LIST -> LcdPanel { LcdSmsList(convs, selIndex, { selIndex = it }) { convs.getOrNull(it)?.let(::openThread) } }
                Mode.SMS_THREAD -> LcdPanel { LcdSmsThread(threadName, threadMsgs, threadScroll) }
                Mode.SMS_COMPOSE -> LcdPanel { LcdSmsCompose(composeTo, editingTo, composeBody, tick, composeStatus) }
                Mode.SNAKE -> LcdPanel { LcdSnake(snake) }
                Mode.YOUTUBE -> LcdPanel { LcdYouTube(web) }
                Mode.CAMERA -> LcdPanel { LcdCamera(camera, cameraGranted) }
            }
        }
        Spacer(Modifier.height(18.dp))
        Keypad(onKey = ::onKey, modifier = Modifier.fillMaxWidth(0.88f).weight(1.12f).align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(19.5.dp))
    }
}

// ── 바디: 사선 광택 브러시드 실버 ─────────────────────
private fun Modifier.brushedMetal(): Modifier = drawBehind {
    drawRect(
        Brush.linearGradient(
            0f to Color(0xFFF6F7F9), 0.22f to Color(0xFFC3C7CD), 0.40f to Color(0xFFEDEFF2),
            0.58f to Color(0xFFAEB3BA), 0.78f to Color(0xFFDADDE2), 1f to Color(0xFF9DA2AA),
            start = Offset(0f, 0f), end = Offset(size.width, size.height * 0.7f)
        )
    )
    var y = 0f
    var k = 0
    while (y < size.height) {
        val a = if (k % 2 == 0) 0.05f else 0.035f
        drawLine(if (k % 2 == 0) Color.White.copy(alpha = a) else Color.Black.copy(alpha = a), Offset(0f, y), Offset(size.width, y), 1f)
        y += 3f; k++
    }
}

// 수신부 좌우 장식: 같은 크기 크롬 캡슐 + 세로 리브 + 안테나 베이스
@Composable
private fun SideDeco(antennaRight: Boolean) {
    Box(
        Modifier.width(52.dp).height(35.5.dp)
            .shadow(2.dp, RoundedCornerShape(18.dp), clip = false)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF8F949C), Color(0xFFD8DBE0))))
            .padding(2.dp).clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFE9EBEE), Color(0xFFB3B8BF)))),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (antennaRight) AntennaBase()
            repeat(3) {
                Box(
                    Modifier.width(3.dp).height(17.dp).clip(RoundedCornerShape(1.5.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF4E535B), Color(0xFF9DA2AA))))
                )
            }
            if (!antennaRight) AntennaBase()
        }
    }
}

// 좌측: 같은 형태의 초록 적외선 수신창
@Composable
private fun BlackBand() {
    Box(
        Modifier.width(110.dp).height(35.5.dp)
            .shadow(2.dp, RoundedCornerShape(18.dp), clip = false)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF8F949C), Color(0xFFD8DBE0))))
            .padding(2.dp).clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF2E6B45), Color(0xFF0B2A19), Color(0xFF14432A))))
    ) {
        Box(
            Modifier.fillMaxWidth().height(10.dp).padding(horizontal = 10.dp, vertical = 3.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.0f), Color(0xFFB9FFD1).copy(alpha = 0.35f), Color.White.copy(alpha = 0.0f))))
        )
        Box(
            Modifier.align(Alignment.Center).width(8.dp).height(8.dp).clip(RoundedCornerShape(4.dp))
                .background(Brush.radialGradient(listOf(Color(0xFF7CFFA8), Color(0xFF1E7A48), Color(0xFF0B2A19))))
        )
    }
}

@Composable
private fun AntennaBase() {
    Box(
        Modifier.width(18.dp).height(18.dp).clip(RoundedCornerShape(9.dp))
            .background(Brush.radialGradient(listOf(Color(0xFF6A7079), Color(0xFF1F2227)))),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.width(7.dp).height(7.dp).clip(RoundedCornerShape(3.5.dp)).background(Brush.radialGradient(listOf(Color(0xFFF2F3F5), Color(0xFF9DA2AA)))))
    }
}

@Composable
private fun Earpiece() {
    Box(
        Modifier.width(110.dp).height(35.5.dp)
            .shadow(2.dp, RoundedCornerShape(26.dp), clip = false)
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF8F949C), Color(0xFFD8DBE0))))
            .padding(2.dp).clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFE9EBEE), Color(0xFFB3B8BF)))),
        contentAlignment = Alignment.Center
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            repeat(3) { Box(Modifier.width(18.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF2E3136))) }
        }
    }
}

// ── 키패드 (천지인 각인 크롬 키) ──────────────────────
@Composable
fun Keypad(onKey: (PhoneKey) -> Unit, modifier: Modifier) {
    val plate = RoundedCornerShape(26.dp)
    Column(
        modifier
            .shadow(3.dp, plate, clip = false, ambientColor = KeyShadow, spotColor = KeyShadow)
            .clip(plate)
            .background(Brush.verticalGradient(listOf(Color(0xFFB0B5BC), Color(0xFF8D929A))))
            .border(1.dp, Brush.verticalGradient(listOf(Color(0xFF5F646C), Color(0xFFD9DCE1))), plate)
            .padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(Modifier.fillMaxWidth().weight(1.75f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LabelKey("MENU", Modifier.fillMaxWidth().weight(0.8f)) { onKey(PhoneKey.SOFT_L) }
                HandsetKey(Modifier.fillMaxWidth().weight(1f), CallGreen, isEnd = false) { onKey(PhoneKey.CALL) }
            }
            DPad(Modifier.weight(1.3f).fillMaxHeight(), onKey)
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LabelKey("CANCEL", Modifier.fillMaxWidth().weight(0.8f)) { onKey(PhoneKey.SOFT_R) }
                HandsetKey(Modifier.fillMaxWidth().weight(1f), EndRed, isEnd = true) { onKey(PhoneKey.END) }
            }
        }
        val rows = listOf(
            listOf(Triple("1", "ㅣ", PhoneKey.D1), Triple("2", "ㆍ", PhoneKey.D2), Triple("3", "ㅡ", PhoneKey.D3)),
            listOf(Triple("4", "ㄱㅋ", PhoneKey.D4), Triple("5", "ㄴㄹ", PhoneKey.D5), Triple("6", "ㄷㅌ", PhoneKey.D6)),
            listOf(Triple("7", "ㅂㅍ", PhoneKey.D7), Triple("8", "ㅅㅎ", PhoneKey.D8), Triple("9", "ㅈㅊ", PhoneKey.D9)),
            listOf(Triple("*", "획추가", PhoneKey.STAR), Triple("0", "ㅇㅁ", PhoneKey.D0), Triple("#", "쌍자음", PhoneKey.HASH))
        )
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { (d, l, k) -> DigitKey(d, l, Modifier.weight(1f).fillMaxHeight()) { onKey(k) } }
            }
        }
    }
}

private val KeyShape = RoundedCornerShape(20.dp)

private fun keyBody(pressed: Boolean): Modifier = Modifier
    .clip(KeyShape)
    .drawBehind {
        val w = size.width; val h = size.height
        // 바탕 금속
        drawRect(
            Brush.verticalGradient(
                if (pressed) listOf(Color(0xFFC3C7CD), Color(0xFFB0B4BB), Color(0xFF9EA3AA))
                else listOf(Color(0xFFFFFFFF), Color(0xFFE6E8EC), Color(0xFFC2C6CC))
            )
        )
        // 상단 볼록 광택
        drawRect(
            Brush.radialGradient(
                listOf(Color.White.copy(alpha = if (pressed) 0.35f else 0.85f), Color.White.copy(alpha = 0f)),
                center = Offset(w / 2f, h * 0.05f), radius = w * 0.75f
            )
        )
        // 하단 홈(그루브)
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, 0.78f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.22f))
        )
    }
    .border(1.dp, Brush.verticalGradient(listOf(Color(0xFFFAFBFC), Color(0xFF8A8F97), Color(0xFF4E535B))), KeyShape)

@Composable
private fun PressableKey(modifier: Modifier, onPress: () -> Unit, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "keyScale")
    Box(
        modifier
            .scale(scale)
            .shadow(if (pressed) 0.dp else 2.dp, KeyShape, clip = false, ambientColor = KeyShadow, spotColor = KeyShadow)
            .then(keyBody(pressed))
            .clickable(interactionSource = interaction, indication = null, onClick = onPress),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun LabelKey(label: String, modifier: Modifier, onPress: () -> Unit) {
    PressableKey(modifier, onPress) {
        Text(label, color = Legend, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

private const val CALL_PATH = "M6.62 10.79c1.44 2.83 3.76 5.14 6.59 6.59l2.2-2.2c.27-.27.67-.36 1.02-.24 1.12.37 2.33.57 3.57.57.55 0 1 .45 1 1V20c0 .55-.45 1-1 1-9.39 0-17-7.61-17-17 0-.55.45-1 1-1h3.5c.55 0 1 .45 1 1 0 1.25.2 2.45.57 3.57.11.35.03.74-.25 1.02l-2.2 2.2z"
private const val CALL_END_PATH = "M12 9c-1.6 0-3.15.25-4.6.72v3.1c0 .39-.23.74-.56.9-.98.49-1.87 1.12-2.66 1.85-.18.18-.43.28-.7.28-.28 0-.53-.11-.71-.29L.29 13.08c-.18-.17-.29-.42-.29-.7 0-.28.11-.53.29-.71C3.34 9.78 7.46 8 12 8s8.66 1.78 11.71 4.67c.18.18.29.43.29.71 0 .28-.11.53-.29.71l-2.48 2.48c-.18.18-.43.29-.71.29-.27 0-.52-.11-.7-.28-.79-.74-1.68-1.36-2.66-1.85-.33-.16-.56-.5-.56-.9v-3.1C15.15 9.25 13.6 9 12 9z"

@Composable
private fun HandsetKey(modifier: Modifier, color: Color, isEnd: Boolean, onPress: () -> Unit) {
    val path = remember(isEnd) {
        PathParser().parsePathString(if (isEnd) CALL_END_PATH else CALL_PATH).toPath()
    }
    PressableKey(modifier, onPress) {
        Canvas(Modifier.width(28.dp).height(28.dp)) {
            val s = size.width / 24f
            scale(s, s, pivot = Offset.Zero) { drawPath(path, color) }
        }
    }
}

@Composable
private fun DigitKey(digit: String, hangul: String, modifier: Modifier, onPress: () -> Unit) {
    PressableKey(modifier, onPress) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(digit, color = Legend, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(7.dp))
            Text(hangul, color = LegendDim, fontSize = if (hangul.length > 2) 9.sp else 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun DPad(modifier: Modifier, onKey: (PhoneKey) -> Unit) {
    Box(
        modifier
            .shadow(3.dp, RoundedCornerShape(26.dp), clip = false, ambientColor = KeyShadow, spotColor = KeyShadow)
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFFDFDFE), Color(0xFFDDE0E4), Color(0xFFB6BAC1))))
            .border(1.dp, Brush.verticalGradient(listOf(Color(0xFFC4C8CE), Color(0xFF61666E))), RoundedCornerShape(26.dp))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val cx = offset.x / size.width
                    val cy = offset.y / size.height
                    val key = if (cx in 0.32f..0.68f && cy in 0.32f..0.68f) PhoneKey.OK
                    else {
                        val dx = cx - 0.5f; val dy = cy - 0.5f
                        if (abs(dx) > abs(dy)) { if (dx < 0) PhoneKey.LEFT else PhoneKey.RIGHT }
                        else { if (dy < 0) PhoneKey.UP else PhoneKey.DOWN }
                    }
                    onKey(key)
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            // 안쪽 링 (오목한 홈)
            val ring = Size(w * 0.78f, h * 0.78f)
            val rtl = Offset((w - ring.width) / 2, (h - ring.height) / 2)
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF9DA2AA), Color(0xFFE3E6EA)), startY = rtl.y, endY = rtl.y + ring.height),
                topLeft = rtl, size = ring, cornerRadius = CornerRadius(40f, 40f))
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFD6D9DE), Color(0xFFF2F3F5)), startY = rtl.y, endY = rtl.y + ring.height),
                topLeft = rtl + Offset(4f, 4f), size = Size(ring.width - 8f, ring.height - 8f), cornerRadius = CornerRadius(36f, 36f))
            val tri = 12f
            fun arrow(cx: Float, cy: Float, dx: Float, dy: Float) {
                val p = Path().apply {
                    moveTo(cx + dx * tri, cy + dy * tri)
                    lineTo(cx - dy * tri * 0.8f, cy + dx * tri * 0.8f)
                    lineTo(cx + dy * tri * 0.8f, cy - dx * tri * 0.8f)
                    close()
                }
                drawPath(p, Color(0xFF4F545C))
            }
            arrow(w / 2, h * 0.19f, 0f, -1f)
            arrow(w / 2, h * 0.81f, 0f, 1f)
            arrow(w * 0.17f, h / 2, -1f, 0f)
            arrow(w * 0.83f, h / 2, 1f, 0f)
            // 중앙 확인키 (크롬)
            val bw = w * 0.36f; val bh = h * 0.36f
            val tl = Offset((w - bw) / 2, (h - bh) / 2)
            drawRoundRect(Color(0xFF5B6069), topLeft = tl + Offset(0f, 3f), size = Size(bw, bh), cornerRadius = CornerRadius(16f, 16f))
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0xFFFDFDFE), Color(0xFFD9DCE1), Color(0xFFA9AEB5)), startY = tl.y, endY = tl.y + bh),
                topLeft = tl, size = Size(bw, bh), cornerRadius = CornerRadius(16f, 16f)
            )
            drawRoundRect(Color(0xFF7C828B), topLeft = tl, size = Size(bw, bh), cornerRadius = CornerRadius(16f, 16f), style = Stroke(2f))
            drawRoundRect(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0f)), startY = tl.y, endY = tl.y + bh * 0.5f),
                topLeft = tl + Offset(bw * 0.1f, bh * 0.08f), size = Size(bw * 0.8f, bh * 0.42f), cornerRadius = CornerRadius(14f, 14f)
            )
        }
        Text("OK", Modifier.align(Alignment.Center), color = Legend, fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}
