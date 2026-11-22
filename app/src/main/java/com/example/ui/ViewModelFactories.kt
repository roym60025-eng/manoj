package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.AppContainer

class AppViewModelFactory<T : ViewModel>(
    private val creator: () -> T
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM {
        return creator() as VM
    }
}

fun AppContainer.factory(creator: AppContainer.() -> ViewModel): ViewModelProvider.Factory {
    return AppViewModelFactory { this.creator() }
}
