package app.gifwidgets.control

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile "Animaciones": the universal on/off switch for every widget. ACTIVE_TILE (manifest):
 * the system binds the service only when the user taps it or when we ask for a refresh, so it uses no
 * battery while idle.
 */
class AnimationsTileService : TileService() {

    override fun onStartListening() {
        render()
    }

    override fun onClick() {
        ControlActions.toggleMaster(this)
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        tile.state = if (ControlStore(this).load().masterEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
