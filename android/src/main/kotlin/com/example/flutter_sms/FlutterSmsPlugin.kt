package com.example.flutter_sms

import android.annotation.TargetApi
import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result


class FlutterSmsPlugin: FlutterPlugin, MethodCallHandler, ActivityAware {
  private lateinit var mChannel: MethodChannel
  private var activity: Activity? = null
  private val REQUEST_CODE_SEND_SMS = 205
  val TAG = "TRACK_SMS_STATUS";

  var result: Result? = null

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
  }

  override fun onDetachedFromActivity() {
    activity = null
  }

  override fun onDetachedFromActivityForConfigChanges() {
    activity = null
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    activity = binding.activity
  }

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    setupCallbackChannels(flutterPluginBinding.binaryMessenger)
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    teardown()
  }

  private fun setupCallbackChannels(messenger: BinaryMessenger) {
    mChannel = MethodChannel(messenger, "flutter_sms")
    mChannel.setMethodCallHandler(this)
  }

  private fun teardown() {
    mChannel.setMethodCallHandler(null)
  }

  // companion object {
  //   const val SENT_SMS_ACTION_NAME = "SMS_SENT_ACTION"
  // }

  override fun onMethodCall(call: MethodCall, result: Result) {
    when (call.method) {
        "sendSMS" -> {
          if (!canSendSMS()) {
            result.error(
                    "device_not_capable",
                    "The current device is not capable of sending text messages.",
                    "A device may be unable to send messages if it does not support messaging or if it is not currently configured to send messages. This only applies to the ability to send text messages via iMessage, SMS, and MMS.")
            return
          }
          val message = call.argument<String?>("message") ?: ""
          val recipients = call.argument<String?>("recipients") ?: ""
          val sendDirect = call.argument<Boolean?>("sendDirect") ?: false
          sendSMS(result, recipients, message!!, sendDirect)
        }
        "canSendSMS" -> result.success(canSendSMS())
        else -> result.notImplemented()
    }
  }

  @TargetApi(Build.VERSION_CODES.ECLAIR)
  private fun canSendSMS(): Boolean {
    if (!activity!!.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY))
      return false
    val intent = Intent(Intent.ACTION_SENDTO)
    intent.data = Uri.parse("smsto:")
    val activityInfo = intent.resolveActivityInfo(activity!!.packageManager, intent.flags.toInt())
    return !(activityInfo == null || !activityInfo.exported)
  }

  private fun sendSMS(result: Result, phones: String, message: String, sendDirect: Boolean) {
    if (sendDirect) {
      sendSMSDirect(result, phones, message);
    }
    else {
      sendSMSDialog(result, phones, message);
    }
  }

  private fun sendSMSDirect(result: Result, phones: String, message: String) {
    this.result = result
    
    // SmsManager is android.telephony
    val send = "SMS_SENT"
    val sentPI = PendingIntent.getBroadcast(activity, 0, Intent(send), PendingIntent.FLAG_IMMUTABLE)
    val numbers = phones.split(";")
    val number = numbers[0];

    activity?.registerReceiver(object : BroadcastReceiver() {
      override fun onReceive(arg0: Context?, arg1: Intent?) {
        activity?.unregisterReceiver(
          this
        )
        when (resultCode) {
          Activity.RESULT_OK -> {
            Log.d(TAG, "SMS sended")
            result?.success("SMS Sent!")
          }
          SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
            Log.d(TAG, "Generic failure")
            result?.error("${SmsManager.RESULT_ERROR_GENERIC_FAILURE}", "RESULT_ERROR_GENERIC_FAILURE", "RESULT_ERROR_GENERIC_FAILURE")
          }
          SmsManager.RESULT_ERROR_NO_SERVICE -> {
            Log.d(TAG, "No service")
            result?.error("${SmsManager.RESULT_ERROR_NO_SERVICE}", "RESULT_ERROR_NO_SERVICE", "No service for sending SMS")
          }
          SmsManager.RESULT_ERROR_NULL_PDU -> {
            Log.d(TAG, "Null PDU")
            result?.error("${SmsManager.RESULT_ERROR_NULL_PDU}", "RESULT_ERROR_NULL_PDU", "Null PDU")
          }
          SmsManager.RESULT_ERROR_RADIO_OFF -> {
            Log.d(TAG, "Radio off")
            result?.error("${SmsManager.RESULT_ERROR_RADIO_OFF}", "RESULT_ERROR_RADIO_OFF", "May airplane mode is turned off")
          }
        }
      }
    }, IntentFilter(send), Context.RECEIVER_EXPORTED)

    val sms = SmsManager.getDefault()
    sms.sendTextMessage(number, null, message, sentPI, null)

  }

  private fun sendSMSDialog(result: Result, phones: String, message: String) {
    val intent = Intent(Intent.ACTION_SENDTO)
    intent.data = Uri.parse("smsto:$phones")
    intent.putExtra("sms_body", message)
    intent.putExtra(Intent.EXTRA_TEXT, message)
    activity?.startActivityForResult(intent, REQUEST_CODE_SEND_SMS)
    result.success("SMS Sent!")
  }

}
