package ma.tany.collect.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ma.tany.collect.R
import ma.tany.collect.core.AppEnvironment
import ma.tany.collect.core.preferences.CollectPreferences
import ma.tany.collect.core.ui.messageRes
import ma.tany.core.designsystem.R as DsR
import ma.tany.core.designsystem.component.TanyButton
import ma.tany.core.designsystem.component.TanyButtonStyle
import ma.tany.core.designsystem.component.TanyIconContainer
import ma.tany.core.designsystem.component.TanyNotice
import ma.tany.core.designsystem.component.TanyTone
import ma.tany.core.designsystem.component.tanyFieldColors
import ma.tany.core.designsystem.format.ltrIsolated
import ma.tany.core.designsystem.theme.TanyTheme
import ma.tany.core.network.ApiError
import ma.tany.core.network.ApiResult
import ma.tany.core.network.CollectAuthRepository
import javax.inject.Inject

data class FormState(val submitting: Boolean = false, val error: ApiError? = null)

/** Server answer to an OTP request, forwarded to the code screen (length and resend delay are never assumed). */
data class CodeSent(val phone: String, val devCode: String?, val codeLength: Int, val resendAfterSeconds: Int)

@HiltViewModel
class PhoneViewModel @Inject constructor(private val auth: CollectAuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(FormState())
    val state: StateFlow<FormState> = _state.asStateFlow()
    private val _codeSent = MutableSharedFlow<CodeSent>(extraBufferCapacity = 1)
    val codeSent: SharedFlow<CodeSent> = _codeSent.asSharedFlow()

    fun submit(phone: String) {
        if (_state.value.submitting || phone.isBlank()) return
        _state.value = FormState(submitting = true)
        viewModelScope.launch {
            when (val result = auth.requestOtp(phone)) {
                is ApiResult.Success -> {
                    _state.value = FormState()
                    val sent = result.value
                    _codeSent.emit(
                        CodeSent(
                            phone = sent.phone,
                            devCode = sent.devCode.takeIf { AppEnvironment.mayShowDevCode },
                            codeLength = sent.codeLength,
                            resendAfterSeconds = sent.resendAfterSeconds,
                        ),
                    )
                }
                is ApiResult.Failure -> _state.value = FormState(error = result.error)
            }
        }
    }
}

/** OTP screen state. Instants are epoch millis of the device clock (countdowns only, never business times). */
data class OtpState(
    val submitting: Boolean = false,
    val error: ApiError? = null,
    /** Resend allowed from this instant (server `resendAfterSeconds`, advisory). */
    val resendAvailableAt: Long = 0,
    /** 429 `otp_too_many_attempts`: verification locked until this instant (server `retryAfterSeconds`). */
    val lockedUntil: Long = 0,
    /** Incremented after a failed verification: the screen clears the typed code. */
    val clearCode: Int = 0,
    val resent: Boolean = false,
)

/**
 * Verifies the merchant OTP. A MERCHANT has exactly one point ⇒ it becomes active immediately; an ADMIN with
 * several points chooses on the point picker. The server re-checks the scope on every request anyway.
 */
@HiltViewModel
class OtpViewModel @Inject constructor(
    private val auth: CollectAuthRepository,
    private val preferences: CollectPreferences,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val phone: String = checkNotNull(savedStateHandle["phone"])
    val devCode: String? = savedStateHandle["devCode"]

    /** Server `codeLength` (6 today) — the field is capped at it and submits itself once complete. */
    val codeLength: Int = savedStateHandle.get<Int>("codeLength")?.takeIf { it > 0 } ?: 6

    internal var now: () -> Long = System::currentTimeMillis

    private val _state = MutableStateFlow(OtpState(resendAvailableAt = now() + (savedStateHandle.get<Int>("resendAfterSeconds") ?: 0) * 1_000L))
    val state: StateFlow<OtpState> = _state.asStateFlow()

    fun verify(code: String) {
        val s = _state.value
        if (s.submitting || code.length != codeLength || now() < s.lockedUntil) return
        _state.update { it.copy(submitting = true, error = null, resent = false) }
        viewModelScope.launch {
            when (val result = auth.verifyOtp(phone, code)) {
                is ApiResult.Success -> {
                    result.value.collectPoints.singleOrNull()?.let { preferences.setActivePoint(it.id) }
                    _state.update { it.copy(submitting = false) }
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        submitting = false,
                        error = result.error,
                        clearCode = it.clearCode + 1,
                        lockedUntil = result.error.retryAfterSeconds()?.let { seconds -> now() + seconds * 1_000L } ?: it.lockedUntil,
                    )
                }
            }
        }
    }

    fun resend() {
        val s = _state.value
        if (s.submitting || now() < s.resendAvailableAt) return
        _state.update { it.copy(submitting = true, error = null, resent = false) }
        viewModelScope.launch {
            when (val result = auth.requestOtp(phone)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        submitting = false,
                        resent = true,
                        clearCode = it.clearCode + 1,
                        resendAvailableAt = now() + result.value.resendAfterSeconds * 1_000L,
                    )
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        submitting = false,
                        error = result.error,
                        resendAvailableAt = result.error.retryAfterSeconds()?.let { seconds -> now() + seconds * 1_000L } ?: it.resendAvailableAt,
                    )
                }
            }
        }
    }
}

