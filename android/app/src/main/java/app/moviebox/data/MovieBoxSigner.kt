package app.moviebox.data

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Kotlin port of the MovieBox (oneroom) request-signing scheme used by the
 * MovieBox-TUI backend: HMAC-MD5 `x-tr-signature`, `x-client-token`,
 * `x-client-build` tag, spoofed client info and forwarded IP.
 */
object MovieBoxSigner {

    private val SECRECT_BYTES = byteArrayOf(
        0xef.toByte(), 0xa8.toByte(), 0x91.toByte(), 0x97.toByte(), 0x4e.toByte(),
        0xec.toByte(), 0xd3.toByte(), 0x14.toByte(), 0x8d.toByte(), 0xf6.toByte(),
        0x3a.toByte(), 0xa6.toByte(), 0x11.toByte(), 0x60.toByte(), 0x2d.toByte(),
        0xef.toByte(), 0xd1.toByte(), 0x01.toByte(), 0x25.toByte(), 0x9b.toByte(),
        0xa5.toByte(), 0x21.toByte(), 0x02.toByte(), 0x2c.toByte(), 0x57.toByte(),
        0xae.toByte(), 0x05.toByte(), 0x66.toByte(), 0xbd.toByte(), 0x8e.toByte(),
    )

    private const val STOCK_CERT_SHA256 =
        "03d4b741632d136bc23e2de4c6adcdb7712f1980b619c183221801730bbd0410"
    private const val BUILD_TAG_SALT = "oneroom"

    private val rand = SecureRandom()

