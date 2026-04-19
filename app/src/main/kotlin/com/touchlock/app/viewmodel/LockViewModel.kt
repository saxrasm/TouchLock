package com.touchlock.app.viewmodel

import android.content.Context
import android.os.CountDownTimer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.touchlock.app.data.SettingsRepository
import com.touchlock.app.services.LockOverlayService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * LockViewModel — Phase 3 (Task 3.3)
 *
 * Single source of truth for all UI state on MainActivity.
 * Reads/writes settings via SettingsRepository (DataStore).
 * Uses CountDownTimer for auto-lock (cancellable).
 */
class LockViewModel : ViewModel() {

    // ── Lock state ────────────────────────────────────────────────────────────
    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    // ── Auto-lock countdown remaining (seconds, 0 = not running) ─────────────
    private val _countdownSeconds = MutableStateFlow(0)
    val countdownSeconds: StateFlow<Int> = _countdownSeconds.asStateFlow()

    // Synced from SettingsRepository so MainActivity can show current values
    private val _autoLockDelay = MutableStateFlow(0)
    val autoLockDelay: StateFlow<Int> = _autoLockDelay.asStateFlow()

    private val _showLockMessage = MutableStateFlow(true)
    val showLockMessage: StateFlow<Boolean> = _showLockMessage.asStateFlow()

    private var autoLockTimer: CountDownTimer? = null
    private var repository: SettingsRepository? = null

    // ── Initialise with repository ────────────────────────────────────────────
    fun init(repo: SettingsRepository) {
        repository = repo
        viewModelScope.launch {
            _autoLockDelay.value = repo.autoLockDelay.first()
            _showLockMessage.value = repo.showLockMessage.first()
        }
    }

    // ── Called by LockOverlayService broadcasts to keep ViewModel in sync ─────
    fun syncLockState(locked: Boolean) {
        _isLocked.value = locked
        if (!locked) cancelAutoLockTimer()
    }

    // ── Toggle called from MainActivity lock button ───────────────────────────
    fun toggleLock(context: Context) {
        if (LockOverlayService.isLocked) {
            LockOverlayService.stopLock(context)
            _isLocked.value = false
            cancelAutoLockTimer()
        } else {
            LockOverlayService.startLock(context)
            _isLocked.value = true
            val delay = _autoLockDelay.value
            if (delay > 0) startAutoLockTimer(context, delay)
        }
    }

    // ── Auto-lock timer ───────────────────────────────────────────────────────
    fun startAutoLockTimer(context: Context, delaySec: Int) {
        cancelAutoLockTimer()
        _countdownSeconds.value = delaySec
        autoLockTimer = object : CountDownTimer(delaySec * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                _countdownSeconds.value = (millisUntilFinished / 1000).toInt()
            }

            override fun onFinish() {
                _countdownSeconds.value = 0
                if (!LockOverlayService.isLocked) {
                    LockOverlayService.startLock(context)
                    _isLocked.value = true
                }
            }
        }.start()
    }

    fun cancelAutoLockTimer() {
        autoLockTimer?.cancel()
        autoLockTimer = null
        _countdownSeconds.value = 0
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onCleared() {
        super.onCleared()
        cancelAutoLockTimer()
    }
}
