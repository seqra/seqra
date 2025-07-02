/*
 *  Copyright 2022 Opentaint contributors (opentaint.dev)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

@SuppressWarnings("ALL")
public class SqlInjectionExamples {

    void bad() {
        String data = System.getenv("USER");
        try (
                Connection dbConnection = DriverManager.getConnection("", "", "");
                Statement sqlStatement = dbConnection.createStatement();
        ) {
            boolean result = sqlStatement.execute("insert into users (status) values ('updated') where name='" + data + "'");

            if (result) {
                System.out.println("User '" + data + "' updated successfully");
            } else {
                System.out.println("Unable to update records for user '" + data + "'");
            }
        } catch (SQLException e) {
            System.err.println("Error: " + e);
        } finally {
            System.out.println("OK!");
        }
    }

}
