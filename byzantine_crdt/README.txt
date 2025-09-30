# How to generate the keys for the different processes
# (Assuming here that there are only 3 processes)

keytool -genkey -alias node1 -keyalg RSA -validity 365 -keystore node1.ks -storetype pkcs12
keytool -genkey -alias node2 -keyalg RSA -validity 365 -keystore node2.ks -storetype pkcs12
keytool -genkey -alias node3 -keyalg RSA -validity 365 -keystore node3.ks -storetype pkcs12
keytool -genkey -alias node4 -keyalg RSA -validity 365 -keystore node4.ks -storetype pkcs12

## I used the password 'password' (without quotes) and maintained default values for all fields.

# Next step is to extract the public key certificate for each node

keytool -exportcert -alias node1 -keystore node1.ks -file node1.cert
keytool -exportcert -alias node2 -keystore node2.ks -file node2.cert
keytool -exportcert -alias node3 -keystore node3.ks -file node3.cert
keytool -exportcert -alias node4 -keystore node4.ks -file node4.cert

# Next and final step in preparation of the cryptographic material is to generate a truststore with all public key certificates

keytool -importcert -alias node1 -file node1.cert -keystore truststore.ks
keytool -importcert -alias node2 -file node2.cert -keystore truststore.ks
keytool -importcert -alias node3 -file node3.cert -keystore truststore.ks
keytool -importcert -alias node4 -file node4.cert -keystore truststore.ks

## I used 'password' (without quotations) as the password for the truststore file

# Compiling

## In the root of the project use maven to generate an executable jar by doing:

mvn clean compile package

# Starting the processes

## In different terminal windows execute the following commands:

java -jar target/dare-0.0.1-SNAPSHOT.jar membership.myhost=127.0.0.1:5000 crypto_name=node1

java -jar target/dare-0.0.1-SNAPSHOT.jar membership.myhost=127.0.0.1:6000 crypto_name=node2

java -jar target/dare-0.0.1-SNAPSHOT.jar membership.myhost=127.0.0.1:7000 crypto_name=node3

java -jar target/dare-0.0.1-SNAPSHOT.jar membership.myhost=127.0.0.1:8000 crypto_name=node4