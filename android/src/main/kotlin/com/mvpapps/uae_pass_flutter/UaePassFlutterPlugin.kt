package com.mvpapps.uae_pass_flutter

import ae.sdg.libraryuaepass.*
import ae.sdg.libraryuaepass.UAEPassController.getAccessToken
import ae.sdg.libraryuaepass.UAEPassController.signDocument
import ae.sdg.libraryuaepass.UAEPassController.getAccessCode
import ae.sdg.libraryuaepass.UAEPassController.resume
import ae.sdg.libraryuaepass.business.authentication.model.UAEPassAccessTokenRequestModel
import ae.sdg.libraryuaepass.business.documentsigning.model.DocumentSigningRequestParams
import ae.sdg.libraryuaepass.business.documentsigning.model.UAEPassDocumentSigningRequestModel
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import android.os.StrictMode
import android.webkit.CookieManager
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import ae.sdg.libraryuaepass.business.Environment
import android.content.pm.PackageManager
import ae.sdg.libraryuaepass.business.Language
import ae.sdg.libraryuaepass.utils.Utils.generateRandomString
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.Charset
import java.net.URL
import java.io.InputStream
import java.util.Base64
// create a class that implements the PluginRegistry.NewIntentListener interface
 


/** UaePassPlugin */
class UaePassFlutterPlugin: FlutterPlugin, MethodCallHandler, ActivityAware,PluginRegistry.NewIntentListener{

  private lateinit var channel : MethodChannel
  private lateinit var requestModel: UAEPassAccessTokenRequestModel

  private var client_id: String? = null
  private var client_secret: String? = null
  private var redirect_url: String? = "https://oauthtest.com/authorization/return"
  private var environment: Environment = Environment.STAGING
  private var state: String? = null
  private var scheme: String? = null
  private var failureHost: String? = null
  private var successHost: String? = null
  private var scope: String? = "urn:uae:digitalid:profile"

  private  val UAE_PASS_PACKAGE_ID = "ae.uaepass.mainapp"
  private  val UAE_PASS_QA_PACKAGE_ID = "ae.uaepass.mainapp.qa"
  private  val UAE_PASS_STG_PACKAGE_ID = "ae.uaepass.mainapp.stg"

  private  val DOCUMENT_SIGNING_SCOPE = "urn:safelayer:eidas:sign:process:document"
  private  val RESPONSE_TYPE = "code"
  private  val SCOPE = "urn:uae:digitalid:profile"
  private  val ACR_VALUES_MOBILE = "urn:digitalid:authentication:flow:mobileondevice"
  private  val ACR_VALUES_WEB = "urn:safelayer:tws:policies:authentication:level:low"

  private var activity: Activity? = null
  private lateinit var result: Result


  override fun onAttachedToActivity(@NonNull binding: ActivityPluginBinding) {
    if(activity==null)
     activity = binding.activity
    binding.addOnNewIntentListener(this)
  }

  override fun onDetachedFromActivityForConfigChanges() {
   }

  override fun onReattachedToActivityForConfigChanges(@NonNull binding: ActivityPluginBinding) {
    activity =binding.activity
    binding.addOnNewIntentListener(this)

  }

