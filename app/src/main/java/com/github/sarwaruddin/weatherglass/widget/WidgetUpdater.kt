package com.github.sarwaruddin.weatherglass.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.github.sarwaruddin.weatherglass.data.Repo
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Keeps the home-screen widgets current. Battery-light by design: the hourly job only exists
 * while at least one widget is placed, needs a network, skips low battery, and the refresh it
 * runs is non-forced (parts younger than 15 minutes aren't re-fetched).
 */
object WidgetUpdater {
    private const val PERIODIC = "weatherglass-widgets-hourly"
    private const val ONCE = "weatherglass-widgets-now"

    /** Ensure the hourly refresh is scheduled if a widget exists (cancelled otherwise). Cheap; call freely. */
    fun schedule(context: Context) {
        val app = context.applicationContext
        val wm = WorkManager.getInstance(app)
        if (!hasWidgets(app)) {
            wm.cancelUniqueWork(PERIODIC)
            return
        }
        val req = PeriodicWorkRequestBuilder<RefreshWorker>(1, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Redraw every placed widget from the disk cache (no network). */
    suspend fun updateAll(context: Context) {
        val app = context.applicationContext
        SmallWidget().updateAll(app)
        WideWidget().updateAll(app)
    }

    /** A widget was placed (or the launcher re-bound widgets): schedule, and fetch once now. */
    internal fun onWidgetsAdded(context: Context) {
        val app = context.applicationContext
        schedule(app)
        val once = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(ONCE, ExistingWorkPolicy.KEEP, once)
    }

    internal fun hasWidgets(context: Context): Boolean {
        val mgr = AppWidgetManager.getInstance(context) ?: return false
        return listOf(SmallWidgetReceiver::class.java, WideWidgetReceiver::class.java).any {
            runCatching { mgr.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }.getOrDefault(false)
        }
    }
}

/** Refreshes the selected place (non-forced) and redraws the widgets. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        if (!WidgetUpdater.hasWidgets(app)) return Result.success()
        val repo = Repo(app)
        val ok = try {
            repo.refresh(WidgetData.selected(repo), force = false) {}
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
        WidgetUpdater.updateAll(app)
        return if (ok || runAttemptCount >= 2) Result.success() else Result.retry()
    }
}
