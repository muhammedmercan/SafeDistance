package com.screen.safedistance.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DistanceViewModel(application: Application, ) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("app_prefs", Application.MODE_PRIVATE)

    private val statsViewModel = StatsViewModel(application.applicationContext)

    private val _distanceThreshold = MutableStateFlow(prefs.getFloat("distanceThreshold", 30f))
    val distanceThreshold: StateFlow<Float> get() = _distanceThreshold

    private val _intervalSeconds = MutableStateFlow(prefs.getFloat("intervalSeconds", 3f))
    val intervalSeconds: StateFlow<Float> get() = _intervalSeconds

    fun setDistanceThreshold(value: Float) {
        viewModelScope.launch {
            prefs.edit().putFloat("distanceThreshold", value).apply()
            _distanceThreshold.value = value
        }
    }

    fun setIntervalSeconds(value: Float) {
        viewModelScope.launch {
            prefs.edit().putFloat("intervalSeconds", value).apply()
            _intervalSeconds.value = value
        }
    }

    private val _todayStats = MutableStateFlow(Pair(0, 0))
    val todayStats: StateFlow<Pair<Int, Int>> = _todayStats

    private val _weeklyStats = MutableStateFlow(Pair(0, 0))
    val weeklyStats: StateFlow<Pair<Int, Int>> = _weeklyStats

    private val _monthlyStats = MutableStateFlow(Pair(0, 0))
    val monthlyStats: StateFlow<Pair<Int, Int>> = _monthlyStats

    fun loadAllStats() {
        viewModelScope.launch {
            _todayStats.value = statsViewModel.getTodayStats()
            _weeklyStats.value = statsViewModel.getWeeklyStats()
            _monthlyStats.value = statsViewModel.getMonthlyStats()
        }
    }
}