package com.focusaudio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val White = Color(0xFFFFFFFF)
private val Black = Color(0xFF0A0A0A)
private val Coral = Color(0xFFE5566D)

@Composable fun PlayerSpotify(d: AppData, set: Setter, cur: Clip?, env: FloatArray?, cuts: List<Long>, sleepAt: Long, onSleep: (Long) -> Unit, onDelete: (Mark) -> Unit, onGesture: () -> Unit) =
    SkinPlayer(Skin.SPOTIFY, d, set, cur, env, cuts, sleepAt, onSleep, onDelete, onGesture)

@Composable fun PlayerYtm(d: AppData, set: Setter, cur: Clip?, env: FloatArray?, cuts: List<Long>, sleepAt: Long, onSleep: (Long) -> Unit, onDelete: (Mark) -> Unit, onGesture: () -> Unit) =
    SkinPlayer(Skin.YTM, d, set, cur, env, cuts, sleepAt, onSleep, onDelete, onGesture)

@Composable private fun SkinPlayer(
    skin: Skin, d: AppData, set: Setter, cur: Clip?, env: FloatArray?, cuts: List<Long>,
    sleepAt: Long, onSleep: (Long) -> Unit, onDelete: (Mark) -> Unit, onGesture: () -> Unit
) {
    val ctx = LocalContext.current
    val p = remember { Audio.player(ctx) }
    val pal = paletteOf(d.palette)
    val accent = Color(pal.c1)
    val haptic = LocalHapticFeedback.current
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(1L) }
    var playing by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var note by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Mark?>(null) }
    LaunchedEffect(Unit) { while (true) { runCatching { pos = p.currentPosition; dur = p.duration.let { if (it > 0) it else 1L }; playing = p.isPlaying }; now = System.currentTimeMillis(); delay(250) } }

    val bg = Brush.verticalGradient(listOf(skinTop(skin, pal), skinBg(skin, pal), skinBg(skin, pal)))
    Box(Modifier.fillMaxSize().background(bg)) {
        if (cur == null) { Empty(Icons.Rounded.GraphicEq, "בחר הקלטה מהספרייה") }
        else {
        val key = cur.uri.toString()
        val marks = d.marks.filter { it.uri == key }.sortedBy { it.ms }
        val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)
        fun seek(ms: Long) = runCatching { p.seekTo(ms.coerceIn(0, dur)) }
        fun prevChapter() { val t = cuts.lastOrNull { it < pos - 1200 } ?: 0L; seek(t) }
        fun nextChapter() { val t = cuts.firstOrNull { it > pos + 500 }; if (t != null) seek(t) else seek(dur) }
        val sleepLeft = if (sleepAt > now) (sleepAt - now) / 60000 + 1 else 0L

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentPadding = PaddingValues(top = 10.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.KeyboardArrowDown, null, tint = White)
                    Text(if (skin == Skin.YTM) "הנגן" else "מושמע מתוך הספרייה", style = MaterialTheme.typography.labelMedium, color = Color(0xCCFFFFFF))
                    Icon(Icons.Rounded.MoreVert, null, tint = White)
                }
            }
            // "art" = the live waveform in a large rounded tile
            item {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(if (skin == Skin.YTM) 10.dp else 16.dp))
                    .background(Brush.linearGradient(listOf(accent.copy(alpha = .9f), Color(pal.c2).copy(alpha = .55f), Color(pal.c3).copy(alpha = .4f))))) {
                    Box(Modifier.align(Alignment.Center).fillMaxWidth().padding(14.dp)) {
                        Wave(env, pos, dur, cuts, marks) { seek(it) }
                    }
                    Text(cur.name.substringBeforeLast('.'), Modifier.align(Alignment.BottomStart).padding(16.dp),
                        color = White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // title row + like
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(cur.name.substringBeforeLast('.'), color = White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (env == null) "מנתח את ההקלטה..." else "פרקים: ${cuts.size + 1} · סימונים: ${marks.size}", color = Color(0xB3FFFFFF), style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton({ haptic.performHapticFeedback(HapticFeedbackType.LongPress); set { x -> x.copy(marks = x.marks + Mark(key, pos, note.ifBlank { "דגל" }, true)) }; note = "" }) {
                        Icon(if (skin == Skin.YTM) Icons.Rounded.ThumbUp else Icons.Rounded.Favorite, "סמן", tint = accent)
                    }
                }
            }
            // progress
            item {
                LinearProgressIndicator(progress = { (pos.toFloat() / dur).coerceIn(0f, 1f) }, color = if (skin == Skin.YTM) Color(0xFFFF0033) else White,
                    trackColor = Color(0x33FFFFFF), modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(3.dp)))
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), Arrangement.SpaceBetween) {
                    Text(fmt(pos), color = Color(0xB3FFFFFF), style = MaterialTheme.typography.labelMedium)
                    Text(fmt(dur), color = Color(0xB3FFFFFF), style = MaterialTheme.typography.labelMedium)
                }
            }
            // transport
            item {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                    IconButton({ prevChapter() }, Modifier.size(52.dp)) { Icon(Icons.Rounded.SkipPrevious, "פרק קודם", Modifier.size(34.dp), tint = White) }
                    IconButton({ seek(pos - 30_000) }, Modifier.size(48.dp)) { Icon(Icons.Rounded.Replay30, "אחורה 30", Modifier.size(28.dp), tint = White) }
                    Box(Modifier.size(70.dp).clip(CircleShape).background(White).clickable { runCatching { if (p.isPlaying) p.pause() else p.play() } }, Alignment.Center) {
                        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "נגן או השהה", Modifier.size(40.dp), tint = Black)
                    }
                    IconButton({ seek(pos + 30_000) }, Modifier.size(48.dp)) { Icon(Icons.Rounded.Forward30, "קדימה 30", Modifier.size(28.dp), tint = White) }
                    IconButton({ nextChapter() }, Modifier.size(52.dp)) { Icon(Icons.Rounded.SkipNext, "פרק הבא", Modifier.size(34.dp), tint = White) }
                }
            }
            // option chips
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
                    AssistChip(onClick = {
                        val i = speeds.indexOf(d.speed); val nv = speeds[(if (i < 0) 1 else i + 1) % speeds.size]
                        set { it.copy(speed = nv) }; runCatching { p.setPlaybackSpeed(nv) }
                    }, label = { Text("${d.speed}x") }, leadingIcon = { Icon(Icons.Rounded.Speed, null, Modifier.size(18.dp)) })
                    AssistChip(onClick = {
                        if (sleepLeft <= 0L) onSleep(System.currentTimeMillis() + 15 * 60000L)
                        else if (sleepLeft <= 15) onSleep(System.currentTimeMillis() + 30 * 60000L)
                        else if (sleepLeft <= 30) onSleep(System.currentTimeMillis() + 60 * 60000L)
                        else onSleep(0L)
                    }, label = { Text(if (sleepLeft > 0) "$sleepLeft דק'" else "שינה") }, leadingIcon = { Icon(Icons.Rounded.Timer, null, Modifier.size(18.dp)) })
                    FilterChip(selected = d.fx, onClick = { val nv = !d.fx; set { it.copy(fx = nv) }; Audio.fx?.on(nv) }, label = { Text("דיבור") })
                    FilterChip(selected = d.skip, onClick = { val nv = !d.skip; set { it.copy(skip = nv) }; runCatching { p.skipSilenceEnabled = nv } }, label = { Text("דלג שקטים") })
                    FilterChip(selected = d.autoNext, onClick = { val nv = !d.autoNext; set { it.copy(autoNext = nv) } }, label = { Text("המשך") })
                }
            }
            // bookmark + note
            item {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("הערה לנקודה הזו") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button({ haptic.performHapticFeedback(HapticFeedbackType.LongPress); set { x -> x.copy(marks = x.marks + Mark(key, pos, note.ifBlank { "סימנייה" })) }; note = "" }, Modifier.weight(1f).height(48.dp)) { Icon(Icons.Rounded.Bookmark, null); Spacer(Modifier.width(6.dp)); Text("סימנייה") }
                            Button({ haptic.performHapticFeedback(HapticFeedbackType.LongPress); set { x -> x.copy(marks = x.marks + Mark(key, pos, note.ifBlank { "דגל" }, true)) }; note = "" }, Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Coral)) { Icon(Icons.Rounded.Flag, null); Spacer(Modifier.width(6.dp)); Text("דגל") }
                        }
                    }
                }
            }
            item { FilledTonalButton(onGesture, Modifier.fillMaxWidth().height(50.dp)) { Icon(Icons.Rounded.DirectionsWalk, null); Spacer(Modifier.width(8.dp)); Text("מצב הליכה (מחוות)") } }
            if (cuts.isNotEmpty()) item { Text("פרקים", color = White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium) }
            items(cuts.size) { i -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { seek(cuts[i]) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Segment, null, tint = Color(pal.c3)); Spacer(Modifier.width(12.dp)); Text("פרק ${i + 2}", Modifier.weight(1f), color = White); Text(fmt(cuts[i]), color = Color(0xB3FFFFFF)) } }
            if (marks.isNotEmpty()) item { Text("סימונים והערות", color = White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium) }
            items(marks.size) { i -> val m = marks[i]
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { seek(m.ms) }.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (m.flag) Icons.Rounded.Flag else Icons.Rounded.Bookmark, null, tint = if (m.flag) Coral else accent)
                    Spacer(Modifier.width(12.dp)); Text(m.text, Modifier.weight(1f), color = White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(fmt(m.ms), color = Color(0xB3FFFFFF))
                    IconButton({ editing = m }) { Icon(Icons.Rounded.Edit, "ערוך", Modifier.size(18.dp), tint = White) }
                    IconButton({ onDelete(m) }) { Icon(Icons.Rounded.Close, "מחק", Modifier.size(18.dp), tint = White) }
                } }
            if (skin == Skin.YTM) item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), Arrangement.SpaceAround) {
                    Text("הבא", color = White, fontWeight = FontWeight.Bold); Text("מילים", color = Color(0x99FFFFFF)); Text("קשור", color = Color(0x99FFFFFF))
                }
            }
        }
        }
    }
    editing?.let { m ->
        var t by remember(m) { mutableStateOf(m.text) }
        AlertDialog(onDismissRequest = { editing = null }, title = { Text("עריכת הערה") },
            text = { OutlinedTextField(t, { t = it }, singleLine = true) },
            confirmButton = { TextButton({ val nt = t.ifBlank { m.text }; set { x -> x.copy(marks = x.marks.map { e -> if (e == m) e.copy(text = nt) else e }) }; editing = null }) { Text("שמור") } },
            dismissButton = { TextButton({ editing = null }) { Text("ביטול") } })
    }
}

