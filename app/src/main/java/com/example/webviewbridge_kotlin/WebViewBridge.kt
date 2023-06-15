package com.example.webviewbridge_kotlin

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.*
import android.os.StrictMode.VmPolicy
import android.provider.MediaStore
import android.webkit.*
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.webviewbridge_kotlin.R
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

class WebViewBridge @SuppressLint("SetJavaScriptEnabled") internal constructor(
    val webView: WebView,
    val context: Context
) {
    val callableFunctions: HashMap<String, CallableFunction>
    private var currentUrl = ""
    private var pageNotFoundUrl = ""
    var lastCallbackValue = ""
        private set

    // Enum "NodePosition" is used to determine the position where a node should be added in HTML
    enum class NodePosition {
        beforeStart, afterStart, beforeEnd, afterEnd
    }

    // Gets an WebView and the App-Context and creates the WebViewBridge object
    init {
        callableFunctions = HashMap()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if (url.contains("#")) {
                    currentUrl =
                        url.split("#".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
                    splitUrlToCallMethod()
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                //Loading the custom error page
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (error.errorCode == -2) if (pageNotFoundUrl != "") view.loadUrl(
                        pageNotFoundUrl
                    )
                }
            }
        }
        setJavaScriptEnabled(true)

        // Adds functions to the callable functions, so they can be accessed from JavaScript
        this.addCallableFunction(this, "loadUrl", "loadUrl", arrayOf("String"))
        this.addCallableFunction(this, "loadData", "loadData", arrayOf("String"))
        this.addCallableFunction(
            this,
            "setPageNotFoundUrl",
            "setPageNotFoundUrl",
            arrayOf("String")
        )

        // Adds an JavaScript-Interface to the WebView (access functions from JavaScript)
        webView.addJavascriptInterface(MyJavaScriptInterface(context), "Android")
    }

    // Options for the WebView ---------------------------------------------------------------------
    fun loadUrl(url: String?) {
        webView.loadUrl(url!!)
    }

    fun loadData(data: String?) {
        webView.loadData(data!!, "text/html", "UTF-8")
    }

    fun setDomStorageEnabled(value: Boolean?) {
        webView.settings.domStorageEnabled = value!!
    }

    fun setJavaScriptEnabled(value: Boolean?) {
        webView.settings.javaScriptEnabled = value!!
    }

    fun setAppCacheEnabled(value: Boolean) {
        webView.settings.cacheMode =
            if (value) WebSettings.LOAD_CACHE_ELSE_NETWORK else WebSettings.LOAD_NO_CACHE
    }

    fun setAllowFileAccess(value: Boolean?) {
        webView.settings.allowFileAccess = value!!
    }

    @SuppressLint("ObsoleteSdkInt")
    fun allowAccessFromFileURLs(value: Boolean?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            webView.settings.allowUniversalAccessFromFileURLs = value!!
            webView.settings.allowFileAccessFromFileURLs = value
        }
    }

    fun loadCacheElseNetwork() {
        setAppCacheEnabled(true)
        webView.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
    }

    fun goBack(): Boolean {
        if (webView.canGoBack() && currentUrl.contains("#")) {
            webView.goBack()
        }
        return if (webView.canGoBack()) {
            webView.goBack()
            true
        } else false
    }

    // Functions that have access to the storage ---------------------------------------------------
    fun writeFile(context: Context, fileName: String?, value: String) {
        try {
            val fileOutputStream = context.openFileOutput(fileName, Context.MODE_PRIVATE)
            fileOutputStream.write(value.toByteArray())
            fileOutputStream.close()
        } catch (e: Exception) {
            println("An error occurred while writing the file: $e")
        }
    }

    fun readFile(context: Context, fileName: String?): String {
        val text = StringBuilder()
        var line: String?
        try {
            val fileInputStream = context.openFileInput(fileName)
            val inputStreamReader = InputStreamReader(fileInputStream)
            val bufferedReader = BufferedReader(inputStreamReader)
            while (bufferedReader.readLine().also { line = it } != null) {
                text.append("\n").append(line)
            }
        } catch (e: Exception) {
            System.err.println("An error occurred while reading the file: $e")
        }
        return text.toString()
    }

    // Functions getting elements from WebView or execute Code in WebView --------------------------
    fun executeJavaScript(cmd: String) {
        webView.webViewClient = object : WebViewClient() {
            @SuppressLint("ObsoleteSdkInt")
            override fun onPageFinished(view: WebView, url: String) {
                if (Build.VERSION.SDK_INT >= 19) view.evaluateJavascript(cmd) { s: String ->
                    lastCallbackValue = s
                } else loadUrl(
                    "javascript:$cmd; void(0);"
                )
                if (url.contains("#")) {
                    currentUrl =
                        url.split("#".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
                    splitUrlToCallMethod()
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (error.errorCode == -2) if (pageNotFoundUrl != "") view.loadUrl(
                        pageNotFoundUrl
                    )
                }
            }
        }
    }

    fun setPageNotFoundUrl(newPageNotFoundUrl: String) {
        pageNotFoundUrl = newPageNotFoundUrl
    }

    private fun getHtmlElementById(id: String): String {
        return "document.getElementById('$id')"
    }

    private fun getHtmlElementsByClass(classname: String): String {
        return "document.getElementsByClassName('$classname')"
    }

    private fun getHtmlElementsByTagName(tagName: String): String {
        return "document.getElementsByTagName('$tagName')"
    }

    private fun doSomethingByClassName(classname: String, function: String) {
        executeJavaScript("var availableClasses = " + getHtmlElementsByClass(classname) + "; [].forEach.call(availableClasses, function (availableClass) {availableClass." + function + "})")
    }

    private fun doSomethingByTagName(tagName: String, function: String) {
        executeJavaScript("var availableTags = " + getHtmlElementsByTagName(tagName) + "; [].forEach.call(availableTags, function (availableTag) {availableTag." + function + "})")
    }

    private fun makeStyleAttributeValid(attribute: String): String {
        var attribute = attribute
        val i = attribute.indexOf("-")
        if (i != -1) attribute =
            attribute.substring(0, i) + attribute.substring(i + 1, i + 2).uppercase(
                Locale.getDefault()
            ) + attribute.substring(i + 2)
        return attribute
    }

    fun setCssById(id: String, attribute: String, value: String) {
        executeJavaScript(getHtmlElementById(id) + ".style." + makeStyleAttributeValid(attribute) + " = '" + value + "';")
    }

    fun setCssByClass(classname: String, attribute: String, value: String) {
        doSomethingByClassName(
            classname,
            "style." + makeStyleAttributeValid(attribute) + " = '" + value + "';"
        )
    }

    fun setCssByTagName(tagName: String, attribute: String, value: String) {
        doSomethingByTagName(
            tagName,
            "style." + makeStyleAttributeValid(attribute) + " = '" + value + "';"
        )
    }

    fun setHtmlAttributeById(id: String, attributeName: String, value: String) {
        executeJavaScript(getHtmlElementById(id) + "." + attributeName + " = '" + value + "';")
    }

    fun setHtmlAttributeByClass(classname: String, attributeName: String, value: String) {
        doSomethingByClassName(classname, "$attributeName = '$value';")
    }

    fun setHtmlAttributeByTagName(tagName: String, attributeName: String, value: String) {
        doSomethingByTagName(tagName, "$attributeName = '$value';")
    }

    fun setInnerHtmlById(id: String, value: String) {
        setHtmlAttributeById(id, "innerHTML", value)
    }

    fun setInnerHtmlByClass(classname: String, value: String) {
        setHtmlAttributeByClass(classname, "innerHTML", value)
    }

    fun setInnerHtmlByTagName(tagName: String, value: String) {
        setHtmlAttributeByTagName(tagName, "innerHTML", value)
    }

    fun setImageSourceById(id: String, url: String) {
        setHtmlAttributeById(id, "src", url)
    }

    fun setImageSourceByClass(classname: String, url: String) {
        setHtmlAttributeByClass(classname, "src", url)
    }

    fun appendNodeById(motherNodeId: String, htmlNode: HtmlNode, nodePosition: NodePosition) {
        executeJavaScript(getHtmlElementById(motherNodeId) + ".insertAdjacentHTML('" + nodePosition + "', '" + htmlNode.get() + "');")
    }

    fun appendNodeByClass(
        motherNodeClassName: String,
        htmlNode: HtmlNode,
        nodePosition: NodePosition
    ) {
        doSomethingByClassName(
            motherNodeClassName,
            "insertAdjacentHTML('" + nodePosition + "', '" + htmlNode.get() + "');"
        )
    }

    fun appendNodeByTagName(
        motherNodeTagName: String,
        htmlNode: HtmlNode,
        nodePosition: NodePosition
    ) {
        doSomethingByTagName(
            motherNodeTagName,
            "insertAdjacentHTML('" + nodePosition + "', '" + htmlNode.get() + "');"
        )
    }

    fun replaceNodeById(nodeToReplaceId: String, htmlNode: HtmlNode) {
        setInnerHtmlById(nodeToReplaceId, htmlNode.get())
    }

    fun replaceNodeByClass(nodeToReplaceClass: String, htmlNode: HtmlNode) {
        setInnerHtmlByClass(nodeToReplaceClass, htmlNode.get())
    }

    fun replaceNodeByTagName(nodeToReplaceTagName: String, htmlNode: HtmlNode) {
        setInnerHtmlByClass(nodeToReplaceTagName, htmlNode.get())
    }

    fun removeNodeById(id: String) {
        executeJavaScript(getHtmlElementById(id) + ".remove();")
    }

    fun removeNodeByClass(classname: String) {
        executeJavaScript("var availableClasses = " + getHtmlElementsByClass(classname) + "; while(availableClasses.length > 0){availableClasses[0].remove();}")
    }

    fun removeNodeByTagName(tagName: String) {
        executeJavaScript("var availableTags = " + getHtmlElementsByTagName(tagName) + "; while(availableTags.length > 0){availableTags[0].remove();}")
    }

    // Methods that give the WebView the opportunity to call native functions ----------------------
    fun addCallableFunction(classObject: Any, methodName: String?, keyword: String) {
        callableFunctions[keyword] = CallableFunction(classObject, methodName, keyword)
    }

    fun addCallableFunction(
        classObject: Any,
        methodName: String?,
        keyword: String,
        arguments: Array<Any?>
    ) {
        callableFunctions[keyword] = CallableFunction(classObject, methodName, keyword, arguments)
    }

    fun removeCallableFunctions(key: String) {
        callableFunctions.remove(key)
    }

    private fun proofCallableFunctions(proofingKeyword: String, arguments: Array<Any?>?) {
        if (callableFunctions.containsKey(proofingKeyword) && arguments != null) {
            Objects.requireNonNull(callableFunctions[proofingKeyword])?.setCallableArguments(arguments)
            Objects.requireNonNull(callableFunctions[proofingKeyword])?.invokeMethod()
        }
    }

    private fun splitUrlToCallMethod() {
        val callableFunctionIdentifier =
            currentUrl.split("=".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()[0]
        val callableFunctionParameters: Array<Any?> =
            currentUrl.split("=".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()[1].split("&".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
        proofCallableFunctions(callableFunctionIdentifier, callableFunctionParameters)
    }

    private fun proofTypes(callableFunctionsParameters: Array<Any>): Array<Any?> {
        val tempParameters = arrayOfNulls<Any>(callableFunctionsParameters.size)
        for (i in callableFunctionsParameters.indices) {
            val tempObject = callableFunctionsParameters[i].toString();
            if (tempObject == "true" || tempObject == "false") {
                tempParameters[i] = java.lang.Boolean.parseBoolean(tempObject)
            } else if(tempObject.toIntOrNull() != null) {
                tempParameters[i] = tempObject.toIntOrNull();
            } else if(tempObject.toFloatOrNull() != null){
                tempParameters[i] = tempObject.toFloatOrNull()
            }else{
                tempParameters[i] = tempObject
            }
        }
        return tempParameters
    }

    // The JavaScript-Interface provides native functions to the JavaScript-Code -------------------
    internal inner class MyJavaScriptInterface(val context: Context) {
        @JavascriptInterface
        fun showToast(message: String?) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }

        @JavascriptInterface
        fun systemOutPrintln(message: String?) {
            println(message)
        }

        @JavascriptInterface
        fun systemErrPrintln(message: String?) {
            System.err.println(message)
        }

        @JavascriptInterface
        fun systemOutPrint(message: String?) {
            print(message)
        }

        @JavascriptInterface
        fun systemErrPrint(message: String?) {
            System.err.print(message)
        }

        @JavascriptInterface
        fun showWarning(warningTitle: String?, warningText: String?) {
            AlertDialog.Builder(context)
                .setTitle(warningTitle)
                .setMessage(warningText)
                .setPositiveButton(android.R.string.yes, null)
                .setIcon(android.R.drawable.ic_dialog_info)
                .show()
        }

        @JavascriptInterface
        fun showError(errorTitle: String?, errorText: String?) {
            AlertDialog.Builder(context)
                .setTitle(errorTitle)
                .setMessage(errorText)
                .setPositiveButton(android.R.string.yes, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show()
        }

        @JavascriptInterface
        fun nightModeEnabled(): String {
            val nightModeFlags =
                this.context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            when (nightModeFlags) {
                Configuration.UI_MODE_NIGHT_YES -> return "UI_MODE_NIGHT_YES"
                Configuration.UI_MODE_NIGHT_NO -> return "UI_MODE_NIGHT_NO"
                Configuration.UI_MODE_NIGHT_UNDEFINED -> return "UI_MODE_NIGHT_UNDEFINED"
            }
            return "null"
        }

        @SuppressLint("SourceLockedOrientationActivity")
        @JavascriptInterface
        fun displayRotationMode(value: Int) {
            if (value == 0) (this.context as Activity).requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else if (value == 1) (this.context as Activity).requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else (this.context as Activity).requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }

        @get:JavascriptInterface
        val connectivityStatus: String
            get() {
                val cm = this.context
                    .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                if (cm != null) {
                    val activeNetwork = cm.activeNetworkInfo
                    if (null != activeNetwork) {
                        if (activeNetwork.type == ConnectivityManager.TYPE_WIFI) return "TYPE_WIFI"
                        if (activeNetwork.type == ConnectivityManager.TYPE_MOBILE) return "TYPE_MOBILE"
                    }
                    return "TYPE_NOT_CONNECTED"
                }
                return "Type_Error"
            }

        @JavascriptInterface
        fun checkPermission(permission: String?): Boolean {
            return ContextCompat.checkSelfPermission(
                this.context,
                permission!!
            ) == PackageManager.PERMISSION_GRANTED
        }

        @JavascriptInterface
        fun requestPermission(permission: String?): Boolean {
            if (!checkPermission(permission)) ActivityCompat.requestPermissions(
                (this.context as Activity),
                arrayOf(permission),
                100
            )
            return checkPermission(permission)
        }

        @get:JavascriptInterface
        val currentLocation: String
            get() {
                Toast.makeText(
                    this.context,
                    "The application wants to get your location.",
                    Toast.LENGTH_SHORT
                ).show()
                requestPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                return if (requestPermission(Manifest.permission.ACCESS_COARSE_LOCATION) && checkPermission(
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                ) {
                    try {
                        val lm =
                            this.context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                        val location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        lm.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            0,
                            0f,
                            MyLocationListener(this.context)
                        )
                        println(location!!.latitude.toString() + "," + location.longitude)
                        location.latitude.toString() + "," + location.longitude
                    } catch (e: Exception) {
                        "null"
                    }
                } else "null"
            }

        @JavascriptInterface
        fun takePhoto(): String? {
            if (this.context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
                val requestedPermissions: Array<String>
                requestedPermissions = if (Build.VERSION.SDK_INT < 28) arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) else if (Build.VERSION.SDK_INT <= 32) arrayOf(
                    Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE
                ) else arrayOf(
                    Manifest.permission.CAMERA, Manifest.permission.READ_MEDIA_IMAGES
                )
                ActivityCompat.requestPermissions(
                    (this.context as Activity),
                    requestedPermissions,
                    1001
                )
                return try {
                    val builder = VmPolicy.Builder()
                    StrictMode.setVmPolicy(builder.build())
                    val photoPathAndFileName =
                        Environment.getExternalStorageDirectory().absolutePath + "/DCIM/Camera/" + "IMG_" + SimpleDateFormat(
                            "yyyyMMdd_HHmmss",
                            Locale.getDefault()
                        ).format(
                            Date()
                        ) + ".jpg"
                    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    intent.putExtra(
                        MediaStore.EXTRA_OUTPUT,
                        Uri.fromFile(File(photoPathAndFileName))
                    )
                    this.context.startActivityForResult(intent, 100)
                    photoPathAndFileName
                } catch (e: Exception) {
                    System.err.println("Camera could not be started")
                    null
                }
            }
            return "null"
        }

        @JavascriptInterface
        fun vibrate(milliseconds: Int) {
            val v = this.context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(
                    VibrationEffect.createOneShot(
                        milliseconds.toLong(),
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                v.vibrate(milliseconds.toLong())
            }
        }

        @SuppressLint("UnspecifiedImmutableFlag")
        @JavascriptInterface
        fun pushNotification(notificationTitle: String?, notificationContent: String?) {
            if (Build.VERSION.SDK_INT >= 33) requestPermission(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT < 33 || checkPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                val notificationManager =
                    this.context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val notificationChannel = NotificationChannel(
                        "NativeWebApp_ID",
                        "NativeWebApp",
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                    notificationChannel.description = "NativeWebApp-Notification"
                    notificationManager.createNotificationChannel(notificationChannel)
                }
                val notificationBuilder =
                    NotificationCompat.Builder(this.context.applicationContext, "NativeWebApp_ID")
                        .setSmallIcon(R.mipmap.ic_launcher) // Icon of the notification
                        .setContentTitle(notificationTitle) // Title of the notification
                        .setContentText(notificationContent) // Content of the notification
                        .setAutoCancel(true) // Notification will be removed after clicking on it
                val intent =
                    Intent(this.context.applicationContext, (this.context as Activity).javaClass)
                val pi: PendingIntent
                pi = if (Build.VERSION.SDK_INT >= 31) PendingIntent.getActivity(
                    this.context,
                    0,
                    intent,
                    PendingIntent.FLAG_MUTABLE
                ) else PendingIntent.getActivity(
                    this.context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
                notificationBuilder.setContentIntent(pi)
                notificationManager.notify(0, notificationBuilder.build())
            } else {
                showToast("Could not show Notification")
            }
        }

        @JavascriptInterface
        fun flashlight(value: Boolean) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val cameraManager =
                    this.context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                try {
                    val cameraId = cameraManager.cameraIdList[0]
                    cameraManager.setTorchMode(cameraId, value)
                } catch (e: CameraAccessException) {
                    System.err.println("Error: $e")
                }
            } else {
                Toast.makeText(
                    this.context,
                    "Feature not supported in this version of Android.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        @JavascriptInterface
        fun writeTextToInternalStorage(fileName: String?, content: String) {
            writeFile(this.context, fileName, content)
        }

        @JavascriptInterface
        fun readTextFromInternalStorage(fileName: String?): String {
            return readFile(this.context, fileName)
        }

        @SuppressLint("ObsoleteSdkInt")
        @JavascriptInterface
        fun setStatusBarColor(color: String?) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) (this.context as Activity).window.statusBarColor =
                Color.parseColor(color)
        }
    }

    private class MyLocationListener internal constructor(val context: Context) : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (location != null) {
                println("Location was determined!")
            }
        }

        override fun onProviderDisabled(provider: String) {
            Toast.makeText(context, "Error onProviderDisabled", Toast.LENGTH_SHORT).show()
        }

        override fun onProviderEnabled(provider: String) {
            Toast.makeText(context, "onProviderEnabled", Toast.LENGTH_SHORT).show()
        }

        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
            Toast.makeText(context, "onStatusChanged", Toast.LENGTH_LONG).show()
        }
    }
} // Class for creating functions that can be called from HTTP fragments (#Fragment) -----------------

