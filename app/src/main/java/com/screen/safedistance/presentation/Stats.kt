import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.screen.safedistance.presentation.DistanceViewModel

enum class StatRange(val label: String) {
    TODAY("Bugün"),
    WEEK("Bu Hafta"),
    MONTH("Bu Ay")
}


@Composable
fun Stats(viewModel: DistanceViewModel) {
    val today by viewModel.todayStats.collectAsState()
    val weekly by viewModel.weeklyStats.collectAsState()
    val monthly by viewModel.monthlyStats.collectAsState()

    var selectedTab by remember { mutableStateOf(StatRange.TODAY) }

    LaunchedEffect(Unit) {
        viewModel.loadAllStats()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Absolute.SpaceBetween
        ) {
            Text("Uyarı İstatistikleri", style = MaterialTheme.typography.titleLarge,  color = Color.White)

            IconButton(
                onClick = {
                    viewModel.loadAllStats()
                }
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
            }
        }


        Spacer(Modifier.height(10.dp))

        // 🔹 Yan yana seçim (TabRow)
        TabRow(
            selectedTabIndex = StatRange.values().indexOf(selectedTab),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            StatRange.values().forEachIndexed { index, range ->
                Tab(
                    selected = selectedTab == range,
                    onClick = { selectedTab = range },
                    text = { Text(range.label) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 🔹 Seçime göre kart
        val stats = when (selectedTab) {
            StatRange.TODAY -> today
            StatRange.WEEK -> weekly
            StatRange.MONTH -> monthly
        }

        StatCard(
            title = selectedTab.label,
            stats = stats,
            backgroundColor = when (selectedTab) {
                StatRange.TODAY -> Color(0xFF8EA6DB) // mavi
                StatRange.WEEK -> Color(0xFF9FA6B2) // gri-mavi
                StatRange.MONTH -> Color(0xFFC9A0DC) // mor
            }
        )
        Spacer(Modifier.height(16.dp))

        InfoCard()
    }
}

@Composable
fun StatCard(title: String, stats: Pair<Int, Int>, backgroundColor: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFA6A6))
                    Text("Mesafe Uyarıları")
                    Text(
                        "${stats.first}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFB266FF))
                    Text("Ekran Süresi İhlalleri")
                    Text(
                        "${stats.second}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Color.Black.copy(alpha = 0.2f))
            Spacer(Modifier.height(12.dp))


            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Toplam Uyarı: ",
                    //style = MaterialTheme.typography.headlineSmall,
                    //fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(6.dp))

                Text(
                    (stats.first + stats.second).toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
fun InfoCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors =  CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E)
        ),
        shape = RoundedCornerShape(12.dp),
        //elevation = RoundedCornerShape(4)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Info",
                tint = Color(0xFF8E8E93), // icon rengi
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "İstatistikler anlık olarak güncellenir. Yenilemek için sağ üstteki butonu kullanabilirsiniz.",
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }
}

