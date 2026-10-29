package com.zikrtime.app.screentime

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zikrtime.app.MainActivity
import com.zikrtime.app.R
import com.zikrtime.app.data.Store
import com.zikrtime.app.i18n.Lang
import com.zikrtime.app.ui.ZCard
import com.zikrtime.app.ui.theme.ZikrTheme
import kotlinx.coroutines.delay
import java.lang.ref.WeakReference

/** A voluntary, local pause. It never changes the daily allowance or the prayer lock. */
class PauseActivity : ComponentActivity() {
    private var target by mutableStateOf("")
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(Lang.wrap(newBase))
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Match system icon contrast to the same system light/dark theme used by ZikrTheme.
        enableEdgeToEdge()
        Store.init(this)
        target = savedInstanceState?.getString(TARGET) ?: intent.getStringExtra(TARGET).orEmpty()
        if (!FocusPause.eligible(this,target) || !ScreenTime.hasUsageAccess(this)) { finish(); return }
        onBackPressedDispatcher.addCallback(this,object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = closeToHome()
        })
        setContent {
            ZikrTheme(natural=true) {
                LaunchedEffect(target) {
                    while (true) {
                        if (!FocusPause.eligible(this@PauseActivity,target) || !ScreenTime.hasUsageAccess(this@PauseActivity) || ScreenTime.hasActiveCall(this@PauseActivity)) {
                            finish(); break
                        }
                        if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) { delay(1_000); continue }
                        val reason=ScreenTime.evaluate(this@PauseActivity)
                        if (reason !is BlockReason.None) {
                            startActivity(BlockActivity.intent(this@PauseActivity,reason)); finish(); break
                        }
                        delay(1_000)
                    }
                }
                PauseContent(target, onOpen = ::openTarget, onExit = ::closeToHome, onSettings = ::openSettings,
                    onDisable = { FocusPause.setEnabled(this,false) })
            }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString(TARGET,target); super.onSaveInstanceState(outState) }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A second event cannot replace the app whose pause the user is completing.
        if (!visible) { target=intent.getStringExtra(TARGET).orEmpty(); setIntent(intent) }
    }
    override fun onResume() { super.onResume(); visible=true; FocusPause.deliveryFailed=false; current=WeakReference(this); PauseOverlay.hide(this) }
    override fun onPause() { visible=false; super.onPause() }
    override fun onDestroy() { if(current?.get()===this) current=null; super.onDestroy() }
    private fun closeToHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); finish()
    }
    private fun openSettings() { startActivity(Intent(this,MainActivity::class.java).putExtra("open_focus",true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish() }
    private fun openTarget(minutes: Int, intention: String) {
        if (!FocusPause.eligible(this,target)) { finish(); return }
        val launch=packageManager.getLaunchIntentForPackage(target)
        if (launch==null) { Toast.makeText(this,R.string.pause_app_missing,Toast.LENGTH_LONG).show(); return }
        if (!FocusPause.grant(this,target,minutes,intention)) {
            val reason=ScreenTime.evaluate(this)
            if (reason !is BlockReason.None) { startActivity(BlockActivity.intent(this,reason)); finish() }
            else Toast.makeText(this,R.string.pause_unavailable,Toast.LENGTH_LONG).show()
            return
        }
        runCatching { startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); finish() }
            .onFailure { Toast.makeText(this,R.string.pause_app_missing,Toast.LENGTH_LONG).show() }
    }
    companion object {
        private const val TARGET="target_package"
        @Volatile var visible=false; private set
        private var current: WeakReference<PauseActivity>?=null
        fun intent(context: Context,pkg: String)=Intent(context,PauseActivity::class.java)
            .putExtra(TARGET,pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        fun closeIfVisible() { current?.get()?.finish() }
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun PauseContent(pkg: String,onOpen:(Int,String)->Unit,onExit:()->Unit,onSettings:()->Unit,onDisable:()->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val revision=FocusPause.version
    val strict=remember(revision) { FocusPause.strict(context) }
    val phrase=remember(revision) { FocusPause.phrase(context) }
    var completed by rememberSaveable(pkg) { mutableStateOf(false) }
    var minutes by rememberSaveable(pkg) { mutableIntStateOf(5) }
    var intention by rememberSaveable(pkg) { mutableIntStateOf(0) }
    var custom by rememberSaveable(pkg) { mutableStateOf("") }
    val choices=listOf(R.string.pause_messages,R.string.pause_information,R.string.pause_rest,R.string.pause_custom)
    val intentionText=if(intention==3) custom.trim() else stringResource(choices[intention])
    val appLabel=remember(pkg) { runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg,0)).toString() }.getOrDefault(pkg) }
    val arabic=listOf("سُبْحَانَ اللَّهِ","الْحَمْدُ لِلَّهِ","اللَّهُ أَكْبَرُ")
    val names=listOf(R.string.zikr_1_name,R.string.zikr_2_name,R.string.zikr_3_name)
    val meanings=listOf(R.string.zikr_1_meaning,R.string.zikr_2_meaning,R.string.zikr_3_meaning)
    Surface(Modifier.fillMaxSize().testTag("pause_screen").semantics { testTagsAsResourceId=true },color=MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()
            .verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            com.zikrtime.app.ui.PageHeading(stringResource(R.string.pause_title), appLabel, R.drawable.focus_courtyard)
            if(!completed) {
                ZCard {
                    Text(stringResource(R.string.pause_zikr),style=MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.pause_zikr_hint),style=MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(20.dp))
                    Text(arabic[phrase],Modifier.fillMaxWidth(),style=com.zikrtime.app.ui.theme.ArabicStyle.copy(textDirection=TextDirection.Rtl,lineHeight=56.sp),textAlign=TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(names[phrase]),style=MaterialTheme.typography.titleMedium)
                    Text(stringResource(meanings[phrase]),style=MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                }
                Button(onClick={completed=true},modifier=Modifier.fillMaxWidth().testTag("pause_done")) { Text(stringResource(R.string.pause_done)) }
                if(!strict) TextButton(onClick={completed=true},modifier=Modifier.fillMaxWidth().testTag("pause_skip")) { Text(stringResource(R.string.pause_skip)) }
            } else {
                ZCard {
                    Text(stringResource(R.string.pause_intention),style=MaterialTheme.typography.titleMedium)
                    choices.forEachIndexed { index,label ->
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            RadioButton(selected=intention==index,onClick={intention=index})
                            TextButton(onClick={intention=index}) { Text(stringResource(label)) }
                        }
                    }
                    if(intention==3) OutlinedTextField(value=custom,onValueChange={custom=it.take(120)},label={Text(stringResource(R.string.pause_custom))},modifier=Modifier.fillMaxWidth(),maxLines=3)
                    Text(stringResource(R.string.pause_local),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ZCard {
                    Text(stringResource(R.string.pause_duration),style=MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        listOf(1,5,10,15,30).forEach { value ->
                            FilterChip(selected=minutes==value,onClick={minutes=value},modifier=Modifier.testTag("pause_duration_$value"),label={Text(stringResource(R.string.pause_minutes,value))})
                        }
                    }
                    Text(stringResource(R.string.pause_foreground),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick={onOpen(minutes,intentionText)},enabled=intentionText.isNotBlank(),modifier=Modifier.fillMaxWidth().testTag("pause_open")) { Text(stringResource(R.string.pause_open)) }
            }
            OutlinedButton(onClick=onExit,modifier=Modifier.fillMaxWidth().testTag("pause_exit")) { Text(stringResource(R.string.pause_exit)) }
            TextButton(onClick=onSettings,modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.pause_settings)) }
            TextButton(onClick=onDisable,modifier=Modifier.fillMaxWidth().testTag("pause_disable")) { Text(stringResource(R.string.pause_disable)) }
        }
    }
}
