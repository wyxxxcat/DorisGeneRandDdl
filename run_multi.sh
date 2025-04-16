THREAD_COUNT=4

for i in $(seq 1 $THREAD_COUNT)
do
    nohup java -jar target/doris-rand-ddl-1.0-SNAPSHOT-jar-with-dependencies.jar > doris_ddl_$i.log 2>&1 &
    echo "Started instance $i with PID $!"
done