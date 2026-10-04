package ma.tany.collect.core.ui

import ma.tany.core.network.ApiError
import ma.tany.core.network.ApiResult

/** Screen load state. Content is ALWAYS server data rendered as-is. */
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>

    data class Loaded<T>(val value: T) : LoadState<T>

    data class Failed(val error: ApiError) : LoadState<Nothing>
}

fun <T> ApiResult<T>.toLoadState(): LoadState<T> = when (this) {
    is ApiResult.Success -> LoadState.Loaded(value)
    is ApiResult.Failure -> LoadState.Failed(error)
}
