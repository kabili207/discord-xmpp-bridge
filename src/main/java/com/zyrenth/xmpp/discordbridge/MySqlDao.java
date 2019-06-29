package com.zyrenth.xmpp.discordbridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.*;
import java.util.Date;

/**
 * Created by kabili on 1/31/16.
 */
public class MySqlDao {

    static Logger logger = LoggerFactory.getLogger(MySqlDao.class);

    static Properties prop = new Properties();
    static String mysqlHost = null;
    static String mysqlDb = null;
    static String mysqlUser = null;
    static String mysqlPass = null;
    static String mysqlTimezone = null;

    private final static Random sRandom = new Random();
    private final Set<Integer> mMessageIds = new HashSet<Integer>();

    private static MySqlDao instance;


    private MySqlDao() {
        try {
            prop.load(MySqlDao.class.getClassLoader().getResourceAsStream("application.properties"));

            mysqlHost = prop.getProperty("mysqlhost");
            mysqlDb = prop.getProperty("mysqldb");
            mysqlUser = prop.getProperty("mysqluser");
            mysqlPass = prop.getProperty("mysqlpass");
            mysqlTimezone = prop.getProperty("mysqltz");
        } catch (IOException e) {
            logger.error("MySqlDao()", "Error loading properties", e);
        }
    }

    public static MySqlDao getInstance() {

        if (instance == null) {
            instance = new MySqlDao();
        }
        return instance;
    }

    public String getUniqueMessageId() {
        int nextRandom = sRandom.nextInt();
        while (mMessageIds.contains(nextRandom)) {
            nextRandom = sRandom.nextInt();
        }
        return Integer.toString(nextRandom);
    }

