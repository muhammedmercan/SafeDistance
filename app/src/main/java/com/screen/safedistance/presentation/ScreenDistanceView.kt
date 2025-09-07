package com.screen.safedistance.presentation

import Stats
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ScreenDistanceView(
    viewModel: DistanceViewModel,
    isServiceRunning: Boolean,
    onStartServiceClick: () -> Unit,
    onStopServiceClick: () -> Unit
) {
    val backgroundColor = Color.Black
    val boxColor = Color(0xFF222222)
    val textColor = Color.White

    val distanceThreshold by viewModel.distanceThreshold.collectAsState()
    val intervalSeconds by viewModel.intervalSeconds.collectAsState()
    val stats by viewModel.todayStats.collectAsState()


    val context = LocalContext.current
    val sharedPref = remember {
        context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
    }
    val isFirstRun = remember {
        mutableStateOf(sharedPref.getBoolean("isFirstRun", true))
    }

    val showPermissionErrorDialog = remember {
        mutableStateOf(false)
    }


    if (isFirstRun.value) {
        InfoDialog(
            onDismiss = {
                // dialog kapandığında preference güncelle
                sharedPref.edit().putBoolean("isFirstRun", false).apply()
                isFirstRun.value = false
            }
        )
    }




    Column(
        modifier = Modifier
            .background(backgroundColor)
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Screen Distance",
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = textColor
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Servis Kontrol Butonu
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isServiceRunning) Color(0xFF4CAF50) else Color(0xFFFF5252)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isServiceRunning) "Servis Çalışıyor" else "Servis Durduruldu",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                    Text(
                        text = if (isServiceRunning) "Ekran mesafesi takip ediliyor" else "Takip başlatmak için butona tıklayın",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }

                Button(
                    onClick = {
                        if (isServiceRunning) {
                            onStopServiceClick()
                        } else {
                            onStartServiceClick()
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(24.dp)
                        )
                ) {
                    Text(if (isServiceRunning) "Durdur" else "Başlat")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(10.dp)
        ) {

            Text(
                text = "Ölçüm Yapılabilmesi için Yüzünüzün Algılanması Gerekmektedir.",
                color = textColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 10.dp)
            )

        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "Uyarı Mesafesi: ${distanceThreshold.toInt()} cm", color = Color.White)
        Slider(
            value = distanceThreshold,
            onValueChange = { viewModel.setDistanceThreshold(it) },
            valueRange = 10f..40f,
            steps = 5,
            enabled = !isServiceRunning
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Ölçüm Sıklığı: ${intervalSeconds.toInt()} saniye", color = Color.White)
        Slider(
            value = intervalSeconds,
            onValueChange = { viewModel.setIntervalSeconds(it) },
            valueRange = 3f..30f,
            steps = 8,
            enabled = !isServiceRunning
        )

        Spacer(modifier = Modifier.height(16.dp))


        Stats(viewModel)
    }
}
