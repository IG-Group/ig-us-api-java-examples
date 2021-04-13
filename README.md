# about

Examples clients for the IG US API.

Note: The purpose of these examples is to demonstrate in a concise manner simple uses of the API. The code is not production quality, as proper abstraction and encapsulation might make it more maintainable but harder to read as a reference. Unit tests are also omitted.

# licence
[licence](./LICENCE)

# run
most examples can be run by providing a url a login and a password. Trading examples will need an IG_ACCOUNT too. Contact IG to get the correct url, credentials, and account for the demo environment.

## websocket examples
```
URL=wss://demo-iguspretrade.ig.com/pretrade IG_USERNAME=user1234566 IG_PASSWORD=secret mvn clean spring-boot:run
```

or for Trading:

```
URL=wss://demo-igustrade.ig.com/trade IG_USERNAME=user1234566 IG_PASSWORD=secret IG_ACCOUNT=ac123 mvn clean spring-boot:run
```
## FIX Examples
```
 HOST=host PORT=12345 IG_USERNAME=user123 IG_PASSWORD=secret mvn clean spring-boot:run
```
or for Trading:

```
 HOST=host PORT=12345 IG_USERNAME=user123 IG_PASSWORD=secret IG_ACCOUNT=ABCDE mvn clean spring-boot:run
```
