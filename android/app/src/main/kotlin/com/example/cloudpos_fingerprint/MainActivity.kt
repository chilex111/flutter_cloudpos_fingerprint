package com.example.cloudpos_fingerprint

import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import com.cloudpos.DeviceException
import com.cloudpos.POSTerminal
import com.cloudpos.fingerprint.FingerprintDevice
import com.digitalpersona.uareu.Engine
import com.digitalpersona.uareu.Fmd
import com.digitalpersona.uareu.Importer
import com.digitalpersona.uareu.UareUException
import com.digitalpersona.uareu.UareUGlobal
import com.example.mgr.FPMgrImpl
import com.example.mgr.IFPMgr
import com.example.utils.HexString
import com.upek.android.ptapi.PtConstants
import com.upek.android.ptapi.PtException
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugins.GeneratedPluginRegistrant
import java.lang.ref.WeakReference

class MainActivity: FlutterActivity(), MethodCallHandler{

    private val ANDROID_CHANNEL = " com.example.cloudpos_fingerprint/cloudpos_fingerprint"
    private var deviceType: String? = null
    private var mImporter: Importer? = null
    private var mEngine: Engine? = null
    private var mChannel: MethodChannel? = null
    private var device: FingerprintDevice? = null
   // private var mHandler: Handler? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        GeneratedPluginRegistrant.registerWith(flutterEngine)
        mChannel = MethodChannel(
            flutterEngine.dartExecutor,
            ANDROID_CHANNEL
        )
        mChannel?.setMethodCallHandler(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        init()

    }
    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "checkDeviceType" -> result.success(deviceType)
            "sendData" -> Thread {
                if (deviceType!!.contains("tuzhengbig")) {
                    mInvokeTuzheng()
                } else if (deviceType!!.lowercase().contains("crossmatch")) {
                    mInvokeCrossmatch()
                }
            }.start()

