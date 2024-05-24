package com.lepu.vtm01

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lepu.vtm01.type.Error
import com.tencent.bugly.crashreport.CrashReport
import io.getstream.log.android.file.StreamLogFileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale


class MainActivity : AppCompatActivity(R.layout.activity_main) {
    private var connected = false
    private lateinit var viewModel: MainViewModel
    private val end = ByteArray(55)
    private var pkgNo = 0
    private var etParse: EditText? = null
    private val recordList = ArrayList<ArrayList<String>>(3600 * 10)
    override fun onResume() {
        super.onResume()
        LogUtil.e("onResume")
    }

    override fun onPause() {
        super.onPause()
        LogUtil.e("onPause")
    }

    @SuppressLint("SetTextI18n")
    @OptIn(ExperimentalStdlibApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReport.initCrashReport(applicationContext, "cb72183979", false)
        //屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        LogUtil.init(this)
        val tv0 = findViewById<TextView>(R.id.tv0)
        etParse = findViewById(R.id.et_parse)
        viewModel = ViewModelProviders.of(this)[MainViewModel::class.java]
        viewModel.usbOperationError.observe(this) {
            LogUtil.e(it.toString())
            when (it) {
                is Error.NoDeviceFoundError -> showMessage(getString(R.string.error_no_device))
                is Error.UsbConnectionError -> showMessage(getString(R.string.error_no_connection))
                is Error.ClaimInterfaceError -> showMessage(getString(R.string.error_claim_interface))
                is Error.ReadError -> showMessage(getString(R.string.error_read_report))
            }
            connected = false
            tv0.text = getString(R.string.disconnected)
        }

        viewModel.usbOperationSuccess.observe(this) {
            connected = true
            tv0.text = getString(R.string.connected)
        }
        val et = findViewById<EditText>(R.id.et)
        viewModel.usbOperationRead.observe(this) {
            et.setText("${if (et.text.toString().length < 64 * 10) et.text else ""}\n\n${it.toHexString()}")
            if (ticker != null) { //实时
                parseReal(it)
            } else {
                //3f a5 e1 1e 01 00 3c00  41 02020001 00010000 324130313030303005000042160a01e4070609111e000002000000000a303030303030303031320000000000000000
                //0500000000df00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000
                if (it[2] == 0xE1.toByte() && it[3] == 0x1E.toByte()) {
                    val v = it[8].toInt()
                    val v1 = it[9].toInt()
                    val v2 = it[10].toInt()
                    val v3 = it[11].toInt()
                    val v4 = it[12].toInt()
                    etParse?.setText("${v.toChar()} v$v4.$v3.$v2.$v1")
                }
            }
        }

        tv0.setOnClickListener {
            viewModel.connect()
        }

        for (i in 0..54) {
            end[i] = 0x00.toByte()
        }
        //获取设备信息
        findViewById<TextView>(R.id.tv1).setOnClickListener {
            if (connected) {
                viewModel.setCmd(
                    byteArrayOf(
                        0x08.toByte(), 0xA5.toByte(), 0xE1.toByte(), 0x1E.toByte(), 0x00.toByte(),
                        getPkgNo().toByte(), 0x00.toByte(), 0x00.toByte()
                    ).getCRC() + end
                )
            }
        }
        //获取实时数据
        val tv5 = findViewById<TextView>(R.id.tv5)
        tv5.setOnClickListener {
            if (ticker != null) {
                tv5.text = getString(R.string.get_device_data)
                ticker?.cancel()
                ticker = null
                launchWhenResumed {
                    withContext(Dispatchers.IO) {
                        //CSV标题
                        val headerList = mutableListOf<String>()
                        headerList.add(getString(R.string.time))
                        headerList.add(getString(R.string.Oxygen_Level))
                        headerList.add(getString(R.string.Pulse_Rate))
                        headerList.add(getString(R.string.Pi))
                        headerList.add(getString(R.string.Status))
                        saveCsvDoc2BatchShare(
                            System.currentTimeMillis().toDateString("yyyyMMddHHmmss"),
                            headerList.toTypedArray(),
                            recordList,
                            "VTMO1"
                        )
                    }?.let {
                        shareFile(it, "application/vnd.ms-excel")
                    }
                }
            } else {
                tv5.text = getString(R.string.stop_get_device_data)
                recordList.clear()
                getRealData()
            }
        }
        tv5.setOnLongClickListener {
            StreamLogFileManager.share()
            true
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private var ticker: ReceiveChannel<Unit>? = null

    @OptIn(ObsoleteCoroutinesApi::class)
    private fun getRealData() {
        ticker = ticker(1000L, 0)
        launchWhenResumed {
            for (event in ticker!!) {
                if (connected) {
                    viewModel.setCmd(
                        byteArrayOf(
                            0x08.toByte(),
                            0xA5.toByte(),
                            0x02.toByte(),
                            0x20.toByte(),
                            0x00.toByte(),
                            getPkgNo().toByte(),
                            0x00.toByte(),
                            0x00.toByte()
                        ).getCRC() + end
                    )
                }
            }
        }
    }


    @SuppressLint("SetTextI18n")
    @OptIn(ExperimentalStdlibApi::class)
    private fun parseReal(byteArray: ByteArray) {
        //RealTimeData{
        //     RealTimeParameters para;
        //     unsigned short waveform_len;
        //	   unsigned char waveform_data[wav_len];	//0-200分辨率(125Hz)值为255时表示脉搏音标记
        //}
//        RealTimeParameters{
//            unsigned char spo2;		//70-99
//            unsigned short pr;			//30-250有效
//            Unsigned char pi;			//0- 200 e.g. 25 : PI = 2.5
//            Unsigned char probe_state;	//探头状态 0:未检测到手指 1:正常测量 2:探头故障		unsigned char reserved[7];	//预留字段
//        }
//35 a5 02 20 01 36  2d00  62 4f00 08 01 000000000000001f006e707173747576777878797a7a7b7b7c7c7d7e7e7f8080818283838485858688000000000000000000a1
        try {
            if (byteArray[2] == 0x02.toByte() && byteArray[3] == 0x20.toByte()) {
                val beanList = ArrayList<String>()
                beanList.add(System.currentTimeMillis().toDateString())
                val data = byteArray.copyOfRange(8, 13)
                val spo2 = data[0].toInt()
                beanList.add("$spo2")
                val pr = data[1].toInt()
                beanList.add("$pr")
                val pi = data[3].toInt()
                beanList.add("$pi")
                val state = data[4].toInt()
                beanList.add("$state")
                recordList.add(beanList)
                LogUtil.e("spo2: $spo2 pr: $pr pi: $pi state: $state")
                etParse?.setText("${if (etParse?.text.toString().length < 128) etParse?.text else ""}\n\n${"spo2: $spo2   pr: $pr   pi: $pi   state: $state"}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ticker?.cancel()
    }

    private fun getPkgNo(): Int {
        if (pkgNo == 255) pkgNo = 0
        return pkgNo++
    }

    @Suppress("SameParameterValue")
    private fun saveCsvDoc2BatchShare(
        startTime: String,
        titles: Array<String?>,
        recordDataList: ArrayList<ArrayList<String>>,
        deviceName: String = ""
    ): File? {
        val headerList: MutableList<String> = ArrayList()
        val separatorColumn = ","
        val builder = StringBuilder()
        for (id in titles) {
            builder.append(id).append(separatorColumn)
        }
        val separatorLine = "\r\n"
        headerList.add(
            builder.replace(builder.length - 1, builder.length, separatorLine).toString()
        )
        val dataList: MutableList<String> = ArrayList()
        for (stringArrayList in recordDataList) {
            val stringBuilder = StringBuilder()
            for (string in stringArrayList) {
                stringBuilder.append(string).append(separatorColumn)
            }
            dataList.add(
                stringBuilder.replace(
                    stringBuilder.length - 1,
                    stringBuilder.length,
                    separatorLine
                ).toString()
            )
        }
        val list = containAllData(headerList, dataList)
        val csvFile = createFile(this, deviceName, startTime)
        return writeDataToFile(csvFile, list)
    }

    private fun containAllData(headerList: List<String>, dataList: List<String>): List<String> {
        val stringList: MutableList<String> = ArrayList()
        for (value in headerList) {
            stringList.add(value)
        }
        for (value in dataList) {
            stringList.add(value)
        }
        return stringList
    }

    private fun writeDataToFile(file: File, dataList: List<String>): File? {
        val output: FileOutputStream
        try {
            output = FileOutputStream(file)
            //写入Utf-8文件头
            //在utf-8编码文件中BOM在文件头部，占用三个字节，用来标示该文件属于utf-8编码，
            //现在已经有很多软件识别bom头，但是还有些不能识别bom头，比如PHP就不能识别bom头，
            //这也是用记事本编辑utf-8编码后执行就会出错的原因了
            output.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            val headerArray = dataList.toTypedArray()
            for (data in headerArray) {
                output.write(data.toByteArray())
            }
            output.flush()
            output.close()
            return file
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}

inline fun LifecycleOwner.launchWhenResumed(
    retryTime: Int = 1,
    crossinline block: suspend CoroutineScope.() -> Unit
) {
    lifecycleScope.launch {
        var retryCount = 0
        repeatOnLifecycle(Lifecycle.State.CREATED) {  //保持一直发送命令
            try {
                block()
                this@launch.cancel()
            } finally {
                if (retryTime != -1) {
                    retryCount += 1
                    if (retryCount >= retryTime) {
                        this@launch.cancel()
                    }
                }
            }
        }
    }
}

fun ByteArray.getCRC(): ByteArray {
    var crc = 0
    this.forEachIndexed { index, b ->
        if (index > 0) {
            crc = crc xor b.toInt()
            for (i in 0..7) {
                crc = if (crc and 0x80 != 0) {
                    crc shl 1 xor 0x07
                } else {
                    crc shl 1
                }
            }
        }
    }
    return this + (crc and 0xff).toByte()
}

fun ByteArray.checkCRC(): Boolean {
    return this.sliceArray(IntRange(0, this.size - 2)).getCRC().contentEquals(this)
}


fun Long.toDateString(format: String = "HH:mm:ss dd/MM/yyyy", context: Context? = null): String {
    var pattern = format
    context?.let {
        pattern = format.is24HourFormat(context)
    }
    return SimpleDateFormat(pattern, Locale.ENGLISH).format(Date(this))
}


fun String.is24HourFormat(context: Context): String {
    if (DateFormat.is24HourFormat(context)) {
        return this
    }
    if (!this.contains("HH")) return this
    return this.replace("HH", "hh") + " aa"
}

fun createFile(context: Context, deviceName: String = "", time: String): File {
    var mDir = ""
    val files = ContextCompat.getExternalFilesDirs(context, null)
    if (files.isNotEmpty()) {
        files.first()?.let {
            mDir = it.absolutePath
        }
    }
    val shareDir = File(mDir, "share/")
    if (!shareDir.exists()) {
        LogUtil.d("不存在当前文件目录")
        shareDir.mkdirs()
    }
    val name = if (deviceName.isEmpty()) "" else deviceName + "_"
    val file = File(shareDir, "$name$time.csv")
    if (!file.exists()) {
        try {
            file.createNewFile()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
    return file
}


@SuppressLint("QueryPermissionsNeeded")
fun Context.shareFile(shareFile: File, type: String = "image/jpeg") {
    if ((RomUtils.isXiaoMi() || RomUtils.isOppo() || RomUtils.isVivo())
        && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        && !Settings.canDrawOverlays(this)
    ) {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        intent.data = Uri.parse("package:$packageName")
        (this as Activity).startActivityForResult(intent, 0)
        return
    }
    LogUtil.e(shareFile.absolutePath)
    val intent = Intent()
    intent.action = Intent.ACTION_SEND
    intent.type = type
    intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val uri =
        FileProvider.getUriForFile(this, "$packageName.fileProvider", shareFile)
    intent.putExtra(Intent.EXTRA_STREAM, uri)
    try {
        val chooser = Intent.createChooser(intent, getString(R.string.share))
        val resInfoList: List<ResolveInfo> =
            this.packageManager.queryIntentActivities(chooser, PackageManager.MATCH_DEFAULT_ONLY)

        for (resolveInfo in resInfoList) {
            val packageName = resolveInfo.activityInfo.packageName
            grantUriPermission(
                packageName,
                uri,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        startActivity(chooser)
    } catch (e: ActivityNotFoundException) {
        e.printStackTrace()
    }
}


object RomUtils {
    fun isXiaoMi() = checkManufacturer("xiaomi")

    fun isOppo() = checkManufacturer("oppo")

    fun isVivo() = checkManufacturer("vivo")

    private fun checkManufacturer(manufacturer: String) =
        manufacturer.equals(Build.MANUFACTURER, true)
}