  override fun onDetachedFromActivity() {
     activity = null
  }


  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "uae_pass")
    channel.setMethodCallHandler(this)
 
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    this.result = result
    if(call.method=="set_up_environment")
    {
      CookieManager.getInstance().removeAllCookies { }
      CookieManager.getInstance().flush() 
      client_id = call.argument<String>("client_id")
      client_secret = call.argument<String>("client_secret")
      redirect_url = call.argument<String?>("redirect_url")
      environment = if(call.argument<String>("environment")!=null && call.argument<String>("environment") == "production")  Environment.PRODUCTION else Environment.STAGING
      state = call.argument<String?>("state")
      scheme = call.argument<String>("scheme")
      failureHost = call.argument<String?>("failureHost") 
      successHost = call.argument<String?>("successHost")
      scope = call.argument<String?>("scope")
      if(redirect_url==null)
      {
        redirect_url = "https://oauthtest.com/authorization/return"
      }
      if(state==null)
      {
        state = generateRandomString(24)
      }

      if(failureHost==null)
      {
        failureHost = "failure"
      }
      if(successHost==null)
      {
        successHost = "success"
      }
       
    }else if(call.method=="sign_out")
    {

      CookieManager.getInstance().removeAllCookies { }
      CookieManager.getInstance().flush()
    }
    else if(call.method=="sign_in")
    { 
      requestModel = getAuthenticationRequestModel(activity!!)

      getAccessCode(activity!!, requestModel, object : UAEPassAccessCodeCallback {
        override fun getAccessCode(code: String?, error: String?) {
          if (error != null) { 
            result.error("ERROR", error, null);
          } else { 
            result.success(code)
          }
        }
      })
    }
    else if(call.method=="access_token")
    { 
      requestModel = getAuthenticationRequestModel(activity!!)

      getAccessToken(activity!!, requestModel, object : UAEPassAccessTokenCallback {
        override fun getToken(accessToken: String?, state: String, error: String?) {
          if (error != null) { 
            result.error("ERROR", error, null);
          } else { 
            result.success(accessToken)
          }
        }
      })
    }
    else if(call.method=="sign_document")
    {
        var base64Str = call.argument<String>("url")
        val decodedBytes: ByteArray = Base64.getDecoder().decode(base64Str)

        val cacheDir = activity!!.cacheDir
        val file = File(cacheDir, "document.pdf")
        FileOutputStream(file).use { fos ->
            fos.write(decodedBytes)
            fos.flush()
        }
        val documentSigningParams = loadDocumentSigningJson()
        documentSigningParams?.let {
            val requestModel = getDocumentRequestModel(file, it)
            signDocument(activity!!, requestModel, object : UAEPassDocumentSigningCallback {
                override fun getDocumentUrl(spId: String?, documentURL: String?, error: String?) {
                    if (error != null) {
                        result.error("ERROR", error, null)
                    } else {
                        result.success(documentURL + "/***/" + spId)
                    }
                }
            })
        }

        //requestModel = getDocumentRequestModel(fileObj)
        //signDocument(activity!!, requestModel, object : UAEPassDocumentSigningCallback {
        //    override fun getDocumentUrl(spId: String?, documentURL: String?, error: String?){
        //        if(documentURL != null) {
        //            result.success(spId)
          //      }
            //    else {
              //      result.error("ERROR", error, null)
    //            }
      //      }
      //  })
    }

    else {
      result.notImplemented()
    }
  }

  
  override  fun onNewIntent(intent: Intent): Boolean {
     handleIntent(intent)
    return false
  }
