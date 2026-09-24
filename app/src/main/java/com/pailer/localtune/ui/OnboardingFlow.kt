package com.pailer.localtune.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.pailer.localtune.R
import com.pailer.localtune.player.LocalTuneViewModel
import com.pailer.localtune.ui.theme.PailerIvory
import com.pailer.localtune.ui.theme.PailerRed

// Boas-vindas do primeiro uso (pedido do usuario 24/09/2026): "como podemos te chamar?"
// (obrigatorio) -> "qual sua melhor foto de perfil?" (galeria ou fazer depois) -> aniversario
// (opcional). So depois disso o app segue o fluxo normal (PermissionGate, leitura da biblioteca).
// O nome vai pro painel de ouvintes no Cloudflare (ver ListenerHeartbeat) - avisado na 1a tela.
private enum class OnboardingStep { Name, Photo, Birthday }

@Composable
fun OnboardingFlow(viewModel: LocalTuneViewModel) {
    val profile = viewModel.profileState.value
    var step by rememberSaveable { mutableStateOf(OnboardingStep.Name) }
    var nameDraft by rememberSaveable { mutableStateOf(profile.name) }
    var birthdayDigits by rememberSaveable { mutableStateOf("") }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::importProfilePhoto)
    }

    BackHandler(enabled = step != OnboardingStep.Name) {
        step = OnboardingStep.entries[step.ordinal - 1]
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appBackgroundBrush()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            StepDots(current = step.ordinal, total = OnboardingStep.entries.size)
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    (slideInHorizontally { if (forward) it / 3 else -it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { if (forward) -it / 3 else it / 3 } + fadeOut())
                },
                modifier = Modifier.weight(1f),
                label = "onboarding",
            ) { current ->
                when (current) {
                    OnboardingStep.Name -> NameStep(
                        name = nameDraft,
                        onNameChange = { nameDraft = it.take(40) },
                        onNext = {
                            viewModel.saveOnboardingName(nameDraft)
                            step = OnboardingStep.Photo
                        },
                    )
                    OnboardingStep.Photo -> PhotoStep(
                        name = nameDraft.trim(),
                        profile = profile,
                        onPick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onNext = { step = OnboardingStep.Birthday },
                    )
                    OnboardingStep.Birthday -> BirthdayStep(
                        digits = birthdayDigits,
                        onDigitsChange = { birthdayDigits = it },
                        onFinish = { viewModel.finishOnboarding(formatBirthday(birthdayDigits)) },
                        onSkip = { viewModel.finishOnboarding("") },
                    )
                }
            }
        }
    }
}

@Composable
private fun StepDots(current: Int, total: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(width = if (index == current) 22.dp else 8.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (index <= current) PailerRed else Color.White.copy(alpha = 0.18f)),
            )
        }
    }
}

@Composable
private fun StepHeader(title: String, subtitle: String) {
    Text(
        title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(Modifier.height(8.dp))
    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun NameStep(name: String, onNameChange: (String) -> Unit, onNext: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val canContinue = name.isNotBlank()
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(28.dp))
        Image(
            painter = painterResource(R.drawable.pailer_logo),
            contentDescription = null,
            modifier = Modifier.size(84.dp).clip(RoundedCornerShape(20.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(28.dp))
        StepHeader("Como podemos te chamar?", "É assim que a Pailer FM vai falar com você.")
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Seu nome") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { if (canContinue) onNext() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Seu nome aparece pra equipe da rádio no painel de ouvintes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Button(onClick = onNext, enabled = canContinue, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Continuar")
        }
    }
}

@Composable
private fun PhotoStep(
    name: String,
    profile: com.pailer.localtune.player.UserProfileUiState,
    onPick: () -> Unit,
    onNext: () -> Unit,
) {
    val hasPhoto = profile.photoUri != null
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(28.dp))
        StepHeader(
            "Qual sua melhor foto de perfil, ${name.substringBefore(' ')}?",
            "Ela aparece no seu perfil e no quadro de fotos da rádio.",
        )
        Spacer(Modifier.height(36.dp))
        // key no appliedVersion: o arquivo da foto e sempre o mesmo (profile.jpg), entao sem
        // isso o avatar nao recarrega quando o usuario troca de foto.
        androidx.compose.runtime.key(profile.appliedVersion) {
            ProfileAvatar(
                profile.photoUri,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(148.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onPick),
            )
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onPick, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (hasPhoto) "Trocar foto" else "Escolher da galeria", color = PailerIvory)
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(if (hasPhoto) "Continuar" else "Fazer depois")
        }
    }
}

@Composable
private fun BirthdayStep(
    digits: String,
    onDigitsChange: (String) -> Unit,
    onFinish: () -> Unit,
    onSkip: () -> Unit,
) {
    val formatted = formatBirthday(digits)
    val valid = isValidBirthday(digits)
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(28.dp))
        StepHeader("Quando é seu aniversário?", "Opcional - é só pra rádio lembrar do seu dia.")
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            // Cursor sempre no fim: a barra do dd/mm e inserida aqui, nao digitada.
            value = TextFieldValue(formatted, selection = TextRange(formatted.length)),
            onValueChange = { onDigitsChange(it.text.filter(Char::isDigit).take(4)) },
            label = { Text("Dia e mês") },
            placeholder = { Text("dd/mm") },
            singleLine = true,
            isError = digits.length == 4 && !valid,
            supportingText = if (digits.length == 4 && !valid) {
                { Text("Data inválida") }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (valid) onFinish() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.weight(1f))
        Button(onClick = onFinish, enabled = valid, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Começar a ouvir")
        }
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text("Pular", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// "2409" -> "24/09" (mesmo formato dd/mm que o campo de aniversario do "Meu perfil" usa)
private fun formatBirthday(digits: String): String =
    if (digits.length <= 2) digits else digits.take(2) + "/" + digits.drop(2)

private fun isValidBirthday(digits: String): Boolean {
    if (digits.length != 4) return false
    val day = digits.take(2).toInt()
    val month = digits.drop(2).toInt()
    val maxDay = when (month) {
        2 -> 29
        4, 6, 9, 11 -> 30
        in 1..12 -> 31
        else -> return false
    }
    return day in 1..maxDay
}
