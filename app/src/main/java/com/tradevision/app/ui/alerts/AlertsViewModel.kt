package com.tradevision.app.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tradevision.app.data.AlertCondition
import com.tradevision.app.data.PriceAlert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Alerts architecture (Phase 4): local storage; WS-triggered evaluation arrives in a later phase. */
class AlertsViewModel : ViewModel() {

    private val _alerts = MutableStateFlow<List<PriceAlert>>(emptyList())
    val alerts: StateFlow<List<PriceAlert>> = _alerts

    fun addAlert(symbol: String, condition: AlertCondition, price: Double) {
        _alerts.value = _alerts.value + PriceAlert(
            symbol = symbol,
            condition = condition,
            price = price,
        )
    }

    fun toggleAlert(id: String) {
        _alerts.value = _alerts.value.map {
            if (it.id == id) it.copy(active = !it.active) else it
        }
    }

    fun removeAlert(id: String) {
        _alerts.value = _alerts.value.filter { it.id != id }
    }
}
