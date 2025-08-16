package com.screen.safedistance.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.screen.safedistance.widget.ToggleServiceAction

class SafeDistanceGlanceWidget : GlanceAppWidget(

) {
    override suspend fun provideGlance(context: Context, id: GlanceId) {

        provideContent {

            val prefs = currentState<Preferences>()
            val running = prefs[booleanPreferencesKey("service_running")] ?: false

            Column(
                modifier = GlanceModifier.fillMaxSize().padding(16.dp).background(Color.Black),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Safe Distance",
                    style = TextStyle(
                        fontSize = 15.sp,
                        color = ColorProvider(
                            day = Color.White,
                            night = Color.White
                        )
                    )
                )

                Spacer(modifier = GlanceModifier.height(8.dp))

                Button(
                    text = if (running) "Durdur" else "Başlat",
                    onClick =
                        actionRunCallback<ToggleServiceAction>()


                )
            }
        }

    }

}