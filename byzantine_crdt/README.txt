# How to generate the keys for the different processes
# (Assuming here that there are only 10 processes)

keytool -genkey -alias node1 -keyalg RSA -validity 365 -keystore node1.ks -storetype pkcs12 -dname "CN=, OU=, O=, L=, ST=, C=" -storepass password -keypass password
keytool -genkey -alias node2 -keyalg RSA -validity 365 -keystore node2.ks -storetype pkcs12 -dname "CN=, OU=, O=, L=, ST=, C=" -storepass password -keypass password
keytool -genkey -alias node3 -keyalg RSA -validity 365 -keystore node3.ks -storetype pkcs12 -dname "CN=, OU=, O=, L=, ST=, C=" -storepass password -keypass password
keytool -genkey -alias node4 -keyalg RSA -validity 365 -keystore node4.ks -storetype pkcs12 -dname "CN=, OU=, O=, L=, ST=, C=" -storepass password -keypass password
keytool -genkey -alias node5 -keyalg RSA -validity 365 -keystore node5.ks -storetype pkcs12 -dname "CN=, OU=, O=, L=, ST=, C=" -storepass password -keypass password
keytool -genkey -alias node6 -keyalg RSA -validity 365 -keystore node6.ks -storetype pkcs12 -dname "CN=, OU=, O=, L=, ST=, C=" -storepass password -keypass password
keytool -genkey -alias node7 -keyalg RSA -validity 365 -keystore node7.ks -storetype pkcs12 -dname "CN=, OU=, O=, L=, ST=, C=" -storepass password -keypass password
keytool -genkey -alias node8 -keyalg RSA -validity 365 -keystore node8.ks -storetype pkcs12 -dname "CN=, OU=, O=, L=, ST=, C=" -storepass password -keypass password
keytool -genkey -alias node9 -keyalg RSA -validity 365 -keystore node9.ks -storetype pkcs12 -dname "CN=, OU=, O=, L=, ST=, C=" -storepass password -keypass password
keytool -genkey -alias node10 -keyalg RSA -validity 365 -keystore node10.ks -storetype pkcs12 -dname "CN=, OU=, O=, L=, ST=, C=" -storepass password -keypass password

## I used the password 'password' (without quotes) and maintained default values for all fields.

# Next step is to extract the public key certificate for each node

keytool -exportcert -alias node1 -keystore node1.ks -file node1.cert
keytool -exportcert -alias node2 -keystore node2.ks -file node2.cert
keytool -exportcert -alias node3 -keystore node3.ks -file node3.cert
keytool -exportcert -alias node4 -keystore node4.ks -file node4.cert
keytool -exportcert -alias node5 -keystore node5.ks -file node5.cert
keytool -exportcert -alias node6 -keystore node6.ks -file node6.cert
keytool -exportcert -alias node7 -keystore node7.ks -file node7.cert
keytool -exportcert -alias node8 -keystore node8.ks -file node8.cert
keytool -exportcert -alias node9 -keystore node9.ks -file node9.cert
keytool -exportcert -alias node10 -keystore node10.ks -file node10.cert

# Next and final step in preparation of the cryptographic material is to generate a truststore with all public key certificates

keytool -importcert -alias node1 -file node1.cert -keystore truststore.ks
keytool -importcert -alias node2 -file node2.cert -keystore truststore.ks
keytool -importcert -alias node3 -file node3.cert -keystore truststore.ks
keytool -importcert -alias node4 -file node4.cert -keystore truststore.ks
keytool -importcert -alias node5 -file node5.cert -keystore truststore.ks
keytool -importcert -alias node6 -file node6.cert -keystore truststore.ks
keytool -importcert -alias node7 -file node7.cert -keystore truststore.ks
keytool -importcert -alias node8 -file node8.cert -keystore truststore.ks
keytool -importcert -alias node9 -file node9.cert -keystore truststore.ks
keytool -importcert -alias node10 -file node10.cert -keystore truststore.ks

## I used 'password' (without quotations) as the password for the truststore file

# Compiling

## In the root of the project use maven to generate an executable jar by doing:

mvn clean compile package

# Starting the processes

## In different terminal windows execute the following commands:

java -jar target/dare-0.0.1-SNAPSHOT.jar membership.myhost=127.0.0.1:8001 crypto_name=node1

java -jar target/dare-0.0.1-SNAPSHOT.jar membership.myhost=127.0.0.1:8002 crypto_name=node2

java -jar target/dare-0.0.1-SNAPSHOT.jar membership.myhost=127.0.0.1:8003 crypto_name=node3

java -jar target/dare-0.0.1-SNAPSHOT.jar membership.myhost=127.0.0.1:8004 crypto_name=node4

java -jar target/dare-0.0.1-SNAPSHOT.jar membership.myhost=127.0.0.1:8005 crypto_name=node5

java -jar target/dare-0.0.1-SNAPSHOT.jar membership.myhost=127.0.0.1:8006 crypto_name=node6

java -jar target/dare-0.0.1-SNAPSHOT.jar membership.myhost=127.0.0.1:8007 crypto_name=node7

java -jar target/dare-0.0.1-SNAPSHOT.jar membership.myhost=127.0.0.1:8008 crypto_name=node8

java -jar target/dare-0.0.1-SNAPSHOT.jar membership.myhost=127.0.0.1:8009 crypto_name=node9

java -jar target/dare-0.0.1-SNAPSHOT.jar membership.myhost=127.0.0.1:8010 crypto_name=node10