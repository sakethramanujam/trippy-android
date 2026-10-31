package samalabs.trippy.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import samalabs.trippy.R
import samalabs.trippy.data.AuthValidation
import samalabs.trippy.ui.LocalContainer
import samalabs.trippy.ui.components.AuthField
import samalabs.trippy.ui.components.FormBanner
import samalabs.trippy.ui.components.InfoBanner
import samalabs.trippy.ui.components.PrimaryButton
import samalabs.trippy.ui.components.TextAction
import samalabs.trippy.ui.theme.TColor
import samalabs.trippy.ui.theme.TType

private enum class Step { Welcome, SignUp, LogIn }

/** Welcome → sign up / log in, with the server row on every screen (like iOS OnboardingFlow). */
@Composable
fun OnboardingFlow() {
    val container = LocalContainer.current
    val status = remember { ServerStatus(container.api, container.sessions) }
    var step by rememberSaveable { mutableStateOf(Step.Welcome) }
    var showServer by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { status.refresh() }
    BackHandler(enabled = step != Step.Welcome) { step = Step.Welcome }

    Box(Modifier.fillMaxSize().background(TColor.canvas)) {
        AnimatedContent(step, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "onboarding") { s ->
            when (s) {
                Step.Welcome -> WelcomeScreen(status, onSignUp = { step = Step.SignUp }, onLogIn = { step = Step.LogIn }, onServer = { showServer = true })
                Step.SignUp -> SignUpScreen(status, onBack = { step = Step.Welcome }, onLogIn = { step = Step.LogIn }, onServer = { showServer = true })
                Step.LogIn -> LogInScreen(status, onBack = { step = Step.Welcome }, onSignUp = { step = Step.SignUp }, onServer = { showServer = true })
            }
        }
        if (showServer) ServerSettingsSheet(onDismiss = { showServer = false }, onSaved = { scope.launch { status.refresh() } })
    }
}

@Composable
fun TrippyWordmark(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Image(painterResource(R.drawable.brand_stamp), null, Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)))
        Text("Trippy", style = TType.title3, color = TColor.textPrimary)
    }
}

@Composable
private fun WelcomeScreen(status: ServerStatus, onSignUp: () -> Unit, onLogIn: () -> Unit, onServer: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(TColor.route.copy(alpha = 0.16f), TColor.route.copy(alpha = 0.04f), Color.Transparent), center = Offset(540f, 420f), radius = 1000f)),
    ) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp).padding(bottom = 12.dp)) {
            TrippyWordmark(Modifier.padding(top = 16.dp))
            Spacer(Modifier.weight(0.6f))
            WelcomeRouteArt(Modifier.fillMaxWidth().height(150.dp))
            Spacer(Modifier.height(32.dp))
            Text("Every trip,\nin one place.", style = TType.display, color = TColor.textPrimary)
            Text(
                "Flights, rental cars, hotels and the money you split, shared with the people you travel with.",
                style = TType.body, color = TColor.textSecondary, modifier = Modifier.padding(top = 12.dp),
            )
            Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FeatureRow(Icons.Filled.Flight, "Import from Calendar, Flighty and TripIt")
                FeatureRow(Icons.Filled.People, "Invite travelers by email")
                FeatureRow(Icons.Filled.CreditCard, "Split costs and settle up")
            }
            Spacer(Modifier.weight(1f))
            PrimaryButton("Create account", onClick = onSignUp)
            Box(Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onLogIn).padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                Text("I already have an account", style = TType.headline, color = TColor.textPrimary)
            }
            ServerRow(status, onServer, Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(TColor.surfaceRaised), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = TColor.textSecondary, modifier = Modifier.size(16.dp))
        }
        Text(text, style = TType.callout, color = TColor.textPrimary.copy(alpha = 0.92f))
    }
}

/** SFO ✈ NRT arc with the plane part-way along, drawn with shapes. */
@Composable
private fun WelcomeRouteArt(modifier: Modifier) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val a = Offset(with(density) { 18.dp.toPx() }, h - with(density) { 30.dp.toPx() })
        val b = Offset(w - with(density) { 18.dp.toPx() }, a.y)
        val c = Offset(w / 2, -h * 0.35f)
        val t = 0.58f
        val pt = Offset(
            (1 - t) * (1 - t) * a.x + 2 * (1 - t) * t * c.x + t * t * b.x,
            (1 - t) * (1 - t) * a.y + 2 * (1 - t) * t * c.y + t * t * b.y,
        )
        Canvas(Modifier.fillMaxSize()) {
            val path = Path().apply { moveTo(a.x, a.y); quadraticTo(c.x, c.y, b.x, b.y) }
            drawPath(path, TColor.textTertiary.copy(alpha = 0.6f), style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 6.dp.toPx()))))
            val measure = PathMeasure().apply { setPath(path, false) }
            val part = Path().also { measure.getSegment(0f, measure.length * t, it, true) }
            drawPath(part, Brush.horizontalGradient(listOf(TColor.route.copy(alpha = 0.3f), TColor.route), startX = a.x, endX = pt.x), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            listOf(a, b).forEach { drawCircle(Color.White, radius = 3.5.dp.toPx(), center = it) }
        }
        val px = with(density) { pt.x.toDp() }
        val py = with(density) { pt.y.toDp() }
        Icon(Icons.Filled.Flight, null, tint = Color.White, modifier = Modifier.offset(px - 12.dp, py - 12.dp).size(24.dp).rotate(90f + 12f))
        val ay = with(density) { a.y.toDp() }
        Text("SFO", style = TType.codeSmall, color = Color.White, modifier = Modifier.offset(4.dp, ay + 8.dp))
        Text("NRT", style = TType.codeSmall, color = Color.White, modifier = Modifier.align(Alignment.TopEnd).offset((-4).dp, ay + 8.dp))
    }
}

