package com.digitalsignage.player

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.ui.PlayerView
import com.digitalsignage.player.cache.MediaCache
import com.digitalsignage.player.data.HttpPlayerApi
import com.digitalsignage.player.data.Settings
import com.digitalsignage.player.engine.EngineState
import com.digitalsignage.player.errors.ErrorMessages
import com.digitalsignage.player.errors.ErrorReporter
import com.digitalsignage.player.engine.PlayerEngine
import com.digitalsignage.player.memory.MemoryProfile
import com.digitalsignage.player.proof.PlayLog
import com.digitalsignage.player.telemetry.Level
import com.digitalsignage.player.telemetry.Telemetry
import com.digitalsignage.player.time.TrustedClock
import com.digitalsignage.player.restart.RestartPolicy
import com.digitalsignage.player.restart.Restarter
import com.digitalsignage.player.sync.ConfigSync
import com.digitalsignage.player.update.UpdateManager
import com.digitalsignage.player.update.UpdateRequestTrigger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tela única do Player. Toda a lógica (registro, sincronização, cache) vive em [PlayerEngine];
 * aqui só se desenha o estado e se toca a playlist.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var settings: Settings
    private lateinit var engine: PlayerEngine
    private lateinit var slideshow: Slideshow
    private var engineJob: Job? = null

    private lateinit var overlay: View
    private lateinit var messageTitle: TextView
    private lateinit var messageBody: TextView
    private lateinit var code: TextView
    private lateinit var serverInfo: TextView
    private lateinit var errorBanner: TextView
    private val reporter = ErrorReporter.shared

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)
        hideSystemUi()

        overlay = findViewById(R.id.overlay)
        messageTitle = findViewById(R.id.messageTitle)
        messageBody = findViewById(R.id.messageBody)
        code = findViewById(R.id.code)
        serverInfo = findViewById(R.id.serverInfo)
        errorBanner = findViewById(R.id.errorBanner)

        settings = Settings(this)
        applyLaunchExtras(intent)
        // Aberto de novo (por alguém, pelo boot ou pelo vigia): apaga a "saída de propósito" e marca que está vivo.
        Restarter.opened(settings)
        Restarter.scheduleWatchdog(this)
        // Voltar fecha o Player de propósito: o vigia espera um tempo antes de trazê-lo de volta.
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Restarter.userLeft(settings)
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })

        val cache = MediaCache(java.io.File(filesDir, "media"))
        // A hora do servidor, mesmo sem internet: a agenda offline (campanhas por horário) depende dela.
        val clock = TrustedClock(uptime = { android.os.SystemClock.elapsedRealtime() }, store = settings)
        // O diário de exibição (comprovação): sobrevive a reinício e a falta de internet; o motor o entrega ao servidor.
        val playLog = PlayLog(
            file = java.io.File(filesDir, "playlog.jsonl"),
            state = settings,
            onDropped = { reporter.report("O diário de exibição ficou cheio sem internet: $it registro(s) mais antigo(s) foram descartados.", 0) },
        )
        val api = HttpPlayerApi(baseUrl = { settings.serverUrl })
        val updates = UpdateManager(applicationContext, settings)
        val updateTrigger = UpdateRequestTrigger(
            lastHandled = { settings.lastUpdateToken },
            remember = { settings.lastUpdateToken = it },
            request = { updates.requestCheck() },
        )
        engine = PlayerEngine(
            api = api,
            sync = ConfigSync(
                api, api, cache, settings,
                freeBytes = { filesDir.usableSpace },
                clock = clock,
                onNotice = { Telemetry.shared.event(Level.WARN, it) },
            ),
            store = settings,
            token = { settings.deviceToken },
            playerVersion = BuildConfig.VERSION_NAME,
            onError = { reporter.report(it) },
            onUpdateCheckRequested = updateTrigger::onHeartbeat,
            plays = playLog,
            clock = clock,
        )
        // O mesmo ConfigSync não é compartilhado com a Activity: o Slideshow só lê o cache.
        slideshow = Slideshow(
            lifecycleScope,
            cache,
            findViewById<ImageView>(R.id.imageView),
            findViewById<PlayerView>(R.id.playerView),
            findViewById<FrameLayout>(R.id.zonesRoot),
            onError = { reporter.report(it) },
            profile = MemoryProfile.detect(this),
            recorder = playLog,
        )
        // Sinal de vida enquanto a tela está aberta: é o que o vigia confere (ver restart/Restarter).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    Restarter.stampAlive(settings)
                    delay(RestartPolicy.ALIVE_EVERY_MS)
                }
            }
        }

        // Toda mensagem de erro aparece por 10 segundos; mensagens em fila aparecem uma depois da outra.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                reporter.messages.collect { message ->
                    errorBanner.text = "⚠  $message"
                    errorBanner.visibility = View.VISIBLE
                    delay(ErrorReporter.DISPLAY_MS)
                    errorBanner.visibility = View.GONE
                }
            }
        }

        // Se o app caiu por um erro inesperado, conta o motivo agora que ele voltou.
        settings.lastCrash?.let {
            reporter.report("O Player foi reiniciado após um erro inesperado ($it).", 0)
            settings.lastCrash = null
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                engine.state.collect { render(it) }
            }
        }
        startEngine()

        // Atualização automática do próprio app pelo site de downloads (sem Google Play).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { updates.run() }
        }
        // O status da atualização muda depois de a tela ser desenhada: mantém o texto da tela de aviso em dia.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(5_000)
                    if (overlay.visibility == View.VISIBLE) serverInfo.text = serverInfoText()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (applyLaunchExtras(intent)) startEngine()
    }

    override fun onStop() {
        super.onStop()
        slideshow.stop()
    }

    /** Início (ou outro app) apertado por alguém: não é queda nem falta de memória, o vigia não deve reabrir já. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        Restarter.userLeft(settings)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        slideshow.trimMemory(level)
    }

    override fun onDestroy() {
        super.onDestroy()
        slideshow.release()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val select = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
        if (select && overlay.visibility == View.VISIBLE) {
            showServerDialog()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun startEngine() {
        engineJob?.cancel()
        engineJob = lifecycleScope.launch(CoroutineExceptionHandler { _, e -> reporter.report(ErrorMessages.describe(e)) }) {
            repeatOnLifecycle(Lifecycle.State.STARTED) { engine.run() }
        }
    }

    private fun render(state: EngineState) {
        when (state) {
            is EngineState.Playing -> {
                overlay.visibility = View.GONE
                slideshow.start(state.config)
            }

            else -> {
                slideshow.stop()
                overlay.visibility = View.VISIBLE
                showMessage(state)
            }
        }
    }

    private fun showMessage(state: EngineState) {
        val (title, body) = when (state) {
            EngineState.Starting -> R.string.starting_title to R.string.starting_body
            EngineState.ConnectionError -> R.string.error_title to R.string.error_body
            is EngineState.WaitingActivation -> R.string.activation_title to R.string.activation_body
            EngineState.Disabled -> R.string.disabled_title to R.string.disabled_body
            EngineState.NoContent -> R.string.no_content_title to R.string.no_content_body
            is EngineState.Playing -> return
        }
        messageTitle.setText(title)
        messageBody.setText(body)

        if (state is EngineState.WaitingActivation) {
            code.text = state.code
            code.visibility = View.VISIBLE
        } else {
            code.visibility = View.GONE
        }
        serverInfo.text = serverInfoText()
    }

    /** Endereço do servidor, versão e (para diagnóstico na TV) o último resultado da atualização automática. */
    private fun serverInfoText(): String =
        getString(R.string.server_info, settings.serverUrl, BuildConfig.VERSION_NAME) +
            (settings.lastUpdateStatus?.let { "\nAtualização: $it" } ?: "")

    private fun showServerDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setText(settings.serverUrl)
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.server_dialog_title)
            .setView(input)
            .setPositiveButton(R.string.server_dialog_ok) { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    settings.serverUrl = url
                    startEngine()
                }
            }
            .setNegativeButton(R.string.server_dialog_cancel, null)
            .show()
    }

    /**
     * Permite configurar via adb sem digitar na TV:
     *   adb shell am start -n com.digitalsignage.player/.MainActivity --es server_url https://meu-servidor
     *   adb shell am start -n com.digitalsignage.player/.MainActivity --ez reset true
     *   adb shell am start -n com.digitalsignage.player/.MainActivity --ez auto_update false
     *   adb shell am start -n com.digitalsignage.player/.MainActivity --es update_window "02:00-05:00"
     */
    private fun applyLaunchExtras(intent: Intent?): Boolean {
        var changed = false
        intent?.getStringExtra("server_url")?.takeIf { it.isNotBlank() }?.let {
            settings.serverUrl = it
            changed = true
        }
        // Configuração da atualização automática (não exige reiniciar o motor).
        intent?.getStringExtra("update_url")?.takeIf { it.isNotBlank() }?.let { settings.updateUrl = it }
        if (intent?.hasExtra("auto_update") == true) settings.autoUpdate = intent.getBooleanExtra("auto_update", true)
        if (intent?.hasExtra("update_window") == true) settings.updateWindow = intent.getStringExtra("update_window").orEmpty()
        if (intent?.getBooleanExtra("reset", false) == true) {
            settings.resetAll()
            changed = true
        }
        return changed
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }

}
