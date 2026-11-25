package com.github.sarwaruddin.weatherglass.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val tabShape = RoundedCornerShape(14.dp)
private val tabFill = Brush.verticalGradient(listOf(Color(0xB81C1C21), Color(0xD9131316)))
private val tabEdge = Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x14FFFFFF)))

/**
 * The house tab strip: a glass bar with a lit plate and a glossy crimson needle under the
 * selected tab. The plate and needle follow the pager's scroll position in the draw phase, so a
 * swipe only redraws this strip; text colours recompose once per page change.
 */
@Composable
fun GlassTabs(
    titles: List<String>,
    pager: PagerState,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = pager.currentPage
    Row(
        modifier
            .fillMaxWidth()
            .clip(tabShape)
            .border(1.dp, tabEdge, tabShape)
            .drawWithCache {
                val n = titles.size.coerceAtLeast(1)
                val cw = size.width / n
                val inset = 4.dp.toPx()
                val plateR = CornerRadius(10.dp.toPx())
                val barH = 2.5f.dp.toPx()
                val barR = CornerRadius(barH / 2)
                val barW = cw * 0.42f
                val barY = size.height - inset - barH
                val gloss = Color(0x73FFFFFF)
                val plate = Color(0x16FFFFFF)
                val plateEdge = Color(0x22FFFFFF)
                onDrawBehind {
                    drawRect(tabFill)
                    val pos = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, n - 1f)
                    val x = pos * cw
                    drawRoundRect(plate, Offset(x + inset, inset), Size(cw - inset * 2, size.height - inset * 2), plateR)
                    drawRoundRect(
                        plateEdge, Offset(x + inset, inset), Size(cw - inset * 2, 1.dp.toPx()), plateR,
                    )
                    val bx = x + (cw - barW) / 2
                    drawRoundRect(Pal.Crimson, Offset(bx, barY), Size(barW, barH), barR)
                    drawRoundRect(gloss, Offset(bx + barW * 0.1f, barY), Size(barW * 0.8f, barH * 0.4f), barR)
                }
            }
            .selectableGroup(),
    ) {
        titles.forEachIndexed { i, t ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(i) })
                    .padding(horizontal = 2.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    t,
                    style = Type.Body.copy(
                        fontSize = 14.sp,
                        color = if (on) Pal.Text else Pal.Sub,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
