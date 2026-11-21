package com.imran.smartstudenthub

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.imran.smartstudenthub.ui.theme.SmartStudentHubAITheme
import org.json.JSONArray
import org.json.JSONObject

private val Navy = Color(0xFF172554)
private val Purple = Color(0xFF6C63FF)
private val LightBg = Color(0xFFF5F6FA)
private val Green = Color(0xFF16A34A)
private val SoftBlue = Color(0xFFEAF3FF)
private val CardWhite = Color(0xFFFFFFFF)

data class Assignment(
    val id: Int,
    val title: String,
    val dueDate: String,
    val completed: Boolean = false
)

data class StudyNote(
    val id: Int,
    val title: String,
    val content: String
)

data class ClassSchedule(
    val id: Int,
    val subject: String,
    val teacher: String,
    val day: String,
    val time: String
)

object StudentDataStore {
    private const val PREFS = "student_hub_data"

    fun loadAssignments(context: Context): List<Assignment> {
        val result = mutableListOf<Assignment>()
        try {
            val json = context.getSharedPreferences(PREFS, 0)
                .getString("assignments", "[]") ?: "[]"
            val array = JSONArray(json)

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                result.add(
                    Assignment(
                        item.getInt("id"),
                        item.getString("title"),
                        item.getString("dueDate"),
                        item.getBoolean("completed")
                    )
                )
            }
        } catch (_: Exception) {}
        return result
    }

    fun saveAssignments(context: Context, items: List<Assignment>) {
        val array = JSONArray()
        items.forEach {
            array.put(JSONObject().apply {
                put("id", it.id)
                put("title", it.title)
                put("dueDate", it.dueDate)
                put("completed", it.completed)
            })
        }
        context.getSharedPreferences(PREFS, 0).edit()
            .putString("assignments", array.toString()).apply()
    }

    fun loadNotes(context: Context): List<StudyNote> {
        val result = mutableListOf<StudyNote>()
        try {
            val json = context.getSharedPreferences(PREFS, 0)
                .getString("notes", "[]") ?: "[]"
            val array = JSONArray(json)

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                result.add(
                    StudyNote(
                        item.getInt("id"),
                        item.getString("title"),
                        item.getString("content")
                    )
                )
            }
        } catch (_: Exception) {}
        return result
    }

    fun saveNotes(context: Context, items: List<StudyNote>) {
        val array = JSONArray()
        items.forEach {
            array.put(JSONObject().apply {
                put("id", it.id)
                put("title", it.title)
                put("content", it.content)
            })
        }
        context.getSharedPreferences(PREFS, 0).edit()
            .putString("notes", array.toString()).apply()
    }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ClassReminderManager.rescheduleAll(this)

        setContent {
            SmartStudentHubAITheme {
                StudentHubApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ClassReminderManager.rescheduleAll(this)
    }
}

