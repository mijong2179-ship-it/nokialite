package kr.tntlabs.featurephone

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── 2004 애니콜 LCD 팔레트 ────────────────────────────
val Pixel = FontFamily(Font(R.font.galmuri9))
val LcdWhite = Color(0xFFF3F5F9)
val Ink = Color(0xFF14264F)
val Accent = Color(0xFF2A5FD6)
private val TitleTop = Color(0xFF6C9BEE)
private val TitleBottom = Color(0xFF1E48B4)
private val SoftBar = Color(0xFFD5DBE7)

// 호환용 별칭 (계산기/문자/게임 화면에서 사용)
val LcdText = Ink
val Metro = Pixel

@Composable
fun LcdPhoto(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.bliss),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)))
        content()
    }
}

@Composable
fun LcdPanel(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(LcdWhite)) { content() }
}

// 파란 그라데이션 타이틀바
@Composable
fun LcdHeader(title: String, right: String) {
    Box(
        Modifier.fillMaxWidth().height(30.dp)
            .background(Brush.verticalGradient(listOf(TitleTop, TitleBottom)))
            .padding(horizontal = 10.dp)
    ) {
        Text(
            title, Modifier.align(Alignment.CenterStart),
            color = Color.White, fontFamily = Pixel, fontSize = 16.sp, fontWeight = FontWeight.Bold
        )
        Text(
            right, Modifier.align(Alignment.CenterEnd),
            color = Color.White, fontFamily = Pixel, fontSize = 14.sp
        )
    }
}

// 검은 외곽선 흰 글씨 (사진 위 시계)
@Composable
fun OutlinedText(text: String, size: TextUnit, modifier: Modifier = Modifier, bold: Boolean = true) {
    val weight = if (bold) FontWeight.Bold else FontWeight.Normal
    Box(modifier) {
        Text(
            text, color = Color.Black, fontFamily = Pixel, fontSize = size, fontWeight = weight,
            style = TextStyle(drawStyle = Stroke(width = 7f, join = StrokeJoin.Round))
        )
        Text(text, color = Color.White, fontFamily = Pixel, fontSize = size, fontWeight = weight)
    }
}

@Composable
fun LcdIdle() {
    val now by produceState(Date()) {
        while (true) { value = Date(); delay(1000) }
    }
    val context = LocalContext.current
    val battery by produceState(100, context) {
        while (true) {
            val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) ?: 100
            val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            value = if (scale > 0) level * 100 / scale else 100
            delay(30_000)
        }
    }
    val dateFmt = remember { SimpleDateFormat("M/d(E)", Locale.KOREAN) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.KOREAN) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SignalBars(Color.White)
            Spacer(Modifier.weight(1f))
            BatteryIcon(level = battery, color = Color.White)
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedText(dateFmt.format(now), 24.sp, Modifier.padding(bottom = 10.dp))
            Spacer(Modifier.weight(1f))
            OutlinedText(timeFmt.format(now), 60.sp)
        }
        SoftkeyRow("바로가기", "메뉴", "설정", overPhoto = true)
    }
}

@Composable
fun LcdDial(digits: String) {
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(40.dp))
        Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            OutlinedText(digits, if (digits.length <= 11) 44.sp else 30.sp)
        }
        Spacer(Modifier.weight(1f))
        SoftkeyRow("통화", "", "지움", overPhoto = true)
    }
}

