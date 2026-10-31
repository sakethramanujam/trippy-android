package samalabs.trippy.data

/** Log in, sign up and log out. Tokens and profile land in [SessionStore]. */
class AuthRepository(private val api: ApiClient, private val sessions: SessionStore) {
    /** Wipes the offline copy; set by the app container. */
    var onSignedOut: suspend () -> Unit = {}

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
        onSignedOut()
    }

    /** `PATCH /me`; the saved profile replaces the cached one. */
    suspend fun updateProfile(name: String, homeAirport: String) {
        val body = TrippyJson.encodeToString(ProfileUpdate.serializer(), ProfileUpdate(name, homeAirport.trim().uppercase()))
        sessions.updateProfile(TrippyJson.decodeFromString<Profile>(api.patch("/me", body)))
    }

    /** `POST /me/password`: the server signs out other devices and returns fresh tokens for this one. */
    suspend fun changePassword(current: String, new: String) {
        val body = TrippyJson.encodeToString(PasswordChange.serializer(), PasswordChange(current, new))
        val auth = TrippyJson.decodeFromString<AuthResponse>(api.post("/me/password", body))
        val profile = auth.user ?: sessions.current.profile
        if (profile != null) sessions.signIn(auth, profile) else sessions.updateTokens(auth.accessToken, auth.refreshToken)
    }

    /** `DELETE /me {password}`, then forget the session. */
    suspend fun deleteAccount(password: String) {
        api.delete("/me", TrippyJson.encodeToString(PasswordOnly.serializer(), PasswordOnly(password)))
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

@kotlinx.serialization.Serializable
data class ProfileUpdate(val name: String, @kotlinx.serialization.SerialName("home_airport") val homeAirport: String)

@kotlinx.serialization.Serializable
data class PasswordChange(@kotlinx.serialization.SerialName("current_password") val currentPassword: String, @kotlinx.serialization.SerialName("new_password") val newPassword: String)

@kotlinx.serialization.Serializable
data class PasswordOnly(val password: String)

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
