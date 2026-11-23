package com.example.cast.discovery

import android.util.Xml
import com.example.BuildConfig
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URI

data class DeviceDescription(
    val friendlyName: String,
    val manufacturer: String,
    val modelName: String,
    val udn: String,
    val avTransportControlUrl: String,
    val renderingControlUrl: String
)

object DeviceDescParser {

    /**
     * Parses a UPnP device description document (desc.xml).
     *
     * Only fields from the FIRST `<device>` element are captured, so embedded
     * `<deviceList>` children can't corrupt the renderer's identity/services.
     * Relative controlURL values are resolved with `java.net.URI.resolve`
     * semantics against `<URLBase>` when present, otherwise against [baseUrl]
     * (the LOCATION of the description document); absolute URLs pass through.
     * Returns null when no `<device>` element is present or the XML is malformed.
     */
    fun parse(xml: String, baseUrl: String): DeviceDescription? {
        return try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xml))

            var foundDevice = false
            var deviceDepth = 0        // how many nested <device> elements we are inside
            var firstDeviceDepth = -1  // depth of the first <device> (its direct children are at this depth)
            var inService = false
            var tag = ""

            var urlBase = ""
            var friendlyName = ""
            var manufacturer = ""
            var modelName = ""
            var udn = ""
            var serviceType = ""
            var controlUrl = ""
            var avTransport = ""
            var renderingControl = ""

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        tag = parser.name ?: ""
                        when (tag) {
                            "device" -> {
                                deviceDepth++
                                if (firstDeviceDepth < 0) firstDeviceDepth = deviceDepth
                                foundDevice = true
                            }
                            "service" -> { inService = true; serviceType = ""; controlUrl = "" }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim() ?: ""
                        if (text.isNotEmpty()) {
                            when {
                                tag == "URLBase" -> urlBase += text
                                inService && deviceDepth == firstDeviceDepth -> when (tag) {
                                    "serviceType" -> serviceType += text
                                    "controlURL" -> controlUrl += text
                                }
                                !inService && deviceDepth == firstDeviceDepth -> when (tag) {
                                    "friendlyName" -> friendlyName += text
                                    "manufacturer" -> manufacturer += text
                                    "modelName" -> modelName += text
                                    "UDN" -> udn += text
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "service" -> {
                                if (deviceDepth == firstDeviceDepth) {
                                    val resolved = resolve(controlUrl, baseUrl, urlBase)
                                    when {
                                        serviceType.contains("AVTransport") -> avTransport = resolved
                                        serviceType.contains("RenderingControl") -> renderingControl = resolved
                                    }
                                }
                                inService = false
                            }
                            "device" -> deviceDepth--
                        }
                        tag = ""
                    }
                }
                event = parser.next()
            }

            if (!foundDevice) null else DeviceDescription(
                friendlyName = friendlyName,
                manufacturer = manufacturer,
                modelName = modelName,
                udn = udn,
                avTransportControlUrl = avTransport,
                renderingControlUrl = renderingControl
            )
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) e.printStackTrace()
            null
        }
    }

    private fun resolve(controlUrl: String, baseUrl: String, urlBase: String): String {
        if (controlUrl.isEmpty()) return ""
        if (controlUrl.startsWith("http://") || controlUrl.startsWith("https://")) return controlUrl
        val base = urlBase.ifEmpty { baseUrl }
        return runCatching { URI(base).resolve(controlUrl).toString() }
            .getOrDefault(controlUrl)
    }
}
