# Doris Random DDL Generator

## Configuration
Edit `config.properties` to set your database connection details:
```properties
db.url=jdbc:mysql://localhost:9030
db.user=root
db.password=your_password
db.database=your_database_name
```

## Build
```bash
mvn clean package assembly:single
```

## Run
After building, you can run the program using:
```bash
java -jar target/doris-rand-ddl-1.0-SNAPSHOT-jar-with-dependencies.jar
```

The program will:
1. Generate a random DDL statement
2. Print it to console
3. Save it to `rand_ddl.log` file with timestamp

## Log File
All generated DDL statements are logged to `rand_ddl.log` with timestamps.
The log file is located in the same directory where you run the program.
