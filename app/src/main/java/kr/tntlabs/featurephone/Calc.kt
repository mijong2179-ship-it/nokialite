package kr.tntlabs.featurephone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.math.RoundingMode

// 옛날 폰 계산기: 입력 중인 숫자 + 수식 토큰, 좌→우 사칙 우선순위 적용
class CalcState {
    var tokens: MutableList<String> = mutableListOf()
    var current: String = ""
    var result: String? = null

    fun digit(c: Char) {
        if (result != null) clear()
        if (c == '.' && current.contains('.')) return
        if (c == '.' && current.isEmpty()) current = "0"
        if (current == "0" && c != '.') current = ""
        if (current.length < 14) current += c
    }

    fun op(sym: String) {
        if (result != null) { tokens = mutableListOf(result!!); current = ""; result = null }
        if (current.isNotEmpty()) tokens.add(current)
        else if (tokens.isEmpty()) tokens.add("0")
        if (tokens.last() in OPS) tokens[tokens.lastIndex] = sym else tokens.add(sym)
        current = ""
    }

    fun backspace() {
        when {
            result != null -> clear()
            current.isNotEmpty() -> current = current.dropLast(1)
            tokens.isNotEmpty() -> {
                val last = tokens.removeAt(tokens.lastIndex)
                if (last !in OPS) current = last
            }
        }
    }

    fun clear() { tokens = mutableListOf(); current = ""; result = null }

    fun equals() {
        if (result != null) return
        val all = tokens.toMutableList()
        if (current.isNotEmpty()) all.add(current)
        if (all.isNotEmpty() && all.last() in OPS) all.removeAt(all.lastIndex)
        if (all.isEmpty()) return
        result = runCatching { evaluate(all) }.getOrDefault("오류")
    }

    val expression: String
        get() = (tokens + listOfNotNull(current.takeIf { it.isNotEmpty() })).joinToString(" ")

    private fun evaluate(all: List<String>): String {
        val nums = mutableListOf<BigDecimal>()
        val ops = mutableListOf<String>()
        var i = 0
        nums.add(BigDecimal(all[i++]))
        while (i < all.size) {
            val op = all[i++]; val n = BigDecimal(all[i++])
            if (op == "×" || op == "÷") {
                val a = nums.removeAt(nums.lastIndex)
                nums.add(if (op == "×") a.multiply(n) else a.divide(n, 10, RoundingMode.HALF_UP))
            } else { nums.add(n); ops.add(op) }
        }
        var acc = nums[0]
        for (k in ops.indices) acc = if (ops[k] == "+") acc.add(nums[k + 1]) else acc.subtract(nums[k + 1])
        return acc.stripTrailingZeros().toPlainString().let { if (it == "-0") "0" else it }
    }

    companion object { val OPS = setOf("+", "-", "×", "÷") }
}

@Composable
fun LcdCalc(state: CalcState, tick: Int) {
    @Suppress("UNUSED_EXPRESSION") tick
    Column(Modifier.fillMaxSize()) {
        LcdHeader("계산기", "")
        Spacer(Modifier.height(30.dp))
        Text(
            state.expression.ifEmpty { "0" },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            color = LcdText.copy(alpha = if (state.result != null) 0.6f else 1f),
            fontFamily = Metro, fontWeight = FontWeight.Light,
            fontSize = if (state.result != null) 28.sp else 44.sp,
            lineHeight = 48.sp, textAlign = TextAlign.End, maxLines = 2
        )
        state.result?.let {
            Text(
                "= $it",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
                color = LcdText, fontFamily = Metro, fontWeight = FontWeight.Normal,
                fontSize = 56.sp, lineHeight = 60.sp, textAlign = TextAlign.End, maxLines = 2
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            "방향키 + − × ÷    *소수점    #계산",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            color = LcdText.copy(alpha = 0.6f), fontFamily = Metro, fontSize = 14.sp
        )
        SoftkeyRow("계산", "", if (state.expression.isEmpty()) "뒤로" else "지움")
    }
}
