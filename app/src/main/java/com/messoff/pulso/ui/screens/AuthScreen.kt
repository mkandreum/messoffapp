package com.messoff.pulso.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messoff.pulso.AppViewModel
import com.messoff.pulso.data.ApiException
import com.messoff.pulso.haptics.Haptic
import com.messoff.pulso.ui.components.Eyebrow
import com.messoff.pulso.ui.components.LogoMark
import com.messoff.pulso.ui.components.press
import com.messoff.pulso.ui.icons.LucideData
import com.messoff.pulso.ui.icons.LucideIcon
import com.messoff.pulso.ui.theme.LocalPulso
import com.messoff.pulso.ui.theme.PulsoFonts
import com.messoff.pulso.ui.theme.Type
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(vm: AppViewModel, notice: String?) {
    val c = LocalPulso.current
    val scope = rememberCoroutineScope()
    var register by rememberSaveable { mutableStateOf(false) }
    var server by rememberSaveable { mutableStateOf(vm.serverUrl) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showServer by rememberSaveable { mutableStateOf(vm.serverUrl.isBlank()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        error = when {
            server.isBlank() -> "Escribe la dirección de tu servidor Pulso."
            register && name.isBlank() -> "Escribe tu nombre."
            !email.contains('@') -> "El email no es válido."
            register && password.length < 8 -> "La contraseña debe tener al menos 8 caracteres."
            register && password != confirm -> "Las contraseñas no coinciden."
            password.isEmpty() -> "Escribe tu contraseña."
            else -> null
        }
        if (error != null) {
            vm.haptics.play(Haptic.Warning)
            if (server.isBlank()) showServer = true
            return
        }
        val url = server.trim().let { if (it.startsWith("http")) it else "https://$it" }
        busy = true
        scope.launch {
            try {
                if (register) vm.register(url, name, email, password) else vm.login(url, email, password)
                vm.haptics.play(Haptic.Success)
            } catch (e: ApiException) {
                vm.haptics.play(Haptic.Error)
                error = e.message
                if (e.code == "NETWORK" || e.code == "BAD_URL" || e.code == "NO_SERVER") showServer = true
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogoMark(44.dp)
            Spacer(Modifier.width(12.dp))
            Text("pulso", style = Type.serif(34.sp, italic = true), color = c.ink)
        }
        Spacer(Modifier.height(22.dp))
        Eyebrow("Agenda personal · edición papel")
        Spacer(Modifier.height(12.dp))
        Text(
            buildAnnotatedString {
                append("Tu semana,\n")
                withStyle(SpanStyle(color = c.accent, fontStyle = FontStyle.Italic, fontFamily = PulsoFonts.Serif)) { append("con pulso.") }
            },
            style = Type.serif(52.sp, lineHeight = 52.sp), color = c.ink, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Un calendario que se siente como papel y responde como una app. Sin ruido, sin reuniones que se pierden y sin colores de dashboard.",
            style = Type.body, color = c.ink2, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(30.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .shadow(28.dp, RoundedCornerShape(26.dp), ambientColor = Color(0x22000000), spotColor = Color(0x44000000))
                .clip(RoundedCornerShape(26.dp))
                .background(c.paper2)
                .border(1.dp, c.line, RoundedCornerShape(26.dp))
                .padding(20.dp),
        ) {
            // Pestañas Entrar / Crear cuenta con indicador deslizante
            BoxWithConstraints(
                Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp)).background(c.paper3).padding(4.dp),
            ) {
                val half = maxWidth / 2
                val x by animateDpAsState(if (register) half else 0.dp, spring(0.75f, Spring.StiffnessMediumLow), label = "tabs")
                Box(Modifier.offset(x = x).size(half, 46.dp).shadow(4.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).background(c.paper2))
                Row(Modifier.fillMaxSize()) {
                    listOf(false to "Entrar", true to "Crear cuenta").forEach { (value, label) ->
                        Box(
                            Modifier.weight(1f).fillMaxSize().press(haptics = vm.haptics) {
                                register = value
                                error = null
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, style = Type.sans(15.sp, FontWeight.SemiBold), color = if (register == value) c.ink else c.ink2)
                        }
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            Eyebrow(if (register) "Empieza en papel" else "Bienvenido de nuevo")
            Spacer(Modifier.height(10.dp))
            Text(if (register) "Crea tu semana." else "Entra en tu semana.", style = Type.serif(36.sp), color = c.ink)
            Spacer(Modifier.height(6.dp))
            Text(
                if (register) "Te dejamos una semana de ejemplo para que no empiece vacía." else "Tus eventos, tus tareas y tu foco. Justo donde los dejaste.",
                style = Type.body, color = c.ink2,
            )

            if (notice != null && error == null) {
                Spacer(Modifier.height(12.dp))
                Text(notice, style = Type.bodySmall, color = c.accent)
            }
            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(error!!, style = Type.sans(14.sp, FontWeight.Medium), color = c.accent)
            }

            AnimatedVisibility(showServer, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Field("Servidor", server, { server = it }, LucideData.Server, "pulso.tu-dominio.com", KeyboardType.Uri)
            }
            AnimatedVisibility(register, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Field("Tu nombre", name, { name = it }, LucideData.User, "Daniel", KeyboardType.Text)
            }
            Field("Email", email, { email = it }, LucideData.Mail, "tu@email.com", KeyboardType.Email)
            Field(
                "Contraseña", password, { password = it }, LucideData.Lock,
                if (register) "Mínimo 8 caracteres" else "Tu contraseña", KeyboardType.Password,
                secret = !showPassword,
                trailing = {
                    Box(Modifier.size(40.dp).press { showPassword = !showPassword }, contentAlignment = Alignment.Center) {
                        LucideIcon(if (showPassword) LucideData.EyeOff else LucideData.Eye, c.ink2, 20.dp)
                    }
                },
                last = !register,
                onDone = ::submit,
            )
            AnimatedVisibility(register, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Field("Repite la contraseña", confirm, { confirm = it }, LucideData.Lock, "Otra vez", KeyboardType.Password, secret = !showPassword, last = true, onDone = ::submit)
            }

            Spacer(Modifier.height(20.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.ink)
                    .press(enabled = !busy, scaleTo = 0.97f, onClick = ::submit),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = c.onInk, strokeWidth = 2.dp)
                } else {
                    LucideIcon(LucideData.Sparkles, c.onInk, 20.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(if (register) "Crear cuenta" else "Entrar", style = Type.sans(17.sp, FontWeight.SemiBold), color = c.onInk)
                    Spacer(Modifier.width(10.dp))
                    LucideIcon(LucideData.ArrowRight, c.onInk, 20.dp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                LucideIcon(LucideData.Server, c.ink3, 16.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (showServer) "Tu servidor Pulso (el de tu Coolify)" else server.removePrefix("https://").removePrefix("http://"),
                    style = Type.sans(13.sp), color = c.ink3,
                    modifier = Modifier.press { showServer = !showServer },
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Al crear una cuenta aceptas que guardemos tus eventos, tareas y sesiones de enfoque para darte el servicio. Nada de publicidad ni terceros.",
                style = Type.sans(12.sp, lineHeight = 17.sp), color = c.ink3, textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text("Pulso · papel, tinta y un poco de bermellón.", style = Type.serif(17.sp, italic = true), color = c.ink3)
        Spacer(Modifier.height(36.dp))
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    icon: List<String>,
    placeholder: String,
    type: KeyboardType,
    secret: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    last: Boolean = false,
    onDone: () -> Unit = {},
) {
    val c = LocalPulso.current
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 16.dp)) {
        Text(label.uppercase(), style = Type.label, color = c.ink2)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .then(if (focused) Modifier.border(4.dp, c.accent.copy(alpha = 0.12f), RoundedCornerShape(18.dp)) else Modifier)
                .padding(if (focused) 2.dp else 0.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(c.paper)
                .border(1.dp, if (focused) c.ink2 else c.line, RoundedCornerShape(16.dp))
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LucideIcon(icon, c.ink2, 20.dp)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text(placeholder, style = Type.sans(16.sp), color = c.ink3)
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = Type.sans(16.sp).copy(color = c.ink),
                    cursorBrush = SolidColor(c.accent),
                    visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = if (last) ImeAction.Done else ImeAction.Next),
                    keyboardActions = KeyboardActions(onDone = { onDone() }),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                )
            }
            trailing?.invoke()
        }
    }
}
