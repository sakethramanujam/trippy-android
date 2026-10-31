package samalabs.trippy.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import samalabs.trippy.BuildConfig

private val Context.settings: DataStore<Preferences> by preferencesDataStore(name = "trippy")

/** Server URL, tokens and the signed-in profile, persisted in DataStore and mirrored in memory. */
data class Session(
    val server: String,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val profile: Profile? = null,
) {
    val signedIn: Boolean get() = accessToken != null && profile != null

    /** Cache partition: one per server and user, so accounts never see each other's data. */
    val scope: String get() = "$server#${profile?.id ?: 0}"
}

/** What [ApiClient] needs from the session; lets tests run without Android. */
interface TokenSource {
    val current: Session
    suspend fun updateTokens(access: String, refresh: String?)
}

class SessionStore(private val context: Context) : TokenSource {
    private object Keys {
        val server = stringPreferencesKey("server")
        val access = stringPreferencesKey("access_token")
        val refresh = stringPreferencesKey("refresh_token")
        val profile = stringPreferencesKey("profile")
        val inviteCode = stringPreferencesKey("invite_code")
    }

    private val _session = MutableStateFlow<Session?>(null)
    /** Null until [load] finishes. */
    val session: StateFlow<Session?> = _session.asStateFlow()
    override val current: Session get() = _session.value ?: Session(BuildConfig.DEFAULT_SERVER)

    suspend fun load(): Session {
        val p = context.settings.data.first()
        val s = Session(
            server = p[Keys.server] ?: BuildConfig.DEFAULT_SERVER,
            accessToken = p[Keys.access],
            refreshToken = p[Keys.refresh],
            profile = p[Keys.profile]?.let { runCatching { TrippyJson.decodeFromString<Profile>(it) }.getOrNull() },
        )
        _session.value = s
        return s
    }

    suspend fun setServer(url: String) {
        context.settings.edit { it[Keys.server] = url }
        _session.value = current.copy(server = url)
    }

    suspend fun signIn(auth: AuthResponse, profile: Profile) {
        context.settings.edit {
            it[Keys.access] = auth.accessToken
            auth.refreshToken?.let { r -> it[Keys.refresh] = r }
            it[Keys.profile] = TrippyJson.encodeToString(Profile.serializer(), profile)
        }
        _session.value = current.copy(accessToken = auth.accessToken, refreshToken = auth.refreshToken ?: current.refreshToken, profile = profile)
    }

    override suspend fun updateTokens(access: String, refresh: String?) {
        context.settings.edit {
            it[Keys.access] = access
            refresh?.let { r -> it[Keys.refresh] = r }
        }
        _session.value = current.copy(accessToken = access, refreshToken = refresh ?: current.refreshToken)
    }

    suspend fun updateProfile(profile: Profile) {
        context.settings.edit { it[Keys.profile] = TrippyJson.encodeToString(Profile.serializer(), profile) }
        _session.value = current.copy(profile = profile)
    }

    suspend fun signOut() {
        context.settings.edit {
            it.remove(Keys.access); it.remove(Keys.refresh); it.remove(Keys.profile)
        }
        _session.value = Session(current.server)
    }

    suspend fun inviteCode(): String = context.settings.data.first()[Keys.inviteCode].orEmpty()
    suspend fun setInviteCode(code: String) { context.settings.edit { it[Keys.inviteCode] = code } }
}
