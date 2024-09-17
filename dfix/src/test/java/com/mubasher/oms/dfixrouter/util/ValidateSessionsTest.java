package com.mubasher.oms.dfixrouter.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import quickfix.ConfigError;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Created by isharaw on 9/26/2017.
 */
class ValidateSessionsTest {


    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void load_sessionIdentifierNotConfigured() throws FileNotFoundException, ConfigError,URISyntaxException {

        final FileInputStream in = getFileInputStream("incorrectSessions.cfg");
        Assertions.assertThrows(quickfix.ConfigError.class,()->ValidateSessions.getInstance().load(in));
    }

    @Test
    void load_DuplicateConfiguration() throws Exception{

        final FileInputStream in = getFileInputStream("duplicateSessions.cfg");
        Assertions.assertThrows(quickfix.ConfigError.class,()->ValidateSessions.getInstance().load(in));
    }

    @Test
    void load_CorrectConfiguration() throws Exception{

        final FileInputStream in = getFileInputStream("correctSessions.cfg");
        ValidateSessions.getInstance().load(in);
        Assertions.assertTrue(true);
    }

    @Test
    void load_CorrectConfigurationWithDefault() throws Exception{
        final FileInputStream in = getFileInputStream("correctSessions_2.cfg");
        System.setProperty("SESSIONIDENTIFIER_TEST","Sys_Ses");
        System.setProperty("SESSIONIDENTIFIER_TEST1","Sys_Ses1");
        ValidateSessions.getInstance().load(in);
        Assertions.assertTrue(ValidateSessions.getInstance().getSessions().contains("Sys_Ses"));
        Assertions.assertTrue(ValidateSessions.getInstance().getSessions().contains("NSys_SesISys_Ses1L"));
        Assertions.assertTrue(ValidateSessions.getInstance().getSessions().contains("\\$NSDQ1"));
    }

    private FileInputStream getFileInputStream(String cfgFileName) throws URISyntaxException, FileNotFoundException {
        ClassLoader classLoader = getClass().getClassLoader();
        String path = classLoader.getResource(cfgFileName).getPath();
        URI uri = new URI(path.trim().replaceAll("\\u0020", "%20"));
        File file = new File(uri.getPath());
        FileInputStream in = new FileInputStream(file);
        return in;
    }
}