@Composable fun SettingsScreen(d: AppData, set: Setter) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("הגדרות", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Text("סגנון נגן", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Skin.values().forEach { s ->
                val sel = d.skin == s.id
                Surface(color = if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().clickable { set { it.copy(skin = s.id) } }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = sel, onClick = { set { it.copy(skin = s.id) } })
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.label, fontWeight = FontWeight.Bold)
                            Text(when (s) { Skin.SPOTIFY -> "מראה בסגנון ספוטיפיי"; Skin.YTM -> "מראה בסגנון יוטיוב מיוזיק"; else -> "המראה המקורי עם גל-הקול" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        Text("ערכת צבע", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PALETTES.forEach { pp ->
                val sel = d.palette == pp.id
                Column(Modifier.clickable { set { it.copy(palette = pp.id) } }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(46.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(pp.c1), Color(pp.c2), Color(pp.c3))))
                        .then(if (sel) Modifier.padding(0.dp) else Modifier)) {
                        if (sel) Icon(Icons.Rounded.Check, null, Modifier.align(Alignment.Center), tint = Color(0xFF07130F))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(pp.label, style = MaterialTheme.typography.labelSmall, color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Text("עוצמת גרפיקה", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(selected = !d.hyper, onClick = { set { it.copy(hyper = false) } }, label = { Text("רגוע") }, leadingIcon = { Icon(Icons.Rounded.GraphicEq, null, Modifier.size(18.dp)) })
            FilterChip(selected = d.hyper, onClick = { set { it.copy(hyper = true) } }, label = { Text("מוגזם") }, leadingIcon = { Icon(Icons.Rounded.Bolt, null, Modifier.size(18.dp)) })
        }

        HorizontalDivider()
        Text("השמעה", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        ToggleRow("מעבד דיבור", "מבהיר את הקול בהקלטות דיבור", d.fx) { nv -> set { it.copy(fx = nv) }; Audio.fx?.on(nv) }
        ToggleRow("דילוג על שקטים", "מקצר שתיקות ארוכות", d.skip) { nv -> set { it.copy(skip = nv) } }
        ToggleRow("המשך אוטומטי", "מעבר להקלטה הבאה בסוף", d.autoNext) { nv -> set { it.copy(autoNext = nv) } }
        ToggleRow("אישור קולי במצב הליכה", "הקראת כל פעולה", d.speak) { nv -> set { it.copy(speak = nv) } }

        Text("SoundPulse · גרסה ${BuildTag.VERSION}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable private fun ToggleRow(title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = value, onCheckedChange = { onChange(it) })
    }
}
