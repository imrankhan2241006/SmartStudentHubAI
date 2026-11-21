package com.imran.smartstudenthub

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import kotlin.concurrent.thread

class AiAssistantActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                AiAssistantScreen()
            }
        }
    }
}

data class ChatMessage(
    val text: String,
    val isUser: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen() {

    val context = LocalContext.current
    val activity = context as Activity

    var question by remember { mutableStateOf("") }

    var selectedNoteId by remember {
        mutableStateOf(
            StudentDataStore.loadNotes(context).firstOrNull()?.id
        )
    }

    var topicMenuExpanded by remember { mutableStateOf(false) }

    var summaryLoading by remember { mutableStateOf(false) }
    var quizLoading by remember { mutableStateOf(false) }
    var chatLoading by remember { mutableStateOf(false) }

    val notes = StudentDataStore.loadNotes(context)

    val selectedTopic = notes.firstOrNull {
        it.id == selectedNoteId
    }

    val selectedTopicName = when {
        selectedNoteId == null -> "All Topics"
        selectedTopic != null -> selectedTopic.title
        else -> "Select Topic"
    }

    val listState = rememberLazyListState()

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                "Hello! I am your Smart Student AI Assistant. Ask me anything about your studies.",
                false
            )
        )
    }

    LaunchedEffect(messages.size, summaryLoading, quizLoading, chatLoading) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun getSelectedNotes(): String {
        val savedNotes = StudentDataStore.loadNotes(context)

        if (savedNotes.isEmpty()) return ""

        val selectedNotes = if (selectedNoteId == null) {
            savedNotes
        } else {
            savedNotes.filter { it.id == selectedNoteId }
        }

        return selectedNotes.joinToString("\n\n") { note ->
            "Topic: ${note.title}\nNotes:\n${note.content}"
        }.trim()
    }

    fun askAI(prompt: String, action: String) {

        when (action) {
            "summary" -> {
                if (summaryLoading) return
                summaryLoading = true
            }

            "quiz" -> {
                if (quizLoading) return
                quizLoading = true
            }

            else -> {
                if (chatLoading) return
                chatLoading = true
            }
        }

        if (prompt.isBlank()) {
            when (action) {
                "summary" -> summaryLoading = false
                "quiz" -> quizLoading = false
                else -> chatLoading = false
            }
            return
        }

        messages.add(ChatMessage(prompt, true))

        if (action == "chat") {
            question = ""
        }

        thread {
            val answer = try {
                GeminiService.generate(prompt)
            } catch (e: Exception) {
                "Something went wrong: ${e.localizedMessage ?: "Please try again."}"
            }

            activity.runOnUiThread {
                messages.add(ChatMessage(answer, false))

                when (action) {
                    "summary" -> summaryLoading = false
                    "quiz" -> quizLoading = false
                    else -> chatLoading = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "AI Study Assistant",
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            "Powered by Gemini AI",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                navigationIcon = {
                    TextButton(
                        onClick = { activity.finish() }
                    ) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {

            Text(
                "Select Study Topic",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(6.dp))

            Box(modifier = Modifier.fillMaxWidth()) {

                OutlinedButton(
                    onClick = { topicMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        selectedTopicName,
                        modifier = Modifier.weight(1f)
                    )
                    Text("▼")
                }

                DropdownMenu(
                    expanded = topicMenuExpanded,
                    onDismissRequest = {
                        topicMenuExpanded = false
                    },
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {

                    DropdownMenuItem(
                        text = { Text("All Topics") },
                        onClick = {
                            selectedNoteId = null
                            topicMenuExpanded = false
                        }
                    )

                    notes.forEach { note ->
                        DropdownMenuItem(
                            text = { Text(note.title) },
                            onClick = {
                                selectedNoteId = note.id
                                topicMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                Button(
                    onClick = {
                        val selectedNotes = getSelectedNotes()

                        if (selectedNotes.isBlank()) {
                            messages.add(
                                ChatMessage(
                                    "Please save some notes in Smart Notes and select a topic first.",
                                    false
                                )
                            )
                        } else {
                            askAI(
                                """
                                You are a helpful student study assistant.

                                Summarize the selected study notes.

                                Requirements:
                                - Use clear headings.
                                - Explain important concepts.
                                - Include important points.
                                - Use simple language.
                                - Do not invent information.

                                SELECTED STUDY NOTES:
                                $selectedNotes
                                """.trimIndent(),
                                "summary"
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !summaryLoading
                ) {
                    if (summaryLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Summarize Notes")
                    }
                }

                Button(
                    onClick = {
                        val selectedNotes = getSelectedNotes()

                        if (selectedNotes.isBlank()) {
                            messages.add(
                                ChatMessage(
                                    "Please save some notes in Smart Notes and select a topic first.",
                                    false
                                )
                            )
                        } else {
                            askAI(
                                """
                                You are a student quiz generator.

                                Create exactly 5 multiple choice questions from the selected notes.

                                Requirements:
                                - Each question must have 4 options (A, B, C, D).
                                - Clearly mark the correct answer.
                                - Add a short explanation.
                                - Use simple language.
                                - Do not ask questions outside the selected notes.

                                SELECTED STUDY NOTES:
                                $selectedNotes
                                """.trimIndent(),
                                "quiz"
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !quizLoading
                ) {
                    if (quizLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Generate Quiz")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                items(messages) { message ->

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (message.isUser) {
                            Arrangement.End
                        } else {
                            Arrangement.Start
                        }
                    ) {

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (message.isUser) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier.fillMaxWidth(0.88f)
                        ) {

                            Text(
                                text = message.text,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }

                if (summaryLoading || quizLoading || chatLoading) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )

                            Text(
                                when {
                                    summaryLoading -> "Summarizing selected notes..."
                                    quizLoading -> "Generating selected topic quiz..."
                                    else -> "AI is thinking..."
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text("Ask your study question...")
                    },
                    shape = RoundedCornerShape(16.dp),
                    maxLines = 3
                )

                Button(
                    onClick = {
                        askAI(question, "chat")
                    },
                    enabled = question.isNotBlank() && !chatLoading,
                    modifier = Modifier.height(54.dp)
                ) {
                    Text("Send")
                }
            }
        }
    }
}