class CallableFunction {
    var classObject: Any
    var methodName: String?
    var keyword: String
    var arguments = arrayOfNulls<Any>(0)

    constructor(classObject: Any, methodName: String?, keyword: String) {
        this.classObject = classObject
        this.methodName = methodName
        this.keyword = keyword
    }

    constructor(classObject: Any, methodName: String?, keyword: String, arguments: Array<Any?>) {
        this.classObject = classObject
        this.methodName = methodName
        this.keyword = keyword
        this.arguments = arguments
    }

    fun setCallableArguments(newArguments: Array<Any?>) {
        arguments = newArguments
    }

    fun invokeMethod() {
        try {
            val method = classObject.javaClass.getDeclaredMethod(methodName, *argumentClasses)
            method.invoke(classObject, *arguments)
        } catch (e: Exception) {
            System.err.println("Error: $e")
        }
    }

    private val argumentClasses: Array<Class<*>?>
        private get() {
            val argumentClasses: Array<Class<*>?> = arrayOfNulls(arguments.size)
            for (i in argumentClasses.indices) {
                argumentClasses[i] = arguments[i]!!.javaClass
            }
            return argumentClasses
        }
} // Class for the creation of HTML nodes from the Java-Code.

class HtmlNode {
    var tagName: String? = null
    var innerHtml: String? = null
        private set
    private var cssRules: HashMap<String, String>? = null
    private var attributes: HashMap<String, String>? = null
    var childNodes: ArrayList<HtmlNode>? = null
        private set

