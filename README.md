# Doris Random DDL Generator

### 构建
```bash
mvn clean package assembly:single
```

## jar 包运行
通过加参数去运行
```
如果不指定的话，默认值为
db_host=127.0.0.1
db_port=9030
db_user=root
db_password=
db_database=db
insert_sleep_ms=1000 // insert 操作的间隔时间
rand_ddl_count=200 // dll 总共执行成功的操作 （不会计算失败的次数）
run_insert_ops=5000 // insert 操作需要执行的次数，设置为0可以不跑

```

如果指定了话，就会按照参数运行，比如
```
java -jar target/doris-rand-ddl-1.0-SNAPSHOT-jar-with-dependencies.jar --db_database db1 --db_port 9030 --rand_ddl_count 10 --run_insert_ops 1
```
后台运行
```
nohup java -jar doris-rand-ddl-1.0-SNAPSHOT-jar-with-dependencies.jar --db_database db1 --db_port 9030 --rand_ddl_count 10 --run_insert_ops 10 > nohup_out.log 2>&1 &
```
会产生两个日志，"insert_operation.log" 会记录 insert 的操作，"rand_ddl.log" 会记录 rand ddl 的操作