package com.imran.smartstudenthub

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class ReminderClass(
    val id: Int,
    val subject: String,
    val teacher: String,
    val day: String,
    val time: String
)

object ClassReminderManager {

    private const val PREFS = "class_reminders"
    private const val KEY_CLASSES = "classes"

    private const val CHANNEL_ID = "class_notifications_v3"
    private const val ACTION_REMINDER = "SHOW_CLASS_REMINDER"
    private const val EXTRA_CLASS_TIME = "classTimeMillis"

    private const val REMINDER_MINUTES_BEFORE = 10

    private fun triggerKey(id: Int) = "trigger_$id"
    private fun classTimeKey(id: Int) = "class_time_$id"

    private fun addOneWeek(timeMillis: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timeMillis
            add(Calendar.DAY_OF_YEAR, 7)
        }.timeInMillis
    }

    // Save all timetable classes.
    fun saveClasses(
        context: Context,
        classes: List<ReminderClass>
    ) {
        val array = JSONArray()

        classes.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("subject", item.subject)
                    put("teacher", item.teacher)
                    put("day", item.day)
                    put("time", item.time)
                }
            )
        }

        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        ).edit()
            .putString(KEY_CLASSES, array.toString())
            .apply()
    }

    // Load saved timetable classes.
    fun getClasses(context: Context): List<ReminderClass> {
        return try {
            val saved = context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            ).getString(KEY_CLASSES, "[]") ?: "[]"

            val array = JSONArray(saved)

            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)

                    add(
                        ReminderClass(
                            id = item.getInt("id"),
                            subject = item.getString("subject"),
                            teacher = item.getString("teacher"),
                            day = item.getString("day"),
                            time = item.getString("time")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun getDayNumber(day: String): Int? {
        return when (day.trim().lowercase(Locale.US)) {
            "sunday" -> Calendar.SUNDAY
            "monday" -> Calendar.MONDAY
            "tuesday" -> Calendar.TUESDAY
            "wednesday" -> Calendar.WEDNESDAY
            "thursday" -> Calendar.THURSDAY
            "friday" -> Calendar.FRIDAY
            "saturday" -> Calendar.SATURDAY
            else -> null
        }
    }

    // Parse 09:00 AM or 9:00 PM.
    private fun parseClassTime(time: String): Calendar? {
        val formats = listOf("hh:mm a", "h:mm a")

        for (format in formats) {
            try {
                val formatter = SimpleDateFormat(
                    format,
                    Locale.US
                ).apply {
                    isLenient = false
                }

                val parsed = formatter.parse(
                    time.trim().uppercase(Locale.US)
                )

                if (parsed != null) {
                    return Calendar.getInstance().apply {
                        this.time = parsed
                    }
                }
            } catch (_: Exception) {
                // Try next format.
            }
        }

        return null
    }

    // Find the next upcoming weekly class.
    private fun nextClassTime(item: ReminderClass): Long? {
        val targetDay = getDayNumber(item.day) ?: return null
        val parsedTime = parseClassTime(item.time) ?: return null

        val now = Calendar.getInstance()

        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY,
                parsedTime.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE,
                parsedTime.get(Calendar.MINUTE))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val daysUntil =
            (targetDay - now.get(Calendar.DAY_OF_WEEK) + 7) % 7

        target.add(Calendar.DAY_OF_YEAR, daysUntil)

        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_YEAR, 7)
        }

        return target.timeInMillis
    }

    private fun createPendingIntent(
        context: Context,
        item: ReminderClass,
        classTime: Long
    ): PendingIntent {

        val intent = Intent(
            context,
            ReminderReceiver::class.java
        ).apply {
            action = ACTION_REMINDER
            putExtra("id", item.id)
            putExtra("subject", item.subject)
            putExtra("teacher", item.teacher)
            putExtra(EXTRA_CLASS_TIME, classTime)
        }

        return PendingIntent.getBroadcast(
            context,
            item.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )
    }

    // Schedule one reminder for a class.
    private fun scheduleAlarm(
        context: Context,
        item: ReminderClass,
        classTime: Long,
        force: Boolean = false
    ) {
        val prefs = context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )

        val now = System.currentTimeMillis()

        var triggerTime =
            classTime - REMINDER_MINUTES_BEFORE * 60_000L

        // If class is less than 10 minutes away,
        // show its reminder shortly instead of missing it.
        if (triggerTime <= now) {
            triggerTime = now + 5_000L
        }

        val savedClassTime = prefs.getLong(
            classTimeKey(item.id),
            0L
        )

        val savedTrigger = prefs.getLong(
            triggerKey(item.id),
            0L
        )

        if (
            !force &&
            savedClassTime == classTime &&
            savedTrigger > now
        ) {
            return
        }

        val pendingIntent = createPendingIntent(
            context,
            item,
            classTime
        )

        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE)
                    as AlarmManager

        try {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }

        prefs.edit()
            .putLong(classTimeKey(item.id), classTime)
            .putLong(triggerKey(item.id), triggerTime)
            .apply()
    }

    // Schedule a newly added class.
    fun scheduleClass(
        context: Context,
        item: ReminderClass
    ) {
        val classTime = nextClassTime(item) ?: return

        scheduleAlarm(
            context,
            item,
            classTime,
            force = true
        )
    }

    // Cancel a class reminder.
    fun cancelClass(
        context: Context,
        id: Int
    ) {
        val intent = Intent(
            context,
            ReminderReceiver::class.java
        ).apply {
            action = ACTION_REMINDER
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE)
                    as AlarmManager

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()

        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        ).edit()
            .remove(triggerKey(id))
            .remove(classTimeKey(id))
            .apply()
    }

    // Restore reminders for all saved classes.
    fun rescheduleAll(
        context: Context,
        force: Boolean = false
    ) {
        getClasses(context).forEach { item ->
            val classTime = nextClassTime(item)
                ?: return@forEach

            scheduleAlarm(
                context,
                item,
                classTime,
                force
            )
        }
    }

    // Schedule the next weekly reminder.
    private fun scheduleFollowingWeek(
        context: Context,
        item: ReminderClass,
        previousClassTime: Long
    ) {
        var nextTime = addOneWeek(previousClassTime)

        var nextReminder =
            nextTime - REMINDER_MINUTES_BEFORE * 60_000L

        val now = System.currentTimeMillis()

        while (nextReminder <= now) {
            nextTime = addOneWeek(nextTime)

            nextReminder =
                nextTime - REMINDER_MINUTES_BEFORE * 60_000L
        }

        scheduleAlarm(
            context,
            item,
            nextTime,
            force = true
        )
    }

    private fun createNotificationChannel(
        context: Context,
        manager: NotificationManager
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(
                    AudioAttributes.CONTENT_TYPE_SONIFICATION
                )
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Class Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description =
                    "Important weekly university class reminders"

                enableVibration(true)

                vibrationPattern = longArrayOf(
                    0L, 400L, 200L, 400L
                )

                setSound(
                    android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    audioAttributes
                )

                lockscreenVisibility =
                    android.app.Notification.VISIBILITY_PUBLIC
            }

            manager.createNotificationChannel(channel)
        }
    }

    // Display class reminder notification.
    fun showNotification(
        context: Context,
        id: Int,
        subject: String,
        teacher: String,
        classTime: Long = 0L
    ) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        if (
            !NotificationManagerCompat.from(context)
                .areNotificationsEnabled()
        ) {
            return
        }

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager

        createNotificationChannel(context, manager)

        val minutesLeft = if (classTime > 0L) {
            (
                    (classTime - System.currentTimeMillis()) / 60_000L
                    ).coerceAtLeast(0L)
        } else {
            10L
        }

        val message = when {
            classTime <= 0L ->
                "$subject class starts in 10 minutes."

            minutesLeft <= 0L ->
                "$subject class is starting now."

            else ->
                "$subject class starts in approximately $minutesLeft minutes."
        }

        val detail = buildString {
            append(message)

            if (teacher.isNotBlank()) {
                append("\nTeacher: $teacher")
            }
        }

        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Class Reminder 📚")
                .setContentText(message)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(detail)
                )
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(
                    NotificationCompat.VISIBILITY_PUBLIC
                )
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVibrate(
                    longArrayOf(0L, 400L, 200L, 400L)
                )
                .setAutoCancel(true)
                .setOngoing(false)
                .build()

        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // Notification permission may have been revoked.
        }
    }

    // Called when the alarm fires.
    internal fun handleReminder(
        context: Context,
        intent: Intent
    ) {
        val id = intent.getIntExtra("id", 0)

        val subject =
            intent.getStringExtra("subject") ?: "Your class"

        val teacher =
            intent.getStringExtra("teacher") ?: ""

        val previousClassTime = intent.getLongExtra(
            EXTRA_CLASS_TIME,
            0L
        )

        val item = getClasses(context)
            .firstOrNull { it.id == id }

        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        ).edit()
            .remove(triggerKey(id))
            .remove(classTimeKey(id))
            .apply()

        showNotification(
            context,
            id,
            subject,
            teacher,
            previousClassTime
        )

        if (item != null && previousClassTime > 0L) {
            scheduleFollowingWeek(
                context,
                item,
                previousClassTime
            )
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        if (intent.action != "SHOW_CLASS_REMINDER") {
            return
        }

        ClassReminderManager.handleReminder(
            context,
            intent
        )
    }
}

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        when (intent.action) {

            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {

                ClassReminderManager.rescheduleAll(
                    context,
                    force = true
                )
            }
        }
    }
}