@Composable
fun StudentHubApp() {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf("dashboard") }

    BackHandler(enabled = currentScreen != "dashboard") {
        currentScreen = "dashboard"
    }

    val assignments = remember {
        mutableStateListOf<Assignment>().apply {
            addAll(StudentDataStore.loadAssignments(context))
        }
    }

    val notes = remember {
        mutableStateListOf<StudyNote>().apply {
            addAll(StudentDataStore.loadNotes(context))
        }
    }

    val schedules = remember {
        mutableStateListOf<ClassSchedule>().apply {
            ClassReminderManager.getClasses(context).forEach {
                add(ClassSchedule(it.id, it.subject, it.teacher, it.day, it.time))
            }
        }
    }

    var nextAssignmentId by remember {
        mutableIntStateOf((assignments.maxOfOrNull { it.id } ?: 0) + 1)
    }

    var nextNoteId by remember {
        mutableIntStateOf((notes.maxOfOrNull { it.id } ?: 0) + 1)
    }

    var nextScheduleId by remember {
        mutableIntStateOf((schedules.maxOfOrNull { it.id } ?: 0) + 1)
    }

    val notificationLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            Toast.makeText(
                context,
                if (granted) "Notifications enabled!"
                else "Please allow notifications in Settings.",
                Toast.LENGTH_LONG
            ).show()
        }

    LaunchedEffect(Unit) {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        color = LightBg
    ) {
        when (currentScreen) {

            "assignments" -> AssignmentsScreen(
                assignments = assignments,
                onBack = { currentScreen = "dashboard" },
                onAdd = { title, date ->
                    assignments.add(
                        Assignment(nextAssignmentId++, title, date)
                    )
                    StudentDataStore.saveAssignments(context, assignments)
                },
                onToggle = { id ->
                    val index = assignments.indexOfFirst { it.id == id }
                    if (index != -1) {
                        assignments[index] =
                            assignments[index].copy(
                                completed = !assignments[index].completed
                            )
                        StudentDataStore.saveAssignments(context, assignments)
                    }
                },
                onDelete = { id ->
                    assignments.removeAll { it.id == id }
                    StudentDataStore.saveAssignments(context, assignments)
                }
            )

            "notes" -> SmartNotesScreen(
                notes = notes,
                onBack = { currentScreen = "dashboard" },
                onAdd = { title, content ->
                    notes.add(StudyNote(nextNoteId++, title, content))
                    StudentDataStore.saveNotes(context, notes)
                },
                onDelete = { id ->
                    notes.removeAll { it.id == id }
                    StudentDataStore.saveNotes(context, notes)
                }
            )

            "timetable" -> TimetableScreen(
                schedules = schedules,
                onBack = { currentScreen = "dashboard" },
                onAdd = { subject, teacher, day, time ->
                    val newClass = ClassSchedule(
                        nextScheduleId++, subject, teacher, day, time
                    )
                    schedules.add(newClass)

                    ClassReminderManager.saveClasses(
                        context,
                        schedules.map {
                            ReminderClass(
                                it.id, it.subject, it.teacher, it.day, it.time
                            )
                        }
                    )

                    ClassReminderManager.scheduleClass(
                        context,
                        ReminderClass(
                            newClass.id,
                            newClass.subject,
                            newClass.teacher,
                            newClass.day,
                            newClass.time
                        )
                    )

                    Toast.makeText(
                        context,
                        "Class saved. Reminder set for 10 minutes before class.",
                        Toast.LENGTH_LONG
                    ).show()
                },
                onDelete = { id ->
                    schedules.removeAll { it.id == id }
                    ClassReminderManager.cancelClass(context, id)

                    ClassReminderManager.saveClasses(
                        context,
                        schedules.map {
                            ReminderClass(
                                it.id, it.subject, it.teacher, it.day, it.time
                            )
                        }
                    )
                }
            )

            else -> Dashboard(
                assignmentCount = assignments.size,
                completedCount = assignments.count { it.completed },
                notesCount = notes.size,
                timetableCount = schedules.size,
                onAssignmentsClick = { currentScreen = "assignments" },
                onNotesClick = { currentScreen = "notes" },
                onTimetableClick = { currentScreen = "timetable" },
                onEnableAlarms = {
                    val alarmManager =
                        context.getSystemService(Context.ALARM_SERVICE)
                                as AlarmManager

                    if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        !alarmManager.canScheduleExactAlarms()
                    ) {
                        try {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        } catch (_: Exception) {
                            Toast.makeText(
                                context,
                                "Please enable Alarms in Android Settings.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        val permissionGranted =
                            Build.VERSION.SDK_INT < 33 ||
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED

                        if (!permissionGranted) {
                            notificationLauncher.launch(
                                Manifest.permission.POST_NOTIFICATIONS
                            )
                        } else {
                            ClassReminderManager.rescheduleAll(context)

                            Toast.makeText(
                                context,
                                "All class reminders have been scheduled!",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                onAiAssistantClick = {
                    context.startActivity(
                        Intent(context, AiAssistantActivity::class.java)
                    )
                }
            )
        }
    }
}

/*
 * Loads the existing app launcher icon automatically.
 * No launcher icon filename or resource name is required.
 */
@Composable
fun StudentHubLogo(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val logoBitmap = remember(context) {
        try {
            val drawable = context.packageManager
                .getApplicationIcon(context.packageName)

            val bitmap = Bitmap.createBitmap(
                256,
                256,
                Bitmap.Config.ARGB_8888
            )

            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)

            bitmap.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    if (logoBitmap != null) {
        Image(
            bitmap = logoBitmap,
            contentDescription = "SmartStudentHubAI Logo",
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else {
        Surface(
            modifier = modifier,
            shape = CircleShape,
            color = SoftBlue
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("🎓", fontSize = 38.sp)
            }
        }
    }
}

@Composable
fun Dashboard(
    assignmentCount: Int,
    completedCount: Int,
    notesCount: Int,
    timetableCount: Int,
    onAssignmentsClick: () -> Unit,
    onNotesClick: () -> Unit,
    onTimetableClick: () -> Unit,
    onEnableAlarms: () -> Unit,
    onAiAssistantClick: () -> Unit
) {
    val progress = (completedCount.toFloat() / 5f).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StudentHubLogo(
                modifier = Modifier.size(68.dp)
            )

            Spacer(Modifier.width(12.dp))

            Column {
                Text(
                    "SmartStudentHubAI",
                    color = Navy,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "Your smart learning companion",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        Text(
            "Hello, Student! 👋",
            fontSize = 27.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Navy
        )

        Spacer(Modifier.height(4.dp))

        Text(
            "Ready to make today productive?",
            color = Color.Gray,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(22.dp))

        Card(
            onClick = onAiAssistantClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Navy),
            elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                Text(
                    "✨ YOUR AI STUDY PARTNER",
                    color = Color(0xFFD9D6FF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    "AI Study Assistant 🤖",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    "Study smarter. Learn better. Achieve more.",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    "Chat with AI  •  Summarize Notes  •  Generate Quiz",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(18.dp))

                Surface(
                    shape = RoundedCornerShape(50.dp),
                    color = Color.White.copy(alpha = 0.15f)
                ) {
                    Text(
                        "OPEN AI ASSISTANT  →",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(
                            horizontal = 16.dp,
                            vertical = 10.dp
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(26.dp))

        Text(
            "Study Overview",
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = Navy
        )

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OverviewCard(
                "Assignments",
                assignmentCount.toString(),
                Modifier.weight(1f)
            )
            OverviewCard(
                "Completed",
                completedCount.toString(),
                Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OverviewCard(
                "Smart Notes",
                notesCount.toString(),
                Modifier.weight(1f)
            )
            OverviewCard(
                "Classes",
                timetableCount.toString(),
                Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(26.dp))

        Text(
            "Weekly Progress",
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = Navy
        )

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Your weekly goal",
                        fontWeight = FontWeight.Bold,
                        color = Navy
                    )
                    Text(
                        "${(progress * 100).toInt()}%",
                        color = Purple,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    "Complete 5 assignments this week",
                    color = Color.Gray,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(14.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(9.dp),
                    color = Purple,
                    trackColor = SoftBlue
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    "$completedCount of 5 assignments completed",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "Class Reminders 🔔",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Navy
        )

        Spacer(Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SoftBlue)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Never miss a class!",
                    fontWeight = FontWeight.Bold,
                    color = Navy,
                    fontSize = 16.sp
                )

                Spacer(Modifier.height(5.dp))

                Text(
                    "Get reminders 10 minutes before your scheduled classes.",
                    color = Color.DarkGray,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = onEnableAlarms,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Navy,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        "Enable Class Reminders",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(26.dp))

        Text(
            "Quick Access",
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = Navy
        )

        Spacer(Modifier.height(12.dp))

        QuickAccessItem("📝  My Assignments", onAssignmentsClick)
        QuickAccessItem("📚  Smart Notes", onNotesClick)
        QuickAccessItem("🗓️  Class Timetable", onTimetableClick)

        Spacer(Modifier.height(24.dp))

        Text(
            "Made with ❤️ for Students",
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(Modifier.height(6.dp))

        Text(
            "Developed by Muhammad Imran",
            color = Navy,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(Modifier.height(4.dp))

        Text(
            "© 2026 All Rights Reserved",
            color = Color.Gray,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun OverviewCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                title,
                color = Color.Gray,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                value,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Navy
            )
        }
    }
}

@Composable
fun QuickAccessItem(title: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                color = Navy,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                "→",
                color = Purple,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PageHeader(title: String, onBack: () -> Unit) {
    Button(
        onClick = onBack,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Navy)
    ) {
        Text("← Back to Dashboard", color = Color.White)
    }

    Spacer(Modifier.height(12.dp))

    Text(
        title,
        fontSize = 27.sp,
        fontWeight = FontWeight.Bold,
        color = Navy
    )
}

@Composable
fun AssignmentsScreen(
    assignments: List<Assignment>,
    onBack: () -> Unit,
    onAdd: (String, String) -> Unit,
    onToggle: (Int) -> Unit,
    onDelete: (Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf("") }
    val completed = assignments.count { it.completed }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        PageHeader("My Assignments 📝", onBack)
        Text("Manage your study tasks", color = Color.Gray)
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCard("Total", assignments.size.toString(), Modifier.weight(1f))
            SummaryCard("Done", completed.toString(), Modifier.weight(1f))
            SummaryCard(
                "Left",
                (assignments.size - completed).toString(),
                Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Add New Assignment",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Navy
        )

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Assignment name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = dueDate,
            onValueChange = { dueDate = it },
            label = { Text("Due date (e.g. 15 Nov 2026)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Button(
            onClick = {
                if (title.isNotBlank() && dueDate.isNotBlank()) {
                    onAdd(title.trim(), dueDate.trim())
                    title = ""
                    dueDate = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Purple)
        ) {
            Text("＋ Add Assignment")
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Your Assignments",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Navy
        )

        if (assignments.isEmpty()) {
            Text("No assignments yet. Add your first assignment above.")
        }

        assignments.forEach { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(item.completed, { onToggle(item.id) })
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.title,
                            fontWeight = FontWeight.Bold,
                            color = if (item.completed) Green else Navy
                        )
                        Text(
                            "Due: ${item.dueDate}",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                    TextButton(onClick = { onDelete(item.id) }) {
                        Text("Delete", color = Color.Red)
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, color = Color.Gray, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                value,
                color = Navy,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SmartNotesScreen(
    notes: List<StudyNote>,
    onBack: () -> Unit,
    onAdd: (String, String) -> Unit,
    onDelete: (Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        PageHeader("Smart Notes 📚", onBack)
        Text("Write and organize your study notes.", color = Color.Gray)
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Note title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text("Write your notes here...") },
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            minLines = 4
        )

        Button(
            onClick = {
                if (title.isNotBlank() && content.isNotBlank()) {
                    onAdd(title.trim(), content.trim())
                    title = ""
                    content = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Purple)
        ) {
            Text("＋ Save Note")
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "My Saved Notes (${notes.size})",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Navy
        )

        if (notes.isEmpty()) Text("No notes saved yet.")

        notes.forEach { note ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        note.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Navy
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(note.content, color = Color.DarkGray)
                    TextButton(
                        onClick = { onDelete(note.id) },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Delete Note", color = Color.Red)
                    }
                }
            }
        }
    }
}

@Composable
fun TimetableScreen(
    schedules: List<ClassSchedule>,
    onBack: () -> Unit,
    onAdd: (String, String, String, String) -> Unit,
    onDelete: (Int) -> Unit
) {
    val context = LocalContext.current

    var subject by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("Monday") }
    var time by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    val days = listOf(
        "Monday", "Tuesday", "Wednesday", "Thursday",
        "Friday", "Saturday", "Sunday"
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        PageHeader("Class Timetable 🗓️", onBack)
        Text("Organize your weekly class schedule.", color = Color.Gray)
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = subject,
            onValueChange = { subject = it },
            label = { Text("Subject name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = teacher,
            onValueChange = { teacher = it },
            label = { Text("Teacher name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(day, color = Navy)
                Spacer(Modifier.weight(1f))
                Text("▼", color = Purple)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                days.forEach { selectedDay ->
                    DropdownMenuItem(
                        text = { Text(selectedDay) },
                        onClick = {
                            day = selectedDay
                            expanded = false
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = time,
            onValueChange = { time = it },
            label = { Text("Class time (e.g. 09:00 AM)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Button(
            onClick = {
                if (
                    subject.isNotBlank() &&
                    teacher.isNotBlank() &&
                    time.matches(
                        Regex("""(0?[1-9]|1[0-2]):[0-5][0-9]\s?(AM|PM|am|pm)""")
                    )
                ) {
                    onAdd(
                        subject.trim(),
                        teacher.trim(),
                        day,
                        time.trim().uppercase()
                    )
                    subject = ""
                    teacher = ""
                    time = ""
                } else {
                    Toast.makeText(
                        context,
                        "Enter all details and a valid time, e.g. 09:00 AM",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Purple)
        ) {
            Text("＋ Add Class")
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "My Weekly Schedule (${schedules.size})",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Navy
        )

        if (schedules.isEmpty()) Text("No classes added yet.")

        days.forEach { scheduleDay ->
            val dayClasses = schedules.filter { it.day == scheduleDay }

            if (dayClasses.isNotEmpty()) {
                Text(
                    scheduleDay,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Purple,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                )

                dayClasses.forEach { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                item.subject,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Navy
                            )
                            Text("Teacher: ${item.teacher}", color = Color.DarkGray)
                            Text("Time: ${item.time}", color = Color.DarkGray)
                            TextButton(
                                onClick = { onDelete(item.id) },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Delete Class", color = Color.Red)
                            }
                        }
                    }
                }
            }
        }
    }
}