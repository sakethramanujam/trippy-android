package samalabs.trippy.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import samalabs.trippy.domain.Fmt

/** A one-time code that lets a placeholder traveler claim their place on a trip (backend `invites.py`). */
@Serializable
data class ApiInviteCode(
    val code: String,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("trip_id") val tripId: Int? = null,
    @SerialName("user_id") val userId: Int? = null,
    val name: String? = null,
) {
    val expiry: String get() = Fmt.parse(expiresAt)?.let { Fmt.monthDay(it) }.orEmpty()
}

@Serializable
data class ApiInviteRedeemed(@SerialName("trip_id") val tripId: Int? = null, val joined: Int = 1)

@Serializable
private data class RedeemRequest(val code: String)

class InviteRepository(private val api: ApiClient) {
    /** Creates or replaces [userId]'s code on [tripId]; the previous code stops working. */
    suspend fun issue(tripId: Int, userId: Int): ApiInviteCode =
        TrippyJson.decodeFromString(ApiInviteCode.serializer(), api.post("/trips/$tripId/travelers/$userId/invite-code"))

    suspend fun redeem(code: String): ApiInviteRedeemed =
        TrippyJson.decodeFromString(ApiInviteRedeemed.serializer(),
            api.post("/invites/redeem", TrippyJson.encodeToString(RedeemRequest.serializer(), RedeemRequest(code.trim()))))
}

object InviteText {
    fun share(tripTitle: String, server: String, invite: ApiInviteCode): String =
        "Join me on $tripTitle in Trippy. Server: $server\nInvite code: ${invite.code} (single use, expires ${invite.expiry})"

    fun joined(n: Int): String = if (n == 1) "You've joined the trip." else "You've joined $n trips."
}
