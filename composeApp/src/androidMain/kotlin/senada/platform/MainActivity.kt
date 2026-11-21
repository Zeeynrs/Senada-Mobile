package senada.platform

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import senada.app.SenadaApp

class MainActivity : ComponentActivity() {
    private var sessionConnection: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val playback = AndroidPlayback.get(this).controller
        val library = AndroidLibrary.get(this)
        setContent { SenadaApp(playback, library) }
    }

    override fun onStart() {
        super.onStart()
        // Terhubung ke PlaybackService agar sesi media siap sebelum audio dimulai.
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        sessionConnection = MediaController.Builder(this, token).buildAsync()
    }

    override fun onStop() {
        AndroidPlayback.get(this).controller.saveQueueNow()
        sessionConnection?.let(MediaController::releaseFuture)
        sessionConnection = null
        super.onStop()
    }
}