@Composable
fun LcdMenu(
    title: String,
    entries: List<MenuEntry>,
    selIndex: Int,
    onSelect: (Int) -> Unit,
    onActivate: (MenuEntry) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selIndex) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(selIndex.coerceIn(0, entries.lastIndex))
    }
    Column(Modifier.fillMaxSize()) {
        LcdHeader(title, "${(selIndex + 1).coerceAtMost(entries.size)}/${entries.size}")
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState) {
            itemsIndexed(entries) { i, entry ->
                val selected = i == selIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (selected) Accent else Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { if (selected) onActivate(entry) else onSelect(i) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${i + 1}.", color = if (selected) Color.White else Ink.copy(alpha = 0.55f),
                        fontFamily = Pixel, fontSize = 15.sp, modifier = Modifier.width(30.dp)
                    )
                    Text(
                        entry.label,
                        color = if (selected) Color.White else Ink,
                        fontFamily = Pixel, fontSize = 18.sp,
                        fontWeight = if (entry is MenuEntry.Builtin) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        SoftkeyRow("선택", "", "뒤로")
    }
}

@Composable
fun SoftkeyRow(left: String, center: String, right: String, overPhoto: Boolean = false) {
    val bg = if (overPhoto) Color.Black.copy(alpha = 0.35f) else SoftBar
    val fg = if (overPhoto) Color.White else Ink
    Box(
        Modifier.fillMaxWidth().background(bg).padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(left, Modifier.align(Alignment.CenterStart), color = fg, fontFamily = Pixel, fontSize = 15.sp)
        Text(center, Modifier.align(Alignment.Center), color = fg, fontFamily = Pixel, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(right, Modifier.align(Alignment.CenterEnd), color = fg, fontFamily = Pixel, fontSize = 15.sp)
    }
}

@Composable
fun SignalBars(color: Color) {
    Canvas(Modifier.width(24.dp).height(12.dp)) {
        val u = size.width / 11f
        drawRect(color, topLeft = Offset(0f, 0f), size = Size(u * 3f, u * 1f))
        drawRect(color, topLeft = Offset(u * 1f, 0f), size = Size(u * 1f, size.height))
        for (i in 0 until 4) {
            val h = size.height * (0.35f + 0.216f * i)
            drawRect(color, topLeft = Offset(u * (4.6f + i * 1.65f), size.height - h), size = Size(u, h))
        }
    }
}

@Composable
fun BatteryIcon(level: Int, color: Color) {
    Canvas(Modifier.width(22.dp).height(11.dp)) {
        val bodyW = size.width * 0.85f
        val stroke = 2f
        drawRect(color, topLeft = Offset(0f, 0f), size = Size(bodyW, size.height), style = Stroke(stroke))
        drawRect(color, topLeft = Offset(bodyW + 1.5f, size.height * 0.28f), size = Size(size.width - bodyW - 1.5f, size.height * 0.44f))
        val pad = stroke + 1.5f
        val fillW = (bodyW - pad * 2) * (level / 100f)
        if (fillW > 0f) drawRect(color, topLeft = Offset(pad, pad), size = Size(fillW, size.height - pad * 2))
    }
}


// ── 3x3 아이콘 그리드 메뉴 ─────────────────────────────
@Composable
fun LcdGrid(
    entries: List<MenuEntry>,
    selIndex: Int,
    onSelect: (Int) -> Unit,
    onActivate: (MenuEntry) -> Unit
) {
    val pages = (entries.size + 8) / 9
    val page = selIndex / 9
    val pageEntries = entries.drop(page * 9).take(9)
    Column(Modifier.fillMaxSize()) {
        LcdHeader(entries.getOrNull(selIndex)?.label ?: "메뉴", "${page + 1}/${pages.coerceAtLeast(1)}")
        Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 6.dp, vertical = 4.dp)) {
            for (r in 0 until 3) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    for (c in 0 until 3) {
                        val i = page * 9 + r * 3 + c
                        val entry = entries.getOrNull(i)
                        Box(
                            Modifier.weight(1f).fillMaxHeight().padding(3.dp)
                                .background(if (i == selIndex) Accent else Color.Transparent, RoundedCornerShape(4.dp))
                                .clickable(remember { MutableInteractionSource() }, null, enabled = entry != null) {
                                    if (i == selIndex) entry?.let(onActivate) else onSelect(i)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (entry != null) GridCell(entry, i == selIndex)
                        }
                    }
                }
            }
        }
        // 페이지 점
        Row(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(pages) { p ->
                Box(Modifier.width(6.dp).height(6.dp).background(if (p == page) Accent else Ink.copy(alpha = 0.25f), RoundedCornerShape(3.dp)))
            }
        }
        SoftkeyRow("실행", "", "뒤로")
    }
}

