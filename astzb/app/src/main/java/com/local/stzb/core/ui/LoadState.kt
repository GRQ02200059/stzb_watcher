package com.local.stzb.core.ui

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Content<T>(val value: T, val refreshing: Boolean = false) : LoadState<T>
    data class Empty(val message: String, val actionLabel: String? = null) : LoadState<Nothing>
    data class Error(val message: String, val retryable: Boolean = true) : LoadState<Nothing>
}
