package io.mo;/*
 * jTPCCUtil - utility functions for the Open Source Java implementation of
 *    the TPC-C benchmark
 *
 * Copyright (C) 2003, Raul Barbosa
 * Copyright (C) 2004-2016, Denis Lussier
 * Copyright (C) 2016, Jan Wieck
 *
 */


import java.sql.*;
import java.util.Properties;

public class jTPCCUtil implements jTPCCConfig
{
  private static Connection             dbConn = null;
  private static PreparedStatement      stmtGetConfig = null;

  public static String getSysProp(String inSysProperty, String defaultValue) {

    String outPropertyValue = null;

    try {
      outPropertyValue = System.getProperty(inSysProperty, defaultValue);
    } catch (Exception e) {
      System.err.println("Error Reading Required System Property '" + inSysProperty + "'");
    }

    return(outPropertyValue);

  } // end getSysProp


  public static String randomStr(long strLen){

    char freshChar;
    String freshString;
    freshString="";

    while(freshString.length() < (strLen - 1)){

      freshChar= (char)(Math.random()*128);
      if(Character.isLetter(freshChar)){
	freshString += freshChar;
      }
    }

    return (freshString);

  } // end randomStr


    public static String getCurrentTime()
    {
	return dateFormat.format(new java.util.Date());
    }

    public static String formattedDouble(double d)
    {
	String dS = ""+d;
	return dS.length() > 6 ? dS.substring(0, 6) : dS;
    }

    public static String getConfig(String db, Properties dbProps, String option)
	throws Exception
    {
        ResultSet   rs;
        String      value;
    
        if (dbConn == null)
        {
            dbConn = DriverManager.getConnection(db, dbProps);
            stmtGetConfig = dbConn.prepareStatement(
            "SELECT cfg_value FROM bmsql_config " +
            " WHERE cfg_name = ?");
            String conn_id = getConnId(dbConn);
            System.out.println(String.format("Connection[%s] has been initialized, and start to get config..",conn_id));
        }
        
        stmtGetConfig.setString(1, option);
        rs = stmtGetConfig.executeQuery();
        if (!rs.next())
            throw new Exception("DB Load configuration parameter '" +
                    option + "' not found");
        
        value = rs.getString("cfg_value");
        rs.close();
    
	    return value;
    }

    public static String getConnId(Connection connection){
        try {
            Statement queryConnId = connection.createStatement();
            ResultSet resultSet = queryConnId.executeQuery("select connection_id();");
            if(resultSet.next())
                return resultSet.getString(1);
            return null;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

} // end jTPCCUtil
