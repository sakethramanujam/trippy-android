package samalabs.trippy.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LedgerPerson(
    @SerialName("user_id") val userId: Int,
    val name: String? = null,
    @SerialName("paid_cents") val paidCents: Long = 0,
    @SerialName("share_cents") val shareCents: Long = 0,
    @SerialName("sent_cents") val sentCents: Long = 0,
    @SerialName("received_cents") val receivedCents: Long = 0,
    @SerialName("net_cents") val netCents: Long = 0,
)

@Serializable
data class LedgerDebt(
    @SerialName("from_user_id") val fromUserId: Int,
    @SerialName("from_username") val fromUsername: String? = null,
    @SerialName("to_user_id") val toUserId: Int,
    @SerialName("to_username") val toUsername: String? = null,
    @SerialName("amount_cents") val amountCents: Long = 0,
)

@Serializable
data class LedgerSettlement(
    val id: Int,
    @SerialName("from_user_id") val fromUserId: Int,
    @SerialName("from_username") val fromUsername: String? = null,
    @SerialName("to_user_id") val toUserId: Int,
    @SerialName("to_username") val toUsername: String? = null,
    @SerialName("amount_cents") val amountCents: Long = 0,
    val note: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class TripLedger(
    @SerialName("total_cents") val totalCents: Long = 0,
    val people: List<LedgerPerson> = emptyList(),
    val pairwise: List<LedgerDebt> = emptyList(),
    val simplified: List<LedgerDebt> = emptyList(),
    val settlements: List<LedgerSettlement> = emptyList(),
)

@Serializable
data class SplitValueBody(@SerialName("user_id") val userId: Int, val value: Double)

/** Body for POST /trips/{id}/expenses/split and PUT /expenses/{id}. */
@Serializable
data class ExpenseBody(
    val amount: Double,
    val description: String,
    @SerialName("payer_id") val payerId: Int,
    @SerialName("split_mode") val splitMode: String,
    @SerialName("split_among") val splitAmong: List<Int>,
    val splits: List<SplitValueBody>? = null,
    val currency: String = "USD",
)

@Serializable
data class SettleBody(
    @SerialName("from_user_id") val fromUserId: Int,
    @SerialName("to_user_id") val toUserId: Int,
    val mode: String = "simplified",
    val amount: Double? = null,
)

class MoneyRepository(private val api: ApiClient) {
    suspend fun trip(id: Int): ApiTrip = TrippyJson.decodeFromString(ApiTrip.serializer(), api.get("/trips/$id"))
    suspend fun ledger(tripId: Int): TripLedger = TrippyJson.decodeFromString(TripLedger.serializer(), api.get("/trips/$tripId/ledger"))

    suspend fun saveExpense(tripId: Int, expenseId: Int?, body: ExpenseBody) {
        val json = TrippyJson.encodeToString(ExpenseBody.serializer(), body)
        if (expenseId == null) api.post("/trips/$tripId/expenses/split", json) else api.put("/expenses/$expenseId", json)
    }

    suspend fun deleteExpense(id: Int) { api.delete("/expenses/$id") }

    suspend fun settle(tripId: Int, debt: LedgerDebt, mode: String) {
        api.post("/trips/$tripId/settle", TrippyJson.encodeToString(SettleBody.serializer(), SettleBody(debt.fromUserId, debt.toUserId, mode)))
    }

    suspend fun settleAll(tripId: Int) { api.post("/trips/$tripId/settle-all") }
    suspend fun undoSettlement(id: Int) { api.delete("/settlements/$id") }
}
