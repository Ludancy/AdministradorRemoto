package com.example.myapplication

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class Screen2ViewModel : ViewModel() {
    val datosCliente = MutableLiveData<String>()

    fun updateClientData(data: String) {
        datosCliente.postValue(data)
    }
}
