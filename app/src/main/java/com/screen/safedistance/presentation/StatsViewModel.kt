package com.screen.safedistance.presentation

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class StatsViewModel(private val context: Context) {

    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private fun getKey(eventType: String, date: String) = "${eventType}_$date"

    fun getDailyStats(date: String): Pair<Int, Int> {
        val closeWarnings = prefs.getInt(getKey("closeWarning", date), 0)
        val longLookWarnings = prefs.getInt(getKey("longLookWarning", date), 0)
        return closeWarnings to longLookWarnings
    }

    fun getTodayStats(): Pair<Int, Int> {
        val today = dateFormat.format(Date())
        return getDailyStats(today)
    }

    fun getWeeklyStats(): Pair<Int, Int> {
        val calendar = Calendar.getInstance()
        val today = calendar.time
        var totalClose = 0
        var totalLong = 0

        for (i in 0 until 7) {
            val date = dateFormat.format(calendar.time)
            val stats = getDailyStats(date)
            totalClose += stats.first
            totalLong += stats.second
            calendar.add(Calendar.DAY_OF_YEAR, -1) // bir gün geri git
        }

        return totalClose to totalLong
    }

    fun getMonthlyStats(): Pair<Int, Int> {
        val calendar = Calendar.getInstance()
        var totalClose = 0
        var totalLong = 0

        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

        for (i in 0 until daysInMonth) {
            val date = dateFormat.format(calendar.time)
            val stats = getDailyStats(date)
            totalClose += stats.first
            totalLong += stats.second
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }

        return totalClose to totalLong
    }
}