package com.mubasher.oms.dfixrouter.system;

import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.exception.DFIXConfigException;

import javax.net.ssl.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;

public class SSLHandler {

    private SSLHandler() {
    }

    public static SSLServerSocketFactory getSSLServerSocketFactory(String trustManagerAlgorithm) throws UnrecoverableKeyException, CertificateException, NoSuchAlgorithmException, IOException, KeyStoreException, DFIXConfigException, KeyManagementException {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(trustManagerAlgorithm);
        tmf.init(getKeyStore());
        return getSSLServerSocketFactory(tmf.getTrustManagers());
    }

    private static KeyStore getKeyStore() throws KeyStoreException {
        return KeyStore.getInstance(System.getProperty("javax.net.ssl.keyStoreType", KeyStore.getDefaultType()));
    }

    private static SSLServerSocketFactory getSSLServerSocketFactory(TrustManager[] trustManagers) throws NoSuchAlgorithmException, UnrecoverableKeyException, CertificateException, IOException, KeyStoreException, DFIXConfigException, KeyManagementException {
        String keyStore = System.getProperty(IConstants.SSL_KEY_STORE_ARG, IConstants.DEFAULT_KEYSTORE_PATH);
        SSLContext sslcontext = SSLContext.getInstance(Settings.getSslVersion());
        sslcontext.init(getKeyManagers(keyStore), trustManagers, null);
        return sslcontext.getServerSocketFactory();
    }

    private static KeyManager[] getKeyManagers(String keyStore) throws DFIXConfigException, NoSuchAlgorithmException, IOException, CertificateException, UnrecoverableKeyException, KeyStoreException {
        File file = new File(keyStore);
        if (file.exists() && file.canRead()){
            KeyStore ks = getKeyStore();
            String keyStorePassword = System.getProperty(IConstants.SSL_KEY_STORE_PASS_ARG,IConstants.DEFAULT_KEY_STORE_PASS);
            String keyPassword = System.getProperty(IConstants.SSL_KEY_STORE_PASS_ARG,IConstants.DEFAULT_KEY_STORE_PASS);
            FileInputStream fs = new FileInputStream(keyStore);
            ks.load(fs, keyStorePassword.toCharArray());
            fs.close();
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(System.getProperty("javax.net.ssl.KeyManagerFactory.algorithm", KeyManagerFactory.getDefaultAlgorithm()));
            kmf.init(ks, keyPassword.toCharArray());
            return kmf.getKeyManagers();
        } else {
            throw new DFIXConfigException("Key Store: " + keyStore + " is not exist/Can not read.");
        }
    }
}
