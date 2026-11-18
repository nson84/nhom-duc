# Copy to test-mysql-env.local.ps1 and enter credentials for a dedicated test MySQL.
$env:ALUMINUM_TEST_DB_URL = "jdbc:mysql://127.0.0.1:3306/aluminum_backup_test?allowPublicKeyRetrieval=true&sslMode=DISABLED"
$env:ALUMINUM_TEST_DB_USERNAME = ""
$env:ALUMINUM_TEST_DB_PASSWORD = ""
