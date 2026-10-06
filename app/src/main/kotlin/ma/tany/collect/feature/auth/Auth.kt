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

@HiltViewModel
class PhoneViewModel @Inject constructor(private val auth: CollectAuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(FormState())
    val state: StateFlow<FormState> = _state.asStateFlow()
    private val _codeSent = MutableSharedFlow<Pair<String, String?>>(extraBufferCapacity = 1)
    val codeSent: SharedFlow<Pair<String, String?>> = _codeSent.asSharedFlow()

    fun submit(phone: String) {
        if (_state.value.submitting || phone.isBlank()) return
        _state.value = FormState(submitting = true)
        viewModelScope.launch {
            when (val result = auth.requestOtp(phone)) {
                is ApiResult.Success -> {
                    _state.value = FormState()
                    _codeSent.emit(result.value.phone to result.value.devCode.takeIf { AppEnvironment.mayShowDevCode })
                }
                is ApiResult.Failure -> _state.value = FormState(error = result.error)
            }
        }
    }
}

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
    private val _state = MutableStateFlow(FormState())
    val state: StateFlow<FormState> = _state.asStateFlow()

    fun verify(code: String) {
        if (_state.value.submitting || code.isBlank()) return
        _state.value = FormState(submitting = true)
        viewModelScope.launch {
            when (val result = auth.verifyOtp(phone, code)) {
                is ApiResult.Success -> {
                    result.value.collectPoints.singleOrNull()?.let { preferences.setActivePoint(it.id) }
                    _state.value = FormState()
                }
                is ApiResult.Failure -> _state.value = FormState(error = result.error)
            }
        }
    }

    fun resend() {
        if (_state.value.submitting) return
        viewModelScope.launch {
            val result = auth.requestOtp(phone)
            if (result is ApiResult.Failure) _state.update { it.copy(error = result.error) }
        }
    }
}

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
fun PhoneScreen(onCodeSent: (phone: String, devCode: String?) -> Unit, viewModel: PhoneViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var phone by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(viewModel) { viewModel.codeSent.collect { (normalized, devCode) -> onCodeSent(normalized, devCode) } }
    AuthLayout(stringResource(R.string.auth_phone_title), stringResource(R.string.auth_phone_subtitle), onBack = null) {
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
    AuthLayout(stringResource(R.string.auth_otp_title), stringResource(R.string.auth_otp_subtitle, ltrIsolated(viewModel.phone)), onBack) {
        viewModel.devCode?.let {
            TanyNotice(message = stringResource(R.string.auth_dev_code, ltrIsolated(it)), tone = TanyTone.INFO)
        }
        LtrField(code, { code = it.filter(Char::isDigit) }, stringResource(R.string.auth_otp_label), KeyboardType.NumberPassword, code = true)
        ErrorLine(state.error)
        TanyButton(stringResource(R.string.auth_verify), { viewModel.verify(code) }, enabled = code.isNotBlank(), loading = state.submitting)
        TanyButton(stringResource(R.string.auth_resend), viewModel::resend, style = TanyButtonStyle.TEXT)
    }
}