//  private fun handleIntent(intent: Intent?) {
//    if (intent != null && intent.data != null) {
//      if (scheme!! == intent.data!!.scheme) {
//        resume(intent.dataString)
//      }
//    }
//  }

    private fun handleIntent(intent: Intent?) {
        if (intent != null && intent.data != null) {
            if (scheme != null && scheme == intent.data!!.scheme) {
                resume(intent.dataString)
            } else {
                Log.e("UaePassFlutterPlugin", "Scheme is null or does not match")
            }
        } else {
            Log.e("UaePassFlutterPlugin", "Intent or intent data is null")
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
     channel.setMethodCallHandler(null)
  }
   private fun isPackageInstalled(packageManager: PackageManager): Boolean {
        val packageName = when (environment) {
            is Environment.STAGING -> {
                UAE_PASS_STG_PACKAGE_ID
            }
            is Environment.QA -> {
                UAE_PASS_QA_PACKAGE_ID
            }
            is Environment.PRODUCTION -> {
                UAE_PASS_PACKAGE_ID
            }
            else -> {
                UAE_PASS_PACKAGE_ID
            }
        }
        var found = true
        try {
            packageManager.getPackageInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            found = false
        }
        return found
    }
     fun getAuthenticationRequestModel(context: Context): UAEPassAccessTokenRequestModel {
        val ACR_VALUE = if (isPackageInstalled(context.packageManager)) {
            ACR_VALUES_MOBILE
        } else {
            ACR_VALUES_WEB
        }
        return UAEPassAccessTokenRequestModel(
            environment!!,
            client_id!!,
            client_secret!!,
            scheme!!,
            failureHost!!,
            successHost!!,
            redirect_url!!,
            scope!!,
            RESPONSE_TYPE,
            ACR_VALUE,
            state!!,
            Language.EN,

        )
    }

    fun getDocumentRequestModel(
            file: File?,
            documentSigningParams: DocumentSigningRequestParams
    ): UAEPassDocumentSigningRequestModel {
        return UAEPassDocumentSigningRequestModel(
                environment!!,
                client_id!!,
                client_secret!!,
                scheme!!,
                failureHost!!,
                successHost!!,
                redirect_url!!,
                "urn:safelayer:eidas:sign:process:document",
                file!!,
                documentSigningParams
        )
    }

     /**
     * Load Document Signing Json from assets.
     *
     * @return DocumentSigningRequestParams Mandatory Parameters
     */
    private fun loadDocumentSigningJson(): DocumentSigningRequestParams? {
       var json: String? = null
        var jsonbase: String = "ewogICAgInByb2Nlc3NfdHlwZSI6ICJ1cm46c2FmZWxheWVyOmVpZGFzOnByb2Nlc3Nlczpkb2N1bWVudDpzaWduOmVzaWdwIiwKICAgICJsYWJlbHMiOiBbCiAgICAgICAgWwogICAgICAgICAgICAiYWR2YW5jZWQiLAogICAgICAgICAgICAiZGlnaXRhbGlkIiwKICAgICAgICAgICAgInNlcnZlciIKICAgICAgICBdCiAgICBdLAogICAgInNpZ25lciI6IHsKICAgICAgICAic2lnbmF0dXJlX3BvbGljeV9pZCI6ICJ1cm46c2FmZWxheWVyOmVpZGFzOnBvbGljaWVzOnNpZ246ZG9jdW1lbnQ6cGRmIiwKICAgICAgICAicGFyYW1ldGVycyI6IHsKICAgICAgICAgICAgInR5cGUiOiAicGRmIiwKICAgICAgICAgICAgInNpZ25hdHVyZV9maWVsZCI6IHsKICAgICAgICAgICAgICAgICJuYW1lIjogIlNpZ24xIiwKICAgICAgICAgICAgICAgICJsb2NhdGlvbiI6IHsKICAgICAgICAgICAgICAgICAgICAicGFnZSI6IHsKICAgICAgICAgICAgICAgICAgICAgICAgIm51bWJlciI6ICJsYXN0IgogICAgICAgICAgICAgICAgICAgIH0sCiAgICAgICAgICAgICAgICAgICAgInJlY3RhbmdsZSI6IHsKICAgICAgICAgICAgICAgICAgICAgICAgIngiOiAzNTAsCiAgICAgICAgICAgICAgICAgICAgICAgICJ5IjogMTAwLAogICAgICAgICAgICAgICAgICAgICAgICAiaGVpZ2h0IjogNTAsCiAgICAgICAgICAgICAgICAgICAgICAgICJ3aWR0aCI6IDIxNQogICAgICAgICAgICAgICAgICAgIH0KICAgICAgICAgICAgICAgIH0sCiAgICAgICAgICAgICAgICAiYXBwZWFyYW5jZSI6IHsKICAgICAgICAgICAgICAgICAgICAic2lnbmF0dXJlX2RldGFpbHMiOiB7CiAgICAgICAgICAgICAgICAgICAgICAgICJkZXRhaWxzIjogWwogICAgICAgICAgICAgICAgICAgICAgICAgICAgewogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICJ0eXBlIjogInN1YmplY3QiLAogICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICJ0aXRsZSI6ICJTaWduZXIgTmFtZTogIgogICAgICAgICAgICAgICAgICAgICAgICAgICAgfSwKICAgICAgICAgICAgICAgICAgICAgICAgICAgIHsKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAidHlwZSI6ICJkYXRlIiwKICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAidGl0bGUiOiAiU2lnbmF0dXJlIERhdGU6ICIKICAgICAgICAgICAgICAgICAgICAgICAgICAgIH0KICAgICAgICAgICAgICAgICAgICAgICAgXQogICAgICAgICAgICAgICAgICAgIH0KICAgICAgICAgICAgICAgIH0KICAgICAgICAgICAgfQogICAgICAgIH0KICAgIH0sCiAgICAidWlfbG9jYWxlcyI6IFsKICAgICAgICAiZW5fVVMiCiAgICBdLAogICAgImZpbmlzaF9jYWxsYmFja191cmwiOiAidWFlcGFzc3N1cGVyYXBwOi8vc2lnbiIsCiAgICAidmlld3MiOiB7CiAgICAgICAgImRvY3VtZW50X2FncmVlbWVudCI6IHsKICAgICAgICAgICAgInNraXBfc2VydmVyX2lkIjogImZhbHNlIgogICAgICAgIH0KICAgIH0sCiAgICAidGltZXN0YW1wIjogewogICAgICAgICJwcm92aWRlcl9pZCI6ICJ1cm46dWFlOnR3czpnZW5lcmF0aW9uOnBvbGljeTpkaWdpdGFsaWQiCiAgICB9Cn0K"
        
        json = try {
            val buffer: ByteArray = Base64.getDecoder().decode(jsonbase)
            String(buffer, Charset.forName("UTF-8"))
        } catch (ex: IOException) {
            ex.printStackTrace()
            return null
        }
        return Gson().fromJson(json, DocumentSigningRequestParams::class.java)
    }
}
