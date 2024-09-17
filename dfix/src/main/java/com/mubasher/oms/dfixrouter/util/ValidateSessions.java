package com.mubasher.oms.dfixrouter.util;

import com.mubasher.oms.dfixrouter.constants.IConstants;
import com.mubasher.oms.dfixrouter.logs.DFIXLogHandlerI;
import com.mubasher.oms.dfixrouter.logs.Log4j2Handler;
import quickfix.ConfigError;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by nuwanis on 10/11/2016.
 */
public class ValidateSessions {
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{(.+?)}");
    private static final DFIXLogHandlerI logger = new Log4j2Handler("com.mubasher.oms.dfixrouter.util.ValidateSessions");
    private static ValidateSessions validateSessions = null;
    private ArrayList<String> sessions;
    private Properties variableValues;

    public static ValidateSessions getInstance() {
        if (validateSessions == null) {
            validateSessions = new ValidateSessions(System.getProperties());
        }
        return validateSessions;
    }
    private ValidateSessions(Properties properties) {
        variableValues = properties;
    }

    public int load(InputStream stream) throws ConfigError {
        try {
            sessions = new ArrayList<>();
            ValidateSessions.Tokenizer tokenizer = new Tokenizer();
            InputStreamReader reader = new InputStreamReader(stream);
            iterateTokenizer(tokenizer, reader);
        } catch (IOException var10) {
            logger.error("FIX Session Validation Failed: " + var10.getMessage(), var10);
            ConfigError configError = new ConfigError(var10.getMessage());
            configError.fillInStackTrace();
            throw configError;
        }
        return sessions.size();
    }

    private void iterateTokenizer(ValidateSessions.Tokenizer tokenizer, InputStreamReader reader) throws IOException, ConfigError {
        String section = null;
        Properties e = null;
        for (Tokenizer.Token token = tokenizer.getToken(reader);
             token != null;
             token = tokenizer.getToken(reader)) {
            if (token.getType() == 4 && IConstants.SESSION_SECTION_NAME.equalsIgnoreCase(token.getValue())) {
                this.validateSessionIDs(section, e);
                section = IConstants.SESSION_SECTION_NAME;
                e = new Properties();
            } else if (token.getType() == 2 && e != null) {
                ValidateSessions.Tokenizer.Token valueToken = tokenizer.getToken(reader);
                String value = null;
                if (valueToken != null) {
                    value = this.interpolate(valueToken.getValue());
                }
                e.setProperty(token.getValue(), value);
            }
        }
        this.validateSessionIDs(section, e);
    }

    private void validateSessionIDs(String currentSectionId, Properties currentSection) throws ConfigError {
        if (currentSectionId != null && IConstants.SESSION_SECTION_NAME.equals(currentSectionId)) {
            String sessionIdentifier = currentSection.getProperty(IConstants.SESSION_IDENTIFIER);
            if (sessionIdentifier == null) {
                throw new ConfigError("SessionIdentifier not configured");
            } else if (sessions.contains(sessionIdentifier)) {
                throw new ConfigError("SessionIdentifier not unique");
            } else {
                sessions.add(sessionIdentifier);
                logger.info("Validated Session: " + sessionIdentifier);
            }
        }

    }

    /**
     * this method process session property values loaded from SystemProperties
     * Eg
     * SessionIdentifier=${SESSIONIDENTIFIER_TEST}
     * @param value
     * @return
     */

    private String interpolate(String value) {
        if (value == null || value.indexOf(36) == -1) {
            return value;
        } else {
            //check whether value contains the dollar sign character ($, ASCII code 36)
            StringBuffer buffer = new StringBuffer();
            Matcher m = VARIABLE_PATTERN.matcher(value);

            while (m.find()) {
                //checks if the match is preceded by a backslash (\), which means the placeholder is escaped and should not be replaced
                if (m.start() == 0 || value.charAt(m.start() - 1) != 92) {
                    String variable = m.group(1);
                    String variableValue = this.variableValues.getProperty(variable);
                    if (variableValue != null) {
                        m.appendReplacement(buffer, variableValue);
                    }
                }
            }
            m.appendTail(buffer);
            return buffer.toString();
        }
    }

    public List<String> getSessions() {
        return sessions;
    }

    private static class Tokenizer {
        public static final int ID_TOKEN = 2;
        public static final int VALUE_TOKEN = 3;
        public static final int SECTION_TOKEN = 4;
        private final StringBuilder sb;
        private char ch;

        private Tokenizer() {
            this.ch = 0;
            this.sb = new StringBuilder();
        }

        private ValidateSessions.Tokenizer.Token getToken(Reader reader) throws IOException {
            if (this.ch == 0) {     //null
                this.ch = this.nextCharacter(reader);
            }

            this.skipWhitespace(reader);
            if (this.isLabelCharacter(this.ch)) {
                this.sb.setLength(0);

                do {
                    this.sb.append(this.ch);
                    this.ch = this.nextCharacter(reader);
                } while (this.isLabelCharacter(this.ch));

                return new ValidateSessions.Tokenizer.Token(ID_TOKEN, this.sb.toString());
            } else if (this.ch == '=') {     //'='
                this.ch = this.nextCharacter(reader);
                this.sb.setLength(0);
                if (this.isValueCharacter(this.ch)) {
                    do {
                        this.sb.append(this.ch);
                        this.ch = this.nextCharacter(reader);
                    } while (this.isValueCharacter(this.ch));
                }

                return new ValidateSessions.Tokenizer.Token(VALUE_TOKEN, this.sb.toString().trim());
            } else if (this.ch == '[') {     //'['
                this.ch = this.nextCharacter(reader);
                ValidateSessions.Tokenizer.Token id = this.getToken(reader);
                this.ch = this.nextCharacter(reader);
                return new ValidateSessions.Tokenizer.Token(SECTION_TOKEN, id.getValue());
            } else if (this.ch != '#') {     //'#'
                return null;
            } else {
                do {
                    this.ch = this.nextCharacter(reader);
                } while (this.isValueCharacter(this.ch));

                return this.getToken(reader);
            }
        }

        private void skipWhitespace(Reader reader) throws IOException {
            if (Character.isWhitespace(this.ch)) {
                do {
                    this.ch = this.nextCharacter(reader);
                } while (Character.isWhitespace(this.ch));
            }

        }

        private char nextCharacter(Reader reader) throws IOException {
            return (char) reader.read();
        }

        private boolean isValueCharacter(char ch) {
            return !this.isEndOfStream(ch) && !this.isNewLineCharacter(ch);
        }

        private boolean isNewLineCharacter(char ch) {
            return "\r\n".indexOf(ch) != -1;
        }

        private boolean isEndOfStream(char ch) {
            return (byte) ch == -1;
        }

        private boolean isLabelCharacter(char ch) {
            return !this.isEndOfStream(ch) && "[]=#".indexOf(ch) == -1;
        }


        private static class Token {
            private final int type;
            private final String value;

            public Token(int type, String value) {
                this.type = type;
                this.value = value;
            }

            public int getType() {
                return this.type;
            }

            public String getValue() {
                return this.value;
            }

            @Override
            public String toString() {
                return this.type + ": " + this.value;
            }
        }
    }
}
