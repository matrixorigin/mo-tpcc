# mo-tpcc
This project is customed from benchmarksql-5.0, to run TPCC Benchmark for MatrixOne.
The project mainly customized the schema, SQL statements, and some transaction conflict processing codes, and did not change the TPCC core process code


# How to use?

This tool is designed to test MatrixOne benchmark for TPCC or any other database functionalities with SQL.

### 1: Run your MatrixOne instance or other DB instance.

Checkout [Install MatrixOne](https://docs.matrixorigin.io/0.4.0/MatrixOne/Get-Started/install-standalone-matrixone/) to launch a MatrixOne instance.

Or you can launch whatever database software as you want.

### 2. Fork and clone this mo-tpch project.

  ```
  git clone https://github.com/matrixorigin/mo-tpcc.git
  ```

### 3. Run the test.

You can use this tool to generate the TPC-C data,create TPC-C tables,load data to MatrixOrigin(or other database),and run TPC-C benchmark.

And these Opertions must be executed step by step as the following descriptions, or there will be errors unexpected during the test.

**Fisrt,you should make some configs in `props.mo` :**

  ```
  db=mo
  driver=com.mysql.cj.jdbc.Driver
  conn=jdbc:mysql://127.0.0.1:6001/tpcc?characterSetResults=utf8&continueBatchOnError=false&useServerPrepStmts=true&alwaysSendSetIsolation=false&useLocalSessionState=true&zeroDateTimeBehavior=CONVERT_TO_NULL&failoverReadOnly=false&serverTimezone=Asia/Shanghai&enabledTLSProtocols=TLSv1.2&useSSL=false
  user=dump
  password=111

  //the number of warehouse
  warehouses=1
  loadWorkers=4
  
  //the num of terminals that will simultaneously run
  //must be less than warehouses*10
  terminals=1
  //To run specified transactions per terminal- runMins must equal zero
  runTxnsPerTerminal=0
  //To run for specified minutes- runTxnsPerTerminal must equal zero
  runMins=1
  //Number of total transactions per minute
  limitTxnsPerMin=0
  ```


**Second,you should TPC-C database and tables by the command:**

`./runSQL.sh props.mo tableCreates`


**Third,you should generate and load TPC-C data to by the command:**

`./runLoader.sh props.mo warehouse 10`

If only need to generate data, use command:

`./runLoader.sh props.mo warehouse 10 filelocation /yourpath/`

**Then，you can run TPC-C benchmark by the commands:**

`./runBenchmark.sh props.mo`