    private Connection getConnection() throws SQLException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        // This will load the MySQL driver, each DB has its own driver
        Class.forName("com.mysql.jdbc.Driver").newInstance();
        // Setup the connection with the DB
        return DriverManager
                .getConnection(String.format("jdbc:mysql://%s/%s?user=%s&password=%s&serverTimezone=%s",
                        mysqlHost, mysqlDb, mysqlUser, mysqlPass, mysqlTimezone));
    }

    public String addRegistration(String regId) throws SQLException, ClassNotFoundException,
            NoSuchAlgorithmException, UnsupportedEncodingException, InstantiationException, IllegalAccessException {
        Connection connect = null;
        PreparedStatement statement = null;

        try {
            connect = getConnection();

            String pairingCode = null;

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] regBytes = regId.getBytes(StandardCharsets.UTF_8);
            md.update(regBytes);
            byte[] digest = md.digest();

            statement = connect.prepareStatement("select pairing_code from users where registration_hash = ?");
            statement.setBytes(1, digest);
            ResultSet result = statement.executeQuery();


            if (result.next()) {
                pairingCode = result.getString("pairing_code");
            } else {
                statement =
                        connect.prepareStatement("insert into users (registration_id, registration_hash, pairing_code) " +
                                "values (?, ?, ?)");


                int num = sRandom.nextInt(90000000) + 10000000;
                pairingCode = Integer.toString(num);


                statement.setBytes(1, regBytes);
                statement.setBytes(2, digest);
                statement.setString(3, pairingCode);

                statement.executeUpdate();

            }

            return pairingCode;

        } finally {
            if (statement != null)
                statement.close();
            if (connect != null)
                connect.close();
        }
    }

    public void updateRegistrationId(String regId, String pairingCode) throws SQLException, ClassNotFoundException,
            NoSuchAlgorithmException, UnsupportedEncodingException, InstantiationException, IllegalAccessException {
        Connection connect = null;
        PreparedStatement statement = null;

        try {
            connect = getConnection();
            statement =
                    connect.prepareStatement("update users set registration_id=?, registration_hash=? " +
                            " where pairing_code = ? ");

            MessageDigest md = MessageDigest.getInstance("SHA-256");

            byte[] regBytes = regId.getBytes(StandardCharsets.UTF_8);
            md.update(regBytes);
            byte[] digest = md.digest();

            statement.setBytes(1, regBytes);
            statement.setBytes(2, digest);
            statement.setString(3, pairingCode);

            statement.executeUpdate();
        } finally {
            if (statement != null)
                statement.close();
            if (connect != null)
                connect.close();
        }
    }

    public void updateJid(String pairingCode, String jid) throws SQLException, ClassNotFoundException,
            NoSuchAlgorithmException, UnsupportedEncodingException, InstantiationException, IllegalAccessException {
        Connection connect = null;
        PreparedStatement statement = null;

        try {
            connect = getConnection();

            statement = connect.prepareStatement("delete from users where jid = ? and pairing_code <> ?");
            statement.setString(1, jid);
            statement.setString(2, pairingCode);
            statement.executeUpdate();

            statement = connect.prepareStatement("update users set jid=? where pairing_code = ? ");
            statement.setString(1, jid);
            statement.setString(2, pairingCode);

            statement.executeUpdate();
        } finally {
            if (statement != null)
                statement.close();
            if (connect != null)
                connect.close();
        }

    }

    public String getJid(String regId) throws SQLException, ClassNotFoundException,
            NoSuchAlgorithmException, UnsupportedEncodingException, InstantiationException, IllegalAccessException {
        Connection connect = null;
        PreparedStatement statement = null;
        ResultSet result = null;

        try {
            connect = getConnection();
            statement =
                    connect.prepareStatement("select jid from users where registration_hash = ?");

            MessageDigest md = MessageDigest.getInstance("SHA-256");

            md.update(regId.getBytes(StandardCharsets.UTF_8));
            byte[] digest = md.digest();

            statement.setBytes(1, digest);

            result = statement.executeQuery();
            String jid = null;
            if (result.next()) {
                jid = result.getString("jid");
            }
            return jid;

        } finally {
            if (result != null)
                result.close();
            if (statement != null)
                statement.close();
            if (connect != null)
                connect.close();
        }
    }

    public String getRegistrationId(String jid) throws SQLException, ClassNotFoundException,
            NoSuchAlgorithmException, UnsupportedEncodingException, InstantiationException, IllegalAccessException {
        Connection connect = null;
        PreparedStatement statement = null;
        ResultSet result = null;

        try {
            connect = getConnection();
            statement =
                    connect.prepareStatement("select registration_id from users where jid = ?");
            statement.setString(1, jid);

            result = statement.executeQuery();
            String regId = null;
            if (result.next()) {
                byte[] bytes = result.getBytes("registration_id");
                regId = new String(bytes, StandardCharsets.UTF_8);
            }
            return regId;

        } finally {
            if (result != null)
                result.close();
            if (statement != null)
                statement.close();
            if (connect != null)
                connect.close();
        }
    }

    public Map<Integer, String> getAllUsers() throws SQLException, ClassNotFoundException,
             InstantiationException, IllegalAccessException {
        Connection connect = null;
        PreparedStatement statement = null;
        ResultSet result = null;

        Map<Integer, String> contacts = new HashMap<>();

        try {
            connect = getConnection();

            statement =
                    connect.prepareStatement("SELECT user_id, jid FROM users WHERE jid IS NOT NULL");

            result = statement.executeQuery();

            while (result.next()) {
                contacts.put(result.getInt("user_id"), result.getString("jid"));
            }

        } finally {
            if (result != null)
                result.close();
            if (statement != null)
                statement.close();
            if (connect != null)
                connect.close();
        }
        return contacts;
    }

    public Map<String, String> getSubscribedContacts(String jid) throws SQLException, ClassNotFoundException,
            NoSuchAlgorithmException, UnsupportedEncodingException, InstantiationException, IllegalAccessException {
        Connection connect = null;
        PreparedStatement statement = null;
        ResultSet result = null;

        Map<String, String> contacts = new HashMap<>();

        try {
            connect = getConnection();

            statement =
                    connect.prepareStatement("SELECT number, name " +
                            "FROM contacts INNER JOIN users on contacts.user_id = users.user_id " +
                            "where jid = ? AND subscribed = 1");
            statement.setString(1, jid);

            result = statement.executeQuery();

            while (result.next()) {
                contacts.put(result.getString("number"), result.getString("name"));
            }

        } finally {
            if (result != null)
                result.close();
            if (statement != null)
                statement.close();
            if (connect != null)
                connect.close();
        }
        return contacts;
    }

    public String getContactName(JID jid, String number) throws SQLException, ClassNotFoundException,
            NoSuchAlgorithmException, UnsupportedEncodingException, InstantiationException, IllegalAccessException {
        return getContactName(jid.toBareJID(), number);
    }

    public String getContactName(String jid, String number) throws SQLException, ClassNotFoundException,
            NoSuchAlgorithmException, UnsupportedEncodingException, InstantiationException, IllegalAccessException {
        Connection connect = null;
        PreparedStatement statement = null;
        ResultSet result = null;

        String contactName = null;

        try {
            connect = getConnection();

            statement =
                    connect.prepareStatement("SELECT name " +
                            "FROM contacts INNER JOIN users on contacts.user_id = users.user_id " +
                            "where jid = ? AND number = ?");
            statement.setString(1, jid);
            statement.setString(2, number);

            result = statement.executeQuery();

            if (result.next()) {
                contactName = result.getString("name");
            }

        } finally {
            if (result != null)
                result.close();
            if (statement != null)
                statement.close();
            if (connect != null)
                connect.close();
        }
        return contactName;
    }


    public void updateContactSubscription(JID jid, String number, boolean subscribed) throws SQLException, ClassNotFoundException,
            NoSuchAlgorithmException, UnsupportedEncodingException, InstantiationException, IllegalAccessException {
        updateContactSubscription(jid.toBareJID(), number, subscribed);
    }

    public void updateContactSubscription(String jid, String number, boolean subscribed) throws SQLException, ClassNotFoundException,
            NoSuchAlgorithmException, UnsupportedEncodingException, InstantiationException, IllegalAccessException {
        Connection connect = null;
        PreparedStatement statement = null;


        try {
            connect = getConnection();
            statement = connect.prepareStatement("INSERT INTO contacts (user_id, number, subscribed) " +
                    "SELECT user_id, ?, ? FROM users where jid = ? " +
                    "ON DUPLICATE KEY UPDATE contacts.subscribed = ?;");
            statement.setString(1, number);
            statement.setBoolean(2, subscribed);
            statement.setString(3, jid);
            statement.setBoolean(4, subscribed);

            statement.executeUpdate();

        } finally {
            if (statement != null)
                statement.close();
            if (connect != null)
                connect.close();
        }
    }


    public boolean updateContactInfo(JID jid, String number, String name) throws SQLException, ClassNotFoundException,
            NoSuchAlgorithmException, UnsupportedEncodingException, InstantiationException, IllegalAccessException {
        return updateContactInfo(jid.toBareJID(), number, name);
    }

    public boolean updateContactInfo(String jid, String number, String name) throws SQLException, ClassNotFoundException,
            NoSuchAlgorithmException, UnsupportedEncodingException, InstantiationException, IllegalAccessException {
        Connection connect = null;
        PreparedStatement statement = null;
        ResultSet result = null;

        boolean updated = false;

        try {
            connect = getConnection();

            statement =
                    connect.prepareStatement("SELECT contact_id, name " +
                            "FROM contacts INNER JOIN users on contacts.user_id = users.user_id " +
                            "where jid = ? AND number = ?");
            statement.setString(1, jid);
            statement.setString(2, number);

            result = statement.executeQuery();

            int contactId = 0;
            String contactName = null;

            if (result.next()) {
                contactName = result.getString("name");
                if (!Objects.equals(contactName, name)) {
                    contactId = result.getInt("contact_id");
                    statement = connect.prepareStatement("UPDATE contacts SET name = ? where contact_id = ?");
                    statement.setString(1, name);
                    statement.setInt(2, contactId);
                    statement.executeUpdate();
                    updated = true;
                }
            } else {
                statement = connect.prepareStatement("INSERT INTO contacts (user_id, number, name) " +
                        "SELECT user_id, ?, ? FROM users where jid = ?");
                statement.setString(1, number);
                statement.setString(2, name);
                statement.setString(3, jid);
                statement.executeUpdate();

                updated = true;
            }

        } finally {
            if (result != null)
                result.close();
            if (statement != null)
                statement.close();
            if (connect != null)
                connect.close();
        }
        return updated;
    }

}
