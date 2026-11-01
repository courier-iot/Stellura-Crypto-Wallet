package com.stellura.wallet.stellarite

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.Process
import com.example.stellariteminer.Job
import com.example.stellariteminer.MiningEngine
import com.example.stellariteminer.NebuliteClient
import com.example.stellariteminer.NodeClient
import com.example.stellariteminer.PoolBackend
import com.example.stellariteminer.SoloBackend
import com.stellura.wallet.MainActivity
import com.stellura.wallet.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

/** What the miner is doing, for the Mine screen. */
data class StelMinerStatus(
    val running: Boolean = false,
    val coin: StelCoin = StelCoin.PLSR,
    val solo: Boolean = true,
    val pausedReason: String? = null,
    val hashrate: Double = 0.0,
    val height: Long = -1,
    val accepted: Int = 0,
    val rejected: Int = 0,
    val earnedUnits: Long = 0,
    val message: String = "Idle",
    val work: String = "",          // the useful work being hashed: the route or mission summary
    val startedAt: Long = 0,
)

/** Shared state between the mining service and the UI. */
object StelMiner {
    private val _status = MutableStateFlow(StelMinerStatus())
    val status: StateFlow<StelMinerStatus> = _status.asStateFlow()
    internal fun set(s: StelMinerStatus) { _status.value = s }
    internal val current get() = _status.value

    fun start(context: Context) {
        val i = Intent(context, StelMiningService::class.java).setAction(StelMiningService.ACTION_START)
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
    }

    fun stop(context: Context) {
        context.startService(Intent(context, StelMiningService::class.java).setAction(StelMiningService.ACTION_STOP))
    }

    /** Why mining can't start yet, or null if it can. */
    fun problem(s: StelSettings, coin: StelCoin): String? = when {
        s.address(coin) == null -> "Open the wallet once (unlocked) so it can derive your ${coin.ticker} address."
        !s.solo(coin) -> StelSettings.nodeProblem(s.node(coin))
        else -> null
    }

    fun formatRate(h: Double): String = when {
        h >= 1e9 -> String.format(Locale.US, "%.2f GH/s", h / 1e9)
        h >= 1e6 -> String.format(Locale.US, "%.2f MH/s", h / 1e6)
        h >= 1e3 -> String.format(Locale.US, "%.1f kH/s", h / 1e3)
        else -> String.format(Locale.US, "%.0f H/s", h)
    }
}

/**
 * Mines Pulsarite or Nebulite with the shared Stellarite core (the same engine as the miner
 * app). It runs only as a visible foreground service the person starts and stops, needs no
 * keys (blocks pay to the wallet's public address), and pauses itself when the phone is hot,
 * low on battery, or (by default) unplugged.
 */
class StelMiningService : Service() {

    companion object {
        const val ACTION_START = "com.stellura.wallet.MINE_START"
        const val ACTION_STOP = "com.stellura.wallet.MINE_STOP"
        private const val CHANNEL_ID = "mining"
        private const val NOTIFICATION_ID = 7
        private const val WORK_REFRESH_MS = 10_000L
        private const val MIN_BATTERY_PERCENT = 20
    }