/** `retryAfterSeconds` of a 429 (OTP attempts / resend), as given by the server. */
internal fun ApiError.retryAfterSeconds(): Int? = (this as? ApiError.Http)?.takeIf { it.status == 429 }?.detailInt("retryAfterSeconds")

/** Whole seconds left until [until] (ticks every second while positive). */
@Composable
private fun secondsUntil(until: Long, now: () -> Long): Int {
    val left by produceState(initialValue = remaining(until, now()), until) {
        while (true) {
            value = remaining(until, now())
            if (value <= 0) break
            delay(1_000)
        }
    }
    return left
}

internal fun remaining(until: Long, now: Long): Int = if (until <= now) 0 else ((until - now + 999) / 1_000).toInt()

/** Auth canvas: brand mark, large title, explanation, then the form. */
@Composable
private fun AuthLayout(title: String, subtitle: String, onBack: (() -> Unit)?, content: @Composable () -> Unit) {
    val colors = TanyTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .imePadding(),
    ) {
        Box(Modifier.heightIn(min = 56.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(DsR.drawable.ic_tany_back), contentDescription = stringResource(R.string.auth_back), tint = colors.textPrimary)
                }
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TanyIconContainer(DsR.drawable.ic_tany_store, contentDescription = null, container = colors.chrome, tint = colors.onChrome, size = 44.dp)
                Text(stringResource(R.string.auth_brand), style = TanyTheme.typography.headline, color = colors.textPrimary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = TanyTheme.typography.largeTitle, modifier = Modifier.semantics { heading() })
                Text(subtitle, style = TanyTheme.typography.body, color = colors.textMuted)
            }
            content()
        }
    }
}

@Composable
private fun ErrorLine(error: ApiError?) {
    if (error == null) return
    TanyNotice(message = stringResource(error.messageRes()), tone = TanyTone.DANGER)
}

@Composable
private fun LtrField(value: String, onValueChange: (String) -> Unit, label: String, keyboardType: KeyboardType, code: Boolean = false) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            textStyle = if (code) TanyTheme.typography.codeLarge else TanyTheme.typography.headline,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = tanyFieldColors(),
            shape = TanyTheme.radii.large,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun PhoneScreen(onCodeSent: (CodeSent) -> Unit, sessionExpired: Boolean = false, viewModel: PhoneViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var phone by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(viewModel) { viewModel.codeSent.collect { onCodeSent(it) } }
    AuthLayout(stringResource(R.string.auth_phone_title), stringResource(R.string.auth_phone_subtitle), onBack = null) {
        if (sessionExpired && state.error == null) {
            TanyNotice(message = stringResource(R.string.auth_session_expired), tone = TanyTone.INFO)
        }
        LtrField(phone, { phone = it }, stringResource(R.string.auth_phone_label), KeyboardType.Phone)
        ErrorLine(state.error)
        TanyButton(stringResource(R.string.auth_continue), { viewModel.submit(phone) }, enabled = phone.isNotBlank(), loading = state.submitting)
    }
}

/** Success is observed through the session state (the app shell switches to the point picker / main screens). */
@Composable
fun OtpScreen(onBack: () -> Unit, viewModel: OtpViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var code by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.clearCode) { if (state.clearCode > 0) code = "" }
    val lockedSeconds = secondsUntil(state.lockedUntil, viewModel.now)
    val resendSeconds = secondsUntil(state.resendAvailableAt, viewModel.now)
    AuthLayout(stringResource(R.string.auth_otp_title), stringResource(R.string.auth_otp_subtitle, viewModel.codeLength, ltrIsolated(viewModel.phone)), onBack) {
        viewModel.devCode?.let {
            TanyNotice(message = stringResource(R.string.auth_dev_code, ltrIsolated(it)), tone = TanyTone.INFO)
        }
        LtrField(
            code,
            { typed ->
                code = typed.filter(Char::isDigit).take(viewModel.codeLength)
                // Complete code ⇒ verified once, like iOS (the button stays for a retry).
                if (code.length == viewModel.codeLength && typed.length <= viewModel.codeLength) viewModel.verify(code)
            },
            stringResource(R.string.auth_otp_label),
            KeyboardType.NumberPassword,
            code = true,
        )
        when {
            lockedSeconds > 0 -> TanyNotice(message = stringResource(R.string.auth_otp_locked, lockedSeconds), tone = TanyTone.DANGER)
            else -> ErrorLine(state.error)
        }
        if (state.resent && state.error == null) TanyNotice(message = stringResource(R.string.auth_otp_resent), tone = TanyTone.SUCCESS)
        TanyButton(
            stringResource(R.string.auth_verify),
            { viewModel.verify(code) },
            enabled = code.length == viewModel.codeLength && lockedSeconds == 0,
            loading = state.submitting,
        )
        TanyButton(
            if (resendSeconds > 0) stringResource(R.string.auth_resend_in, resendSeconds) else stringResource(R.string.auth_resend),
            viewModel::resend,
            style = TanyButtonStyle.TEXT,
            enabled = resendSeconds == 0 && !state.submitting,
        )
        TanyButton(stringResource(R.string.auth_change_number), onBack, style = TanyButtonStyle.TEXT)
    }
}
