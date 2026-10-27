package com.messoff.pulso.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messoff.pulso.AppViewModel
import com.messoff.pulso.ui.components.press
import com.messoff.pulso.ui.icons.LucideData
import com.messoff.pulso.ui.icons.LucideIcon
import com.messoff.pulso.ui.theme.LocalPulso
import com.messoff.pulso.ui.theme.Type
import com.messoff.pulso.util.Dates

/** Lista con scroll y hueco para la barra inferior. */
@Composable
fun ScreenList(content: LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        state = rememberLazyListState(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/** La nota física de "próximo evento", con su cinta adhesiva. */
@Composable
fun NextUpNote(vm: AppViewModel, onOpen: (String) -> Unit) {
    val c = LocalPulso.current
    val events by vm.events.collectAsState()
    val now by vm.now.collectAsState()
    val nowMs = System.currentTimeMillis()
    val next = events
        .map { it to Dates.epochMillis(it.date, it.start) }
        .filter { it.second >= nowMs - 60_000 }
        .minByOrNull { it.second }?.first

    Box(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .rotate(-0.4f)
                .shadow(12.dp, RoundedCornerShape(6.dp), ambientColor = Color(0x33000000), spotColor = Color(0x44000000))
                .clip(RoundedCornerShape(6.dp))
                .background(c.note)
                .then(if (next != null) Modifier.press(scaleTo = 0.98f, haptics = vm.haptics) { onOpen(next.id) } else Modifier)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(if (next != null) "PRÓXIMO" else "SIN PRISA", style = Type.sans(12.sp, FontWeight.Bold, 0.12.sp), color = c.noteInk)
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(c.noteInk.copy(alpha = 0.1f)).padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        if (next != null) Dates.countdown(next.date, next.start, now) else "Semana despejada",
                        style = Type.sans(12.5.sp, FontWeight.SemiBold), color = c.noteInk,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Text(
                next?.title ?: "Añade algo que apetezca",
                style = Type.serif(25.sp), color = c.noteInk,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (next != null) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).border(1.3.dp, c.noteInk.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { LucideIcon(LucideData.ArrowUpRight, c.noteInk, 18.dp) }
            }
        }
        // Cinta adhesiva
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 0.dp)
                .width(78.dp)
                .height(14.dp)
                .rotate(-2f)
                .background(c.paper4.copy(alpha = 0.55f)),
        )
    }
}
