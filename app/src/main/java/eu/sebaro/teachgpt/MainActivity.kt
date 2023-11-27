package eu.sebaro.teachgpt

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import eu.sebaro.teachgpt.ui.theme.TeachGPTTheme
import android.Manifest
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardColors
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechRecognizerIntent: Intent

    private var messages = mutableStateListOf<Message>()
    private var recognizerBusy = false
    private var selectedTeacher: Teacher? = null
    private lateinit var teacherList: MutableList<Teacher>
    private lateinit var textToSpeech: TextToSpeech


    // In your Activity or Fragment
    private fun requestRecordAudioPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                MY_PERMISSIONS_RECORD_AUDIO
            )
        }

// Rest of your code for handling permissions and speech recognition

    }

    // Handle the permissions result
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            MY_PERMISSIONS_RECORD_AUDIO -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Permission was granted, you can start speech recognition
                } else {
                    // Permission denied, you cannot use speech recognition
                }
                return
            }
            // Other 'when' lines to check for other permissions your app might request
        }
    }

    companion object {
        const val MY_PERMISSIONS_RECORD_AUDIO = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Teachers().getTeachers(this)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // Start speech recognition
        } else {
            // Request permission
            requestRecordAudioPermission()
        }
        textToSpeech = TextToSpeech(this, this)

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        speechRecognizerIntent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de")

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                recognizerBusy = false
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onBeginningOfSpeech() {
                recognizerBusy = true
            }

            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                log("error: $error")
                recognizerBusy = false
                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    speechRecognizer.cancel()
                    // Optionally, add a delay before restarting or alerting the user
                }
                Toast.makeText(applicationContext, "Error $error", Toast.LENGTH_SHORT).show()
            }

            override fun onResults(results: Bundle?) {

                recognizerBusy = false // Set microphone to off
                val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                data?.let { array ->
                    if (array.isNotEmpty()) {
                        val inputText = array[0].toString().replaceFirstChar { it.uppercase() }
                        // Handle the recognized text here
                        log("onResult Speech: $inputText")
                        addMessage(inputText, false)
//                        Toast.makeText(applicationContext, it[0], Toast.LENGTH_LONG).show()
                        createAnswer(inputText)
                    }
                }
            }
        })

        setContent {
            TeachGPTTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MyApp(messages)
                }
            }
        }
    }

    fun createAnswer(question: String) {
        val request = OpenApi.createChatCompletionRequest(selectedTeacher!!, question)
        lifecycleScope.launch {
            log("start call: $request")
            val response = OpenApi.makeCall(request)
            log("response: ${response.choices[0].message.content}")
            speakOut(response.choices[0].message.content.toString())
            addMessage(response.choices[0].message.content.toString(), true)
            recognizerBusy = false
        }
    }

    @Composable
    fun TeacherList(teachers: List<Teacher>, onTeacherSelected: (Teacher) -> Unit) {
        LazyColumn {
            items(teachers) { teacher ->
                TeacherRow(teacher, onTeacherClick = { onTeacherSelected(teacher) })
            }
        }
    }

    @Composable
    fun TeacherRow(teacher: Teacher, onTeacherClick: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth() // Fill the max width of the parent
                .background(Color.Cyan)
                .padding(32.dp) // Add padding around the row
                .clickable(onClick = onTeacherClick),
            horizontalArrangement = Arrangement.Start, // Center content horizontally
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painterResource(id = teacher.avatar),
                contentDescription = "Teacher's Avatar",
                Modifier.size(96.dp)
            )
            Text(
                modifier = Modifier
                    .align(Alignment.CenterVertically) // Align text vertically in the center
                    .padding(horizontal = 8.dp), // Optional: Add horizontal padding to text
                text = teacher.name,
                color = Color.Black
            )

        }
        Spacer(
            modifier = Modifier
                .height(2.dp)
                .background(Color.LightGray)
                .padding(2.dp)
                .fillMaxWidth()
        )
    }

    @Composable
    fun MyApp(messages: MutableList<Message>) {
        var currentScreen by remember { mutableStateOf("list") }

        when (currentScreen) {
            "list" -> TeacherList(teacherList, onTeacherSelected = { teacher ->
                selectedTeacher = teacher
                currentScreen = "chat"
            })

            "chat" -> selectedTeacher?.let { teacher ->
                ChatScreen(teacher = teacher, onBack = {
                    currentScreen = "list"
                    selectedTeacher = null
                })
            }
        }
    }

    @Composable
    fun ChatScreen(teacher: Teacher, onBack: () -> Unit) {
        val onMessageSend: (String) -> Unit = { messageText ->
            addMessage(messageText, false)
            createAnswer(messageText)
        }
        // Custom back button handling
        BackHandler(onBack = {
            speechRecognizer.cancel()
            textToSpeech.stop()
            onBack()
        })

        if (teacher.messageList.isEmpty()) {
            addMessage(teacher.description, true)
            speakOut(teacher.description)
        }

        Column(modifier = Modifier.background(Color.Black)) {
                AvatarAndName(selectedTeacher!!)
            // Avatar and possibly the teacher's name


            // Messages list
            LazyColumn(modifier = Modifier
                .weight(1f)
                .background(Color.Black)) {
                items(teacher.messageList) { message ->
                    ChatBubble(message)
                }
            }

            // Input field
            MessageInput(onMessageSend = onMessageSend)
        }
    }


    fun addMessage(text: String, isFromTeacher: Boolean) {
        selectedTeacher?.messageList?.add(Message(text, isFromTeacher))
    }

    data class Message(
        val text: String,
        val isFromTeacher: Boolean
    )

    @Composable
    fun AvatarAndName(teacher: Teacher) {
        Row(
            modifier = Modifier
                .fillMaxWidth() // Fill the max width of the parent
                .background(Color.Cyan),
            horizontalArrangement = Arrangement.Start, // Center content horizontally
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                modifier = Modifier
                    .padding(16.dp)
                    .size(24.dp),
                painter = painterResource(id = teacher.avatar),
                contentDescription = "Teacher's Avatar",
            )
            Text(teacher.name, color = Color.Black)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MessageInput(onMessageSend: (String) -> Unit) {
        var text by remember { mutableStateOf("") }
        var isMicClicked by remember { mutableStateOf(recognizerBusy) }

        // Use LaunchedEffect to observe changes in recognizerBusy
        LaunchedEffect(recognizerBusy ) {
            isMicClicked = recognizerBusy
        }

        Row(modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,) {
            Image(
                painter = if (!isMicClicked) painterResource(id = R.drawable.baseline_mic_24) else painterResource(
                    id = R.drawable.baseline_mic2_24 // this should be your alternative icon or color
                ),
                contentDescription = "Microphone",
                modifier = Modifier
                    .padding(16.dp)
                    .clickable(onClick = {
                        textToSpeech.stop()
                        isMicClicked = !isMicClicked // Toggle the state
                        if (!recognizerBusy) {
                            recognizerBusy = true
                            speechRecognizer.startListening(speechRecognizerIntent)
                        }
                    })
            )
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp)
            )
            Text("Send", modifier = Modifier
                .padding(8.dp)
                .clickable {
                    if (text.isNotBlank()) {
                        onMessageSend(text)
                        text = ""
                    }
                },
                color = Color.White)
        }
    }

    @Composable
    fun ChatBubble(message: Message) {
        Row(
            horizontalArrangement = if (!message.isFromTeacher) Arrangement.End else Arrangement.Start,
            modifier = Modifier
                .fillMaxWidth(if (!message.isFromTeacher) 1f else 0.7f)
                .padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            if (!message.isFromTeacher) {
                Spacer(modifier = Modifier.weight(0.3f)) // Spacer to push the card to the right
            }

            Card(
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .weight(0.7f) // Take up to 70% of the width
            ) {
                Text(
                    text = message.text,
                    modifier = Modifier
                        .background(color = if (!message.isFromTeacher) Color.White else Color.White)
                        .padding(2.dp)
                        .fillMaxWidth(1f),
                    color = if (!message.isFromTeacher) Color.Black else Color.Black
                )
            }
        }
    }

    override fun onDestroy() {
        if (textToSpeech.isSpeaking) {
            textToSpeech.stop()
        }
        textToSpeech.shutdown()
        super.onDestroy()
    }

    @Composable
    fun AnimateAvatar(avatar: ImageBitmap, isResponding: Boolean) {
        val scale by animateFloatAsState(if (isResponding) 1.1f else 1f)

        Image(
            bitmap = avatar,
            contentDescription = "Teacher Avatar",
            modifier = Modifier.scale(scale)
        )
    }

    private fun speakOut(text: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            log("voices: ${textToSpeech.voices}")
            log("availableLanguages: ${textToSpeech.availableLanguages}")
            log("engines: ${textToSpeech.engines}")
            val availableVoices = textToSpeech.voices
            val germanVoice = availableVoices.find { it.locale == Locale.GERMAN }
            if (germanVoice != null) {
                textToSpeech.voice = germanVoice
            } else {
                log("German voice is not available")
            }

            val desiredVoice = availableVoices.find { voice ->
                // Replace with the criteria you are interested in, such as "male" or "female", if available
                voice.name.contains("male") && voice.locale == Locale.GERMAN
            }
            if (desiredVoice != null) {
                textToSpeech.voice = desiredVoice
            } else {
                log("Desired German voice is not available")
            }

            val result = textToSpeech.setLanguage(Locale.GERMANY)
            // Set pitch. The normal pitch value is 1.0. Lower values lower the tone of the synthesized voice, higher values increase it.
            textToSpeech.setPitch(0.65f)

            // Set speaking speed. The normal rate is 1.0, and lower values slow down the speech (0.5 is half the normal speech rate).
            textToSpeech.setSpeechRate(1.5f)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Handle error case
            }
        } else {
            // Initialization failed
        }
    }
}

data class Teacher(
    val description: String,
    val name: String,
    val topic: String,
    val instructions: String,
    val avatar: Int,
    val messageList: MutableList<MainActivity.Message>
)