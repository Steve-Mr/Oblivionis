package top.maary.oblivionis.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import top.maary.oblivionis.NotificationHelper
import top.maary.oblivionis.NotificationWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit

class NotificationViewModel(application: Application): AndroidViewModel(application) {
    private val workManager = WorkManager.getInstance(application.applicationContext)

    companion object {
        const val UNIQUE_WORK_NAME = "periodicNotification"
    }
    val notificationHelper = NotificationHelper(application.applicationContext)


    fun scheduleNotification(
        date: Int,
        hour: Int,
        minute: Int,
        interval: Long) {
        val initialDelay = calculateInitialDelay(date, hour, minute)

        val workRequest = PeriodicWorkRequestBuilder<NotificationWorker>(interval, TimeUnit.DAYS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, workRequest)
    }

    fun cancelNotification() {
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private fun calculateInitialDelay(date: Int, hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, date)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (timeInMillis < System.currentTimeMillis()) {
                add(Calendar.MONTH, 1)
            }
        }
        return calendar.timeInMillis - System.currentTimeMillis()
    }

//    fun testN() {
//        notificationHelper.sendNotification()
//    }
}