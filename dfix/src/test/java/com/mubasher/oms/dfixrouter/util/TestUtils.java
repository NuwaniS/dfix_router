package com.mubasher.oms.dfixrouter.util;

import com.mubasher.oms.dfixrouter.constants.IConstants;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Created by Nilaan L on 7/1/2024.
 */
public final class TestUtils {

    private TestUtils() {}

    // Change field accessibility
    public static Field changeFieldAccessibility(Class<?> clazz, String fieldName,boolean accessible) throws NoSuchFieldException {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(accessible);
        return field;
    }

    public static Object getPrivateField(Object object, String field) throws NoSuchFieldException, IllegalAccessException {
        final Field field1 = changeFieldAccessibility(object.getClass(), field, IConstants.CONSTANT_TRUE);
        final Object o = field1.get(object);
        field1.setAccessible(IConstants.CONSTANT_FALSE);
        return o;
    }

    public static void setPrivateField(Object object, String field, Object value) throws NoSuchFieldException, IllegalAccessException {
        final Field field1 = changeFieldAccessibility(object.getClass(), field, IConstants.CONSTANT_TRUE);
        field1.set(object, value);
        field1.setAccessible(IConstants.CONSTANT_FALSE);
    }

    // Change method accessibility
    public static Method changeMethodAccessibility(Class<?> clazz, String methodName,boolean accessible, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(accessible);
        return method;
    }
    // Change method accessibility and invoke it
    public static Object invokePrivateMethod(Object object, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method reflectdMethod = changeMethodAccessibility(object.getClass(),methodName,true, parameterTypes);
        final Object results = reflectdMethod.invoke(object, args);
        reflectdMethod.setAccessible(false);
        return results;
    }

    public static void deleteFile(String filePath) {
        File file = new File(filePath);
        if (file.exists()) {
            file.delete();
        }
    }

    public static void deleteDirectory(String directoryPath) {
        File directory = new File(directoryPath);

        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file.getPath());
                    } else {
                        file.delete();
                    }
                }
            }
        }
        directory.delete();
    }

}
