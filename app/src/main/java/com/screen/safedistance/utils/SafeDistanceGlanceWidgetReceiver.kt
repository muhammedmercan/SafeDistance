package com.screen.safedistance.utils

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.screen.safedistance.presentation.SafeDistanceGlanceWidget

class SafeDistanceGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SafeDistanceGlanceWidget()
}