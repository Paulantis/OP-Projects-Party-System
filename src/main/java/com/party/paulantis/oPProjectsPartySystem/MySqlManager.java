package com.party.paulantis.oPProjectsPartySystem;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class MySqlManager {

    private static Connection connection;

    public static void connect() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/partydb", "partyuser", "starkespasswort");
            System.out.println("Verbindung zur MySQL-Datenbank erfolgreich!");
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
            System.out.println("Fehler beim Verbinden mit der MySQL-Datenbank.");
        }
    }

    public static Connection getConnection() {
        return connection;
    }

    public static void disconnect() {
        if (connection != null) {
            try {
                connection.close();
                System.out.println("MySQL-Verbindung geschlossen.");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}

