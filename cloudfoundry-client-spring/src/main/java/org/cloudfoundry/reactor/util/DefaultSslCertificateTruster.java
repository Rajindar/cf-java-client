/*
 * Copyright 2013-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cloudfoundry.reactor.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.tuple.Tuple2;
import reactor.io.netty.config.ClientOptions;
import reactor.io.netty.tcp.TcpClient;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

final class DefaultSslCertificateTruster implements SslCertificateTruster {

    private final Logger logger = LoggerFactory.getLogger("cloudfoundry-client.trust");

    private final AtomicReference<X509TrustManager> delegate;

    private final ProxyContext proxyContext;

    private final Set<Tuple2<String, Integer>> trustedHostsAndPorts;

    DefaultSslCertificateTruster(ProxyContext proxyContext) {
        this.proxyContext = proxyContext;
        this.delegate = new AtomicReference<>(getTrustManager(getTrustManagerFactory(null)));
        this.trustedHostsAndPorts = Collections.newSetFromMap(new ConcurrentHashMap<>());
    }

    @Override
    public void checkClientTrusted(X509Certificate[] x509Certificates, String authType) throws CertificateException {
        this.delegate.get().checkClientTrusted(x509Certificates, authType);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] x509Certificates, String authType) throws CertificateException {
        this.delegate.get().checkServerTrusted(x509Certificates, authType);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return this.delegate.get().getAcceptedIssuers();
    }

    @Override
    public void trust(String host, int port, Duration duration) {
        Tuple2<String, Integer> hostAndPort = Tuple2.of(host, port);
        if (this.trustedHostsAndPorts.contains(hostAndPort)) {
            return;
        }

        this.logger.warn("Trusting SSL Certificate for {}:{}", host, port);

        X509TrustManager trustManager = this.delegate.get();
        X509Certificate[] untrustedCertificates = getUntrustedCertificates(duration, host, port, this.proxyContext, trustManager);

        if (untrustedCertificates != null) {
            KeyStore trustStore = addToTrustStore(untrustedCertificates, trustManager);
            this.delegate.set(getTrustManager(getTrustManagerFactory(trustStore)));
        }

        this.trustedHostsAndPorts.add(hostAndPort);
    }

    private static KeyStore addToTrustStore(X509Certificate[] untrustedCertificates, X509TrustManager trustManager) {
        try {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null);

            int count = 0;
            for (X509Certificate certificate : untrustedCertificates) {
                trustStore.setCertificateEntry(String.valueOf(count++), certificate);
            }
            for (X509Certificate certificate : trustManager.getAcceptedIssuers()) {
                trustStore.setCertificateEntry(String.valueOf(count++), certificate);
            }

            return trustStore;
        } catch (CertificateException | NoSuchAlgorithmException | IOException | KeyStoreException e) {
            throw new RuntimeException(e);
        }
    }

    private static TcpClient getTcpClient(ProxyContext proxyContext, CertificateCollectingTrustManager collector, String host, int port) {
        ClientOptions options = ClientOptions.to(host, port)
            .sslSupport();

        proxyContext.ifConfigured((proxyHost, proxyPort, username, password) -> options.proxy(ClientOptions.Proxy.HTTP, proxyHost, proxyPort, username, u -> password));
        options.ssl().trustManager(new StaticTrustManagerFactory(collector));

        return TcpClient.create(options);
    }

    private static X509TrustManager getTrustManager(TrustManagerFactory trustManagerFactory) {
        for (TrustManager trustManager : trustManagerFactory.getTrustManagers()) {
            if (trustManager instanceof X509TrustManager) {
                return (X509TrustManager) trustManager;
            }
        }

        throw new IllegalStateException("No X509TrustManager in TrustManagerFactory");
    }

    private static TrustManagerFactory getTrustManagerFactory(KeyStore trustStore) {
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);

            return trustManagerFactory;
        } catch (KeyStoreException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static X509Certificate[] getUntrustedCertificates(Duration duration, String host, int port, ProxyContext proxyContext, X509TrustManager delegate) {
        CertificateCollectingTrustManager collector = new CertificateCollectingTrustManager(delegate);

        getTcpClient(proxyContext, collector, host, port)
            .start(channel -> channel.receive().then())
            .block(duration);

        X509Certificate[] chain = collector.getCollectedCertificateChain();
        if (chain == null) {
            throw new IllegalStateException("Could not obtain server certificate chain");
        }

        if (collector.isTrusted()) {
            return null;
        } else {
            return chain;
        }
    }

}
