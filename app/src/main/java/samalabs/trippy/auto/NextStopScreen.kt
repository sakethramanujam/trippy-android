package samalabs.trippy.auto

import android.content.Intent
import android.net.Uri
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import samalabs.trippy.data.ApiRentalCar
import samalabs.trippy.data.ApiTrip
import samalabs.trippy.data.TrippyDatabase
import samalabs.trippy.data.TrippyJson
import samalabs.trippy.data.TripsRepository

class NextStopScreen(carContext: CarContext) : Screen(carContext) {
    private var nextStop: NextStop? = null
    private var loaded = false

    init {
        lifecycleScope.launch {
            loadNextStop()
            loaded = true
            invalidate()
        }
    }

    private suspend fun loadNextStop() = withContext(Dispatchers.IO) {
        val db = TrippyDatabase.open(carContext)
        val tripsJson = db.cache().readLatest(TripsRepository.TRIPS)?.body
        val rentalsJson = db.cache().readLatest(TripsRepository.RENTALS)?.body

        val trips = tripsJson?.let {
            runCatching { TrippyJson.decodeFromString(ListSerializer(ApiTrip.serializer()), it) }.getOrNull()
        }.orEmpty()

        val rentals = rentalsJson?.let {
            runCatching { TrippyJson.decodeFromString(ListSerializer(ApiRentalCar.serializer()), it) }.getOrNull()
        }.orEmpty()

        nextStop = NextStop.find(trips, rentals)
    }

    @Suppress("DEPRECATION")
    override fun onGetTemplate(): Template {
        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Refresh")
                    .setOnClickListener {
                        lifecycleScope.launch {
                            loadNextStop()
                            invalidate()
                        }
                    }
                    .build()
            )
            .build()

        if (!loaded) {
            return PaneTemplate.Builder(Pane.Builder().setLoading(true).build())
                .setTitle("Trippy Next Stop")
                .setHeaderAction(Action.APP_ICON)
                .setActionStrip(actionStrip)
                .build()
        }

        val stop = nextStop
            ?: return MessageTemplate.Builder("No upcoming flights, stays, or rentals found in your trips.\nSafe travels!")
                .setTitle("Trippy")
                .setHeaderAction(Action.APP_ICON)
                .setActionStrip(actionStrip)
                .build()

        val pane = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle(stop.title)
                    .addText(stop.subtitle)
                    .addText(stop.timeDescription)
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle(stop.actionLabel)
                    .setOnClickListener { navigateTo(stop.navigationQuery) }
                    .build()
            )
            .build()

        return PaneTemplate.Builder(pane)
            .setTitle("Next Stop")
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(actionStrip)
            .build()
    }

    private fun navigateTo(query: String) {
        val uri = Uri.parse("geo:0,0?q=" + Uri.encode(query))
        val intent = Intent(Intent.ACTION_VIEW, uri).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            carContext.startCarApp(intent)
        } catch (_: Exception) {
            CarToast.makeText(carContext, "Couldn't open navigation", CarToast.LENGTH_SHORT).show()
        }
    }
}
