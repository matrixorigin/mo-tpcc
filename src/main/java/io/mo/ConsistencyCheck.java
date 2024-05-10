package io.mo;

import org.apache.log4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

public class ConsistencyCheck {
    private static org.apache.log4j.Logger log = Logger.getLogger(io.mo.ConsistencyCheck.class);
    public static void main(String[] args){
        
        int interval = 0;
        if(args.length > 0){
            interval = Integer.parseInt(args[0]);
        }
        
        Properties ini = new Properties();
        try {
            ini.load( new FileInputStream(System.getProperty("prop")));
        } catch (IOException e) {
            log.error("Could not load properties file, please check.");
        }

        String  iDB                 = getProp(ini,"db");
        String  iDriver             = getProp(ini,"driver");
        String  iConn               = getProp(ini,"conn");
        String  iUser               = getProp(ini,"user");
        String  iPassword           = ini.getProperty("password");
        boolean success             = true;

        HashMap<String,String>  errors = new HashMap<>();    
        
        String[] queries = new String[]{
                "(Select w_id, w_ytd from bmsql_warehouse) except (select d_w_id, sum(d_ytd) from bmsql_district group by d_w_id);",
                "(Select d_w_id, d_id, D_NEXT_O_ID - 1 from bmsql_district)  except (select o_w_id, o_d_id, max(o_id) from bmsql_oorder group by  o_w_id, o_d_id);",
                "(Select d_w_id, d_id, D_NEXT_O_ID - 1 from bmsql_district)  except (select no_w_id, no_d_id, max(no_o_id) from bmsql_new_order group by no_w_id, no_d_id);",
                "select * from (select (count(no_o_id)-(max(no_o_id)-min(no_o_id)+1)) as diff from bmsql_new_order group by no_w_id, no_d_id) as temp where diff != 0;",
                "(select o_w_id, o_d_id, sum(o_ol_cnt) from bmsql_oorder  group by o_w_id, o_d_id) except (select ol_w_id, ol_d_id, count(ol_o_id) from bmsql_order_line group by ol_w_id, ol_d_id);",
                "(select d_w_id, sum(d_ytd) from bmsql_district group by d_w_id)  except(Select w_id, w_ytd from bmsql_warehouse);",
                "select c_w_id, c_d_id, c_id,count(1) from bmsql_customer group by c_w_id, c_d_id, c_id having count(1) > 1 limit 10;",
                "select c_w_id, c_d_id, c_id,count(1) from bmsql_customer group by c_w_id, c_d_id, c_id having count(1) > 1 limit 10;",
                "select no_w_id, no_d_id, no_o_id, count(1) from bmsql_new_order group by no_w_id, no_d_id, no_o_id having count(1) > 1 limit 10;",
                "select o_w_id,o_d_id, o_id,count(1) from bmsql_oorder group by o_w_id, o_d_id, o_id having count(1) > 1 limit 10;",
                "select s_w_id, s_i_id,count(1) from bmsql_stock group by s_w_id, s_i_id having count(1) > 1 limit 10;",
                "select ol_w_id, ol_d_id, ol_o_id, ol_number,count(1) from bmsql_order_line group by ol_w_id, ol_d_id, ol_o_id, ol_number having count(1) > 1 limit 10;"};

        Properties dbProps = new Properties();
        dbProps.setProperty("user", iUser);
        dbProps.setProperty("password", iPassword);
        Connection conn = null;
        String currentQuery = null;
        try {
            Class.forName(iDriver);
            
            try {
                conn = DriverManager.getConnection(iConn, dbProps);
            }catch (SQLException e){
                log.error("Could not get valid connection from " + iDB + ":\n" +
                        "user=" + iUser + ", password=" + iPassword + "\n" +
                        "jdbcURL="+iConn);
                System.exit(1);
            }
            
            if(interval == 0) {
                try {
                    Statement stmt = conn.createStatement();
                    for (String query : queries) {
                        currentQuery = query;
                        ResultSet resultSet = stmt.executeQuery(query);
                        String error = "";
                        int colcount = resultSet.getMetaData().getColumnCount();
                        boolean current = true;
                        while (resultSet.next()) {
                            success = false;
                            current = false;
                            for (int i = 1; i < colcount + 1; i++) {
                                error += resultSet.getString(i);
                                error += "\t";
                            }
                            error += "\n";
                        }

                        if (!current) {
                            errors.put(query, error);
                        }
                    }

                    if (!success) {
                        Set keys = errors.keySet();
                        Iterator iterator = keys.iterator();
                        while (iterator.hasNext()) {
                            String key = (String) iterator.next();
                            String error = errors.get(key);
                            log.error("Consistency verification failed for sql : " + key);
                            log.error("The exceptional result are :\n" + error);
                        }
                        System.exit(1);
                    } else {
                        log.info("Consistency verification successfully.");
                        System.exit(0);
                    }
                } catch (SQLException e) {
                    log.error(e.getMessage());
                    log.error("SQL: " + currentQuery);
                    success = false;
                    System.exit(1);
                }
            }else {
                while (true){
                    success = true;
                    try {
                        Statement stmt = conn.createStatement();
                        for (String query : queries) {
                            currentQuery = query;
                            ResultSet resultSet = stmt.executeQuery(query);
                            String error = "";
                            int colcount = resultSet.getMetaData().getColumnCount();
                            boolean current = true;
                            while (resultSet.next()) {
                                success = false;
                                current = false;
                                for (int i = 1; i < colcount + 1; i++) {
                                    error += resultSet.getString(i);
                                    error += "\t";
                                }
                                error += "\n";
                            }

                            if (!current) {
                                errors.put(query, error);
                            }
                        }

                        if (!success) {
                            Set keys = errors.keySet();
                            Iterator iterator = keys.iterator();
                            while (iterator.hasNext()) {
                                String key = (String) iterator.next();
                                String error = errors.get(key);
                                log.error("Consistency verification failed for sql : " + key);
                                log.error("The exceptional result are :\n" + error);
                            }
                        } else {
                            log.info("Consistency verification successfully.");
                        }
                    } catch (SQLException e) {
                        log.error(e.getMessage());
                        log.error("SQL: " + currentQuery);
                        success = false;
                    }

                    Thread.sleep(interval*1000);
                }
            }
        } catch (ClassNotFoundException e) {
            log.error("Could not find driver " + iDriver);
            System.exit(1);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    private static String getProp (Properties p, String pName)
    {
        String prop =  p.getProperty(pName);
        log.info(pName + "=" + prop);
        return(prop);
    }
}
