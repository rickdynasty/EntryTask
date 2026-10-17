package com.entry.lib.net

import android.content.Context
import okhttp3.Call
import okhttp3.Request
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.InputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object NetManager {
    private lateinit var mockContext: Context

    fun initMock(context: Context) {
        mockContext = context.applicationContext
        baseUrl = "https://entrytask.invalid/"
    }

    fun newCall(request: Request): Call = okHttpClient.newCall(request)

    private var baseUrl: String = "https://api.example.com/" // 默认兜底地址
    private val interceptors = mutableListOf<Interceptor>()

    // 保存双向认证配置所需的凭证数据
    private var clientCertStream: InputStream? = null
    private var clientCertPassword: String? = null
    private var serverCaStream: InputStream? = null

    /**
     * 初始化网络配置（建议在 Application 中调用）
     * @param url 基础请求地址 (BaseUrl)
     * @param ignoreCert 是否忽略证书校验（true 适用于开发、测试环境或自签证书，生产环境严禁开启）
     */
    fun init(url: String, ignoreCert: Boolean = false) {
        baseUrl = url
    }

    /**
     * 【双向认证初始化】
     * @param url BaseUrl
     * @param clientCert 客户端证书(.p12 / .pfx)的输入流
     * @param clientPassword 客户端证书的访问密码
     * @param serverCa 服务端 CA 根证书的输入流（如果服务端是自签证书需要提供，若是公开 CA 可传 null）
     */
    fun initMutualTls(
        url: String,
        clientCert: InputStream,
        clientPassword: String,
        serverCa: InputStream? = null
    ) {
        baseUrl = url
        clientCertStream = clientCert
        clientCertPassword = clientPassword
        serverCaStream = serverCa
    }

    /**
     * 动态注册全局拦截器（如 MockInterceptor、LogInterceptor 等）
     */
    fun addInterceptor(interceptor: Interceptor): NetManager {
        if (!interceptors.contains(interceptor)) {
            interceptors.add(interceptor)
        }
        return this
    }

    private val okHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            // Native auth includes CPU-bound password verification; RN requests override to 10s.
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .dispatcher(Dispatcher().apply { maxRequests = 8; maxRequestsPerHost = 8 })
            .addInterceptor(MockInterceptor(checkNotNull(if (::mockContext.isInitialized) mockContext else null) { "Call initMock first" })) // 统一挂载 Mock 拦截器

        // 1. 动态挂载所有已注册的拦截器
        interceptors.forEach { builder.addInterceptor(it) }

        // 如果配置了客户端证书，则启用双向认证
        if (clientCertStream != null && clientCertPassword != null) {
            configureMutualTls(builder, clientCertStream!!, clientCertPassword!!, serverCaStream)
        }

        builder.build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * 提供泛型创建方法供业务模块调用
     */
    fun <T> create(serviceClass: Class<T>): T {
        return retrofit.create(serviceClass)
    }

    /**
     * 核心：配置双向认证的 SSLContext
     */
    private fun configureMutualTls(
        builder: OkHttpClient.Builder,
        clientCert: InputStream,
        password: String,
        serverCa: InputStream?
    ) {
        try {
            // 1. 加载客户端证书 (PKCS12 格式)
            val keyStore = KeyStore.getInstance("PKCS12")
            keyStore.load(clientCert, password.toCharArray())

            val keyManagerFactory =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            keyManagerFactory.init(keyStore, password.toCharArray())

            // 2. 加载服务端 CA 证书（用于校验服务器）
            val trustManagerFactory = if (serverCa != null) {
                val trustStore = KeyStore.getInstance(KeyStore.getDefaultType())
                trustStore.load(null, null)

                val certificateFactory = CertificateFactory.getInstance("X.509")
                val caCertificate = certificateFactory.generateCertificate(serverCa)
                trustStore.setCertificateEntry("server_ca", caCertificate)

                val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                tmf.init(trustStore)
                tmf
            } else {
                // 如果没有提供私有 CA，使用系统默认的 TrustManager
                val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                tmf.init(null as KeyStore?)
                tmf
            }

            // 3. 初始化 SSLContext
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(
                keyManagerFactory.keyManagers,
                trustManagerFactory.trustManagers,
                SecureRandom()
            )

            val trustManagers = trustManagerFactory.trustManagers
            if (trustManagers.size == 1 && trustManagers[0] is X509TrustManager) {
                val x509TrustManager = trustManagers[0] as X509TrustManager
                builder.sslSocketFactory(sslContext.socketFactory, x509TrustManager)
            } else {
                throw IllegalStateException("Unexpected default trust managers")
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}