    constructor(tagName: String) {
        standardNode(tagName)
    }

    constructor(tagName: String, innerHtml: String?) {
        standardNode(tagName)
        this.innerHtml = innerHtml
    }

    fun get(): String {
        var temp = "<" + tagName
        for ((key, value) in attributes!!) {
            temp = "$temp $key=\"$value\""
        }
        if (cssRules!!.size > 0) {
            temp = "$temp style=\""
            for ((key, value) in cssRules!!) {
                temp = "$temp$key: $value; "
            }
            temp = temp + "\""
        }
        if (innerHtml.contentEquals("") && childNodes!!.size < 1) {
            temp = "$temp/>"
        } else {
            temp = temp + ">" + innerHtml
            for (tempNode in childNodes!!) temp = temp + tempNode.get()
            temp = temp + "</" + tagName + ">"
        }
        return temp
    }

    private fun standardNode(tagName: String) {
        this.tagName = tagName
        innerHtml = ""
        attributes = HashMap()
        cssRules = HashMap()
        childNodes = ArrayList()
    }

    fun setInnerHTML(newInnerHtml: String?) {
        innerHtml = newInnerHtml
    }

    fun setAttribute(attributeName: String, attributeContent: String) {
        attributes!![attributeName] = attributeContent
    }

    fun getAttribute(attributeName: String): String? {
        return attributes!![attributeName]
    }

    fun setCssAttribute(cssAttributeName: String, cssAttributeContent: String) {
        cssRules!![cssAttributeName] = cssAttributeContent
    }

    fun getCssAttribute(cssAttributeName: String): String? {
        return cssRules!![cssAttributeName]
    }

    fun appendChild(childNode: HtmlNode) {
        childNodes!!.add(childNode)
    }

    fun removeChild(id: Int) {
        childNodes!!.removeAt(id)
    }

    fun removeChild(childNode: HtmlNode) {
        childNodes!!.remove(childNode)
    }

    fun setId(id: String) {
        setAttribute("id", id)
    }

    val id: String?
        get() = getAttribute("id")

    fun setHtmlClass(classname: String) {
        setAttribute("class", classname)
    }

    val htmlClass: String?
        get() = getAttribute("class")
}