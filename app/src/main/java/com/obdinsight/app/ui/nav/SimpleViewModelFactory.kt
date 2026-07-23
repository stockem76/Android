package com.obdinsight.app.ui.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** Tiny manual-DI factory - avoids pulling in a DI framework for a handful of ViewModels. */
class SimpleViewModelFactory(private val creator: () -> ViewModel) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = creator() as T
}
