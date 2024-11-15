#!/usr/bin/env bash
tar -xzvf /homeassistant.tar.gz --strip-components=1 -C /config
exec /init "$@"