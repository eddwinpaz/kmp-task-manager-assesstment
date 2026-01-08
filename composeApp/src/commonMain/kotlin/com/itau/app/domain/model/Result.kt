package com.itau.app.domain.model

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Throwable) : Result<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = (this as? Success)?.data
    fun exceptionOrNull(): Throwable? = (this as? Error)?.exception

    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
    }

    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onError(action: (Throwable) -> Unit): Result<T> {
        if (this is Error) action(exception)
        return this
    }
}

inline fun <T> resultOf(block: () -> T): Result<T> {
    return try {
        Result.Success(block())
    } catch (e: Throwable) {
        Result.Error(e)
    }
}
