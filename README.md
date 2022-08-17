# gtfsrt2hfp [![Build .jar and publish Docker image](https://github.com/HSLdevcom/gtfsrt2hfp/actions/workflows/build-and-publish.yml/badge.svg)](https://github.com/HSLdevcom/gtfsrt2hfp/actions/workflows/build-and-publish.yml)
Application for producing HFP from GTFS-RT vehicle positions by matching third-party GTFS data to HSL GTFS data 

## Usage

```bash
docker run -it --rm -v $(pwd)/application.properties:/application.properties hsldevcom/gtfsrt2hfp:develop -f /application.properties
```

### Configuration

Create configuration file, which will be mounted to the Docker container (see above):

```
gtfs.url.a = https://dev.hsl.fi/gtfs/hsl.zip #HSL GTFS feed
gtfs.url.b = https://minfoapi.matkahuolto.fi/gtfs/067/gtfs.zip #GTFS feed corresponding to the GTFS-RT feed

gtfsRt.url = #GTFS-RT feed URL
gtfsRt.apiKey = #API key for GTFS-RT feed

mqtt.brokerUri = #MQTT broker URI

routeIds = 7280 #Route IDs from HSL GTFS feed for which HFP is produved

hfp.operatorId = 9999 #Operator ID which will be used in HFP messages
```
