package com.squarenova.emaanwallpapers.ui.ringtone
import kotlinx.serialization.Serializable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squarenova.emaanwallpapers.data.model.Ringtone
import com.squarenova.emaanwallpapers.data.repository.RingtoneRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RingtoneViewModel : ViewModel() {

    private val _ringtones = MutableStateFlow<List<Ringtone>>(emptyList())
    val ringtones: StateFlow<List<Ringtone>> = _ringtones

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        loadRingtones()
    }

    private fun loadRingtones() {
        viewModelScope.launch {
            _isLoading.value = true

            _ringtones.value = RingtoneRepository.getRingtones()

            _isLoading.value = false
        }
    }
}