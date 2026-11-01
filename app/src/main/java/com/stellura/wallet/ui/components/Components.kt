package com.stellura.wallet.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.stellura.wallet.ui.theme.Mono
import com.stellura.wallet.ui.theme.S

/** The "Hex Vault" brand mark, drawn on a 100×100 grid. */
@Composable
fun HexMark(size: Dp, modifier: Modifier = Modifier, ring: Color = S.c.accent, star: Color = S.c.text) {
    Canvas(modifier.size(size).semantics { contentDescription = "Stellura" }) {
        val k = this.size.minDimension / 100f
        fun p(x: Float, y: Float) = Offset(x * k, y * k)
        val hex = Path().apply {
            moveTo(p(50f, 6f).x, p(50f, 6f).y)
            listOf(88f to 28f, 88f to 72f, 50f to 94f, 12f to 72f, 12f to 28f).forEach { (x, y) -> lineTo(x * k, y * k) }
            close()
        }
        drawPath(hex, ring, style = Stroke(width = 6f * k, join = StrokeJoin.Round))
        val st = Path().apply {
            val pts = listOf(50f to 30f, 54f to 46f, 70f to 50f, 54f to 54f, 50f to 70f, 46f to 54f, 30f to 50f, 46f to 46f)
            moveTo(pts[0].first * k, pts[0].second * k)
            pts.drop(1).forEach { (x, y) -> lineTo(x * k, y * k) }
            close()
        }
        drawPath(st, star)
    }
}

@Composable
fun Sparkline(values: List<Double>, color: Color, modifier: Modifier = Modifier, strokeDp: Float = 1.6f) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val min = values.min(); val max = values.max()
        val range = (max - min).takeIf { it > 0 } ?: 1.0
        val stepX = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = (size.height - ((v - min) / range * size.height)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = strokeDp * density, join = StrokeJoin.Round))
    }
}

@Composable
fun SurfaceCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(S.c.surface)
            .border(1.dp, S.c.line, RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) { content() }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = S.c.accent, contentColor = S.c.onAccent),
    ) { Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = S.c.surface2, contentColor = S.c.text),
        border = androidx.compose.foundation.BorderStroke(1.dp, S.c.line),
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun TickerBadge(symbol: String, size: Dp = 38.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(S.c.surface).border(1.5.dp, S.c.line, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text(symbol.take(4), fontFamily = Mono, fontSize = 11.sp, color = S.c.text) }
}

@Composable
fun PinDots(entered: Int, total: Int = 6) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.semantics {
        contentDescription = "$entered of $total digits entered"
    }) {
        repeat(total) { i ->
            val filled = i < entered
            Box(
                Modifier.size(14.dp).clip(CircleShape)
                    .then(if (filled) Modifier.background(S.c.accent) else Modifier.border(1.5.dp, S.c.muted, CircleShape))
            )
        }
    }
}

@Composable
fun PinPad(onDigit: (Char) -> Unit, onDelete: () -> Unit, onBiometric: (() -> Unit)?) {
    val rows = listOf("123", "456", "789")
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) { r.forEach { d -> PinKey(d.toString()) { onDigit(d) } } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                if (onBiometric != null) IconButton(onClick = onBiometric, modifier = Modifier.size(72.dp)) {
                    Icon(Icons.Filled.Fingerprint, "Unlock with fingerprint or face", tint = S.c.up, modifier = Modifier.size(32.dp))
                }
            }
            PinKey("0") { onDigit('0') }
            IconButton(onClick = onDelete, modifier = Modifier.size(72.dp)) {
                Icon(Icons.AutoMirrored.Filled.Backspace, "Delete digit", tint = S.c.muted)
            }
        }
    }
}

@Composable
private fun PinKey(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(72.dp).clip(CircleShape).background(S.c.surface).border(1.dp, S.c.line, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 26.sp, color = S.c.text) }
}

@Composable
fun QrCode(content: String, size: Dp, modifier: Modifier = Modifier) {
    val bmp = remember(content) {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val m = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 512, 512, hints)
        val px = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) 0xFF070A14.toInt() else 0xFFFFFFFF.toInt() }
        Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    Image(bmp, contentDescription = "QR code", modifier = modifier.size(size).clip(RoundedCornerShape(12.dp)))
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(), fontFamily = Mono, fontSize = 11.sp, letterSpacing = 1.6.sp, color = S.c.muted,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp, top = 8.dp)
    )
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun HSpace(w: Dp) = Spacer(Modifier.width(w))

@Composable
fun FullWidth(content: @Composable () -> Unit) = Box(Modifier.fillMaxWidth()) { content() }
