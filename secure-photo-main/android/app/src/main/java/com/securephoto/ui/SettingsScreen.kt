package com.securephoto.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.securephoto.viewmodel.MainViewModel

@Composable fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) { val context=LocalContext.current; val link=vm.link; Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){ Text("Настройки",style=MaterialTheme.typography.headlineMedium); Text("Ссылка отправителя",style=MaterialTheme.typography.titleMedium); QrCode(link); Text(link,style=MaterialTheme.typography.bodySmall); Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Button({share(context,link)}){Text("Поделиться")}; TextButton(onBack){Text("Назад")}}; Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Биометрия при открытии");Switch(vm.biometricEnabled,{vm.setBiometric(it)})}; Button({vm.rotate()}){Text("Отозвать старую ссылку")}; OutlinedButton({vm.deleteAll()}){Text("Удалить все фото")}} }

@Composable private fun QrCode(value: String) { val bitmap=remember(value){ val matrix=QRCodeWriter().encode(value,BarcodeFormat.QR_CODE,480,480); android.graphics.Bitmap.createBitmap(480,480,android.graphics.Bitmap.Config.ARGB_8888).also{ b->for(x in 0 until 480)for(y in 0 until 480)b.setPixel(x,y,if(matrix[x,y]) android.graphics.Color.BLACK android.graphics.Color.WHITE) } }; Image(bitmap.asImageBitmap(),"QR-код ссылки",Modifier.size(220.dp)) }
private fun share(context: Context,value: String){context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,value)},"Поделиться ссылкой"))}
