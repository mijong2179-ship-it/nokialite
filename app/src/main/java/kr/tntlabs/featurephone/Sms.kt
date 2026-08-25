package kr.tntlabs.featurephone

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SmsMsg(val address: String, val body: String, val date: Long, val incoming: Boolean)
data class Conversation(val address: String, val name: String, val last: SmsMsg, val count: Int)

object SmsRepo {
    fun conversations(ctx: Context): List<Conversation> {
        val byAddr = LinkedHashMap<String, MutableList<SmsMsg>>()
        ctx.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
            null, null, "${Telephony.Sms.DATE} DESC"
        )?.use { c ->
            while (c.moveToNext()) {
                val addr = c.getString(0) ?: continue
                val msg = SmsMsg(
                    addr, c.getString(1) ?: "", c.getLong(2),
                    c.getInt(3) == Telephony.Sms.MESSAGE_TYPE_INBOX
                )
                byAddr.getOrPut(normalize(addr)) { mutableListOf() }.add(msg)
            }
        }
        return byAddr.values.map { list ->
            val last = list.first()
            Conversation(last.address, contactName(ctx, last.address) ?: last.address, last, list.size)
        }
    }

    fun thread(ctx: Context, address: String): List<SmsMsg> {
        val key = normalize(address)
        val out = mutableListOf<SmsMsg>()
        ctx.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
            null, null, "${Telephony.Sms.DATE} ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val addr = c.getString(0) ?: continue
                if (normalize(addr) != key) continue
                out.add(SmsMsg(addr, c.getString(1) ?: "", c.getLong(2),
                    c.getInt(3) == Telephony.Sms.MESSAGE_TYPE_INBOX))
            }
        }
        return out
    }

    fun send(ctx: Context, to: String, body: String): Boolean = runCatching {
        val sm = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java)
        else @Suppress("DEPRECATION") SmsManager.getDefault()
        val parts = sm.divideMessage(body)
        if (parts.size > 1) sm.sendMultipartTextMessage(to, null, parts, null, null)
        else sm.sendTextMessage(to, null, body, null, null)
        true
    }.getOrDefault(false)

    private fun normalize(a: String): String {
        val d = a.filter { it.isDigit() }
        return if (d.length > 8) d.takeLast(8) else d.ifEmpty { a }
    }

    private fun contactName(ctx: Context, number: String): String? = runCatching {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)
        )
        ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()
}

private val timeFmt = SimpleDateFormat("M/d HH:mm", Locale.KOREAN)

@Composable
fun LcdSmsList(convs: List<Conversation>, selIndex: Int, onSelect: (Int) -> Unit, onOpen: (Int) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(selIndex) {
        if (convs.isNotEmpty()) listState.animateScrollToItem(selIndex.coerceIn(0, convs.lastIndex))
    }
    Column(Modifier.fillMaxSize()) {
        LcdHeader("받은 문자", if (convs.isEmpty()) "" else "${selIndex + 1}/${convs.size}")
        if (convs.isEmpty()) {
            Text(
                "문자가 없습니다", Modifier.padding(24.dp),
                color = LcdText.copy(alpha = 0.7f), fontFamily = Metro, fontSize = 20.sp
            )
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState) {
            itemsIndexed(convs) { i, c ->
                val selected = i == selIndex
                Column(
                    Modifier.fillMaxWidth()
                        .background(if (selected) Accent else Color.Transparent)
                        .clickable(remember { MutableInteractionSource() }, null) {
                            if (selected) onOpen(i) else onSelect(i)
                        }
                        .padding(horizontal = 24.dp, vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            c.name, Modifier.weight(1f), color = if (selected) Color.White else Ink, fontFamily = Metro,
                            fontSize = 21.sp, fontWeight = FontWeight.Normal, maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            timeFmt.format(Date(c.last.date)), color = if (selected) Color.White else Ink.copy(alpha = 0.7f),
                            fontFamily = Metro, fontSize = 13.sp
                        )
                    }
                    Text(
                        c.last.body.replace('\n', ' '), color = if (selected) Color.White else Ink.copy(alpha = 0.8f),
                        fontFamily = Metro, fontWeight = FontWeight.Light, fontSize = 16.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        SoftkeyRow("새 문자", "", "뒤로")
    }
}

@Composable
fun LcdSmsThread(name: String, msgs: List<SmsMsg>, scrollTick: Int) {
    val listState = rememberLazyListState()
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.scrollToItem(msgs.lastIndex) }
    LaunchedEffect(scrollTick) {
        if (scrollTick != 0) listState.animateScrollBy(if (scrollTick > 0) 300f else -300f)
    }
    Column(Modifier.fillMaxSize()) {
        LcdHeader(name, "${msgs.size}건")
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp), state = listState
        ) {
            itemsIndexed(msgs) { _, m ->
                Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(
                        Modifier
                            .align(if (m.incoming) Alignment.CenterStart else Alignment.CenterEnd)
                            .fillMaxWidth(0.82f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (m.incoming) Color(0xFFDDE3EE) else Accent)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(m.body, color = if (m.incoming) Ink else Color.White, fontFamily = Pixel, fontSize = 16.sp)
                        Text(
                            timeFmt.format(Date(m.date)), color = if (m.incoming) Ink.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.8f),
                            fontFamily = Metro, fontSize = 11.sp
                        )
                    }
                }
            }
        }
        SoftkeyRow("답장", "", "뒤로")
    }
}

@Composable
fun LcdSmsCompose(to: String, editingTo: Boolean, body: MultitapState, tick: Int, status: String?) {
    @Suppress("UNUSED_EXPRESSION") tick
    Column(Modifier.fillMaxSize()) {
        LcdHeader("문자 작성", if (editingTo) "번호" else body.modeLabel)
        Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp)) {
            Text("받는 사람", color = LcdText.copy(alpha = 0.6f), fontFamily = Metro, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    to.ifEmpty { "번호 입력" }, color = if (to.isEmpty()) LcdText.copy(alpha = 0.4f) else LcdText,
                    fontFamily = Metro, fontSize = 24.sp, fontWeight = FontWeight.Light
                )
                if (editingTo) Cursor()
            }
            Spacer(Modifier.height(12.dp))
            Text("내용", color = LcdText.copy(alpha = 0.6f), fontFamily = Metro, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    body.text, color = LcdText, fontFamily = Metro, fontSize = 22.sp,
                    fontWeight = FontWeight.Light, lineHeight = 28.sp
                )
                if (!editingTo) Cursor()
            }
            Spacer(Modifier.weight(1f))
            Text(
                status ?: if (editingTo) "번호 입력 후 OK" else "*한/영/숫자  #쌍자음  ▶띄움 ◀지움",
                color = LcdText.copy(alpha = 0.6f), fontFamily = Metro, fontSize = 13.sp
            )
        }
        SoftkeyRow(if (editingTo) "다음" else "전송", "", "지움")
    }
}

@Composable
private fun Cursor() {
    Spacer(Modifier.width(2.dp))
    Box(Modifier.width(2.dp).height(24.dp).background(Ink))
}