    fun md5Hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("MD5").digest(bytes)
        return md.joinToString("") { "%02x".format(it) }
    }

    private fun randomHex(len: Int): String {
        return (0 until len).joinToString("") { "%x".format(rand.nextInt(16)) }
    }

    private fun randomUuid(): String =
        "${randomHex(8)}-${randomHex(4)}-${randomHex(4)}-${randomHex(4)}-${randomHex(12)}"

    fun nowMillis(): Long = System.currentTimeMillis()

    fun generateClientToken(ts: Long): String {
        val reversed = ts.toString().reversed()
        return "$ts,${md5Hex(reversed.toByteArray())}"
    }

    fun generateBuildTag(ts: Long): String {
        val randPart = rand.nextInt(1000)
        val stamp = "${ts / 1000}${"%03d".format(ts % 1000)}000$randPart"
        val hash = md5Hex("$STOCK_CERT_SHA256$BUILD_TAG_SALT$stamp".toByteArray())
        return "$stamp.$hash"
    }

    fun buildCanonicalString(
        method: String,
        accept: String,
        contentType: String,
        url: String,
        body: String?,
        timestampMs: Long,
    ): String {
        val parsed = java.net.URI(url)
        val path = parsed.rawPath ?: ""
        val query = sortedQueryString(url)
        val canonicalUrl = if (query.isEmpty()) path else "$path?$query"

        val bodyHash = if (body != null) {
            val bytes = body.toByteArray(Charsets.UTF_8)
            val truncated = if (bytes.size > 102_400) bytes.copyOf(102_400) else bytes
            md5Hex(truncated)
        } else ""
        val bodyLen = body?.toByteArray(Charsets.UTF_8)?.size?.toString() ?: ""

        return listOf(
            method.uppercase(),
            accept,
            contentType,
            bodyLen,
            timestampMs.toString(),
            bodyHash,
            canonicalUrl,
        ).joinToString("\n")
    }

    private fun sortedQueryString(url: String): String {
        val parsed = java.net.URI(url)
        val query = parsed.rawQuery ?: return ""
        val params = query.split("&").filter { it.isNotEmpty() }.map { kv ->
            val idx = kv.indexOf('=')
            if (idx < 0) kv to "" else kv.substring(0, idx) to kv.substring(idx + 1)
        }.sortedWith(compareBy({ it.first }, { it.second }))
        return params.joinToString("&") { "${it.first}=${it.second}" }
    }

    fun generateSignature(
        method: String,
        accept: String,
        contentType: String,
        url: String,
        body: String?,
        timestampMs: Long,
    ): String {
        val canonical = buildCanonicalString(method, accept, contentType, url, body, timestampMs)
        val mac = Mac.getInstance("HmacMD5")
        mac.init(SecretKeySpec(SECRECT_BYTES, "HmacMD5"))
        val sigB64 = Base64.getEncoder().encodeToString(mac.doFinal(canonical.toByteArray()))
        return "$timestampMs|2|$sigB64"
    }

    fun generateClientInfoAndUa(): Pair<String, String> {
        val androidVersions = arrayOf(
            arrayOf("9", "PQ3A.190605.03081104"),
            arrayOf("10", "QP1A.191005.007.A3"),
            arrayOf("11", "RP1A.200720.011"),
            arrayOf("12", "S1B.220414.015"),
            arrayOf("13", "TQ2A.230405.003"),
        )
        val devices = arrayOf(
            arrayOf("23078RKD5C", "Redmi"),
            arrayOf("2201117TY", "Redmi"),
            arrayOf("2201117TG", "Redmi"),
            arrayOf("22101316G", "Redmi"),
            arrayOf("21121210G", "Redmi"),
            arrayOf("M2012K11AG", "Redmi"),
            arrayOf("M2007J20CG", "Redmi"),
        )
        val versionCodes = intArrayOf(50020117, 50020118, 50020119, 50020120, 50020121)
        val networks = arrayOf("NETWORK_WIFI", "NETWORK_MOBILE")
        val timezones = arrayOf(
            "Asia/Kolkata", "Asia/Shanghai", "Asia/Tokyo",
            "America/New_York", "Europe/London",
        )

        val android = androidVersions[rand.nextInt(androidVersions.size)]
        val device = devices[rand.nextInt(devices.size)]
        val versionCode = versionCodes[rand.nextInt(versionCodes.size)]
        val network = networks[rand.nextInt(networks.size)]
        val timezone = timezones[rand.nextInt(timezones.size)]
        val gaid = randomUuid()
        val deviceId = randomHex(32)

        val userAgent = "com.community.oneroom/$versionCode (Linux; U; Android ${android[0]}; en_US; " +
            "${device[0]}; Build/${android[1]}; Cronet/135.0.7012.3)"

        val clientInfo = """{"package_name":"com.community.oneroom","version_name":"4.0.01.0813.03",
            |"version_code":$versionCode,"os":"android","os_version":"${android[0]}",
            |"install_ch":"ps","device_id":"$deviceId","install_store":"ps","gaid":"$gaid",
            |"brand":"${device[1]}","model":"${device[0]}","system_language":"en","net":"$network",
            |"region":"US","timezone":"$timezone","sp_code":"40401","X-Play-Mode":"2"}""".trimMargin()

        return userAgent to clientInfo
    }

    fun randomSpoofedIp(): String {
        val prefixes = arrayOf("103.85", "103.93", "104.14", "152.101", "167.71", "185.199")
        return "${prefixes[rand.nextInt(prefixes.size)]}.${rand.nextInt(100) + 1}.${rand.nextInt(254) + 1}"
    }

    data class Headers(
        val userAgent: String,
        val accept: String,
        val contentType: String,
        val clientToken: String,
        val signature: String,
        val buildTag: String,
        val clientInfo: String,
        val spoofedIp: String,
    ) {
        fun toMap(): Map<String, String> = mapOf(
            "User-Agent" to userAgent,
            "Accept" to accept,
            "Content-Type" to contentType,
            "Connection" to "keep-alive",
            "x-client-token" to clientToken,
            "x-tr-signature" to signature,
            "x-client-build" to buildTag,
            "x-client-info" to clientInfo,
            "x-client-status" to "0",
            "x-forwarded-for" to spoofedIp,
        )
    }

    fun buildHeaders(method: String, url: String, body: String?, authToken: String?): Headers {
        val (userAgent, baseClientInfo) = generateClientInfoAndUa()
        val ts = nowMillis()
        val accept = "application/json"
        val contentType = "application/json"
        val buildTag = generateBuildTag(ts)
        val clientInfo = baseClientInfo.replace(
            "\"X-Play-Mode\":\"2\"}",
            "\"X-Play-Mode\":\"2\",\"X-Client-Build\":\"$buildTag\"}",
        )
        val signature = generateSignature(method, accept, contentType, url, body, ts)
        return Headers(
            userAgent = userAgent,
            accept = accept,
            contentType = contentType,
            clientToken = generateClientToken(ts),
            signature = signature,
            buildTag = buildTag,
            clientInfo = clientInfo,
            spoofedIp = randomSpoofedIp(),
        )
    }
}
