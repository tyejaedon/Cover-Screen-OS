package com.tyejaedon.coverscreenos.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

/**
 * Shared compact QWERTY keyboard used across the cover-screen UI. It renders three
 * letter rows plus a shift/backspace row and a space + done row. Callers hoist all
 * state; the composable only exposes typed characters and control actions.
 *
 * This is the single canonical implementation used by both the input injection
 * overlay ([com.tyejaedon.coverscreenos.overlay.input.CoverScreenInputInjectionEngine])
 * and the launcher search widget so their key layouts stay consistent.
 */
@Composable
internal fun CoverCompactQwertyKeyboard(
    onChar: (Char) -> Unit,
    onBackspace: () -> Unit,
    onDone: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isShifted by remember { mutableStateOf(false) }

    val row1 = "qwertyuiop".toList()
    val row2 = "asdfghjkl".toList()
    val row3 = "zxcvbnm".toList()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            row1.forEach { char ->
                val targetChar = if (isShifted) char.uppercaseChar() else char
                CoverKeypadButton(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    onClick = { onChar(targetChar) }
                ) {
                    Text(text = targetChar.toString(), fontSize = 14.sp, color = Color.White)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            row2.forEach { char ->
                val targetChar = if (isShifted) char.uppercaseChar() else char
                CoverKeypadButton(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    onClick = { onChar(targetChar) }
                ) {
                    Text(text = targetChar.toString(), fontSize = 14.sp, color = Color.White)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            CoverKeypadButton(
                modifier = Modifier
                    .weight(1.2f)
                    .height(36.dp),
                backgroundColor = if (isShifted) Color(0xFF3B82F6) else Color(0xFF2A2A30),
                onClick = { isShifted = !isShifted }
            ) {
                Text(if (isShifted) "^" else "v", fontSize = 12.sp, color = Color.White)
            }

            row3.forEach { char ->
                val targetChar = if (isShifted) char.uppercaseChar() else char
                CoverKeypadButton(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    onClick = { onChar(targetChar) }
                ) {
                    Text(text = targetChar.toString(), fontSize = 14.sp, color = Color.White)
                }
            }

            CoverKeypadButton(
                modifier = Modifier
                    .weight(1.2f)
                    .height(36.dp),
                backgroundColor = Color(0xFF2A2A30),
                onClick = onBackspace
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Backspace",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            CoverKeypadButton(
                modifier = Modifier
                    .weight(0.85f)
                    .height(44.dp),
                backgroundColor = Color(0xFF2A2A30),
                onClick = onClear
            ) {
                Text(
                    text = "CLR",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF7043)
                )
            }

            CoverKeypadButton(
                modifier = Modifier
                    .weight(2.5f)
                    .height(34.dp),
                backgroundColor = Color(0xFF24242A),
                onClick = { onChar(' ') }
            ) {
                Text("SPACE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            CoverKeypadButton(
                modifier = Modifier
                    .weight(1.2f)
                    .height(34.dp),
                backgroundColor = Color(0xFF10B981),
                onClick = onDone
            ) {
                Text("DONE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun CoverKeypadButton(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF1F1F24),
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

