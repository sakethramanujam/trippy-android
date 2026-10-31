package samalabs.trippy.data

/** Log in, sign up and log out. Tokens and profile land in [SessionStore]. */
class AuthRepository(private val api: ApiClient, private val sessions: SessionStore) {

    suspend fun logIn(email: String, password: String) {
        val body = TrippyJson.encodeToString(LoginRequest.serializer(), LoginRequest(email.trim(), password))
        finish(api.post("/auth/login", body, auth = false))
    }

    suspend fun signUp(name: String, email: String, password: String, homeAirport: String, inviteCode: String) {
        val req = SignupRequest(
            email = email.trim(),
            password = password,
            name = name.trim(),
            homeAirport = homeAirport.trim().uppercase().ifEmpty { null },
            inviteCode = inviteCode.trim().ifEmpty { null },
        )
        finish(api.post("/auth/signup", TrippyJson.encodeToString(SignupRequest.serializer(), req), auth = false))
        sessions.setInviteCode("")
    }

    suspend fun reloadProfile() {
        if (!sessions.current.signedIn) return
        val me = TrippyJson.decodeFromString<Profile>(api.get("/me"))
        sessions.updateProfile(me)
    }

    suspend fun logOut() {
        runCatching {
            sessions.current.refreshToken?.let {
                api.post("/auth/logout", TrippyJson.encodeToString(RefreshRequest.serializer(), RefreshRequest(it)))
            }
        }
        sessions.signOut()
    }

    private suspend fun finish(json: String) {
        val auth = TrippyJson.decodeFromString<AuthResponse>(json)
        val profile = auth.user ?: run {
            sessions.updateTokens(auth.accessToken, auth.refreshToken)
            TrippyJson.decodeFromString<Profile>(api.get("/me"))
        }
        sessions.signIn(auth, profile)
    }
}

/** Client-side checks that match the iOS app, so both reject the same input. */
object AuthValidation {
    fun email(s: String): Boolean {
        val t = s.trim()
        val at = t.indexOf('@')
        return at > 0 && t.substring(at + 1).contains('.') && !t.contains(' ')
    }

    fun passwordProblem(s: String): String? = when {
        s.length < 8 -> "Use at least 8 characters."
        s.length > 256 -> "That password is too long."
        else -> null
    }

    fun airport(s: String): Boolean = s.isEmpty() || (s.length == 3 && s.all { it in 'A'..'Z' || it in 'a'..'z' })
}
