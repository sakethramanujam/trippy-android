package samalabs.trippy.auto

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

class TrippyCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        return NextStopScreen(carContext)
    }
}