@Composable
private fun GridCell(entry: MenuEntry, selected: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(44.dp).height(44.dp), contentAlignment = Alignment.Center) {
            when (entry) {
                is MenuEntry.App -> entry.icon?.let {
                    Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize(), filterQuality = FilterQuality.None)
                }
                is MenuEntry.Builtin -> BuiltinIcon(entry.kind)
                else -> {}
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            entry.label, color = if (selected) Color.White else Ink, fontFamily = Pixel, fontSize = 11.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun BuiltinIcon(kind: BuiltinKind) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val s = Stroke(width = 4f)
        when (kind) {
            BuiltinKind.SMS -> {
                drawRoundRect(Color(0xFFFFF4C2), Offset(w * 0.08f, h * 0.22f), Size(w * 0.84f, h * 0.56f), CornerRadius(6f, 6f))
                drawRoundRect(Ink, Offset(w * 0.08f, h * 0.22f), Size(w * 0.84f, h * 0.56f), CornerRadius(6f, 6f), style = s)
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.08f, h * 0.24f); lineTo(w * 0.5f, h * 0.55f); lineTo(w * 0.92f, h * 0.24f)
                }
                drawPath(p, Ink, style = s)
            }
            BuiltinKind.CALC -> {
                drawRoundRect(Color(0xFFDDE6F7), Offset(w * 0.18f, h * 0.06f), Size(w * 0.64f, h * 0.88f), CornerRadius(6f, 6f))
                drawRoundRect(Ink, Offset(w * 0.18f, h * 0.06f), Size(w * 0.64f, h * 0.88f), CornerRadius(6f, 6f), style = s)
                drawRect(Color(0xFF9CB83C), Offset(w * 0.26f, h * 0.14f), Size(w * 0.48f, h * 0.2f))
                for (r in 0 until 3) for (c in 0 until 3)
                    drawRect(Ink, Offset(w * (0.27f + c * 0.17f), h * (0.42f + r * 0.16f)), Size(w * 0.1f, h * 0.1f))
            }
            BuiltinKind.YOUTUBE -> {
                drawRoundRect(Color(0xFFE62117), Offset(w * 0.06f, h * 0.2f), Size(w * 0.88f, h * 0.6f), CornerRadius(10f, 10f))
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.42f, h * 0.36f); lineTo(w * 0.64f, h * 0.5f); lineTo(w * 0.42f, h * 0.64f); close()
                }
                drawPath(p, Color.White)
            }
            BuiltinKind.CAMERA -> {
                drawRoundRect(Color(0xFF3C4250), Offset(w * 0.06f, h * 0.24f), Size(w * 0.88f, h * 0.6f), CornerRadius(6f, 6f))
                drawRect(Color(0xFF3C4250), Offset(w * 0.3f, h * 0.14f), Size(w * 0.3f, h * 0.14f))
                drawCircle(Color(0xFFDDE6F7), radius = w * 0.2f, center = Offset(w * 0.5f, h * 0.54f))
                drawCircle(Color(0xFF2A5FD6), radius = w * 0.12f, center = Offset(w * 0.5f, h * 0.54f))
                drawCircle(Color.White, radius = w * 0.04f, center = Offset(w * 0.46f, h * 0.49f))
            }
            BuiltinKind.SETTINGS -> {
                val c = Offset(w / 2, h / 2)
                for (k in 0 until 8) {
                    val a = k * Math.PI / 4
                    val dx = kotlin.math.cos(a).toFloat(); val dy = kotlin.math.sin(a).toFloat()
                    drawLine(Color(0xFF5A6270), c + Offset(dx * w * 0.22f, dy * w * 0.22f), c + Offset(dx * w * 0.42f, dy * w * 0.42f), 9f)
                }
                drawCircle(Color(0xFF8C95A3), radius = w * 0.3f, center = c)
                drawCircle(Color(0xFF5A6270), radius = w * 0.3f, center = c, style = s)
                drawCircle(LcdWhite, radius = w * 0.12f, center = c)
                drawCircle(Color(0xFF5A6270), radius = w * 0.12f, center = c, style = s)
            }
            BuiltinKind.PHONE -> {
                drawRoundRect(Color(0xFF1FA33A), Offset(w * 0.06f, h * 0.06f), Size(w * 0.88f, h * 0.88f), CornerRadius(10f, 10f))
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.28f, h * 0.22f); lineTo(w * 0.4f, h * 0.22f); lineTo(w * 0.46f, h * 0.4f)
                    lineTo(w * 0.38f, h * 0.48f); quadraticTo(w * 0.5f, h * 0.66f, w * 0.62f, h * 0.7f)
                    lineTo(w * 0.7f, h * 0.62f); lineTo(w * 0.8f, h * 0.7f); lineTo(w * 0.78f, h * 0.8f)
                    quadraticTo(w * 0.3f, h * 0.78f, w * 0.28f, h * 0.22f); close()
                }
                drawPath(p, Color.White)
            }
            BuiltinKind.CONTACTS -> {
                drawRoundRect(Color(0xFFF2C94C), Offset(w * 0.12f, h * 0.08f), Size(w * 0.76f, h * 0.84f), CornerRadius(6f, 6f))
                drawRoundRect(Ink, Offset(w * 0.12f, h * 0.08f), Size(w * 0.76f, h * 0.84f), CornerRadius(6f, 6f), style = s)
                for (k in 0 until 3) drawRect(Ink, Offset(w * 0.06f, h * (0.24f + k * 0.2f)), Size(w * 0.12f, h * 0.07f))
                drawCircle(Ink, radius = w * 0.11f, center = Offset(w * 0.52f, h * 0.38f))
                drawRoundRect(Ink, Offset(w * 0.32f, h * 0.54f), Size(w * 0.4f, h * 0.22f), CornerRadius(8f, 8f))
            }
            BuiltinKind.GALLERY -> {
                drawRoundRect(Color(0xFF7EC8F0), Offset(w * 0.06f, h * 0.12f), Size(w * 0.88f, h * 0.76f), CornerRadius(6f, 6f))
                drawCircle(Color(0xFFFFE27A), radius = w * 0.09f, center = Offset(w * 0.3f, h * 0.34f))
                val m = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.06f, h * 0.88f); lineTo(w * 0.36f, h * 0.5f); lineTo(w * 0.54f, h * 0.7f)
                    lineTo(w * 0.68f, h * 0.56f); lineTo(w * 0.94f, h * 0.88f); close()
                }
                drawPath(m, Color(0xFF3E9A4C))
                drawRoundRect(Ink, Offset(w * 0.06f, h * 0.12f), Size(w * 0.88f, h * 0.76f), CornerRadius(6f, 6f), style = s)
            }
            BuiltinKind.SNAKE -> {
                drawRect(Color(0xFF9CB83C), Offset(w * 0.06f, h * 0.06f), Size(w * 0.88f, h * 0.88f))
                drawRect(Ink, Offset(w * 0.06f, h * 0.06f), Size(w * 0.88f, h * 0.88f), style = s)
                val cells = listOf(1 to 2, 2 to 2, 3 to 2, 3 to 3, 3 to 4, 4 to 4, 5 to 4)
                val u = w * 0.88f / 7f
                cells.forEach { (x, y) -> drawRect(Ink, Offset(w * 0.06f + (x - 0.5f) * u, h * 0.06f + (y - 0.5f) * u), Size(u * 0.85f, u * 0.85f)) }
                drawRect(Color(0xFFD6362B), Offset(w * 0.06f + 5.7f * u, h * 0.06f + 1.6f * u), Size(u * 0.7f, u * 0.7f))
            }
        }
    }
}
