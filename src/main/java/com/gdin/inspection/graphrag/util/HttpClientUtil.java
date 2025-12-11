package com.gdin.inspection.graphrag.util;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.Timeout;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.http.HttpClient;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;

public class HttpClientUtil {

    public static HttpClient getClient(){
        return getClient(null);
    }

    public static HttpClient getClient(Long timeoutInSeconds){
        return getClient(timeoutInSeconds, true);
    }

    public static HttpClient getClient(Long timeoutInSeconds, boolean trustAllCertificates){
        HttpClient.Builder httpBuilder = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1);
        if(trustAllCertificates){
            try {
                TrustManager[] trustAll = new TrustManager[]{
                        new X509TrustManager() {
                            public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                            public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                            public X509Certificate[] getAcceptedIssuers() { return null; }
                        }
                };
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustAll, new SecureRandom());
                httpBuilder.sslContext(sslContext);

                // 禁用主机名验证
                SSLParameters sslParams = sslContext.getDefaultSSLParameters();
                sslParams.setEndpointIdentificationAlgorithm(null);
                httpBuilder.sslParameters(sslParams);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        if(timeoutInSeconds!=null) httpBuilder.connectTimeout(Duration.ofSeconds(timeoutInSeconds));
        return httpBuilder.build();
    }

    public static CloseableHttpClient getApacheClient() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        return getApacheClient(null);
    }

    public static CloseableHttpClient getApacheClient(Long timeoutInSeconds) throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        return getApacheClient(timeoutInSeconds, true);
    }

    public static CloseableHttpClient getApacheClient(Long timeoutInSeconds, boolean trustAllCertificates) throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        CloseableHttpClient client;
        if(trustAllCertificates||timeoutInSeconds!=null){
            HttpClientBuilder builder = HttpClients.custom();
            if(trustAllCertificates){
                // 1. 构建 SSLContext（信任所有证书，生产环境请使用正规 CA）
                SSLContext sslContext = SSLContextBuilder.create()
                        .loadTrustMaterial(null, (X509Certificate[] chain, String authType) -> true)
                        .build();
                // 2. 用 SSLContext 构造一个 SSLConnectionSocketFactory，并关闭主机名校验
                SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
                        sslContext,
                        (hostname, session) -> true  // NoopHostnameVerifier
                );
                // 3. 构造一个注册了 http/https 的 Registry
                Registry registry = RegistryBuilder.create()
                        .register("http", PlainConnectionSocketFactory.getSocketFactory())
                        .register("https", sslSocketFactory)
                        .build();
                // 4. 用这个 Registry 创建连接管理器
                PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager(registry);
                builder.setConnectionManager(connManager);
            }
            if(timeoutInSeconds!=null){
                RequestConfig requestConfig = RequestConfig.custom()
                        .setConnectTimeout(Timeout.ofSeconds(timeoutInSeconds))
                        .setConnectionRequestTimeout(Timeout.ofSeconds(timeoutInSeconds))
                        .setResponseTimeout(Timeout.ofSeconds(timeoutInSeconds))
                        .build();
                builder.setDefaultRequestConfig(requestConfig);
            }
            client = builder.build();
        }
        else client = HttpClients.createDefault();
        return client;
    }
}
