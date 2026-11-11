package com.gimnasio.sistemagym.integration.mysql;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;

/** Rejects selected MySQL integration classes that bypass the shared safety setup. */
@Order(-1000)
public final class MySql84IntegrationClassGuard implements BeforeAllCallback {

    private static final String TEST_PACKAGE =
            "com.gimnasio.sistemagym.integration.mysql";

    @Override
    public void beforeAll(ExtensionContext context) {
        Class<?> testClass = context.getRequiredTestClass();
        if (TEST_PACKAGE.equals(testClass.getPackageName())
                && testClass.getSimpleName().endsWith("MySql84IT")
                && !MySql84IntegrationTestSupport.class.isAssignableFrom(testClass)) {
            throw new ExtensionConfigurationException(
                    "Toda prueba *MySql84IT del paquete dedicado debe extender "
                            + "MySql84IntegrationTestSupport.");
        }
    }
}