            else -> result.notImplemented()
        }
    }
    private val mHandler = MyHandler(this)

    private class MyHandler(activity: MainActivity) : Handler() {
        private val mActivity: WeakReference<MainActivity> = WeakReference(activity)

        override fun handleMessage(msg: Message) {
            val activity = mActivity.get()
            if (activity != null) {
                when (msg.what) {
                    0 -> activity.mChannel!!.invokeMethod("getDataNomal", msg.obj)
                    1 -> activity.mChannel!!.invokeMethod("getDataSuccess", msg.obj)
                    2 -> activity.mChannel!!.invokeMethod("getDataFail", msg.obj)
                }
            }
        }
    }
    private fun init() {
     /*   mHandler = object : Handler() {
            override fun handleMessage(msg: Message) {
                if (msg.what == 0) {
                    mChannel!!.invokeMethod("getDataNomal", msg.obj)
                } else if (msg.what == 1) {
                    mChannel!!.invokeMethod("getDataSuccess", msg.obj)
                } else if (msg.what == 2) {
                    mChannel!!.invokeMethod("getDataFail", msg.obj)
                }
            }
        }*/
        try {
            mImporter = UareUGlobal.GetImporter()
            mEngine = UareUGlobal.GetEngine()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        deviceType = checkDeviceType()
        if (deviceType!!.contains("tuzhengbig")) {
            //ready FingerprintDevice
            Thread {
                if (device == null) {
                    device = POSTerminal.getInstance(this)
                        .getDevice("cloudpos.device.fingerprint") as? FingerprintDevice
                }
                try {
                    device?.open(1)
                    writerLogInTextview("device.open success!", 1)
                } catch (e: DeviceException) {
                    writerLogInTextview("device.open failed!", 2)
                    e.printStackTrace()
                }
            }.start()
        }
    }
    private fun checkDeviceType(): String? {
        return getProperty("wp.fingerprint.model", "not")
    }

    private fun getProperty(key: String?, defaultValue: String?): String? {
        var value = defaultValue
        try {
            val c = Class.forName("android.os.SystemProperties")
            val get = c.getMethod("get", String::class.java, String::class.java)
            value = get.invoke(c, key, defaultValue) as String
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        return value
    }
    private fun mInvokeCrossmatch() {
        val crossmatchBs: ByteArray? = getcrossmatchData()
        if (crossmatchBs == null) {
            Log.d("ConvertDiffFinger", "not get crossmatch fingerprint")
            writerLogInTextview("not get crossmatch fingerprint", 2)
            return
        }
        try {
            //Convert the acquired data into an FMD object,format is ANSI_378_2004;
            val fmCROSSMATCH_ANSI = mImporter!!.ImportFmd(
                crossmatchBs,
                Fmd.Format.ANSI_378_2004,
                Fmd.Format.ANSI_378_2004
            )
            writerLogInTextview("fmCROSSMATCH_ANSI finger import ok!", 1)
            Log.e("fp", "" + HexString.bufferToHex(fmCROSSMATCH_ANSI.data))

            //Convert the acquired data into an FMD object,format is ISO_19794_2_2005;
            val CROSSMATCH_ANSI_TO_ISO = mImporter!!.ImportFmd(
                crossmatchBs,
                Fmd.Format.ANSI_378_2004,
                Fmd.Format.ISO_19794_2_2005
            )
            Log.e("fp", "" + HexString.bufferToHex(CROSSMATCH_ANSI_TO_ISO.data))
            writerLogInTextview("fmCROSSMATCH_ANSI_TO_ISO finger ok!", 1)

            // use crossmatch sdk : "engine.Compare"
            /*
             * Compare
             * int Compare(Fmd fmd1,
             *           int view_index1,
             *           Fmd fmd2,
             *           int view_index2)
             *           throws UareUException
             * Compares two fingerprints.
             * Given two single views from two FMDs, this function returns a dissimilarity score indicating the quality of the match. The dissimilarity scores returned values are between: 0=match PROBABILITY_ONE=no match Values close to 0 indicate very close matches, values closer to PROBABILITY_ONE indicate very poor matches. For a discussion of how to evaluate dissimilarity scores, as well as the statistical validity of the dissimilarity score and error rates, consult the Developer Guide.
             *
             * Parameters:
             * fmd1 - First FMD.
             * view_index1 - Index of the view in the first FMD.
             * fmd2 - Second FMD.
             * view_index2 - Index of the view in the second FMD.
             * Returns:
             * Dissimilarity score.
             * Throws:
             * UareUException - if failed to perform comparison.
             * */

            //The parameter is the fmd object converted above.
            val score = mEngine!!.Compare(fmCROSSMATCH_ANSI, 0, CROSSMATCH_ANSI_TO_ISO, 0)

            //compare myself, score: 0=match
            if (score == 0) {
                writerLogInTextview(
                    "Compare fmCROSSMATCH_ANSI and CROSSMATCH_ANSI_TO_ISO fingerprint success!",
                    1
                )
            } else {
                writerLogInTextview(
                    "Compare fmCROSSMATCH_ANSI and CROSSMATCH_ANSI_TO_ISO fingerprint failed!",
                    2
                )
            }
            Log.i("ConvertDiffFinger", String.format("mEngine.Compare,score = %d", score))
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun mInvokeTuzheng() {
        writerLogInTextview("press your finger!", 0)

        //1,get Tuzheng fingerprint Data
        val tuzhengBs: ByteArray? = getTuzhengData()
        if (tuzhengBs == null) {
            Log.d("ConvertDiffFinger", "not get tuzheng fingerprint")
            writerLogInTextview("not get tuzheng fingerprint", 2)
            return
        }
        try {
            //Convert the acquired data into an FMD object,format is ISO_19794_2_2005;
            val fmTUZHENG_ISO = mImporter!!.ImportFmd(
                tuzhengBs,
                Fmd.Format.ISO_19794_2_2005,
                Fmd.Format.ISO_19794_2_2005
            )
            writerLogInTextview("fmTUZHENG_ISO finger import ok!", 1)

            //Convert the acquired data into an FMD object,format is ANSI_378_2004;
            val fmTUZHENG_ISO_TO_ANSI378 = mImporter!!.ImportFmd(
                tuzhengBs,
                Fmd.Format.ISO_19794_2_2005,
                Fmd.Format.ANSI_378_2004
            )
            writerLogInTextview("fmTUZHENG_ISO_TO_ANSI378 finger ok!", 1)

            // use crossmatch sdk : "engine.Compare"
            /*
             * Compare
             * int Compare(Fmd fmd1,
             *           int view_index1,
             *           Fmd fmd2,
             *           int view_index2)
             *           throws UareUException
             * Compares two fingerprints.
             * Given two single views from two FMDs, this function returns a dissimilarity score indicating the quality of the match. The dissimilarity scores returned values are between: 0=match PROBABILITY_ONE=no match Values close to 0 indicate very close matches, values closer to PROBABILITY_ONE indicate very poor matches. For a discussion of how to evaluate dissimilarity scores, as well as the statistical validity of the dissimilarity score and error rates, consult the Developer Guide.
             *
             * Parameters:
             * fmd1 - First FMD.
             * view_index1 - Index of the view in the first FMD.
             * fmd2 - Second FMD.
             * view_index2 - Index of the view in the second FMD.
             * Returns:
             * Dissimilarity score.
             * Throws:
             * UareUException - if failed to perform comparison.
             * */

            //The parameter is the fmd object converted above.
            val score = mEngine!!.Compare(fmTUZHENG_ISO, 0, fmTUZHENG_ISO_TO_ANSI378, 0)

            //compare myself, score: 0=match
            if (score == 0) {
                writerLogInTextview(
                    "Compare fmTUZHENG_ISO and fmTUZHENG_ISO_TO_ANSI378 fingerprint success!",
                    1
                )
            } else {
                writerLogInTextview(
                    "Compare fmTUZHENG_ISO and fmTUZHENG_ISO_TO_ANSI378 fingerprint failed!",
                    2
                )
            }
            Log.i("ConvertDiffFinger", String.format("mEngine.Compare,score = %d", score))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getTuzhengData(): ByteArray? {
        try {
            val fingerprint1 = device!!.getFingerprint(1) //1 = ISOFINGERPRINT_TYPE_ISO2005
            return fingerprint1.feature
        } catch (e: DeviceException) {
            writerLogInTextview(e.message!!, 2)
            e.printStackTrace()
        }
        return null
    }


    private fun getcrossmatchData(): ByteArray? {
        var crossmatchBs: ByteArray? = null
        val fpMgr: IFPMgr = FPMgrImpl.getInstance()
        try {
            fpMgr.open(this)
            fpMgr.deleteAll(this)
            writerLogInTextview("press finger keep,enroll ", 0)
            try {
                val image: ByteArray =
                    fpMgr.GrabImage(PtConstants.PT_GRAB_TYPE_508_508_8_SCAN508_508_8)
                val iWidth: Int = fpMgr.getImagewidth()
                //get fingerprint , format is ANSI_378
                val fm: Fmd? = convertImgToIsoTemplate(image, iWidth)
                //Fmd fm1 = im.ImportFmd(readFingerprintData(), Fmd.Format.ISO_19794_2_2005, Fmd.Format.ISO_19794_2_2005);
                if (fm != null) {
                    val bs = fm.data
                    crossmatchBs = bs
                    //Log.e("fp", "" + HexString.bufferToHex(bs));
                    writerLogInTextview("crossmatch enroll success!", 1)
                } else {
                    Log.e("fm", "fm is null")
                    writerLogInTextview("crossmatch enroll failed!", 2)
                }
            } catch (e: PtException) {
                writerLogInTextview("exception " + e.message, 2)
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            writerLogInTextview("exception occured !" + e.message, 2)
        } finally {
            fpMgr.close(this)
        }
        return crossmatchBs
    }

    private fun convertImgToIsoTemplate(aImage: ByteArray?, iWidth: Int): Fmd? {
        if (aImage == null) {
            return null
        }
        val iHeight = aImage.size / iWidth
        return try {
            val engine = UareUGlobal.GetEngine()
            val fmd = engine.CreateFmd(aImage, iWidth, iHeight, 500, 0, 0, Fmd.Format.ANSI_378_2004)
            Log.i("BasicSample", "Import a Fmd from a raw image OK")
            fmd
        } catch (e: UareUException) {
            Log.d("BasicSample", "Import Raw Image Fail", e)
            null
        }
    }

    private fun writerLogInTextview(log: String, id: Int) {
        val msg = Message()
        msg.what = id
        msg.obj = log
        mHandler!!.sendMessage(msg)
    }

    protected override fun onDestroy() {
        super.onDestroy()
        if (device != null) {
            try  {
                device?.close()
            }catch (e: DeviceException) {
                e.printStackTrace()
                android.util.Log.d("CloudPOS", "onDestroy,device.close()")
            }
        }
    }
}
