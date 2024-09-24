package com.example.remind

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.remind.ui.theme.RemindTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import android.provider.Settings
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat

data class Reminder(val id: Int, val message: String, val date: String, val time: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
            }
        }
        val REQUEST_CODE_POST_NOTIFICATIONS = 1001
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE_POST_NOTIFICATIONS)
            }
        }

        setContent {
            RemindTheme {
                ReminderApp()
            }
        }
    }
}

@SuppressLint("ScheduleExactAlarm")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderApp() {
    var reminderMessage by remember { mutableStateOf(TextFieldValue()) }
    var reminderDate by remember { mutableStateOf("") }
    var reminderTime by remember { mutableStateOf("") }
    val reminders = remember { mutableStateListOf<Reminder>() }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var nextReminderId by remember { mutableStateOf(1) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text(text = "Reminder App") })
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                TextField(
                    value = reminderMessage,
                    onValueChange = { reminderMessage = it },
                    label = { Text("Reminder Message") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row {
                    Button(onClick = {
                        val calendar = Calendar.getInstance()
                        val datePicker = android.app.DatePickerDialog(context, { _, year, month, dayOfMonth ->
                            calendar.set(year, month, dayOfMonth)
                            reminderDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
                        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
                        datePicker.show()
                    }) {
                        Text(if (reminderDate.isNotEmpty()) reminderDate else "Select Date")
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Button(onClick = {
                        val calendar = Calendar.getInstance()
                        val timePicker = android.app.TimePickerDialog(context, { _, hourOfDay, minute ->
                            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                            calendar.set(Calendar.MINUTE, minute)
                            reminderTime = SimpleDateFormat("HH:mm", Locale.US).format(calendar.time)
                        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true)
                        timePicker.show()
                    }) {
                        Text(if (reminderTime.isNotEmpty()) reminderTime else "Select Time")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (reminderMessage.text.isNotEmpty() && reminderDate.isNotEmpty() && reminderTime.isNotEmpty()) {
                            val reminderId = nextReminderId++
                            val reminder = Reminder(reminderId, reminderMessage.text, reminderDate, reminderTime)
                            reminders.add(reminder)

                            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                            val intent = Intent(context, ReminderReceiver::class.java).apply {
                                putExtra("reminderMessage", reminder.message)
                            }
                            val pendingIntent = PendingIntent.getBroadcast(
                                context, reminder.id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )

                            val calendar = Calendar.getInstance().apply {
                                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                                time = sdf.parse("${reminder.date} ${reminder.time}") ?: Date()
                            }

                            if (calendar.timeInMillis > System.currentTimeMillis()) {
                                alarmManager.setExactAndAllowWhileIdle(
                                    AlarmManager.RTC_WAKEUP,
                                    calendar.timeInMillis,
                                    pendingIntent
                                )
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Reminder set for ${reminder.date} at ${reminder.time}")
                                }
                            } else {
                                Toast.makeText(context, "Selected time is in the past!", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Please enter all details", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Set Reminder")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(text = "All Reminders:", fontSize = 20.sp, modifier = Modifier.padding(bottom = 8.dp))
                LazyColumn {
                    items(reminders) { reminder ->
                        ReminderItem(reminder, onDelete = {
                            reminders.remove(reminder)
                            cancelReminder(context, reminder.id)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Reminder deleted")
                            }
                        })
                    }
                }
            }
        }
    )
}

@Composable
fun ReminderItem(reminder: Reminder, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(text = "Message: ${reminder.message}")
            Text(text = "Date: ${reminder.date}")
            Text(text = "Time: ${reminder.time}")
        }
        Button(onClick = onDelete) {
            Text("Delete")
        }
    }
}

fun cancelReminder(context: Context, reminderId: Int) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, ReminderReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context, reminderId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    alarmManager.cancel(pendingIntent)
}
