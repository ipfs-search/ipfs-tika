# ipfs-tika
[![Build Status](https://travis-ci.org/ipfs-search/ipfs-tika.svg?branch=travis)](https://travis-ci.org/ipfs-tika/ipfs-tika)

Java web application taking IPFS hashes, extracting (textual) content and metadata through Apache's Tika.

## Compiling
`mvn compile`

## Running
`mvn exec:java -Dexec.mainClass="com.ipfssearch.ipfstika.App"`

## Packaging
`mvn package`

## Settings
Setting can be done through environment variables:

* `IPFS_TIKA_LISTEN_HOST`: Host to listen on for connections (default localhost).
* `IPFS_TIKA_LISTEN_PORT`: Port to listen on for connections (default 8081).
* `IPFS_GATEWAY`: URL of IPFS gateway (default: "http://localhost:8080/").
