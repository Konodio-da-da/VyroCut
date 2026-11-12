package com.vyrocut.app

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun VyroCutToolDock(
    activeTool: String,
    onToolSelected: (String) -> Unit,
) {
    val tools = listOf("Edit", "Audio", "Text", "Stickers", "Effects", "Filters", "Ratio", "Canvas", "Adjust")
    Row(
        Modifier.background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tools.forEach { tool ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton(
                    onClick = { onToolSelected(tool) },
                    modifier = Modifier.padding(horizontal = 1.dp)
                ) {
                    Text(
                        tool,
                        color = if (activeTool == tool) Color(0xFF0FE8B8) else Color(0xFF929B98),
                        fontSize = 10.sp,
                        fontWeight = if (activeTool == tool) FontWeight.Bold else FontWeight.Medium
                    )
                }
                if (activeTool == tool) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.padding(horizontal = 12.dp)
                            .padding(horizontal = 10.dp, vertical = 1.dp)
                            .background(Color(0xFF0FE8B8), RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
}
