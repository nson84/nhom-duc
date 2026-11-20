package com.aluminum.manager.backup;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BackupSqlPolicyTest {
    @ParameterizedTest @ValueSource(strings = {
            "USE aluminum_app;", "DROP DATABASE aluminum_app;",
            "INSERT INTO `aluminum_app`.`app_settings` (`setting_key`) VALUES ('x');",
            "INSERT INTO `app_settings` (`setting_key`) VALUES (LOAD_FILE('/private'));",
            "INSERT INTO `app_settings` (`setting_key`) VALUES ('x'); DELETE FROM `products`;",
            "INSERT INTO `app_settings` (`setting_key`) VALUES ('unfinished);",
            "SET FOREIGN_KEY_CHECKS = 0; USE aluminum_app;"
    }) void refusesCommandsThatCanEscapeStagingOrExecuteExpressions(String line) {
        assertThrows(java.io.IOException.class, () -> BackupSqlPolicy.validate(line));
    }

    @ParameterizedTest @ValueSource(strings = {
            "SET FOREIGN_KEY_CHECKS = 0;", "SET FOREIGN_KEY_CHECKS = 1;", "DELETE FROM `app_settings`;",
            "INSERT INTO `app_settings` (`setting_key`, `setting_value`) VALUES ('shop.name', 'Nhôm Đức');",
            "INSERT INTO `products` (`id`, `name`, `description`) VALUES (1, 'O\\'Brien', 'ghi chú; USE x; \\n');",
            "INSERT INTO `admin_users` (`id`, `is_active`, `historical_username`) VALUES (101, FALSE, 'Hải');",
            "INSERT INTO `app_settings` (`setting_key`, `setting_value`) VALUES ('binary', X'00ff');",
            "INSERT INTO `products` (`id`, `name`, `description`) VALUES (1.25E+3, 'Nhôm', NULL);"
    }) void permitsOnlyGeneratedDumpStatementsAndLiteralValues(String line) {
        assertDoesNotThrow(() -> BackupSqlPolicy.validate(line));
    }
}
