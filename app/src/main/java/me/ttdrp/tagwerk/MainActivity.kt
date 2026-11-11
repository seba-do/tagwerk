package me.ttdrp.tagwerk

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import me.ttdrp.tagwerk.alarm.FocusAlarmScheduler
import me.ttdrp.tagwerk.alarm.TaskReminderScheduler
import me.ttdrp.tagwerk.ui.AppRoot
import me.ttdrp.tagwerk.ui.AppViewModel
import me.ttdrp.tagwerk.ui.TagwerkTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TagwerkTheme { AppRoot(viewModel) }
        }
        handleExtras(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleExtras(intent)
    }

    private fun handleExtras(intent: Intent) {
        intent.getStringExtra(TaskReminderScheduler.EXTRA_START_FOCUS)?.let {
            intent.removeExtra(TaskReminderScheduler.EXTRA_START_FOCUS)
            viewModel.startFocusFromReminder(it)
        }
        intent.getStringExtra(TaskReminderScheduler.EXTRA_OPEN_TASK)?.let {
            intent.removeExtra(TaskReminderScheduler.EXTRA_OPEN_TASK)
            viewModel.requestOpenTask(it)
        }
        if (intent.getBooleanExtra(FocusAlarmScheduler.EXTRA_OPEN_FOCUS, false)) {
            intent.removeExtra(FocusAlarmScheduler.EXTRA_OPEN_FOCUS)
            viewModel.requestOpenFocus()
        }
    }
}
