# ipfs-tika
[![Build Status](https://travis-ci.org/ipfs-search/ipfs-tika.svg?branch=travis)](https://travis-ci.org/ipfs-search/ipfs-tika) [![](https://img.shields.io/docker/automated/ipfssearch/ipfs-tika.svg)](https://cloud.docker.com/u/ipfssearch/repository/docker/ipfssearch/ipfs-tika)

Java web application taking IPFS hashes, extracting (textual) content and metadata through Apache's Tika.

## Requirements
* Java 8
* Maven

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

## Docker
This starts an IPFS daemon at the default ports and exposes ipfs-tika on 8081.

## Usage
Request the IPFS path and metadata will be extracted and returned. Example:

`curl  http://localhost:8081/ipfs/QmS4ustL54uo8FzR9455qaxZwuMiUhyvMcX9Ba8nUH4uVv/readme`
