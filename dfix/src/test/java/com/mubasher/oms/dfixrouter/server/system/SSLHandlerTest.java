package com.mubasher.oms.dfixrouter.server.system;

import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.exception.DFIXConfigException;
import com.mubasher.oms.dfixrouter.system.SSLHandler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLServerSocketFactory;

/**
 * Created by Nilaan L on 7/01/2024.
 */
public class SSLHandlerTest {

    @Test
    public void getSSLServerSocketFactoryTest() throws Exception {
        //valid keystore configuration
        // Arrange
        String trustManagerAlgorithm = "X509";
        System.setProperty(IConstants.SSL_KEY_STORE_PASS_ARG,IConstants.DEFAULT_KEY_STORE_PASS);
        System.setProperty(IConstants.SSL_KEY_STORE_PASS_ARG,IConstants.DEFAULT_KEY_STORE_PASS);
        System.setProperty(IConstants.SSL_KEY_STORE_ARG, System.getProperty("base.dir") + "/src/main/external-resources/system/server.keystore");
        // Act
        SSLServerSocketFactory result = SSLHandler.getSSLServerSocketFactory(trustManagerAlgorithm);

        // Assert
        Assertions.assertNotNull(result);
    }

    @Test
    public void getSSLServerSocketFactoryFailTest() {
        //keystore File does not exist
        // Arrange
        String trustManagerAlgorithm = "X509";
        System.setProperty(IConstants.SSL_KEY_STORE_PASS_ARG,IConstants.DEFAULT_KEY_STORE_PASS);
        System.setProperty(IConstants.SSL_KEY_STORE_PASS_ARG,IConstants.DEFAULT_KEY_STORE_PASS);
        System.setProperty(IConstants.SSL_KEY_STORE_ARG, System.getProperty("base.dir") + "/src/main/external-resources/system/serverFail.keystore");

        // Assert
        Assertions.assertThrows(DFIXConfigException.class,()->SSLHandler.getSSLServerSocketFactory(trustManagerAlgorithm));
    }
}
