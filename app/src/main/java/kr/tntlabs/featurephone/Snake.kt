package kr.tntlabs.featurephone

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

// 노키아 스네이크 — 2/4/6/8 또는 방향키, 5 시작·일시정지
class SnakeGame {
    val cols = 22
    val rows = 16
    var body by mutableStateOf(listOf(Pair(11, 8), Pair(10, 8), Pair(9, 8)))
    var dir = Pair(1, 0)
    private var pendingDir = dir
    var food by mutableStateOf(Pair(16, 8))
    var score by mutableIntStateOf(0)
    var best by mutableIntStateOf(0)
    var running by mutableStateOf(false)
    var over by mutableStateOf(false)

    fun turn(dx: Int, dy: Int) {
        if (dx == -dir.first && dy == -dir.second) return
        pendingDir = Pair(dx, dy)
        if (!running && !over) running = true
    }

    fun toggle() {
        if (over) reset()
        running = !running
    }

    fun reset() {
        body = listOf(Pair(11, 8), Pair(10, 8), Pair(9, 8))
        dir = Pair(1, 0); pendingDir = dir
        score = 0; over = false; running = false
        spawnFood()
    }

    fun step() {
        dir = pendingDir
        val head = body.first()
        val next = Pair(head.first + dir.first, head.second + dir.second)
        if (next.first !in 0 until cols || next.second !in 0 until rows || next in body) {
            over = true; running = false
            if (score > best) best = score
            return
        }
        val grew = next == food
        body = listOf(next) + if (grew) body else body.dropLast(1)
        if (grew) { score += 10; spawnFood() }
    }

    private fun spawnFood() {
        var p: Pair<Int, Int>
        do { p = Pair(Random.nextInt(cols), Random.nextInt(rows)) } while (p in body)
        food = p
    }
}

@Composable
fun LcdSnake(game: SnakeGame) {
    LaunchedEffect(game.running) {
        while (game.running) {
            delay((190L - (game.score / 30) * 15L).coerceAtLeast(90L))
            game.step()
        }
    }
    val snakeColor = Color(0xFF1B2A12)
    val boardColor = Color(0xFF9CB83C)
    Column(Modifier.fillMaxSize()) {
        LcdHeader("스네이크", "점수 ${game.score}   최고 ${game.best}")
        Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 6.dp)) {
            Canvas(Modifier.fillMaxSize().background(boardColor)) {
                val cell = minOf(size.width / game.cols, size.height / game.rows)
                val ox = (size.width - cell * game.cols) / 2f
                val oy = (size.height - cell * game.rows) / 2f
                drawRect(snakeColor, Offset(ox, oy), Size(cell * game.cols, cell * game.rows),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
                fun cellRect(p: Pair<Int, Int>, inset: Float) = drawRect(
                    snakeColor,
                    Offset(ox + p.first * cell + inset, oy + p.second * cell + inset),
                    Size(cell - inset * 2, cell - inset * 2)
                )
                game.body.forEach { cellRect(it, 1.5f) }
                cellRect(game.food, cell * 0.25f)
            }
            if (!game.running) {
                Text(
                    if (game.over) "게임 오버\n5 다시 시작" else "5 시작\n2 4 6 8 이동",
                    Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.6f)).padding(16.dp),
                    color = Color.White, fontFamily = Metro, fontSize = 22.sp,
                    fontWeight = FontWeight.Light, textAlign = TextAlign.Center, lineHeight = 30.sp
                )
            }
        }
        SoftkeyRow(if (game.running) "일시정지" else "시작", "", "뒤로")
    }
}
