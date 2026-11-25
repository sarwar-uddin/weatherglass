package com.github.sarwaruddin.weatherglass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.sarwaruddin.weatherglass.data.SearchHit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesSheet(
    s: UiState,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
    onSearch: (String) -> Unit,
    onAdd: (SearchHit) -> Unit,
    onRemove: (Int) -> Unit,
    onLocate: () -> Unit,
) {
    var q by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = { onSearch(""); onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Pal.Card,
    ) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding().verticalScroll(rememberScrollState())) {
            Text("PLACES", style = Type.Title, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(12.dp))

            // Search box
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Pal.Bg)
                    .border(1.dp, Pal.Edge, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box0 {
                    if (q.isEmpty()) Text("Search a city…", style = Type.Body.copy(color = Pal.Dim))
                    BasicTextField(
                        q, { q = it; onSearch(it) }, singleLine = true,
                        textStyle = Type.Body, cursorBrush = SolidColor(Pal.Crimson),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (s.searching) CircularProgressIndicator(
                    Modifier.size(16.dp).semantics { contentDescription = "Searching" },
                    color = Pal.Crimson, strokeWidth = 2.dp,
                )
            }

            for (h in s.search) {
                val where = listOf(h.region, h.country).filter { it.isNotBlank() }.joinToString(", ")
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .clickable(onClickLabel = "Add ${h.name}", role = Role.Button) { q = ""; onAdd(h) }
                        .clearAndSetSemantics { contentDescription = "Add ${h.name}, $where" }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(h.name, style = Type.Body)
                        Text(where, style = Type.Small)
                    }
                    Text("+", style = Type.Big.copy(color = Pal.Crimson))
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClick = onLocate)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("◉  ", style = Type.Body.copy(color = Pal.Crimson))
                Text(if (s.locating) "Finding you…" else "Use my location", style = Type.Body)
            }

            Spacer(Modifier.height(8.dp))
            Text("SAVED", style = Type.Title, modifier = Modifier.semantics { heading() })
            s.places.forEachIndexed { i, p ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .selectable(selected = i == s.selected, role = Role.RadioButton) { onSelect(i) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            (if (p.gps) "◉ " else "") + p.name,
                            style = Type.Body.copy(
                                color = if (i == s.selected) Pal.Crimson else Pal.Text,
                                fontWeight = if (i == s.selected) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                        )
                        if (p.region.isNotBlank()) Text(p.region, style = Type.Small)
                    }
                    if (s.places.size > 1) Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(50))
                            .clickable(onClickLabel = "Remove ${p.name}", role = Role.Button) { onRemove(i) }
                            .semantics { contentDescription = "Remove ${p.name}" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✕", style = Type.Body.copy(color = Pal.Sub))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** A Box that takes the row's remaining width (keeps the placeholder under the field). */
@Composable
private fun androidx.compose.foundation.layout.RowScope.Box0(content: @Composable () -> Unit) =
    androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { content() }