@Composable
private fun AuthScaffold(title: String, subtitle: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        Box(Modifier.padding(start = 16.dp, top = 8.dp).size(40.dp).clip(CircleShape).background(TColor.surfaceRaised).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TColor.textPrimary, modifier = Modifier.size(20.dp))
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                Text(title, style = TType.largeTitle, color = TColor.textPrimary)
                Text(subtitle, style = TType.callout, color = TColor.textSecondary)
            }
            content()
        }
    }
}

@Composable
private fun LogInScreen(status: ServerStatus, onBack: () -> Unit, onSignUp: () -> Unit, onServer: () -> Unit) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AuthScaffold("Welcome back", "Log in to see your trips.", onBack) {
        ServerRow(status, onServer)
        error?.let { FormBanner(it) }
        AuthField("Email", email, { email = it }, placeholder = "you@example.com", keyboardType = KeyboardType.Email)
        AuthField("Password", password, { password = it }, placeholder = "Password", secure = true)
        PrimaryButton("Log in", busy = busy, modifier = Modifier.padding(top = 6.dp), onClick = {
            if (!AuthValidation.email(email)) { error = "Enter a valid email address."; return@PrimaryButton }
            if (password.isEmpty()) { error = "Enter your password."; return@PrimaryButton }
            busy = true
            scope.launch {
                try { container.auth.logIn(email, password) } catch (e: Exception) { error = e.message }
                busy = false
            }
        })
        TextAction("New to Trippy? Create an account", onSignUp, Modifier.fillMaxWidth())
    }
}

@Composable
private fun SignUpScreen(status: ServerStatus, onBack: () -> Unit, onLogIn: () -> Unit, onServer: () -> Unit) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var airport by rememberSaveable { mutableStateOf("") }
    var invite by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (invite.isEmpty()) invite = container.sessions.inviteCode() }

    val note = when (status.health?.signup) {
        "invite" -> "This server is invite-only. Ask someone on it to add you to a trip and send you an invite code."
        "closed" -> "Sign-ups are closed on this server. Ask its owner to add your email."
        else -> null
    }
    AuthScaffold(
        "Create account",
        "If someone added you to a trip, enter the invite code they sent you and it shows up as soon as you sign up.",
        onBack,
    ) {
        ServerRow(status, onServer)
        note?.let { InfoBanner(it) }
        error?.let { FormBanner(it) }
        AuthField("Name", name, { name = it }, placeholder = "Your name", capitalization = KeyboardCapitalization.Words)
        AuthField("Email", email, { email = it }, placeholder = "you@example.com", keyboardType = KeyboardType.Email)
        AuthField("Password", password, { password = it }, placeholder = "At least 8 characters", secure = true)
        AuthField("Home airport (optional)", airport, { airport = it.uppercase().take(3) }, placeholder = "SFO", capitalization = KeyboardCapitalization.Characters)
        AuthField(
            if (status.health?.signup == "open" || status.health?.signup == null) "Invite code (optional)" else "Invite code",
            invite, { invite = it.uppercase() }, placeholder = "XXXXX-XXXXX", capitalization = KeyboardCapitalization.Characters,
        )
        PrimaryButton("Create account", busy = busy, modifier = Modifier.padding(top = 6.dp), onClick = {
            val problem = when {
                name.isBlank() -> "Enter your name."
                !AuthValidation.email(email) -> "Enter a valid email address."
                else -> AuthValidation.passwordProblem(password)
                    ?: if (!AuthValidation.airport(airport)) "Home airport is a 3-letter code, like SFO." else null
            }
            if (problem != null) { error = problem; return@PrimaryButton }
            busy = true
            scope.launch {
                try { container.auth.signUp(name, email, password, airport, invite) } catch (e: Exception) { error = e.message }
                busy = false
            }
        })
        TextAction("Already have an account? Log in", onLogIn, Modifier.fillMaxWidth())
    }
}
