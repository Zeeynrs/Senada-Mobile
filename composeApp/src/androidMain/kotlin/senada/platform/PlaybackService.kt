package senada.platform

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import senada.app.AndroidResourceHolder

/**
 * Menjaga audio tetap berjalan saat layar terkunci atau aplikasi berpindah, serta
 * menampilkan notifikasi media. Media3 menaikkan service ke foreground selama audio diputar.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        AndroidResourceHolder.register(this)
        val builder = MediaSession.Builder(this, AndroidPlayback.get(this).sessionPlayer)
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            builder.setSessionActivity(
                PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        }
        session = builder.build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        AndroidPlayback.get(this).controller.saveQueueNow()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // Pemutar milik proses aplikasi; hanya sesi yang dilepas di sini.
        session?.release()
        session = null
        super.onDestroy()
    }
}
