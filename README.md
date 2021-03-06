Data @ The Point of Care
-

[![Build Status](https://travis-ci.org/CMSgov/dpc-app.svg?branch=master)](https://travis-ci.org/CMSgov/dpc-app)
[![Maintainability](https://api.codeclimate.com/v1/badges/46309e9b1877a7b18324/maintainability)](https://codeclimate.com/github/CMSgov/dpc-app/maintainability)
[![Test Coverage](https://api.codeclimate.com/v1/badges/46309e9b1877a7b18324/test_coverage)](https://codeclimate.com/github/CMSgov/dpc-app/test_coverage)  

Required services
---

DPC requires two external services to be running. *Postgres* and *Redis*

Any version of Redis should suffice, but we require at least Postgres *9.5*.

The `docker-compose` file includes the necessary applications and configurations, and can be started like so: 

```bash
docker-compose up db redis
```

By default, the application attempts to connect to the `dpc_attribution` database on the localhost as the `postgres` user with an empty password.
This database needs to be manually created, but table setup and data migration will be handled by the DPC services.

For Redis, we assume the server is running on the localhost, with the default port.

The defaults can be overridden in the configuration files.
Common configuration options (such as database connection strings) are stored in a [server.conf](src/main/resources/server.conf) file and included in the various modules via the `include "server.conf"` attribute in module application config files.
See the `dpc-attribution` [application.conf](dpc-attribution/src/main/resources/application.conf) for an example.

Default settings can be overridden either directly in the module configurations, or via an `application.local.conf` file in the project root directory. 
For example, modifying the `dpc-attribution` configuration:

```yaml
dpc.attribution {
  database = {
    driverClass = org.postgresql.Driver
    url = "jdbc:postgresql://localhost:5432/dpc-dev"
    user = postgres
  }
  queue {
      singleServerConfig {
        address = "redis://localhost:6379"
      }
    }
}
```

> Note: On startup, the services look for a local override file (application.local.conf) in the root of their *current* working directory.
This can create an issue when running tests with IntelliJ which by default sets the working directory to be the module root, which means any local overrides are ignored.
This can be fixed by setting the working directory to the project root, but needs to done manually.

Building DPC
---

1. Run `mvn clean install` after cloning to build and test the application.
This will also construct the *Docker* images for the various services.
To skip the Docker build pass `-Djib.skip=True`

> Note: DPC only supports Java 11 due to our use of new languages features, which prevents using older JDK versions.
In addition, some of upstream dependencies have not been updated to support Java 12 and newer, but we plan on adding support at a later date. 

Running DPC
--- 

Once the JARs are built, they can be run in two ways either via [`docker-compose`](https://docs.docker.com/compose/overview/) or by manually running the JARs.

## Running via Docker 

The application (along with all required dependencies) can be automatically started with the following command: `docker-compose up`. [Install Docker](https://www.docker.com/products/docker-desktop)

The individual services can be started (along with their dependencies) by passing the service name to the `up` command.

```bash
docker-compose up {db,redis,dpc-aggregation,dpc-attribution,dpc-api}
``` 

## Manual JAR execution

Alternatively, the individual services can be manually executing the `server` command for the various services.

> Note: When manually running the individual services you'll need to ensure that there are no listening port collisions.
By default, each service starts with the same application (8080) and admin (9900) ports. We provide a sample `application.local.conf` file which contains all the necessary configuration options.
This file can be copied and used directly: `cp application.local.conf.sample application.local.conf`. 

Next start each service in a new terminal window, from within the the `dpc-app` root directory. 

```bash
java -jar dpc-attribution/target/dpc-attribution.jar server
java -jar dpc-aggregation/target/dpc-aggregation.jar server
java -jar dpc-api/target/dpc-api.jar server
```

By default, the services will attempt to load the `local.application.conf` file from the current execution directory. 
This can be overriden in two ways.
1. Passing `ENV={dev,test,prod}` will load a `{dev,test,prod}.application.conf` file from the service resources directory.
1. Manually specifying a configuration file after the server command `server src/main/resources/application.conf` will directly load that configuration set.

> Note: Manually specifying a config file will disable the normal configuration merging process. 
This means that only the config variables directly specified in the file will be loaded, no other `application.conf` or `reference.conf` files will be processed. 

1. You can check that the application is running by requesting the FHIR `CapabilitiesStatement` for the `dpc-api` service, which will return a json formatted FHIR resource.
    ```bash
    curl -H "Accept: application/fhir+json" http://localhost:3002/v1/metadata
    ```

Seeding the database
---

> Note: This step is not required when directly running the `demo` for the `dpc-api` service, which partially seeds the database on first execution.

By default, DPC initially starts with an empty attribution database, this means that no patients have been attributed to any providers and thus nothing can be exported from BlueButton.

In order to successfully test and demonstrate the application, there needs to be initial data loaded into the attribution database.
We provide a small CSV [file](src/main/resources/test_associations.csv) which associates some fake providers with valid patients from the BlueButton sandbox.
The database can be automatically migrated and seeded by running the following commands, before starting the Attribution service. 

```bash
java -jar dpc-attribution/target/dpc-attribution.jar db migrate
java -jar dpc-attribution/target/dpc-attribution.jar seed
``` 

Testing the Application
---

### Demo client
The `dpc-api` component contains a `demo` command, which illustrates the basic workflow for submitting an export request and modifying an attribution roster.
It can be executed with the following command:

`java -jar dpc-api/target/dpc-api.jar demo`

> Note: The demo client expects the entire system (all databases and services) to be running from a new state (no data in the database).
This is the default when starting the services from the *docker-compose* file.
When running the JARs manually, the user will need to ensure that the `dpc_attribution` database is truncated after each run. 

The demo performs the following actions:

1. Makes an export request for a given provider.
This request fails because the provider is not registered with the application and has no attributed patients
1. Generates an attribution roster for the provider using the [test_association.csv](src/main/resources/test_associations.csv) file.
1. Resubmits the original export request.
1. Polls the *Job* endpoint using the URL returned from the export request and waits for a completed status.
1. Outputs the download URLs for all files generated by the export request.

### Manual testing


The recommended method for testing the Services is with the [Postman](https://www.getpostman.com) application.
This allows easy visualization of responses, as well as simplifies adding the necessary HTTP Headers. 

> Note: the CURL command strips off the trailing `$export` from any request URIs, which makes it impossible to correctly test the initial data export request.

Steps for testing the data export:

1. Start the services using either the `docker-compose` command or through manually running the JARs.

    If running the JARs manually, you will need to migrate and seed the database before continuing. 
1. Make an initial *GET* request to the following endpoint: `http://localhost:3002/v1/Group/3461C774-B48F-11E8-96F8-529269fb1459/$export`.
This will request a data export for all the patients attribution to provider: `3461C774-B48F-11E8-96F8-529269fb1459`.
You will need to set the *ACCEPT* header to `application/fhir+json` (per the FHIR bulk spec).
1. The response from the export endpoint should be a *204* with the `Content-Location` header containing a URL which the user can use to to check the status of the job.
1. Make a *GET* request using the URL provided by the `/Group` endpoint from the previous step.
 Which has this format: `http://localhost:3002/v1/Jobs/{unique UUID of export job}`.
 You will need to ensure that the *ACCEPT* header is set to `application/fhir+json` (per the FHIR bulk spec).
 You will need to ensure that the *PREFER* header is set to `respond-async`.
 The server should return a *204* response until the job has completed.
 Once the job is complete, the endpoint should return data in the following format (the actual values will be different):
 
     ```javascript
    {
        "transactionTime": 1550868647.776162,
        "request": "http://localhost:3002/v1/Job/de00da66-86cf-4be1-a2a8-0415b21a6a9b",
        "requiresAccessToken": false,
        "output": [
            "http://localhost:3002/v1/Data/de00da66-86cf-4be1-a2a8-0415b21a6a9b"
        ],
        "error": []
    }
    ```
    The output array contains a list of URLs where the exported files can be downloaded from.
1. Download the exported files by calling the `/Data` endpoint with URLs provided. e.g. `http://localhost:3002/v1/Data/de00da66-86cf-4be1-a2a8-0415b21a6a9b`.
1. Enjoy your glorious ND-JSON formatted FHIR data.
