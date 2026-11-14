package fm.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fm.core.*

@Composable
fun SectionCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
            }
            content()
        }
    }
}

@Composable
fun KeyValue(k: String, v: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(k, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

@Composable
fun Stars(value: Double, size: Dp = 14.dp) {
    Row {
        for (i in 1..5) {
            val c = when {
                value >= i -> MaterialTheme.colorScheme.secondary
                value >= i - 0.5 -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
            }
            Text("★", color = c, fontSize = (size.value).sp)
        }
    }
}

@Composable
fun ClubBadge(club: Club?, size: Dp = 28.dp) {
    val c1 = Color(club?.primaryColor ?: 0xFF888888)
    val c2 = Color(club?.secondaryColor ?: 0xFFFFFFFF)
    Box(Modifier.size(size).clip(CircleShape).background(c1), contentAlignment = Alignment.Center) {
        Text(club?.shortName?.take(3) ?: "-", color = if (c2 == c1) Color.White else c2, fontSize = (size.value * 0.33f).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun FormDots(form: List<Char>) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (f in form.takeLast(5)) {
            val c = when (f) { 'W' -> Pal.win; 'D' -> Pal.draw; else -> Pal.loss }
            Box(Modifier.size(16.dp).clip(RoundedCornerShape(3.dp)).background(c), contentAlignment = Alignment.Center) {
                Text(f.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Attribute value with colour coding (1–20 scale), or a range when scouting knowledge is partial. */
@Composable
fun AttrCell(label: String, range: IntRange, modifier: Modifier = Modifier) {
    val v = (range.first + range.last) / 2
    val color = when {
        v >= 16 -> Color(0xFF2ECC71)
        v >= 12 -> Color(0xFF9CCC65)
        v >= 8 -> Color(0xFFFFCA28)
        else -> Color(0xFFEF6C00)
    }
    Row(modifier.padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(if (range.first == range.last) "${range.first}" else "${range.first}-${range.last}",
            color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
    }
}

/** Generic sortable table used for squads, searches and league tables. */
class Col<T>(val title: String, val width: Dp, val sortKey: ((T) -> Comparable<*>)? = null, val cell: @Composable (T) -> Unit)

@Composable
fun <T> SortableTable(rows: List<T>, cols: List<Col<T>>, key: (T) -> Any, onClick: (T) -> Unit, defaultSort: Int = -1, header: (@Composable () -> Unit)? = null) {
    var sortCol by remember { mutableIntStateOf(defaultSort) }
    var desc by remember { mutableStateOf(true) }
    val sorted = remember(rows, sortCol, desc) {
        val c = cols.getOrNull(sortCol)?.sortKey
        if (c == null) rows else {
            @Suppress("UNCHECKED_CAST")
            val cmp = Comparator<T> { a, b -> (c(a) as Comparable<Any>).compareTo(c(b) as Any) }
            if (desc) rows.sortedWith(cmp.reversed()) else rows.sortedWith(cmp)
        }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (header != null) item { header() }
        item {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 8.dp, vertical = 6.dp)) {
                cols.forEachIndexed { i, c ->
                    val mod = if (c.width == Dp.Unspecified) Modifier.weight(1f) else Modifier.width(c.width)
                    Text(c.title + if (sortCol == i) (if (desc) " ▼" else " ▲") else "",
                        mod.clickable(enabled = c.sortKey != null) { if (sortCol == i) desc = !desc else { sortCol = i; desc = true } },
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
        items(sorted, key = key) { r ->
            Row(Modifier.fillMaxWidth().clickable { onClick(r) }.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                for (c in cols) {
                    val mod = if (c.width == Dp.Unspecified) Modifier.weight(1f) else Modifier.width(c.width)
                    Box(mod) { c.cell(r) }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
fun SmallText(s: String, color: Color = MaterialTheme.colorScheme.onSurface, bold: Boolean = false) =
    Text(s, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

@Composable
fun ChipRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(options.size) { i ->
            FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(options[i]) })
        }
    }
}

@Composable
fun LabeledSlider(label: String, value: Double, left: String, right: String, onChange: (Double) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Slider(value = value.toFloat(), onValueChange = { onChange(it.toDouble()) }, valueRange = 0f..1f)
        Row { SmallText(left, MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.weight(1f)); SmallText(right, MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
