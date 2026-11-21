package io.github.carvalhoxx.gecko.presentation.onboarding

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.carvalhoxx.gecko.presentation.components.ByokFieldTrailingVisualState
import io.github.carvalhoxx.gecko.presentation.components.resolveByokFieldTrailingState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val OnbCardBg = Color(0xFF111815)
private val OnbCardBorder = Color(0xFF1E2E27)
private val OnbEmerald = Color(0xFF10B981)
private val OnbEmeraldBright = Color(0xFF34D399)
private val OnbEmeraldSoft = Color(0xFF6EE7B7)
private val OnbCoreBg = Color(0xFF0F291E)
private val OnbAvatarBg = Color(0xFF16221D)
private val OnbInputBg = Color(0xFF0B100E)
private val OnbSubtitleColor = Color(0xFF8E9E96)
private val OnbLabelColor = Color(0xFF9CA8A2)
private val OnbPrivacyColor = Color(0xFF6E8077)

/**
 * Passo 1 de 3 (`ONB_01_Welcome`, id 321 no protótipo FigCraft):
 * - Halo concêntrico pulsante (`InfiniteTransition`) em verde esmeralda com mascote Gecko.
 * - `AvatarPhotoPicker` com `ActivityResultContracts.PickVisualMedia` e alvo de toque >= 48dp.
 * - Input `"Como você quer ser chamado?"` com rótulo imediatamente acima e exclusividade UDF
 *   entre estado em foco (`✕`) e estado validado sem foco (`✓`).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingProfileStep(
    userName: String,
    avatarInitial: String,
    avatarLocalUri: String?,
    onNameChange: (String) -> Unit,
    onAvatarSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    val imeBottomPx = WindowInsets.ime.getBottom(LocalDensity.current)

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onAvatarSelected(uri.toString())
        }
    }

    val openPhotoPicker = {
        runCatching {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    var isNameFocused by remember { mutableStateOf(false) }
    val nameTrailingState = resolveByokFieldTrailingState(
        isFocused = isNameFocused,
        value = userName,
        isValidated = userName.trim().isNotEmpty()
    )

    LaunchedEffect(isNameFocused, imeBottomPx) {
        if (isNameFocused) {
            bringIntoViewRequester.bringIntoView()
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
            .testTag("onboarding_step_1_profile"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        GeckoPulsingHaloHero()

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Bem-vindo ao Gecko",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Seu diário e planejador inteligente de jogos.",
            color = OnbSubtitleColor,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding_profile_card"),
            shape = RoundedCornerShape(28.dp),
            color = OnbCardBg,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.2.dp, OnbCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(108.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(OnbAvatarBg)
                            .border(width = 1.8.dp, color = OnbEmeraldBright, shape = CircleShape)
                            .clickable { openPhotoPicker() }
                            .testTag("onboarding_avatar_circle"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!avatarLocalUri.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(Uri.parse(avatarLocalUri))
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Foto de perfil selecionada",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .testTag("onboarding_avatar_image")
                            )
                        } else {
                            Text(
                                text = avatarInitial,
                                color = OnbEmeraldSoft,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        onClick = { openPhotoPicker() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .size(48.dp)
                            .testTag("onboarding_avatar_picker_button"),
                        shape = CircleShape,
                        color = Color.Transparent,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(color = OnbEmerald, shape = CircleShape)
                                    .border(width = 2.dp, color = OnbCardBg, shape = CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "📷",
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (!avatarLocalUri.isNullOrBlank()) {
                        "Toque na foto para alterar"
                    } else {
                        "Adicionar foto de perfil (opcional)"
                    },
                    color = OnbEmeraldSoft,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(bringIntoViewRequester),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Como você quer ser chamado?",
                        color = OnbLabelColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("onboarding_name_label")
                    )

                    OutlinedTextField(
                        value = userName,
                        onValueChange = onNameChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp)
                            .onFocusChanged { focusState ->
                                isNameFocused = focusState.isFocused
                                if (focusState.isFocused) {
                                    coroutineScope.launch {
                                        delay(120)
                                        bringIntoViewRequester.bringIntoView()
                                        scrollState.animateScrollTo(scrollState.maxValue)
                                    }
                                }
                            }
                            .testTag("onboarding_name_field"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        ),
                        shape = RoundedCornerShape(12.dp),
                        placeholder = {
                            Text(
                                text = "Seu nome ou apelido",
                                color = OnbPrivacyColor,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        trailingIcon = {
                            when (nameTrailingState) {
                                ByokFieldTrailingVisualState.FOCUSED_CLEARABLE -> {
                                    Surface(
                                        onClick = { onNameChange("") },
                                        modifier = Modifier
                                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                            .testTag("onboarding_name_clear_button"),
                                        color = Color.Transparent,
                                        shape = CircleShape
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "✕",
                                                color = OnbSubtitleColor,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                ByokFieldTrailingVisualState.UNFOCUSED_VALIDATED -> {
                                    Box(
                                        modifier = Modifier
                                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                            .testTag("onboarding_name_valid_icon"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .background(
                                                    color = OnbCoreBg,
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "✓",
                                                color = OnbEmeraldBright,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                ByokFieldTrailingVisualState.NONE -> Unit
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = OnbInputBg,
                            unfocusedContainerColor = OnbInputBg,
                            focusedBorderColor = OnbEmeraldBright,
                            unfocusedBorderColor = Color(0xFF2ECC71).copy(alpha = 0.6f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = OnbEmeraldBright
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "🔒 Salvo apenas no seu aparelho, sem conta em nuvem.",
                        color = OnbPrivacyColor,
                        fontSize = 11.sp,
                        modifier = Modifier.testTag("onboarding_privacy_note")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun GeckoPulsingHaloHero(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "gecko_welcome_halo")
    val outerScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "outer_halo_scale"
    )
    val midAlpha by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mid_halo_alpha"
    )

    Box(
        modifier = modifier
            .size(136.dp)
            .testTag("onboarding_pulsing_halo"),
        contentAlignment = Alignment.Center
    ) {
        // AnimRippleOuter (128.dp)
        Box(
            modifier = Modifier
                .size(128.dp)
                .scale(outerScale)
                .background(
                    color = OnbEmerald.copy(alpha = 0.06f),
                    shape = CircleShape
                )
                .border(
                    width = 1.dp,
                    color = OnbEmerald.copy(alpha = 0.15f),
                    shape = CircleShape
                )
        )

        // AnimRippleMid (96.dp)
        Box(
            modifier = Modifier
                .size(96.dp)
                .alpha(midAlpha + 0.7f)
                .background(
                    color = OnbEmerald.copy(alpha = 0.12f),
                    shape = CircleShape
                )
                .border(
                    width = 1.dp,
                    color = OnbEmeraldBright.copy(alpha = 0.25f),
                    shape = CircleShape
                )
        )

        // Sparkles do protótipo FigCraft
        Text(
            text = "✦",
            color = OnbEmeraldBright,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 10.dp, y = 10.dp)
        )
        Text(
            text = "✦",
            color = OnbEmeraldSoft,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-6).dp, y = 18.dp)
        )
        Text(
            text = "•",
            color = Color(0xFFA7F3D0),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 18.dp, y = (-10).dp)
        )

        // AnimCoreGecko (68.dp)
        Box(
            modifier = Modifier
                .size(68.dp)
                .background(color = OnbCoreBg, shape = CircleShape)
                .border(width = 2.dp, color = OnbEmeraldBright, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🦎",
                fontSize = 30.sp
            )
        }
    }
}