    private var engine: MiningEngine? = null
    private var backend: PoolBackend? = null
    private var controller: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile private var running = false
    private val found = LinkedBlockingQueue<Pair<Job, Long>>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopMining(); stopSelf() }
            ACTION_START -> startMining()
            else -> stopSelf()      // restarted by the system without an explicit start: stay off
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopMining()
        super.onDestroy()
    }

    private fun startMining() {
        createChannel()
        val notification = buildNotification("Starting…")
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIFICATION_ID, notification)
        if (running) return

        val s = StelSettings(this)
        val coin = s.mineCoin
        val problem = StelMiner.problem(s, coin)
        if (problem != null) {
            StelMiner.set(StelMinerStatus(coin = coin, message = problem))
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return
        }
        val address = s.address(coin)!!
        val be: PoolBackend = if (s.solo(coin)) {
            SoloBackend(SoloBackend.chainFor(this, coin.profile), address, s.miningName,
                // Pulsarite routes can cite escape missions from this phone's Nebulite solo chain as scouts
                if (coin == StelCoin.PLSR) { { SoloBackend.chainFor(this, StelCoin.NBLT.profile).blocksSnapshot() } } else null)
        } else when (coin) {
            StelCoin.PLSR -> NodeClient(s.node(coin), address, s.miningName)
            StelCoin.NBLT -> NebuliteClient(s.node(coin), address, s.miningName)
        }

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Stellura:mining").apply { acquire(12 * 60 * 60 * 1000L) }
        val lowPriority = ThreadFactory { r -> Thread { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND); r.run() } }
        found.clear()
        val eng = MiningEngine(s.threads, lowPriority) { job, nonce -> found.offer(job to nonce) }
        engine = eng; backend = be; running = true
        StelMiner.set(StelMinerStatus(running = true, coin = coin, solo = s.solo(coin),
            message = if (s.solo(coin)) "Preparing solo chain…" else "Connecting to node…", startedAt = System.currentTimeMillis()))
        eng.start()
        controller = Thread({ controlLoop(eng, be, s) }, "stellura-mining").apply { start() }
    }

    private fun stopMining() {
        if (!running && engine == null) return
        running = false
        controller?.interrupt()
        engine?.stop(); engine = null
        Thread { backend?.close(); backend = null }.start()
        wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
        StelMiner.set(StelMiner.current.copy(running = false, hashrate = 0.0, pausedReason = null, message = "Stopped"))
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun controlLoop(eng: MiningEngine, node: PoolBackend, s: StelSettings) {
        var lastFetch = 0L; var lastHashes = 0L; var lastTick = System.nanoTime(); var rate = 0.0; var lastNotify = 0L
        while (running) {
            val reason = pauseReason(s.chargingOnly)
            eng.paused = reason != null
            try {
                val solution = found.poll(1, TimeUnit.SECONDS)
                if (!running) break
                if (solution != null) {
                    val (job, nonce) = solution
                    val r = node.submit(job, nonce)
                    val st = StelMiner.current
                    StelMiner.set(if (r.accepted) st.copy(accepted = st.accepted + 1, earnedUnits = st.earnedUnits + job.rewardUnits, message = r.message)
                        else st.copy(rejected = st.rejected + 1, message = "Rejected: ${r.message}"))
                    lastFetch = 0                       // that block is spent: get the next one
                }
                val now = System.currentTimeMillis()
                val needWork = eng.job == null || node.hasNewWork() || eng.exhausted === eng.job || now - lastFetch > WORK_REFRESH_MS
                if (reason == null && needWork) {
                    val job = node.getWork()
                    eng.job = job; lastFetch = now
                    StelMiner.set(StelMiner.current.copy(height = job.height, work = job.work?.summary ?: "",
                        message = if (solution == null) "Mining" else StelMiner.current.message))
                }
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                StelMiner.set(StelMiner.current.copy(message = "Connection problem: ${e.message ?: e.javaClass.simpleName}. Retrying…"))
                eng.job = null
                try { Thread.sleep(5000) } catch (ie: InterruptedException) { break }
            }
            val t = System.nanoTime(); val total = eng.hashes.get(); val sec = (t - lastTick) / 1e9
            if (sec > 0) { val inst = (total - lastHashes) / sec; rate = if (rate == 0.0) inst else rate * 0.7 + inst * 0.3 }
            lastHashes = total; lastTick = t
            StelMiner.set(StelMiner.current.copy(hashrate = if (reason != null) 0.0 else rate, pausedReason = reason))
            val now = System.currentTimeMillis()
            if (now - lastNotify > 5000) {
                lastNotify = now
                val st = StelMiner.current
                val text = reason?.let { "Paused: $it" } ?: "${st.coin.ticker} · ${StelMiner.formatRate(rate)} · ${st.accepted} blocks"
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
            }
        }
    }

    private fun pauseReason(chargingOnly: Boolean): String? {
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val plugged = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else 100
        if (Build.VERSION.SDK_INT >= 29 &&
            getSystemService(PowerManager::class.java).currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) return "phone too hot"
        if (chargingOnly && !plugged) return "not charging"
        if (!plugged && percent < MIN_BATTERY_PERCENT) return "battery low"
        return null
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Mining", NotificationManager.IMPORTANCE_LOW))
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, StelMiningService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mining)
            .setContentTitle("Stellura mining")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_stat_mining), "Stop", stop).build())
            .build()
    }
}
