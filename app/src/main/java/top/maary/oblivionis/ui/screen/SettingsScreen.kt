package top.maary.oblivionis.ui.screen

import android.Manifest
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import top.maary.oblivionis.R
import top.maary.oblivionis.data.PreferenceRepository
import top.maary.oblivionis.ui.DropdownRow
import top.maary.oblivionis.ui.SwitchRow
import top.maary.oblivionis.ui.TextContent
import top.maary.oblivionis.ui.TimePickerItem
import top.maary.oblivionis.viewmodel.NotificationViewModel
import java.util.Calendar
import java.util.Date

/**
 * 权限设置
 * 通知设置——是否启用/通知频率/时间/何时重置/开始提醒时间
 * about
 * */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SettingsScreen(
    onReWelcomeClick: () -> Unit,
    onBackButtonClicked: () -> Unit,
    notificationViewModel: NotificationViewModel
) {

    val notificationPermissionState =
        rememberPermissionState(permission = Manifest.permission.POST_NOTIFICATIONS)

    val context = LocalContext.current
    val dataStore = PreferenceRepository(context)
    val scope = rememberCoroutineScope()

    val notificationState = dataStore.notificationEnabled.collectAsState(initial = false)
    val notificationInterval = dataStore.notificationInterval.collectAsState(initial = 30)
    val notificationIntervalFixed = dataStore.intervalStartFixed.collectAsState(initial = false)
    val notificationIntervalStart = dataStore.intervalStart.collectAsState(initial = 1)
    val notificationTime = dataStore.notificationTime.collectAsState(initial = "21:00")

    val intervalsHashMap: LinkedHashMap<String, Int> = linkedMapOf(
        stringResource(R.string.d1) to 1,
        stringResource(R.string.d15) to 15,
        stringResource(R.string.d30) to 30,
        stringResource(R.string.d45) to 45,
        stringResource(R.string.d60) to 60
    )

    val intervalsList: MutableList<String> = intervalsHashMap.keys.toMutableList()

    val intervalStartList: MutableList<String> = (1..28).map { it.toString() }.toMutableList()

    fun getNotificationTimeDate(): Date {
        val calendar = Calendar.getInstance()
        val timeParts = notificationTime.value.split(":").map { it.toInt() }

        calendar.set(Calendar.HOUR_OF_DAY, timeParts[0])
        calendar.set(Calendar.MINUTE, timeParts[1])
        calendar.set(Calendar.SECOND, 0) // 设置秒为0
        calendar.set(Calendar.MILLISECOND, 0)

        return calendar.time
    }

    var notificationTimeInDate = getNotificationTimeDate()
    LaunchedEffect(notificationTime) { notificationTimeInDate = getNotificationTimeDate() }

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(topBar = {
        LargeTopAppBar(colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.primary,
        ), navigationIcon = {
            IconButton(onBackButtonClicked) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.close)
                )
            }
        }, title = {
            Text(
                stringResource(id = R.string.settings),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }, scrollBehavior = scrollBehavior
        )
    }) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
//            item { Button({ notificationViewModel.testN() }) { } }
            item {
                SwitchRow(
                    title = stringResource(R.string.notification),
                    description = stringResource(R.string.notification_description),
                    state = notificationState.value,
                ) { state ->
                    if (state) {
                        if (!notificationPermissionState.status.isGranted) {
                            notificationPermissionState.launchPermissionRequest()
                        }
                        scope.launch {
                            dataStore.setNotificationEnabled(true)
                            val timeParts = notificationTime.value.split(":").map { it.toInt() }
                            notificationViewModel.scheduleNotification(
                                date = notificationIntervalStart.value,
                                hour = timeParts[0],
                                minute = timeParts[1],
                                interval = notificationInterval.value.toLong()
                            )
                        }


                    } else {
                        notificationViewModel.cancelNotification()
                    }
                }
            }

            item {
                if (notificationState.value) {
                    Column(modifier = Modifier.padding(start = 16.dp)) {
                        DropdownRow(
                            title = stringResource(R.string.notification_interval),
                            description = stringResource(R.string.notification_interval_description),
                            options = intervalsList,
                            position = intervalsHashMap.entries.indexOfFirst {
                                it.value == notificationInterval.value
                            },
                            onItemClicked = {
                                scope.launch {
                                    intervalsHashMap[intervalsList[it]]?.let { value ->
                                        Log.v("OBLIVIONIS", "$value INTERVAL")
                                        dataStore.setNotificationInterval(value)

                                        val timeParts =
                                            notificationTime.value.split(":").map { it.toInt() }
                                        notificationViewModel.scheduleNotification(
                                            date = notificationIntervalStart.value,
                                            hour = timeParts[0],
                                            minute = timeParts[1],
                                            interval = value.toLong()
                                        )
                                    }
                                }
                            },
                        )

                        SwitchRow(
                            title = stringResource(R.string.notification_interval_fixed),
                            description = stringResource(R.string.notification_interval_fixed_description),
                            state = notificationIntervalFixed.value
                        ) { scope.launch { dataStore.setIntervalFixed(it) } }

                        TimePickerItem(title = stringResource(R.string.notfication_time),
                            time = notificationTimeInDate,
                            onConfirm = {
                                val calendar = Calendar.getInstance()
                                calendar.time = it
                                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                                val minute = calendar.get(Calendar.MINUTE)
                                scope.launch {
                                    dataStore.setNotificationTime(
                                        hour = hour, minute = minute
                                    )
                                    notificationViewModel.scheduleNotification(
                                        date = notificationIntervalStart.value,
                                        hour = hour,
                                        minute = minute,
                                        interval = notificationInterval.value.toLong()
                                    )
                                }


                            })
                        if (notificationIntervalFixed.value) {
                            val position =
                                if (intervalStartList.indexOf(notificationIntervalStart.value.toString()) == -1) 0
                                else intervalStartList.indexOf(notificationIntervalStart.value.toString())
                            DropdownRow(
                                title = stringResource(R.string.select_start_date),
                                description = stringResource(R.string.select_start_date_description),
                                options = intervalStartList,
                                position = position,
                                onItemClicked = { item ->
                                    scope.launch {
                                        dataStore.setIntervalStart(intervalStartList[item].toInt())
                                    }
                                    Log.v(
                                        "OBLIVIONIS",
                                        "${intervalStartList[item].toInt()} INTERVAL START"
                                    )
                                    val timeParts =
                                        notificationTime.value.split(":").map { it.toInt() }
                                    notificationViewModel.scheduleNotification(
                                        date = intervalStartList[item].toInt(),
                                        hour = timeParts[0],
                                        minute = timeParts[1],
                                        interval = notificationInterval.value.toLong()
                                    )
                                },
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }

                }
            }

            item {
                TextContent(modifier = Modifier.fillMaxWidth()
                    .clickable {
                        onReWelcomeClick()
                    }
                    .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                    title = stringResource(R.string.restart_permission),
                    description = stringResource(R.string.restart_permission_description))
            }

            item {
                context.packageManager.getPackageInfo(
                    context.packageName, 0
                ).versionName?.let {
                    TextContent(
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                        title = stringResource(R.string.app_name),
                        description = it
                    )
                }
            }

            // item { Button({ notificationViewModel.testN() }) { } }

            item { Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding())) }

        }
    }

}