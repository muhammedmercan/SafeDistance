package com.screen.safedistance.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun AutoStartPermissionDialog(
    onDismiss: () -> Unit,
    onAllow: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Batarya Optimizasyonu",
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Text(
                "Uygulamanın arka planda düzgün çalışabilmesi ve size güvenli mesafe uyarıları gönderebilmesi için batarya optimizasyonunu kapatmanız önerilir.",
                style = TextStyle(fontSize = 14.sp),
                color = Color.White
            )
        },
        icon = {
            Icon(
                Icons.Default.Notifications,
                contentDescription = null,
                tint = Color(0xFF4CAF50)
            )
        },
        confirmButton = {
            TextButton(
                onClick = onAllow,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFF4CAF50)
                )
            ) {
                Text("İzin Ver", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color.Gray
                )
            ) {
                Text("Daha Sonra")
            }
        },
        containerColor = Color(0xFF222222),
        iconContentColor = Color.White,
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}