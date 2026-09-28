package com.securephoto

import android.app.Activity
import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.securephoto.viewmodel.MainViewModel
import com.securephoto.viewmodel.ScreenState
import com.securephoto.data.PollScheduler
import com.securephoto.ui.SettingsScreen
import com.securephoto.ui.authenticate
import androidx.fragment.app.FragmentActivity
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE); if (Build.VERSION.SDK_INT >= 31) { setRecentsScreenshotEnabled(false); window.setHideOverlayWindows(true) }; setContent { SecurePhotoApp() } }
}

@Composable fun SecurePhotoApp(vm: MainViewModel = viewModel()) { val state by vm.ui.collectAsState(); var settings by remember { mutableStateOf(false) }; val owner=LocalLifecycleOwner.current; val context=androidx.compose.ui.platform.LocalContext.current; DisposableEffect(owner){ val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP)vm.clearSensitiveView()}; owner.lifecycle.addObserver(observer); onDispose{owner.lifecycle.removeObserver(observer)} }; LaunchedEffect(Unit){ PollScheduler.schedule(context) }; MaterialTheme { Surface(Modifier.fillMaxSize()) { when(val s=state) { ScreenState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center){CircularProgressIndicator()}; is ScreenState.Setup -> SetupScreen(s,vm); is ScreenState.List -> if(settings) SettingsScreen(vm){settings=false} else ListScreen(s,vm){settings=true}; is ScreenState.Viewing -> ViewScreen(s,vm) } } } }
@Composable private fun SetupScreen(s: ScreenState.Setup, vm: MainViewModel) { var secret by remember { mutableStateOf("") }; Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){ Text("Secure Photo",style=MaterialTheme.typography.headlineLarge); Text("Создайте защищённую комнату для получения фотографий."); OutlinedTextField(secret,{secret=it},label={Text("Registration Secret")},singleLine=true); Button({vm.setup(secret)},enabled=secret.isNotBlank()){Text("Создать комнату")}; s.error?.let{Text(it,color=MaterialTheme.colorScheme.error)} } }
@Composable private fun ListScreen(s: ScreenState.List, vm: MainViewModel, openSettings: () -> Unit) { val context=androidx.compose.ui.platform.LocalContext.current as? FragmentActivity; Scaffold(topBar={TopAppBar(title={Text("Входящие фото")},actions={TextButton({vm.refresh()}){Text("Обновить")}; TextButton(openSettings){Text("Настройки")}})}){ pad -> Column(Modifier.padding(pad).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){ if(s.photos.isEmpty()) Text("Пока нет фотографий."); LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){items(s.photos,key={it.id}){p-> Card(Modifier.fillMaxWidth()){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(DateFormat.getDateTimeInstance().format(Date(p.created_at)));Text("${p.size/1024} КБ · просмотры: ${if(p.max_views==0)"∞" else "${p.max_views-p.views_used}"}")};Button({ val open={vm.open(p)}; if(vm.biometricEnabled && context!=null) authenticate(context,open,{ }) else open() }){Text("Открыть")}}}}}; s.error?.let{Text(it,color=MaterialTheme.colorScheme.error)} } } }
@Composable private fun ViewScreen(s: ScreenState.Viewing, vm: MainViewModel) { var now by remember { mutableLongStateOf(System.currentTimeMillis()) }; LaunchedEffect(Unit){ while(true){now=System.currentTimeMillis(); kotlinx.coroutines.delay(1000)} }; Box(Modifier.fillMaxSize().background(Color.Black)){ Image(s.bitmap.asImageBitmap(),null,Modifier.fillMaxSize()); Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("все для тебя любимый Л\n${DateFormat.getTimeInstance().format(Date(now))}",color=Color.White.copy(alpha=.38f),textAlign=TextAlign.Center,style=MaterialTheme.typography.titleLarge)}; Button({vm.closeViewing()},Modifier.align(Alignment.BottomCenter).padding(24.dp)){Text("Закрыть")}} }
