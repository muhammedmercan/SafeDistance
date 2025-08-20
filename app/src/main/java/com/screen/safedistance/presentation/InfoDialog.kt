package com.screen.safedistance.presentation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun InfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        confirmButton = {
            Button(onClick = { onDismiss() }) {
                Text("Tamam")
            }
        },
        title = { Text("Bilgilendirme") },
        text = {
            Text("Uzun süre ekran kullanımı göz yorgunluğuna ve baş ağrısına neden olabilir.\n\nEkrana çok yakın oturmamaya ve her 20 dakikada bir kısa molalar vererek gözlerinizi dinlendirmeye özen gösterin.")
        }
    )
}